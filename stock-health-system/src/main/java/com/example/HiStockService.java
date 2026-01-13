package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        public double cheapPE;
        public double cheapPE_percent = 40;
        public double fairPE;
        public double fairPE_percent = 70;
        public double expensivePE;
        public double expensivePE_percent = 90;
        public int dataCount; // 有效數據筆數
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
            result.cheapPE = getPercentile(peList, result.cheapPE_percent);
            result.fairPE = getPercentile(peList, result.fairPE_percent);
            result.expensivePE = getPercentile(peList, result.expensivePE_percent);

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
        // 計算百分位對應的索引位置 (0-based index)
        // 例如：有 10 筆資料 (n=10)，求 50% 位置 => 0.5 * 9 = 4.5
        double position = (percentile / 100.0) * (n - 1);
        
        // 取整數部分作為下界索引
        int lowerIndex = (int) position;
        // 取小數部分作為插值權重
        double fraction = position - lowerIndex;

        // 邊界檢查：如果計算出的位置已經是最後一個元素（或超過），直接回傳最後一個值
        if (lowerIndex + 1 >= n) {
            return sortedList.get(n - 1);
        }

        // 取得下界與上界的數值
        double lower = sortedList.get(lowerIndex);
        double upper = sortedList.get(lowerIndex + 1);
        
        // 線性插值公式：下界值 + (差值 * 權重)
        return lower + fraction * (upper - lower);
    }

    public record FITXRealtime(
        double open,
        double high,
        double low,
        double change,          // 漲跌值（可正可負）
        String changeText,      // 原始文字 e.g. "▲39.0" 或 "▼12.5"
        double current,         // 現價 / 成交價
        long volume,            // 成交量(口)
        String updateTime,      // e.g. "2026.01.14 16:03"
        boolean success         // 是否成功取得資料
    ) {
        public static FITXRealtime empty() {
            return new FITXRealtime(0, 0, 0, 0, "無法取得", 0, 0, "未知", false);
        }
    }

    /**
     * 台指近
     */
    public FITXRealtime fetchFITXChange() {
        try {
            Document doc = Jsoup.connect("https://histock.tw/stock/module/function.aspx")
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .header("Origin", "https://histock.tw")
                .header("Referer", "https://histock.tw/")  // 模擬從首頁來
                .data("m", "stocktop2017")
                .data("no", "FITX")
                .ignoreContentType(true) // ← 關鍵！忽略 Content-Type 檢查
                .post();

            Element ul = doc.selectFirst("ul");
            if (ul == null) {
                return FITXRealtime.empty();
            }

            Map<String, String> dataMap = new LinkedHashMap<>();
            Elements lis = ul.select("li");
            for (Element li : lis) {
                Element titleElem = li.selectFirst(".ci_title");
                Element valueElem = li.selectFirst(".ci_value span");
                if (titleElem != null && valueElem != null) {
                    String title = titleElem.ownText().trim();
                    String value = valueElem.ownText().trim();
                    dataMap.put(title, value);
                }
            }

            // 解析更新時間（~ 後面的文字）
            String updateTime = "未知";
            String html = doc.body().html();
            int tildeIdx = html.indexOf("~");
            if (tildeIdx >= 0 && tildeIdx + 1 < html.length()) {
                String timePart = html.substring(tildeIdx + 1).trim();
                // 取到下一個非時間字元為止，或整段
                int end = timePart.indexOf("<");
                if (end > 0) timePart = timePart.substring(0, end);
                updateTime = timePart.trim();
            }

            // 提取各欄位（注意：key 必須與網站實際文字完全一致）
            double open   = parseDoubleSafe(dataMap.get("開盤"));
            double high   = parseDoubleSafe(dataMap.get("最高"));
            double low    = parseDoubleSafe(dataMap.get("最低"));
            String changeStr = dataMap.get("漲跌");
            double changeVal = parseChangeValue(changeStr);  // 自訂解析 ▲39.0 → 39.0
            double current = parseDoubleSafe(dataMap.get("指數"));  // 或 "成交"，視網站
            long volume   = parseLongSafe(dataMap.get("成交量(口)"));

            return new FITXRealtime(
                open, high, low,
                changeVal, changeStr != null ? changeStr : "無法取得",
                current, volume,
                updateTime,
                true
            );

        } catch (Exception e) {
            System.err.println("fetchFITXChange 失敗：" + e.getMessage());
            return FITXRealtime.empty();
        }
    }

    // 輔助解析方法
    private double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseChangeValue(String s) {
        if (s == null || s.isBlank()) return 0.0;
        String numPart = s.replaceAll("[▲▼+]", "").trim();
        try {
            return Double.parseDouble(numPart);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public double fetchTaifexTXUpdown() {
        try {
            String url = "https://www.taifex.com.tw/cht/quotesApi/getQuotes?objId=2";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                if (root.isArray() && root.size() > 0) {
                    for (JsonNode node : root) {
                        String contractName = node.path("contractName").asText("");
                        if ("臺股期貨".equals(contractName)) {
                            String updownStr = node.path("updown").asText("0");
                            // 移除逗號並轉成 double
                            return Double.parseDouble(updownStr.replace(",", ""));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("抓取台指官方盤中漲跌失敗：" + e.getMessage());
        }
        return 0.0;  // 失敗回傳 0
    }
}