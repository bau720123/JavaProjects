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
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.TextTitle;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

import java.awt.Font;
import java.awt.Color;  // 顏色設定用於 MACD 圖表線條
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.jfree.chart.axis.NumberAxis;

public class MainApp extends Application {
    private final FugleService service = new FugleService(); // 使用 Fugle API 做資料存取
    private TextField symbolField; // 股票代號
    private PasswordField keyField; // API Key
    private TextField daysField; // 天數輸入欄位（共用給歷史 K 線、RSI、MACD）
    private TextArea resultArea; // 文字顯示區塊
    private ScrollPane chartPane; // 圖表顯示區塊
    private BorderPane root;  // 讓 queryHistory() 可存取
    private ChartPanel currentChartPanel;  // 存取 ChartPanel 成員，允許多次 repaint
    private Stage primaryStage;  // 將 stage 升級為類別成員變數，讓 createLineChart 可存取

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

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

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

        // 查聯準會利率 按鈕
        Button fedRateBtn = new Button("查聯準會利率");
        fedRateBtn.setPrefWidth(120);
        fedRateBtn.setOnAction(e -> queryFedRateProbability());

        // 查 VIX 恐慌指數 按鈕
        Button vixBtn = new Button("查 VIX 恐慌指數");
        vixBtn.setPrefWidth(120);
        vixBtn.setOnAction(e -> queryVix());

        buttonBox.getChildren().addAll(queryBtn, historyBtn, smaBtn, rsiBtn, macdBtn, bollingerBtn, institutionalBtn, foreignNetBtn, fedRateBtn, vixBtn); // 添加子節點到容器的操作

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
                // String original = resultArea.getText();
                resultArea.setText(eventMsg);

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

            double minValue = Double.MAX_VALUE;
            double maxValue = Double.MIN_VALUE;

            // 迴圈填充 dataset：從 data List 迭代，每個 data 轉日期字符串 + 收盤價。
            // 目的：建 X=日期類別，Y=close 數值系列 "收盤價走勢"。
            for (int i = 0; i < data.size(); i++) {
                Object item = data.get(i); // 取得單日資料記錄
                double value = valueExtractor.applyAsDouble(item);
                LocalDate localDate = dateExtractor.apply(item); // 取出 LocalDate

                // 強制轉成統一格式的字串
                String dateStr = sdf.format(Date.from(
                    localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                ));

                dataset.addValue(value, titlePrefix, dateStr); // 用日期字符串作為類別（X 軸標籤），Y 為 value 值

                // 第二條線
                if (secondaryValueExtractor != null && secondarySeriesName != null && secondaryColor != null) {
                    double secondaryValue = secondaryValueExtractor.applyAsDouble(item);
                    dataset.addValue(secondaryValue, secondarySeriesName, dateStr);
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

            // if (titlePrefix.contains("RSI")) {
            if ("RSI 指標" .equals(titlePrefix)) {
                minValue = 0;
                maxValue = 100;
            }

            plot.getRangeAxis().setLowerBound(minValue - padding); // 下限
            plot.getRangeAxis().setUpperBound(maxValue + padding); // 上限

            // 線條樣式設定
            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, lineColor);
            renderer.setSeriesStroke(0, new BasicStroke(2.5f));

            // 設定第二條線的樣式（如果存在）
            if (secondarySeriesName != null) {
                renderer.setSeriesPaint(1, secondaryColor);
                renderer.setSeriesStroke(1, new BasicStroke(2.0f));  // 信號線細一點
            }

            // CategoryAxis：X 軸類別軸，處理日期標籤位置。
            CategoryAxis domainAxis = (CategoryAxis) plot.getDomainAxis();
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90); // 日期標籤垂直顯示（UP_90），避免擁擠（依需調整為 STANDARD 或 DOWN_90）

            // 建立 ChartPanel 並設定大小
            currentChartPanel = new ChartPanel(chart);
            currentChartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
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

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchHistory(symbol, days, apiKey))
                .thenAccept(candles -> Platform.runLater(() -> {
                    if (!candles.isEmpty()) {
                        LocalDate today = LocalDate.now();
                        boolean hasToday = candles.stream().anyMatch(c -> c.date().equals(today));

                        if (!hasToday) {
                            Quote quote = service.fetchQuote(symbol, apiKey); // 即時報價

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

                        StringBuilder sb = new StringBuilder(String.format("歷史 K 線圖已載入（近 %d 日走勢）。\n\n", candles.size()));
                        for (Candle c : candles) {
                            String tag = c.date().equals(today) && !hasToday ? "（盤中預估）" : "";
                            sb.append(String.format("日期：%s%s\n開盤價：%.1f\n最高價：%.1f\n最低價：%.1f\n收盤價：%.1f\n成交量：%d\n漲跌：%.1f\n\n",
                                c.date(), tag, c.open(), c.high(), c.low(), c.close(), c.volume(), c.change()));
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

                        sb.append(String.format("區間最高價：%.1f（%s）\n", maxHigh, maxHighDateStr)); // 格式化添加（%.1f 保留1位小數）
                        sb.append(String.format("區間最低價：%.1f（%s）\n", minLow, minLowDateStr)); // 格式化添加（%.1f 保留1位小數）

                        resultArea.setText(sb.toString()); // 設定完整文字

                        // K 線圖表
                        chartPane.setContent(createCommonLineChart(
                            candles,
                            "收盤價走勢",
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

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchSMA(symbol, days, apiKey))
                .thenAccept(smaList -> Platform.runLater(() -> {
                    if (!smaList.isEmpty()) {
                        LocalDate today = LocalDate.now();
                        boolean hasToday = smaList.stream().anyMatch(s -> s.date().equals(today));

                        if (!hasToday) {
                            Quote quote = service.fetchQuote(symbol, apiKey);
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

                        StringBuilder sb = new StringBuilder(String.format("簡單移動平均線（SMA）已載入（近 %d 日走勢）\n\n", smaList.size()));
                        for (SMA s : smaList) {
                            String tag = s.date().equals(today) && !hasToday ? "（盤中預估）" : "";
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

                        resultArea.setText(sb.toString()); // 設定完整文字

                        // SMA 圖表
                        chartPane.setContent(createCommonLineChart(
                            smaList,
                            "SMA 指標",
                            "價格",
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

                        StringBuilder sb = new StringBuilder(String.format("相對強弱指標 （RSI）已載入（近 %d 日走勢）。\n\n強弱指數如下：\n\n", rsiList.size())); // 使用 StringBuilder 可多行段落顯示，並且在字串相接時比較高效，無額外開銷
                        for (RSI r : rsiList) {
                            String tag = r.date().equals(today) && !hasToday ? "（盤中預估）" : "";
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

                        sb.append("\n* 超買與超賣：\n");
                        sb.append("  當RSI 顯示超買時（通常大於70），可能表示市場過熱，價格有回調的可能，是賣出訊號。 反之，當RSI 顯示超賣時（通常小於30），可能表示市場過冷，價格有上漲的潛力，是買入訊號。\n\n");
                        sb.append("* 市場趨勢：\n");
                        sb.append("  RSI 值越高，表示過去一段期間的上漲機率較大；值越小，則下跌機率較大。");

                        resultArea.setText(sb.toString());  // 設定完整文字

                        // MRSI 圖表
                        chartPane.setContent(createCommonLineChart(
                            rsiList,
                            "RSI 指標",
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

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchMACD(symbol, days, apiKey))
                .thenAccept(macdList -> Platform.runLater(() -> {
                    if (!macdList.isEmpty()) {
                        LocalDate today = LocalDate.now();
                        boolean hasToday = macdList.stream().anyMatch(m -> m.date().equals(today));

                        if (!hasToday) {
                            Quote quote = service.fetchQuote(symbol, apiKey);
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

                        StringBuilder sb = new StringBuilder(String.format("移動平均指標 （MACD）已載入（近 %d 日走勢）。\n\n", macdList.size()));
                        for (MACD m : macdList) {
                            String tag = m.date().equals(today) && !hasToday ? "（盤中預估）" : "";
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

                        sb.append("\n* 黃金交叉：\n");
                        sb.append("  當移動平均線（MACD）慢慢往上交叉信號線（signalLine）時發生。這通常被視為一個買進訊號，表示上漲趨勢可能增強。\n\n");
                        sb.append("* 死亡交叉：\n");
                        sb.append("  當移動平均線（MACD）慢慢往下交叉信號線（signalLine）時發生。這通常被視為一個賣出訊號，表示下跌趨勢可能增強。");

                        resultArea.setText(sb.toString());  // 設定完整文字

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
            if (days < 1) {
                showAlert("天數必須為 1 以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（1 以上）");
            return;
        }

        resultArea.setText("正在載入布林通道，請稍候…");
        chartPane.setVisible(false);

        CompletableFuture
            .supplyAsync(() -> service.fetchBollinger(symbol, days, apiKey))
            .thenAccept(bbList -> Platform.runLater(() -> {
                if (!bbList.isEmpty()) {
                    // Bollinger 盤中預估（基於資料歸檔，Fugle 的 API最其碼要在今天收盤之後，才會進行歸檔，在那之前，是不會有今天的資料的）
                    LocalDate today = LocalDate.now();
                    List<Candle> candles = service.fetchHistory(symbol, days, apiKey);
                    boolean hasToday = candles.stream().anyMatch(c -> c.date().equals(today));

                    if (!hasToday) {
                        Quote quote = service.fetchQuote(symbol, apiKey);
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

                    // === 文字區塊 ===
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("布林通道 （Bollinger Bands）已載入（近 %d 日走勢）。\n\n", bbList.size()));
                    sb.append("布林通道指數如下：\n\n");

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    for (Bollinger b : bbList) {
                        String dateStr = sdf.format(Date.from(b.date().atStartOfDay(ZoneId.systemDefault()).toInstant()));
                        sb.append(String.format("日期：%s\n", dateStr));
                        sb.append(String.format("   上軌：%.4f\n", b.upper()));
                        sb.append(String.format("   中軌：%.4f\n", b.middle()));
                        sb.append(String.format("   下軌：%.4f\n\n", b.lower()));
                    }

                    sb.append("* 買入訊號：當股價觸及下軌並有反彈跡象時，可能是一個買入訊號\n");
                    sb.append("* 賣出訊號：當股價觸及上軌並有回落跡象時，可能是一個賣出訊號。");

                    resultArea.setText(sb.toString());

                    // === 畫圖 ===
                    Node chartNode = createBollingerWithCandlesChart(candles, bbList);
                    chartPane.setContent(chartNode);

                    // 跟 queryMACD 一樣優雅處理圖表顯示
                    resizeChartProportionally();

                    PauseTransition delayVisible = new PauseTransition(Duration.millis(400));
                    delayVisible.setOnFinished(e -> chartPane.setVisible(true));
                    delayVisible.play();
                } else {
                    resultArea.setText("布林通道資料載入失敗，請稍後再試\n若 API 不可用，請確認 API key 有效。");
                }
            }))
            .exceptionally(ex -> {
                // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                Platform.runLater(() -> showAlert("系統異常，請稍後再試：" + ex.getMessage()));
                return null;
            });
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
        resultArea.setText("查三大法人買賣超數，載入中，請稍候...");
        chartPane.setVisible(false);

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
                sb.append("【三大法人買賣超】 ").append(symbol).append("\n\n");
                sb.append("三大法人買賣超如下：\n\n");

                for (String[] row : displayAndChartData) {
                    int trust = Integer.parseInt(row[1]);
                    int dealer = Integer.parseInt(row[2]);
                    int foreign = Integer.parseInt(row[3]);

                    dates.add(row[0]);
                    trustList.add(trust);
                    dealerList.add(dealer);
                    foreignList.add(foreign);

                    sb.append(String.format("日期：%s\n", row[0]));
                    sb.append(String.format("投信：%,d\n", trust));
                    sb.append(String.format("自營商：%,d\n", dealer));
                    sb.append(String.format("外資：%,d\n\n", foreign));

                    // 極值更新
                    if (trust > maxTrust) { maxTrust = trust; maxTrustDate = row[0]; }
                    if (dealer > maxDealer) { maxDealer = dealer; maxDealerDate = row[0]; }
                    if (foreign > maxForeign) { maxForeign = foreign; maxForeignDate = row[0]; }
                    if (trust < minTrust) { minTrust = trust; minTrustDate = row[0]; }
                    if (dealer < minDealer) { minDealer = dealer; minDealerDate = row[0]; }
                    if (foreign < minForeign) { minForeign = foreign; minForeignDate = row[0]; }
                }

                sb.append("[買超]\n");
                sb.append(String.format("區間最大（投信）：%,d（%s）\n", maxTrust, maxTrustDate));
                sb.append(String.format("區間最大（自營商）：%,d（%s）\n", maxDealer, maxDealerDate));
                sb.append(String.format("區間最大（外資）：%,d（%s）\n", maxForeign, maxForeignDate));

                sb.append("\n[賣超]\n");
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
                        resizeChartProportionally();

                        PauseTransition delay = new PauseTransition(Duration.millis(400));
                        delay.setOnFinished(e -> chartPane.setVisible(true));
                        delay.play();
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
                dataset.addValue(trust.get(i), "投信", dates.get(i));
                dataset.addValue(dealer.get(i), "自營商", dates.get(i));
                dataset.addValue(foreign.get(i), "外資", dates.get(i));
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(
                "三大法人買賣超（張）",
                "日期",
                "買賣超（張）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
            );

            Font chineseFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            chart.getTitle().setFont(new Font("Microsoft JhengHei", Font.BOLD, 18));
            chart.getLegend().setItemFont(chineseFont);

            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.getDomainAxis().setLabelFont(chineseFont);
            plot.getDomainAxis().setTickLabelFont(chineseFont);
            plot.getRangeAxis().setLabelFont(chineseFont);
            plot.getRangeAxis().setTickLabelFont(chineseFont);
            plot.getDomainAxis().setCategoryLabelPositions(
                CategoryLabelPositions.UP_90  // ← 你原本所有圖表都用的神技！
            );

            // 顏色設定
            plot.getRenderer().setSeriesPaint(0, new Color(255, 100, 100)); // 投信 紅
            plot.getRenderer().setSeriesPaint(1, new Color(100, 100, 255)); // 自營商 藍
            plot.getRenderer().setSeriesPaint(2, new Color(0, 180, 0));     // 外資 綠

            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 400));
            currentChartPanel = chartPanel;
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

        resultArea.setText("查外資大盤空單數，載入中，請稍候...");
        chartPane.setVisible(false);

        CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = Jsoup.connect("https://stock.wearn.com/taifexphoto.asp")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(12000)
                        .get();

                Element table = doc.selectFirst("table.taifexphoto");
                if (table == null) {
                    return new ForeignNetData("錯誤：無法找到外資空單數表格（網站可能改版）", null, null);
                }

                // 原始資料
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
                            String adDate = year + dateStr.substring(3); // 2025/11/21

                            try {
                                int net = Integer.parseInt(foreignStr);
                                originalDates.add(adDate);
                                originalNet.add(net);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                if (originalDates.isEmpty()) {
                    return new ForeignNetData("錯誤：未解析到任何外資空單數資料", null, null);
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
                    if (i < originalNet.size() - 1) {
                        changes.add(originalNet.get(i) - originalNet.get(i + 1));
                    } else {
                        changes.add(0);
                    }
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
                    int startIndex = ascendingDates.size() - days;
                    ascendingDates = ascendingDates.subList(startIndex, ascendingDates.size());
                    ascendingNet = ascendingNet.subList(startIndex, ascendingNet.size());
                    chartDates = chartDates.subList(startIndex, chartDates.size());
                    changes = changes.subList(startIndex, changes.size());
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

                // 文字區
                StringBuilder sb = new StringBuilder();
                sb.append("外資歷史空單數如下：\n\n");

                for (int i = 0; i < ascendingDates.size(); i++) {
                    String displayDate = ascendingDates.get(i).replace("/", "-"); // 轉成 yyyy-MM-dd
                    sb.append(String.format("日期：%s\n空單數：%,d\n增減：%,d\n\n",
                            displayDate, ascendingNet.get(i), changes.get(i)));
                }

                sb.append(String.format("區間最高空單數：%,d（%s）\n",
                        highestNet, String.join("、", highestDates.stream()
                                .map(d -> d.replace("/", "-"))
                                .collect(Collectors.toList()))));
                sb.append(String.format("區間最低空單數：%,d（%s）\n",
                        lowestNet, String.join("、", lowestDates.stream()
                                .map(d -> d.replace("/", "-"))
                                .collect(Collectors.toList()))));

                return new ForeignNetData(sb.toString(), chartDates, ascendingNet);

            } catch (Exception e) {
                return new ForeignNetData("爬蟲失敗：" + e.getMessage(), null, null);
            }
        }).thenAccept(data -> Platform.runLater(() -> {
            resultArea.setText(data.text);

            if (data.dates != null && data.netPositions != null) {
                chartPane.setContent(createForeignNetLineChart(data.dates, data.netPositions));
                resizeChartProportionally();
                PauseTransition delay = new PauseTransition(Duration.millis(400));
                delay.setOnFinished(e -> chartPane.setVisible(true));
                delay.play();
            }
        }));
    }

    // 外資空單數折線圖（Y 軸倒置 + 自動範圍 + 與 K 線完全一致）
    private Node createForeignNetLineChart(List<String> dates, List<Integer> netPositions) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            for (int i = 0; i < dates.size(); i++) {
                dataset.addValue(netPositions.get(i), "外資淨空單數", dates.get(i));
            }

            JFreeChart chart = ChartFactory.createLineChart(
                "外資台指期淨空單數趨勢圖",
                "日期",
                "淨空單數",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false
            );

            CategoryPlot plot = chart.getCategoryPlot();

            // Y 軸倒置（數值越小越在上方）
            plot.getRangeAxis().setInverted(true);

            // 自動調整範圍 + 10% padding
            if (!netPositions.isEmpty()) {
                int min = netPositions.stream().mapToInt(Integer::intValue).min().orElse(0);
                int max = netPositions.stream().mapToInt(Integer::intValue).max().orElse(0);
                int range = max - min;
                int padding = range == 0 ? 2000 : (int) (range * 0.1);

                plot.getRangeAxis().setLowerBound(min - padding);
                plot.getRangeAxis().setUpperBound(max + padding);
            }

            Font font = new Font("Microsoft YaHei", Font.BOLD, 14);
            chart.getTitle().setFont(font);
            plot.getDomainAxis().setLabelFont(font);
            plot.getRangeAxis().setLabelFont(font);

            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, Color.BLUE);
            renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.5f));

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

    private static class ForeignNetData {
        final String text;
        final List<String> dates;
        final List<Integer> netPositions;

        ForeignNetData(String text, List<String> dates, List<Integer> netPositions) {
            this.text = text;
            this.dates = dates;
            this.netPositions = netPositions;
        }
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
        resultArea.appendText("【聯準會利率期貨隱含機率】\n查詢中，請稍候...\n");

        CompletableFuture.runAsync(() -> {
            FedWatchService.FedWatchResult data = FedWatchService.getProbability(apiKey);

            Platform.runLater(() -> {
                resultArea.clear();
                resultArea.appendText(data.fullText);

                if (!data.labels.isEmpty() && !data.probabilities.isEmpty()) {
                    Node pieChart = createFedRatePieChart(data);
                    chartPane.setContent(pieChart);
                    resizeChartProportionally();
                    PauseTransition delay = new PauseTransition(Duration.millis(400));
                    delay.setOnFinished(e -> chartPane.setVisible(true));
                    delay.play();
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

        CompletableFuture.supplyAsync(() -> {
            try {
                LocalDate today = LocalDate.now();
                LocalDate startDate = today.minusDays(days);

                long period1 = startDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
                long period2 = today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();

                String url = String.format(
                    "https://query1.finance.yahoo.com/v8/finance/chart/%%5EVIX" +
                    "?period1=%d&period2=%d&interval=1d&events=history&includeAdjustedClose=true",
                    period1, period2
                );

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new RuntimeException("HTTP " + response.code());

                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode result = root.path("chart").path("result").get(0);
                    JsonNode timestamps = result.path("timestamp");
                    JsonNode meta = result.path("meta");
                    JsonNode opens = result.path("indicators").path("quote").get(0).path("open");
                    JsonNode highs = result.path("indicators").path("quote").get(0).path("high");
                    JsonNode lows = result.path("indicators").path("quote").get(0).path("low");
                    JsonNode closes = result.path("indicators").path("quote").get(0).path("close");

                    List<VixCandle> candles = new ArrayList<>();
                    double maxClose = Double.MIN_VALUE;
                    double minClose = Double.MAX_VALUE;
                    LocalDate maxDate = null, minDate = null;

                    for (int i = 0; i < timestamps.size(); i++) {
                        long ts = timestamps.get(i).asLong();
                        LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(ts), ZoneId.of("UTC"));

                        double o = opens.get(i).asDouble(0);
                        double h = highs.get(i).asDouble(0);
                        double l = lows.get(i).asDouble(0);
                        double c = closes.get(i).asDouble(0);

                        if (o > 0 && h > 0 && l > 0 && c > 0 && date.isBefore(today.plusDays(1))) {
                            candles.add(new VixCandle(date, o, h, l, c));
                            if (c > maxClose) { maxClose = c; maxDate = date; }
                            if (c < minClose) { minClose = c; minDate = date; }
                        }
                    }

                    // 補今日即時價（若無收盤）
                    double realtime = meta.path("regularMarketPrice").asDouble();
                    if (realtime > 0 && (candles.isEmpty() || !candles.get(candles.size()-1).date().equals(today))) {
                        candles.add(new VixCandle(today, realtime, realtime, realtime, realtime));
                        if (realtime > maxClose) { maxClose = realtime; maxDate = today; }
                        if (realtime < minClose) { minClose = realtime; minDate = today; }
                    }

                    return new VixResult(candles, maxClose, minClose, maxDate, minDate);

                } catch (Exception e) {
                    throw new RuntimeException("VIX API 失敗: " + e.getMessage());
                }
            } catch (Exception e) {
                throw new RuntimeException("VIX 資料解析錯誤: " + e.getMessage());
            }
        }).thenAcceptAsync(vix -> Platform.runLater(() -> {
            if (vix.candles().isEmpty()) {
                showAlert("無法取得 VIX 資料，請檢查網路");
                return;
            }

            // === 文字區塊顯示 ===
            StringBuilder sb = new StringBuilder();
            sb.append("歷史 K 線圖已載入（近 ").append(vix.candles().size()).append(" 日走勢）。\n\n");

            for (VixCandle c : vix.candles()) {
                sb.append(String.format("日期：%s\n", c.date()))
                .append(String.format("開盤指數：%.2f\n", c.open()))
                .append(String.format("最高指數：%.2f\n", c.high()))
                .append(String.format("最低指數：%.2f\n", c.low()))
                .append(String.format("收盤指數：%.2f\n\n", c.close()));
            }

            sb.append(String.format("區間最高指數：%.2f（%s）\n", vix.maxClose(), vix.maxDate()))
            .append(String.format("區間最低指數：%.2f（%s）\n", vix.minClose(), vix.minDate()));

            sb.append("\n* 恐慌指數：\n");
            sb.append("  是衡量市場對未來30天標準普爾500指數波動性預期的指標。它被廣泛認為是市場恐慌和不確定性的指標，並提供了關於市場風險的有力信號。\n\n");
            sb.append("* 常態區間：\n");
            sb.append("  通常保持在10-20之間。\n\n");
            sb.append("* 警戒區間：\n");
            sb.append("  當超過20時，投資者應注意市場可能出現較大波動。\n\n");
            sb.append("* 恐慌區間：\n");
            sb.append("  當超過30，尤其是40以上，市場已經進入高度恐慌階段，並可能伴隨大規模拋售和市場崩盤風險。");

            resultArea.setText(sb.toString());

            // === 圖表區塊顯示 ===
            chartPane.setVisible(true);
            chartPane.setContent(createVixChart(vix.candles()));
            resizeChartProportionally(); // 改用統一的等比例縮放方法

        }), Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> showAlert(ex.getMessage()));
            return null;
        });
    }

    // 輔助 record
    record VixCandle(LocalDate date, double open, double high, double low, double close) {}
    record VixResult(List<VixCandle> candles, double maxClose, double minClose, LocalDate maxDate, LocalDate minDate) {}

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
                private final Comparable seriesKey = "股價";

                @Override public int getSeriesCount() { return 1; }
                @Override public Comparable getSeriesKey(int series) { return seriesKey; }
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
            candleRenderer.setUpPaint(Color.GREEN);
            candleRenderer.setDownPaint(Color.RED);
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
                    "布林通道 + K線圖（近 " + candles.size() + " 日）",
                    new Font("Microsoft JhengHei", Font.BOLD, 18),
                    plot,
                    true
            );

            // 字體
            Font chineseFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            Font legendFont = new Font("Microsoft JhengHei", Font.BOLD, 15);
            plot.getDomainAxis().setLabelFont(chineseFont);
            plot.getRangeAxis().setLabelFont(chineseFont);
            chart.getLegend().setItemFont(legendFont);

            // ChartPanel
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 420));
            chartPanel.setMouseWheelEnabled(true);
            chartPanel.setRangeZoomable(false);

            currentChartPanel = chartPanel;
            swingNode.setContent(chartPanel);

            // 解決 SwingNode 延遲
            Timer timer = new Timer(180, e -> {
                chartPanel.revalidate();
                chartPanel.repaint();
                ((Timer) e.getSource()).stop();
            });
            timer.setRepeats(false);
            timer.start();
        });

        return swingNode;
    }

    // 創建 聯準會利率 圓餅圖
    private Node createFedRatePieChart(FedWatchService.FedWatchResult data) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
            Color[] colors = { new Color(0, 120, 0), new Color(144, 238, 144) };

            for (int i = 0; i < data.labels.size(); i++) {
                dataset.setValue(data.labels.get(i), data.probabilities.get(i));
            }

            JFreeChart chart = ChartFactory.createPieChart(
                "聯準會利率期貨隱含機率 - " + data.meetingDate,
                dataset,
                true, true, false
            );

            Font chineseFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            chart.getTitle().setFont(new Font("Microsoft JhengHei", Font.BOLD, 18));
            chart.getLegend().setItemFont(chineseFont);

            PiePlot plot = (PiePlot) chart.getPlot();
            plot.setLabelFont(chineseFont);
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

    // 繪製 VIX 收盤走勢圖
    private Node createVixChart(List<VixCandle> candles) {
        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            XYSeries closeSeries = new XYSeries("VIX 收盤指數");
            XYSeries line20 = new XYSeries("20 - 安全區");
            XYSeries line30 = new XYSeries("30 - 警戒區");
            XYSeries line40 = new XYSeries("40 - 恐慌區");

            String[] dateLabels = new String[candles.size()];
            double maxClose = Double.MIN_VALUE;
            double minClose = Double.MAX_VALUE;

            for (int i = 0; i < candles.size(); i++) {
                VixCandle c = candles.get(i);
                double x = i;
                closeSeries.add(x, c.close());
                dateLabels[i] = c.date().format(java.time.format.DateTimeFormatter.ofPattern("YYYY-MM-dd"));

                if (c.close() > maxClose) maxClose = c.close();
                if (c.close() < minClose) minClose = c.close();

                // 為每條警戒線補滿點（讓它橫跨整個圖表）
                line20.add(x, 20.0);
                line30.add(x, 30.0);
                line40.add(x, 40.0);
            }

            XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(closeSeries);
            dataset.addSeries(line20);
            dataset.addSeries(line30);
            dataset.addSeries(line40);

            JFreeChart chart = ChartFactory.createXYLineChart(
                    "VIX 恐慌指數走勢圖（近 " + candles.size() + " 日）",
                    "日期",
                    "收盤指數",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true, true, false
            );

            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

            // === X 軸：日期標籤 ===
            SymbolAxis domainAxis = new SymbolAxis("日期", dateLabels);
            domainAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
            domainAxis.setVerticalTickLabels(true);
            plot.setDomainAxis(domainAxis);

            // === Y 軸：自動範圍 + 10% 留白 ===
            NumberAxis rangeAxis = new NumberAxis("收盤指數");
            double range = maxClose - minClose;
            if (range == 0) range = maxClose * 0.2;
            // double padding = range * 0.1;
            double padding = (maxClose - minClose) * 0.05;  // 5% 緩衝空間（padding）：Y 軸上下留白，避免線貼邊
            rangeAxis.setRange(Math.max(0, minClose - padding), maxClose + padding);
            rangeAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
            plot.setRangeAxis(rangeAxis);

            // === 渲染器設定 ===
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            
            // 主走勢線（藍色粗線 + 圓點）
            renderer.setSeriesPaint(0, new Color(0, 80, 255));
            renderer.setSeriesStroke(0, new BasicStroke(3.0f));
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8));
            
            // 20 線 - 翠綠色
            renderer.setSeriesPaint(1, new Color(0, 180, 0));
            renderer.setSeriesStroke(1, new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));
            
            // 30 線 - 亮黃色
            renderer.setSeriesPaint(2, new Color(255, 200, 0));
            renderer.setSeriesStroke(2, new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));
            
            // 40 線 - 鮮紅色
            renderer.setSeriesPaint(3, new Color(220, 20, 60));
            renderer.setSeriesStroke(3, new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));

            plot.setRenderer(renderer);

            // === 中文標題與圖例 ===
            Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
            Font legendFont = new Font("Microsoft JhengHei", Font.BOLD, 14);
            chart.getTitle().setFont(titleFont);
            chart.getLegend().setItemFont(legendFont);

            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(695, 420));
            chartPanel.setMouseWheelEnabled(true);

            currentChartPanel = chartPanel;
            swingNode.setContent(chartPanel);

            // 修復延遲
            Timer timer = new Timer(150, e -> {
                chartPanel.revalidate();
                chartPanel.repaint();
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