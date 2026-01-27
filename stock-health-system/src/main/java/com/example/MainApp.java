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
import javafx.stage.StageStyle;
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
import com.example.HiStockService.FUTURESRealtime;
import com.example.HiStockService.HistoricalPE;
import com.example.HiStockService.MarginRecord;
import com.example.TaiFexService.TaifexQuote;

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
    private TextField daysField; // 天數輸入欄位
    private TextArea resultArea; // 文字顯示區塊
    private ScrollPane chartPane; // 圖表顯示區塊
    private BorderPane root;
    private ChartPanel currentChartPanel; // 存取 ChartPanel 成員，允許多次 repaint
    private Stage primaryStage; // 將 stage 升級為類別成員變數，讓 createLineChart 可存取

    // 在類別載入時讀取版本號
    private static String APP_VERSION = "Unknown";
    private static String DEFAULT_SYMBOL = "";
    private static String DEFAULT_API_KEY = "";
    private static String DEFAULT_DAY = "";
    static {
        try (InputStream input = MainApp.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                APP_VERSION = prop.getProperty("app.version", "Unknown");
                DEFAULT_SYMBOL = prop.getProperty("default-symbol", "");
                DEFAULT_API_KEY = prop.getProperty("default-api-key", "");
                DEFAULT_DAY = prop.getProperty("default-day", "");
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
        symbolField = new TextField(DEFAULT_SYMBOL);
        symbolField.setPromptText("請輸入股票代號");
        symbolField.setPrefWidth(155);
        symbolVBox.getChildren().addAll(symbolLabel, symbolField);

        // API Key 輸入
        VBox keyVBox = new VBox(5);
        Label keyLabel = new Label("API Key：");
        keyField = new PasswordField();
        keyField.setText(DEFAULT_API_KEY);
        keyField.setPromptText("請輸入 API Key");
        keyField.setPrefWidth(200);
        keyVBox.getChildren().addAll(keyLabel, keyField);

        // 天數輸入
        VBox daysVBox = new VBox(5);
        Label daysLabel = new Label("資料範圍：");
        daysField = new TextField(DEFAULT_DAY);
        daysField.setPromptText("天數");
        daysField.setPrefWidth(50);
        daysVBox.getChildren().addAll(daysLabel, daysField);

        inputBox.getChildren().addAll(symbolVBox, keyVBox, daysVBox); // 添加子節點到容器的操作
        root.setTop(inputBox); // 將 inputBox 設定為根容器的頂部區域。結果：輸入區固定在上方視窗，無論視窗resize，BorderPane會自動拉伸中間/底部內容。

        /* 下方左側版面配置（功能列表），改用 Accordion 實現可折疊群組 */
        Accordion accordion = new Accordion();
        accordion.setPrefWidth(155);
        //accordion.setMaxWidth(180);

        // 查股票
        TitledPane stockPane = new TitledPane();
        stockPane.setText("查股票");
        stockPane.setAnimated(true); // 展開/收合動畫

        VBox stockBox = new VBox(8);
        stockBox.setPadding(new Insets(8, 0, 5, 6));

        Button queryBtn = new Button("即時報價");
        queryBtn.setPrefWidth(140);
        queryBtn.setOnAction(e -> queryQuote());

        Button queryVolumeBtn = new Button("分價量表");
        queryVolumeBtn.setPrefWidth(140);
        queryVolumeBtn.setOnAction(e -> queryVolume());

        Button historyBtn = new Button("歷史K線");
        historyBtn.setPrefWidth(140);
        historyBtn.setOnAction(e -> queryHistory());

        Button institutionalBtn = new Button("三大法人買賣超");
        institutionalBtn.setPrefWidth(140);
        institutionalBtn.setOnAction(e -> queryInstitutionalTrading());

        Button smaBtn = new Button("簡單移動平均線");
        smaBtn.setPrefWidth(140);
        smaBtn.setOnAction(e -> querySMA());

        Button rsiBtn = new Button("相對強弱指數");
        rsiBtn.setPrefWidth(140);
        rsiBtn.setOnAction(e -> queryRSI());

        Button macdBtn = new Button("移動平均線");
        macdBtn.setPrefWidth(140);
        macdBtn.setOnAction(e -> queryMACD());

        Button bollingerBtn = new Button("布林通道");
        bollingerBtn.setPrefWidth(140);
        bollingerBtn.setOnAction(e -> queryBollinger());

        stockBox.getChildren().addAll(
            queryBtn, queryVolumeBtn, historyBtn, institutionalBtn, smaBtn,
            rsiBtn, macdBtn, bollingerBtn
        );

        // 用 ScrollPane 包起來
        ScrollPane stockScroll = new ScrollPane(stockBox);
        stockScroll.setFitToWidth(true);
        stockScroll.setFitToHeight(true);
        stockScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        stockScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stockScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        // stockScroll.setPrefViewportHeight(200); // 可選：限制最大顯示高度，強制出現滾動條

        stockPane.setContent(stockScroll);

        // 查大盤
        TitledPane marketPane = new TitledPane();
        marketPane.setText("查大盤");
        marketPane.setAnimated(true);

        VBox marketBox = new VBox(8);
        marketBox.setPadding(new Insets(8, 0, 5, 6));

        Button foreignNetBtn = new Button("外資空單數");
        foreignNetBtn.setPrefWidth(140);
        foreignNetBtn.setOnAction(e -> queryForeignNetPosition());

        Button institutionalMarketBtn = new Button("三大法人買賣超");
        institutionalMarketBtn.setPrefWidth(140);
        institutionalMarketBtn.setOnAction(e -> queryInstitutionalMarketTrading());

        Button FITXBtn = new Button("即時行情");
        FITXBtn.setPrefWidth(140);
        FITXBtn.setOnAction(e -> queryRealtimeQuotes());

        Button weightedBtn = new Button("加權指數");
        weightedBtn.setPrefWidth(140);
        weightedBtn.setOnAction(e -> queryYahooFinance("^TWII", "加權指數"));

        Button DowJonesBtn = new Button("道瓊工業指數");
        DowJonesBtn.setPrefWidth(140);
        DowJonesBtn.setOnAction(e -> queryYahooFinance("^DJI", "道瓊工業指數"));

        Button SP500Btn = new Button("標普500指數");
        SP500Btn.setPrefWidth(140);
        SP500Btn.setOnAction(e -> queryYahooFinance("^GSPC", "標普500指數"));

        Button NasDaqBtn = new Button("那斯達克指數");
        NasDaqBtn.setPrefWidth(140);
        NasDaqBtn.setOnAction(e -> queryYahooFinance("^IXIC", "那斯達克指數"));

        Button PHLXSemiconductorBtn = new Button("費城半導體指數");
        PHLXSemiconductorBtn.setPrefWidth(140);
        PHLXSemiconductorBtn.setOnAction(e -> queryYahooFinance("^SOX", "費城半導體指數"));

        Button TSMBtn = new Button("台積電ADR");
        TSMBtn.setPrefWidth(140);
        TSMBtn.setOnAction(e -> queryYahooFinance("TSM", "台積電ADR"));

        Button marginBtn = new Button("融資融券餘額");
        marginBtn.setPrefWidth(140);
        marginBtn.setOnAction(e -> queryMarginBalance());

        Button marginRateBtn = new Button("融資維持率");
        marginRateBtn.setPrefWidth(140);
        marginRateBtn.setOnAction(e -> queryMarginRate());

        Button comprehensiveAlertBtn = new Button("市場綜合警訊");
        comprehensiveAlertBtn.setPrefWidth(140);
        comprehensiveAlertBtn.setOnAction(e -> queryComprehensiveAlert());

        marketBox.getChildren().addAll(foreignNetBtn, institutionalMarketBtn, weightedBtn, FITXBtn, DowJonesBtn, SP500Btn, NasDaqBtn, PHLXSemiconductorBtn, TSMBtn, marginBtn, marginRateBtn, comprehensiveAlertBtn);

        // 用 ScrollPane 包起來
        ScrollPane marketScroll = new ScrollPane(marketBox);
        marketScroll.setFitToWidth(true);
        marketScroll.setFitToHeight(true);
        marketScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        marketScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        marketScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        // marketScroll.setPrefViewportHeight(150);

        marketPane.setContent(marketScroll);

        // 查經濟指數
        TitledPane econPane = new TitledPane();
        econPane.setText("查經濟指數");
        econPane.setAnimated(true);

        VBox econBox = new VBox(8);
        econBox.setPadding(new Insets(8, 0, 5, 6));

        Button fedRateBtn = new Button("聯準會利率");
        fedRateBtn.setPrefWidth(140);
        fedRateBtn.setOnAction(e -> queryFedRateProbability());

        Button vixBtn = new Button("VIX 恐慌指數");
        vixBtn.setPrefWidth(140);
        vixBtn.setOnAction(e -> queryVix());

        econBox.getChildren().addAll(fedRateBtn, vixBtn);

        // 用 ScrollPane 包起來
        ScrollPane econScroll = new ScrollPane(econBox);
        econScroll.setFitToWidth(true);
        econScroll.setFitToHeight(true);
        econScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        econScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        econScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        // econScroll.setPrefViewportHeight(100);

        econPane.setContent(econScroll);

        // 將所有群組加入 Accordion
        accordion.getPanes().addAll(stockPane, marketPane, econPane);

        // 程式啟動時預設展開「查股票」群組
        accordion.setExpandedPane(stockPane);

        root.setLeft(accordion);

        /* 下方右側版面配置（文字跟圖表顯示區），使用 HBox 水平排列 */
        HBox centerBox = new HBox(10); // 每個節點「水平」之間間隔 10 像素
        centerBox.setAlignment(Pos.TOP_LEFT);  // 改為 TOP_LEFT，讓內容頂左對齊

        // 文字區塊
        resultArea = new TextArea("歡迎使用台股健診系統\n\n請在上方輸入股票代號與 API Key\n\n完成後點擊查詢左方相關功能\n\n任何系統回饋請寄EMAIL：\nbau720123@gmail.com\n\n"); // 可設定文字區塊預設文字
        resultArea.setWrapText(true); // 設定當文字超過欄位的寬度時是否自動換行
        resultArea.setPrefRowCount(10); // 但JavaFX布局系統的響應式設計（responsive layout）會讓其根據視窗大小的變化來自動延展其高
        resultArea.setEditable(false); // 設定該文字區塊可否修改
        resultArea.setPrefWidth(200); // 寬度維持 200px
        HBox.setMargin(resultArea, new Insets(0, 0, 0, 10));  // 新增：向左微移 10px，盡可能對齊上方區塊位置

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
        Platform.runLater(() -> {
            root.requestFocus();
        });

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

        // 顯示載入提示，並取得 Alert 物件
        // Alert loading = showLoading("即時報價載入中", "正在查詢即時報價，請稍候...");

        // 顯示載入提示，並取得 Stage 物件
        // Stage loading = showCustomLoading("載入中，請稍候...");

        // 處裡非同步的操作，有點像是jQuery中的$.ajax(...)
        CompletableFuture.supplyAsync(() -> service.fetchQuote(symbol, apiKey))
            .thenAccept(quote -> Platform.runLater(() -> {
                if (quote != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("日期：%s\n", LocalDate.now())); // 無 date 
                    sb.append(String.format("股票代碼：%s\n股票名稱：%s\n\n上個收盤價：%.2f\n開盤價：%.2f\n最高價：%.2f\n最低價：%.2f\n現價：%.2f\n均價：%.2f\n漲跌：%.2f\n幅度：%.2f\n累計成交量：%d \n累計內盤成交量：%d \n累計外盤成交量：%d \n累計成交筆數：%d \n",
                            quote.symbol(), quote.name(), quote.previousClose(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice(),
                            quote.avgPrice(), quote.change(), quote.changePercent(), quote.tradeVolume(), quote.tradeVolumeAtBid(), quote.tradeVolumeAtAsk(), quote.transaction()));

                    // 委買價區段內容
                    sb.append("\n【委買價】\n\n");
                    for (BidAsk ba : quote.bids()) {
                        sb.append(String.format("    價格：%.2f\n    張數：%d\n\n", ba.price(), ba.size()));
                    }

                    // 委賣價區段內容
                    sb.append("【委賣價】\n\n");
                    for (BidAsk ba : quote.asks()) {
                        sb.append(String.format("    價格：%.2f\n    張數：%d\n\n", ba.price(), ba.size()));
                    }

                    // EPS 計算與估值參考
                    CompletableFuture.supplyAsync(() -> hiStockService.fetchQuarterlyEps(symbol))
                        .thenAccept(epsData -> Platform.runLater(() -> {
                            sb.append("【估值參考】\n\n");

                            if (epsData != null) {
                                double ttmEps = 0.0;
                                boolean useUserInput = false;

                                // 彈出輸入對話框
                                String stockInfo = quote.name() + "（" + quote.symbol() + "）";
                                TextInputDialog dialog = createStyledInputDialog(
                                    "全年度每股盈餘（EPS）（可選）",
                                    "輸入 " + stockInfo + " 的 全年度每股盈餘(EPS)",
                                    "請輸入法人預估的全年度 EPS（如 21.95）\n留空或填 0 則自動使用最近四季 TTM 計算"
                                );

                                Optional<String> result = dialog.showAndWait();
                                if (result.isPresent()) {
                                    String input = result.get().trim();
                                    if (!input.isEmpty()) {
                                        try {
                                            double inputEps = Double.parseDouble(input);
                                            if (inputEps > 0) {
                                                ttmEps = inputEps;
                                                useUserInput = true;
                                            }
                                        } catch (NumberFormatException ex) {
                                            // 輸入無效，維持使用當前股價
                                        }
                                    }
                                }

                                if (!useUserInput) {
                                    // 季度陣列：index 0=Q4, 1=Q3, 2=Q2, 3=Q1（從新到舊，便於找基準）
                                    double[] currentQuarters = { epsData.q4Current(), epsData.q3Current(), epsData.q2Current(), epsData.q1Current() };
                                    double[] previousQuarters = { epsData.q4Previous(), epsData.q3Previous(), epsData.q2Previous(), epsData.q1Previous() };
                                    System.err.println("當年度：" + epsData.q4Current() + ", " + epsData.q3Current() + ", " + epsData.q2Current() + ", " + epsData.q1Current());
                                    System.err.println("上個年度：" + epsData.q4Previous() + ", " + epsData.q3Previous() + ", " + epsData.q2Previous() + ", " + epsData.q1Previous());

                                    for (int i = 0; i < 4; i++) { // i=0: Q4, i=1: Q3, ...
                                        if (currentQuarters[i] != 0.0) {
                                            // 如果有當年季度的資料，就用當下的
                                            ttmEps += currentQuarters[i];
                                            System.err.println("使用當年度 Q" + (4-i) + "：" + currentQuarters[i]);
                                        } else {
                                            // 找最近的前一季作為基準（從 i+1 開始往前找）
                                            boolean foundRatio = false;
                                            for (int j = i + 1; j < 4; j++) {
                                                if (currentQuarters[j] != 0.0 && previousQuarters[j] != 0.0 && previousQuarters[i] != 0.0) {
                                                    double ratio = previousQuarters[i] / previousQuarters[j];
                                                    double estimated = currentQuarters[j] * ratio;
                                                    ttmEps += estimated;
                                                    foundRatio = true;
                                                    System.err.println("使用比例調整 Q" + (4-i) + "：" + ratio + " × " + currentQuarters[j] + " = " + estimated);
                                                    break;
                                                }
                                            }
                                            if (!foundRatio && previousQuarters[i] != 0.0) {
                                                // fallback：直接補上一年
                                                ttmEps += previousQuarters[i];
                                                System.err.println("直接補上一年 Q" + (4-i) + "：" + previousQuarters[i]);
                                            }
                                        }
                                    }
                                }

                                // 目前股價（使用現價）
                                double currentPrice = quote.closePrice();

                                // 本益比 TTM (Trailing Twelve Months) 是一種滾動計算的本益比，它使用公司「最近 12 個月（過去四個季度）的實際獲利」來評估當前股價，相比傳統的年度本益比，能更即時反映公司最新的獲利能力，剔除季節性影響，數據時效性強，是港股美股常用的估值指標。
                                double currentPer = ttmEps > 0 ? currentPrice / ttmEps : 0; // 目前價格 除以 最近四季 EPS

                                // 歷史本益比分位估值參考
                                double cheapPrice;
                                double fairPrice;
                                double expensivePrice ;
                                HistoricalPE pe = hiStockService.fetchHistoricalPE(symbol);
                                if (pe != null && epsData != null) {
                                    cheapPrice = ttmEps * pe.cheapPE;
                                    fairPrice = ttmEps * pe.fairPE;
                                    expensivePrice = ttmEps * pe.expensivePE;

                                    sb.append("【動態（基於歷史本益比分位，").append(pe.dataCount).append("筆數據）】\n\n");
                                    sb.append(String.format("便宜價（" + pe.cheapPE_percent + "%%分位 %.1f倍）：%.0f 元\n", pe.cheapPE, cheapPrice));
                                    sb.append(String.format("合理價（" + pe.fairPE_percent + "%%分位 %.1f倍）：%.0f 元\n", pe.fairPE, fairPrice));
                                    sb.append(String.format("昂貴價（" + pe.expensivePE_percent + "%%分位 %.1f倍）：%.0f 元\n\n", pe.expensivePE, expensivePrice));

                                    // sb.append("※ 目前本益比位於歷史 ※\n");
                                    // if (currentPer <= pe.cheapPE) {
                                    //     sb.append("極便宜區（低於20%分位）\n");
                                    // } else if (currentPer <= pe.fairPE) {
                                    //     sb.append("相對便宜至合理區\n");
                                    // } else if (currentPer <= pe.expensivePE) {
                                    //     sb.append("相對貴區\n");
                                    // } else {
                                    //     sb.append("極貴區（高於80%分位）\n");
                                    // }
                                } else {
                                    cheapPrice = ttmEps * 25;
                                    fairPrice = ttmEps * 35;
                                    expensivePrice = ttmEps * 50;

                                    sb.append("【靜態】\n\n");
                                    sb.append(String.format("便宜價（25倍）：%.0f 元\n", cheapPrice));
                                    sb.append(String.format("合理價（35倍）：%.0f 元\n", fairPrice));
                                    sb.append(String.format("昂貴價（50倍）：%.0f 元\n", expensivePrice));
                                }

                                sb.append(String.format("最近四季 EPS (TTM)：%.2f 元\n", ttmEps));
                                sb.append(String.format("目前本益比：%.2f 倍\n\n", currentPer));

                                // 樂觀預估全年 EPS（僅當 Q4 未出時顯示）
                                // if (!useUserInput && epsData.q4Current() == 0.0) {
                                //     double currentCumulative = epsData.q1Current() + epsData.q2Current() + epsData.q3Current();
                                //     double previousCumulative = epsData.q1Previous() + epsData.q2Previous() + epsData.q3Previous();
                                //     double yoyGrowth = previousCumulative > 0 ? (currentCumulative - previousCumulative) / previousCumulative : 0;

                                //     double q4Base = epsData.q4Previous(); // 去年 Q4
                                //     double adjustedQ4 = q4Base * (1 + yoyGrowth * 0.6); // 成長率打 6 折
                                //     double estimatedAnnual = currentCumulative + adjustedQ4;

                                //     sb.append(String.format("程式樂觀預估 %d 年全年 EPS：%.2f 元\n", epsData.currentYear(), estimatedAnnual));
                                //     sb.append(String.format("→ 若實現，潛在昂貴價可達 %.0f 元\n", estimatedAnnual * 50));
                                // }
                            } else {
                                sb.append("EPS 資料暫無法取得（可能網路問題或網站改版）\n");
                            }

                            resultArea.setText(sb.toString());
                        }));

                    resultArea.setText(sb.toString()); // 設定完整文字

                    // 柱狀圖
                    chartPane.setContent(createQuoteBarChart(quote));
                    resizeChartProportionally(); // 改用統一的等比例縮放方法

                    // loading.close();
                } else {
                    resultArea.setText("查詢失敗，請稍後再試\n若 API 不可用，請稍後再使用。");
                    // loading.close();
                }
            }))
            .exceptionally(ex -> {
                // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                Platform.runLater(() -> {
                    showAlert("系統異常，請稍後再試：" + ex.getMessage());
                    // loading.close(); // 例外時關閉
                });
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
            // int open = (int) Math.round(quote.openPrice());
            // int high = (int) Math.round(quote.highPrice());
            // int low = (int) Math.round(quote.lowPrice());
            // int close = (int) Math.round(quote.closePrice());
            // int avg = (int) Math.round(quote.avgPrice());

            double open = quote.openPrice();
            double high = quote.highPrice();
            double low = quote.lowPrice();
            double close = quote.closePrice();
            double avg = quote.avgPrice();

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
                sb.append(String.format("股票代碼：%s\n股票名稱：%s\n\n開盤價：%.2f\n最高價：%.2f\n最低價：%.2f\n現價：%.2f\n\n",
                    quote.symbol(), quote.name(), quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.closePrice()));

                // 依序顯示資料
                for (VolumeByPrice v : dataList) {
                    sb.append(String.format("成交價：%.2f\n累計成交量：%d\n內盤累計成交量：%d\n外盤累計成交量：%d\n\n",
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
                    
                    sb.append(String.format("POC（最大成交量價位）：%.2f 元（成交 %d 張，外盤比例 %.2f%%）\n",
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
                
                resultArea.setText(sb.toString()); // 設定完整文字

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
            DecimalFormat df = new DecimalFormat("#0.00");  // 強制顯示兩位小數，如 915.00

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

    // 查詢歷史K線邏輯
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

                    StringBuilder sb = new StringBuilder(String.format("歷史K線圖已載入（近 %d 日走勢）。\n\n", candles.size()));
                    for (Candle c : candles) {
                        String tag = c.date().equals(today) && !hasToday ? "（即時演算）" : "";
                        sb.append(String.format("日期：%s%s\n開盤價：%.2f\n最高價：%.2f\n最低價：%.2f\n收盤價：%.2f\n成交量：%d\n漲跌：%.2f\n\n",
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

                    sb.append(String.format("區間觸及最高價：%.2f（%s）\n", maxHigh, maxHighDateStr)); // 格式化添加（%.2f 保留1位小數）
                    sb.append(String.format("區間觸及最低價：%.2f（%s）\n\n", minLow, minLowDateStr)); // 格式化添加（%.2f 保留1位小數）

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
                    String maxGainDisplay = maxDailyGain > 0 ? String.format("%.2f", maxDailyGain) : "0";
                    String minGainDisplay = minDailyGain < 0 ? String.format("%.2f", minDailyGain) : "0";

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
                            sb.append(String.format("頂背離警訊：%s 觸及區間最高價 %.2f，但成交量 %,d 僅為平均 %,d 的 %.0f%%（量縮明顯，追價動能不足）\n",
                                    maxHighDate, maxHigh, maxHighVolume, avgVolume,
                                    (maxHighVolume * 100.0 / avgVolume)));
                            hasDivergence = true;
                        }

                        // 底背離
                        if (minLowIndex != -1 && avgVolume > 0 && minLowVolume < avgVolume * 0.7) {
                            LocalDate minLowDate = candles.get(minLowIndex).date();
                            sb.append(String.format("底背離警訊：%s 觸及區間最低價 %.2f，但成交量 %,d 僅為平均 %,d 的 %.0f%%（量縮明顯，賣壓衰竭）\n",
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
                Platform.runLater(() -> {
                    showAlert("系統異常，請稍後再試：" + ex.getMessage());
                });
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
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.runAsync(() -> {
            try {
                List<StockWearnService.InstitutionalTrade> rawData = 
                    StockWearnService.fetchInstitutionalTrading(symbol);

                if (rawData.isEmpty()) throw new Exception("查無資料");

                // 決定要幾筆（最近 N 天，或全部）
                int count = (days == 0) ? rawData.size() : Math.min(rawData.size(), days);

                // 取最新的 count 筆（最近 N 天）
                List<StockWearnService.InstitutionalTrade> recentData = rawData.subList(0, count);

                // 顯示與圖表共用這份資料：最舊在前
                List<StockWearnService.InstitutionalTrade> displayAndChartData = new ArrayList<>(recentData);
                Collections.reverse(displayAndChartData); // 讓最舊在前面

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

                for (StockWearnService.InstitutionalTrade row : displayAndChartData) {
                    int trust = row.trust();
                    int dealer = row.dealer();
                    int foreign = row.foreign();

                    dates.add(row.date());
                    trustList.add(trust);
                    dealerList.add(dealer);
                    foreignList.add(foreign);

                    sb.append(String.format("日期：%s\n", row.date()));
                    sb.append(String.format("外資：%,d\n", foreign));
                    sb.append(String.format("投信：%,d\n", trust));
                    sb.append(String.format("自營商：%,d\n", dealer));
                    sb.append(String.format("合計：%,d\n\n", foreign + dealer + trust));

                    // 極值更新
                    if (trust > maxTrust) { maxTrust = trust; maxTrustDate = row.date(); }
                    if (dealer > maxDealer) { maxDealer = dealer; maxDealerDate = row.date(); }
                    if (foreign > maxForeign) { maxForeign = foreign; maxForeignDate = row.date(); }
                    if (trust < minTrust) { minTrust = trust; minTrustDate = row.date(); }
                    if (dealer < minDealer) { minDealer = dealer; minDealerDate = row.date(); }
                    if (foreign < minForeign) { minForeign = foreign; minForeignDate = row.date(); }
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

                    resultArea.setText(sb.toString()); // 設定完整文字
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
                Platform.runLater(() -> {
                    showAlert("系統異常，請稍後再試：" + ex.getMessage());
                });
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

                    resultArea.setText(sb.toString()); // 設定完整文字
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
                Platform.runLater(() -> {
                    showAlert("系統異常，請稍後再試：" + ex.getMessage());
                });
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

                resultArea.setText(sb.toString()); // 設定完整文字
                resultArea.appendText(""); // 自動滾動到最底部

                Node chartNode = createBollingerWithCandlesChart(candles, bbList);
                chartPane.setContent(chartNode);
                resizeChartProportionally(); // 改用統一的等比例縮放方法
            }))
            .exceptionally(ex -> {
                // exceptionally 像是 "非同步catch"，上游supplyAsync拋錯（如Fugle Key無效）時，自動恢復null並秀Alert—避免整個CompletableFuture崩潰，若直接showAlert，會造成整個應用程式crash
                Platform.runLater(() -> {
                    showAlert("系統異常，請稍後再試：" + ex.getMessage());
                });
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
                List<StockWearnService.ForeignNetPosition> positions = 
                    StockWearnService.fetchForeignNetPositions();

                if (positions.isEmpty()) {
                    Platform.runLater(() -> {
                        showAlert("沒有抓到任何外資空單資料，或網站改版導致無法解析！");
                    });
                    return;
                }

                // positions 已是最新的在前
                List<String> originalDates = positions.stream()
                        .map(StockWearnService.ForeignNetPosition::date)
                        .collect(Collectors.toList());
                List<Integer> originalNet = positions.stream()
                        .map(StockWearnService.ForeignNetPosition::netPosition)
                        .collect(Collectors.toList());

                List<String> chartDates = new ArrayList<>(originalDates);

                // 計算增減（最新在前）
                List<Integer> changes = new ArrayList<>();
                for (int i = 0; i < originalNet.size(); i++) {
                    changes.add(i < originalNet.size() - 1 ? originalNet.get(i) - originalNet.get(i + 1) : 0);
                }

                // 反轉：最舊在前
                List<String> ascendingDates = new ArrayList<>(originalDates);
                List<Integer> ascendingNet = new ArrayList<>(originalNet);
                Collections.reverse(ascendingDates);
                Collections.reverse(ascendingNet);
                Collections.reverse(changes);

                // 刪除第一筆不需顯示的資料（最早一天）
                if (!ascendingDates.isEmpty()) {
                    ascendingDates.remove(0);
                    ascendingNet.remove(0);
                    chartDates.remove(0);  // chartDates 仍保持最新在前，移除最早一筆
                    changes.remove(0);
                }

                // 若天數限制，則截取最後 N 筆
                if (days > 0 && ascendingDates.size() > days) {
                    int start = ascendingDates.size() - days;
                    ascendingDates = ascendingDates.subList(start, ascendingDates.size());
                    ascendingNet = ascendingNet.subList(start, ascendingNet.size());
                    chartDates = chartDates.subList(chartDates.size() - days, chartDates.size());
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
                            ascendingDates.get(i),
                            ascendingNet.get(i),
                            changes.get(i)));
                }
                sb.append(String.format("區間最高空單數：%,d（%s）\n",
                        highestNet, String.join("、", highestDates)));
                sb.append(String.format("區間最低空單數：%,d（%s）\n",
                        lowestNet, String.join("、", lowestDates)));

                String finalText = sb.toString();

                // 圖表資料：必須從舊到新（時間軸由左到右遞增）
                // ascendingDates / ascendingNet 已是最舊在前，直接使用
                List<String> chartDisplayDates = new ArrayList<>(ascendingDates);
                List<Integer> chartDisplayNet = new ArrayList<>(ascendingNet);

                Platform.runLater(() -> {
                    resultArea.setText(finalText);
                    resultArea.appendText(""); // 自動滾動到最底部

                    // 使用計數器確保日期正確對應
                    java.util.concurrent.atomic.AtomicInteger indexCounter = new java.util.concurrent.atomic.AtomicInteger(0);

                    chartPane.setContent(createCommonLineChart(
                        chartDisplayNet, "外資大盤淨空單", "口數",
                        new Color(255, 140, 0),
                        obj -> ((Integer) obj).doubleValue(),
                        obj -> LocalDate.parse(chartDisplayDates.get(indexCounter.getAndIncrement()))
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

    // 查大盤三大法人買賣超
    private void queryInstitutionalMarketTrading() {
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
                List<StockWearnService.InstitutionalMarketTrade> rawData = 
                    StockWearnService.fetchInstitutionalMarketTrading();

                if (rawData.isEmpty()) throw new Exception("查無資料");

                // 決定要幾筆（最近 N 天，或全部）
                int count = (days == 0) ? rawData.size() : Math.min(rawData.size(), days);

                // 取最新的 count 筆
                List<StockWearnService.InstitutionalMarketTrade> recentData = rawData.subList(0, count);

                // 顯示與圖表：最舊在前（僅此一次 reverse）
                List<StockWearnService.InstitutionalMarketTrade> displayAndChartData = new ArrayList<>(recentData);
                Collections.reverse(displayAndChartData);

                // 容器
                List<String> dates = new ArrayList<>();
                List<Double> trustList = new ArrayList<>();
                List<Double> dealerList = new ArrayList<>();
                List<Double> foreignList = new ArrayList<>();

                // 極值追蹤（買超最大 = 正數最大，賣超最大 = 負數最小）
                double maxTrust = Double.MIN_VALUE, maxDealer = Double.MIN_VALUE, maxForeign = Double.MIN_VALUE;
                double minTrust = Double.MAX_VALUE, minDealer = Double.MAX_VALUE, minForeign = Double.MAX_VALUE;
                String maxTrustDate = "", maxDealerDate = "", maxForeignDate = "";
                String minTrustDate = "", minDealerDate = "", minForeignDate = "";

                StringBuilder sb = new StringBuilder();
                sb.append("大盤三大法人買賣超資訊如下：\n\n");

                for (StockWearnService.InstitutionalMarketTrade row : displayAndChartData) {
                    double trust = row.trust();
                    double dealer = row.dealer();
                    double foreign = row.foreign();
                    double total = trust + dealer + foreign;

                    dates.add(row.date());
                    trustList.add(trust);
                    dealerList.add(dealer);
                    foreignList.add(foreign);

                    sb.append(String.format("日期：%s\n", row.date()));
                    sb.append(String.format("外資：%s%.2f\n", foreign >= 0 ? "+ " : "", foreign));
                    sb.append(String.format("投信：%s%.2f\n", trust >= 0 ? "+ " : "", trust));
                    sb.append(String.format("自營商：%s%.2f\n", dealer >= 0 ? "+ " : "", dealer));
                    sb.append(String.format("合計：%s%.2f\n\n", total >= 0 ? "+ " : "", total));

                    // 極值更新
                    if (trust > maxTrust) { maxTrust = trust; maxTrustDate = row.date(); }
                    if (dealer > maxDealer) { maxDealer = dealer; maxDealerDate = row.date(); }
                    if (foreign > maxForeign) { maxForeign = foreign; maxForeignDate = row.date(); }
                    if (trust < minTrust) { minTrust = trust; minTrustDate = row.date(); }
                    if (dealer < minDealer) { minDealer = dealer; minDealerDate = row.date(); }
                    if (foreign < minForeign) { minForeign = foreign; minForeignDate = row.date(); }
                }

                sb.append("[買超]\n\n");
                sb.append(String.format("區間最大（投信）：%.2f（%s）\n", maxTrust, maxTrustDate));
                sb.append(String.format("區間最大（自營商）：%.2f（%s）\n", maxDealer, maxDealerDate));
                sb.append(String.format("區間最大（外資）：%.2f（%s）\n", maxForeign, maxForeignDate));

                sb.append("\n[賣超]\n\n");
                sb.append(String.format("區間最大（投信）：%.2f（%s）\n", minTrust, minTrustDate));
                sb.append(String.format("區間最大（自營商）：%.2f（%s）\n", minDealer, minDealerDate));
                sb.append(String.format("區間最大（外資）：%.2f（%s）\n", minForeign, minForeignDate));

                String finalText = sb.toString();

                // 圖表資料準備：轉換為 List<Integer> 以符合原有 createInstitutionalChart 需求
                // 大盤單位為「億」，乘以 100 轉為「張」單位（1億 ≈ 100張），四捨五入取整
                List<Integer> chartTrustInt = trustList.stream()
                        .map(v -> (int) Math.round(v * 100))
                        .collect(Collectors.toList());
                List<Integer> chartDealerInt = dealerList.stream()
                        .map(v -> (int) Math.round(v * 100))
                        .collect(Collectors.toList());
                List<Integer> chartForeignInt = foreignList.stream()
                        .map(v -> (int) Math.round(v * 100))
                        .collect(Collectors.toList());

                // dates 已是最舊在前，直接使用
                List<String> chartDates = new ArrayList<>(dates);

                Platform.runLater(() -> {
                    resultArea.clear();
                    resultArea.appendText(finalText);

                    if (!chartDates.isEmpty()) {
                        // 直接呼叫原有方法，參數完全相容
                        Node chart = createInstitutionalChart(
                            chartDates,
                            chartTrustInt,    // List<Integer>
                            chartDealerInt,   // List<Integer>
                            chartForeignInt   // List<Integer>
                        );
                        chartPane.setContent(chart);
                        resizeChartProportionally(); // 改用統一的等比例縮放方法
                    } else {
                        chartPane.setContent(createEmptyChartPanel());
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.clear();
                    resultArea.appendText("【大盤三大法人買賣超】抓取失敗\n" + e.getMessage() + "\n");
                    chartPane.setContent(createEmptyChartPanel());
                });
            }
        });
    }

    private final TaiFexService taiFexService = new TaiFexService(); // 加入成員變數

    // 查詢即時行情
    private void queryRealtimeQuotes() {
        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> {
            FUTURESRealtime fitx = hiStockService.fetchFUTURESChange("stocktop2017", "FITX", "指數", "成交量(口)"); // 台指近
            FUTURESRealtime twn = hiStockService.fetchFUTURESChange("stocktop2017", "TWN", "指數", "成交量(口)"); // 富台期
            // FUTURESRealtime adr = hiStockService.fetchFUTURESChange("stocktop2017_Global", "TSM", "股價", "成交量"); // 台積電ADR
            // TaifexQuote txQuote = taiFexService.fetchTaifexQuote(2, "臺股期貨");
            TaifexQuote tsmcQuote = taiFexService.fetchTaifexQuote(12, "台積電期貨");
            CnbcService cnbc = new CnbcService();
            CnbcService.FairValueFutures fvFutures = cnbc.getFairValueFutures(); // 美股電子盤
            CnbcService.MarketQuote marketQuote = cnbc.getQuote(); // 美股四大指數

            return new Object[]{fitx, twn, tsmcQuote, fvFutures, marketQuote};
        })
        .thenAccept(results -> Platform.runLater(() -> {
            FUTURESRealtime fitx = (FUTURESRealtime) results[0];
            FUTURESRealtime twn = (FUTURESRealtime) results[1];
            // FUTURESRealtime adr = (FUTURESRealtime) results[2];
            // TaifexQuote txQuote = (TaifexQuote) results[2];
            TaifexQuote tsmcQuote = (TaifexQuote) results[2];
            CnbcService.FairValueFutures fvFutures = (CnbcService.FairValueFutures) results[3];
            CnbcService.MarketQuote marketQuote = (CnbcService.MarketQuote) results[4];

            StringBuilder sb = new StringBuilder();

            // 台積電期貨詳細資料
            sb.append("【台積電期貨】\n\n");
            if (tsmcQuote.isValid()) {
                String signTSMC = tsmcQuote.updown() > 0 ? "▲" : (tsmcQuote.updown() < 0 ? "▼" : "");
                sb.append(String.format("漲跌：%s%.0f\n", signTSMC, Math.abs(tsmcQuote.updown())));
                // sb.append(String.format("成交量：%,d 口\n", tsmcQuote.ttlvol()));
                sb.append(String.format("現價：%.1f　\n", tsmcQuote.price()));
            } else {
                sb.append("無法取得\n");
            }

            // FITX 爬蟲詳細資料
            sb.append("\n【台股期貨】\n\n");
            if (fitx.success()) {
                sb.append(String.format("開盤：%.0f\n", fitx.open()));
                sb.append(String.format("最高：%.0f\n", fitx.high()));
                sb.append(String.format("最低：%.0f\n", fitx.low()));
                sb.append(String.format("漲跌：%s\n", fitx.changeText()));
                sb.append(String.format("成交：%.1f\n", fitx.current()));
                // sb.append(String.format("成交量(口)：%,d 口\n", fitx.volume()));
                // sb.append("更新時間：" + fitx.updateTime() + "\n");
            } else {
                sb.append("無法取得\n");
            }

            // TWN 爬蟲詳細資料
            sb.append("\n【富台指】\n\n");
            if (twn.success()) {
                // sb.append(String.format("開盤：%.0f\n", twn.open()));
                // sb.append(String.format("最高：%.0f\n", twn.high()));
                // sb.append(String.format("最低：%.0f\n", twn.low()));
                sb.append(String.format("漲跌：%s\n", twn.changeText()));
                sb.append(String.format("成交：%.1f\n", twn.current()));
                // sb.append(String.format("成交量(口)：%,d 口\n", twn.volume()));
                // sb.append("更新時間：" + twn.updateTime() + "\n");
            } else {
                sb.append("無法取得\n");
            }

            // 美股盤前電子盤
            sb.append("\n【美股盤前電子盤】\n\n");
            sb.append(String.format("道瓊期貨：%.2f\n", fvFutures.dowChange()));
            sb.append(String.format("標普500期貨：%.2f\n", fvFutures.spChange()));
            sb.append(String.format("納斯達克100期貨：%.2f\n", fvFutures.nasdaqChange()));
            sb.append(String.format("羅素2000期貨：%.2f\n", fvFutures.russellChange()));

            // 美股四大指數
            sb.append("\n【美股四大指數】\n\n");
            sb.append(String.format("道瓊工業指數：%s\n", marketQuote.dowChange()));
            sb.append(String.format("標普500指數：%s\n", marketQuote.spChange()));
            sb.append(String.format("納斯達克指數：%s\n", marketQuote.nasdaqChange()));
            sb.append(String.format("費城半導體指數：%s\n", marketQuote.soxChange()));

            sb.append("\n【台積電 ADR】\n\n");
            if (marketQuote.hasData()) {
                String tsmType = marketQuote.tsmType();
                if ("PRE_MKT".equals(tsmType) || "POST_MKT_PREV".equals(tsmType)) {
                    sb.append("盤前變動：").append(marketQuote.tsmMarket()).append("\n");
                }
                sb.append("盤中或收盤變動：").append(marketQuote.tsmRegular()).append("\n");
                if ("POST_MKT" .equals(tsmType)) {
                    sb.append("盤後變動：").append(marketQuote.tsmMarket()).append("\n");
                }
            } else {
                sb.append("無法取得台積電 ADR 資訊\n");
            }

            resultArea.setText(sb.toString());

            chartPane.setContent(createFITXBarChart(fitx));
            resizeChartProportionally(); // 改用統一的等比例縮放方法
        }))
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                resultArea.setText("查詢失敗：" + ex.getMessage());
            });
            return null;
        });
    }

    // 台指近專用柱狀圖
    private Node createFITXBarChart(FUTURESRealtime fitx) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            // X 軸類別標籤名稱
            dataset.addValue(fitx.open(),    "點", "開盤");
            dataset.addValue(fitx.high(),    "點", "最高");
            dataset.addValue(fitx.current(), "點", "成交");

            JFreeChart chart = ChartFactory.createBarChart(
                "台指期價格結構",
                "",
                "點",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false
            );

            // CategoryPlot：JFreeChart 繪圖區域，處理 CategoryDataset 的線圖。
            CategoryPlot plot = chart.getCategoryPlot();

            // 設定 Y 軸範圍，給予適當邊界
            double max = Math.max(fitx.high(), fitx.current());
            double min = Math.min(fitx.low(), fitx.open());
            plot.getRangeAxis().setRange(min, max);

            // 設定刻度單位（台指期最小跳動單位通常為 1 點）
            ((NumberAxis) plot.getRangeAxis()).setTickUnit(new NumberTickUnit(10)); // 可依需求調整 1/5/10

            // 字型設定並且解決亂碼問題
            Font font = new Font("Microsoft JhengHei", Font.BOLD, 16); // 或 Microsoft YaHei
            chart.getTitle().setFont(font);
            plot.getDomainAxis().setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
            plot.getRangeAxis().setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
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
        PauseTransition delay = new PauseTransition(Duration.millis(500));
        delay.setOnFinished(e -> chartPane.setVisible(true));
        delay.play();

        return swingNode;
    }

    // 查 YahooFinance
    private void queryYahooFinance(String symbol, String symbolName) {
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
            List<YahooFinanceService.YahooCandle> candles = yahooService.fetchHistory(symbol, days);

            if (candles.isEmpty()) {
                throw new RuntimeException("無法取得 " + symbolName + " 資料，請檢查網路或稍後再試");
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
            sb.append(symbolName + " 線圖已載入（近 ").append(candles.size()).append(" 日走勢）。\n\n");

            // 計算漲跌（第一筆無漲跌）
            double previousClose = 0.0; // 第一筆前無參考

            // 用來統計最大漲跌與日期
            double maxRise = Double.NEGATIVE_INFINITY;
            double maxFall = Double.POSITIVE_INFINITY;
            List<LocalDate> maxRiseDates = new ArrayList<>();
            List<LocalDate> maxFallDates = new ArrayList<>();

            // 直接使用 YahooFinanceService.YahooCandle 迭代
            for (YahooFinanceService.YahooCandle c : candles) {
                double change = 0.0;
                double changePct = 0.0;
                if (previousClose != 0.0) {
                    change = c.close() - previousClose;
                    changePct = (change / previousClose) * 100;

                    // 更新最大漲幅
                    if (change > maxRise) {
                        maxRise = change;
                        maxRiseDates.clear();
                        maxRiseDates.add(c.date());
                    } else if (Math.abs(change - maxRise) < 0.01) { // 同值
                        maxRiseDates.add(c.date());
                    }

                    // 更新最大跌幅
                    if (change < maxFall) {
                        maxFall = change;
                        maxFallDates.clear();
                        maxFallDates.add(c.date());
                    } else if (Math.abs(change - maxFall) < 0.01) {
                        maxFallDates.add(c.date());
                    }
                }
                previousClose = c.close();

                sb.append(String.format("日期：%s\n", c.date()))
                  .append(String.format("開盤指數：%.2f\n", c.open()))
                  .append(String.format("最高指數：%.2f\n", c.high()))
                  .append(String.format("最低指數：%.2f\n", c.low()))
                  .append(String.format("收盤指數：%.2f\n", c.close()))
                  .append(String.format("漲跌：%.2f\n", change))
                  .append(String.format("漲跌幅：%.2f%%\n\n", changePct));
            }

            sb.append(String.format("區間最高指數：%.2f（%s）\n", maxClose, maxDate))
              .append(String.format("區間最低指數：%.2f（%s）\n\n", minClose, minDate));

            // 區間單日最大漲跌幅
            if (maxRise > 0) { // 有上漲
                sb.append(String.format("區間單日最大漲幅：%.2f（%s）\n",
                        maxRise, String.join("、", maxRiseDates.stream().map(LocalDate::toString).toList())));
            }
            if (maxFall < 0) { // 有下跌
                sb.append(String.format("區間單日最大跌幅：%.2f（%s）\n",
                        maxFall, String.join("、", maxFallDates.stream().map(LocalDate::toString).toList())));
            }

            resultArea.setText(sb.toString()); // 設定完整文字
            resultArea.appendText(""); // 自動滾動到最底部

            // 加權指數圖表
            chartPane.setContent(createCommonLineChart(
                candles, // 資料來源
                symbol,
                symbolName,
                new Color(178, 34, 34),
                candle -> ((YahooFinanceService.YahooCandle) candle).close(),
                candle -> ((YahooFinanceService.YahooCandle) candle).date()
            ));
            resizeChartProportionally(); // 改用統一的等比例縮放方法

        }), Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                showAlert("系統異常，請稍後再試：" + ex.getMessage());
            });
            return null;
        });
    }

    private final HiStockService hiStockService = new HiStockService(); // 加入成員變數

    // 查 融資融券餘額
    private void queryMarginBalance() {
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        try {
            days = Integer.parseInt(daysText);
            if (days < 0) {
                showAlert("天數必須為 0 包含以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（0 包含以上）");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> {
            List<HiStockService.MarginRecord> records = hiStockService.fetchMarginBalance();

            if (records.isEmpty()) {
                return null;
            }

            // 取最新的 days 筆（records 已是最舊在前、最新在後）
            if (days > 0 && days < records.size()) {
                records = records.subList(records.size() - days, records.size());
            }

            return records;
        }).thenAccept(records -> Platform.runLater(() -> {
            if (records == null || records.isEmpty()) {
                resultArea.setText("融資融券餘額資料載入失敗，請稍後再試。");
                chartPane.setContent(createEmptyChartPanel());
                return;
            }

            StringBuilder sb = new StringBuilder(
                String.format("融資融券餘額已載入（顯示最近 %d 天）。\n\n",
                    days == 0 ? records.size() : days)
            );

            double maxMargin = records.stream().mapToDouble(r -> r.marginBalance()).max().orElse(0);
            double minMargin = records.stream().mapToDouble(r -> r.marginBalance()).min().orElse(0);
            double maxMarginChg = records.stream().mapToDouble(r -> r.marginChange()).max().orElse(0);
            double minMarginChg = records.stream().mapToDouble(r -> r.marginChange()).min().orElse(0);

            // 逐筆顯示
            for (HiStockService.MarginRecord r : records) {
                sb.append(String.format("日期：%s\n", r.date()));
                sb.append(String.format("融資餘額（億）：%.2f\n", r.marginBalance()));
                sb.append(String.format("融資增加（億）：%.2f\n", r.marginChange()));
                sb.append(String.format("融券餘額（張）：%,d\n", r.shortBalance()));
                sb.append(String.format("融券增加（張）：%,d\n", r.shortChange()));
                sb.append(String.format("價格：%.2f\n", r.price()));
                sb.append(String.format("比例：%.2f%%\n", r.priceChangePct()));
                sb.append(String.format("成交量（億）：%.2f\n\n", r.volume()));
            }

            // 區間統計
            List<String> maxMarginDates = records.stream()
                    .filter(r -> Math.abs(r.marginBalance() - maxMargin) < 0.01)
                    .map(r -> r.date())
                    .toList();

            List<String> minMarginDates = records.stream()
                    .filter(r -> Math.abs(r.marginBalance() - minMargin) < 0.01)
                    .map(r -> r.date())
                    .toList();

            List<String> maxChgDates = records.stream()
                    .filter(r -> Math.abs(r.marginChange() - maxMarginChg) < 0.01)
                    .map(r -> r.date())
                    .toList();

            List<String> minChgDates = records.stream()
                    .filter(r -> Math.abs(r.marginChange() - minMarginChg) < 0.01)
                    .map(r -> r.date())
                    .toList();

            sb.append(String.format("區間融資餘額最高：%.2f（%s）\n",
                    maxMargin, String.join("、", maxMarginDates)));
            sb.append(String.format("區間融資餘額最低：%.2f（%s）\n\n",
                    minMargin, String.join("、", minMarginDates)));

            sb.append(String.format("區間融資增加最多：%.2f（%s）\n",
                    maxMarginChg, String.join("、", maxChgDates)));
            sb.append(String.format("區間融資減少最多：%.2f（%s）\n\n",
                    minMarginChg, String.join("、", minChgDates)));

            HiStockService.MarginRecord latest = records.get(records.size() - 1); // 最新一筆
            if (latest.marginBalance() >= 3500) {
                sb.append("融資過熱風險高\n\n");
            }
            if (latest.marginChange() >= 30) {
                sb.append("散戶追價積極\n\n");
            }

            resultArea.setText(sb.toString());
            resultArea.appendText(""); // 自動滾動到最底部

            chartPane.setContent(createCommonLineChart(
                    records,
                    "融資融券餘額進出行情",
                    "融資餘額（億元）",
                    new Color(0, 150, 136),
                    obj -> ((HiStockService.MarginRecord) obj).marginBalance(),
                    obj -> LocalDate.parse(((HiStockService.MarginRecord) obj).date())
            ));
            resizeChartProportionally(); // 改用統一的等比例縮放方法
        }));
    }

    // 融資維持率記錄類別（與 MarginRecord 平級）
    private static class MarginRateRecord {
        final String date;
        final double maintenanceRate;
        final double index;

        MarginRateRecord(String date, double maintenanceRate, double index) {
            this.date = date;
            this.maintenanceRate = maintenanceRate;
            this.index = index;
        }
    }

    // 查 融資維持率
    private void queryMarginRate() {
        String daysText = daysField.getText().trim(); // 使用共用天數欄位
        int days;

        try {
            days = Integer.parseInt(daysText);
            if (days < 0) {
                showAlert("天數必須為 0 包含以上");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("天數必須為有效數字（0 包含 以上）");
            return;
        }

        resultArea.clear();
        resultArea.setText("載入中，請稍候...");

        CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = Jsoup.connect("https://www.istock.tw/post/twmarginrequirement_more")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                        .timeout(15000)
                        .get();

                Elements rows = doc.select("table.ecostyle1 tbody tr");
                if (rows.size() <= 1) { // 只有標題行或無資料
                    return null;
                }

                // 移除標題行
                rows.remove(0);

                List<MarginRateRecord> records = new ArrayList<>();

                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() < 3) continue;

                    String date = cells.get(0).text().trim(); // 已為 yyyy-MM-dd 格式，直接使用
                    String rateStr = cells.get(1).text().trim().replace("%", "");
                    double maintenanceRate = Double.parseDouble(rateStr);
                    double index = Double.parseDouble(cells.get(2).text().trim().replace(",", ""));

                    records.add(new MarginRateRecord(date, maintenanceRate, index));
                }

                // 根據 days 過濾：取最近 N 天（從頭取前 N 筆）
                if (days > 0 && days < records.size()) {
                    records = records.subList(0, days); // 取最新的 days 筆
                }

                // 再反轉成舊的在前（顯示習慣）
                Collections.reverse(records); // 舊的在前

                return records;

            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }).thenAccept(records -> Platform.runLater(() -> {
            if (records == null || records.isEmpty()) {
                resultArea.setText("融資維持率資料載入失敗，請稍後再試。");
                chartPane.setContent(createEmptyChartPanel());
                return;
            }

            StringBuilder sb = new StringBuilder(
                String.format("融資維持率已載入（顯示最近 %d 天）。\n\n",
                    days == 0 ? records.size() : days)
            );

            // 逐筆顯示
            for (MarginRateRecord r : records) {
                sb.append(String.format("日期：%s\n", r.date));
                sb.append(String.format("維持率：%.2f%%\n", r.maintenanceRate));
                sb.append(String.format("加權指數：%.2f\n\n", r.index));
            }

            // 區間統計
            double maxRate = records.stream().mapToDouble(r -> r.maintenanceRate).max().orElse(0);
            double minRate = records.stream().mapToDouble(r -> r.maintenanceRate).min().orElse(0);
            double maxIndex = records.stream().mapToDouble(r -> r.index).max().orElse(0);
            double minIndex = records.stream().mapToDouble(r -> r.index).min().orElse(0);

            sb.append(String.format("區間融資維持率最高：%.2f%%\n", maxRate));
            sb.append(String.format("區間融資維持率最低：%.2f%%\n\n", minRate));
            sb.append(String.format("區間加權指數最高：%.2f\n", maxIndex));
            sb.append(String.format("區間加權指數最低：%.2f\n\n", minIndex));

            // 警訊判斷（最新一筆）
            MarginRateRecord latest = records.get(records.size() - 1);

            if (latest.maintenanceRate >= 146) {
                sb.append("目前融資維持率正常\n\n");
            } else if (latest.maintenanceRate < 146 && latest.maintenanceRate >= 135) {
                sb.append("目前融資維持率相對低迷\n\n");
            } else if (latest.maintenanceRate < 135) {
                sb.append("目前融資維持率低下，容易觸發連環斷頭、恐慌殺盤\n\n");
            }

            resultArea.setText(sb.toString()); // 設定完整文字
            resultArea.appendText(""); // 自動滾動到最底部

            chartPane.setContent(createCommonLineChart(
                    records,
                    "大盤融資維持率",
                    "維持率 (%)",
                    new Color(0, 150, 136), // 翠綠色
                    obj -> ((MarginRateRecord) obj).maintenanceRate,
                    obj -> LocalDate.parse(((MarginRateRecord) obj).date)
            ));
            resizeChartProportionally(); // 改用統一的等比例縮放方法
        }));
    }

    // 實驗用：模擬最新融資餘額資料（使用你提供的真實數據）
    private List<MarginRecord> fetchLatestMarginData() {
        List<MarginRecord> records = new ArrayList<>();

        // 你提供的真實融資餘額資料（12/01 ~ 12/18）
        records.add(new MarginRecord("2025-12-01", 3193.6, 21.8, 301654, -1540, 27342.53, -1.03, 4773.1));
        records.add(new MarginRecord("2025-12-02", 3197.4, 3.8, 305582, 3928, 27564.27, 0.81, 4704.4));
        records.add(new MarginRecord("2025-12-03", 3214.3, 16.9, 302856, -2726, 27793.04, 0.83, 4406.2));
        records.add(new MarginRecord("2025-12-04", 3228.3, 14.0, 299991, -2865, 27795.71, 0.01, 3890.8));
        records.add(new MarginRecord("2025-12-05", 3229.2, 0.9, 303648, 3657, 27980.89, 0.67, 4459.2));
        records.add(new MarginRecord("2025-12-08", 3247.4, 18.2, 310486, 6838, 28303.78, 1.15, 4247.4));
        records.add(new MarginRecord("2025-12-09", 3268.8, 21.5, 313708, 3222, 28182.60, -0.43, 5101.1));
        records.add(new MarginRecord("2025-12-10", 3276.9, 8.1, 303179, -10529, 28400.73, 0.77, 4930.8));
        records.add(new MarginRecord("2025-12-11", 3266.7, -10.2, 307829, 4650, 28024.75, -1.32, 5188.9));
        records.add(new MarginRecord("2025-12-12", 3293.5, 26.8, 307405, -424, 28198.02, 0.62, 4757.2));
        records.add(new MarginRecord("2025-12-15", 3318.6, 25.1, 304195, -3210, 27866.94, -1.17, 4375.9));
        records.add(new MarginRecord("2025-12-16", 3287.9, -30.7, 296807, -7387, 27536.66, -1.19, 5258.5));
        records.add(new MarginRecord("2025-12-17", 3321.7, 33.9, 302030, 5223, 27525.17, -0.04, 5078.6));
        records.add(new MarginRecord("2025-12-18", 3323.8, 2.1, 300546, -1484, 27468.53, -0.21, 4471.4));

        // 最新的在最後
        return records;
    }

    // 實驗用：模擬最新維持率資料（使用你提供的真實數據）
    private List<MarginRateRecord> fetchLatestMarginRateData() {
        List<MarginRateRecord> records = new ArrayList<>();

        // 你提供的真實維持率資料（12/01 ~ 12/18）
        records.add(new MarginRateRecord("2025-12-01", 171.63, 27342.50));
        records.add(new MarginRateRecord("2025-12-02", 171.27, 27564.30));
        records.add(new MarginRateRecord("2025-12-03", 171.52, 27793.00));
        records.add(new MarginRateRecord("2025-12-04", 171.33, 27795.70));
        records.add(new MarginRateRecord("2025-12-05", 172.44, 27980.90));
        records.add(new MarginRateRecord("2025-12-08", 173.78, 28303.80));
        records.add(new MarginRateRecord("2025-12-09", 173.79, 28182.60));
        records.add(new MarginRateRecord("2025-12-10", 174.16, 28400.70));
        records.add(new MarginRateRecord("2025-12-11", 173.21, 28024.80));
        records.add(new MarginRateRecord("2025-12-12", 174.32, 28198.00));
        records.add(new MarginRateRecord("2025-12-15", 172.69, 27866.90));
        records.add(new MarginRateRecord("2025-12-16", 169.16, 27536.70));
        records.add(new MarginRateRecord("2025-12-17", 169.39, 27525.20));
        records.add(new MarginRateRecord("2025-12-18", 168.29, 27468.50));

        return records;
    }

    private void queryComprehensiveAlert() {
        // 顯示「分析中」遮罩 3 秒
        Alert loadingAlert = new Alert(AlertType.INFORMATION);
        loadingAlert.setTitle("市場綜合警訊");
        loadingAlert.setHeaderText(null);
        loadingAlert.setContentText("正在綜合分析市場警訊，請稍候...");
        loadingAlert.setGraphic(null);

        Stage loadingStage = (Stage) loadingAlert.getDialogPane().getScene().getWindow();
        loadingStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png"))));

        // 禁用右上角 X 按鈕（使用者無法手動關閉）
        // loadingStage.setOnCloseRequest(e -> e.consume());

        // 移除所有按鈕（包括「確定」）
        // loadingAlert.getButtonTypes().clear();

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> {
            loadingAlert.close();
            performComprehensiveAnalysis();
        });

        loadingAlert.show();
        delay.play();
    }

    private void queryComprehensiveAlert_new() {
        // 自訂不可關閉的「分析中」視窗（Stage 方式，避開 Alert/Dialog bug）
        Stage loadingStage = new Stage();
        loadingStage.initStyle(StageStyle.UNDECORATED); // 無標題列、無邊框、無 X
        loadingStage.setAlwaysOnTop(true); // 置頂，避免被其他視窗蓋住

        VBox loadingBox = new VBox(15);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(30));
        // loadingBox.setMaxWidth(400);
        // loadingBox.setMaxHeight(200);

        // 自訂樣式顯示
        loadingBox.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.9); " +
            "-fx-border-color: #cccccc; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 10; " +
            "-fx-background-radius: 10;"
        );

        Label loadingLabel = new Label("正在綜合分析市場警訊，請稍候...");
        loadingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(50, 50);

        loadingBox.getChildren().addAll(loadingLabel, progress);

        Scene scene = new Scene(loadingBox);
        loadingStage.setScene(scene);
        // loadingStage.setWidth(350);
        // loadingStage.setHeight(150);

        // 置中於主視窗
        Stage mainStage = (Stage) resultArea.getScene().getWindow();
        loadingStage.initOwner(mainStage);
        loadingStage.setX(mainStage.getX() + mainStage.getWidth() / 2 - 175);
        loadingStage.setY(mainStage.getY() + mainStage.getHeight() / 2 - 75);

        // 完全禁用任何關閉方式
        loadingStage.setOnCloseRequest(e -> e.consume());

        // 故意延遲 3 秒後自動關閉並執行分析
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> {
            loadingStage.close(); // 穩定關閉
            performComprehensiveAnalysis();
        });

        loadingStage.show();
        delay.play();
    }

    private void performComprehensiveAnalysis() {
        CompletableFuture.supplyAsync(() -> {
            List<MarginRecord> marginRecords = fetchLatestMarginData();
            List<MarginRateRecord> rateRecords = fetchLatestMarginRateData();

            if (marginRecords == null || marginRecords.isEmpty() ||
                rateRecords == null || rateRecords.isEmpty()) {
                return "資料載入失敗，無法進行綜合分析。";
            }

            // 取最新一筆
            MarginRecord latestMargin = marginRecords.get(marginRecords.size() - 1);
            MarginRateRecord latestRate = rateRecords.get(rateRecords.size() - 1);

            StringBuilder analysis = new StringBuilder();
            analysis.append("【市場綜合警訊分析】\n\n");
            analysis.append(String.format("分析日期：%s\n", latestRate.date));
            analysis.append(String.format("加權指數：%.2f\n", latestRate.index));
            analysis.append(String.format("融資餘額：%.2f 億元\n", latestMargin.marginBalance()));
            analysis.append(String.format("融資單日增減：%.2f 億元\n", latestMargin.marginChange()));
            analysis.append(String.format("融資維持率：%.2f%%\n\n", latestRate.maintenanceRate));

            // 警訊判斷
            boolean indexDown = latestMargin.priceChangePct() < 0;
            boolean marginUp = latestMargin.marginChange() > 0;

            if (latestRate.maintenanceRate >= 160) {
                analysis.append("🟢 維持率健康（≥160%），斷頭風險低\n");
            } else if (latestRate.maintenanceRate < 160 && latestRate.maintenanceRate >= 140) {
                analysis.append("🟡 維持率進入注意區（<160%）\n");
            } else {
                analysis.append("🔴 維持率危險（<140%），斷頭潮風險極高！\n");
            }

            if (latestMargin.marginBalance() >= 3500) {
                analysis.append("🔴 融資餘額過熱（≥3500億元）\n");
            }

            if (indexDown && marginUp) {
                analysis.append("🔴 【頂背離警訊】指數下跌但融資增加，散戶接刀，後續賣壓恐放大！\n");
            }

            if (!indexDown && marginUp && latestMargin.marginChange() >= 20) {
                analysis.append("🟡 指數上漲且融資大增，追價熱情高，需防過熱\n");
            }

            analysis.append("\n目前市場處於：高檔震盪回落階段（伴隨頂背離特徵）");

            return analysis.toString();

        }).thenAccept(result -> Platform.runLater(() -> {
            showAlert(result, AlertType.INFORMATION);
        }));
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

            resultArea.setText(sb.toString()); // 設定完整文字
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
            Platform.runLater(() -> {
                showAlert("系統異常，請稍後再試：" + ex.getMessage());
            });
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

        // 直接監聽 scene + window + 延遲 2000ms 強制重繪
        swingNode.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                PauseTransition finalForce = new PauseTransition(Duration.millis(2000));
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

    /**
     * 顯示「載入中」提示視窗（可自訂標題與內容）
     * @param title   視窗標題
     * @param content 提示文字
     * @return Alert 物件，讓呼叫端可在完成後 close()
     */
    private Alert showLoading(String title, String content) {
        Alert loadingAlert = new Alert(AlertType.INFORMATION);
        loadingAlert.setTitle(title);
        loadingAlert.setHeaderText(null);
        loadingAlert.setContentText(content);
        loadingAlert.setGraphic(null);

        // 設定圖示
        Stage stage = (Stage) loadingAlert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png"))));

        loadingAlert.show();
        return loadingAlert; // 返回 Alert，讓呼叫端可 close()
    }

    /**
     * 顯示「載入中」提示視窗（可自訂標題與內容）
     * @param content 提示文字
     * @return loadingStage 物件，讓呼叫端可在完成後 close()
     */
    private Stage showCustomLoading(String content) {
        // 自訂不可關閉的「分析中」視窗（Stage 方式，避開 Alert/Dialog bug）
        Stage loadingStage = new Stage();
        loadingStage.initStyle(StageStyle.UNDECORATED); // 無標題列、無邊框、無 X
        loadingStage.setAlwaysOnTop(true); // 置頂，避免被其他視窗蓋住

        VBox loadingBox = new VBox(15);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(30));

        // 自訂樣式顯示
        loadingBox.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.9); " +
            "-fx-border-color: #cccccc; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 10; " +
            "-fx-background-radius: 10;"
        );

        Label loadingLabel = new Label(content);
        loadingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(50, 50);

        loadingBox.getChildren().addAll(loadingLabel, progress);

        Scene scene = new Scene(loadingBox);
        loadingStage.setScene(scene);

        // 置中於主視窗
        Stage mainStage = (Stage) resultArea.getScene().getWindow();
        loadingStage.initOwner(mainStage);
        loadingStage.setX(mainStage.getX() + mainStage.getWidth() / 2);
        loadingStage.setY(mainStage.getY() + mainStage.getHeight() / 2);

        // 完全禁用任何關閉方式
        loadingStage.setOnCloseRequest(e -> e.consume());

        loadingStage.show();
        return loadingStage; // 返回 loadingStage，讓呼叫端可 close()
    }

    // JVM 的要求：所有 Java 應用程式必須有一個 public static void main(String[] args) 作為啟動入口
    // JavaFX 的特殊性：JavaFX 應用程式繼承 Application 類別，但仍需要 main() 來橋接傳統 Java 啟動方式
    // mvn javafx:run 的關係：Maven 會讀取 pom.xml 中 javafx-maven-plugin 中 <mainClass> 的設定值，找到 MainApp.main() 並執行
    // 與 .exe 安裝檔的關係：跟 run 差不多，啟動時執行 com.example.MainApp.main()
    public static void main(String[] args) {
        launch(args);
    }
}