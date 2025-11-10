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
            String params = String.format("?from=%s&to=%s&timeframe=D&fields=open,high,low,close,volume&sort=asc",
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
                                node.path("volume").asLong(0L)
                        ));
                    }
                    return candles;
                } else if (response.code() == 401 || response.code() == 404) {
                    return crawlYahooHistory(symbol, days);
                }
            }
        } catch (IOException e) {
            return crawlYahooHistory(symbol, days);
        }
        return List.of();
    }

    private List<Candle> crawlYahooHistory(String symbol, int days) {
        try {
            String url = "https://tw.stock.yahoo.com/quote/" + symbol + "/history?period1=" + (LocalDate.now().minusDays(days).toEpochDay() * 86400) + "&period2=" + (LocalDate.now().toEpochDay() * 86400) + "&interval=1d";
            Document doc = Jsoup.connect(url).get();
            Elements rows = doc.select("table tr");
            List<Candle> candles = new ArrayList<>();
            for (Element row : rows.subList(1, Math.min(rows.size(), days + 1))) {
                Elements cells = row.select("td");
                if (cells.size() >= 5) {
                    LocalDate date = LocalDate.parse(cells.get(0).text().trim(), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                    candles.add(new Candle(
                            date,
                            Double.parseDouble(cells.get(1).text().replace(",", "")),
                            Double.parseDouble(cells.get(2).text().replace(",", "")),
                            Double.parseDouble(cells.get(3).text().replace(",", "")),
                            Double.parseDouble(cells.get(4).text().replace(",", "")),
                            0L  // Yahoo 無 volume，設 0
                    ));
                }
            }
            return candles;
        } catch (Exception e) {
            return List.of();
        }
    }
}