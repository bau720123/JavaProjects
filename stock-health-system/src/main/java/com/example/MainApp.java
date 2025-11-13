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
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

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

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 使用 Fugle API 做資料存取
    private TextField symbolField; // 股票代號
    private PasswordField keyField; // Fugle API Key
    private Button queryBtn; // 查即時報價
    private Button historyBtn; // 查歷史 K 線
    private TextArea resultArea; // 文字顯示區塊
    private ScrollPane chartPane; // 圖表顯示區塊
    private BorderPane root;  // 讓 queryHistory() 可存取
    private ChartPanel currentChartPanel;  // 存取 ChartPanel 成員，允許多次 repaint（解決 SwingNode 延遲）
    private Stage primaryStage;  // 將 stage 升級為類別成員變數，讓 createLineChart 可存取（修 stage cannot find symbol）

    @Override
    public void start(Stage stage) {
        // 使用 BorderPane 作為根布局，以實現左中右三欄結構
        this.root = new BorderPane();  // 用 this.root 初始化成員變數（非局部）
        root.setPadding(new Insets(10));

        // 上方：輸入區（股票代號 + API Key），使用 HBox 水平排列
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(0, 0, 20, 0));  // 下方額外 20px 間距，隔開上方輸入與左側按鈕

        // 股票代號輸入（左側）
        VBox symbolVBox = new VBox(5);
        Label symbolLabel = new Label("股票代號：");
        symbolField = new TextField("");
        symbolField.setPromptText("請輸入股票代號");
        symbolField.setPrefWidth(155);  // 設定偏好寬度為
        symbolVBox.getChildren().addAll(symbolLabel, symbolField);

        // API Key 輸入（右側）
        VBox keyVBox = new VBox(5);
        Label keyLabel = new Label("Fugle API Key：");
        keyField = new PasswordField();
        keyField.setText("");
        keyField.setPromptText("請輸入 Fugle API Key");
        keyField.setPrefWidth(200);  // 設定偏好寬度為
        keyVBox.getChildren().addAll(keyLabel, keyField);

        inputBox.getChildren().addAll(symbolVBox, keyVBox); // 添加子節點到容器的操作，將兩個VBox（symbolVBox和keyVBox，各含Label + TextField）同時加入inputBox（HBox容器）的子節點列表中。
        root.setTop(inputBox); // 將inputBox（已含兩個VBox的HBox）設定為根容器root（BorderPane）的頂部區域。結果：輸入區固定在上方視窗，無論視窗resize，BorderPane會自動拉伸中間/底部內容。

        // 左側：功能按鈕垂直擺設，使用 VBox
        VBox buttonBox = new VBox(10);
        buttonBox.setAlignment(Pos.TOP_CENTER);
        buttonBox.setPrefWidth(150); // 左側固定寬度
        buttonBox.setPadding(new Insets(0, 0, 0, 10));  // 右側 10px 內邊距，避免太貼中間區塊

        // 查即時報價 按鈕
        queryBtn = new Button("查即時報價");
        queryBtn.setOnAction(e -> queryQuote());
        queryBtn.setPrefWidth(120); // 按鈕固定寬度

        // 查歷史 K 線 按鈕
        historyBtn = new Button("查歷史 K 線");
        historyBtn.setOnAction(e -> queryHistory());
        historyBtn.setPrefWidth(120);

        buttonBox.getChildren().addAll(queryBtn, historyBtn); // 添加子節點到容器的操作，將兩個Button（queryBtn和historyBtn）同時加入buttonBox（VBox容器）的子節點列表中。結果：按鈕垂直排列（查即時報價在上，查歷史 K 線在下），間距10px（來自new VBox(10)）。
        root.setLeft(buttonBox); // 將buttonBox（已含兩個Button的VBox）設定為根容器root（BorderPane）的左側區域。結果：按鈕區固定在左側視窗，寬度150px（來自setPrefWidth(150)），高度跟隨視窗拉伸，但內容不變形。

        // 中間：文字區塊（靠左）+ 圖表區塊（靠右），使用 HBox
        HBox centerBox = new HBox(10);
        centerBox.setAlignment(Pos.TOP_LEFT);  // 調整：改為 TOP_LEFT，讓內容頂左對齊

        // 文字區塊（中間靠左，寬度 200px，並向左微移以對齊紅線）
        resultArea = new TextArea("歡迎使用台股健診系統\nJava不是很好用\n請多見諒！"); // 可設定文字區塊預設文字
        resultArea.setWrapText(true); // 設定當文字超過欄位的寬度時是否自動換行
        resultArea.setPrefRowCount(10); // 但JavaFX布局系統的響應式設計（responsive layout）會讓其根據視窗大小的變化來自動延展其高
        resultArea.setEditable(false); // 設定該文字區塊可否修改
        resultArea.setPrefWidth(200); // 寬度維持 200px
        HBox.setMargin(resultArea, new Insets(0, 0, 0, 15));  // 新增：向左微移 20px，盡可能對齊上方區塊位置

        // 圖表區塊（中間靠右，寬度 700px）
        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(false); // 一開始不直接顯示圖表區塊
        chartPane.setPrefWidth(700); // 寬度維持 700px
        chartPane.setFitToWidth(true); // 啟用內容自動fit容器寬（Content Scaling，響應式延展/壓縮），當視窗窄時，內容壓縮（不水平滾動）；寬時，內容延展（但不超過原圖）

        centerBox.getChildren().addAll(resultArea, chartPane); // 添加子節點到容器的操作，將TextArea（resultArea）和ScrollPane（chartPane）同時加入centerBox（HBox容器）的子節點列表中。結果：中間內容水平排列（左：文字區200px，右：圖表區700px），間距10px（來自new HBox(10)）。
        root.setCenter(centerBox); // 將centerBox（已含TextArea和ScrollPane的HBox）設定為根容器root（BorderPane）的中間區域。結果：中間內容填滿剩餘視窗空間（寬=視窗寬 - left 150px - padding，高=視窗高 - top），無論視窗resize，BorderPane會自動拉伸中間區內容。

        // 設定場景
        Scene scene = new Scene(root, 1100, 700); // 初始寬度維持 1100px
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統");
        stage.setMaximized(false); // 初始視窗最大化
        stage.setResizable(true); // 允許調整大小
        this.primaryStage = stage;  // 初始化成員變數
        stage.show();
    }

    // 查詢即時報價邏輯
    private void queryQuote() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // Fugle API Key

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchQuote(symbol, apiKey))
                .thenAccept(quote -> Platform.runLater(() -> {
                    if (quote != null) {
                        StringBuilder sb = new StringBuilder(); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        sb.append(String.format("股票：%s（%s）\n昨日收盤價：%.0f\n開盤價：%.0f\n最高價：%.0f\n最低價：%.0f\n收盤價或現價：%.0f\n均價：%.2f\n總量：%d 股\n漲跌：%.0f\n幅度：%.2f\n",
                                quote.symbol(), quote.name(), quote.previousClose(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                                quote.avgPrice(), quote.tradeVolume(), quote.change(), quote.changePercent()));

                        // [委買價] 區段
                        sb.append("\n[委買價]\n\n");
                        for (BidAsk ba : quote.bids()) {
                            sb.append(String.format("    價格：%.0f\n    張數：%d\n\n", ba.price(), ba.size()));
                        }

                        // [委賣價] 區段
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
                    // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 查詢歷史 K 線邏輯
    private void queryHistory() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // Fugle API Key

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, 10, apiKey))
                .thenAccept(candles -> Platform.runLater(() -> {
                    // [Candle[date=2025-11-03, open=1510.0, high=1520.0, low=1490.0, close=1510.0, volume=30913479, change=10.0], Candle[date=2025-11-04, open=1520.0, high=1525.0, low=1495.0, close=1505.0, volume=35922791, change=-5.0], Candle[date=2025-11-05, open=1480.0, high=1485.0, low=1455.0, close=1460.0, volume=63423241, change=-45.0], Candle[date=2025-11-06, open=1470.0, high=1480.0, low=1465.0, close=1465.0, volume=29955147, change=5.0], Candle[date=2025-11-07, open=1460.0, high=1465.0, low=1455.0, close=1460.0, volume=23551721, change=-5.0], Candle[date=2025-11-10, open=1470.0, high=1485.0, low=1465.0, close=1475.0, volume=26579263, change=15.0], Candle[date=2025-11-11, open=1490.0, high=1495.0, low=1465.0, close=1465.0, volume=24857058, change=-10.0], Candle[date=2025-11-12, open=1470.0, high=1485.0, low=1460.0, close=1475.0, volume=24523385, change=10.0]]

                    if (!candles.isEmpty()) {
                        chartPane.setContent(createLineChart(candles));
                        chartPane.setFitToWidth(false);  // [新增]：關閉自動壓縮，讓 ChartPanel 自然寬度，溢出時滾動
                        chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // 水平滾動條自動出現（當寬度溢出時），確保用戶拖曳查看全圖，不切斷日期

                        // 延遲 setVisible，給 Swing 初始化時間
                        PauseTransition delayVisible = new PauseTransition(Duration.millis(400));
                        delayVisible.setOnFinished(e -> {
                            chartPane.setVisible(true);
                        });
                        delayVisible.play();
                        
                        // 原文字 + 歷史股價列表
                        StringBuilder sb = new StringBuilder("歷史 K 線圖已載入（近 10 日收盤價走勢）。\n\n歷史股價如下：\n\n"); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        for (Candle c : candles) {
                            sb.append(String.format("日期：%s\n開盤價：%.1f\n最高價：%.1f\n最低價：%.1f\n收盤價：%.1f\n成交量：%d\n漲跌：%.1f\n\n",
                                c.date(), c.open(), c.high(), c.low(), c.close(), c.volume(), c.change()));
                        }
                        resultArea.setText(sb.toString());  // 設定完整文字
                    } else {
                        resultArea.setText("歷史資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                    }
                }))
                .exceptionally(ex -> {
                    // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 創建線圖（使用 JFreeChart API）：這是個私有方法，返回一個 Node（JavaFX 的 UI 節點），用來嵌入 SwingNode 組件到 ScrollPane 中顯示 K 線圖。
    // 輸入：List<Candle> candles - 從 FugleService.fetchHistory() 取得的歷史 K 線資料（每個 Candle 含日期、開高低收等）。
    // 輸出：SwingNode - 包裝 JFreeChart 的 ChartPanel，讓圖表在 JavaFX 場景中渲染。
    // 目的：根據 candles 資料動態生成線圖（X 軸：日期，Y 軸：收盤價），支援滾動和 tooltip。
    private Node createLineChart(List<Candle> candles) {
        // 創建 SwingNode：JavaFX-Swing 橋接器，用來將 Swing 組件（如 JFreeChart 的 ChartPanel）嵌入 JavaFX 場景圖中。
        // JFreeChart 是基於 Swing 的圖表庫，需嵌入到 JavaFX 的 ScrollPane 中渲染。
        SwingNode swingNode = new SwingNode();

        // SwingUtilities.invokeLater(Runnable)：Swing 框架的執行緒工具，確保以下代碼在 Swing 的 EDT (Event Dispatch Thread) 中運行。
        // 目的：JFreeChart 的 chart 建構和 repaint 必須在 EDT，避免 "IllegalComponentStateException" 或渲染錯誤。
        // 這是混合 UI (JavaFX + Swing) 的標準實務，類似 JavaFX 的 Platform.runLater() 但針對 Swing。
        SwingUtilities.invokeLater(() -> {
            // DefaultCategoryDataset：JFreeChart 的資料集類別，用於類別型資料（如 X=日期字符串，Y=數值），支援多系列。
            // 日期是離散類別（非連續時間），CategoryAxis 只顯示有資料的點，解決假日空白問題。
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            // SimpleDateFormat：Java 文字處理 API，用來格式化 LocalDate 為字符串（X 軸標籤）。
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            // 迴圈填充 dataset：從 candles List 迭代，每個 Candle 轉日期字符串 + 收盤價。
            // 目的：建 X=日期類別，Y=close 數值系列 "收盤價走勢"。
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);  // 取得單日 Candle 記錄（從 Fugle API 解析）
                LocalDate localDate = c.date();  // Candle date() 返回 LocalDate - Java 時間 API，不可變日期

                // Date.from(Instant)：橋接 LocalDate 到舊 Date API（JFreeChart 需 Date 格式化）。
                // atStartOfDay(ZoneId.systemDefault())：加時區轉 Instant（台灣時間）。
                String dateStr = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

                dataset.addValue(c.close(), "收盤價走勢", dateStr);  // 用日期字符串作為類別（X 軸標籤），Y 為 close 收盤價
            }

            // JFreeChart 核心工廠，生成線圖（CategoryPlot 類型）。
            JFreeChart chart = ChartFactory.createLineChart("近 10 日 K 線 (收盤價)", "日期", "價格（元）", dataset, PlotOrientation.VERTICAL, true, true, false);
            
            // 切換字型以利解決亂碼問題
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);  // "Microsoft YaHei"：Windows 中文字體，BOLD 加粗，14pt 大小

            // TextTitle：JFreeChart 標題類別，取得並自訂圖表標題字體。
            TextTitle title = chart.getTitle();
            title.setFont(font);

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            // chart.getPlot()：強轉 plot 為 CategoryPlot。
            CategoryPlot plot = (CategoryPlot) chart.getPlot();  // [新增]：取得 CategoryPlot
            plot.getDomainAxis().setLabelFont(font);  // X 軸字體（"日期"）
            plot.getRangeAxis().setLabelFont(font);  // Y 軸字體（"價格（元）"）
            
            // Y 軸範圍動態調整（根據資料 min/max，類似 before 的行為，避免從 0 開始）
            // candles.stream().mapToDouble(Candle::close).min().orElse(0.0)：Stream API 計算收盤價最小值（method reference Candle::close）。
            double minClose = candles.stream().mapToDouble(Candle::close).min().orElse(0.0); // minClose：資料中的最小收盤價
            double maxClose = candles.stream().mapToDouble(Candle::close).max().orElse(0.0);  // maxClose：資料中最大收盤價
            double padding = (maxClose - minClose) * 0.05;  // 5% 緩衝空間（padding）：Y 軸上下留白，避免線貼邊

            // getRangeAxis()：Y 軸 ValueAxis，setLowerBound / setUpperBound 動態設範圍。
            plot.getRangeAxis().setLowerBound(Math.max(0, minClose - padding));  // 下限：min - padding，但不低於 0（股票價 >0）
            plot.getRangeAxis().setUpperBound(maxClose + padding);  // 上限：max + padding

            // LineAndShapeRenderer：CategoryPlot 的渲染器，控制線條/點/標籤樣式。
            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();  // 強轉 renderer 為線圖類型
            renderer.setSeriesItemLabelFont(0, font);  // （"收盤價走勢"）套中文字體

            // setSeriesToolTipGenerator(int series, CategoryToolTipGenerator generator)：為系列設定 tooltip 生成器（hover 提示）。
            // CategoryToolTipGenerator：介面，generateToolTip(CategoryDataset dataset, int row, int column) 返回字符串。
            // 目的：hover 點時顯示 "日期: 價格"，用完整日期避免年份歧義。
            renderer.setSeriesToolTipGenerator(0, (dataset1, row, column) -> {  // [修改]：tooltip 顯示日期/價格（CategoryDataset 版本）
                String category = (String) dataset1.getColumnKey(column);
                double y = dataset1.getValue(row, column).doubleValue();
                return category + ": " + y;  // 格式化 tooltip "2025-11-03: 1510.0"
            });
            
            // 系列名稱 "收盤價走勢" 字體防亂碼（JFreeChart 系列名稱在圖例顯示）
            // getLegend()：圖表圖例，setItemFont 套字體到所有項目。
            chart.getLegend().setItemFont(font);  // [新增]：將中文字體套用到圖例所有項目，解決系列名稱亂碼
            
            // CategoryAxis：X 軸類別軸，處理日期標籤位置。
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);  // 日期標籤垂直顯示（UP_90），避免擁擠（依需調整為 STANDARD 或 DOWN_90）

            currentChartPanel = new ChartPanel(chart);  // ChartPanel：JFreeChart 的 Swing 面板容器，包裝 chart 支援互動（zoom、tooltip）。
            int dynamicWidth = Math.max(800, candles.size() * 160);  // 加大倍率到 160 px/點（你的測試 160 勉強OK），10 日 ~1600px、20 日 ~3200px 自適應（確保最後日期全顯示，無切斷）
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));  // 寬動態，高固定 400px
            swingNode.setContent(currentChartPanel);

            // Timer：Swing 的計時器，單次延遲 200ms 觸發 ActionListener。
            // 目的：解決 SwingNode 嵌入 JavaFX 時的初始渲染延遲（社區常見 bug，JFreeChart 需要時間初始化 plot）。
            Timer timer = new Timer(200, e -> {
                currentChartPanel.revalidate();
                currentChartPanel.repaint();
                ((Timer) e.getSource()).stop();
            });
            timer.setRepeats(false);
            timer.start();
        });
        
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