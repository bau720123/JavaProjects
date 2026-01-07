package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * 專門負責從 stock.wearn.com 抓取外資大盤淨空單資料的服務類別
 */
public class StockWearnService {

    /**
     * 抓取外資大盤淨空單歷史資料
     * @return 包含日期與淨空單口數的列表（最新日期在前，與網站表格順序一致）
     */
    public static List<ForeignNetPosition> fetchForeignNetPositions() {
        List<ForeignNetPosition> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://stock.wearn.com/taifexphoto.asp")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            Element table = doc.selectFirst("table.taifexphoto");
            if (table == null) {
                System.err.println("StockWearnService: 找不到外資空單表格，網站可能改版");
                return result;
            }

            List<String> originalDates = new ArrayList<>();
            List<Integer> originalNet = new ArrayList<>();

            Elements rows = table.select("tr:gt(1)");
            for (Element row : rows) {
                Elements tds = row.select("td");
                if (tds.size() >= 9) {
                    String dateStr = tds.get(0).text().trim();
                    String foreignStr = tds.get(5).text().trim().replace(",", "");

                    if (dateStr.matches("\\d{3}/\\d{2}/\\d{2}")) {
                        int rocYear = Integer.parseInt(dateStr.substring(0, 3));
                        int year = 1911 + rocYear;
                        String adDate = year + dateStr.substring(3).replace("/", "-"); // yyyy-MM-dd

                        try {
                            int net = Integer.parseInt(foreignStr);
                            originalDates.add(adDate);
                            originalNet.add(net);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (originalDates.isEmpty()) {
                System.err.println("StockWearnService: 沒有抓到任何外資空單資料");
                return result;
            }

            // 不反轉！保持最新日期在前（與網站原始順序一致）
            for (int i = 0; i < originalDates.size(); i++) {
                result.add(new ForeignNetPosition(originalDates.get(i), originalNet.get(i)));
            }

        } catch (Exception e) {
            System.err.println("StockWearnService 抓取外資空單失敗: " + e.getMessage());
        }

        return result;
    }

    public record ForeignNetPosition(String date, int netPosition) {}
}