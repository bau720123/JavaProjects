package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance 資料服務類
 * 支援取得各大指數或股票的歷史日K線資料（包含即時價格補充）
 * 例如：
 *   ^VIX     → 美國恐慌指數
 *   ^TWII    → 台灣加權指數
 *   2330.TW  → 台積電
 *   AAPL     → Apple 美股
 */
public class YahooFinanceService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 取得指定 symbol 的歷史日K資料（包含可能補充的今日即時價格）
     *
     * @param symbol Yahoo Finance 格式的股票/指數代碼（如 "^VIX", "^TWII", "2330.TW"）
     * @param days   欲取得的天數（包含今天）
     * @return List<YahooCandle> 每日K線資料，若失敗回傳空列表
     */
    public List<YahooCandle> fetchHistory(String symbol, int days) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(days - 1); // 包含今天，所以減 days-1
            String encodedSymbol = symbol.replace("^", "%5E"); // ^VIX → %5EVIX

            long period1 = startDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
            long period2 = today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

            // String url = String.format(
            //     "https://query1.finance.yahoo.com/v8/finance/chart/%s" +
            //     "?period1=%d&period2=%d&interval=1d&events=history&includeAdjustedClose=true",
            //     encodedSymbol, period1, period2
            // );

            String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s" + "?interval=1d&range=%d" + "d",
                encodedSymbol, days
            );

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Yahoo Finance API 失敗，HTTP " + response.code() + "：" + symbol);
                    return List.of();
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode resultNode = root.path("chart").path("result");
                if (resultNode.isEmpty() || resultNode.get(0) == null) {
                    return List.of();
                }

                JsonNode result = resultNode.get(0);
                JsonNode timestamps = result.path("timestamp");
                JsonNode meta = result.path("meta");
                JsonNode quote = result.path("indicators").path("quote").get(0);

                JsonNode opens = quote.path("open");
                JsonNode highs = quote.path("high");
                JsonNode lows = quote.path("low");
                JsonNode closes = quote.path("close");

                List<YahooCandle> candles = new ArrayList<>();

                for (int i = 0; i < timestamps.size(); i++) {
                    long ts = timestamps.get(i).asLong();
                    LocalDate date = Instant.ofEpochSecond(ts)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate();

                    double o = getDoubleOrZero(opens, i);
                    double h = getDoubleOrZero(highs, i);
                    double l = getDoubleOrZero(lows, i);
                    double c = getDoubleOrZero(closes, i);

                    // 過濾無效資料（Yahoo 有時會補 null）
                    if (o > 0 && h > 0 && l > 0 && c > 0) {
                        candles.add(new YahooCandle(date, o, h, l, c));
                    }
                }

                // 補充今日即時價格（如果加權指數歷史資料未包含今天）
                double realtime = meta.path("regularMarketPrice").asDouble(0.0);
                if (realtime > 0.0 && encodedSymbol.equals("TWII")) {
                    boolean hasToday = candles.stream().anyMatch(c -> c.date().equals(today));
                    if (!hasToday) {
                        candles.add(new YahooCandle(today, realtime, realtime, realtime, realtime));
                    }
                }

                return candles;

            } catch (IOException e) {
                System.err.println("Yahoo Finance 網路錯誤 [" + symbol + "]：" + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Yahoo Finance 資料解析錯誤 [" + symbol + "]：" + e.getMessage());
        }

        return List.of();
    }

    private double getDoubleOrZero(JsonNode array, int index) {
        if (array.has(index) && !array.get(index).isNull()) {
            return array.get(index).asDouble(0.0);
        }
        return 0.0;
    }

    /**
     * 代表 Yahoo Finance 單日K線的記錄類
     */
    public record YahooCandle(
        LocalDate date,
        double open,
        double high,
        double low,
        double close
    ) {}
}