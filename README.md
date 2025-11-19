# 台股股票健診系統 (Stock Health System)

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/) [![Maven](https://img.shields.io/badge/Maven-3.9.11-orange.svg)](https://maven.apache.org/) [![JavaFX](https://img.shields.io/badge/JavaFX-21-green.svg)](https://openjfx.io/) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

這是一個基於 Java 的桌面應用，專為台股投資者設計的股票健診系統。透過 Fugle API 獲取即時報價、歷史 K 線和技術指標（如 RSI、MACD）。支援輸入股票代號和 API Key，顯示報價、折線圖和分析建議。適合個人投資者快速診斷持股狀況。

## 功能特色
- **即時報價**：開盤價、最高/最低價、成交量（Fugle API 優先）。
- **歷史 K 線**：近 10 日 OHLCV 資料，JFreeChart 折線圖視覺化。
- **技術指標**：RSI (超買/超賣警報)、MACD (趨勢判斷)。
- **隱私輸入**：API Key 用密碼欄位輸入，無硬編碼。
- **桌面 GUI**：JavaFX 介面，簡單易用，一鍵查詢/刷新。

## 系統需求
- Java 25 LTS (Temurin 或 Oracle JDK)。  
  開源下載連結：https://adoptium.net/zh-CN/temurin/releases?version=25  
  官方下載連結：https://www.oracle.com/tw/java/technologies/downloads/#jdk25-windows

```
PS C:\project\JavaProjects\stock-health-system> java -version
openjdk version "25.0.1" 2025-10-21 LTS
OpenJDK Runtime Environment Temurin-25.0.1+8 (build 25.0.1+8-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.1+8 (build 25.0.1+8-LTS, mixed mode, sharing)
```

- Maven 3.9.11+  
  Maven是一個管理和構建項目的工具，是 Java 程序員的得力助手。它使開發人員在工作的每個階段都更加輕鬆：從創建項目結構和連接必要的庫到在服務器上部署產品。
  下載連結：https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip

  [編輯系統變數]  
  手動加 PATH（搜 "編輯系統環境變數" → 環境變數 > 系統變數 > Path > 編輯 > 新增 > C:\project\JavaProjects\apache-maven-3.9.11\bin  

  <img width="1284" height="458" alt="2025-11-08_112015" src="https://github.com/user-attachments/assets/473c9a45-a2d4-44f6-9a43-435149b9e1da" />  

```
PS C:\project\JavaProjects\stock-health-system> mvn -version  
Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
Maven home: C:\project\JavaProjects\apache-maven-3.9.11
Java version: 25.0.1, vendor: Eclipse Adoptium, runtime: C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot
Default locale: zh_TW, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

- WiX Toolset v3.14.1  
  作為Maven的外掛套件，可以將Java應用打包成.exe的安裝檔，並根據該安裝檔設定相關的配置細節  
  下載連結：https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip

  [編輯系統變數]  
  手動加 PATH（搜 "編輯系統環境變數" → 環境變數 > 系統變數 > Path > 編輯 > 新增 > C:\project\JavaProjects\wix314-binaries  

  <img width="1741" height="591" alt="wix314-binaries" src="https://github.com/user-attachments/assets/4453a062-6bf9-4e13-aaf5-ee4bda22b38f" />  

```
C:\project\JavaProjects\stock-health-system> candle -?
Windows Installer XML Toolset Compiler version 3.14.1.8722
Copyright (c) .NET Foundation and contributors. All rights reserved.
PS C:\project\JavaProjects\stock-health-system> light -?
Windows Installer XML Toolset Linker version 3.14.1.8722
Copyright (c) .NET Foundation and contributors. All rights reserved.
```

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

```
PS C:\project\JavaProjects\stock-health-system> mvn clean compile
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner (file:/C:/project/JavaProjects/apache-maven-3.9.11/lib/guice-5.1.0-classes.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.inject.internal.aop.HiddenClassDefiner
WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< com.example:stock-health >----------------------
[INFO] Building stock-health 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- clean:3.2.0:clean (default-clean) @ stock-health ---
[INFO] Deleting C:\project\JavaProjects\stock-health-system\target
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ stock-health ---
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] The encoding used to copy filtered properties files have not been set. This means that the same encoding will be used to copy filtered properties files as when copying other filtered resources. This might not be what you want! Run your build with --debug to see which files might be affected. Read more at https://maven.apache.org/plugins/maven-resources-plugin/examples/filtering-properties-files.html
[INFO]
[INFO] --- compiler:3.13.0:compile (default-compile) @ stock-health ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 6 source files with javac [debug release 25] to target\classes
[WARNING] module name in --add-reads option not found: com.example.stockhealth
[WARNING] module name in --add-reads option not found: com.example.stockhealth
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.409 s
[INFO] Finished at: 2025-11-17T13:50:54+08:00
[INFO] ------------------------------------------------------------------------
```

### 3. 執行應用  
- 生成視窗軟體

```
PS C:\project\JavaProjects\stock-health-system> mvn javafx:run
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner (file:/C:/project/JavaProjects/apache-maven-3.9.11/lib/guice-5.1.0-classes.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.inject.internal.aop.HiddenClassDefiner
WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< com.example:stock-health >----------------------
[INFO] Building stock-health 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] >>> javafx:0.0.8:run (default-cli) > process-classes @ stock-health >>>
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ stock-health ---
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] The encoding used to copy filtered properties files have not been set. This means that the same encoding will be used to copy filtered properties files as when copying other filtered resources. This might not be what you want! Run your build with --debug to see which files might be affected. Read more at https://maven.apache.org/plugins/maven-resources-plugin/examples/filtering-properties-files.html
[INFO]
[INFO] --- compiler:3.13.0:compile (default-compile) @ stock-health ---
[INFO] Nothing to compile - all classes are up to date.
[INFO]
[INFO] <<< javafx:0.0.8:run (default-cli) < process-classes @ stock-health <<<
[INFO]
[INFO] 
[INFO] --- javafx:0.0.8:run (default-cli) @ stock-health ---
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by com.sun.glass.utils.NativeLibLoader in module javafx.graphics (file:/C:/Users/colin.bau/.m2/repository/org/openjfx/javafx-graphics/25/javafx-graphics-25-win.jar)
WARNING: Use --enable-native-access=javafx.graphics to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.585 s
[INFO] Finished at: 2025-11-17T13:52:14+08:00
[INFO] ------------------------------------------------------------------------
```

### 4. 打包應用  
- 將視窗軟體發布成.exe的安裝檔，以利客戶端瀏覽

```
PS C:\project\JavaProjects\stock-health-system> mvn clean install
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner (file:/C:/project/JavaProjects/apache-maven-3.9.11/lib/guice-5.1.0-classes.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.inject.internal.aop.HiddenClassDefiner
WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< com.example:stock-health >----------------------
[INFO] Building stock-health 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- clean:3.2.0:clean (default-clean) @ stock-health ---
[INFO] Deleting C:\project\JavaProjects\stock-health-system\target
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ stock-health ---
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] The encoding used to copy filtered properties files have not been set. This means that the same encoding will be used to copy filtered properties files as when copying other filtered resources. This might not be what you want! Run your build with --debug to see which files might be affected. Read more at https://maven.apache.org/plugins/maven-resources-plugin/examples/filtering-properties-files.html
[INFO]
[INFO] --- compiler:3.13.0:compile (default-compile) @ stock-health ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 6 source files with javac [debug release 25] to target\classes
[WARNING] module name in --add-reads option not found: com.example.stockhealth
[WARNING] module name in --add-reads option not found: com.example.stockhealth
[INFO]
[INFO] --- resources:3.3.1:testResources (default-testResources) @ stock-health ---
[INFO] skip non existing resourceDirectory C:\project\JavaProjects\stock-health-system\src\test\resources
[INFO]
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ stock-health ---
[INFO] No sources to compile
[INFO]
[INFO] --- surefire:3.2.5:test (default-test) @ stock-health ---
[INFO] No tests to run.
[INFO]
[INFO] --- jar:3.4.1:jar (default-jar) @ stock-health ---
[INFO] Building jar: C:\project\JavaProjects\stock-health-system\target\stock-health-1.0.0.jar
[INFO] 
[INFO] --- shade:3.5.1:shade (default) @ stock-health ---
[INFO] Including org.openjfx:javafx-controls:jar:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-controls:jar:win:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-graphics:jar:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-graphics:jar:win:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-base:jar:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-base:jar:win:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-swing:jar:25 in the shaded jar.
[INFO] Including org.openjfx:javafx-swing:jar:win:25 in the shaded jar.
[INFO] Including com.squareup.okhttp3:okhttp:jar:4.12.0 in the shaded jar.
[INFO] Including com.squareup.okio:okio:jar:3.6.0 in the shaded jar.
[INFO] Including com.squareup.okio:okio-jvm:jar:3.6.0 in the shaded jar.
[INFO] Including org.jetbrains.kotlin:kotlin-stdlib-common:jar:1.9.10 in the shaded jar.    
[INFO] Including org.jetbrains.kotlin:kotlin-stdlib-jdk8:jar:1.8.21 in the shaded jar.      
[INFO] Including org.jetbrains.kotlin:kotlin-stdlib:jar:1.8.21 in the shaded jar.
[INFO] Including org.jetbrains:annotations:jar:13.0 in the shaded jar.
[INFO] Including org.jetbrains.kotlin:kotlin-stdlib-jdk7:jar:1.8.21 in the shaded jar.      
[INFO] Including com.fasterxml.jackson.core:jackson-databind:jar:2.15.2 in the shaded jar.  
[INFO] Including com.fasterxml.jackson.core:jackson-annotations:jar:2.15.2 in the shaded jar.
[INFO] Including com.fasterxml.jackson.core:jackson-core:jar:2.15.2 in the shaded jar.      
[INFO] Including org.jsoup:jsoup:jar:1.17.2 in the shaded jar.
[INFO] Including org.jfree:jfreechart:jar:1.5.4 in the shaded jar.
[INFO] Dependency-reduced POM written at: C:\project\JavaProjects\stock-health-system\dependency-reduced-pom.xml
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.        
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
[WARNING] jackson-annotations-2.15.2.jar, jackson-core-2.15.2.jar, jackson-databind-2.15.2.jar define 1 overlapping resource:
[WARNING]   - META-INF/NOTICE
[WARNING] okio-3.6.0.jar, okio-jvm-3.6.0.jar define 1 overlapping resource:
[WARNING]   - META-INF/okio.kotlin_module
[WARNING] jackson-core-2.15.2.jar, jackson-databind-2.15.2.jar, jsoup-1.17.2.jar, kotlin-stdlib-1.8.21.jar, kotlin-stdlib-jdk7-1.8.21.jar, kotlin-stdlib-jdk8-1.8.21.jar define 1 overlapping classes:
[WARNING]   - META-INF.versions.9.module-info
[WARNING] jackson-annotations-2.15.2.jar, jackson-core-2.15.2.jar, jackson-databind-2.15.2.jar, jsoup-1.17.2.jar define 1 overlapping resource:
[WARNING]   - META-INF/LICENSE
[WARNING] maven-shade-plugin has detected that some files are
[WARNING] present in two or more JARs. When this happens, only one
[WARNING] single version of the file is copied to the uber jar.
[WARNING] Usually this is not harmful and you can skip these warnings,
[WARNING] otherwise try to manually exclude artifacts based on
[WARNING] mvn dependency:tree -Ddetail=true and the above output.
[WARNING] See https://maven.apache.org/plugins/maven-shade-plugin/
[INFO] Replacing C:\project\JavaProjects\stock-health-system\target\stock-health-1.0.0-full.jar with C:\project\JavaProjects\stock-health-system\target\stock-health-1.0.0-shaded.jar   
[INFO]
[INFO] --- install:3.1.2:install (default-install) @ stock-health ---
[INFO] Installing C:\project\JavaProjects\stock-health-system\dependency-reduced-pom.xml to C:\Users\colin.bau\.m2\repository\com\example\stock-health\1.0.0\stock-health-1.0.0.pom     
[INFO] Installing C:\project\JavaProjects\stock-health-system\target\stock-health-1.0.0.jar to C:\Users\colin.bau\.m2\repository\com\example\stock-health\1.0.0\stock-health-1.0.0.jar  
[INFO] 
[INFO] --- antrun:3.1.0:run (copy-fat-jar) @ stock-health ---
[INFO] Executing tasks
[INFO]     [mkdir] Created dir: C:\project\JavaProjects\stock-health-system\target\jpackage-input
[INFO]      [copy] Copying 1 file to C:\project\JavaProjects\stock-health-system\target\jpackage-input
[INFO] Executed tasks
[INFO]
[INFO] --- jpackage:1.7.1:jpackage (jpackage-bundle) @ stock-health ---
[INFO] Using: C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot\bin\jpackage.exe
[INFO] jpackage options:
[INFO]   --name StockHealth
[INFO]   --dest C:\project\JavaProjects\stock-health-system\target\jpackage
[INFO]   --type exe
[INFO]   --app-version 1.0.0
[INFO]   --input C:\project\JavaProjects\stock-health-system\target\jpackage-input
[INFO]   --vendor YourName
[INFO]   --main-class com.example.Launcher
[INFO]   --main-jar stock-health-1.0.0.jar
[INFO]   --icon C:\project\JavaProjects\stock-health-system\src\main\resources\icon.ico     
[INFO]   --add-modules java.desktop,java.logging,java.sql,java.xml,jdk.unsupported,jdk.unsupported.desktop
[INFO]   --java-options -Dfile.encoding=UTF-8
[INFO]   --java-options --enable-native-access=ALL-UNNAMED
[INFO]   --win-dir-chooser
[INFO]   --win-menu
[INFO]   --win-menu-group StockHealth
[INFO]   --win-shortcut
[INFO]   --win-shortcut-prompt
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  30.425 s
[INFO] Finished at: 2025-11-17T13:54:36+08:00
[INFO] ------------------------------------------------------------------------
```

<img width="387" height="301" alt="01_install" src="https://github.com/user-attachments/assets/f179288c-2575-4a29-97a5-c9148d90c6f6" />
<img width="387" height="301" alt="02_install" src="https://github.com/user-attachments/assets/ef598c74-949f-4a34-83a7-57b64bf1f121" />
<img width="315" height="108" alt="03_install" src="https://github.com/user-attachments/assets/189e5fde-81af-4f99-824f-f4ee6105bf4c" />
<img width="387" height="301" alt="04_install" src="https://github.com/user-attachments/assets/7571ae48-f864-4829-982a-e3133351349a" />
<img width="387" height="301" alt="05_install" src="https://github.com/user-attachments/assets/58581d7e-aab1-47ed-8166-79105d5f5964" />
<img width="387" height="301" alt="06_install" src="https://github.com/user-attachments/assets/0ffccc25-303c-413d-b5b0-a0e2d6d0c92e" />
<img width="1456" height="767" alt="07_install" src="https://github.com/user-attachments/assets/28458862-619b-4235-891a-80c3b16fa1ee" />

### 5. 使用步驟
1. 輸入股票代號（舉例：2330）。
2. 輸入 Fugle API Key（請依照文件說明自行申請）。
3. 點 "查即時報價" 或是 "查歷史 K 線" → 顯示報價/圖表。
4. 若 API 失效，自動顯示 "系統異常，請稍後再試"。

## 6. 專案結構（Maven 的標準目錄布局） 
https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html  
  stock-health-system/  
  ├── .gitignore              # 版控檔案忽略配置  
  ├── build.bat               # 打包自動執行檔   
  ├── pom.xml                 # Maven 配置 (依賴/插件)  
  ├── src/main/java/com/example/  
  │   ├── Candle.java         # 委買與委賣模型  
  │   ├── Candle.java         # K 線模型 
  │   ├── FugleService.java   # API 服務 (OkHttp + Jackson)  
  │   ├── Launcher.java       # 啟動器 (打包應用時需要使用到的過程)  
  │   ├── MainApp.java        # GUI 主入口 (JavaFX)  
  │   ├── MainApp.txt         # 很多註解說明  
  │   ├── Quote.java          # 報價模型 (record)   
  ├── src/main/resources/  
  │   ├── application.properties          # 軟體名稱與版本配置檔（以利打包運用）  
  │   ├── icon.ico            # 軟體圖示   
  ├── stock_api/              # 富果API
  │   ├── fugle_historical_api.txt        # 歷史股價  
  │   ├── fugle_intraday_api.txt          # 即時股價   
  └── ├── fugle_technical_api.txt         # 技術指標  

## 技術使用
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
