package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 專門處理 HiStock 網站爬蟲的服務類
 * 統一 User-Agent、timeout、錯誤處理，方便後續擴充
 */
public class TaiFexService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

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

    public record TaifexQuote(
        String contract,       // e.g. "CDF016"
        double price,          // 現價
        long ttlvol,           // 總成交量
        String contractName,   // 合約名稱
        double updown          // 漲跌
    ) {
        // 方便建立「無資料」實例
        public static TaifexQuote empty() {
            return new TaifexQuote("", 0.0, 0L, "", 0.0);
        }

        public boolean isValid() {
            return price > 0 || updown != 0.0;
        }
    }

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 從台灣期貨交易所 API 取得指定 objId 下，符合 keyword 的單一合約完整報價
     * @param objId API 參數，例如 2 = 主力期貨報價
     * @param keyword 合約名稱，例如 "臺股期貨" 或 "台積電期貨"
     * @return 該合約的報價資料，若找不到或失敗回傳 empty()
     */
    public TaifexQuote fetchTaifexQuote(int objId, String keyword) {
        try {
            String url = "https://www.taifex.com.tw/cht/quotesApi/getQuotes?objId=" + objId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    for (JsonNode node : root) {
                        String contractName = node.path("contractName").asText("");
                        if (keyword.equals(contractName)) {
                            String contract = node.path("contract").asText("");
                            double price = parseDoubleSafe(node.path("price").asText("0"));
                            long ttlvol = parseLongSafe(node.path("ttlvol").asText("0"));
                            double updown = parseDoubleSafe(node.path("updown").asText("0"));

                            return new TaifexQuote(contract, price, ttlvol, contractName, updown);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("抓取台指官方報價失敗：" + e.getMessage());
        }
        return TaifexQuote.empty();
    }
}