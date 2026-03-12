package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 專門處理 StockQ 網站爬蟲的服務類
 * 目前主要用來抓取布蘭特原油即時報價
 */
public class StockqService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    /**
     * 布蘭特原油即時報價結果
     */
    public record BrentOilQuote(
            double price,           // 最新價格
            String priceText,       // 原始文字（保留單位或格式）
            boolean success
    ) {
        public static BrentOilQuote empty() {
            return new BrentOilQuote(0.0, "—", false);
        }

        public static BrentOilQuote of(double price, String priceText) {
            return new BrentOilQuote(price, priceText, true);
        }
    }

    /**
     * 從 StockQ 抓取布蘭特原油最新報價
     * 目標：第一個 <tr class="row2"> → 第一個 <td> 的內容
     */
    public BrentOilQuote fetchBrentOilLatest() {
        try {
            Document doc = Jsoup.connect("https://www.stockq.org/commodity/FUTRBOIL.php")
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // 1. 找到第一個 class="row2" 的 tr
            Element firstRow2 = doc.selectFirst("tr.row2");
            if (firstRow2 == null) {
                System.err.println("StockQ 找不到 tr.row2");
                return BrentOilQuote.empty();
            }

            // 2. 抓該 tr 內的第一個 td
            Elements tds = firstRow2.select("td");
            if (tds.isEmpty()) {
                System.err.println("StockQ row2 內無 td");
                return BrentOilQuote.empty();
            }

            Element priceTd = tds.first();
            String priceText = priceTd.ownText().trim();

            // 嘗試轉成數字（可能有逗號或單位）
            double price = parsePrice(priceText);

            return BrentOilQuote.of(price, priceText);

        } catch (Exception e) {
            System.err.println("StockQ 布蘭特原油爬蟲失敗：" + e.getMessage());
            return BrentOilQuote.empty();
        }
    }

    // 安全解析價格（處理逗號、可能的非數字）
    private double parsePrice(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String cleaned = text.replace(",", "").replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}