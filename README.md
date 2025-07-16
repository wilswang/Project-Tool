# Project Tool 工具說明

這是一個多功能 Java 工具集，包含兩個主要功能：
- **工具 A (White Label Generator)**: 根據 `whiteLabel.json` 的輸入資料，自動產出 SQL 檔案與對應的 Java/JS 程式碼
- **工具 B (Domain Checker)**: 根據 `checkDomain.json` 的設定，批次檢查網域連線狀態

---

## 🔧 工具選擇

使用對應作業系統的執行腳本，並傳入參數選擇要執行的工具：

```batch
# Windows
project-tool.bat A    # 工具 A: White Label Generator
project-tool.bat B    # 工具 B: Domain Checker
```

```bash
# Mac / Linux
./project-tool.sh A   # 工具 A: White Label Generator
./project-tool.sh B   # 工具 B: Domain Checker
```

---

## 📥 工具 A: temp.json 檔案格式 (White Label Generator)

請提供一個 JSON 檔案作為輸入，檔名預設為 `whiteLabel.json`。

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
  }
}
```

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
project-tool.bat A    # 執行 White Label Generator
project-tool.bat B    # 執行 Domain Checker
```

```bash
# Mac / Linux
cd src/main/resources
chmod +x project-tool.sh    # 賦予執行權限（首次執行）
./project-tool.sh A         # 執行 White Label Generator
./project-tool.sh B         # 執行 Domain Checker
```

### 📋 執行前準備

1. **建置專案**: 執行 `mvn package` 產生 JAR 檔案
2. **複製檔案**: 將 `Project-Tool-1.0.4-jar-with-dependencies.jar` 從 `target/` 複製到 `src/main/resources/`
3. **準備設定檔**: 將對應的 JSON 設定檔放置於 `src/main/resources/` 目錄下
   - **工具 A**: `whiteLabel.json` 檔案
   - **工具 B**: `checkDomain.json` 檔案
4. **Mac/Linux**: 賦予腳本執行權限 `chmod +x project-tool.sh`

### 🎯 自動功能

執行腳本提供以下自動化功能：
- 自動檢查 JAR 檔案是否存在，若不存在會提示執行 `mvn package`
- 自動建立 `result` 資料夾存放輸出檔案
- 自動切換到腳本所在目錄執行

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

---

## 🛠 建議

- 若格式變更頻繁，建議改用 JSON Schema 驗證
- 可搭配 Git commit hook 或 CI 工具自動驗證 JSON 檔案合法性
- Domain Checker 可搭配 CI/CD 流程進行網域可用性監控
