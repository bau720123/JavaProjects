# 台股股票健診系統 (Stock Health System)

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/) [![Maven](https://img.shields.io/badge/Maven-3.9.11-orange.svg)](https://maven.apache.org/) [![JavaFX](https://img.shields.io/badge/JavaFX-21-green.svg)](https://openjfx.io/) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

這是一個基於 Java 的桌面應用，專為台股投資者設計的股票健診系統。透過 Fugle API 獲取即時報價、歷史 K 線和技術指標（如 RSI、MACD）。支援輸入股票代號和 API Key，顯示報價、折線圖和分析建議。適合個人投資者快速診斷持股狀況。

## 功能特色
- **即時報價**：開盤價、最高/最低價、成交量（Fugle API 優先，爬蟲備援）。
- **歷史 K 線**：近 10 日 OHLCV 資料，JFreeChart 折線圖視覺化。
- **技術指標**：RSI (超買/超賣警報)、MACD (趨勢判斷)。
- **隱私輸入**：API Key 用密碼欄位輸入，無硬編碼。
- **桌面 GUI**：JavaFX 介面，簡單易用，一鍵查詢/刷新。

## 系統需求
- Java 21 LTS (Temurin 或 Oracle JDK)。
  https://adoptium.net/zh-CN/temurin/releases?version=21
- Maven 3.9.11+。
  https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip
- Fugle API Key
  https://developer.fugle.tw/docs/key/
  https://developer.fugle.tw/docs/data/intro
  https://github.com/fugle-dev/fugle-marketdata-python
- Windows (支援 .exe 打包)。

## 安裝與執行
### 1. 克隆專案
git clone https://github.com/bau720123/JavaProjects.git
cd JavaProjects

### 2. 編譯依賴
- 自動下載 OkHttp (API 請求)、Jackson (JSON 解析)、JavaFX (GUI)、Jsoup (爬蟲備援)。

### 3. 執行應用
mvn javafx:run
- 或打包 .exe：
- mvn jpackage:jpackage
- 生成 dist/stock-health.exe，雙擊執行。

### 4. 使用步驟
1. 輸入股票代號 (e.g., 2330)。
2. 輸入 Fugle API Key (密碼欄位)。
3. 點 "查詢" → 顯示報價/圖表/指標。
4. 若 API 失效，自動顯示 "系統異常，請稍後再試"（可手動切爬蟲）。

## 專案結構
stock-health-system/
├── pom.xml                 # Maven 配置 (依賴/插件)
├── src/main/java/com/example/
│   ├── MainApp.java        # GUI 主入口 (JavaFX)
│   ├── Quote.java          # 報價模型 (record)
│   ├── Candle.java         # K 線模型
│   └── FugleService.java   # API 服務 (OkHttp + Jackson)
└── README.md               # 此檔

## 技術棧
- **語言**：Java 21 (records, virtual threads)。
- **API 存取**：OkHttp GET 請求 Fugle endpoint (e.g., /intraday/quote/2330)。
- **資料解析**：Jackson 轉 JSON 到 POJO。
- **GUI**：JavaFX (VBox 布局、Alert 錯誤)。
- **圖表**：JFreeChart (折線圖)。
- **備援**：Jsoup 爬 Yahoo 財經 (若 API 404/401)。

## 貢獻指南
1. Fork 專案。
2. 建立分支：`git checkout -b feature/xxx`。
3. 提交變更：`git commit -m "Add RSI 指標"`。
4. Push：`git push origin feature/xxx`。
5. Pull Request 到 main。

## 授權
MIT License – 見 [LICENSE](LICENSE)。

## 聯絡
- 問題？開 Issue。
- 作者：@your-username (GitHub)。
