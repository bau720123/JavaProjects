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
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;  // 保持：用於 JavaFX 延遲布局
import javafx.util.Duration;  // 保持：用於 PauseTransition.millis
import javax.swing.Timer;  // 保持：用於 Swing 線程延遲 repaint，解決初始不渲染（社區 fix）

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 假設 FugleService 已定義，使用 Fugle API 做資料存取
    private TextField symbolField;
    private PasswordField keyField;
    private Button queryBtn;
    private Button historyBtn;
    private TextArea resultArea;
    private ScrollPane chartPane;
    private BorderPane root;  // [新增這 1 行]：將 root 升級為類別成員變數，讓 queryHistory() 可存取
    private ChartPanel currentChartPanel;  // [新增這 1 行]：存取 ChartPanel 成員，允許多次 repaint（解決 SwingNode 延遲）
    private Stage primaryStage;  // [新增這 1 行]：將 stage 升級為類別成員變數，讓 createLineChart 可存取（修 stage cannot find symbol）

    @Override
    public void start(Stage stage) {
        // 使用 BorderPane 作為根布局，以實現左中右三欄結構
        this.root = new BorderPane();  // [修改這 1 行]：用 this.root 初始化成員變數（非局部）
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

        // 圖表區塊（中間靠右，寬度 700px）
        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(false);
        chartPane.setPrefWidth(700); // 寬度維持 700px
        chartPane.setFitToWidth(true);

        centerBox.getChildren().addAll(resultArea, chartPane);
        root.setCenter(centerBox);

        // 設定場景
        Scene scene = new Scene(root, 1100, 700); // 初始寬度維持 1100px
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統");
        stage.setMaximized(false); // 初始視窗最大化
        stage.setResizable(false); // 允許調整大小
        this.primaryStage = stage;  // [新增這 1 行]：初始化成員變數
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
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("股票：%s（%s）\n昨日收盤價：%.0f\n開盤價：%.0f\n最高價：%.0f\n最低價：%.0f\n收盤價或現價：%.0f\n均價：%.2f\n總量：%d 股\n漲跌：%.0f\n幅度：%.2f\n",
                                quote.symbol(), quote.name(), quote.previousClose(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                                quote.avgPrice(), quote.tradeVolume(), quote.change(), quote.changePercent()));

                        // 新增：[委買價] 區段
                        sb.append("\n[委買價]\n\n");
                        for (BidAsk ba : quote.bids()) {
                            sb.append(String.format("    價格：%.0f\n    張數：%d\n\n", ba.price(), ba.size()));
                        }

                        // 新增：[委賣價] 區段
                        sb.append("[委賣價]\n\n");
                        for (BidAsk ba : quote.asks()) {
                            sb.append(String.format("    價格：%.0f\n    張數：%d\n\n", ba.price(), ba.size()));
                        }

                        resultArea.setText(sb.toString());
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
        String symbol = symbolField.getText().trim();  // 保持原版：方法開頭定義 symbol（用於 API 呼叫）
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
                    // System.out.println("thenAccept：收到 candles，數量: " + candles.size());  // [註解]：除錯 log，DEMO 時移除
                    if (!candles.isEmpty()) {
                        chartPane.setContent(createLineChart(candles));  // 保持原版
                        chartPane.setFitToWidth(false);  // [新增]：關閉自動壓縮，讓 ChartPanel 自然寬度，溢出時滾動（解決切斷最後日期）
                        chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // [新增]：水平滾動條自動出現（當寬度溢出時），確保用戶拖曳查看全圖，不切斷日期
                        // [新增]：延遲 setVisible，給 Swing 初始化時間
                        PauseTransition delayVisible = new PauseTransition(Duration.millis(400));  // 保持：簡寫
                        delayVisible.setOnFinished(e -> {
                            chartPane.setVisible(true);
                            // System.out.println("queryHistory：setVisible(true) 完成");  // [註解]：除錯 log，DEMO 時移除
                            // [註解]：除錯 Alert（INFO 型，DEMO 時移除，避免彈窗）
                            // Platform.runLater(() -> showInfoAlert("圖表設定完成！檢查 console log（預期 '布局請求 + resize hack 完成'）。若仍空，確認 Fugle API key 或試 resize 視窗。"));
                        });
                        delayVisible.play();
                        
                        // [修改]：原文字 + 歷史股價列表
                        StringBuilder sb = new StringBuilder("歷史 K 線圖已載入（近 10 日收盤價走勢）。\n\n歷史股價如下：\n\n");
                        for (Candle c : candles) {
                            sb.append(String.format("日期：%s\n開盤價：%.1f\n最高價：%.1f\n最低價：%.1f\n收盤價：%.1f\n成交量：%d\n漲跌：%.1f\n\n",
                                c.date(), c.open(), c.high(), c.low(), c.close(), c.volume(), c.change()));
                        }
                        resultArea.setText(sb.toString());  // 設定完整文字
                    } else {
                        resultArea.setText("歷史資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");  // 保持原版，簡化提示
                        // [註解]：暫不加爬蟲 fallback（統一使用 Fugle API 做資料存取；若需，後續在 FugleService 加 crawlHistoryFallback 方法）
                        // List<Candle> fallbackCandles = service.crawlHistoryFallback(symbol, 10);
                        // if (!fallbackCandles.isEmpty()) { ... }
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
            // System.out.println("Swing 線程：開始建圖，candles 數量: " + candles.size());  // [註解]：除錯 log，DEMO 時移除
            // [修改]：改用 CategoryDataset，只用有資料的日期作為類別（砍掉非營業日空白），避免 DateAxis 自動補日期
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                LocalDate localDate = c.date();  // Candle date() 返回 LocalDate (從 println 確認)
                String dateStr = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));  // LocalDate 轉日期字符串
                dataset.addValue(c.close(), "收盤價走勢", dateStr);  // 用日期字符串作為類別（X 軸標籤），Y 為 close
            }
            JFreeChart chart = ChartFactory.createLineChart("近 10 日 K 線 (收盤價)", "日期", "價格 (元)", dataset, PlotOrientation.VERTICAL, true, true, false);  // [修改]：改用 createLineChart (CategoryPlot)，自動用 CategoryAxis，X 軸只顯示有資料日期
            
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);
            TextTitle title = chart.getTitle();
            title.setFont(font);
            
            CategoryPlot plot = (CategoryPlot) chart.getPlot();  // [新增]：取得 CategoryPlot
            plot.getDomainAxis().setLabelFont(font);  // X 軸字體
            plot.getRangeAxis().setLabelFont(font);   // Y 軸字體
            
            // [新增]：Y 軸範圍動態調整（根據資料 min/max，類似 before 的行為，避免從 0 開始）
            double minClose = candles.stream().mapToDouble(Candle::close).min().orElse(0.0);
            double maxClose = candles.stream().mapToDouble(Candle::close).max().orElse(0.0);
            double padding = (maxClose - minClose) * 0.05;  // 5% 緩衝空間
            plot.getRangeAxis().setLowerBound(Math.max(0, minClose - padding));  // 下限：min - padding，但不低於 0
            plot.getRangeAxis().setUpperBound(maxClose + padding);  // 上限：max + padding
            
            // [新增]：系列名稱字體防亂碼（Renderer 套用中文字體）
            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesItemLabelFont(0, font);  // 系列 0 用中文字體
            renderer.setSeriesToolTipGenerator(0, (dataset1, row, column) -> {  // [修改]：tooltip 顯示日期/價格（CategoryDataset 版本）
                String category = (String) dataset1.getColumnKey(column);  // 從 dataset1.getColumnKey(int column) 取日期字符串
                double y = dataset1.getValue(row, column).doubleValue();  // 從 dataset1.getValue(row, column) 取 double
                return category + ": " + y;  // 格式化 tooltip "2025-11-03: 1510.0"
            });
            
            // [新增]：圖例 (legend) 系列名稱 "收盤價走勢" 字體防亂碼（JFreeChart 系列名稱在圖例顯示）
            chart.getLegend().setItemFont(font);  // [新增]：將中文字體套用到圖例所有項目，解決系列名稱亂碼
            
            // [新增]：X 軸日期標籤調整（CategoryAxis 版本）
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.STANDARD);  // [新增]：日期標籤垂直顯示（UP_90），避免擁擠（依需調整為 STANDARD 或 DOWN_90）
            
            currentChartPanel = new ChartPanel(chart);  // 保持
            int dynamicWidth = Math.max(800, candles.size() * 160);  // [修改]：加大倍率到 160 px/點（你的測試 160 勉強OK），10 日 ~1600px、20 日 ~3200px 自適應（確保最後日期全顯示，無切斷）
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));  // [修改]：寬動態，高固定 400px
            swingNode.setContent(currentChartPanel);    // 保持
            
            // [新增]：立即 revalidate/repaint
            currentChartPanel.revalidate();
            currentChartPanel.repaint();
            
            // [新增]：雙重 invokeLater + Timer 延遲 200ms 再 repaint（社區 fix SwingNode 初始不渲染）
            SwingUtilities.invokeLater(() -> {
                currentChartPanel.revalidate();
                currentChartPanel.repaint();
            });
            Timer timer = new Timer(200, e -> {  // Swing Timer 延遲
                currentChartPanel.revalidate();
                currentChartPanel.repaint();
                // System.out.println("Swing Timer：延遲 repaint 完成");  // [註解]：除錯 log，DEMO 時移除
                ((Timer) e.getSource()).stop();  // 單次執行
            });
            timer.setRepeats(false);
            timer.start();
        });
        
        // [修改]：JavaFX 側延遲改 300ms，並針對 chartPane.requestLayout()（修編譯錯誤）
        PauseTransition pause = new PauseTransition(Duration.millis(300));  // [修改]：簡寫（需 import javafx.util.Duration）
        pause.setOnFinished(e -> {
            chartPane.requestLayout();  // 保持：強制 ScrollPane 布局
            root.requestLayout();       // 保持：頂層更新
            // [新增]：輕微 stage resize hack（模擬拖曳，觸發 SwingNode 重繪，無視覺影響）
            double currentWidth = primaryStage.getWidth();  // 保持
            primaryStage.setWidth(currentWidth + 1);        // 保持
            primaryStage.setWidth(currentWidth);            // 保持
            // System.out.println("JavaFX Pause：布局請求 + resize hack 完成");  // [註解]：除錯 log，DEMO 時移除
        });
        pause.play();
        
        return swingNode;
    }

    // 創建空圖表面板
    private Node createEmptyChartPanel() {
        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset emptyDataset = new DefaultCategoryDataset();
            JFreeChart emptyChart = ChartFactory.createLineChart(" ", " ", " ", emptyDataset);
            swingNode.setContent(new ChartPanel(emptyChart));
        });
        return swingNode;
    }

    // 顯示警示（ERROR 型）
    private void showAlert(String msg) {
        Alert alert = new Alert(AlertType.ERROR, msg);
        alert.showAndWait();
    }

    // [新增]：顯示資訊（INFO 型，用於除錯 Alert）
    private void showInfoAlert(String msg) {
        Alert alert = new Alert(AlertType.INFORMATION, msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}