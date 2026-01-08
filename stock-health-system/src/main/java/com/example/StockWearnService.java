package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * 專門負責從 stock.wearn.com 抓取資料的服務類別
 * - 外資大盤淨空單
 * - 三大法人買賣超（個股）
 */
public class StockWearnService {

    /**
     * 抓取外資大盤淨空單歷史資料
     * @return 包含日期與淨空單口數的列表
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
                System.err.println("StockWearnService：找不到外資空單表格，網站可能改版");
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
                System.err.println("StockWearnService：沒有抓到任何外資空單資料");
                return result;
            }

            // 保持最新日期在前（與網站原始順序一致）
            for (int i = 0; i < originalDates.size(); i++) {
                result.add(new ForeignNetPosition(originalDates.get(i), originalNet.get(i)));
            }

        } catch (Exception e) {
            System.err.println("StockWearnService 抓取外資空單失敗：" + e.getMessage());
        }

        return result;
    }

    public record ForeignNetPosition(String date, int netPosition) {}

    // === 新增：三大法人買賣超（個股）===
    /**
     * 抓取指定股票的三大法人買賣超資料
     * @param symbol 股票代號
     * @return 列表元素為 [日期(yyyy-MM-dd), 投信買賣超, 自營商買賣超, 外資買賣超]，最新日期在前
     */
    public static List<InstitutionalTrade> fetchInstitutionalTrading(String symbol) {
        List<InstitutionalTrade> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://stock.wearn.com/netbuy.asp?kind=" + symbol)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            Elements tables = doc.select("table.mobile_img");
            if (tables.isEmpty()) {
                System.err.println("StockWearnService：三大法人買賣超表格不存在（股票代號 " + symbol + "）");
                return result;
            }

            Elements rows = tables.first().select("tbody tr");

            for (int i = 2; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("td");
                if (cells.size() < 4) continue;

                String rawDate = cells.get(0).text().trim();
                String trustStr = cells.get(1).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");
                String dealerStr = cells.get(2).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");
                String foreignStr = cells.get(3).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");

                int trust = trustStr.isEmpty() || trustStr.equals("-") ? 0 : Integer.parseInt(trustStr);
                int dealer = dealerStr.isEmpty() || dealerStr.equals("-") ? 0 : Integer.parseInt(dealerStr);
                int foreign = foreignStr.isEmpty() || foreignStr.equals("-") ? 0 : Integer.parseInt(foreignStr);

                String standardDate = convertTwDateToStandard(rawDate); // 轉成 yyyy-MM-dd

                result.add(new InstitutionalTrade(standardDate, trust, dealer, foreign));
            }

        } catch (Exception e) {
            System.err.println("StockWearnService 抓取三大法人買賣超失敗（" + symbol + "）：" + e.getMessage());
        }

        // 保持最新日期在前（與網站原始順序一致）
        return result;
    }

    public record InstitutionalTrade(String date, int trust, int dealer, int foreign) {}

    // 民國日期轉西元標準日期（yyyy-MM-dd）
    private static String convertTwDateToStandard(String twDate) {
        // twDate 格式如 "115/01/02"
        if (!twDate.matches("\\d{3}/\\d{2}/\\d{2}")) return twDate;
        int rocYear = Integer.parseInt(twDate.substring(0, 3));
        int year = 1911 + rocYear;
        return year + "-" + twDate.substring(4, 6) + "-" + twDate.substring(7, 9);
    }

    /**
     * 抓取大盤三大法人買賣超資料（單位：億）
     * @return 列表元素為 [日期(yyyy-MM-dd), 投信, 自營商, 外資]，最新日期在前
     */
    public static List<InstitutionalMarketTrade> fetchInstitutionalMarketTrading() {
        List<InstitutionalMarketTrade> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://stock.wearn.com/fundthree.asp")
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            Elements tables = doc.select("table.mobile_img");
            if (tables.size() < 2) {
                System.err.println("StockWearnService：大盤三大法人表格不足兩筆，網站可能改版");
                return result;
            }

            // 取第二筆 table（第一筆是其他資訊，第二筆才是三大法人買賣超）
            Element targetTable = tables.get(1);
            Elements rows = targetTable.select("tbody tr");

            // 從第 3 筆開始（跳過標題兩行）
            for (int i = 2; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("td");
                if (cells.size() < 4) continue;

                String rawDate = cells.get(0).text().trim(); // 115/01/07
                String trustText = cells.get(1).text().trim();
                String dealerText = cells.get(2).text().trim();
                String foreignText = cells.get(3).text().trim();

                // 解析數字（處理 + - 空格 和顏色 span）
                double trust = parseAmount(trustText);
                double dealer = parseAmount(dealerText);
                double foreign = parseAmount(foreignText);

                String standardDate = convertTwDateToStandard(rawDate);

                result.add(new InstitutionalMarketTrade(standardDate, trust, dealer, foreign));
            }

        } catch (Exception e) {
            System.err.println("StockWearnService 抓取大盤三大法人買賣超失敗：" + e.getMessage());
        }

        // 保持最新日期在前（與網站原始順序一致）
        return result;
    }

    public record InstitutionalMarketTrade(String date, double trust, double dealer, double foreign) {}

    // 輔助方法：解析金額文字（如 "+ 22.79" 或 "- 298.71"）
    private static double parseAmount(String text) {
        if (text == null || text.isEmpty() || text.equals("-")) return 0.0;
        return Double.parseDouble(text.replace("+", "").replace(" ", "").trim());
    }
}