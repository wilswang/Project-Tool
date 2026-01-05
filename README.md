# Project Tool 工具說明

這是一個多功能 Java 工具集，包含三個主要功能：
- **工具 A (White Label Generator)**: 根據 `whiteLabelConfig.json` 的輸入資料，自動產出多環境 SQL 檔案與對應的 Java/JS 程式碼
- **工具 B (Domain Checker)**: 根據 `checkDomain.json` 的設定，批次檢查網域連線狀態
- **工具 C (Jira Tool)**: Jira API 整合工具，支援 issue 查詢、留言、狀態轉換等操作

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
# 工具 C 使用 java -jar 直接執行，詳見下方說明
```

```bash
# Mac / Linux
./project-tool.sh A <configFilePath>  # 工具 A: White Label Generator
./project-tool.sh B                   # 工具 B: Domain Checker
# 工具 C 使用 java -jar 直接執行，詳見下方說明
```

---

## 📥 工具 A: JSON 檔案格式 (White Label Generator)

請提供一個 JSON 檔案作為輸入，可指定任意檔案路徑與名稱。

### ✅ JSON 格式說明（WhiteLabel 結構）

```json
{
  "sqlOnly": false,
  "ticketNo": "SACRIC-12345",
  "webSiteName": "ABC_SITE",
  "webSiteValue": 101,
  "host": "abc.com",
  "apiWhiteLabel": true,
  "apiWalletInfo": {
    "cert": "ABC123-CERT",
    "newGroup": true,
    "group": "A48",
    "groupInfo": {
      "privateIpSetId": "ipset-private-1",
      "privateIp": ["192.168.0.1"],
      "bkIpSetId": ["bk1-id", "bk2-id"],
      "apiInfoBkIpSetId": "api-info-id",
      "backup": ["abc-api1.com", "abc-api2.com"]
    }
  },

  "siteCategory": "Sports",
  "region": "Asia",
  "launchDate": "2025-12-01"
}
```

### 🆕 動態字段支持（v1.1.0+）

從 v1.1.0 開始，配置文件支持添加**自定義字段**，這些字段會自動轉換為可用的占位符：

**示例**：
```json
{
  "ticketNo": "SACRIC-12345",
  "webSiteName": "ABC_SITE",
  "webSiteValue": 101,

  "siteCategory": "Sports",
  "region": "Asia",
  "maxUsers": 100000
}
```

**自動生成的占位符**：
- `{$siteCategory}` = "Sports"
- `{$region}` = "Asia"
- `{$maxUsers}` = "100000"

這些占位符可以直接在模板文件中使用，**無需修改 Java 代碼**。

詳細使用說明請參考：[DYNAMIC_FIELDS_GUIDE.md](DYNAMIC_FIELDS_GUIDE.md)

---

## 🌍 多環境 SQL 生成架構

工具 A 現已支援**多環境架構**，每次執行會自動為以下環境生成獨立的 SQL 檔案：

| 環境 | 代號 | 靜態資源子域名 | API 子域名 | 說明 |
|------|------|---------------|-----------|------|
| 開發環境 | DEV | `devnginx` | `dev9wapi` | 開發測試用 |
| 測試環境 | UAT | `tberwxsjyk` | `uat9wapi` | User Acceptance Testing |
| 正式環境 | SIM | `www` | `saapipl` | 生產環境 |

### 📦 輸出檔案格式

執行後會在 `result/` 目錄下產生以下檔案：

```
result/
├── SACRIC-{ticketNo}-DEV-DB-01.sql    # DEV 環境 - DB-01
├── SACRIC-{ticketNo}-DEV-DB-41.sql    # DEV 環境 - DB-41
├── SACRIC-{ticketNo}-UAT-DB-01.sql    # UAT 環境 - DB-01
├── SACRIC-{ticketNo}-UAT-DB-41.sql    # UAT 環境 - DB-41
├── SACRIC-{ticketNo}-SIM-DB-01.sql    # SIM 環境 - DB-01
└── SACRIC-{ticketNo}-SIM-DB-41.sql    # SIM 環境 - DB-41
```

### 🔧 模板架構

SQL 生成採用**模板化設計**，支援以下功能：
- ✅ 環境隔離：每個環境有獨立的模板檔案
- ✅ 統一管理：SQL 結構集中在 `src/template/` 目錄
- ✅ 易於擴展：新增環境只需添加對應的模板檔案與枚舉值
- ✅ 參數替換：透過 `{$placeholder}` 進行動態內容替換

### 📄 相關模板檔案

| 模板檔案 | 用途 |
|---------|------|
| `UpdateGroup-SQL-template.txt` | 更新現有 API Group |
| `NewGroup-SQL-DEV-template.txt` | 新建 API Group (DEV) |
| `NewGroup-SQL-UAT-template.txt` | 新建 API Group (UAT) |
| `NewGroup-SQL-SIM-template.txt` | 新建 API Group (SIM) |

---

## 🧾 欄位說明

| 欄位                           | 類型 | 是否必填 | 說明                                       |
|------------------------------|------|----------|------------------------------------------|
| `sqlOnly`                    | boolean | 否 | 若為 true，僅產出 SQL，不處理程式碼插入                 |
| `ticketNo`                   | string | ✅ 是 | 專案或需求的代號，如 `SACRIC-12345`                |
| `webSiteName`                | string | ✅ 是 | 網站名稱，會轉為 enum 名稱、JS 變數名等                 |
| `webSiteValue`               | int | ✅ 是 | 對應的整數代碼，寫入 enum value 與資料庫               |
| `host`                       | string | ✅ 是 | 一般白牌host，若`apiWhiteLabel = false`, 則不可為空 |
| `apiWhiteLabel`              | boolean | ✅ 是 | 若為 true，需提供 `apiWalletInfo` 區塊           |
| `apiWalletInfo.cert`         | string | ✅ 是 | API 驗證憑證代號                               |
| `apiWalletInfo.group`        | string | ✅ 是 | 群組名稱，如 `A48`                             |
| `apiWalletInfo.newGroup`     | boolean | ✅ 是 | 是否建立新群組, 若為 true，需提供 `groupInfo` 區塊      |
| `groupInfo.privateIpSetId`   | string | ✅ 是 | 私有 IP 的設定 ID                             |
| `groupInfo.privateIp`        | string[] | ✅ 是 | 私有 IP 清單                                 |
| `groupInfo.bkIpSetId`        | string[] | ✅ 是 | 備援 IP 設定 ID，需有兩筆                         |
| `groupInfo.apiInfoBkIpSetId` | string | ✅ 是 | API 設定 ID                                |
| `groupInfo.backup`           | string[] | ✅ 是 | 備援 domain 名稱                             |

### 🆕 動態自定義字段（v1.1.0+）

除了上述標準字段外，您可以添加任意自定義字段：

| 特性 | 說明 |
|------|------|
| **字段名稱** | 支援任意合法的 JSON 鍵名（建議使用駝峰命名） |
| **字段類型** | 支援字符串、數字、布爾值（會自動轉為字符串） |
| **占位符格式** | `{$字段名}` |
| **使用場景** | 臨時字段、A/B 測試、實驗性功能 |
| **限制** | 不支持嵌套對象（建議使用扁平結構） |

**示例**：
```json
{
  "customField1": "value1",
  "siteType": "Premium",
  "maxConnections": 1000
}
```

會自動生成占位符：`{$customField1}`, `{$siteType}`, `{$maxConnections}`

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
- 支援相對路徑和絕對路徑
- 檔案格式必須為有效的 JSON
- 檔案內容需符合 WhiteLabel 結構驗證要求

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
2. **複製檔案**: 將 `Project-Tool-1.0.4-jar-with-dependencies.jar` 從 `target/` 複製到 `src/main/resources/`
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
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool <command> [arguments] [options]

# 顯示說明
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool --help
```

### 🔧 可用命令

#### 1. get-issue - 取得 Issue 資訊
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-issue <issueKey>

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-issue SACRIC-1020
```

#### 2. get-comments - 取得 Issue 留言
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-comments <issueKey>

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-comments SACRIC-1020
```

#### 3. get-transitions - 取得可用的狀態轉換選項
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-transitions <issueKey>

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool get-transitions SACRIC-1020
```

#### 4. post-comment - 新增留言
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool post-comment <issueKey> <commentText> [options]

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool post-comment SACRIC-1020 "This is a comment"

# 測試模式（不實際發送）
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool post-comment SACRIC-1020 "Test comment" -t
```

#### 5. transition-issue - 轉換 Issue 狀態
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool transition-issue <issueKey> <transitionKey> [options]

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool transition-issue SACRIC-1020 TO_DEV

# 測試模式
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool transition-issue SACRIC-1020 TO_DEV --testMode
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
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool start-jira-issue <issueKey> [options]

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool start-jira-issue SACRIC-1020

# 測試模式（跳過狀態檢查和轉換）
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool start-jira-issue SACRIC-1020 -t
```

**執行步驟**:
1. **Step 1**: 檢查 issue 狀態是否為 "Ready to DEV"
2. **Step 2**: 將狀態轉換為 "IN DEV"（包含 transition ID 驗證）
3. **Step 3**: 取得 issue 詳細資訊並儲存至 `./result/jira/{issueKey}-jira.txt`

#### 7. enhanced-search - JQL 進階搜尋
```bash
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool enhanced-search [templatePath]

# 範例
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool enhanced-search
java -cp target/Project-Tool-1.2.3-jar-with-dependencies.jar tool.http.JiraTool enhanced-search my-search.json
```

### ⚙️ 配置檔案

JiraTool 需要 `application.properties` 配置檔案，包含以下設定：

```properties
jira.base.url=https://your-domain.atlassian.net
jira.auth.email=your-email@example.com
jira.auth.token=your-api-token
```

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
  2. 在 `src/template/` 創建對應的 `NewGroup-SQL-{ENV}-template.txt`
  3. 在 `WhiteLabelTool.java` 的 `TEMPLATE_PATHS` 註冊新模板
- SQL 結構變更建議在模板檔案中統一修改，無需修改 Java 代碼

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
- **配置管理**: 使用 `application.properties` 集中管理 Jira 連線資訊
- **測試模式**: 開發和測試時使用 `-t` 參數避免實際修改 Jira 資料
- **檔案輸出**: `start-jira-issue` 會將 issue 資訊儲存至 `./result/jira/` 目錄，便於後續處理
- **API 整合**: 可作為 CI/CD 流程的一部分，自動化 issue 管理
- **擴展性**: 新增 transition 狀態時只需修改 `JiraTransitionId` enum

---

## 📝 版本歷史

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
