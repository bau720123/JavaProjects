package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 專門負責抓取聯準會利率期貨隱含機率（Investing.com + FRED）
 * 回傳 FedWatchResult 物件，供 MainApp 顯示文字 + 繪製圓餅圖
 */
public class FedWatchService {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    // 共用 FRED API 呼叫方法
    private static HttpResponse<String> callFredApi(String route, String seriesId, String apiKey) {
        try {
            String url = String.format(
                "https://api.stlouisfed.org/%s?series_id=%s&api_key=%s&file_type=json&limit=1&sort_order=desc",
                route, seriesId, apiKey
            );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("FRED API 呼叫失敗 [" + seriesId + "]：" + e.getMessage());
            return null;
        }
    }

    // 自動計算目前區間中點（使用共用方法）
    private static double getCurrentRateMidpoint(String fredApiKey) {
        try {
            // 抓上界
            HttpResponse<String> upperResp = callFredApi("fred/series/observations", "DFEDTARU", fredApiKey);
            double upper = 0.0;
            if (upperResp != null && upperResp.statusCode() == 200) {
                JsonNode obs = mapper.readTree(upperResp.body()).path("observations").get(0);
                String val = obs.path("value").asText();
                if (!".".equals(val)) upper = Double.parseDouble(val);
            }

            // 抓下界
            HttpResponse<String> lowerResp = callFredApi("fred/series/observations", "DFEDTARL", fredApiKey);
            double lower = 0.0;
            if (lowerResp != null && lowerResp.statusCode() == 200) {
                JsonNode obs = mapper.readTree(lowerResp.body()).path("observations").get(0);
                String val = obs.path("value").asText();
                if (!".".equals(val)) lower = Double.parseDouble(val);
            }

            if (upper > 0 && lower > 0) {
                return (lower + upper) / 2.0;
            }
        } catch (Exception e) {
            System.err.println("計算中點失敗，使用備援值 4.125：" + e.getMessage());
        }
        return 4.125;
    }

    // 判斷降幾碼
    private static String getRateAction(String targetRate, double midCurrent) {
        try {
            String[] parts = targetRate.split(" - ");
            double lower = Double.parseDouble(parts[0]);
            double upper = Double.parseDouble(parts[1]);
            double diff = midCurrent - ((lower + upper) / 2);
            int codes = (int) Math.round(diff / 0.25);
            if (codes == 0) return "維持利率";
            return codes > 0 ? "降" + codes + "碼" : "升" + (-codes) + "碼";
        } catch (Exception e) {
            return "未知";
        }
    }

    public static class FedWatchResult {
        public String meetingDate = "未知";
        public List<String> labels = new ArrayList<>();
        public List<Double> probabilities = new ArrayList<>();
        public double currentRate = 0.0;
        public String fullText = "【聯準會利率期貨隱含機率】\n\n";
    }

    public static FedWatchResult getProbability(String fredApiKey) {
        FedWatchResult result = new FedWatchResult();

        // 只算一次中點
        double midCurrent = getCurrentRateMidpoint(fredApiKey);

        try {
            Document doc = Jsoup.connect("https://www.investing.com/central-banks/fed-rate-monitor")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            // 會議日期
            Element dateElem = doc.getElementById("cardName_0");
            String rawDate = (dateElem != null) ? dateElem.text().trim() : "2025-12-10";
            try {
                String clean = rawDate.replace(",", "").trim();
                if (clean.matches("^[A-Za-z]{3} \\d{1,2} \\d{4}$")) {
                    result.meetingDate = java.time.LocalDate.parse(clean,
                            java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.ENGLISH))
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } else {
                    result.meetingDate = rawDate;
                }
            } catch (Exception e) {
                result.meetingDate = rawDate;
            }
            result.fullText += "下次會議：" + result.meetingDate + "\n\n";

            // 抓第一個 table
            Elements tables = doc.select("table.genTbl.openTbl.fedRateTbl");
            if (!tables.isEmpty()) {
                Elements rows = tables.first().select("tbody tr");

                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 4 && cells.get(0).text().contains("-")) {
                        String targetRate = cells.get(0).text().trim();
                        String curr = cells.get(1).text().trim();
                        String prevDay = cells.get(2).text().trim();
                        String prevWeek = cells.get(3).text().trim();

                        String action = getRateAction(targetRate, midCurrent);
                        String label = targetRate + "（" + action + "）";

                        result.labels.add(label);

                        // 安全解析 curr，支援 "—"、" - "、空白等情況
                        double probability;
                        if (curr.equals("—") || curr.equals("-") || curr.isEmpty() || curr.equals("—%")) {
                            probability = 0.0;
                        } else {
                            // 正常有 % 的情況（如 "77.7%"）
                            String cleaned = curr.replace("%", "").trim();
                            try {
                                probability = Double.parseDouble(cleaned);
                            } catch (NumberFormatException e) {
                                System.err.println("無法解析的機率格式，預設為 0：" + curr);
                                probability = 0.0;
                            }
                        }
                        result.probabilities.add(probability);

                        result.fullText += "  目標利率：" + targetRate + "（" + action + "）\n";
                        result.fullText += "  目前概率：" + curr + "\n";
                        result.fullText += "  前日概率：" + prevDay + "\n";
                        result.fullText += "  前週概率：" + prevWeek + "\n\n";
                    }
                }
            }

            // 有效利率（用共用方法）
            HttpResponse<String> resp = callFredApi("fred/series/observations", "FEDFUNDS", fredApiKey);
            if (resp != null && resp.statusCode() == 200) {
                JsonNode obs = mapper.readTree(resp.body()).path("observations").get(0);
                String rateStr = obs.path("value").asText();
                if (!".".equals(rateStr) && !rateStr.isEmpty()) {
                    result.currentRate = Double.parseDouble(rateStr);
                    result.fullText += "目前有效利率：" + rateStr + "%\n";
                }
            }

            return result;

        } catch (Exception e) {
            result.fullText = "【聯準會利率期貨機率】抓取失敗，請稍後再試：" + e.getMessage();
            return result;
        }
    }
}