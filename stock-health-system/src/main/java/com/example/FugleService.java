package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
                    return new Quote(
                        data.path("symbol").asText(),
                        data.path("name").asText(),
                        data.path("openPrice").asDouble(),
                        data.path("highPrice").asDouble(),
                        data.path("lowPrice").asDouble(),
                        data.path("closePrice").asDouble(),
                        data.path("avgPrice").asDouble(),
                        data.path("total").path("tradeVolume").asLong(0L)
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
                                node.path("open").asDouble(),
                                node.path("high").asDouble(),
                                node.path("low").asDouble(),
                                node.path("close").asDouble(),
                                node.path("volume").asLong(0L),
                                node.path("change").asDouble()  // [新增]：解析 change 漲跌額欄位 (若無值，預設 0.0)
                        ));
                    }
                    return candles;
                } else if (response.code() == 401 || response.code() == 404) {
                    return List.of();  // [修改]：API 失效時返回空 list，讓 UI 顯示錯誤提示
                }
            }
        } catch (IOException e) {
            return List.of();  // [修改]：API 失效時返回空 list，讓 UI 顯示錯誤提示
        }
        return List.of();
    }
}