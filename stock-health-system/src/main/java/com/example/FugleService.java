package com.example;

// JSON 解析（Jackson）
import com.fasterxml.jackson.databind.JsonNode;  // Jackson 資料綁定模組中的 JsonNode 類別：用於表示 JSON 資料的節點結構，允許以物件導向方式讀取、遍歷和操作 API 回應的 JSON 資料（例如，Fugle API 返回的 bids/asks 陣列）。這是 Jackson 庫的核心類別，用來處理非結構化 JSON 而不需預先定義 POJO（Plain Old Java Object），適合動態解析 API 回應
import com.fasterxml.jackson.databind.ObjectMapper;  // Jackson 資料綁定模組中的 ObjectMapper 類別：Jackson 庫的核心工具，用於將 JSON 字串轉換為 Java 物件（如 JsonNode）或反之。這裡用來解析 Fugle API 的 HTTP 回應 body（body().string()），轉成可操作的 JSON 樹狀結構。若無此類別，我們需手動處理字串，易出錯。

// 網路請求（OkHttp）
import okhttp3.OkHttpClient;  // OkHttp 庫的核心客戶端類別：用於建立和管理 HTTP 連線。這裡用來發送 GET 請求到 Fugle API（如 fetchQuote 的 url），支援非同步、連線池和超時設定。OkHttp 是 Android/Java 的標準 HTTP 客戶端，高效且輕量。
import okhttp3.Request;  // OkHttp 庫的 Request 類別：用於建構 HTTP 請求物件。這裡用來設定 URL、Header（如 X-API-KEY）和方法（GET），然後傳給 OkHttpClient 執行。支援 Builder 模式，易於自訂。
import okhttp3.Response; // OkHttp 庫的 Response 類別：代表 HTTP 回應物件。這裡用來檢查狀態碼（isSuccessful()）、讀取 body（body().string()）和關閉資源（try-with-resources）。支援自動處理重試和錯誤。

// 捕捉例外錯誤
import java.io.IOException;  // Java I/O 套件的 IOException 類別：標準例外類別，用於處理 I/O 操作錯誤（如網路斷線、API 回應讀取失敗）。這裡在 try-catch 中捕捉，轉為 RuntimeException 讓上層 UI 處理。

// 日期處理（java.time）
import java.time.LocalDate;  // Java 時間 API (java.time) 中的 LocalDate 類別：不可變的日期類別（無時區），用於處理 fetchHistory 的日期範圍（如 from/to）。支援 minusDays() 等操作，取代舊的 Date/Calendar（易錯）。
import java.time.format.DateTimeFormatter;  // Java 時間 API 中的 DateTimeFormatter 類別：用於格式化/解析日期字串。這裡用 ISO_LOCAL_DATE 格式轉換 LocalDate 為 "yyyy-MM-dd" 字串，傳給 API 參數。

// 集合操作（java.util）
import java.util.List; // Java 集合框架的 List 介面（抽象）：有序、可重複元素的集合介面。這裡用作泛型（如 List<BidAsk>），讓方法返回靈活的資料結構。ArrayList 實現它。
import java.util.ArrayList;  // Java 集合框架中的 ArrayList 類別（具體）：動態陣列實現 List 介面，用於儲存可變大小的資料。這裡用來建構 bids/asks 的 List<BidAsk>，或 fetchHistory 的 candles 清單。

// 新增：RSI 記錄類別（簡潔記錄 date 和 rsi）
record RSI(LocalDate date, double rsi) {}

public class FugleService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

    public Quote fetchQuote(String symbol, String apiKey) {
        try {
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/intraday/quote/" + symbol;
            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-KEY", apiKey)
                    .build();

            // try-with-resources（Java 7+語法，自動關閉Response資源），所以無需再catch
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode data = root;  // 文件平坦結構

                    // 新增：解析 bids
                    JsonNode bidsNode = data.path("bids");
                    List<BidAsk> bids = new ArrayList<>();
                    for (JsonNode node : bidsNode) {
                        bids.add(new BidAsk(node.path("price").asDouble(), node.path("size").asLong()));
                    }

                    // 新增：解析 asks
                    JsonNode asksNode = data.path("asks");
                    List<BidAsk> asks = new ArrayList<>();
                    for (JsonNode node : asksNode) {
                        asks.add(new BidAsk(node.path("price").asDouble(), node.path("size").asLong()));
                    }

                    return new Quote(
                        data.path("symbol").asText(), // 股票代碼
                        data.path("name").asText(), // 股票簡稱
                        data.path("previousClose").asDouble(), // 昨日收盤價
                        data.path("openPrice").asDouble(), // 開盤價
                        data.path("highPrice").asDouble(), // 最高價
                        data.path("lowPrice").asDouble(), // 最低價
                        data.path("closePrice").asDouble(), // 收盤價
                        data.path("avgPrice").asDouble(), // 均價
                        data.path("total").path("tradeVolume").asLong(0L), // 總量
                        data.path("change").asDouble(), // 漲跌
                        data.path("changePercent").asDouble(), // 幅度
                        bids, // 委買價
                        asks  // 委賣價
                    );
                } else if (response.code() == 401 || response.code() == 403 || response.code() == 404) {
                    // 401 Unauthorized：需要用戶端進行身分驗證。
                    // 403 Forbidden：伺服器理解請求，但拒絕存取。
                    // 404 Not Found：請求的資源在伺服器上不存在。
                    throw new IOException("用戶端錯誤：" + response.code());
                } else {
                    // 301 Moved Permanently: 永久重新導向。
                    // 304 Not Modified: 資源未被修改，可使用快取版本。
                    // 500 Internal Server Error: 伺服器遇到一個未預期的情況。
                    throw new IOException("其它錯誤：" + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("系統異常，請稍後再試", e);
        }
    }

    public List<Candle> fetchHistory(String symbol, int days, String apiKey) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(days);
            String params = String.format("?from=%s&to=%s&timeframe=D&fields=open,high,low,close,volume,change&sort=asc",
                    from.format(formatter), to.format(formatter));
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/historical/candles/" + symbol + params;

            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-KEY", apiKey)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode dataArray = root.path("data");
                    List<Candle> candles = new ArrayList<>();
                    for (JsonNode node : dataArray) {
                        candles.add(new Candle(
                                LocalDate.parse(node.path("date").asText(), formatter),
                                node.path("open").asDouble(), // 開盤價
                                node.path("high").asDouble(), // 最高價
                                node.path("low").asDouble(), // 最低價
                                node.path("close").asDouble(), // 收盤價
                                node.path("volume").asLong(0L), // 成交量
                                node.path("change").asDouble()  // 漲跌
                        ));
                    }
                    return candles;
                } else if (response.code() == 401 || response.code() == 404) {
                    return List.of();  // API 失效時返回空 list，讓 UI 顯示錯誤提示
                }
            }
        } catch (IOException e) {
            return List.of();  // API 失效時返回空 list，讓 UI 顯示錯誤提示
        }
        return List.of();
    }

    // 新增：取得 RSI 指標
    public List<RSI> fetchRSI(String symbol, int days, String apiKey) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(days);
            // 修正：正確端點格式，symbol 放路徑，新增 timeframe=D
            String params = String.format("?from=%s&to=%s&timeframe=D&period=6", from.format(formatter), to.format(formatter));
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/technical/rsi/" + symbol + params;

            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-KEY", apiKey)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode dataArray = root.path("data");
                    List<RSI> rsiList = new ArrayList<>();
                    for (JsonNode node : dataArray) {
                        rsiList.add(new RSI(
                                LocalDate.parse(node.path("date").asText(), formatter),
                                node.path("rsi").asDouble()
                        ));
                    }
                    return rsiList;
                } else if (response.code() == 401 || response.code() == 404) {
                    return List.of();  // API 失效時返回空 list，讓 UI 顯示錯誤提示
                }
            }
        } catch (IOException e) {
            return List.of();  // API 失效時返回空 list，讓 UI 顯示錯誤提示
        }
        return List.of();
    }
}