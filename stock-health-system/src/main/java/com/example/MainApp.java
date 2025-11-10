package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.Font;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 假設 FugleService 已定義，使用 Fugle API 做資料存取
    private TextField symbolField;
    private PasswordField keyField;
    private Button queryBtn;
    private Button historyBtn;
    private TextArea resultArea;
    private ScrollPane chartPane;

    @Override
    public void start(Stage stage) {
        // 使用 BorderPane 作為根布局，以實現左中右三欄結構
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 上方：輸入區（股票代號 + API Key），使用 HBox 水平排列
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(0, 0, 20, 0));  // 下方額外 20px 間距，隔開上方輸入與左側按鈕

        // 股票代號輸入（左側）
        VBox symbolVBox = new VBox(5);
        Label symbolLabel = new Label("股票代號：");
        symbolField = new TextField();
        symbolField.setPromptText("請輸入股票代號");
        symbolField.setPrefWidth(155);  // 設定偏好寬度為
        symbolVBox.getChildren().addAll(symbolLabel, symbolField);

        // API Key 輸入（右側）
        VBox keyVBox = new VBox(5);
        Label keyLabel = new Label("Fugle API Key：");
        keyField = new PasswordField();
        keyField.setPromptText("請輸入 Fugle API Key");
        keyField.setPrefWidth(200);  // 設定偏好寬度為
        keyVBox.getChildren().addAll(keyLabel, keyField);

        inputBox.getChildren().addAll(symbolVBox, keyVBox);
        root.setTop(inputBox);

        // 左側：功能按鈕垂直擺設，使用 VBox
        VBox buttonBox = new VBox(10);
        buttonBox.setAlignment(Pos.TOP_CENTER);
        buttonBox.setPrefWidth(150); // 左側固定寬度
        buttonBox.setPadding(new Insets(0, 0, 0, 10));  // 右側 10px 內邊距，避免太貼中間區塊

        queryBtn = new Button("查即時報價");
        queryBtn.setOnAction(e -> queryQuote());
        queryBtn.setPrefWidth(120); // 按鈕固定寬度

        historyBtn = new Button("查歷史 K 線");
        historyBtn.setOnAction(e -> queryHistory());
        historyBtn.setPrefWidth(120);

        buttonBox.getChildren().addAll(queryBtn, historyBtn);

        root.setLeft(buttonBox);

        // 中間：文字區塊（靠左） + 圖表區塊（靠右），使用 HBox
        HBox centerBox = new HBox(10);
        centerBox.setAlignment(Pos.TOP_LEFT);  // 調整：改為 TOP_LEFT，讓內容頂左對齊，匹配紅線位置

        // 文字區塊（中間靠左，寬度 200px，並向左微移以對齊紅線）
        resultArea = new TextArea("歡迎使用台股健診系統\nJava不是很好用\n請多見諒！");
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(10);
        resultArea.setEditable(false);
        resultArea.setPrefWidth(200); // 寬度維持 200px
        HBox.setMargin(resultArea, new Insets(0, 0, 0, 15));  // 新增：向左微移 20px，盡可能對齊紅線位置（依截圖調整，若需更多移位可改為 -30 或 -40）

        // 圖表區塊（中間靠右，寬度 600px）
        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(false);
        chartPane.setPrefWidth(600); // 寬度維持 600px
        chartPane.setFitToWidth(true);

        centerBox.getChildren().addAll(resultArea, chartPane);
        root.setCenter(centerBox);

        // 設定場景
        Scene scene = new Scene(root, 1100, 700); // 初始寬度維持 1100px
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統");
        stage.setMaximized(false);
        stage.setResizable(false); // 允許調整大小
        stage.show();
    }

    // 查詢即時報價邏輯（使用 Fugle API；若 API 取不到資料，可在 FugleService 中擴展爬蟲備案，如使用 Jsoup 解析 https://www.fugle.tw/intraday/{symbol}）
    private void queryQuote() {
        String symbol = symbolField.getText().trim();
        String apiKey = keyField.getText().trim();

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        CompletableFuture.supplyAsync(() -> service.fetchQuote(symbol, apiKey))
                .thenAccept(quote -> Platform.runLater(() -> {
                    if (quote != null) {
                        resultArea.setText(String.format("股票：%s（%s）\n開盤：%.0f\n最高：%.0f\n最低：%.0f\n收盤：%.0f\n均價：%.2f\n成交量：%d 股",
                                quote.symbol(), quote.name(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                                quote.avgPrice(), quote.tradeVolume()));
                    } else {
                        resultArea.setText("查詢失敗，請稍後再試\n若 API 不可用，請確認 FugleService 已加入爬蟲備案。");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 查詢歷史 K 線邏輯（使用 Fugle API；若 API 取不到資料，可在 FugleService 中擴展爬蟲備案，如使用 Jsoup 解析 https://www.fugle.tw/history/{symbol}）
    private void queryHistory() {
        String symbol = symbolField.getText().trim();
        String apiKey = keyField.getText().trim();

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, 10, apiKey))
                .thenAccept(candles -> Platform.runLater(() -> {
                    if (!candles.isEmpty()) {
                        chartPane.setContent(createLineChart(candles));
                        chartPane.setVisible(true);
                        resultArea.setText("歷史 K 線圖已載入（近 10 日收盤價走勢）。");
                    } else {
                        resultArea.setText("歷史資料載入失敗，請稍後再試\n若 API 不可用，請確認 FugleService 已加入爬蟲備案。");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 創建線圖（使用 JFreeChart API）
    private Node createLineChart(List<Candle> candles) {
        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            XYSeries series = new XYSeries("收盤價走勢");
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                series.add(i, c.close());
            }
            XYSeriesCollection dataset = new XYSeriesCollection(series);
            JFreeChart chart = ChartFactory.createXYLineChart("近 10 日 K 線 (收盤價)", "日期", "價格 (元)", dataset, PlotOrientation.VERTICAL, true, true, false);
            
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);
            TextTitle title = chart.getTitle();
            title.setFont(font);
            chart.getXYPlot().getDomainAxis().setLabelFont(font);
            chart.getXYPlot().getRangeAxis().setLabelFont(font);
            
            swingNode.setContent(new ChartPanel(chart));
        });
        return swingNode;
    }

    // 創建空圖表面板
    private Node createEmptyChartPanel() {
        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            XYSeriesCollection emptyDataset = new XYSeriesCollection();
            JFreeChart emptyChart = ChartFactory.createXYLineChart(" ", " ", " ", emptyDataset);
            swingNode.setContent(new ChartPanel(emptyChart));
        });
        return swingNode;
    }

    // 顯示警示
    private void showAlert(String msg) {
        Alert alert = new Alert(AlertType.ERROR, msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}