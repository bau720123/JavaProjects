package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MainApp extends Application {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String BASE_URL = "https://api.fugle.tw/marketdata/v1.0/stock";

    private TextField symbolField;
    private PasswordField keyField;
    private Button queryBtn;
    private Label resultLabel;

    @Override
    public void start(Stage stage) {
        symbolField = new TextField("2330");
        symbolField.setPromptText("輸入股票代號 (e.g., 2330)");

        keyField = new PasswordField();
        keyField.setPromptText("輸入 Fugle API Key (隱私輸入)");

        queryBtn = new Button("查詢");
        queryBtn.setOnAction(e -> queryData());

        resultLabel = new Label("歡迎使用台股健診系統，請輸入股票代號與 API Key 並點查詢。");

        VBox root = new VBox(10, 
                             new Label("股票代號:"), symbolField,
                             new Label("Fugle API Key:"), keyField,
                             queryBtn, resultLabel);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 400, 250);
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統");
        stage.show();
    }

    private void queryData() {
        String symbol = symbolField.getText().trim();
        String apiKey = keyField.getText().trim();
        if (symbol.isEmpty() || apiKey.isEmpty()) {
            showAlert("請輸入股票代號與 API Key");
            return;
        }

        // 非同步請求，避免 UI 卡
        CompletableFuture.supplyAsync(() -> fetchQuote(symbol, apiKey))
                .thenAccept(quote -> Platform.runLater(() -> {
                    if (quote != null) {
                        resultLabel.setText(String.format("股票: %s (%s)\n開盤: %.0f | 最高: %.0f | 最低: %.0f | 收盤: %.0f\n均價: %.2f | 成交量: %d 股",
                                quote.symbol(), quote.name(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                                quote.avgPrice(), quote.tradeVolume()));
                    } else {
                        resultLabel.setText("查詢失敗，請稍後再試。");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        showAlert("系統異常，請稍後再試: " + ex.getMessage());
                        resultLabel.setText("查詢失敗，請稍後再試。");
                    });
                    return null;
                });
    }

    private Quote fetchQuote(String symbol, String apiKey) {
        try {
            String url = BASE_URL + "/intraday/quote/" + symbol;
            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-KEY", apiKey)  // 用輸入的 key
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode data = root; // 根據 Fugle 文件，response 是平坦結構
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
                    throw new IOException("API 錯誤: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}