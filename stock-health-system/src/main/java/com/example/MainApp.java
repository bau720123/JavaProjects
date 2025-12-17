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
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.example.FugleService.Bollinger;
import com.example.FugleService.SMA;
import com.example.FugleService.VolumeByPrice;

import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

import java.awt.Font;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javafx.animation.Timeline;

import java.awt.BasicStroke;

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 使用 Fugle API 做資料存取
    private TextField symbolField; // 股票代號
    private PasswordField keyField; // API Key
    private TextField daysField; // 天數輸入欄位（共用給歷史 K 線、RSI、MACD）
    private TextArea resultArea; // 文字顯示區塊
    private ScrollPane chartPane; // 圖表顯示區塊
    private BorderPane root; // 讓 queryHistory() 可存取
    private ChartPanel currentChartPanel; // 存取 ChartPanel 成員，允許多次 repaint
    private Stage primaryStage; // 將 stage 升級為類別成員變數，讓 createLineChart 可存取

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
            System.err.println("無法載入版本資訊：" + e.getMessage());
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
        symbolField.setPrefWidth(155);
        symbolVBox.getChildren().addAll(symbolLabel, symbolField);

        // API Key 輸入
        VBox keyVBox = new VBox(5);
        Label keyLabel = new Label("API Key：");
        keyField = new PasswordField();
        keyField.setText("");
        keyField.setPromptText("請輸入 API Key");
        keyField.setPrefWidth(200);
        keyVBox.getChildren().addAll(keyLabel, keyField);

        // 天數輸入
        VBox daysVBox = new VBox(5);
        Label daysLabel = new Label("天數：");
        daysField = new TextField("");
        daysField.setPromptText("天數");
        daysField.setPrefWidth(50);
        daysVBox.getChildren().addAll(daysLabel, daysField);

        inputBox.getChildren().addAll(symbolVBox, keyVBox, daysVBox); // 添加子節點到容器的操作
        root.setTop(inputBox); // 將 inputBox 設定為根容器的頂部區域。結果：輸入區固定在上方視窗，無論視窗resize，BorderPane會自動拉伸中間/底部內容。

        /* 下方左側版面配置（功能列表），使用 VBox 垂直排列 */
        VBox buttonBox = new VBox(10); // 每個節點「垂直」之間間隔 10 像素
        buttonBox.setAlignment(Pos.TOP_CENTER);
        buttonBox.setPrefWidth(150);
        // buttonBox.setPadding(new Insets(0, 0, 0, 10)); // 右側 10px 內邊距，避免太貼中間區塊
        buttonBox.setPadding(new Insets(0, 0, 0, 0));

        // 查即時報價
        Button queryBtn = new Button("查即時報價");
        queryBtn.setPrefWidth(120);
        queryBtn.setOnAction(e -> queryQuote());

        // 查分價量表
        Button queryVolumeBtn = new Button("查分價量表");
        queryVolumeBtn.setPrefWidth(120);
        queryVolumeBtn.setOnAction(e -> queryVolume());

        // 查歷史 K 線
        Button historyBtn = new Button("查歷史 K 線");
        historyBtn.setPrefWidth(120);
        historyBtn.setOnAction(e -> queryHistory());

        // 查簡單移動平均線
        Button smaBtn = new Button("查簡單移動平均線");
        smaBtn.setPrefWidth(120);
        smaBtn.setOnAction(e -> querySMA());

        // 查相對強弱指數
        Button rsiBtn = new Button("查相對強弱指數");
        rsiBtn.setPrefWidth(120);
        rsiBtn.setOnAction(e -> queryRSI());

        // 查移動平均線 按鈕
        Button macdBtn = new Button("查移動平均線");
        macdBtn.setPrefWidth(120);
        macdBtn.setOnAction(e -> queryMACD());

        // 查布林通道 按鈕
        Button bollingerBtn = new Button("查布林通道");
        bollingerBtn.setPrefWidth(120);
        bollingerBtn.setOnAction(e -> queryBollinger());

        // 查三大法人買賣超 按鈕
        Button institutionalBtn = new Button("查三大法人買賣超");
        institutionalBtn.setPrefWidth(120);
        institutionalBtn.setOnAction(e -> queryInstitutionalTrading());

        // 查外資大盤空單數 按鈕
        Button foreignNetBtn = new Button("查外資大盤空單數");
        foreignNetBtn.setPrefWidth(120);
        foreignNetBtn.setOnAction(e -> queryForeignNetPosition());

        // 查大盤加權指數 按鈕
        Button weightedBtn = new Button("查大盤加權指數");
        weightedBtn.setPrefWidth(120);
        weightedBtn.setOnAction(e -> queryWeighted());

        // 查聯準會利率 按鈕
        Button fedRateBtn = new Button("查聯準會利率");
        fedRateBtn.setPrefWidth(120);
        fedRateBtn.setOnAction(e -> queryFedRateProbability());

        // 查 VIX 恐慌指數 按鈕
        Button vixBtn = new Button("查 VIX 恐慌指數");
        vixBtn.setPrefWidth(120);
        vixBtn.setOnAction(e -> queryVix());

        buttonBox.getChildren().addAll(queryBtn, queryVolumeBtn, historyBtn, smaBtn, rsiBtn, macdBtn, bollingerBtn, institutionalBtn, foreignNetBtn, weightedBtn, fedRateBtn, vixBtn); // 添加子節點到容器的操作

        // 用 ScrollPane 包住 buttonBox
        ScrollPane buttonScrollPane = new ScrollPane(buttonBox);
        buttonScrollPane.setFitToWidth(true); // 讓內容寬度自動適應 ScrollPane
        buttonScrollPane.setFitToHeight(false); // 不要強制填滿高度
        buttonScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // 垂直滾輪：需要時出現
        buttonScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // 水平滾輪：永不顯示

        // 美化 ScrollPane 背景與邊框
        buttonScrollPane.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-padding: 0; " +
            "-fx-border-color: transparent;"
        );
        buttonScrollPane.setMaxHeight(Double.MAX_VALUE); // 允許垂直拉伸到父容器上限

        // 最後把 ScrollPane 放進 BorderPane.left
        root.setLeft(buttonScrollPane);

        /* 下方右側版面配置（文字跟圖表顯示區），使用 HBox 水平排列 */
        HBox centerBox = new HBox(10); // 每個節點「水平」之間間隔 10 像素
        centerBox.setAlignment(Pos.TOP_LEFT);  // 改為 TOP_LEFT，讓內容頂左對齊

        // 文字區塊
        resultArea = new TextArea("歡迎使用台股健診系統\n\n請在上方輸入股票代號與 API Key\n\n完成後點擊查詢左方相關功能\n\n任何系統回饋請寄EMAIL：\nbau720123@gmail.com\n\n"); // 可設定文字區塊預設文字
        resultArea.setWrapText(true); // 設定當文字超過欄位的寬度時是否自動換行
        resultArea.setPrefRowCount(10); // 但JavaFX布局系統的響應式設計（responsive layout）會讓其根據視窗大小的變化來自動延展其高
        resultArea.setEditable(false); // 設定該文字區塊可否修改
        resultArea.setPrefWidth(200); // 寬度維持 200px
        HBox.setMargin(resultArea, new Insets(0, 0, 0, 15));  // 新增：向左微移 20px，盡可能對齊上方區塊位置

        // 圖表區塊
        chartPane = new ScrollPane(createEmptyChartPanel());
        chartPane.setVisible(true); // 一開始不直接顯示圖表區塊
        chartPane.setPrefWidth(700); // 寬度維持 700px
        chartPane.setFitToWidth(true); // 啟用內容自動fit容器寬（Content Scaling，響應式延展/壓縮），當視窗窄時，內容壓縮（不水平滾動）；寬時，內容延展（但不超過原圖）
        chartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chartPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        centerBox.getChildren().addAll(resultArea, chartPane); // 添加子節點到容器的操作
        root.setCenter(centerBox); // 將centerBox（已含TextArea和ScrollPane的HBox）設定為根容器root（BorderPane）的中間區域。結果：中間內容填滿剩餘視窗空間（寬=視窗寬 - left 150px - padding，高=視窗高 - top），無論視窗resize，BorderPane會自動拉伸中間區內容。

        // 桌面視窗的設定
        Scene scene = new Scene(root, 1100, 700); // 初始寬度維持 1100px
        stage.setScene(scene);
        stage.setTitle("台股股票健診系統（版本號：" + APP_VERSION + "）");
        stage.setMaximized(false); // 初始視窗最大化
        stage.setResizable(true); // 允許調整大小
        this.primaryStage = stage; // 初始化成員變數

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

        // 重大事件自動提醒
        Platform.runLater(() -> {
            String eventMsg = MarketEventCalendar.getTodayEventMessage();
            if (eventMsg != null) {
                String original = resultArea.getText();
                resultArea.setText(original + eventMsg);

                // 套用紅色警示風格
                resultArea.setStyle("-fx-font-weight: bold; " +
                        "-fx-text-fill: #d32f2f; " +
                        "-fx-background-color: #ffebee; " +
                        "-fx-font-size: 14px;");

                // 閃爍效果
                Timeline blink = new Timeline(
                        new KeyFrame(Duration.seconds(0),   new KeyValue(resultArea.opacityProperty(), 1)),
                        new KeyFrame(Duration.seconds(0.5), new KeyValue(resultArea.opacityProperty(), 0.6)),
                        new KeyFrame(Duration.seconds(1),   new KeyValue(resultArea.opacityProperty(), 1))
                );
                blink.setCycleCount(6);
                blink.play();
            }
        });
    }

    // 查詢即時報價邏輯
    private void queryQuote() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchQuote(symbol, apiKey))
            .thenAccept(quote -> Platform.runLater(() -> {
                if (quote != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("股票代碼：%s\n股票名稱：%s\n\n上個收盤價：%.0f\n開盤價：%.0f\n最高價：%.0f\n最低價：%.0f\n現價：%.0f\n均價：%.2f\n漲跌：%.0f\n幅度：%.2f\n累計成交量：%d \n累計內盤成交量：%d \n累計外盤成交量：%d \n累計成交筆數：%d \n",
                            quote.symbol(), quote.name(), quote.previousClose(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                            quote.avgPrice(), quote.change(), quote.changePercent(), quote.tradeVolume(), quote.tradeVolumeAtBid(), quote.tradeVolumeAtAsk(), quote.transaction()));

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

                    // 柱狀圖
                    chartPane.setContent(createQuoteBarChart(quote));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
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

    // 即時報價專用柱狀圖
    private Node createQuoteBarChart(Quote quote) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // DefaultCategoryDataset：JFreeChart 的資料集類別，用於類別型資料（如 X=日期字符串，Y=數值），支援多系列。
            // 日期是離散類別（非連續時間），CategoryAxis 只顯示有資料的點，解決假日空白問題。
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            // 取整數
            int open = (int) Math.round(quote.openPrice());
            int high = (int) Math.round(quote.highPrice());
            int low = (int) Math.round(quote.lowPrice());
            int close = (int) Math.round(quote.closePrice());
            int avg = (int) Math.round(quote.avgPrice());

            // X 軸類別標籤名稱
            dataset.addValue(open, "價格", "開盤價");
            dataset.addValue(high, "價格", "最高價");
            dataset.addValue(close, "價格", "現價");
            dataset.addValue(avg, "價格", "均價");

            JFreeChart chart = ChartFactory.createBarChart(
                quote.name() + "（" + quote.symbol() + "）今日價格結構",
                "",
                "價格",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            CategoryPlot plot = chart.getCategoryPlot();

            // 設定 Y 軸範圍，給予適當邊界
            double max = Math.max(high, Math.max(close, avg));
            double min = Math.min(low, Math.min(open, avg));
            plot.getRangeAxis().setRange(min, max);

            // 動態設定台股最小跳動單位
            ((NumberAxis) plot.getRangeAxis())
                .setTickUnit(new NumberTickUnit(getTaiwanStockTickSize(close)));

            // 字型設定並且解決亂碼問題
            Font font = new Font("Microsoft YaHei", Font.BOLD, 16);
            chart.getTitle().setFont(font);
            plot.getDomainAxis().setTickLabelFont(font);
            plot.getDomainAxis().setLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);

            // 柱狀圖顏色設定
            BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, new Color(30, 144, 255)); // 經典藍
            renderer.setMaximumBarWidth(0.15);

            // 建立 ChartPanel 並設定大小
            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            swingNode.setContent(currentChartPanel);

            // 延遲 repaint，確保圖表正確顯示
            Timer timer = new Timer(200, e -> {
                currentChartPanel.revalidate();
                currentChartPanel.repaint();
                ((Timer) e.getSource()).stop();
            });
            timer.setRepeats(false);
            timer.start();
        });

        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 股價刻度
    private static double getTaiwanStockTickSize(double price) {
        if (price < 10)    return 0.01;
        if (price < 50)    return 0.05;
        if (price < 100)   return 0.1;
        if (price < 500)   return 0.5;
        if (price < 1000)  return 1.0;
        return 5.0;
    }

    // 查詢分價量表邏輯
    private void queryVolume() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key

        if (symbol.isEmpty()) {
            showAlert("請輸入 股票代號");
            return;
        }

        if (apiKey.isEmpty()) {
            showAlert("請輸入 Fugle API Key");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> service.fetchVolume(symbol, apiKey))
            .thenAccept(dataList -> Platform.runLater(() -> {
                if (dataList.isEmpty()) {
                    resultArea.setText("分價量表資料抓取失敗或無資料");
                    return;
                }
                
                // 同時抓即時報價
                Quote quote = service.fetchQuote(symbol, apiKey);
                
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("日期：%s\n", LocalDate.now())); // 無 date 欄位，用當日
                sb.append(String.format("股票代碼：%s\n股票名稱：%s\n\n開盤價：%.0f\n最高價：%.0f\n最低價：%.0f\n現價：%.0f\n\n",
                    quote.symbol(), quote.name(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice()));

                // 依序顯示資料
                for (VolumeByPrice v : dataList) {
                    sb.append(String.format("成交價：%.1f\n累計成交量：%d\n內盤累計成交量：%d\n外盤累計成交量：%d\n\n",
                        v.price(), v.volume(), v.volumeAtBid(), v.volumeAtAsk()));
                }

                // 科學化分析
                sb.append("【分價量表分析】\n\n");
                if (!dataList.isEmpty()) {
                    // 計算 POC
                    VolumeByPrice poc = dataList.stream()
                        .max(Comparator.comparingLong(VolumeByPrice::volume))
                        .orElse(dataList.get(0));
                    
                    double askPct = poc.volume() > 0 ? (poc.volumeAtAsk() * 100.0 / poc.volume()) : 0;
                    
                    sb.append(String.format("POC（最大成交量價位）：%.1f 元（成交 %d 張，外盤比例 %.1f%%）\n",
                        poc.price(), poc.volume(), askPct));
                    
                    if (askPct > 70) {
                        sb.append("\n強力支撐區！多方積極承接，建議觀察守住此價可偏多操作！\n");
                    } else if (askPct < 30) {
                        sb.append("\n強力壓力區！賣壓沉重，需警惕繼續下殺！\n");
                    } else {
                        sb.append("\n中性換手區，價格易在此震盪盤整！\n");
                    }
                    
                    // 現價與 POC 關係
                    double currentPrice = quote.closePrice();
                    if (currentPrice > poc.price() * 1.005) {
                        sb.append("\n現價高於 POC：多頭控盤較強，偏多格局！\n");
                    } else if (currentPrice < poc.price() * 0.995) {
                        sb.append("\n現價低於 POC：空頭控盤較強，偏空格局！\n");
                    } else {
                        sb.append("\n現價接近 POC：多空平衡，易橫盤整理！\n");
                    }
                    
                    // 低價區內盤重警訊
                    VolumeByPrice lowPriceZone = dataList.stream()
                        .filter(v -> v.price() <= quote.lowPrice() + 5)
                        .max(Comparator.comparingLong(VolumeByPrice::volumeAtBid))
                        .orElse(null);
                    
                    if (lowPriceZone != null && lowPriceZone.volumeAtBid() > lowPriceZone.volumeAtAsk() * 2) {
                        sb.append("\n低價區內盤偏重：賣壓尚未完全釋放，需注意下殺風險！\n");
                    }
                }
                
                resultArea.setText(sb.toString());

                // 分價量圖
                chartPane.setContent(createVolumeProfileChart(dataList, quote));
                resizeChartProportionally(); // 改用統一的等比例縮放方法
            }));
    }

    // 分價量表圖
    private Node createVolumeProfileChart(List<VolumeByPrice> dataList, Quote quote) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // DefaultCategoryDataset：JFreeChart 的資料集類別，用於類別型資料（如 X=日期字符串，Y=數值），支援多系列。
            // 日期是離散類別（非連續時間），CategoryAxis 只顯示有資料的點，解決假日空白問題。
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            long maxVolume = dataList.stream()
                    .mapToLong(VolumeByPrice::volume)
                    .max()
                    .orElse(1000L);
            double xMax = maxVolume * 1.3;  // 多留空間

            //宣告 DecimalFormat，用來統一價格格式並避免浮點誤差
            DecimalFormat df = new DecimalFormat("#0.0");  // 強制顯示一位小數，如 915.0

            // 依據將價格加入Y軸
            for (VolumeByPrice v : dataList) {
                String priceKey = df.format(v.price());  // 這裡使用 df，統一格式
                dataset.addValue(v.volumeAtAsk(), "外盤累積成交量", priceKey);
                dataset.addValue(v.volumeAtBid(), "內盤累積成交量", priceKey);
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(
                null,
                "",
                "成交量",
                dataset,
                PlotOrientation.HORIZONTAL,
                false,
                true,
                false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            CategoryPlot plot = chart.getCategoryPlot();
            // plot.setBackgroundPaint(new Color(30, 30, 30));
            plot.setRangeGridlinePaint(new Color(80, 80, 80)); // 格線顏色

            StackedBarRenderer renderer = (StackedBarRenderer) plot.getRenderer();
            renderer.setDefaultStroke(new BasicStroke(1.5f));
            renderer.setSeriesPaint(0, new Color(255, 230, 0));   // 外盤：亮黃
            renderer.setSeriesPaint(1, new Color(200, 200, 200)); // 內盤：淺灰
            renderer.setBarPainter(new StandardBarPainter());

            // === 強制顯示所有價位標籤 ===
            CategoryAxis domainAxis = plot.getDomainAxis();
            domainAxis.setCategoryMargin(0.1);
            domainAxis.setLowerMargin(0.02);
            domainAxis.setUpperMargin(0.02);
            domainAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
            domainAxis.setTickLabelsVisible(true);
            // domainAxis.setTickLabelPaint(new Color(220, 220, 220));  // 預設顏色（萬一有漏的）

            // 標記 高 / 開 / 現 + 顏色
            for (VolumeByPrice v : dataList) {
                String key = df.format(v.price());

                if (Math.abs(v.price() - quote.highPrice()) < 0.1) {
                    // 最高價
                    domainAxis.setTickLabelPaint(key, Color.RED);
                } else if (Math.abs(v.price() - quote.openPrice()) < 0.1) {
                    // 開盤價
                    domainAxis.setTickLabelPaint(key, Color.CYAN);
                } else if (Math.abs(v.price() - quote.closePrice()) < 0.1) {
                    // 現價
                    domainAxis.setTickLabelPaint(key, Color.GREEN);
                    // 現價整條變藍色高亮
                    renderer.setSeriesPaint(0, new Color(50, 180, 255));   // 外盤藍
                    renderer.setSeriesPaint(1, new Color(120, 200, 255));  // 內盤淡藍
                } else if (v.price() == quote.lowPrice()) {
                    // 最低價
                    domainAxis.setTickLabelPaint(key, Color.BLUE);
                } else {
                    domainAxis.setTickLabelPaint(key, Color.BLACK);
                }
            }

            // X軸：成交量標題明確顯示
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setLabel("成交量");
            rangeAxis.setLabelFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
            rangeAxis.setLabelPaint(Color.BLACK);
            rangeAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
            rangeAxis.setRange(0, xMax);

            // 建立 ChartPanel 並設定大小
            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            swingNode.setContent(currentChartPanel);

            // 延遲 repaint，確保圖表正確顯示
            Timer timer = new Timer(200, e -> {
                currentChartPanel.revalidate();
                currentChartPanel.repaint();
                ((Timer) e.getSource()).stop();
            });
            timer.setRepeats(false);
            timer.start();
        });


        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 通用圖表
    private Node createCommonLineChart(
            List<?> data, // 實際資料面
            String titlePrefix, // 標題前綴
            String yPrefix, // Y軸說明
            Color lineColor, // 線條顏色
            ToDoubleFunction<Object> valueExtractor, // 提取數值
            ToLocalDateFunction<Object> dateExtractor, // 提取日期字串
            ToDoubleFunction<Object> secondaryValueExtractor,
            String secondarySeriesName,
            Color secondaryColor
    ) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // DefaultCategoryDataset：JFreeChart 的資料集類別，用於類別型資料（如 X=日期字符串，Y=數值），支援多系列。
            // 日期是離散類別（非連續時間），CategoryAxis 只顯示有資料的點，解決假日空白問題。
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            // SimpleDateFormat：Java 文字處理 API，用來格式化 LocalDate 為字符串（X 軸標籤）。
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            /**
             * 重要！千萬別用 Double.MIN_VALUE 初始化 maxValue！
             * Double.MIN_VALUE 是 4.9E-324（最小正數），不是「最小的 double」！
             * 當資料全是負數時，maxValue 永遠不會被更新！
             * 正確做法：用 Double.NEGATIVE_INFINITY
             */
            double minValue = Double.POSITIVE_INFINITY;
            double maxValue = Double.NEGATIVE_INFINITY;

            // 迴圈填充 dataset：從 data List 迭代，每個 data 轉日期字符串 + 收盤價。
            // 目的：建 X=日期類別，Y=close 數值系列 "歷史股價資訊"。
            for (int i = 0; i < data.size(); i++) {
                Object item = data.get(i); // 取得單日資料記錄
                double value = valueExtractor.applyAsDouble(item);
                LocalDate localDate = dateExtractor.apply(item); // 取出 LocalDate

                // 強制轉成統一格式的字串
                String dateStr = sdf.format(Date.from(
                    localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                ));

                dataset.addValue(value, titlePrefix, dateStr); // 用日期字符串作為類別（X 軸標籤），Y 為 value 值

                // 如果有指定第二條線
                if (secondaryValueExtractor != null && secondarySeriesName != null && secondaryColor != null) {
                    double secondaryValue = secondaryValueExtractor.applyAsDouble(item);
                    dataset.addValue(secondaryValue, secondarySeriesName, dateStr);
                }

                // 如果是 VIX
                if ("VIX" .equals(titlePrefix)) {
                    dataset.addValue(20, "安全區", dateStr);
                    dataset.addValue(30, "警戒區", dateStr);
                    dataset.addValue(40, "恐慌區", dateStr);
                }

                minValue = Math.min(minValue, value);
                maxValue = Math.max(maxValue, value);
            }

            // JFreeChart 核心工廠，生成線圖（CategoryPlot 類型）。
            JFreeChart chart = ChartFactory.createLineChart(
                " 近 " + data.size() + " 日",
                "日期", yPrefix,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            CategoryPlot plot = (CategoryPlot) chart.getPlot();

            // 設定字型以利解決亂碼問題
            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);
            chart.getTitle().setFont(font);
            chart.getLegend().setItemFont(font);
            plot.getDomainAxis().setLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);

            // Y 軸範圍動態調整，給予適當邊界
            double padding = (maxValue - minValue) * 0.05;
            if (padding == 0) padding = maxValue * 0.1;

            // if (titlePrefix.contains("相對強弱指數")) {
            if ("相對強弱指數" .equals(titlePrefix)) {
                minValue = 0;
                maxValue = 100;
            }

            plot.getRangeAxis().setLowerBound(minValue - padding); // 下限
            plot.getRangeAxis().setUpperBound(maxValue + padding); // 上限

            // 線條樣式設定
            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, lineColor);
            renderer.setSeriesStroke(0, new BasicStroke(2.5f));
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8));
            renderer.setDefaultToolTipGenerator(new StandardCategoryToolTipGenerator("{2}", NumberFormat.getInstance())); // {0}=系列名, {1}=X軸, {2}=Y值；滑鼠移過去跳出 Tooltip
            renderer.setDefaultItemLabelsVisible(true); // 每個點上直接顯示數值
            renderer.setSeriesItemLabelsVisible(0, true);// 讓主要線顯示數值

            // 設定第二條線的樣式（如果存在）
            if (secondarySeriesName != null) {
                renderer.setSeriesPaint(1, secondaryColor);
                renderer.setSeriesStroke(1, new BasicStroke(2.0f));  // 信號線細一點
                renderer.setSeriesShapesVisible(1, true);
                renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8));
            }

            // CategoryAxis：X 軸類別軸，處理日期標籤位置。
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90); // 日期標籤垂直顯示（UP_90），避免擁擠（依需調整為 STANDARD 或 DOWN_90）

            // 建立 ChartPanel 並設定大小
            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            swingNode.setContent(currentChartPanel);

            // 模擬自動點擊 currentChartPanel
            /*try {
                // Timer 延遲 400ms-800ms，等待 JavaFX 佈局完成，讓 ChartPanel 取得正確的螢幕座標。
                javax.swing.Timer robotTimer = new javax.swing.Timer(800, e -> {
                    try {
                        // 確保 currentChartPanel 已經在螢幕上可見且有尺寸
                        java.awt.Point screenLoc = currentChartPanel.getLocationOnScreen();
                        int width = currentChartPanel.getWidth();
                        int height = currentChartPanel.getHeight();
                        
                        if (screenLoc != null && width > 0 && height > 0) {
                            java.awt.Robot robot = new java.awt.Robot();
                            
                            // 定點到圖表的中心點
                            int centerX = screenLoc.x + width / 2;
                            int centerY = screenLoc.y + height / 2;

                            // 執行 Robot 操作：移動、點擊
                            robot.mouseMove(centerX, centerY); // 移動滑鼠到圖表中央
                            robot.delay(50); 
                            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                            robot.delay(20);
                            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                            
                            // 讓滑鼠回到不會干擾圖表的位置
                            robot.mouseMove(centerX + 10, centerY + 10);
                            
                            // System.out.println("Robot 點擊成功，Tooltip 已激活。");

                        } else {
                            // System.err.println("ChartPanel 尚未取得螢幕座標或尺寸，Robot 點擊跳過。");
                        }
                        
                    } catch (Exception ex) {
                        System.err.println("自動激活失敗（Robot 錯誤）：" + ex.getMessage());
                    }
                    
                    // 無論成功與否，停止 Timer
                    ((javax.swing.Timer) e.getSource()).stop();
                });
                
                robotTimer.setRepeats(false);
                robotTimer.start(); // 在 Swing EDT 啟動 Timer

            } catch (Exception ex) {
                System.err.println("Timer 啟動失敗：" + ex.getMessage());
            }*/
        });

        return swingNode;
    }

    // 重載
    private Node createCommonLineChart(
            List<?> data,
            String titlePrefix,
            String yPrefix,
            Color lineColor,
            ToDoubleFunction<Object> valueExtractor,
            ToLocalDateFunction<Object> dateExtractor
    ) {
        return createCommonLineChart(
            data,
            titlePrefix,
            yPrefix,
            lineColor,
            valueExtractor,
            dateExtractor,
            null,
            null,
            null
        );
    }

    @FunctionalInterface
    interface ToDoubleFunction<T> { 
        double applyAsDouble(T value); 
    }

    @FunctionalInterface
    interface ToLocalDateFunction<T> { 
        LocalDate apply(T value); 
    }

    // 查詢歷史 K 線邏輯
    private void queryHistory() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key
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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, days, apiKey))
            .thenAccept(candles -> Platform.runLater(() -> {
                if (!candles.isEmpty()) {
                    LocalDate today = LocalDate.now();
                    boolean hasToday = candles.stream().anyMatch(c -> c.date().equals(today));

                    if (!hasToday) {
                        Quote quote = service.fetchQuote(symbol, apiKey); // 即時報價

                        if (quote.closePrice() != 0) {
                            // 建立今日虛擬K棒
                            Candle todayCandle = new Candle(
                                today,
                                quote.openPrice(),
                                quote.highPrice(),
                                quote.lowPrice(),
                                quote.closePrice(), // 目前成交價當作「收盤價」
                                0, // 今日成交量暫設為0，因為歷史K線的volume是整日總量，無法從即時報價取得
                                quote.change()
                            );

                            candles.add(todayCandle);
                            candles.sort(Comparator.comparing(Candle::date));
                        }
                    }

                    StringBuilder sb = new StringBuilder(String.format("歷史 K 線圖已載入（近 %d 日走勢）。\n\n", candles.size()));
                    for (Candle c : candles) {
                        String tag = c.date().equals(today) && !hasToday ? "（即時演算）" : "";
                        sb.append(String.format("日期：%s%s\n開盤價：%.1f\n最高價：%.1f\n最低價：%.1f\n收盤價：%.1f\n成交量：%d\n漲跌：%.1f\n\n",
                            c.date(), tag, c.open(), c.high(), c.low(), c.close(), c.volume(), c.change()));
                    }

                    // 計算區間最高價（所有 high 的 max）和最低價（所有 low 的 min）
                    // 用 Stream API：mapToDouble(Candle::high).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                    double maxHigh = candles.stream().mapToDouble(Candle::high).max().orElse(0.0); // 區間最高價
                    double minLow = candles.stream().mapToDouble(Candle::low).min().orElse(0.0); // 區間最低價

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

                    sb.append(String.format("區間觸及最高價：%.1f（%s）\n", maxHigh, maxHighDateStr)); // 格式化添加（%.1f 保留1位小數）
                    sb.append(String.format("區間觸及最低價：%.1f（%s）\n\n", minLow, minLowDateStr)); // 格式化添加（%.1f 保留1位小數）

                    // 區間單日最大漲幅與最大跌幅
                    double maxDailyGain = candles.stream()
                            .mapToDouble(c -> c.change()) 
                            .max()
                            .orElse(0.0);

                    double minDailyGain = candles.stream()
                            .mapToDouble(c -> c.change())
                            .min()
                            .orElse(0.0);

                    // 找出最大漲幅的所有日期（從新到舊）
                    List<LocalDate> maxGainDates = candles.stream()
                            .filter(c -> c.change() == maxDailyGain)
                            .map(Candle::date)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());
                    String maxGainDateStr = maxGainDates.stream()
                            .map(LocalDate::toString)
                            .collect(Collectors.joining("、"));

                    // 找出最大跌幅的所有日期（從新到舊）
                    List<LocalDate> minGainDates = candles.stream()
                            .filter(c -> c.change() == minDailyGain)
                            .map(Candle::date)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());
                    String minGainDateStr = minGainDates.stream()
                            .map(LocalDate::toString)
                            .collect(Collectors.joining("、"));

                    // 若漲跌為 0 或無資料，顯示為 0（避免顯示 -0）
                    String maxGainDisplay = maxDailyGain > 0 ? String.format("%.1f", maxDailyGain) : "0";
                    String minGainDisplay = minDailyGain < 0 ? String.format("%.1f", minDailyGain) : "0";

                    sb.append(String.format("區間單日最大漲幅：%s（%s）\n", 
                        maxGainDisplay, maxGainDates.isEmpty() ? "無" : maxGainDateStr));
                    sb.append(String.format("區間單日最大跌幅：%s（%s）\n", 
                        minGainDisplay, minGainDates.isEmpty() ? "無" : minGainDateStr));

                    // 量價背離
                    sb.append("\n＊什麼是量價背離＊\n\n");
                    sb.append("量價背離是技術分析中最經典的反轉訊號之一，指價格走勢與成交量走勢方向相反，代表趨勢動能減弱，容易出現轉折。\n\n");
                    sb.append("常見兩種型態：\n\n");

                    sb.append("＊頂背離（漲勢末期，最常見的空頭訊號）\n\n");
                    sb.append("價格創新高（或持續上漲），但成交量卻縮小（或無法放大）。\n\n");
                    sb.append("意思：追價買盤意願減弱，雖然價格還在漲，但缺乏量能支撐，主力可能在出貨。\n\n");
                    sb.append("例子：股價連續拉紅K創新高，但每天成交量越來越小 → 後續容易回落或反轉下跌。\n\n");

                    sb.append("＊底背離（跌勢末期，多頭訊號）\n\n");
                    sb.append("價格創新低（或持續下跌），但成交量卻縮小（賣壓減弱）。\n\n");
                    sb.append("意思：殺盤力量衰竭，雖然價格還在跌，但沒人願意再賣 → 容易反彈或止跌。\n\n");
                    sb.append("例子：指數連續重挫，但成交量從天量變地量 → 常是底部訊號。\n\n");

                    sb.append("【量價背離分析如下】\n");
                    if (candles.size() >= 30) {
                        int maxHighIndex = -1; // 頂背離：第一次出現（最舊）
                        long maxHighVolume = 0;

                        // 底背離改用：從最後往前找第一個 volume > 0 的創低日
                        int minLowIndex = -1;
                        long minLowVolume = 0;

                        // 先找頂背離（維持原邏輯：最舊的那次創高）
                        for (int i = 0; i < candles.size(); i++) {
                            Candle c = candles.get(i);
                            if (c.high() == maxHigh && maxHighIndex == -1) {
                                maxHighIndex = i;
                                maxHighVolume = c.volume();
                                break; // 只取第一次
                            }
                        }

                        // 底背離優化：從後往前找（最新優先，且 volume > 0）
                        for (int i = candles.size() - 1; i >= 0; i--) {
                            Candle c = candles.get(i);
                            if (c.low() == minLow && c.volume() > 0) {
                                minLowIndex = i;
                                minLowVolume = c.volume();
                                break; // 找到就停止（最新有量的創低日）
                            }
                        }

                        // 計算全區間平均成交量
                        long avgVolume = (long) candles.stream()
                                .mapToLong(Candle::volume)
                                .average()
                                .orElse(0.0);

                        boolean hasDivergence = false;

                        // 頂背離
                        if (maxHighIndex != -1 && avgVolume > 0 && maxHighVolume < avgVolume * 0.7) {
                            LocalDate maxHighDate = candles.get(maxHighIndex).date();
                            sb.append(String.format("頂背離警訊：%s 觸及區間最高價 %.1f，但成交量 %,d 僅為平均 %,d 的 %.0f%%（量縮明顯，追價動能不足）\n",
                                    maxHighDate, maxHigh, maxHighVolume, avgVolume,
                                    (maxHighVolume * 100.0 / avgVolume)));
                            hasDivergence = true;
                        }

                        // 底背離
                        if (minLowIndex != -1 && avgVolume > 0 && minLowVolume < avgVolume * 0.7) {
                            LocalDate minLowDate = candles.get(minLowIndex).date();
                            sb.append(String.format("底背離警訊：%s 觸及區間最低價 %.1f，但成交量 %,d 僅為平均 %,d 的 %.0f%%（量縮明顯，賣壓衰竭）\n",
                                    minLowDate, minLow, minLowVolume, avgVolume,
                                    (minLowVolume * 100.0 / avgVolume)));
                            hasDivergence = true;
                        }

                        if (!hasDivergence) {
                            sb.append("區間內無明顯量價背離現象（量價配合正常）。\n");
                        }
                    } else {
                        sb.append("目前數據不夠，請讓資料面最其碼超過30天");
                    }

                    resultArea.setText(sb.toString()); // 設定完整文字
                    resultArea.appendText(""); // 自動滾動到最底部

                    // K 線圖表
                    chartPane.setContent(createCommonLineChart(
                        candles,
                        "歷史股價資訊",
                        "價格",
                        Color.RED,
                        obj -> ((Candle) obj).close(),
                        obj -> ((Candle) obj).date() // 直接回傳 LocalDate
                    ));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
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

    // 查詢 SMA 邏輯（使用共用 daysField）
    private void querySMA() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key
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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchSMA(symbol, days, apiKey))
            .thenAccept(smaList -> Platform.runLater(() -> {
                if (!smaList.isEmpty()) {
                    LocalDate today = LocalDate.now();
                    boolean hasToday = smaList.stream().anyMatch(s -> s.date().equals(today));
                    Quote quote = service.fetchQuote(symbol, apiKey);
                    double currentPrice = quote.closePrice(); // 使用即時價當收盤價
                    String stockInfo = quote.name() + "（" + quote.symbol() + "）";

                    if (!hasToday) {
                        if (quote.closePrice() != 0) {
                            List<Candle> history = service.fetchHistory(symbol, days, apiKey);

                            // 建立今日虛擬K棒
                            Candle todayCandle = new Candle(
                                today,
                                quote.openPrice(),
                                quote.highPrice(),
                                quote.lowPrice(),
                                currentPrice, // 目前成交價當作「收盤價」
                                0, // 今日成交量暫設為0，因為歷史K線的volume是整日總量，無法從即時報價取得
                                quote.change()
                            );

                            List<Candle> fullCandles = new ArrayList<>(history);
                            fullCandles.add(todayCandle);
                            fullCandles.sort(Comparator.comparing(Candle::date));

                            // 計算 SMA(5)，確認資料筆數最其碼有5筆以上
                            if (fullCandles.size() >= 5) {
                                double sum = fullCandles.stream()
                                    .skip(Math.max(0, fullCandles.size() - 5)) // 挑最近的5筆
                                    .mapToDouble(Candle::close)
                                    .sum();
                                double sma5 = sum / 5.0;

                                smaList.add(new SMA(today, round(sma5)));
                                smaList.sort(Comparator.comparing(SMA::date));
                            }
                        }
                    }

                    // 彈出輸入對話框
                    TextInputDialog dialog = createStyledInputDialog(
                        "庫存均價查詢（可選）",
                        "查詢 " + stockInfo + " 的 SMA 相對位置",
                        "請輸入您的庫存均價（不輸入或填 0 則使用當前股價）："
                    );

                    Optional<String> result = dialog.showAndWait();
                    if (result.isPresent()) {
                        String input = result.get().trim();
                        if (!input.isEmpty()) {
                            try {
                                double inputPrice = Double.parseDouble(input);
                                if (inputPrice > 0) {
                                    currentPrice = inputPrice;
                                    stockInfo += "\n您的庫存均價： " + String.format("%.2f", currentPrice);
                                }
                            } catch (NumberFormatException ex) {
                                // 輸入無效，維持使用當前股價
                            }
                        }
                    }

                    StringBuilder sb = new StringBuilder(String.format("簡單移動平均線（SMA）已載入（近 %d 日走勢）\n\n", smaList.size()));
                    for (SMA s : smaList) {
                        String tag = s.date().equals(today) && !hasToday ? "（即時演算）" : "";
                        sb.append(String.format("日期：%s%s\nSMA：%.2f\n\n", s.date(), tag, s.sma()));
                    }

                    // 計算區間最高價（所有 high 的 max）和最低價（所有 low 的 min）
                    // 用 Stream API：mapToDouble(Candle::high).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                    double max = smaList.stream().mapToDouble(SMA::sma).max().orElse(0);
                    double min = smaList.stream().mapToDouble(SMA::sma).min().orElse(0);

                    // 找出達到最高價的所有日期，並按遞減（從最新到最舊）排序
                    List<LocalDate> maxDates = smaList.stream()
                            .filter(s -> s.sma() == max)
                            .map(SMA::date)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());

                    // 找出達到最低價的所有日期，並按遞減（從最新到最舊）排序
                    List<LocalDate> minDates = smaList.stream()
                            .filter(s -> s.sma() == min)
                            .map(SMA::date)
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());

                    sb.append(String.format("區間最高：%.2f（%s）\n", max, maxDates.stream().map(Object::toString).collect(Collectors.joining("、"))));
                    sb.append(String.format("區間最低：%.2f（%s）\n", min, minDates.stream().map(Object::toString).collect(Collectors.joining("、"))));

                    // 系統建議
                    if (currentPrice != 0) {
                        double latestSMA = smaList.get(smaList.size() - 1).sma(); // 最新一筆 SMA
                        double deviationPct = (currentPrice - latestSMA) / latestSMA * 100.0; // 偏差百分比

                        sb.append(String.format("\n%s（" + "%s）投資建議如下\n\n", quote.name(), quote.symbol()));
                        sb.append(String.format("當前股價：%.2f\n", quote.closePrice()));
                        sb.append(String.format("偏差幅度：%.2f%% %s\n", deviationPct, deviationPct >= 0 ? "（高於均線）" : "（低於均線）"));

                        String signal; // 信號
                        String advice; // 建議

                        if (currentPrice >= latestSMA) {
                            signal = "股價站上均線";
                            advice = "多頭排列啟動，持股信心強，可考慮加碼或追多";
                        } else if (deviationPct >= -1.0) {
                            signal = "貼近均線震盪";
                            advice = "主力磨線階段，等待突破方向，暫時觀望為主";
                        } else if (deviationPct >= -3.0) {
                            signal = "輕度超賣";
                            advice = "技術性回檔結束，反彈機率＞80%，建議佈局";
                        } else if (deviationPct >= -6.0) {
                            signal = "明顯超賣";
                            advice = "高勝率反彈區，歷史經驗強力買點";
                        } else {
                            signal = "極度超賣";
                            advice = "恐慌性賣壓尾聲，大底即將成形，可重壓";
                        }

                        sb.append(String.format("信號：%s\n\n", signal));
                        sb.append(advice + "\n");
                        // showAlert(
                        //     stockInfo + "\n" + 
                        //     "最新SMA（" + days + "日）：" + String.format("%.2f", latestSMA) + "\n" +
                        //     "當前股價：" + quote.closePrice() + "\n" +
                        //     "偏差幅度：" + deviationPct + "\n\n" +
                        //     "信號：" + signal + "\n" +
                        //     advice,
                        //     AlertType.INFORMATION
                        // );
                    }

                    resultArea.setText(sb.toString()); // 設定完整文字
                    resultArea.appendText(""); // 自動滾動到最底部

                    // SMA 圖表
                    chartPane.setContent(createCommonLineChart(
                        smaList,
                        "簡單移動平均線",
                        "SMA",
                        Color.ORANGE,
                        obj -> ((SMA) obj).sma(),
                        obj -> ((SMA) obj).date() // 直接回傳 LocalDate
                    ));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
                } else {
                    resultArea.setText("SMA 資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                }
            }));
    }

    // 查詢 RSI 邏輯（使用共用 daysField）
    private void queryRSI() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key
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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchRSI(symbol, days, apiKey))
            .thenAccept(rsiList -> Platform.runLater(() -> {
                if (!rsiList.isEmpty()) {
                    LocalDate today = LocalDate.now();
                    boolean hasToday = rsiList.stream().anyMatch(r -> r.date().equals(today));

                    if (!hasToday) {
                        Quote quote = service.fetchQuote(symbol, apiKey);

                        if (quote.closePrice() != 0) {
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
                    }

                    StringBuilder sb = new StringBuilder(String.format("相對強弱指標 （RSI）已載入（近 %d 日走勢）。\n\n強弱指數如下：\n\n", rsiList.size())); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                    for (RSI r : rsiList) {
                        String tag = r.date().equals(today) && !hasToday ? "（即時演算）" : "";
                        sb.append(String.format("日期：%s%s\n指數：%.2f\n\n",
                            r.date(), tag, r.rsi()));
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

                    sb.append("\n＊超買與超賣\n\n");
                    sb.append("當RSI 顯示超買時（通常大於70），可能表示市場過熱，價格有回調的可能，是賣出訊號。 反之，當RSI 顯示超賣時（通常小於30），可能表示市場過冷，價格有上漲的潛力，是買入訊號。\n\n");
                    sb.append("＊市場趨勢\n\n");
                    sb.append("RSI 值越高，表示過去一段期間的上漲機率較大；值越小，則下跌機率較大。");

                    resultArea.setText(sb.toString());  // 設定完整文字
                    resultArea.appendText(""); // 自動滾動到最底部

                    // MRSI 圖表
                    chartPane.setContent(createCommonLineChart(
                        rsiList,
                        "相對強弱指數",
                        "RSI（0-100）",
                        Color.MAGENTA,
                        obj -> ((RSI) obj).rsi(),
                        obj -> ((RSI) obj).date() // 直接回傳 LocalDate
                    ));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
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
        String apiKey = keyField.getText().trim(); // API Key
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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchMACD(symbol, days, apiKey))
            .thenAccept(macdList -> Platform.runLater(() -> {
                if (!macdList.isEmpty()) {
                    LocalDate today = LocalDate.now();
                    boolean hasToday = macdList.stream().anyMatch(m -> m.date().equals(today));

                    if (!hasToday) {
                        Quote quote = service.fetchQuote(symbol, apiKey);

                        if (quote.closePrice() != 0) {
                            List<Candle> history = service.fetchHistory(symbol, days, apiKey);

                            // 建立今日虛擬K棒
                            Candle todayCandle = new Candle(
                                today,
                                quote.openPrice(),
                                quote.highPrice(),
                                quote.lowPrice(),
                                quote.closePrice(), // 目前成交價當作「收盤價」
                                0, // 今日成交量暫設為0，因為歷史K線的volume是整日總量，無法從即時報價取得
                                quote.change()
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
                    }

                    StringBuilder sb = new StringBuilder(String.format("移動平均指標 （MACD）已載入（近 %d 日走勢）。\n\n", macdList.size()));
                    for (MACD m : macdList) {
                        String tag = m.date().equals(today) && !hasToday ? "（即時演算）" : "";
                        sb.append(String.format("日期：%s%s\nMACD 線：%.2f\n信號線：%.2f\n\n",
                            m.date(), tag, m.macdLine(), m.signalLine()));
                    }

                    // 計算區間最強勢（所有 macdLine 的 max）和最弱勢（所有 macdLine 的 min）
                    // 用 Stream API：mapToDouble(MACD::macdLine).max().orElse(0.0) - 高效 O(n)，method reference 簡潔
                    double maxMacd = macdList.stream().mapToDouble(MACD::macdLine).max().orElse(0.0);
                    double minMacd = macdList.stream().mapToDouble(MACD::macdLine).min().orElse(0.0);

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

                    sb.append("\n＊黃金交叉\n\n");
                    sb.append("當移動平均線（MACD）慢慢往上交叉信號線（signalLine）時發生。這通常被視為一個買進訊號，表示上漲趨勢可能增強。\n\n");
                    sb.append("＊死亡交叉\n\n");
                    sb.append("當移動平均線（MACD）慢慢往下交叉信號線（signalLine）時發生。這通常被視為一個賣出訊號，表示下跌趨勢可能增強。");

                    resultArea.setText(sb.toString());  // 設定完整文字
                    resultArea.appendText(""); // 自動滾動到最底部

                    // MACD 圖表
                    chartPane.setContent(createCommonLineChart(
                        macdList,
                        "MACD 線",
                        "MACD",
                        Color.RED,
                        obj -> ((MACD) obj).macdLine(),
                        obj -> ((MACD) obj).date(), // 直接回傳 LocalDate
                        obj -> ((MACD) obj).signalLine(),
                        "信號線",
                        Color.BLUE
                    ));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
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
        if (candles.isEmpty()) return result;

        double[] close = candles.stream().mapToDouble(Candle::close).toArray();

        // 從第一筆開始初始化 EMA
        double ema12 = close[0];
        double ema26 = close[0];
        double k12 = 2.0 / (12 + 1);
        double k26 = 2.0 / (26 + 1);

        double prevDea = 0.0;
        boolean firstDea = true;

        for (int i = 1; i < close.length; i++) {
            ema12 = close[i] * k12 + ema12 * (1 - k12);
            ema26 = close[i] * k26 + ema26 * (1 - k26);

            double dif = ema12 - ema26;

            double dea;
            if (firstDea) {
                dea = dif;
                firstDea = false;
            } else {
                dea = dif * (2.0 / (9 + 1)) + prevDea * (1 - 2.0 / (9 + 1));
            }
            prevDea = dea;

            // 每一天都加入結果（盤中也能看到最新值）
            result.add(new MACD(candles.get(i).date(), round(dif), round(dea)));
        }

        return result;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // 查詢 Bollinger 邏輯（使用共用 daysField）
    private void queryBollinger() {
        String symbol = symbolField.getText().trim(); // 股票代號
        String apiKey = keyField.getText().trim(); // API Key
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
            if (days < 30) {
                showAlert("查詢布林通道建議設定「30 天以上」，以確保有足夠交易日");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（建議 30 以上）");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> service.fetchBollinger(symbol, days, apiKey))
            .thenAccept(bbList -> Platform.runLater(() -> {
                if (bbList.isEmpty()) {
                    resultArea.setText("布林通道資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                    return;
                }

                if (bbList.size() < 20) {
                    resultArea.setText("資料不足：目前僅有 " + bbList.size() + " 筆交易日資料\n\n" + "布林通道需至少 20 個交易日才能正確計算\n" + "建議將「天數」設定為 40 以上，或等待更多交易日");
                    return;
                }

                LocalDate today = LocalDate.now();
                List<Candle> candles = service.fetchHistory(symbol, days, apiKey);
                boolean hasToday = candles.stream().anyMatch(c -> c.date().equals(today));

                if (!hasToday) {
                    Quote quote = service.fetchQuote(symbol, apiKey);

                    if (quote.closePrice() != 0) {
                        Candle todayCandle = new Candle(
                            LocalDate.now(),
                            quote.openPrice(),
                            quote.highPrice(),
                            quote.lowPrice(),
                            quote.closePrice(), // 目前成交價當作「收盤價」
                            0, // 今日成交量暫設為0，因為歷史K線的volume是整日總量，無法從即時報價取得
                            quote.change()
                        );
                        candles.add(todayCandle);
                    }
                }

                boolean bbListHasToday = bbList.get(bbList.size() - 1).date().equals(today);

                if (!bbListHasToday) {
                    // 近 20 天的股價歷史資料
                    List<Double> last20Closes = candles.stream()
                        .sorted(Comparator.comparing(Candle::date))
                        .skip(candles.size() - 20)
                        .limit(20)
                        .map(Candle::close)
                        .collect(Collectors.toList());

                    if (last20Closes.size() == 20) {
                        // 將近 20 天的收盤價加總之後除以 20，藉此得到中位數（均價）
                        double sum = last20Closes.stream().mapToDouble(d -> d).sum();
                        double middle = sum / 20.0;
                        
                        // 對每一根 K 線：(當天收盤價 - 中軌)²
                        // 把這 20 個平方值加起來，再除以 20 → 得到「變異數」
                        double varianceSum = last20Closes.stream()
                            .mapToDouble(c -> Math.pow(c - middle, 2))
                            .sum();
                        double stdDev = Math.sqrt(varianceSum / 20.0);

                        double upper = middle + 2 * stdDev; // 上軌
                        double lower = middle - 2 * stdDev; // 下軌

                        bbList.add(new Bollinger(today, upper, middle, lower));
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("布林通道 （Bollinger Bands）已載入（近 %d 日走勢）。\n\n", bbList.size()));
                sb.append("布林通道指數如下：\n\n");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                for (Bollinger b : bbList) {
                    String dateStr = sdf.format(Date.from(b.date().atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    sb.append(String.format("日期：%s\n", dateStr));
                    sb.append(String.format("上軌：%.4f\n", b.upper()));
                    sb.append(String.format("中軌：%.4f\n", b.middle()));
                    sb.append(String.format("下軌：%.4f\n\n", b.lower()));
                }

                sb.append("＊買入訊號：當股價觸及下軌並有反彈跡象時，可能是一個買入訊號\n\n");
                sb.append("＊賣出訊號：當股價觸及上軌並有回落跡象時，可能是一個賣出訊號。");

                resultArea.setText(sb.toString());
                resultArea.appendText(""); // 自動滾動到最底部

                Node chartNode = createBollingerWithCandlesChart(candles, bbList);
                chartPane.setContent(chartNode);
                resizeChartProportionally(); // 改用統一的等比例縮放方法
            }))
            .exceptionally(ex -> {
                // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                return null;
            });
    }

    // 布林通道 + K線 複合圖表
    private Node createBollingerWithCandlesChart(List<Candle> candles, List<Bollinger> bbList) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // 1) 準備索引化資料（x = index；labels = yyyy-MM-dd）
            final int n = candles.size();
            final String[] labels = new String[n];
            final double[] xIndex = new double[n];
            final double[] opens = new double[n];
            final double[] highs = new double[n];
            final double[] lows = new double[n];
            final double[] closes = new double[n];
            final double[] volumes = new double[n];

            for (int i = 0; i < n; i++) {
                Candle c = candles.get(i);
                labels[i] = c.date().toString();  // yyyy-MM-dd
                xIndex[i] = i;                    // 用索引作為 x
                opens[i] = c.open();
                highs[i] = c.high();
                lows[i] = c.low();
                closes[i] = c.close();
                volumes[i] = c.volume();
            }

            // 本地類別：索引化 OHLCDataset（避免 DateAxis）
            class IndexedOHLCDataset extends org.jfree.data.xy.AbstractXYDataset implements org.jfree.data.xy.OHLCDataset {
                private final String seriesKey = "股價";

                @Override public int getSeriesCount() { return 1; }
                @Override public String getSeriesKey(int series) { return seriesKey; }
                @Override public int getItemCount(int series) { return n; }

                // X/Y as Number
                @Override public Number getX(int series, int item) { return xIndex[item]; }
                @Override public Number getY(int series, int item) { return closes[item]; }

                // X/Y as primitive double (some renderers use these)
                @Override public double getXValue(int series, int item) { return xIndex[item]; }
                @Override public double getYValue(int series, int item) { return closes[item]; }

                // OHLC as Number
                @Override public Number getOpen(int series, int item)   { return opens[item]; }
                @Override public Number getHigh(int series, int item)   { return highs[item]; }
                @Override public Number getLow(int series, int item)    { return lows[item]; }
                @Override public Number getClose(int series, int item)  { return closes[item]; }
                @Override public Number getVolume(int series, int item) { return volumes[item]; }

                // OHLC as primitive double
                @Override public double getOpenValue(int series, int item)   { return opens[item]; }
                @Override public double getHighValue(int series, int item)   { return highs[item]; }
                @Override public double getLowValue(int series, int item)    { return lows[item]; }
                @Override public double getCloseValue(int series, int item)  { return closes[item]; }
                @Override public double getVolumeValue(int series, int item) { return volumes[item]; }
            }

            org.jfree.data.xy.OHLCDataset candleDataset = new IndexedOHLCDataset();

            // 2) 建 Bollinger 線（同樣使用索引 x）
            XYSeries upperSeries = new XYSeries("上軌（壓力線）");
            XYSeries middleSeries = new XYSeries("中軌（20日均線）");
            XYSeries lowerSeries = new XYSeries("下軌（支撐線）");

            // 建立日期→索引對應，確保只畫存在的日期
            Map<String, Integer> indexByDate = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) indexByDate.put(labels[i], i);

            for (Bollinger b : bbList) {
                String d = b.date().toString();
                Integer idx = indexByDate.get(d);
                if (idx != null) {
                    upperSeries.add(idx.doubleValue(), b.upper());
                    middleSeries.add(idx.doubleValue(), b.middle());
                    lowerSeries.add(idx.doubleValue(), b.lower());
                }
            }

            XYSeriesCollection lineDataset = new XYSeriesCollection();
            lineDataset.addSeries(upperSeries);
            lineDataset.addSeries(middleSeries);
            lineDataset.addSeries(lowerSeries);

            // 3) 建圖：使用 XYPlot + CandlestickRenderer（支援 OHLCDataset）
            XYPlot plot = new XYPlot();
            plot.setDataset(0, candleDataset);
            plot.setDataset(1, lineDataset);

            // Y 軸
            org.jfree.chart.axis.NumberAxis rangeAxis = new org.jfree.chart.axis.NumberAxis("股價");
            rangeAxis.setAutoRangeIncludesZero(false);
            plot.setRangeAxis(rangeAxis);

            // X 軸：SymbolAxis（強制顯示所有日期，垂直排列）
            org.jfree.chart.axis.SymbolAxis domainAxis = new org.jfree.chart.axis.SymbolAxis("日期", labels);
            domainAxis.setGridBandsVisible(false);
            domainAxis.setLowerMargin(0.0);
            domainAxis.setUpperMargin(0.0);

            // 顯示所有標籤並垂直排列（UP_90）
            domainAxis.setTickLabelsVisible(true);
            domainAxis.setVerticalTickLabels(true);
            domainAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));

            plot.setDomainAxis(domainAxis);

            // Renderers
            CandlestickRenderer candleRenderer = new CandlestickRenderer();
            candleRenderer.setUpPaint(Color.RED);
            candleRenderer.setDownPaint(Color.GREEN);
            candleRenderer.setDrawVolume(false);
            candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
            plot.setRenderer(0, candleRenderer);

            XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
            lineRenderer.setSeriesPaint(0, new Color(255, 80, 80));    // 上軌：亮紅
            lineRenderer.setSeriesPaint(1, new Color(70, 130, 255));   // 中軌：寶藍
            lineRenderer.setSeriesPaint(2, new Color(80, 200, 120));   // 下軌：翠綠
            lineRenderer.setSeriesStroke(0, new BasicStroke(2.2f));
            lineRenderer.setSeriesStroke(1, new BasicStroke(2.5f));
            lineRenderer.setSeriesStroke(2, new BasicStroke(2.2f));
            plot.setRenderer(1, lineRenderer);

            // 4) 設定 Y 軸範圍（含布林上軌，用於視覺留白）
            double maxPrice = Arrays.stream(highs).max().orElse(1000);
            double minPrice = Arrays.stream(lows).min().orElse(0);
            double bbMax = bbList.stream().mapToDouble(Bollinger::upper).max().orElse(maxPrice);
            maxPrice = Math.max(maxPrice, bbMax);
            double range = maxPrice - minPrice;
            if (range == 0) range = Math.max(1.0, maxPrice * 0.1);
            double upperBound = maxPrice + range * 0.1;
            double lowerBound = minPrice - range * 0.1;
            plot.getRangeAxis().setRange(lowerBound, upperBound);

            // 5) 組成 JFreeChart
            JFreeChart chart = new JFreeChart(
                    "布林通道＋K線圖（近 " + candles.size() + " 日）",
                    new Font("Microsoft JhengHei", Font.BOLD, 18),
                    plot,
                    true
            );

            // 字體
            Font chineseFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            Font legendFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            plot.getDomainAxis().setLabelFont(chineseFont);
            plot.getRangeAxis().setLabelFont(chineseFont);
            chart.getLegend().setItemFont(legendFont);

            // ChartPanel
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            chartPanel.setMouseWheelEnabled(true);
            chartPanel.setRangeZoomable(false);

            currentChartPanel = chartPanel;
            swingNode.setContent(chartPanel);

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

        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 查三大法人買賣超
    private void queryInstitutionalTrading() {
        String symbol = symbolField.getText().trim();
        if (symbol.isEmpty()) {
            showAlert("請輸入股票代號");
            return;
        }

        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        try {
            days = Integer.parseInt(daysText);
            if (days < 0) {
                showAlert("天數必須為 0 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（0 以上）");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.runAsync(() -> {
            try {
                Document doc = Jsoup.connect("https://stock.wearn.com/netbuy.asp?kind=" + symbol)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();

                Elements tables = doc.select("table.mobile_img");
                if (tables.isEmpty()) throw new Exception("查無資料");

                Elements rows = tables.first().select("tbody tr");

                // 原始資料：最新在前（weearn.com 就是這樣）
                List<String[]> rawData = new ArrayList<>();

                for (int i = 2; i < rows.size(); i++) {
                    Elements cells = rows.get(i).select("td");
                    if (cells.size() < 4) continue;

                    String rawDate = cells.get(0).text().trim();
                    String trustStr = cells.get(1).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");
                    String dealerStr = cells.get(2).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");
                    String foreignStr = cells.get(3).text().trim().replace("+ ", "").replace(" ", "").replace(",", "");

                    int trust = trustStr.isEmpty() || trustStr.equals("-") ? 0 : Integer.parseInt(trustStr);
                    int dealer = dealerStr.isEmpty() || dealerStr.equals("-") ? 0 : Integer.parseInt(dealerStr);
                    int foreign = foreignStr.isEmpty() || foreignStr.equals("-") ? 0 : Integer.parseInt(foreignStr);

                    String standardDate = convertTwDateToStandard(rawDate);
                    rawData.add(new String[]{standardDate, String.valueOf(trust), String.valueOf(dealer), String.valueOf(foreign)});
                }

                if (rawData.isEmpty()) throw new Exception("查無資料");

                // 決定要幾筆（最近 N 天，或全部）
                int count = (days == 0) ? rawData.size() : Math.min(rawData.size(), days);

                // 取最新的 count 筆（最近 N 天）
                List<String[]> recentData = rawData.subList(0, count);

                // 顯示與圖表共用這份資料：最舊在前
                List<String[]> displayAndChartData = new ArrayList<>(recentData);
                Collections.reverse(displayAndChartData); // ← 關鍵！讓最舊在前面

                // 準備容器
                List<String> dates = new ArrayList<>();
                List<Integer> trustList = new ArrayList<>();
                List<Integer> dealerList = new ArrayList<>();
                List<Integer> foreignList = new ArrayList<>();

                // 極值追蹤
                int maxTrust = Integer.MIN_VALUE, maxDealer = Integer.MIN_VALUE, maxForeign = Integer.MIN_VALUE;
                int minTrust = Integer.MAX_VALUE, minDealer = Integer.MAX_VALUE, minForeign = Integer.MAX_VALUE;
                String maxTrustDate = "", maxDealerDate = "", maxForeignDate = "";
                String minTrustDate = "", minDealerDate = "", minForeignDate = "";

                StringBuilder sb = new StringBuilder();
                sb.append("三大法人買賣超資訊如下：\n\n");

                for (String[] row : displayAndChartData) {
                    int trust = Integer.parseInt(row[1]);
                    int dealer = Integer.parseInt(row[2]);
                    int foreign = Integer.parseInt(row[3]);

                    dates.add(row[0]);
                    trustList.add(trust);
                    dealerList.add(dealer);
                    foreignList.add(foreign);

                    sb.append(String.format("日期：%s\n", row[0]));
                    sb.append(String.format("外資：%,d\n", foreign));
                    sb.append(String.format("投信：%,d\n", trust));
                    sb.append(String.format("自營商：%,d\n\n", dealer));

                    // 極值更新
                    if (trust > maxTrust) { maxTrust = trust; maxTrustDate = row[0]; }
                    if (dealer > maxDealer) { maxDealer = dealer; maxDealerDate = row[0]; }
                    if (foreign > maxForeign) { maxForeign = foreign; maxForeignDate = row[0]; }
                    if (trust < minTrust) { minTrust = trust; minTrustDate = row[0]; }
                    if (dealer < minDealer) { minDealer = dealer; minDealerDate = row[0]; }
                    if (foreign < minForeign) { minForeign = foreign; minForeignDate = row[0]; }
                }

                sb.append("[買超]\n\n");
                sb.append(String.format("區間最大（投信）：%,d（%s）\n", maxTrust, maxTrustDate));
                sb.append(String.format("區間最大（自營商）：%,d（%s）\n", maxDealer, maxDealerDate));
                sb.append(String.format("區間最大（外資）：%,d（%s）\n", maxForeign, maxForeignDate));

                sb.append("\n[賣超]\n\n");
                sb.append(String.format("區間最大（投信）：%,d（%s）\n", minTrust, minTrustDate));
                sb.append(String.format("區間最大（自營商）：%,d（%s）\n", minDealer, minDealerDate));
                sb.append(String.format("區間最大（外資）：%,d（%s）\n", minForeign, minForeignDate));

                String finalText = sb.toString();

                Platform.runLater(() -> {
                    resultArea.clear();
                    resultArea.appendText(finalText);

                    if (!dates.isEmpty()) {
                        Node chart = createInstitutionalChart(dates, trustList, dealerList, foreignList);
                        chartPane.setContent(chart);
                        resizeChartProportionally(); // 改用統一的等比例縮放方法
                    } else {
                        chartPane.setContent(createEmptyChartPanel());
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.clear();
                    resultArea.appendText("【三大法人買賣超】抓取失敗\n" + e.getMessage() + "\n");
                    chartPane.setContent(createEmptyChartPanel());
                });
            }
        });
    }

    // 轉換民國日期 "114/11/26" → "2025-11-26"
    private String convertTwDateToStandard(String twDate) {
        try {
            String[] parts = twDate.split("/");
            if (parts.length != 3) return twDate;

            int year = Integer.parseInt(parts[0]) + 1911; // 民國轉西元
            String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
            String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];

            return String.format("%d-%s-%s", year, month, day);
        } catch (Exception e) {
            return twDate; // 轉失敗就原樣回傳
        }
    }

    private Node createInstitutionalChart(List<String> dates, List<Integer> trust, List<Integer> dealer, List<Integer> foreign) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            for (int i = 0; i < dates.size(); i++) {
                dataset.addValue(foreign.get(i), "外資", dates.get(i));
                dataset.addValue(trust.get(i), "投信", dates.get(i));
                dataset.addValue(dealer.get(i), "自營商", dates.get(i));
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(
                "三大法人買賣超（張）",
                "日期",
                "買賣超（張）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            CategoryPlot plot = (CategoryPlot) chart.getPlot();

            // 設定字型以利解決亂碼問題
            Font font = new Font("Microsoft JhengHei", Font.BOLD, 14);
            chart.getTitle().setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
            chart.getLegend().setItemFont(font);
            plot.getDomainAxis().setLabelFont(font);
            plot.getDomainAxis().setTickLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);
            plot.getRangeAxis().setTickLabelFont(font);

            plot.setBackgroundPaint(Color.WHITE);
            plot.getDomainAxis().setCategoryLabelPositions(
                CategoryLabelPositions.UP_90
            );

            // 顏色設定
            plot.getRenderer().setSeriesPaint(0, new Color(0, 180, 0)); // 外資 綠
            plot.getRenderer().setSeriesPaint(1, new Color(255, 100, 100)); // 投信 紅
            plot.getRenderer().setSeriesPaint(2, new Color(100, 100, 255)); // 自營商 藍

            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            currentChartPanel = chartPanel;
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

        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 查外資大盤空單數
    private void queryForeignNetPosition() {
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        try {
            days = Integer.parseInt(daysText);
            if (days < 0) {
                showAlert("天數必須為 0 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（0 以上）");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.runAsync(() -> {
            try {
                Document doc = Jsoup.connect("https://stock.wearn.com/taifexphoto.asp")
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();

                Element table = doc.selectFirst("table.taifexphoto");
                if (table == null) {
                    Platform.runLater(() -> showAlert("找不到外資空單表格，網站可能改版了！"));
                    return;
                }

                List<String> originalDates = new ArrayList<>();
                List<Integer> originalNet = new ArrayList<>();

                Elements rows = table.select("tr:gt(1)");
                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() >= 9) {
                        String dateStr = tds.get(0).text().trim();
                        String foreignStr = tds.get(5).text().trim().replace(",", "");

                        if (dateStr.matches("\\d{3}/\\d{2}/\\d{2}")) {
                            int rocYear = Integer.parseInt(dateStr.substring(0, 3));
                            int year = 1911 + rocYear;
                            String adDate = year + dateStr.substring(3);

                            try {
                                int net = Integer.parseInt(foreignStr);
                                originalDates.add(adDate);
                                originalNet.add(net);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                if (originalDates.isEmpty()) {
                    Platform.runLater(() -> showAlert("沒有抓到任何外資空單資料！"));
                    return;
                }

                // 反轉：最舊在前
                List<String> ascendingDates = new ArrayList<>(originalDates);
                List<Integer> ascendingNet = new ArrayList<>(originalNet);
                Collections.reverse(ascendingDates);
                Collections.reverse(ascendingNet);

                // 轉換為 JFreeChart 格式 yyyy-MM-dd
                List<String> chartDates = ascendingDates.stream()
                        .map(d -> d.replace("/", "-"))
                        .collect(Collectors.toList());

                // 計算增減（基於原始順序：最新在前）
                List<Integer> changes = new ArrayList<>();
                for (int i = 0; i < originalNet.size(); i++) {
                    changes.add(i < originalNet.size() - 1 ? originalNet.get(i) - originalNet.get(i + 1) : 0);
                }

                // 反轉增減順序對應文字區
                Collections.reverse(changes);

                // 刪除第一筆不需顯示的資料
                if (!ascendingDates.isEmpty()) {
                    ascendingDates.remove(0);
                    ascendingNet.remove(0);
                    chartDates.remove(0);
                    changes.remove(0);
                }

                // 若天數限制，則截取最後 N 筆
                if (days > 0 && ascendingDates.size() > days) {
                    int start = ascendingDates.size() - days;
                    ascendingDates = ascendingDates.subList(start, ascendingDates.size());
                    ascendingNet = ascendingNet.subList(start, ascendingNet.size());
                    chartDates = chartDates.subList(start, chartDates.size());
                    changes = changes.subList(start, changes.size());
                }

                // 最大最小空單數
                int highestNet = ascendingNet.stream().mapToInt(Integer::intValue).min().orElse(0);
                int lowestNet  = ascendingNet.stream().mapToInt(Integer::intValue).max().orElse(0);

                List<String> highestDates = new ArrayList<>();
                List<String> lowestDates = new ArrayList<>();
                for (int i = 0; i < ascendingNet.size(); i++) {
                    if (ascendingNet.get(i) == highestNet) highestDates.add(ascendingDates.get(i));
                    if (ascendingNet.get(i) == lowestNet)  lowestDates.add(ascendingDates.get(i));
                }

                // 組文字
                StringBuilder sb = new StringBuilder("外資歷史空單數如下：\n\n");
                for (int i = 0; i < ascendingDates.size(); i++) {
                    sb.append(String.format("日期：%s\n空單數：%,d\n增減：%,d\n\n",
                            ascendingDates.get(i).replace("/", "-"),
                            ascendingNet.get(i),
                            changes.get(i)));
                }
                sb.append(String.format("區間最高空單數：%,d（%s）\n",
                        highestNet, String.join("、", highestDates.stream().map(d -> d.replace("/", "-")).toList())));
                sb.append(String.format("區間最低空單數：%,d（%s）\n",
                        lowestNet, String.join("、", lowestDates.stream().map(d -> d.replace("/", "-")).toList())));

                // ====== 成功！直接更新 UI（不用 final 變數）======
                String finalText = sb.toString();
                List<String> finalDates = new ArrayList<>(chartDates);
                List<Integer> finalNet = new ArrayList<>(ascendingNet);

                Platform.runLater(() -> {
                    resultArea.setText(finalText);
                    resultArea.appendText(""); // 自動滾動到最底部
                    chartPane.setContent(createCommonLineChart(
                        finalNet, "外資大盤淨空單", "口數",
                        new Color(255, 140, 0),
                        obj -> ((Integer) obj).doubleValue(),
                        obj -> LocalDate.parse(finalDates.get(finalNet.indexOf(obj)))
                    ));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.setText("載入失敗");
                    showAlert("外資空單資料抓取失敗！\n\n" +
                            "錯誤訊息：" + e.getMessage() + "\n\n" +
                            "請檢查網路，或稍後再試（網站可能改版）");
                });
            }
        });
    }

    // 查 加權指數
    private void queryWeighted() {
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> {
            YahooFinanceService yahooService = new YahooFinanceService();
            List<YahooFinanceService.YahooCandle> candles = yahooService.fetchHistory("^TWII", days);

            if (candles.isEmpty()) {
                throw new RuntimeException("無法取得 加權指數 資料，請檢查網路或稍後再試");
            }

            // 直接在背景執行緒計算統計值
            double maxClose = candles.stream().mapToDouble(c -> c.close()).max().orElse(0.0);
            double minClose = candles.stream().mapToDouble(c -> c.close()).min().orElse(0.0);

            YahooFinanceService.YahooCandle maxCandle = candles.stream()
                    .max(Comparator.comparingDouble(c -> c.close()))
                    .orElse(candles.get(0));
            YahooFinanceService.YahooCandle minCandle = candles.stream()
                    .min(Comparator.comparingDouble(c -> c.close()))
                    .orElse(candles.get(0));

            // 用 Map 包裝傳回 UI 執行緒（避免自訂 record）
            return Map.of(
                "candles", candles,
                "maxClose", maxClose,
                "minClose", minClose,
                "maxDate", maxCandle.date(),
                "minDate", minCandle.date()
            );

        }).thenAcceptAsync(resultMap -> Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            List<YahooFinanceService.YahooCandle> candles =
                    (List<YahooFinanceService.YahooCandle>) resultMap.get("candles");
            double maxClose = (double) resultMap.get("maxClose");
            double minClose = (double) resultMap.get("minClose");
            LocalDate maxDate = (LocalDate) resultMap.get("maxDate");
            LocalDate minDate = (LocalDate) resultMap.get("minDate");

            StringBuilder sb = new StringBuilder();
            sb.append("加權指數 線圖已載入（近 ").append(candles.size()).append(" 日走勢）。\n\n");

            // 直接使用 YahooFinanceService.YahooCandle 迭代
            for (YahooFinanceService.YahooCandle c : candles) {
                sb.append(String.format("日期：%s\n", c.date()))
                  .append(String.format("開盤指數：%.2f\n", c.open()))
                  .append(String.format("最高指數：%.2f\n", c.high()))
                  .append(String.format("最低指數：%.2f\n", c.low()))
                  .append(String.format("收盤指數：%.2f\n\n", c.close()));
            }

            sb.append(String.format("區間最高指數：%.2f（%s）\n", maxClose, maxDate))
              .append(String.format("區間最低指數：%.2f（%s）\n", minClose, minDate));

            resultArea.setText(sb.toString());
            resultArea.appendText(""); // 自動滾動到最底部

            // 加權指數圖表
            chartPane.setContent(createCommonLineChart(
                candles, // 資料來源
                "TWII",
                "加權指數",
                new Color(178, 34, 34),
                candle -> ((YahooFinanceService.YahooCandle) candle).close(),
                candle -> ((YahooFinanceService.YahooCandle) candle).date()
            ));
            resizeChartProportionally(); // 改用統一的等比例縮放方法

        }), Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> showAlert(ex.getMessage()));
            return null;
        });
    }

    // 查聯準會利率
    private void queryFedRateProbability() {
        /**
         * 其它資訊參考
         * https://cmegroup-tools.quikstrike.net/User/QuikStrikeView.aspx?qsid=0a56e4e3-4ae7-4af4-b828-f79eeb83f456&insid=196261620
         * https://www.investing.com/central-banks/fed-rate-monitor
         */
        String apiKey = keyField.getText().trim(); // API Key
        if (apiKey.isEmpty()) {
            showAlert("請輸入 FRED API Key");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.runAsync(() -> {
            FedWatchService.FedWatchResult data = FedWatchService.getProbability(apiKey);

            Platform.runLater(() -> {
                resultArea.clear();
                resultArea.appendText(data.fullText);

                if (!data.labels.isEmpty() && !data.probabilities.isEmpty()) {
                    chartPane.setContent(createFedRatePieChart(data));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法
                } else {
                    chartPane.setContent(createEmptyChartPanel());
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                resultArea.clear();
                resultArea.appendText("【聯準會利率期貨機率】查詢失敗\n" + ex.getMessage() + "\n");
                chartPane.setContent(createEmptyChartPanel());
            });
            return null;
        });
    }

    // 創建 聯準會利率 圓餅圖
    private Node createFedRatePieChart(FedWatchService.FedWatchResult data) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // DefaultCategoryDataset：JFreeChart 的資料集類別，用於類別型資料（如 X=日期字符串，Y=數值），支援多系列。
            // 日期是離散類別（非連續時間），CategoryAxis 只顯示有資料的點，解決假日空白問題。
            DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
            Color[] colors = { new Color(0, 120, 0), new Color(144, 238, 144) };

            for (int i = 0; i < data.labels.size(); i++) {
                dataset.setValue(data.labels.get(i), data.probabilities.get(i));
            }

            // JFreeChart 核心工廠，生成線圖（CategoryPlot 類型）。
            JFreeChart chart = ChartFactory.createPieChart(
                "聯準會利率期貨隱含機率 - " + data.meetingDate,
                dataset,
                true, true, false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            // PiePlot plot = (PiePlot) chart.getPlot();
            @SuppressWarnings("unchecked")
            PiePlot<String> plot = (PiePlot<String>) chart.getPlot();

            // 設定字型以利解決亂碼問題
            Font font = new Font("Microsoft JhengHei", Font.BOLD, 14);
            chart.getTitle().setFont(new Font("Microsoft JhengHei", Font.BOLD, 18));
            chart.getLegend().setItemFont(font);
            plot.setLabelFont(font);

            plot.setBackgroundPaint(Color.WHITE);

            for (int i = 0; i < data.labels.size(); i++) {
                plot.setSectionPaint(data.labels.get(i), colors[i % colors.length]);
            }

            plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
                "{0}: {1} ({2})",
                NumberFormat.getPercentInstance(),
                NumberFormat.getPercentInstance()
            ));

            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            chartPanel.setMouseWheelEnabled(true);

            currentChartPanel = chartPanel;
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

        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 查 VIX 恐慌指數
    private void queryVix() {
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

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

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> {
            YahooFinanceService yahooService = new YahooFinanceService();
            List<YahooFinanceService.YahooCandle> candles = yahooService.fetchHistory("^VIX", days);

            if (candles.isEmpty()) {
                throw new RuntimeException("無法取得 VIX 資料，請檢查網路或稍後再試");
            }

            // 直接在背景執行緒計算統計值
            double maxClose = candles.stream().mapToDouble(c -> c.close()).max().orElse(0.0);
            double minClose = candles.stream().mapToDouble(c -> c.close()).min().orElse(0.0);

            YahooFinanceService.YahooCandle maxCandle = candles.stream()
                    .max(Comparator.comparingDouble(c -> c.close()))
                    .orElse(candles.get(0));
            YahooFinanceService.YahooCandle minCandle = candles.stream()
                    .min(Comparator.comparingDouble(c -> c.close()))
                    .orElse(candles.get(0));

            // 用 Map 包裝傳回 UI 執行緒（避免自訂 record）
            return Map.of(
                "candles", candles,
                "maxClose", maxClose,
                "minClose", minClose,
                "maxDate", maxCandle.date(),
                "minDate", minCandle.date()
            );

        }).thenAcceptAsync(resultMap -> Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            List<YahooFinanceService.YahooCandle> candles =
                    (List<YahooFinanceService.YahooCandle>) resultMap.get("candles");
            double maxClose = (double) resultMap.get("maxClose");
            double minClose = (double) resultMap.get("minClose");
            LocalDate maxDate = (LocalDate) resultMap.get("maxDate");
            LocalDate minDate = (LocalDate) resultMap.get("minDate");

            StringBuilder sb = new StringBuilder();
            sb.append("VIX 恐慌指數 線圖已載入（近 ").append(candles.size()).append(" 日走勢）。\n\n");

            // 直接使用 YahooFinanceService.YahooCandle 迭代
            for (YahooFinanceService.YahooCandle c : candles) {
                sb.append(String.format("日期：%s\n", c.date()))
                  .append(String.format("開盤指數：%.2f\n", c.open()))
                  .append(String.format("最高指數：%.2f\n", c.high()))
                  .append(String.format("最低指數：%.2f\n", c.low()))
                  .append(String.format("收盤指數：%.2f\n\n", c.close()));
            }

            sb.append(String.format("區間最高指數：%.2f（%s）\n", maxClose, maxDate))
              .append(String.format("區間最低指數：%.2f（%s）\n", minClose, minDate));

            sb.append("\n＊恐慌指數\n\n")
              .append("是衡量市場對未來30天標準普爾500指數波動性預期的指標。它被廣泛認為是市場恐慌和不確定性的指標，並提供了關於市場風險的有力信號。\n\n")
              .append("＊常態區間\n\n")
              .append("通常保持在10-20之間。\n\n")
              .append("＊警戒區間\n\n")
              .append("當超過20時，投資者應注意市場可能出現較大波動。\n\n")
              .append("＊恐慌區間\n\n")
              .append("當超過30，尤其是40以上，市場已經進入高度恐慌階段，並可能伴隨大規模拋售和市場崩盤風險。");

            resultArea.setText(sb.toString());
            resultArea.appendText(""); // 自動滾動到最底部

            // 恐慌指數圖表
            chartPane.setContent(createCommonLineChart(
                candles, // 資料來源
                "VIX",
                "恐慌指數",
                new Color(178, 34, 34),
                candle -> ((YahooFinanceService.YahooCandle) candle).close(),
                candle -> ((YahooFinanceService.YahooCandle) candle).date()
            ));
            resizeChartProportionally(); // 改用統一的等比例縮放方法

        }), Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> showAlert(ex.getMessage()));
            return null;
        });
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

        SwingUtilities.invokeLater(() -> {
            // 調整 ChartPanel 實際尺寸（等比例）
            currentChartPanel.setPreferredSize(
                new java.awt.Dimension((int)chartWidth, (int)chartHeight)
            );

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

        // 延遲 setVisible，給 Swing 初始化時間
        PauseTransition delay = new PauseTransition(Duration.millis(400));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();
    }

    // 創建空圖表面板
    private Node createEmptyChartPanel() {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            JFreeChart emptyChart = ChartFactory.createLineChart("", "", "", new DefaultCategoryDataset());
            ChartPanel panel = new ChartPanel(emptyChart);
            panel.setPreferredSize(new java.awt.Dimension(695, 400));

            swingNode.setContent(panel);
            currentChartPanel = panel;
        });

        // 直接監聽 scene + window + 延遲 1200ms 強制重繪
        swingNode.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                PauseTransition finalForce = new PauseTransition(Duration.millis(1200));
                finalForce.setOnFinished(e -> {
                    Platform.runLater(() -> {
                        SwingUtilities.invokeLater(() -> {
                            ChartPanel p = (ChartPanel) swingNode.getContent();
                            if (p != null) {
                                p.setSize(695, 400);
                                p.revalidate();
                                p.repaint();
                            }
                        });
                    });
                });
                finalForce.play();
            }
        });

        return swingNode;
    }

    private void showAlert(String message, AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setHeaderText("");
            alert.setTitle(switch (type) {
                case ERROR -> "錯誤";
                case INFORMATION -> "資訊";
                case WARNING -> "警告";
                case CONFIRMATION -> "確認";
                default -> "提示";
            });

            // 設定 ALERT 視窗上左上角的小圖
            Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alertStage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icon.png"))
            ));

            alert.showAndWait();
        });
    }

    // 重載
    private void showAlert(String message) {
        showAlert(message, AlertType.ERROR);
    }

    private TextInputDialog createStyledInputDialog(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        // 等 dialog 真正顯示出來後，再抓 Stage 設圖示
        dialog.showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
            if (isNowShowing && !wasShowing) {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icon.png"))
                ));
            }
        });

        return dialog;
    }

    // JVM 的要求：所有 Java 應用程式必須有一個 public static void main(String[] args) 作為啟動入口
    // JavaFX 的特殊性：JavaFX 應用程式繼承 Application 類別，但仍需要 main() 來橋接傳統 Java 啟動方式
    // mvn javafx:run 的關係：Maven 會讀取 pom.xml 中 javafx-maven-plugin 中 <mainClass> 的設定值，找到 MainApp.main() 並執行
    // 與 .exe 安裝檔的關係：跟 run 差不多，啟動時執行 com.example.MainApp.main()
    public static void main(String[] args) {
        launch(args);
    }
}