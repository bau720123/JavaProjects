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

    // 回傳結果封裝（MainApp 會用到）
    public static class FedWatchResult {
        public String meetingDate = "未知";
        public List<String> labels = new ArrayList<>();  // 顯示用標籤（含降幾碼）
        public List<Double> probabilities = new ArrayList<>(); // 機率（純數字）
        public double currentRate = 0.0;  // FRED 有效利率
        public String fullText = "【聯準會利率期貨隱含機率】\n"; // 完整文字輸出
    }

    public static FedWatchResult getProbability(String fredApiKey) {
        FedWatchResult result = new FedWatchResult();

        // 在迴圈外先算一次 midCurrent，因為只需要呼叫一次
        double midCurrent = getCurrentRateMidpoint(fredApiKey);

        try {
            Document doc = Jsoup.connect("https://www.investing.com/central-banks/fed-rate-monitor")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            // 抓會議日期
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
            result.fullText += "下次會議：" + result.meetingDate + "\n";

            // 抓第一個 table（下次會議）
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
                        result.probabilities.add(Double.parseDouble(curr.replace("%", "")));

                        result.fullText += "• Target Rate：" + targetRate + "（" + action + "）\n";
                        result.fullText += "  目前概率：" + curr + "\n";
                        result.fullText += "  前日概率：" + prevDay + "\n";
                        result.fullText += "  前週概率：" + prevWeek + "\n";
                    }
                }
            }

            // FRED 目前有效利率
            try {
                String url = String.format(
                    "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json&limit=1&sort_order=desc",
                    fredApiKey);
                HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode obs = mapper.readTree(resp.body()).path("observations").get(0);
                    String rateStr = obs.path("value").asText();
                    if (!".".equals(rateStr) && !rateStr.isEmpty()) {
                        result.currentRate = Double.parseDouble(rateStr);
                        result.fullText += "目前有效利率：" + rateStr + "%\n";
                    }
                }
            } catch (Exception ignored) {}

            return result;

        } catch (Exception e) {
            result.fullText = "【聯準會利率期貨機率】抓取失敗，請稍後再試\n" + e.getMessage();
            return result;
        }
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

    // 自動計算目前區間中點（基於 DFEDTARU + DFEDTARL）
    private static double getCurrentRateMidpoint(String fredApiKey) {
        try {
            // 抓上界（DFEDTARU）
            String upperUrl = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=DFEDTARU&api_key=%s&file_type=json&limit=1&sort_order=desc",
                fredApiKey);
            HttpResponse<String> upperResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(upperUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            double upperBound = 0.0;
            if (upperResp.statusCode() == 200) {
                JsonNode upperObs = mapper.readTree(upperResp.body()).path("observations").get(0);
                String upperStr = upperObs.path("value").asText();
                if (!".".equals(upperStr)) upperBound = Double.parseDouble(upperStr);
            }

            // 抓下界（DFEDTARL）
            String lowerUrl = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=DFEDTARL&api_key=%s&file_type=json&limit=1&sort_order=desc",
                fredApiKey);
            HttpResponse<String> lowerResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(lowerUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            double lowerBound = 0.0;
            if (lowerResp.statusCode() == 200) {
                JsonNode lowerObs = mapper.readTree(lowerResp.body()).path("observations").get(0);
                String lowerStr = lowerObs.path("value").asText();
                if (!".".equals(lowerStr)) lowerBound = Double.parseDouble(lowerStr);
            }

            // 計算中點
            if (upperBound > 0 && lowerBound > 0) {
                double midPoint = (lowerBound + upperBound) / 2.0;
                return midPoint;
            }
        } catch (Exception e) {
            System.err.println("自動計算中點失敗，使用備援值 4.125：" + e.getMessage());
        }

        return 4.125; // 備援：舊值
    }
}