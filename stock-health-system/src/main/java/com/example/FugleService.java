package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
                        bids, // 新增：委買價
                        asks  // 新增：委賣價
                    );
                } else if (response.code() == 401 || response.code() == 404) {
                    throw new IOException("API Error: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("系統異常，請稍後再試", e);
        }
        return null;
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
}