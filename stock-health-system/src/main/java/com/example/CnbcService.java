package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * 從 CNBC 取得美股期貨盤前電子盤（Fair Value）與台積電 ADR 盤前/盤後報價
 * 使用 CNBC 的 fvquote 與 quote-html-webservice 接口
 */
public class CnbcService {

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // 共用請求方法
    private JsonNode fetchJson(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("CNBC API 回應失敗，狀態碼：" + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            if (body.trim().isEmpty()) {
                throw new IOException("CNBC 回應為空");
            }
            return mapper.readTree(body);
        }
    }

    /**
     * 取得美股主要指數期貨盤前 Fair Value 變動（道瓊、標普、納斯達克、羅素2000）
     * 一次呼叫即可取得全部四個指數
     * 參考網址：https://www.cnbc.com/pre-markets/
     */
    public FairValueFutures getFairValueFutures() {
        String url = "https://quote.cnbc.com/quote-html-webservice/fvquote.htm" +
                     "?requestMethod=quick&noform=0&realtime=1&client=fairValue&output=json" +
                     "&symbols=DJ%7CSP%7CND%7CTF";

        try {
            JsonNode root = fetchJson(url);
            JsonNode quotes = root.path("FairValueQuoteResult").path("FairValueQuote");

            double djChange = 0.0, spChange = 0.0, ndChange = 0.0, tfChange = 0.0;
            String updateTime = "未知";

            for (JsonNode item : quotes) {
                String symbol = item.path("symbol").asText();
                double fvChange = item.path("fv_change").asDouble(0.0);

                switch (symbol) {
                    case "DJ":
                        djChange = fvChange;
                        break;
                    case "SP":
                        spChange = fvChange;
                        break;
                    case "ND":
                        ndChange = fvChange;
                        break;
                    case "TF":
                        tfChange = fvChange;
                        break;
                }

                // 取第一個有時間的作為代表（通常時間相近）
                if (updateTime.equals("未知") && item.has("last_timedate")) {
                    updateTime = item.path("last_timedate").asText("未知");
                }
            }

            return new FairValueFutures(djChange, spChange, ndChange, tfChange, updateTime);

        } catch (Exception e) {
            System.err.println("抓取 CNBC 美股期貨 Fair Value 失敗：" + e.getMessage());
            return new FairValueFutures(0.0, 0.0, 0.0, 0.0, "抓取失敗");
        }
    }

    public record FairValueFutures(
            double dowChange,
            double spChange,
            double nasdaqChange,
            double russellChange,
            String updateTime
    ) {
        public boolean isValid() {
            return dowChange != 0 || spChange != 0 || nasdaqChange != 0 || russellChange != 0;
        }
    }

    public MarketQuote getQuoteNew() {
        String url = "https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=.DJI%7C.SP500%7C.IXIC%7C.SOX%7CTSM&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json&events=1";

        try {
            JsonNode root = fetchJson(url);
            JsonNode quotes = root.path("FormattedQuoteResult").path("FormattedQuote");

            if (quotes.isMissingNode() || !quotes.isArray()) {
                return new MarketQuote("無法取得資料", "無法取得資料", "無法取得資料", "無法取得資料", "無法取得資料", "無法取得資料", "無法取得資料");
            }

            String dowChange = "無法取得";
            String spChange = "無法取得";
            String nasdaqChange = "無法取得";
            String soxChange = "無法取得";
            String tsmRegular = "無法取得";
            String tsmType = "無法取得";
            String tsmMarket = "無法取得";

            for (JsonNode item : quotes) {
                String symbol = item.path("symbol").asText("");

                if (".DJI".equals(symbol)) {
                    dowChange = item.path("change").asText("無法取得");
                } else if (".SP500".equals(symbol)) {
                    spChange = item.path("change").asText("無法取得");
                } else if (".IXIC".equals(symbol)) {
                    nasdaqChange = item.path("change").asText("無法取得");
                } else if (".SOX".equals(symbol)) {
                    soxChange = item.path("change").asText("無法取得");
                } else if ("TSM".equals(symbol)) {
                    tsmRegular = item.path("change").asText("無法取得");

                    JsonNode extended = item.path("ExtendedMktQuote");
                    if (!extended.isMissingNode()) {
                        tsmType = extended.path("type").asText("無法取得");
                        tsmMarket = extended.path("change").asText("無法取得");
                    }
                }
            }

            return new MarketQuote(dowChange, spChange, nasdaqChange, soxChange, tsmRegular, tsmType, tsmMarket);

        } catch (Exception e) {
            System.err.println("抓取 CNBC 主要指數 + TSM ADR 報價失敗：" + e.getMessage());
            return new MarketQuote("抓取失敗", "抓取失敗", "抓取失敗", "抓取失敗", "抓取失敗", "抓取失敗", "抓取失敗");
        }
    }

    public record MarketQuote(
            String dowChange, // 道瓊 change
            String spChange, // S&P 500 change
            String nasdaqChange, // 納斯達克 change
            String soxChange, // 費城半導體 change
            String tsmRegular, // 台積電 ADR 盤中 change
            String tsmType, // 台積電 ADR 延伸數據 type
            String tsmMarket // 台積電 ADR 延伸數據 change
    ) {
        public boolean hasData() {
            return !dowChange.contains("無法取得") && !dowChange.contains("抓取失敗");
        }
    }
}