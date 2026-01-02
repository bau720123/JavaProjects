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

    /**
     * 季度EPS 記錄（只取最新兩年）
     */
    public record QuarterlyEps(
            int currentYear, // 最新年度
            int previousYear, // 上一年度
            double q1Current,
            double q2Current,
            double q3Current,
            double q4Current, // 若無則 0.0
            double q1Previous,
            double q2Previous,
            double q3Previous,
            double q4Previous,
            double annualCurrent, // 最新年累計（若未滿則為已公布累計）
            double annualPrevious // 上一年全年
    ) {}

    /**
     * 取得個股季度EPS（最新兩年）
     * @param symbol 股票代號（如 "2308"）
     * @return QuarterlyEps，若抓取失敗或無資料回傳 null
     */
    public QuarterlyEps fetchQuarterlyEps(String symbol) {
        try {
            String url = "https://histock.tw/stock/" + symbol + "/%E6%AF%8F%E8%82%A1%E7%9B%88%E9%A4%98";
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            Element table = doc.selectFirst("table.tb-stock.text-center.tbBasic");
            if (table == null) {
                return null;
            }

            Elements rows = table.select("tbody tr");
            if (rows.size() < 6) {
                return null;
            }

            // 年度標題（第一行）
            Elements headerCells = rows.get(0).select("th");
            if (headerCells.size() < 3) return null;

            int systemYear = LocalDate.now().getYear(); // 例如 2026

            // 判斷 headerCells 中是否有當年度（systemYear）的資料
            boolean hasCurrentYear = false;
            for (int i = 1; i < headerCells.size(); i++) { // 從 1 開始跳過「季別/年度」
                String text = headerCells.get(i).text().trim();
                if (!text.isEmpty()) {
                    try {
                        int year = Integer.parseInt(text);
                        if (year == systemYear) {
                            hasCurrentYear = true;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        // 非數字，忽略
                    }
                }
            }

            // 根據是否有當年度決定 indexLocate
            int indexLocate = hasCurrentYear ? 1 : 2;

            System.err.println("hasCurrentYear：" + hasCurrentYear + ", indexLocate：" + indexLocate);

            int currentYear = Integer.parseInt(headerCells.get(headerCells.size() - indexLocate).text().trim());
            int previousYear = Integer.parseInt(headerCells.get(headerCells.size() - (indexLocate + 1)).text().trim());

            System.err.println("systemYear：" + systemYear + ", currentYear：" + currentYear + ", previousYear：" + previousYear);

            double[] current = new double[5];
            double[] previous = new double[5];

            for (int i = 0; i < 5; i++) {
                Element row = rows.get(i + 1); // 從第 2 行開始（Q1）
                Elements cells = row.select("td");
                if (cells.size() < 2) continue;

                String currStr = cells.get(cells.size() - indexLocate).text().trim();
                current[i] = "-".equals(currStr) ? 0.0 : parseDouble(currStr);

                String prevStr = cells.get(cells.size() - (indexLocate + 1)).text().trim();
                previous[i] = "-".equals(prevStr) ? 0.0 : parseDouble(prevStr);
            }

            return new QuarterlyEps(
                    currentYear, previousYear,
                    current[0], current[1], current[2], current[3],
                    previous[0], previous[1], previous[2], previous[3],
                    current[4], previous[4]
            );

        } catch (Exception e) {
            System.err.println("HiStock EPS 抓取失敗 [" + symbol + "]：" + e.getMessage());
            return null;
        }
    }

    /**
     * 回傳歷史本益比分位資訊
     * 若爬蟲失敗，回傳 null，使用預設固定倍數並提示
     */
    public static class HistoricalPE {
        public double cheapPE;    // 20% 分位
        public double fairPE;     // 50% 分位
        public double expensivePE; // 80% 分位
        public int dataCount;    // 有效數據筆數
    }

    public HistoricalPE fetchHistoricalPE(String symbol) {
        String url = "https://histock.tw/stock/" + symbol + "/%E6%9C%AC%E7%9B%8A%E6%AF%94";
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            Elements rows = doc.select("table.tb-stock.tb-outline.tbBasic tbody tr");
            List<Double> peList = new ArrayList<>();

            for (Element row : rows) {
                if (row.hasClass("row") || row.hasClass("alt-row")) {  // 跳過表頭
                    Elements tds = row.select("td");
                    // 每行10個td，偶數索引(1,3,5,7,9) 才是本益比值 (Java索引從0開始，所以1,3,5,7,9)
                    System.err.println("tds.size()：" + tds.size());
                    for (int i = 1; i < tds.size(); i += 2) {
                        String text = tds.get(i).text().trim();
                        if (!text.equals("--") && text.matches("\\d+\\.\\d+")) {
                            System.err.println("歷史本益比：" + Double.parseDouble(text));
                            peList.add(Double.parseDouble(text));
                        }
                    }
                }
            }

            if (peList.size() < 10) {
                System.err.println("歷史本益比數據不足：" + peList.size());
                return null;
            }

            System.err.println("資料大小：" + peList.size());
            Collections.sort(peList);

            HistoricalPE result = new HistoricalPE();
            result.dataCount = peList.size();
            result.cheapPE = getPercentile(peList, 20);
            result.fairPE = getPercentile(peList, 50);
            result.expensivePE = getPercentile(peList, 80);

            return result;

        } catch (Exception e) {
            System.err.println("抓取歷史本益比失敗：" + e.getMessage());
            return null;
        }
    }

    /**
     * 線性插值計算分位數（標準統計方法）
     */
    private static double getPercentile(List<Double> sortedList, double percentile) {
        int n = sortedList.size();
        double position = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) position;
        double fraction = position - lowerIndex;

        if (lowerIndex + 1 >= n) {
            return sortedList.get(n - 1);
        }

        double lower = sortedList.get(lowerIndex);
        double upper = sortedList.get(lowerIndex + 1);
        return lower + fraction * (upper - lower);
    }
}