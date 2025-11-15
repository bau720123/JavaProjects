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
- Java 25 LTS (Temurin 或 Oracle JDK)。  
  下載連結：https://adoptium.net/zh-CN/temurin/releases?version=25

  [命令提示字元]  
  PS C:\project\JavaProjects\stock-health-system> java -version  
  openjdk version "25.0.1" 2025-10-21 LTS
  OpenJDK Runtime Environment Temurin-25.0.1+8 (build 25.0.1+8-LTS)
  OpenJDK 64-Bit Server VM Temurin-25.0.1+8 (build 25.0.1+8-LTS, mixed mode, sharing)

- Maven 3.9.11+  
  下載連結：https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip

  [編輯系統變數]  
  手動加 PATH（搜 "編輯系統環境變數" → 環境變數 > 系統變數 > Path > 編輯 > 新增 > C:\project\JavaProjects\apache-maven-3.9.11\bin  

  <img width="1284" height="458" alt="2025-11-08_112015" src="https://github.com/user-attachments/assets/473c9a45-a2d4-44f6-9a43-435149b9e1da" />  

  [命令提示字元]  
  PS C:\project\JavaProjects\stock-health-system> mvn -version  
  Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)  
  Maven home: C:\project\JavaProjects\apache-maven-3.9.11  
  Java version: 25.0.1, vendor: Eclipse Adoptium, runtime: C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot  
  Default locale: zh_TW, platform encoding: UTF-8  
  OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"

- WiX Toolset v3.14.1  
  下載連結：https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip

  [編輯系統變數]  
  手動加 PATH（搜 "編輯系統環境變數" → 環境變數 > 系統變數 > Path > 編輯 > 新增 > C:\project\JavaProjects\wix314-binaries  

  <img width="1741" height="591" alt="wix314-binaries" src="https://github.com/user-attachments/assets/4453a062-6bf9-4e13-aaf5-ee4bda22b38f" />  

  [命令提示字元]  
  C:\project\JavaProjects\stock-health-system> candle -?  
  Windows Installer XML Toolset Compiler version 3.14.1.8722  
  Copyright (c) .NET Foundation and contributors. All rights reserved.  
  PS C:\project\JavaProjects\stock-health-system> light -?  
  Windows Installer XML Toolset Linker version 3.14.1.8722  
  Copyright (c) .NET Foundation and contributors. All rights reserved.

- Fugle API Key  
  [教學文件與TOKEN申請]  
  https://developer.fugle.tw/docs/key/  
  https://developer.fugle.tw/docs/data/intro  
  https://github.com/fugle-dev/fugle-marketdata-python  

## 安裝與執行
### 1. 克隆專案  
- git clone https://github.com/bau720123/JavaProjects.git  

### 2. 編譯依賴
- 自動下載 OkHttp (API 請求)、Jackson (JSON 解析)、JavaFX (GUI)。

  [命令提示字元]  
  PS C:\project\JavaProjects\stock-health-system> mvn clean compile  
  [INFO] Scanning for projects...  
  [INFO]  
  [INFO] ----------------------< com.example:stock-health >----------------------  
  [INFO] Building stock-health 1.0-SNAPSHOT  
  [INFO]   from pom.xml  
  [INFO] --------------------------------[ jar ]---------------------------------
  [WARNING] 6 problems were encountered while building the effective model for org.openjfx:javafx-controls:jar:21 during dependency collection step for project (use -X to see details)  
  [INFO]  
  [INFO] --- clean:3.2.0:clean (default-clean) @ stock-health ---  
  [INFO] Deleting C:\project\JavaProjects\stock-health-system\target  
  [INFO]  
  [INFO] --- resources:3.3.1:resources (default-resources) @ stock-health ---  
  [INFO] skip non existing resourceDirectory C:\project\JavaProjects\stock-health-system\src\main\resources  
  [INFO]  
  [INFO] --- compiler:3.13.0:compile (default-compile) @ stock-health ---  
  [INFO] Recompiling the module because of changed source code.  
  [INFO] Compiling 2 source files with javac [debug target 21] to target\classes  
  [INFO] ------------------------------------------------------------------------  
  [INFO] BUILD SUCCESS  
  [INFO] ------------------------------------------------------------------------  
  [INFO] Total time:  1.629 s  
  [INFO] Finished at: 2025-11-08T11:01:48+08:00  
  [INFO] ------------------------------------------------------------------------  

### 3. 執行應用  
- 生成視窗軟體，並且進行後續的打包

  [命令提示字元]  
  PS C:\project\JavaProjects\stock-health-system> mvn javafx:run  
  [INFO] Scanning for projects...  
  [INFO]  
  [INFO] ----------------------< com.example:stock-health >----------------------  
  [INFO] Building stock-health 1.0-SNAPSHOT  
  [INFO]   from pom.xml  
  [INFO] --------------------------------[ jar ]---------------------------------  
  [INFO]  
  [INFO] >>> javafx:0.0.8:run (default-cli) > process-classes @ stock-health >>>  
  [WARNING] 6 problems were encountered while building the effective model for org.openjfx:javafx-controls:jar:21 during dependency collection step for project (use -X to see details)  
  [INFO]  
  [INFO] --- resources:3.3.1:resources (default-resources) @ stock-health ---  
  [INFO] skip non existing resourceDirectory C:\project\JavaProjects\stock-health-system\src\main\resources  
  [INFO]  
  [INFO] --- compiler:3.13.0:compile (default-compile) @ stock-health ---  
  [INFO] Nothing to compile - all classes are up to date.  
  [INFO]  
  [INFO] <<< javafx:0.0.8:run (default-cli) < process-classes @ stock-health <<<  
  [INFO]  
  [INFO]  
  [INFO] --- javafx:0.0.8:run (default-cli) @ stock-health ---  

  <img width="314" height="219" alt="2025-11-08_114217" src="https://github.com/user-attachments/assets/8e8e8924-1fd2-46c4-9663-9ab2a292e9f9" />  

- 或打包 .exe：
- mvn jpackage:jpackage
- 生成 dist/stock-health.exe，雙擊執行。
<img width="387" height="301" alt="01_install" src="https://github.com/user-attachments/assets/f179288c-2575-4a29-97a5-c9148d90c6f6" />
<img width="387" height="301" alt="02_install" src="https://github.com/user-attachments/assets/ef598c74-949f-4a34-83a7-57b64bf1f121" />
<img width="315" height="108" alt="03_install" src="https://github.com/user-attachments/assets/189e5fde-81af-4f99-824f-f4ee6105bf4c" />
<img width="387" height="301" alt="04_install" src="https://github.com/user-attachments/assets/7571ae48-f864-4829-982a-e3133351349a" />
<img width="387" height="301" alt="05_install" src="https://github.com/user-attachments/assets/58581d7e-aab1-47ed-8166-79105d5f5964" />
<img width="387" height="301" alt="06_install" src="https://github.com/user-attachments/assets/0ffccc25-303c-413d-b5b0-a0e2d6d0c92e" />
<img width="1456" height="767" alt="07_install" src="https://github.com/user-attachments/assets/28458862-619b-4235-891a-80c3b16fa1ee" />

### 4. 使用步驟
1. 輸入股票代號 (e.g., 2330)。
2. 輸入 Fugle API Key (密碼欄位)。
3. 點 "查詢" → 顯示報價/圖表/指標。
4. 若 API 失效，自動顯示 "系統異常，請稍後再試"。

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
- **語言**：Java 25 (records, virtual threads)。
- **API 存取**：OkHttp GET 請求 Fugle endpoint (e.g., /intraday/quote/2330)。
- **資料解析**：Jackson 轉 JSON 到 POJO。
- **GUI**：JavaFX (VBox 布局、Alert 錯誤)。
- **圖表**：JFreeChart (折線圖)。

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
- test
