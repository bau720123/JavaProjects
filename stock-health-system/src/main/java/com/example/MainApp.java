package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.swing.SwingUtilities;  // 修正：SwingUtilities 來自 javax.swing
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
    private final FugleService service = new FugleService();
    private TextField symbolField;
    private PasswordField keyField;
    private Button queryBtn;
    private Button historyBtn;
    private TextArea resultArea;
    private ScrollPane chartPane;

    @Override
    public void start(Stage stage) {
        symbolField = new TextField("2330");
        symbolField.setPromptText("輸入股票代號 (e.g., 2330)");

        keyField = new PasswordField();
        keyField.setPromptText("輸入 Fugle API Key (隱私輸入)");

        queryBtn = new Button("查即時報價");
        queryBtn.setOnAction(e -> queryQuote());

        historyBtn = new Button("查歷史 K 線");
        historyBtn.setOnAction(e -> queryHistory());

        resultArea = new TextArea("歡迎使用台股健診系統，請輸入股票代號與 API Key。");
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(5);
        resultArea.setEditable(false);

        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(false);

        VBox root = new VBox(10, 
                             new Label("股票代號:"), symbolField,
                             new Label("Fugle API Key:"), keyField,
                             queryBtn, historyBtn, resultArea, chartPane);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統");
        stage.setMaximized(true);
        stage.setResizable(false);
        stage.show();
    }

    private void queryQuote() {
        String symbol = symbolField.getText().trim();
        String apiKey = keyField.getText().trim();
        if (symbol.isEmpty() || apiKey.isEmpty()) {
            showAlert("請輸入股票代號與 API Key");
            return;
        }

        CompletableFuture.supplyAsync(() -> service.fetchQuote(symbol, apiKey))
                .thenAccept(quote -> Platform.runLater(() -> {
                    if (quote != null) {
                        resultArea.setText(String.format("股票: %s (%s)\n開盤: %.0f | 最高: %.0f | 最低: %.0f | 收盤: %.0f\n均價: %.2f | 成交量: %d 股",
                                quote.symbol(), quote.name(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                                quote.avgPrice(), quote.tradeVolume()));
                    } else {
                        resultArea.setText("查詢失敗，請稍後再試。");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試: " + ex.getMessage()));
                    return null;
                });
    }

    private void queryHistory() {
        String symbol = symbolField.getText().trim();
        String apiKey = keyField.getText().trim();
        if (symbol.isEmpty() || apiKey.isEmpty()) {
            showAlert("請輸入股票代號與 API Key");
            return;
        }

        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, 10, apiKey))
                .thenAccept(candles -> Platform.runLater(() -> {
                    if (!candles.isEmpty()) {
                        chartPane.setContent(createLineChart(candles));
                        chartPane.setVisible(true);
                        resultArea.setText("歷史 K 線圖已載入 (近 10 日收盤價走勢)。");
                    } else {
                        resultArea.setText("歷史資料載入失敗，請稍後再試。");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試: " + ex.getMessage()));
                    return null;
                });
    }

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

    private Node createEmptyChartPanel() {
        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            XYSeriesCollection emptyDataset = new XYSeriesCollection();
            JFreeChart emptyChart = ChartFactory.createXYLineChart(" ", " ", " ", emptyDataset);
            swingNode.setContent(new ChartPanel(emptyChart));
        });
        return swingNode;
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}