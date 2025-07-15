# White Label Inserter 工具說明

這是一個 Java 工具，可根據 `temp.json` 的輸入資料，自動產出 SQL 檔案與對應的 Java/JS 程式碼，並插入到指定的原始檔案中。

---

## 📥 temp.json 檔案格式

請提供一個 JSON 檔案作為輸入，檔名預設為 `temp.json`。

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

## ▶️ 執行方式

### ✅ Mac / Linux

```bash
chmod +x run-tool.sh
./run-tool.sh
```

### ✅ Windows

```bat
run-tool.bat
```

---

## 🔍 驗證與容錯

- 使用 `@Valid` 驗證 JSON 結構與欄位
- 支援條件邏輯驗證：如 `apiWhiteLabel = true` 時，`apiWalletInfo` 不可為空, `newGroup = true` 時，`groupInfo` 不可為空 


---

## 🛠 建議

- 若格式變更頻繁，建議改用 JSON Schema 驗證
- 可搭配 Git commit hook 或 CI 工具自動驗證 temp.json 合法性
