# Project Tool 工具說明

這是一個多功能 Java 工具集，包含四個主要功能：
- **工具 A (White Label Generator)**: 根據 JSON 設定檔（含 `files` 動態配置陣列），自動產出 SQL 檔案與對應的 Java/JS 程式碼，支援多環境展開
- **工具 B (Domain Checker)**: 根據 `checkDomain.json` 的設定，批次檢查網域連線狀態
- **工具 C (Jira Tool)**: Jira API 整合工具，支援 issue 查詢、留言、狀態轉換等操作
- **工具 D (Sheet Tool)**: 用 service account 讀取 Google 試算表，主要用途是查詢 API 2.0 群組的 `groupInfo`（v1.5.0+）

## 📚 相關文檔

- [動態字段使用指南](DYNAMIC_FIELDS_GUIDE.md) - 如何使用自定義字段功能（v1.1.0+）
- [緩存優化報告](CACHE_OPTIMIZATION_REPORT.md) - 性能優化詳情（v1.1.0）
- [第一階段完成報告](PHASE1_COMPLETION_REPORT.md) - v1.1.0 改進總結
- [第二階段規劃](PHASE2_PLAN.md) - 未來功能規劃

---

## 🔧 工具選擇

使用對應作業系統的執行腳本，並傳入參數選擇要執行的工具：

```batch
# Windows
project-tool.bat A <configFilePath>   # 工具 A: White Label Generator
project-tool.bat B                    # 工具 B: Domain Checker
project-tool.bat D <command> [選項]    # 工具 D: Sheet Tool
# 工具 C 使用 java -jar 直接執行，詳見下方說明
```

```bash
# Mac / Linux
./project-tool.sh A <configFilePath>  # 工具 A: White Label Generator
./project-tool.sh B                   # 工具 B: Domain Checker
./project-tool.sh D <command> [選項]   # 工具 D: Sheet Tool
# 工具 C 使用 java -jar 直接執行，詳見下方說明
```

> 代號 `C` 保留給 Jira Tool（以 `java -cp` 直接呼叫），所以 Sheet Tool 用 `D`。

---

## 📥 工具 A: JSON 檔案格式 (White Label Generator)

請提供一個 JSON 檔案作為輸入，可指定任意檔案路徑與名稱。

### ✅ JSON 格式說明

設定檔由兩部分組成：
1. **基本欄位**：提供 placeholder 的資料來源（如 `ticketNo`、`webSiteName` 等）
2. **`files` 陣列**：動態定義要產出哪些檔案、使用哪個 template、輸出到哪裡

```json
{
  "ticketNo": "SACRIC-1200",
  "webSiteName": "ABC_SITE",
  "webSiteValue": 101,
  "jiraSummary": "[ApiWallet] ABC_SITE",
  "developer": "Wilson",
  "apiWhiteLabel": true,
  "apiWalletInfo": {
    "cert": "ABC123-CERT",
    "group": "A48",
    "newGroup": true,
    "groupInfo": {
      "privateIpSetId": "ipset-private-1",
      "privateIp": ["192.168.0.1"],
      "bkIpSetId": ["bk1-id", "bk2-id"],
      "apiInfoBkIpSetId": "api-info-id",
      "backup": ["abc-api1.com", "abc-api2.com"]
    }
  },
  "files": [
    {
      "name": "{$ticketNo}-DB-01.sql",
      "isNew": true,
      "location": "./result/sql/",
      "template": "./template/white-label/ApiWallet/DB-01-template.txt"
    },
    {
      "name": "WebSiteType",
      "isNew": false,
      "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
      "template": "./template/white-label/ApiWallet/WST.txt",
      "imports": ["com.nv.commons.website.page.{$className}ApiWalletWebSitePage"]
    }
  ]
}
```

### 🆕 動態自定義字段（v1.1.0+）

除標準欄位外，可加入任意自定義欄位，自動轉為 `{$欄位名}` placeholder：

```json
{
  "project": "SACRIC",
  "lowLiquidity": 20000
}
```

自動生成：`{$project}` = "SACRIC"、`{$lowLiquidity}` = "20000"，可直接在 template 中使用。

### 🗂 模板／輸出路徑依白牌類型分類（v1.3.0+）

`src/template/white-label/` 底下依白牌類型分為 `ApiWallet/`、`NewSite/` 子目錄，`Const.txt` 則放在 `white-label/` 根目錄（插入 Const.js，不分類型）。由於 `files[].location`、`files[].template`、`files[].name` 本來就是可自由填寫的字串（支援 placeholder），這種依類型分類只是把模板檔案實際放到對應子目錄，並在 JSON 設定檔的 `template` 欄位填對應路徑即可，**不需要修改 `WhiteLabelTool.java`**：

```json
{
  "ticketNo": "SACRIC-1200",
  "project": "SACRIC",
  "files": [
    {
      "name": "{$project}-{$ticketNo}-DB-01.sql",
      "isNew": true,
      "location": "./result/sql/ApiWallet/",
      "template": "./template/white-label/ApiWallet/DB-01-template.txt"
    },
    {
      "name": "{$project}-{$ticketNo}-DB-01.sql",
      "isNew": true,
      "location": "./result/sql/NewSite/",
      "template": "./template/white-label/NewSite/DB-01-template.txt"
    }
  ]
}
```

- **模板分類**：`template/white-label/ApiWallet/`、`template/white-label/NewSite/` — 依白牌類型放置對應模板檔案，見下方「常用模板檔案」表格
- **輸出路徑分類**：`result/sql/...` 底下要分幾層子目錄、叫什麼名字，完全由 `files[].location` 決定，工具本身不限制或硬編碼任何白牌類型的輸出路徑
- **檔名前綴**：SQL 檔名想帶上 `project` 前綴（如 `SACRIC-1200-...`）→ 用 v1.1.0 動態欄位機制加一個 `"project": "SACRIC"`，即可在 `name`/`template` 中用 `{$project}`
- **範例設定檔**：`src/template/whiteLabel.json`（ApiWallet 白牌完整範例）、`src/template/sample-urlChecker.json`（工具 B 範例）可直接參考或複製修改

> ⚠️ **已知差異**：模板目錄重組後，`NewGroup-SQL-DEV/UAT/SIM-template.txt`、`UpdateGroup-SQL-template.txt` 這幾個新建/更新 API Group 用的 SQL 模板已被移除，未搬移到新目錄結構下。若既有設定檔的 `files[].template` 仍指向這些路徑（例如 `newGroup:true` 的流程），會因為找不到模板檔案而處理失敗，目前尚未補上對應模板。

---

## 🌍 多環境架構

環境值自 v1.4.0 起**由外部設定檔提供**（`config/env-values.json`），改值不需要重新打包 JAR。

| 環境 | 代號 | 靜態資源子域名 | API 子域名 |
|------|------|---------------|-----------|
| 開發環境 | DEV | `devnginx` | `dev9wapi` |
| 測試環境 | UAT | `tberwxsjyk` | `uat9wapi` |
| 正式環境 | SIM | `www` | `saapipl` |

上表是 `config/env-values.json` 的預設內容，同時也是 `constant/EnvEnumType` 內建的 fallback 值。

在 `files` 中設定 `"environments": ["DEV", "UAT", "SIM"]`，工具會自動展開為三個環境的檔案，`{$env}` 在 `name` 和 `template` 路徑中會被替換為對應環境名稱。

### 🗄 環境值外部化（`config/env-values.json`，v1.4.0+）

任何「依環境而變的值」都放這個檔，每個 key 自動成為一個 `{$key}` placeholder：

```json
{
  "DEV": { "subDomainStatic": "devnginx",   "subDomainApi": "dev9wapi" },
  "UAT": {
    "subDomainStatic": "tberwxsjyk",
    "subDomainApi": "uat9wapi",
    "extraPrivateDomains": ["cckk77.net", "cckk77.live"],
    "extraPublicDomains": ["qqkk77.net", "qqkk77.live", "ppkk77.net"],
    "activateOwnDomains": false
  },
  "SIM": { "subDomainStatic": "www",        "subDomainApi": "saapipl"  }
}
```

**三層優先序**（高 → 低）：

1. 單號 JSON 的 `envValues` 區塊（逐單客製）
2. 共用的 `config/env-values.json`
3. `constant/EnvEnumType` 內建值（只有 `subDomainStatic` / `subDomainApi`）

**保留字與特殊 key**：

| key | 型別 | 說明 |
|-----|------|------|
| `env` | — | **保留字**，由工具寫入 `{$env}`，設定檔不可提供 |
| `extraPrivateDomains` | string[] | 該環境要額外插入的 private domain（`apidomaintype` 0），預設 `[]` |
| `extraPublicDomains` | string[] | 該環境要額外插入的 public domain（`apidomaintype` 1），預設 `[]` |
| `activateOwnDomains` | boolean | 自家 domain 與 backup 的 `isactive` 是否開啟，預設 `true` |
| 其他任意 key | 純量 | 自動成為 `{$key}` placeholder；不支援巢狀物件或陣列 |

上面三個非純量 key **不會**變成 placeholder，它們是 `NewGroupSqlBuilder` 用來決定 `apidomainname` 要多插幾列、`isactive` 填什麼的資料。這就是舊版寫死在 `WhiteLabelTool` 裡的 `UAT_PUBLIC_DOMAIN_LIST` / `UAT_PRIVATE_DOMAIN_LIST` / `isUat` 判斷。

**新增第 4 個環境**（例如 `PROD`）只要在此檔加一塊、並在 `files[].environments` 加上該名稱即可，**不需要改 Java、不需要重新打包**。

**檔案缺少 / 格式錯誤的行為**：

| 狀況 | 行為 |
|------|------|
| 檔案不存在 | 印警告，退回 `EnvEnumType` 內建值，繼續執行 |
| JSON 解析失敗或結構不合 | **exit 1，零檔案產出** |
| 檔案缺某個環境，但 `EnvEnumType` 有 | 印警告，用內建值 |
| 檔案與 `EnvEnumType` 都沒有該環境 | 跳過該環境，**exit 1** |
| `newGroup=true` 但某個環境沒有明確條目 | **exit 1，零檔案產出** —— `extraPrivateDomains` 等沒有內建 fallback，缺了會產出「看起來合法但錯誤」的 SQL |

### 📄 常用模板檔案（v1.3.0+ 依白牌類型分類）

> ⚠️ **模板的權威來源是部署端 `citixchange_work/ProjectTool/template/`，不是本 repo 的 `src/template/`。**
> JAR **不內建**任何模板（`unzip -l Project-Tool.jar | grep template` 為空），全部由 `files[].template`
> 的相對路徑經 `new FileReader` 讀取，而工作目錄由 `project-tool.sh` 固定 cd 到 `ProjectTool/`。
> 本 repo 的 `src/template/` 只是給本地測試用的鏡像副本，v1.4.0 已與部署端對齊一次；
> **改模板請改部署端**，本副本若又落後，以部署端為準。


| 模板檔案 | 用途 |
|---------|------|
| `ApiWallet/DB-01-template.txt` | API Wallet DB-01 SQL |
| `ApiWallet/DB-01-template-New-Group.txt` | API Wallet 新建群組 DB-01 SQL |
| `ApiWallet/DB-41-template.txt` | API Wallet DB-41 SQL |
| `ApiWallet/WST.txt` | 插入 WebSiteType.java（API wallet） |
| `ApiWallet/WebSitePageTemplate.txt` | 產生 ApiWalletWebSitePage.java |
| `ApiWallet/Setting1.txt` / `Setting2.txt` | 插入 Setting.java（API wallet） |
| `NewSite/DB-01-template.txt` | 一般白牌 DB-01 SQL |
| `NewSite/DB-41-template.txt` | 一般白牌 DB-41 SQL |
| `NewSite/WST.txt` | 插入 WebSiteType.java（一般白牌） |
| `NewSite/WebSitePageTemplate.txt` | 產生 WebSitePage.java |
| `NewSite/DomainTypeTemplate.txt` | 產生 DomainType.java |
| `NewSite/Setting1.txt` / `Setting2.txt` | 插入 Setting.java（一般白牌） |
| `Const.txt`（`white-label/` 根目錄） | 插入 Const.js |

> `NewGroup-SQL-{DEV/UAT/SIM}-template.txt`、`UpdateGroup-SQL-template.txt` 已在 v1.3.0 移除，尚無替代模板。

---

## 🧾 基本欄位說明

| 欄位                           | 類型 | 是否必填 | 說明 |
|------------------------------|------|----------|------|
| `ticketNo`                   | string | ✅ 是 | 需求代號，如 `SACRIC-12345` |
| `webSiteName`                | string | ✅ 是 | 網站名稱，會衍生 `{$className}`、`{$enumName}` 等 placeholder |
| `webSiteValue`               | int | ✅ 是 | 對應的整數代碼 |
| `jiraSummary`                | string | ✅ 是 | Jira issue 標題 |
| `host`                       | string | 條件必填 | 一般白牌 host，`apiWhiteLabel=false` 時必填 |
| `apiWhiteLabel`              | boolean | 否 | 若為 true，需提供 `apiWalletInfo` |
| `sqlOnly`                    | boolean | 否 | true 時僅處理 SQL 相關 files entry |
| `developer`                  | string | 否 | 開發者名稱，預設 "MCP" |
| `apiWalletInfo.cert`         | string | 條件必填 | API 憑證代號 |
| `apiWalletInfo.group`        | string | 條件必填 | 群組名稱，如 `A48` |
| `apiWalletInfo.newGroup`     | boolean | 條件必填 | true 時需提供 `groupInfo` |
| `groupInfo.privateIpSetId`   | string | 條件必填 | 私有 IP 設定 ID |
| `groupInfo.privateIp`        | string[] | 條件必填 | 私有 IP 清單 |
| `groupInfo.bkIpSetId`        | string[] | 條件必填 | 備援 IP 設定 ID（需兩筆） |
| `groupInfo.apiInfoBkIpSetId` | string | 條件必填 | API 備援設定 ID |
| `groupInfo.backup`           | string[] | 條件必填 | 備援 domain 清單 |
| `envValues`                  | object | 否 | 逐單的環境值覆寫，形狀 `{"<ENV>": {"<key>": "<value>"}}`，優先序高於 `config/env-values.json`（v1.4.0+，見「環境值外部化」）。這是**宣告式欄位**，不會變成 `{$envValues}` placeholder |

自定義欄位（v1.1.0+）：任意新增欄位皆自動成為 `{$欄位名}` placeholder，支援字串、數字、布林值（例如 `project`，見上方「用 `files[]` + 動態欄位達成路徑分類」）。

## 🗂 `files` 陣列欄位說明（v1.2.6+）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `name` | string | `isNew:true` 時為輸出檔名；`isNew:false` 時為標籤。可含 `{$ticketNo}`、`{$className}`、`{$env}` 等 placeholder |
| `isNew` | boolean | `true`=新建檔案；`false`=插入現有檔案 |
| `location` | string | `isNew:true` 時為輸出目錄；`isNew:false` 時為目標檔案完整路徑 |
| `template` | string | Template 檔案路徑，可含 `{$env}` |
| `environments` | string[] | 多環境展開，如 `["DEV","UAT","SIM"]`；`{$env}` 自動替換為各環境名稱 |
| `marker` | string | `isNew:false` 時的插入點 keyword，預設 `// insert New White Label` |
| `insertAfter` | boolean | 插入在 marker 之後（true）或之前（false），預設 false |
| `imports` | string[] | `isNew:false` 時要插入的 import 路徑，支援 `{$className}` 等 placeholder |

---

## 🛠 工具 A: 檔案路徑參數使用方式

White Label Generator 現在支援自訂配置檔案路徑，不再限制於預設檔名。

### 使用範例

```bash
# 使用相對路徑
./project-tool.sh A ./config/whiteLabelConfig.json
./project-tool.sh A ./data/site-config.json

# 使用絕對路徑
./project-tool.sh A /home/user/configs/my-site.json

# 使用不同檔名
./project-tool.sh A ./custom-config.json
```

### 參數說明
- `<configFilePath>`: 必填參數，指定 WhiteLabel 配置檔案的完整路徑
- `[envValuesFilePath]`: 選填參數（v1.4.0+），覆寫環境值設定檔路徑，預設為 `./config/env-values.json`
- 支援相對路徑和絕對路徑（相對路徑以工作目錄為基準，`project-tool.sh` 會先 `cd` 到工具根目錄）
- 檔案格式必須為有效的 JSON
- 檔案內容需符合 WhiteLabel 結構驗證要求

### ⛔ 結束代碼（v1.4.0+）
- `0`: 全部檔案產出成功
- `1`: 環境值設定錯誤、環境名稱未知、或有檔案因未解析的 placeholder 而中止

未解析的 placeholder 會**在寫檔之前**擋下，並印出檔名、行號、token 與該行原文。這讓 `white-label-process.sh` 的 `if [ $? -eq 0 ]` 能確實攔住錯誤產出，不讓 `SQL-processing.sh` 拿到壞的 `.sql`。

---

## 📡 工具 B: checkDomain.json 檔案格式 (Domain Checker)

請提供一個 JSON 檔案作為輸入，檔名預設為 `checkDomain.json`。

### ✅ JSON 格式說明（Domain Config 結構）

```json
{
  "subdomain": "api",
  "isHttps": true,
  "domainList": ["example.com", "test.com", "demo.net"],
  "connectTimeout": 10000,
  "readTimeout": 15000
}
```

---

## 🧾 checkDomain.json 欄位說明

| 欄位                | 類型 | 是否必填 | 說明                                       |
|-------------------|------|----------|------------------------------------------|
| `subdomain`       | string | 否 | 子網域前綴，若為空則直接檢查主網域                        |
| `isHttps`         | boolean | ✅ 是 | 是否使用 HTTPS 協定，false 為 HTTP                |
| `domainList`      | string[] | ✅ 是 | 要檢查的網域清單                               |
| `connectTimeout`  | int | 否 | 連線逾時時間（毫秒），預設 15000                     |
| `readTimeout`     | int | 否 | 讀取逾時時間（毫秒），預設 15000                     |

---

## ▶️ 執行方式

### 🔨 編譯與建置

```bash
# 編譯專案
mvn compile

# 打包成 JAR 檔（包含相依套件）
mvn package
```

### ✅ 執行工具

執行腳本位於 `src/main/resources/` 目錄下：

```batch
# Windows
cd src\main\resources
project-tool.bat A ./config/my-config.json    # 執行 White Label Generator
project-tool.bat B                            # 執行 Domain Checker
```

```bash
# Mac / Linux
cd src/main/resources
chmod +x project-tool.sh                      # 賦予執行權限（首次執行）
./project-tool.sh A ./config/my-config.json   # 執行 White Label Generator
./project-tool.sh B                           # 執行 Domain Checker
```

### 📋 執行前準備

1. **建置專案**: 執行 `mvn package` 產生 JAR 檔案
2. **複製檔案**: 將 `Project-Tool.jar` 從 `target/` 複製到實際部署位置（本專案為 `citixchange_work/ProjectTool/Project-Tool.jar`），並同步 `src/config/env-values.json` → `ProjectTool/config/env-values.json`
3. **準備設定檔**: 準備對應的 JSON 設定檔
   - **工具 A**: 可使用任意檔案路徑與名稱的 WhiteLabel 設定檔（建議：`whiteLabelConfig.json`）
   - **工具 B**: 將 `checkDomain.json` 檔案放置於 `src/main/resources/` 目錄下
4. **Mac/Linux**: 賦予腳本執行權限 `chmod +x project-tool.sh`

### 🎯 自動功能

執行腳本提供以下自動化功能：
- 自動檢查 JAR 檔案是否存在，若不存在會提示執行 `mvn package`
- 自動建立 `result` 資料夾存放輸出檔案
- 自動切換到腳本所在目錄執行

---

## 🎫 工具 C: Jira Tool

Jira Tool 是一個命令列工具，用於與 Jira REST API 互動，支援以下功能：

### ✨ 主要功能

1. **Issue 查詢** - 取得 issue 的詳細資訊
2. **留言管理** - 取得和新增 issue 留言
3. **狀態轉換** - 轉換 issue 狀態（支援自動驗證）
4. **開發流程** - 一鍵啟動 issue 開發流程
5. **JQL 搜尋** - 使用 JQL 進行進階搜尋

### 📋 使用方式

```bash
# 執行 JiraTool
java -cp target/Project-Tool.jar tool.http.JiraTool <command> [arguments] [options]

# 顯示說明
java -cp target/Project-Tool.jar tool.http.JiraTool --help
```

### 🔧 可用命令

#### 1. get-issue - 取得 Issue 資訊
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool get-issue <issueKey>

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool get-issue SACRIC-1020
```

查詢欄位由 `application.properties` 的 `jira.issue.fields` 控制（見下方配置檔案說明），未設定時 Jira 會回傳完整預設欄位。

#### 2. get-comments - 取得 Issue 留言
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool get-comments <issueKey>

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool get-comments SACRIC-1020
```

#### 3. get-transitions - 取得可用的狀態轉換選項
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool get-transitions <issueKey>

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool get-transitions SACRIC-1020
```

#### 4. post-comment - 新增留言
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool post-comment <issueKey> <commentText> [options]

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool post-comment SACRIC-1020 "This is a comment"

# 測試模式（不實際發送）
java -cp target/Project-Tool.jar tool.http.JiraTool post-comment SACRIC-1020 "Test comment" -t
```

#### 5. transition-issue - 轉換 Issue 狀態
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool transition-issue <issueKey> <transitionKey> [options]

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool transition-issue SACRIC-1020 TO_DEV

# 測試模式
java -cp target/Project-Tool.jar tool.http.JiraTool transition-issue SACRIC-1020 TO_DEV --testMode
```

**可用的 Transition Keys**:
- `OPEN` (41) - Not Start Yet
- `REJECT_1` (301) - IN ANALYSIS
- `TO_DEV` (101) - IN DEV
- `REJECT` (111) - Ready to DEV
- `DEV_DONE` (121) - DEV DONE
- `RESOLVED` (221) - Resolved

**✅ 自動驗證功能**: 在執行 transition 前，會自動檢查 transition ID 是否有效，避免執行無效的狀態轉換。

#### 6. start-jira-issue - 啟動開發流程
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool start-jira-issue <issueKey> [options]

# 範例
java -cp target/Project-Tool.jar tool.http.JiraTool start-jira-issue SACRIC-1020

# 測試模式（跳過狀態檢查和轉換）
java -cp target/Project-Tool.jar tool.http.JiraTool start-jira-issue SACRIC-1020 -t
```

**執行步驟**:
1. **Step 1**: 檢查 issue 狀態是否為 "Ready to DEV"
2. **Step 2**: 將狀態轉換為 "IN DEV"（包含 transition ID 驗證）
3. **Step 3**: 取得 issue 詳細資訊並儲存至 `./result/jira/{issueKey}-jira.txt`

#### 7. enhanced-search - JQL 進階搜尋
```bash
java -cp target/Project-Tool.jar tool.http.JiraTool enhanced-search <issueList> [templatePath]

# 範例：單一 issue
java -cp target/Project-Tool.jar tool.http.JiraTool enhanced-search SACRIC-1020

# 範例：多個 issues（逗號分隔，不加空格）
java -cp target/Project-Tool.jar tool.http.JiraTool enhanced-search SACRIC-1020,SACRIC-1021

# 範例：指定自訂 template 路徑
java -cp target/Project-Tool.jar tool.http.JiraTool enhanced-search SACRIC-1020 ./my-search.json
```

**參數說明**：
- `<issueList>`：必填，逗號分隔的 issue key 清單，會替換 template 中的 `{$issueList}` 佔位符
- `[templatePath]`：選填，JSON template 路徑，預設為 `./template/jira/enhanced-search.json`

### ⚙️ 配置檔案

JiraTool 需要 `application.properties` 配置檔案，包含以下設定：

```properties
# Jira API 基礎 URL
jira.baseUrl=https://your-domain.atlassian.net

# 認證資訊（Basic Auth）
jira.account=your-email@example.com
jira.token=your-api-token

# Timeout 設定（毫秒，選填）
connectTimeout=30000
readTimeout=60000
writeTimeout=60000

# get-issue 查詢欄位（逗號分隔，選填，留空或不設定則不夾帶 fields 參數，由 Jira 回傳完整預設欄位）
jira.issue.fields=id,key,assignee,fixVersions,status,customfield_10072,customfield_10037,comment,description,summary
```

### 📁 Template 目錄

JiraTool 的 template 檔案為**外部檔案**，打包後仍可直接修改，無需重新編譯。部署時請保持以下目錄結構：

```
/deploy/
├── Project-Tool.jar
├── application.properties
└── template/
    └── jira/
        ├── enhanced-search.json    ← 修改 JQL 搜尋條件
        ├── post-comment.json       ← 修改留言格式
        └── transition-issue.json   ← 修改狀態轉換格式
```

**Template 佔位符格式**：`{$變數名}` — 例如 `enhanced-search.json` 中的 `{$issueList}` 會由命令列參數帶入。

### 📁 輸出檔案結構

執行 `start-jira-issue` 命令後，會在以下位置產生輸出檔案：

```
result/
└── jira/
    ├── SACRIC-1020-jira.txt    # Issue 詳細資訊（JSON 格式）
    └── SACRIC-1021-jira.txt
```

### 🎯 測試模式

所有支援的命令都可以使用測試模式：
- 使用 `-t` 或 `--testMode` 參數
- 測試模式下會顯示 Jira Config 資訊
- 部分操作會跳過實際的 API 調用

### 🔒 安全性

- 使用 Jira API Token 進行認證（Basic Auth）
- Token 在顯示時會自動遮罩（只顯示前後 10 個字元）
- 支援 HTTPS 連線

---

## 📊 工具 D: Sheet Tool（v1.5.0+）

用 Google service account 讀取試算表。主要用途是查詢 **API 2.0 群組的 `groupInfo`** ——
`privateIpSetId` / `privateIp` / `bkIpSetId` / `apiInfoBkIpSetId` / `backup`，
產出可直接貼進白牌單 JSON 的 `apiWalletInfo.groupInfo`。

在這之前這些值是人工從試算表抄進 `ProjectTool/task/api-2.0-group-info.md` 的快照，
抄錯或漏抄都曾造成產出錯誤的 SQL 而 step 3／step 4 仍回報成功。

### 📋 使用方式

```bash
# 透過 MainSelector
./project-tool.sh D group-info A69

# 或直接呼叫（腳本用這種）
java -cp Project-Tool.jar tool.sheet.SheetTool group-info A69

# 查完直接填回白牌單 JSON（白牌流程 step 2.5 的做法）
java -cp Project-Tool.jar tool.sheet.SheetTool group-info A69 --patch sample/New\ Group/SACRIC-1410.json

# 顯示說明
java -cp Project-Tool.jar tool.sheet.SheetTool --help
```

### 🔧 可用命令

| 命令 | 說明 |
|---|---|
| `check-auth` | 驗證憑證。**不碰任何試算表**，所以不需要 spreadsheetId |
| `list-tabs` | 列出所有分頁，並檢查設定檔的 `tab` 是否逐字相符 |
| `read-tab` | 讀整個分頁，可用 `--max-rows` 限制 |
| `read-columns` | 讀指定欄位，欄位用字母指定 |
| `group-info <code>` | 查詢群組的 `groupInfo`，**stdout 只有純 JSON**（狀態走 stderr，可直接 pipe） |
| `group-info <code> --patch <ticket.json>` | 同上，再把結果**填回**白牌單 JSON 的 `apiWalletInfo.groupInfo`。白牌流程 step 2.5 用的就是這個 |

共通選項：`--config` / `--credential` / `--target` / `--spreadsheetId` / `--tab` /
`--columns` / `--rows <start:end>` / `--max-rows` / `--format table|tsv|json` /
`-t`（印出解析後的 range 與每格 backup 的來源）/ `-h`。

欄位一律用**字母**指定，範圍用 `-` 或 `:`：`H`、`J-K`、`U:V`、`B,H,J-K,M,S,U-Y`。

#### `--patch` 的合併規則

逐欄獨立判斷，**不會覆寫已填的值**：

| 單子現值 | 動作 |
|---|---|
| 未填（`null`、空字串、空陣列，或 `"privateIpSetId"` / `"backup1"` 這類字面假值） | 填入試算表的值 |
| 已填且與試算表相同 | 不動（`-s 3` 重跑會走到這裡） |
| 已填且與試算表不同 | **不動**，把兩邊的值印到 stderr 警告 |

沒有任何欄位需要填時**不改寫檔案**，避免只有格式差異的 diff；要改寫時走
temp file + rename，並沿用 `jq .` 的排版（2 空格縮排），所以不會擾動 step 2 的產出格式。

⚠️ 有 `--patch` 時，`apiInfoBkIpSetId` 不是 UUID（例如試算表寫 `Cloudfront`）會從**警告升級為錯誤**
（exit 1，檔案不動）—— 那個值會直接進正式 SQL，寧可中止也不要靜靜寫錯。沒有 `--patch` 時維持警告。

### ⚙️ 配置檔案

**`config/sheet-config.json`**（進版控）

```json
{
  "credentialPath": "./config/service-account.json",
  "applicationName": "Project-Tool Sheet Reader",
  "defaultTarget": "api20-domain",
  "targets": {
    "api20-domain": {
      "spreadsheetId": "<試算表 ID>",
      "tab": "API 2.0Domain配置_20260814",
      "startRow": 4,
      "endRow": null,
      "valueRenderOption": "FORMATTED_VALUE",
      "groupInfo": {
        "groupCodeColumn": "B",
        "maxRowsPerGroup": 5,
        "privateIpSetIdColumn": "H",
        "privateIpColumns": ["J", "K"],
        "bkIpSetIdColumn": "M",
        "bkIpSetIdWafNameColumn": "N",
        "apiInfoBkIpSetIdColumn": "S",
        "backupColumns": ["U", "V", "W", "X", "Y"],
        "emptyMarkers": ["-"]
      }
    }
  }
}
```

- `targets` 是具名字典，加第二張表不用改結構；`defaultTarget` 讓常用的那個不必打 `--target`
- `valueRenderOption` 用 `FORMATTED_VALUE` 是刻意的 —— `UNFORMATTED_VALUE` 會把數字回成
  `Double`（`1.0E10` 這種形式），而這個工具要的是人在畫面上看到的字
- `bkIpSetIdWafNameColumn` 是選用的交叉驗證：第 1 列該是 `*-GA-*`、第 2 列該是 `*-CF-*`，
  不符只警告不擋
- **路徑解析**：依序找 `./config/<檔名>` → `./src/config/<檔名>`。
  部署期 CWD 是 `ProjectTool/`（命中前者），開發期是 repo 根目錄（退到後者）。
  可用 `-DprojectTool.configDir=<dir>` 覆寫

**`config/service-account.json`**（**絕不進版控**）

GCP 下載的 service account 金鑰。兩個 repo 的 `.gitignore` 都已擋下，
範本見 `config/service-account.sample.json`。

### 🗺 試算表結構與對應規則

一個群組佔**連續數列**（多數 2 列，但不固定；實測有 1 列與 3 列的例子）。
定位方式是「找 `B` 欄等於該代號的**連續**列」—— B 欄在群組的每一列都有值，
不連續的是**代號的排列**（A04 → A05 → A07 → …）。

| `groupInfo` 欄位 | 來源 |
|---|---|
| `privateIpSetId` | `H`（群組第 1 列） |
| `privateIp` | `J` / `K`（群組第 1 列） |
| `bkIpSetId` | `M`，依列順序（第 1 列 GA、第 2 列 CF） |
| `apiInfoBkIpSetId` | `S`（群組第 1 列） |
| `backup` | `U`~`Y`，**欄優先、欄內依列**：U1 → U2 → V1 → V2 → W1 → … |

**單一 cell 內含多個網域且數量不固定**，取出後要先切分（換行／逗號／分號皆可）再串接。
cell 內容為 `-` 一律視為無資料，整格跳過。

### 🔒 安全性

- 只申請 `spreadsheets.readonly` scope
- 啟動時檢查憑證是否**已被 git 追蹤**，是就擋下並 exit 1 ——
  `.gitignore` 對已追蹤的檔案無效，這是唯一真正防得住誤 commit 的機制
- 檢查檔案權限，group/other 可讀就提示 `chmod 600`
- 讀取前確認內容是 service account 金鑰（有 `type` 與 `client_email`）
- 輸出只帶 `client_email` / `project_id` / `private_key_id` 末 6 碼，
  **結構上不持有** `private_key` 與 access token

### ⛔ 結束代碼

與工具 C 不同，**任何錯誤都會 `System.exit(1)`**，讓 shell 的 `$?` 判斷得出來。

### 💡 常見問題

| 症狀 | 原因 |
|---|---|
| `Google Sheets API 尚未啟用 (403)` | GCP 專案沒開 Sheets API，照訊息裡的 console 連結啟用 |
| `讀取被拒 (403)` | 試算表沒共用給 service account。訊息會印出要加的 email |
| `範圍解析失敗 (400)` | 分頁名對不上。先跑 `list-tabs` 看線上的實際名稱 |
| `找不到試算表 (404)` | spreadsheetId 錯，或該檔是 Excel 原生格式（需先轉成 Google 試算表） |

---

## 🔍 驗證與容錯

### 工具 A (White Label Generator)
- 使用 `@Valid` 驗證 JSON 結構與欄位
- 支援條件邏輯驗證：如 `apiWhiteLabel = true` 時，`apiWalletInfo` 不可為空, `newGroup = true` 時，`groupInfo` 不可為空
- 自動檢查必填欄位完整性

### 工具 B (Domain Checker)
- 自動檢查 `domainList` 是否為空
- 支援逾時設定，避免無限等待
- 提供連線狀態回饋（✅ 200 OK / ❌ 錯誤碼 / ⚠️ 連線失敗）

### 工具 C (Jira Tool)
- **Transition ID 自動驗證**: 執行狀態轉換前會先檢查 transition ID 是否有效
- **重複留言檢查**: 新增留言前會檢查是否已存在相同內容
- **錯誤處理**: 提供清楚的錯誤訊息和可用選項列表
- **輸出格式**: 所有輸出訊息為英文，保持一致性

---

## 🛠 建議

### 工具 A (White Label Generator)
- 若格式變更頻繁，建議改用 JSON Schema 驗證
- 可搭配 Git commit hook 或 CI 工具自動驗證 JSON 檔案合法性
- 新增環境時，遵循以下步驟：
  1. 在 `constant/EnvEnumType.java` 添加新的環境枚舉值
  2. 在 `src/template/white-label/{ApiWallet,NewSite}/` 底下建立對應環境的模板檔案，並在 `files[].template` 填入 `{$env}` placeholder
- SQL 結構變更建議在模板檔案中統一修改，無需修改 Java 代碼
- 依白牌類型分類模板/SQL 輸出路徑、或用票號當檔名前綴等需求，優先透過 `files[].location`／`files[].template`／動態自定義欄位（如 `project`）在 JSON 設定檔中處理，不需要修改 Java 代碼（見上方「模板／輸出路徑依白牌類型分類」）

### 🆕 動態字段最佳實踐（v1.1.0+）
- **命名規範**：使用駝峰命名（如 `siteCategory` 而非 `site_category`）
- **類型建議**：優先使用字符串，避免複雜對象和數組
- **文檔化**：在 JSON 配置中添加註釋說明字段用途
- **核心字段**：頻繁使用的字段建議添加到 `WhiteLabelConfig.java`（獲得類型安全和驗證）
- **臨時字段**：短期使用或實驗性字段適合使用動態字段功能
- **性能提示**：v1.1.0 已優化占位符生成性能，減少 56-64% 重複計算
- **詳細指南**：參考 [DYNAMIC_FIELDS_GUIDE.md](DYNAMIC_FIELDS_GUIDE.md)

### 工具 B (Domain Checker)
- Domain Checker 可搭配 CI/CD 流程進行網域可用性監控

### 工具 C (Jira Tool)
- **配置管理**: 使用 `application.properties` 集中管理 Jira 連線資訊（`jira.baseUrl`/`jira.account`/`jira.token`/`jira.issue.fields`）
- **測試模式**: 開發和測試時使用 `-t` 參數避免實際修改 Jira 資料
- **檔案輸出**: `start-jira-issue` 會將 issue 資訊儲存至 `./result/jira/` 目錄，便於後續處理
- **API 整合**: 可作為 CI/CD 流程的一部分，自動化 issue 管理
- **擴展性**: 新增 transition 狀態時只需修改 `JiraTransitionId` enum

### 工具 D (Sheet Tool)
- **憑證絕不進版控**: 只放 `config/service-account.json`，兩個 repo 的 `.gitignore` 都已擋下；
  工具啟動時還會主動檢查該檔是否已被 git 追蹤
- **先 `check-auth` 再查資料**: 它不碰試算表，可以把「憑證有問題」與「試算表沒共用」分開判斷
- **換試算表先跑 `list-tabs`**: 分頁名必須逐字相符（注意空白數量與全形／半形）
- **欄位對應放設定檔、走訪規則放程式**: 欄位字母搬動是常見變化，
  但 backup 的「欄優先、欄內依列」與 cell 內切分是演算法，寫進 JSON 只會變成難讀的 schema
- **改動對應規則後，用 A61~A69 九組回歸**: `task/api-2.0-group-info.md` 有九組人工核對過的值，
  是現成的黃金基準

---

## 📝 版本歷史

### v1.5.3 (2026-09-15)
- 🐛 **`bkIpSetId` 長度守衛** - `NewGroupSqlBuilder` 原本寫成
  `!isEmpty() ? get(0) : null` / `!isEmpty() ? get(1) : null`，兩個問題：
  只有一個元素時 `get(1)` 直接 IndexOutOfBounds；一個都沒有時兩者都是 `null`，
  而 `TemplateEngine.fill` 把 null 當空字串，於是 SQL 寫進 `''` 且
  `PlaceholderValidator` 放行（沒有 `{$token}` 殘留）—— 又一條靜默錯誤
  - 「只有一個」是真實資料到得了的狀態：`GroupInfoMapper` 逐列取試算表的 M 欄，
    空白或 `-` 會被跳過，所以群組第二列 M 欄沒填就只會回一個
  - `WhiteLabelConfig.validate()` 的檢查從「非空」升級為「至少 2 個」，
    在產生任何檔案之前就擋下，訊息說明第 1 個是 GA、第 2 個是 CF
  - `NewGroupSqlBuilder` 另有防守檢查（繞過 validate 的呼叫端），不足丟 `IllegalStateException`
  - 超過兩個 → 用前兩個並印警告，不中止
- ✅ **新增 5 個單元測試** - `NewGroupSqlBuilderTest` 的 bkIpSetId 長度案例，共 198 項

### v1.5.2 (2026-09-15)
- 🐛 **`TemplateEngine` 不再吞掉 IO 例外** - `fillFile` 讀不到模板時原本只印一行 stderr
  並回傳**空字串**，呼叫端照樣寫檔並印 `✅ Created`，於是產生 0-byte 檔而整個流程全綠。
  2026-09-09 實際發生：情境 6 指向不存在的 `RacingOnly/DB-41-template.txt`，三個 DB-41 都是空的。
  `PlaceholderValidator` 攔不到 —— 空字串裡沒有任何 `{$token}`。現在 `fillFile` 與 `writeToFile`
  都 `throws IOException`
- 🐛 **`insertAtMarker` 找不到 marker 時中止** - 原本什麼都不插，卻無條件印
  `✅ Content successfully written` 並把原內容照樣寫回：檔案看起來被處理過，實際少了一整段
  註冊程式碼，而且不編譯錯（只是執行期少一個站台）。現在丟 `MarkerNotFoundException`，
  而且是在 `Files.write` 之前 —— **目標檔一個 byte 都不會動**
  - marker 出現多次會印警告（維持既有的「插入多次」行為）
- 🐛 **`WhiteLabelTool` 的兩個通用 catch 補上 `hasError = true`** - 以前印了 ❌ 卻沒設旗標，
  step 3 照樣 `exit 0`。少了這個，上面兩項修正丟出來的錯會被靜靜吃掉
- ✨ **空內容守衛** - 模板讀得到但填值後是空的（或只有空白）就不寫出，這是通往 0-byte 檔的
  第二條路徑。白牌產出不存在「合法的空檔案」
- 🔧 **成功訊息移到實際寫入之後** - `insertAtMarker` 與 `insertImportStatement` 原本先印 ✅ 再
  `Files.write`
- ✅ **新增 16 個單元測試** - `TemplateEngineTest`(8)、`InsertAtMarkerTest`(8)，共 193 項

### v1.5.1 (2026-09-15)
- ✨ **`SheetTool` 新增 `--patch <ticket.json>`** - 查到的 `groupInfo` 直接填回白牌單 JSON。
  逐欄獨立判斷、不覆寫已填的值（不同就警告）、沒有欄位要填就不改寫檔案；
  沿用 `jq .` 的排版，不擾動既有格式。白牌流程的 step 2.5 用的就是這個
  - 有 `--patch` 時，`apiInfoBkIpSetId` 不是 UUID 會從警告升級為錯誤並 exit 1、檔案不動
- 🐛 **`JiraTool` 失敗時不再回 exit 0** - 以前每條失敗路徑都只是 `return`，JVM 照樣回 0，
  呼叫端的 `if java -cp ... ; then` 把失敗當成功。最嚴重的是 `start-jira-issue` 因狀態不符
  而中止時，腳本只檢查 `result/jira/<TICKET>-jira.txt` 存不存在，於是拿上一次留下的**舊檔**
  當最新描述用（2026-09-10 實際誤導過：以為 BA 沒改單，其實檔案 mtime 停在前一天）
  - 新增 `JiraToolException`，工具自己判定的失敗一律往上拋，退出碼由 `main` 統一負責
  - unknown command、7 處參數不足、大 catch 全部改為 exit 1
  - `post-comment` 的「留言已存在，跳過」維持 exit 0 —— 那是冪等跳過，不是失敗
- 🔧 **`start-jira-issue` 接受 `IN DEV`** - 已經是 IN DEV 就跳過轉態、直接重新抓取，
  所以 `-s 1` 重跑可行，也能拿它抓最新描述（以前只認 `Ready to DEV`，其餘一律中止）
- 🔧 **移除 `commons-collections4`** - 全專案只有 `WhiteLabelConfig` 用到三次 `CollectionUtils.isEmpty`，
  為此背 1.6MB 不划算，改為本地私有方法、行為原樣保留
- 🔧 **排除 Apache HttpClient 相依鏈** - `google-http-client-apache-v2` / `httpclient` / `httpcore` /
  `commons-codec` 在 `google-api-client` 與 `google-http-client` 兩邊都排除。
  那些類別實際上永遠不會被載入（JVM 延遲解析），fat jar 由 14.9MB 減為約 12.7MB
- ✅ **新增 26 個單元測試** - `GroupInfoPatcherTest`(18)、`JiraToolStatusGateTest`(8)，共 177 項

### v1.5.0 (2026-09-11)
- ✨ **新增工具 D (Sheet Tool)** - 用 Google service account 讀取試算表，
  `group-info <代號>` 直接產出可貼進白牌單 JSON 的 `apiWalletInfo.groupInfo`
  - 子指令 `check-auth` / `list-tabs` / `read-tab` / `read-columns` / `group-info`
  - 新增 `config/sheet-config.json`（進版控）與 `config/service-account.sample.json`（範本）
  - 掛在 `MainSelector` 的 `D`（`C` 已保留給 Jira Tool）
- 🛡 **憑證防呆** - 啟動時檢查憑證是否已被 git 追蹤（是就 exit 1）、檔案權限過寬則警告、
  內容不是 service account 金鑰則明確報錯；摘要結構上不持有 `private_key` 與 access token
- 🔧 **`GroupInfo` 加上 `@JsonPropertyOrder`** - 釘死序列化順序與白牌單 JSON 一致，
  產出可直接複製貼上；只影響輸出順序，不影響反序列化
- 🔧 **新增 6 個 Google 相依並明確 pin 版本** - `google-api-services-sheets`、`google-api-client`、
  `google-auth-library-oauth2-http`、`google-http-client(-gson)`、`guava`。
  打包方式不變（實測依賴閉包無簽章 jar、無 `META-INF/services` 撞名），fat jar 由 7.8MB 增為約 14MB
- 🔧 **補上 `project.build.sourceEncoding=UTF-8`** - 原本靠平台預設編碼
- ✅ **新增 89 個單元測試** - `A1RangeBuilderTest`(25)、`GroupInfoMapperTest`(31)、
  `SheetConfigLoaderTest`(13)、`SheetValuesTest`(10)、`ConfigPathResolverTest`(10)；
  另以 A61~A69 九組對 `task/api-2.0-group-info.md` 做端到端比對，9/9 逐字相同

### v1.4.0 (2026-09-08)
- ✨ **環境值外部化** - 新增 `config/env-values.json`，任何依環境而變的值都改為資料；改值不需要重新打包 JAR
  - 三層優先序：單號 `envValues` → 共用設定檔 → `EnvEnumType` 內建值
  - 新增 `EnvValues` / `EnvValuesLoader` / `EnvValuesResolver`，以及 `EnvValuesException` / `UnknownEnvironmentException`
  - `WhiteLabelConfig` 新增宣告式欄位 `envValues`（型別為 `Map`，因此不會被 `PlaceholderMapper` 自動映射成 placeholder）
  - 新增第 4 個環境（如 `PROD`）只要改設定檔，不必改 `EnvEnumType`
- ✨ **`isUat` 分支改為資料驅動** - 抽出 `NewGroupSqlBuilder`，把 `UAT_PUBLIC_DOMAIN_LIST` / `UAT_PRIVATE_DOMAIN_LIST` 與 `isactive` 規則搬進設定檔的 `extraPublicDomains` / `extraPrivateDomains` / `activateOwnDomains`
  - 預設值 `[]` / `[]` / `true` 重現舊版 DEV / SIM 行為；UAT 的三個 key 重現舊版 UAT 行為，產出逐 byte 相同
- 🛡 **未解析 placeholder 一律擋下** - 新增 `PlaceholderValidator` 與 `UnresolvedPlaceholderException`，`processNewFilePerEnv` / `processNewFile` / `processInsertFile` 在寫檔前掃描殘留 `{$...}`，有殘留就不寫檔並讓整批 exit 1
  - 比對用嚴格 regex `\{\$[A-Za-z_][A-Za-z0-9_.]*\}`，刻意不匹配 MySQL JSON path（`'$."44"'`）與 JSON 物件字面值（`'{"key":1}'`），已對 339 個既有產出實測零誤判
- 🔧 **環境改以名稱字串為鍵** - `envReplacementsCache` 改為 `Map<String, ...>`，`EnvEnumType.valueOf` 改為新的寬鬆 `EnvEnumType.findByName`
- 🔧 **`MainSelector A` 接受第三個參數** - 可覆寫環境值設定檔路徑
- 🔧 **`main` 在失敗時 exit 1** - 舊版即使丟出未捕捉的例外仍 exit 0，導致 shell 的 `$?` 判斷無效
- ✅ **新增 36 個單元測試** - `EnvValuesLoaderTest`(10)、`EnvValuesResolverTest`(12)、`NewGroupSqlBuilderTest`(6)、`PlaceholderValidatorTest`(8)

### v1.3.2 (2026-08-20)
- ✨ **`Transformers` 新增 `SNAKE_TO_LOWER_CAMEL`** - 蛇形命名轉小駝峰（首字母小寫），`hello_world` → `helloWorld`；實作上複用 `SNAKE_TO_CAMEL` 再降首字母，行為與大駝峰一致
  - ⚠️ 請勿與現有的 `SNAKE_TO_CAMEL_LOWER` 混淆：後者是「轉駝峰後全小寫」，`hello_world` → `helloworld`，駝峰會被壓平
- ✅ **`PlaceholderMapperTest` 新增測試** - `testTransformers_SnakeToLowerCamel()` 涵蓋一般情形、單字元、全大寫輸入（`HELLO_BIG_WORLD` → `helloBigWorld`）、空字串與 null
- ✨ **`WhiteLabelTool` 新增 `{$lowerCamelCase}` 佔位符** - 由 `webSiteName` 經 `SNAKE_TO_LOWER_CAMEL` 產生，與既有的 `{$className}`（大駝峰）、`{$lowerCase}`（全小寫）並列，供模板取用

### v1.3.1 (2026-07-14)
- 🔧 **`get-issue` 查詢欄位改由設定檔控制** - `application.properties` 新增 `jira.issue.fields`，可自訂逗號分隔欄位清單；未設定或空值時，`get-issue` 不夾帶 `fields` 參數，改由 Jira 回傳完整預設欄位
- 🔧 **`application.properties` 認證/連線 key 加上 `jira.` 前綴** - `baseUrl`/`account`/`token` 改為 `jira.baseUrl`/`jira.account`/`jira.token`，與 `jira.issue.fields` 命名風格一致並區分用途
- 🐛 **修正 `--help` 說明文字** - 修正 `Configuration File` 區塊中與實際設定檔不符的 key 名稱（`jira.base.url` → `jira.baseUrl` 等）
- ✅ **`JiraClientTest` 新增測試** - 驗證 `jira.issue.fields` 正確載入、帶 `fields` 參數時回應僅含指定欄位、未帶 `fields` 參數時回應包含完整預設欄位

### v1.3.0 (2026-07-14)
- 🗂 **模板目錄依白牌類型重組** - `src/template/` 下的白牌模板分類到 `white-label/ApiWallet/` 和 `white-label/NewSite/` 子目錄，`Const.txt` 留在 `white-label/` 根目錄
  - ⚠️ 重組過程中 `NewGroup-SQL-DEV/UAT/SIM-template.txt`、`UpdateGroup-SQL-template.txt` 被移除且未搬移到新結構下，`newGroup:true` 流程若沿用舊路徑會找不到模板檔案，目前尚無替代模板
- ✨ **新增 `ApiWallet/DB-01-template-New-Group.txt`** - API Wallet 新建群組專用的 DB-01 SQL 模板
- ✨ **新增範例設定檔** - `src/template/whiteLabel.json`（ApiWallet 白牌完整範例）、`src/template/sample-urlChecker.json`（工具 B 範例）
- 📚 **補充說明：模板/SQL 路徑分類、`project` 前綴的達成方式** - 依白牌類型分類模板/SQL 輸出路徑、用 `project` 當檔名前綴等效果，是透過既有的 `files[]` 動態配置（v1.2.6）與動態自定義欄位機制（v1.1.0）在 JSON 設定檔中達成，`WhiteLabelTool.java` 未變動，見「模板／輸出路徑依白牌類型分類」

### v1.2.6 (2026-04-23)
- ✨ **`files` 動態配置陣列** - 設定檔改用 `files[]` 陣列動態定義輸出檔案，取代硬編碼的模板路徑表
  - 支援 `isNew`（新建 / 插入現有檔案）、`location`、`template`、`environments`、`marker`、`insertAfter`、`imports` 欄位
  - `environments` 陣列可一次展開多環境（DEV / UAT / SIM），`{$env}` 自動替換
- 🔧 **重構 WhiteLabelTool** - 移除 `TEMPLATE_PATHS` 靜態常數與固定處理流程，改由 `processDynamicFiles()` 根據 `files` 設定動態驅動
- 📚 **更新 README** - 補充 `files` 陣列格式說明與欄位表格

### v1.2.4 (2026-02-24)
- 🐛 **修正 `enhanced-search.json` 模板 typo** - JQL `{$issueList}}` 多餘的 `}` 導致 Jira API 語法錯誤
- ✨ **`enhanced-search` 新增 `<issueList>` 必填參數** - 可直接透過命令列傳入 issue key，自動替換 template 中的 `{$issueList}` 佔位符
- 🔧 **修正 `enhanced-search` 預設 template 路徑** - 從 `enhanced-search.json` 改為 `./template/jira/enhanced-search.json`，與其他指令路徑規範一致
- 📚 **更新使用說明** - README 補充 Template 外部目錄結構說明，並修正 `application.properties` 屬性名稱

### v1.2.1 (2025-12-04)
- ✨ **新增 Jira Tool (工具 C)** - 完整的 Jira API 整合工具
  - 支援 issue 查詢、留言管理、狀態轉換、JQL 搜尋
  - `start-jira-issue` 命令啟動完整開發流程
  - Transition ID 自動驗證機制
  - 測試模式支援（`-t` / `--testMode`）
- 🌍 **所有輸出訊息英文化**
  - JiraTool: 所有命令列輸出改為英文
  - WhiteLabelTool: 所有 System.out.println 輸出改為英文
  - 保留 emoji 和中文註解
- 💾 **檔案輸出優化**
  - `start-jira-issue` Step 3 將 issue 資訊儲存至 `./result/jira/{issueKey}-jira.txt`
  - 自動建立輸出目錄
- 🔧 **程式碼優化**
  - 重構 `startJiraIssue` 使用統一的 `handleTransitionIssue` 方法
  - 移除重複代碼，提升可維護性

### v1.1.0 (2025-11-17)
- ✨ **新增動態字段支持** - 允許在 JSON 配置中添加自定義字段，自動映射為占位符
- ⚡ **性能優化** - buildReplacements() 緩存機制，減少 56-64% 重複計算，性能提升 60-70%
- 🔧 **重構占位符映射引擎** - 使用反射驅動的 PlaceholderMapper，支持自動映射和派生映射
- 📚 **新增文檔**：
  - `DYNAMIC_FIELDS_GUIDE.md` - 動態字段使用指南
  - `CACHE_OPTIMIZATION_REPORT.md` - 緩存優化報告
  - `PHASE1_COMPLETION_REPORT.md` - 第一階段完成報告
  - `PHASE2_PLAN.md` - 第二階段功能規劃
- 🎯 **核心改進**：
  - 無需修改 Java 代碼即可添加新占位符
  - 兩層緩存機制（基礎映射 + 環境映射）
  - Builder API 支持鏈式調用
  - 完整的單元測試覆蓋

### v1.0.7 (2025-01-XX)
- ✨ 新增多環境 SQL 生成架構（DEV/UAT/SIM）
- ✨ 重構 SQL 生成方式，統一使用模板替換
- ✨ 新增 `EnvEnumType` 枚舉支援環境配置
- ✨ 新增環境專屬子域名配置（CORS、前後端分離）
- 🔧 `generateNewGroupSql` 從布林參數改為環境枚舉參數
- 🔧 `generateUpdateGroupSql` 完全模板化
- 📦 輸出檔案命名格式變更：`SACRIC-{ticketNo}-{ENV}-DB-{01|41}.sql`
