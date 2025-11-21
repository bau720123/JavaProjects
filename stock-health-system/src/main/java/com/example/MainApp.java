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
import java.awt.Color;  // 顏色設定用於 MACD 圖表線條
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 使用 Fugle API 做資料存取
    private TextField symbolField; // 股票代號
    private PasswordField keyField; // Fugle API Key
    private TextField daysField; // 天數輸入欄位（共用給歷史 K 線、RSI、MACD）
    private Button queryBtn; // 查即時報價
    private Button historyBtn; // 查歷史 K 線
    private Button rsiBtn; // 查相對強弱指數按鈕
    private Button macdBtn; // 查移動平均線按鈕
    private TextArea resultArea; // 文字顯示區塊
    private ScrollPane chartPane; // 圖表顯示區塊
    private BorderPane root;  // 讓 queryHistory() 可存取
    private ChartPanel currentChartPanel;  // 存取 ChartPanel 成員，允許多次 repaint（解決 SwingNode 延遲）
    private Stage primaryStage;  // 將 stage 升級為類別成員變數，讓 createLineChart 可存取（修 stage cannot find symbol）

    // 在類別載入時讀取版本號
    private static String APP_VERSION = "Unknown";
    static {
        try (InputStream input = MainApp.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                APP_VERSION = prop.getProperty("app.version", "Unknown");
            }
        } catch (IOException e) {
            System.err.println("無法載入版本資訊: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage stage) {
        // 使用 BorderPane 作為根布局，以實現左中右三欄結構
        this.root = new BorderPane();  // 用 this.root 初始化成員變數（非局部）
        root.setPadding(new Insets(10));

        /* 上方版面配置（股票資訊輸入區），使用 HBox 水平排列 */
        HBox inputBox = new HBox(10); // 每個節點「水平」之間間隔 10 像素
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(0, 0, 20, 0));  // 下方額外 20px 間距，隔開上方輸入與左側按鈕

        // 股票代號輸入
        VBox symbolVBox = new VBox(5);
        Label symbolLabel = new Label("股票代號：");
        symbolField = new TextField("");
        symbolField.setPromptText("請輸入股票代號");
        symbolField.setPrefWidth(155);  // 設定偏好寬度
        symbolVBox.getChildren().addAll(symbolLabel, symbolField);

        // API Key 輸入
        VBox keyVBox = new VBox(5);
        Label keyLabel = new Label("Fugle API Key：");
        keyField = new PasswordField();
        keyField.setText("");
        keyField.setPromptText("請輸入 Fugle API Key");
        keyField.setPrefWidth(200);  // 設定偏好寬度
        keyVBox.getChildren().addAll(keyLabel, keyField);

        // 天數輸入
        VBox daysVBox = new VBox(5);
        Label daysLabel = new Label("天數：");
        daysField = new TextField("");
        daysField.setPromptText("天數");
        daysField.setPrefWidth(50); // 天數輸入欄位小寬度
        daysVBox.getChildren().addAll(daysLabel, daysField);

        inputBox.getChildren().addAll(symbolVBox, keyVBox, daysVBox); // 添加子節點到容器的操作，將三個VBox（symbolVBox、keyVBox、daysVBox）同時加入inputBox（HBox容器）的子節點列表中。
        root.setTop(inputBox); // 將inputBox（已含三個VBox的HBox）設定為根容器root（BorderPane）的頂部區域。結果：輸入區固定在上方視窗，無論視窗resize，BorderPane會自動拉伸中間/底部內容。

        /* 下方左側版面配置（功能列表），使用 VBox 垂直排列 */
        VBox buttonBox = new VBox(10); // 每個節點「垂直」之間間隔 10 像素
        buttonBox.setAlignment(Pos.TOP_CENTER);
        buttonBox.setPrefWidth(150); // 左側固定寬度
        buttonBox.setPadding(new Insets(0, 0, 0, 10));  // 右側 10px 內邊距，避免太貼中間區塊

        // 查即時報價
        queryBtn = new Button("查即時報價");
        queryBtn.setOnAction(e -> queryQuote());
        queryBtn.setPrefWidth(120); // 按鈕寬度調整為120

        // 查歷史 K 線
        historyBtn = new Button("查歷史 K 線");
        historyBtn.setOnAction(e -> queryHistory());
        historyBtn.setPrefWidth(120); // 按鈕寬度調整為120

        // 查相對強弱指數
        rsiBtn = new Button("查相對強弱指數");
        rsiBtn.setOnAction(e -> queryRSI());
        rsiBtn.setPrefWidth(120); // 按鈕寬度調整為120

        // 查移動平均線 按鈕
        macdBtn = new Button("查移動平均線");
        macdBtn.setOnAction(e -> queryMACD());
        macdBtn.setPrefWidth(120); // 按鈕寬度調整為120

        buttonBox.getChildren().addAll(queryBtn, historyBtn, rsiBtn, macdBtn); // 添加子節點到容器的操作，將 queryBtn、historyBtn、rsiBtn 和 macdBtn 加入 buttonBox
        root.setLeft(buttonBox); // 將buttonBox（已含四個元素的VBox）設定為根容器root（BorderPane）的左側區域。結果：按鈕區固定在左側視窗，寬度150px（來自setPrefWidth(150)），高度跟隨視窗拉伸，但內容不變形。

        /* 下方右側版面配置（文字跟圖表顯示區），使用 HBox 水平排列 */
        HBox centerBox = new HBox(10); // 每個節點「水平」之間間隔 10 像素
        centerBox.setAlignment(Pos.TOP_LEFT);  // 改為 TOP_LEFT，讓內容頂左對齊

        // 文字區塊
        resultArea = new TextArea("歡迎使用台股健診系統\n功能持續擴充中\n請多多支持！"); // 可設定文字區塊預設文字
        resultArea.setWrapText(true); // 設定當文字超過欄位的寬度時是否自動換行
        resultArea.setPrefRowCount(10); // 但JavaFX布局系統的響應式設計（responsive layout）會讓其根據視窗大小的變化來自動延展其高
        resultArea.setEditable(false); // 設定該文字區塊可否修改
        resultArea.setPrefWidth(200); // 寬度維持 200px
        HBox.setMargin(resultArea, new Insets(0, 0, 0, 15));  // 新增：向左微移 20px，盡可能對齊上方區塊位置

        // 圖表區塊
        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(false); // 一開始不直接顯示圖表區塊
        chartPane.setPrefWidth(700); // 寬度維持 700px
        chartPane.setFitToWidth(true); // 啟用內容自動fit容器寬（Content Scaling，響應式延展/壓縮），當視窗窄時，內容壓縮（不水平滾動）；寬時，內容延展（但不超過原圖）

        centerBox.getChildren().addAll(resultArea, chartPane); // 添加子節點到容器的操作，將TextArea（resultArea）和ScrollPane（chartPane）同時加入centerBox（HBox容器）的子節點列表中。結果：中間內容水平排列（左：文字區200px，右：圖表區700px），間距10px（來自new HBox(10)）。
        root.setCenter(centerBox); // 將centerBox（已含TextArea和ScrollPane的HBox）設定為根容器root（BorderPane）的中間區域。結果：中間內容填滿剩餘視窗空間（寬=視窗寬 - left 150px - padding，高=視窗高 - top），無論視窗resize，BorderPane會自動拉伸中間區內容。

        // 桌面視窗的設定
        Scene scene = new Scene(root, 1100, 700); // 初始寬度維持 1100px
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統（版本號：" + APP_VERSION + "）");
        stage.setMaximized(false); // 初始視窗最大化
        stage.setResizable(true); // 允許調整大小
        this.primaryStage = stage;  // 初始化成員變數


        // 使用 getClass().getResourceAsStream() 從 resources 資料夾讀取圖標
        InputStream iconStream = getClass().getResourceAsStream("/icon.png");

        if (iconStream != null) {
            // 將圖檔載入為 JavaFX Image 物件
            stage.getIcons().add(new javafx.scene.image.Image(iconStream));
        } else {
            // 如果找不到檔案，輸出警告（不會中斷程式）
            System.err.println("警告：找不到視窗圖標檔案 /icon.png");
        }

        // 監聽視窗寬度變化，動態調整 chartPane 寬度
        scene.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            resizeChartProportionally(); // 統一調用等比例縮放方法
        });

        // 取消自動聚焦，將焦點移到根容器，不然會預設聚焦在 "股票代號" 那個欄位
        Platform.runLater(() -> root.requestFocus());

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

                        // 委買價區段內容
                        sb.append("\n【委買價】\n\n");
                        for (BidAsk ba : quote.bids()) {
                            sb.append(String.format("    價格：%.0f\n    張數：%d\n\n", ba.price(), ba.size()));
                        }

                        // 委賣價區段內容
                        sb.append("【委賣價】\n\n");
                        for (BidAsk ba : quote.asks()) {
                            sb.append(String.format("    價格：%.0f\n    張數：%d\n\n", ba.price(), ba.size()));
                        }

                        resultArea.setText(sb.toString());
                    } else {
                        resultArea.setText("查詢失敗，請稍後再試\n若 API 不可用，請稍後再使用。");
                    }
                }))
                .exceptionally(ex -> {
                    // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 查詢歷史 K 線邏輯（使用共用 daysField）
    private void queryHistory() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // Fugle API Key
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        try {
            days = Integer.parseInt(daysText);
            if (days < 1) {
                showAlert("天數必須為 1 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（1 以上）");
            return;
        }

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, days, apiKey))
                .thenAccept(candles -> Platform.runLater(() -> {
                    if (!candles.isEmpty()) {
                        chartPane.setContent(createLineChart(candles));
                        // chartPane.setFitToWidth(false);  // 關閉自動壓縮，讓 ChartPanel 自然寬度，溢出時滾動
                        // chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // 水平滾動條自動出現（當寬度溢出時），確保用戶拖曳查看全圖，不切斷日期
                        resizeChartProportionally(); // 改用統一的等比例縮放方法

                        // 延遲 setVisible，給 Swing 初始化時間
                        PauseTransition delayVisible = new PauseTransition(Duration.millis(400));
                        delayVisible.setOnFinished(e -> {
                            chartPane.setVisible(true);
                        });
                        delayVisible.play();
                        
                        // 原文字 + 歷史股價列表
                        StringBuilder sb = new StringBuilder(String.format("歷史 K 線圖已載入（近 %d 日收盤價走勢）。\n\n歷史股價如下：\n\n", days)); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        for (Candle c : candles) {
                            sb.append(String.format("日期：%s\n開盤價：%.1f\n最高價：%.1f\n最低價：%.1f\n收盤價：%.1f\n成交量：%d\n漲跌：%.1f\n\n",
                                c.date(), c.open(), c.high(), c.low(), c.close(), c.volume(), c.change()));
                        }

                        // 計算區間最高價（所有 high 的 max）和最低價（所有 low 的 min）
                        // 用 Stream API：mapToDouble(Candle::high).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                        double maxHigh = candles.stream().mapToDouble(Candle::high).max().orElse(0.0);  // 區間最高價
                        double minLow = candles.stream().mapToDouble(Candle::low).min().orElse(0.0);  // 區間最低價

                        // 找出達到最高價的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> maxHighDates = candles.stream()
                                .filter(c -> c.high() == maxHigh)
                                .map(Candle::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String maxHighDateStr = maxHighDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        // 找出達到最低價的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> minLowDates = candles.stream()
                                .filter(c -> c.low() == minLow)
                                .map(Candle::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String minLowDateStr = minLowDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        sb.append(String.format("區間最高價：%.1f（%s）\n", maxHigh, maxHighDateStr));  // 格式化添加（%.1f 保留1位小數）
                        sb.append(String.format("區間最低價：%.1f（%s）\n", minLow, minLowDateStr));  // 格式化添加（%.1f 保留1位小數）

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

    // 查詢 RSI 邏輯（使用共用 daysField）
    private void queryRSI() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // Fugle API Key
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        try {
            days = Integer.parseInt(daysText);
            if (days < 1) {
                showAlert("天數必須為 1 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（1 以上）");
            return;
        }

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchRSI(symbol, days, apiKey))
                .thenAccept(rsiList -> Platform.runLater(() -> {
                    if (!rsiList.isEmpty()) {
                        // RSI 盤中預估（基於資料歸檔，Fugle 的 API最其碼要在今天收盤之後，才會進行歸檔，在那之前，是不會有今天的資料的）
                        LocalDate today = LocalDate.now();
                        boolean hasToday = rsiList.stream().anyMatch(r -> r.date().equals(today));

                        if (!hasToday) {
                            Quote quote = service.fetchQuote(symbol, apiKey);
                            List<Candle> history = service.fetchHistory(symbol, days, apiKey);

                            // 建立今日虛擬K棒
                            Candle todayCandle = new Candle(
                                today,
                                0, 0, 0, quote.closePrice(), 0L, 0.0
                            );

                            List<Candle> fullCandles = new ArrayList<>(history);
                            fullCandles.add(todayCandle);
                            fullCandles.sort(Comparator.comparing(Candle::date));

                            // 呼叫標準 Wilder RSI 計算
                            List<RSI> calculated = calculateWilderRSI(fullCandles, 6);

                            if (!calculated.isEmpty()) {
                                RSI todayRSI = calculated.get(calculated.size() - 1);
                                rsiList.add(todayRSI);
                                rsiList.sort(Comparator.comparing(RSI::date));
                            }
                        }

                        chartPane.setContent(createRSIChart(rsiList));
                        // chartPane.setFitToWidth(true);  // 關閉自動壓縮，讓 ChartPanel 自然寬度，溢出時滾動
                        // chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // 水平滾動條自動出現（當寬度溢出時），確保用戶拖曳查看全圖，不切斷日期
                        resizeChartProportionally(); // 改用統一的等比例縮放方法

                        // 延遲 setVisible，給 Swing 初始化時間
                        PauseTransition delayVisible = new PauseTransition(Duration.millis(400));
                        delayVisible.setOnFinished(e -> {
                            chartPane.setVisible(true);
                        });
                        delayVisible.play();
                        
                        // RSI 文字列表
                        StringBuilder sb = new StringBuilder(String.format("相對強弱指標 （RSI）已載入（近 %d 日 RSI 走勢）。\n\n強弱指數如下：\n\n", days)); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        for (RSI r : rsiList) {
                            sb.append(String.format("日期：%s\n指數：%.2f\n\n",
                                r.date(), r.rsi()));
                        }

                        // 計算區間最強勢（所有 rsi 的 max）和最弱勢（所有 rsi 的 min）
                        // 用 Stream API：mapToDouble(RSI::rsi).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                        double maxRsi = rsiList.stream().mapToDouble(RSI::rsi).max().orElse(0.0);  // 區間最強勢
                        double minRsi = rsiList.stream().mapToDouble(RSI::rsi).min().orElse(0.0);  // 區間最弱勢

                        // 找出達到最高 RSI 的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> maxRsiDates = rsiList.stream()
                                .filter(r -> r.rsi() == maxRsi)
                                .map(RSI::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String maxRsiDateStr = maxRsiDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        // 找出達到最低 RSI 的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> minRsiDates = rsiList.stream()
                                .filter(r -> r.rsi() == minRsi)
                                .map(RSI::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String minRsiDateStr = minRsiDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        sb.append(String.format("區間最強勢：%.2f（%s）\n", maxRsi, maxRsiDateStr));  // 格式化添加（%.2f 保留2位小數）
                        sb.append(String.format("區間最弱勢：%.2f（%s）\n", minRsi, minRsiDateStr));  // 格式化添加（%.2f 保留2位小數）

                        sb.append("\n* 超買與超賣：\n");
                        sb.append("  當RSI 顯示超買時（通常大於70），可能表示市場過熱，價格有回調的可能，是賣出訊號。 反之，當RSI 顯示超賣時（通常小於30），可能表示市場過冷，價格有上漲的潛力，是買入訊號。\n\n");
                        sb.append("* 市場趨勢：\n");
                        sb.append("  RSI 值越高，表示過去一段期間的上漲機率較大；值越小，則下跌機率較大。");

                        resultArea.setText(sb.toString());  // 設定完整文字
                    } else {
                        resultArea.setText("RSI 資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                    }
                }))
                .exceptionally(ex -> {
                    // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 標準 Wilder RSI 計算
    private List<RSI> calculateWilderRSI(List<Candle> candles, int period) {
        List<RSI> result = new ArrayList<>();
        if (candles.size() < period + 1) return result;

        double avgGain = 0.0;
        double avgLoss = 0.0;

        // 計算最初 period 天的平均漲跌
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // 第 period 天的 RSI
        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        double rsi = avgLoss == 0 ? 100 : 100.0 - (100.0 / (1.0 + rs));
        result.add(new RSI(candles.get(period).date(), Math.round(rsi * 100.0) / 100.0));

        // 之後使用 Wilder 平滑公式
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            double gain = change > 0 ? change : 0.0;
            double loss = change < 0 ? Math.abs(change) : 0.0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            rsi = avgLoss == 0 ? 100 : 100.0 - (100.0 / (1.0 + rs));

            result.add(new RSI(candles.get(i).date(), Math.round(rsi * 100.0) / 100.0));
        }
        return result;
    }

    // 查詢 MACD 邏輯（使用共用 daysField）
    private void queryMACD() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // Fugle API Key
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        try {
            days = Integer.parseInt(daysText);
            if (days < 1) {
                showAlert("天數必須為 1 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（1 以上）");
            return;
        }

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchMACD(symbol, days, apiKey))
                .thenAccept(macdList -> Platform.runLater(() -> {
                    if (!macdList.isEmpty()) {
                        // MACD 盤中預估（基於資料歸檔，Fugle 的 API最其碼要在今天收盤之後，才會進行歸檔，在那之前，是不會有今天的資料的）
                        LocalDate today = LocalDate.now();
                        boolean hasToday = macdList.stream().anyMatch(m -> m.date().equals(today));

                        if (!hasToday) {
                            Quote quote = service.fetchQuote(symbol, apiKey);
                            List<Candle> history = service.fetchHistory(symbol, days, apiKey);

                            // 建立今日虛擬K棒
                            Candle todayCandle = new Candle(
                                today,
                                0, 0, 0, quote.closePrice(), 0L, 0.0
                            );

                            List<Candle> fullCandles = new ArrayList<>(history);
                            fullCandles.add(todayCandle);
                            fullCandles.sort(Comparator.comparing(Candle::date));

                            // 呼叫標準 MACD 計算
                            List<MACD> calculated = calculateStandardMACD(fullCandles);

                            if (!calculated.isEmpty()) {
                                MACD todayMACD = calculated.get(calculated.size() - 1);
                                macdList.add(todayMACD);
                                macdList.sort(Comparator.comparing(MACD::date));
                            }
                        }

                        chartPane.setContent(createMACDChart(macdList));
                        // chartPane.setFitToWidth(true);  // 關閉自動壓縮，讓 ChartPanel 自然寬度，溢出時滾動
                        // chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // 水平滾動條自動出現（當寬度溢出時），確保用戶拖曳查看全圖，不切斷日期
                        resizeChartProportionally(); // 改用統一的等比例縮放方法

                        // 延遲 setVisible，給 Swing 初始化時間
                        PauseTransition delayVisible = new PauseTransition(Duration.millis(400));
                        delayVisible.setOnFinished(e -> {
                            chartPane.setVisible(true);
                        });
                        delayVisible.play();
                        
                        // MACD 文字列表
                        StringBuilder sb = new StringBuilder(String.format("移動平均指標 （MACD）已載入（近 %d 日MACD走勢）。\n\n移動平均指數如下：\n\n", days)); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        for (MACD m : macdList) {
                            sb.append(String.format("日期：%s\nMACD 線：%.2f\n信號線：%.2f\n\n",
                                m.date(), m.macdLine(), m.signalLine()));
                        }

                        // 計算區間最強勢（所有 macdLine 的 max）和最弱勢（所有 macdLine 的 min）
                        // 用 Stream API：mapToDouble(MACD::macdLine).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                        double maxMacd = macdList.stream().mapToDouble(MACD::macdLine).max().orElse(0.0);  // 區間最強勢
                        double minMacd = macdList.stream().mapToDouble(MACD::macdLine).min().orElse(0.0);  // 區間最弱勢

                        // 找出達到最高 MACD 的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> maxMacdDates = macdList.stream()
                                .filter(m -> m.macdLine() == maxMacd)
                                .map(MACD::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String maxMacdDateStr = maxMacdDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        // 找出達到最低 MACD 的所有日期，並按遞減（從最新到最舊）排序
                        List<LocalDate> minMacdDates = macdList.stream()
                                .filter(m -> m.macdLine() == minMacd)
                                .map(MACD::date)
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());
                        String minMacdDateStr = minMacdDates.stream()
                                .map(LocalDate::toString)
                                .collect(Collectors.joining("、"));

                        sb.append(String.format("區間最強勢：%.2f（%s）\n", maxMacd, maxMacdDateStr));  // 格式化添加（%.2f 保留2位小數）
                        sb.append(String.format("區間最弱勢：%.2f（%s）\n", minMacd, minMacdDateStr));  // 格式化添加（%.2f 保留2位小數）

                        // 新增：MACD 解釋文字（修正死亡交叉為賣出訊號）
                        sb.append("\n* 黃金交叉：\n");
                        sb.append("  當移動平均線（MACD）慢慢往上交叉信號線（signalLine）時發生。這通常被視為一個買進訊號，表示上漲趨勢可能增強。\n\n");
                        sb.append("* 死亡交叉：\n");
                        sb.append("  當移動平均線（MACD）慢慢往下交叉信號線（signalLine）時發生。這通常被視為一個賣出訊號，表示下跌趨勢可能增強。");

                        resultArea.setText(sb.toString());  // 設定完整文字
                    } else {
                        resultArea.setText("MACD 資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                    }
                }))
                .exceptionally(ex -> {
                    // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                    Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                    return null;
                });
    }

    // 標準 MACD 計算（12,26,9）
    private List<MACD> calculateStandardMACD(List<Candle> candles) {
        List<MACD> result = new ArrayList<>();
        if (candles.size() < 26 + 9) return result;

        int fast = 12, slow = 26, signal = 9;
        double[] close = candles.stream().mapToDouble(Candle::close).toArray();

        // EMA12, EMA26
        double emaFast = close[0];
        double emaSlow = close[0];
        double multiplierFast = 2.0 / (fast + 1);
        double multiplierSlow = 2.0 / (slow + 1);

        for (int i = 1; i < close.length; i++) {
            emaFast = (close[i] - emaFast) * multiplierFast + emaFast;
            emaSlow = (close[i] - emaSlow) * multiplierSlow + emaSlow;

            if (i >= slow - 1) {
                double dif = emaFast - emaSlow;

                // 從第 slow-1 天開始計算 DEA (Signal Line)
                if (i == slow - 1) {
                    result.add(new MACD(candles.get(i).date(), dif, dif)); // 第一筆 DEA = DIF
                } else {
                    MACD prev = result.get(result.size() - 1);
                    double dea = (dif - prev.signalLine()) * (2.0 / (signal + 1)) + prev.signalLine();
                    result.add(new MACD(candles.get(i).date(), dif, dea));
                }
            }
        }
        return result;
    }

    // 等比例調整圖表尺寸（統一方法，避免程式碼重複）
    // 參數：無（自動從 scene 和 chartPane 讀取當前尺寸）
    // 目的：根據視窗寬度計算等比例的圖表高度，並觸發 Swing 組件重繪
    private void resizeChartProportionally() {
        if (primaryStage == null || primaryStage.getScene() == null) {
            return; // 防止 stage 未初始化時調用
        }
        
        // 計算可用寬度：視窗寬度 - 左側按鈕區 - 文字區 - padding/margin
        double sceneWidth = primaryStage.getScene().getWidth();
        double availableWidth = sceneWidth - 400; // 左側 150px + 文字區 200px + 間距 50px
        
        // 設定最小寬度 500px，避免過窄
        double chartWidth = Math.max(500, availableWidth);
        
        // 等比例縮放：假設原始圖表是 16:9（可依需求調整 aspectRatio）
        double aspectRatio = 16.0 / 9.0; // 寬高比 16:9
        double chartHeight = chartWidth / aspectRatio;
        
        chartPane.setPrefWidth(chartWidth);
        chartPane.setPrefHeight(chartHeight); // 同步調整高度
        chartPane.setFitToWidth(false); // 關閉自動拉寬（改用等比例）
        chartPane.setFitToHeight(false); // 關閉自動拉高
        
        // 若圖表已載入，延遲 200ms 後觸發 Swing 組件重繪
        if (currentChartPanel != null) {
            SwingUtilities.invokeLater(() -> {
                // 調整 ChartPanel 實際尺寸（等比例）
                currentChartPanel.setPreferredSize(
                    new java.awt.Dimension((int)chartWidth, (int)chartHeight)
                );
                
                Timer timer = new Timer(200, e -> {
                    currentChartPanel.revalidate();
                    currentChartPanel.repaint();
                    ((Timer) e.getSource()).stop();
                });
                timer.setRepeats(false);
                timer.start();
            });
        }
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
            JFreeChart chart = ChartFactory.createLineChart("近 " + candles.size() + " 日 K 線 (收盤價)", "日期", "價格（元）", dataset, PlotOrientation.VERTICAL, true, true, false);
            
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

    // 創建 RSI 線圖（複製自 createLineChart 並調整）
    private Node createRSIChart(List<RSI> rsiList) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            for (int i = 0; i < rsiList.size(); i++) {
                RSI r = rsiList.get(i);
                LocalDate localDate = r.date();

                String dateStr = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

                dataset.addValue(r.rsi(), "RSI 指標", dateStr);  // 用日期字符串作為類別（X 軸標籤），Y 為 rsi 值
            }

            JFreeChart chart = ChartFactory.createLineChart("近 " + rsiList.size() + " 日 RSI 指標", "日期", "RSI 值 (0-100)", dataset, PlotOrientation.VERTICAL, true, true, false);
            
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);

            TextTitle title = chart.getTitle();
            title.setFont(font);

            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);
            
            // Y 軸範圍動態調整（RSI 通常 0-100，padding 5%）
            double minRsi = rsiList.stream().mapToDouble(RSI::rsi).min().orElse(0.0);
            double maxRsi = rsiList.stream().mapToDouble(RSI::rsi).max().orElse(100.0);
            double padding = (maxRsi - minRsi) * 0.05;

            plot.getRangeAxis().setLowerBound(Math.max(0, minRsi - padding));
            plot.getRangeAxis().setUpperBound(Math.min(100, maxRsi + padding));

            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesItemLabelFont(0, font);

            renderer.setSeriesToolTipGenerator(0, (dataset1, row, column) -> {
                String category = (String) dataset1.getColumnKey(column);
                double y = dataset1.getValue(row, column).doubleValue();
                return category + ": " + y;
            });
            
            chart.getLegend().setItemFont(font);
            
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);

            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            swingNode.setContent(currentChartPanel);

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

    // 創建 MACD 線圖（複製自 createRSIChart 並調整為兩系列）
    private Node createMACDChart(List<MACD> macdList) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            for (int i = 0; i < macdList.size(); i++) {
                MACD m = macdList.get(i);
                LocalDate localDate = m.date();

                String dateStr = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

                dataset.addValue(m.macdLine(), "MACD 線", dateStr);  // 系列 0：MACD 線
                dataset.addValue(m.signalLine(), "信號線", dateStr);  // 系列 1：信號線
            }

            JFreeChart chart = ChartFactory.createLineChart("近 " + macdList.size() + " 日 MACD 指標", "日期", "MACD 值", dataset, PlotOrientation.VERTICAL, true, true, false);
            
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);

            TextTitle title = chart.getTitle();
            title.setFont(font);

            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);
            
            // Y 軸範圍動態調整（基於 MACD 線 min/max，padding 5%）
            double minMacd = macdList.stream().mapToDouble(MACD::macdLine).min().orElse(0.0);
            double maxMacd = macdList.stream().mapToDouble(MACD::macdLine).max().orElse(0.0);
            double padding = (maxMacd - minMacd) * 0.05;

            plot.getRangeAxis().setLowerBound(minMacd - padding);
            plot.getRangeAxis().setUpperBound(maxMacd + padding);

            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesItemLabelFont(0, font);  // MACD 線字體
            renderer.setSeriesItemLabelFont(1, font);  // 信號線字體

            // 設定線條顏色：系列 0 (MACD) 紅色，系列 1 (信號線) 藍色
            renderer.setSeriesPaint(0, Color.RED);
            renderer.setSeriesPaint(1, Color.BLUE);

            // Tooltip：顯示日期 + 各系列值
            renderer.setSeriesToolTipGenerator(0, (dataset1, row, column) -> {
                String category = (String) dataset1.getColumnKey(column);
                double macdVal = dataset1.getValue(0, column).doubleValue();  // 系列 0
                double signalVal = dataset1.getValue(1, column).doubleValue();  // 系列 1
                return String.format("%s: MACD=%.2f, Signal=%.2f", category, macdVal, signalVal);
            });
            renderer.setSeriesToolTipGenerator(1, (dataset1, row, column) -> {
                // 信號線 tooltip 同上（避免重複）
                String category = (String) dataset1.getColumnKey(column);
                double macdVal = dataset1.getValue(0, column).doubleValue();
                double signalVal = dataset1.getValue(1, column).doubleValue();
                return String.format("%s: MACD=%.2f, Signal=%.2f", category, macdVal, signalVal);
            });
            
            chart.getLegend().setItemFont(font);
            
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);

            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            swingNode.setContent(currentChartPanel);

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

    // 顯示資訊（INFO 型，用於除錯 Alert）
    private void showInfoAlert(String msg) {
        Alert alert = new Alert(AlertType.INFORMATION, msg);
        alert.showAndWait();
    }

    // JVM 的要求：所有 Java 應用程式必須有一個 public static void main(String[] args) 作為啟動入口
    // JavaFX 的特殊性：JavaFX 應用程式繼承 Application 類別，但仍需要 main() 來橋接傳統 Java 啟動方式
    // mvn javafx:run 的關係：Maven 會讀取 pom.xml 中 javafx-maven-plugin 中 <mainClass> 的設定值，找到 MainApp.main() 並執行
    // 與 .exe 安裝檔的關係：跟 run 差不多，啟動時執行 com.example.MainApp.main()
    public static void main(String[] args) {
        launch(args);
    }
}