module com.example.stockhealth {  // 模組名稱：用 groupId + artifactId 衍生，唯一識別
    requires javafx.controls;  // JavaFX UI 控件（你的 Button/TextField）
    requires javafx.swing;     // JavaFX-Swing 橋接（ChartPanel）
    requires javafx.graphics;  // 圖形基礎（若有圖表渲染）

    // 第三方模組（從 pom.xml 依賴推斷，jlink 會自動 transitive 拉入）
    requires okhttp3;                    // HTTP 請求（FugleService fetchQuote，用 OkHttpClient/Request）
    requires com.fasterxml.jackson.databind;  // JSON 解析（JsonNode in FugleService）
    requires org.jsoup;                  // 爬蟲 fallback（若 API 失效，用 Jsoup 抓 Yahoo 股價）

    // 修正：JFreeChart 官方 automatic module name，讓模組讀取其 package（解決 module not found）
    requires org.jfree.jfreechart;       // 線圖繪製（ChartFactory.createLineChart in MainApp）

    // 標準 Java 模組（Swing/圖表用）
    requires java.desktop;     // AWT/Swing 支援（ChartPanel）
    requires java.base;        // 基礎（預設，但顯式加防警告）

    exports com.example;       // 公開你的包，讓模組外部（如 jpackage）存取 MainApp/FugleService
}