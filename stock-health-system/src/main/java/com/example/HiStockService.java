package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 專門處理 HiStock 網站爬蟲的服務類
 * 統一 User-Agent、timeout、錯誤處理，方便後續擴充
 */
public class HiStockService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    /**
     * 融資融券餘額記錄（大盤整體）
     */
    public record MarginRecord(
            String date,              // yyyy-MM-dd
            double marginBalance,     // 融資餘額（億元）
            double marginChange,      // 融資增減（億元）
            long shortBalance,        // 融券餘額（張）
            long shortChange,         // 融券增減（張）
            double price,             // 指數收盤
            double priceChangePct,    // 漲跌幅 %
            double volume             // 成交金額（億元）
    ) {}

    /**
     * 取得大盤融資融券餘額資料（最近全部）
     * @return List<MarginRecord>，最新日期在最後
     */
    public List<MarginRecord> fetchMarginBalance() {
        try {
            Document doc = Jsoup.connect("https://histock.tw/stock/three.aspx?m=mg")
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements rows = doc.select("table.gvTB.gvTB_TWSE tbody tr");
            if (rows.size() <= 1) {
                return List.of(); // 無資料或只有標題
            }

            rows.remove(0); // 移除標題行

            List<MarginRecord> records = new ArrayList<>();

            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 8) continue;

                String dateStr = cells.get(0).text().trim(); // MM/DD
                String[] parts = dateStr.split("/");
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                LocalDate fullDate = LocalDate.of(LocalDate.now().getYear(), month, day);

                double marginBal = parseDouble(cells.get(1).text());
                double marginChg = parseDouble(cells.get(2).text());
                long shortBal = parseLong(cells.get(3).text());
                long shortChg = parseLong(cells.get(4).text());
                double price = parseDouble(cells.get(5).text());
                String pctStr = cells.get(6).text().trim().replace("%", "");
                double pricePct = pctStr.isEmpty() ? 0.0 : Double.parseDouble(pctStr);
                double vol = parseDouble(cells.get(7).text());

                records.add(new MarginRecord(
                        fullDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        marginBal, marginChg, shortBal, shortChg,
                        price, pricePct, vol
                ));
            }

            Collections.reverse(records); // 舊的在前

            return records;

        } catch (Exception e) {
            System.err.println("HiStock 融資融券資料抓取失敗：" + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    // 工具方法：安全解析帶逗號的數字
    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long parseLong(String text) {
        try {
            return Long.parseLong(text.trim().replace(",", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    // 未來擴充用（EPS、歷史本益比、三大法人等）
    // public List<EpsRecord> fetchEps(String symbol) { ... }
    // public List<PerRecord> fetchHistoricalPer(String symbol) { ... }
}