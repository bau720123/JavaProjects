package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class FedWatchService {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String getProbability(String fredApiKey) {
        StringBuilder sb = new StringBuilder("【聯準會利率期貨隱含機率】\n");

        try {
            // 1. 抓 Investing.com
            Document doc = Jsoup.connect("https://www.investing.com/central-banks/fed-rate-monitor")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            Element dateElem = doc.getElementById("cardName_0");
            String meetingDate = (dateElem != null) ? dateElem.text().trim() : "未知";

            // 轉換日期格式
            try {
                String clean = meetingDate.replace(",", "").trim();
                if (clean.matches("^[A-Za-z]{3} \\d{1,2} \\d{4}$")) {
                    meetingDate = java.time.LocalDate.parse(clean,
                            java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.ENGLISH))
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
            } catch (Exception ignored) {}

            sb.append("下次會議：").append(meetingDate).append("\n");

            Elements tables = doc.select("table.genTbl.openTbl.fedRateTbl");
            if (!tables.isEmpty()) {
                Elements rows = tables.first().select("tbody tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 4 && cells.get(0).text().contains("-")) {
                        String target = cells.get(0).text().trim();
                        String curr = cells.get(1).text().trim();
                        String prevDay = cells.get(2).text().trim();
                        String prevWeek = cells.get(3).text().trim();

                        String action = getRateAction(target);
                        sb.append("• Target Rate：").append(target).append("（").append(action).append("）\n");
                        sb.append("  目前概率：").append(curr).append("\n");
                        sb.append("  前日概率：").append(prevDay).append("\n");
                        sb.append("  前週概率：").append(prevWeek).append("\n");
                    }
                }
            }

            // 2. 補目前有效利率（FRED）
            try {
                String url = String.format(
                    "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json&limit=1&sort_order=desc",
                    fredApiKey);
                HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode obs = mapper.readTree(resp.body()).path("observations").get(0);
                    String rate = obs.path("value").asText();
                    if (!".".equals(rate)) {
                        sb.append("目前有效利率：").append(rate).append("%\n");
                    }
                }
            } catch (Exception ignored) {}

            return sb.toString();

        } catch (Exception e) {
            return "【聯準會利率期貨機率】抓取失敗，請稍後再試\n" + e.getMessage();
        }
    }

    private static String getRateAction(String targetRate) {
        try {
            double lower = Double.parseDouble(targetRate.split(" - ")[0]);
            double upper = Double.parseDouble(targetRate.split(" - ")[1]);
            double midCurrent = 4.125;
            double diff = midCurrent - ((lower + upper) / 2);
            int codes = (int) Math.round(diff / 0.25);
            if (codes == 0) return "維持利率";
            return codes > 0 ? "降" + codes + "碼" : "升" + (-codes) + "碼";
        } catch (Exception e) {
            return "未知";
        }
    }
}