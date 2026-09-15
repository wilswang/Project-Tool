# ProjectTool — 白牌開發工具

把 Jira 單轉成白牌所需的 Java 程式碼與 SQL，並代跑 Jira 狀態流轉。

這份說明只講**怎麼裝、怎麼跑**。工具本身的開發、完整的 JSON 欄位規格與版本歷史，
都在原始碼 repo：<https://github.com/wilswang/Project-Tool>

---

## 1. 安裝

把壓縮檔解到 **citixchange 專案的根目錄底下**：

```
citixchange/
├── src/
├── pom.xml
└── ProjectTool/        ← 解壓後長這樣
```

⚠️ **資料夾名稱必須是 `ProjectTool`。** 腳本靠相對位置推算專案根目錄，改名會找不到 `src/`。

`ProjectTool/` 不進 citixchange 的版控（根目錄的 `.gitignore` 已排除），
所以你在裡面的設定與產出都只存在本機。

## 2. 填入自己的憑證

工具需要兩組憑證，**都不在壓縮檔裡**，要自己填。

### 2-1. Jira（必要）

編輯 `ProjectTool/application.properties`：

```properties
jira.account=你的公司信箱
jira.token=你的 Atlassian API token
```

Token 在 <https://id.atlassian.com/manage-profile/security/api-tokens> 申請。

> 這個檔案已被 `ProjectTool/.gitignore` 排除，不會進版控。**請勿把 token 貼到任何會進版控的地方。**

### 2-2. Google 試算表（只有要跑「新群組」白牌才需要）

新群組白牌（`newGroup=true`）的 step 2.5 會去查 API 2.0 網域試算表。
把 service account 金鑰放成 `ProjectTool/config/service-account.json`，
格式參照同目錄的 `service-account.sample.json`。

金鑰由團隊提供；另外要請 BA 把該 service account 的信箱加進試算表的檢視權限。

還要在 `ProjectTool/config/sheet-config.json` 填入試算表 ID（壓縮檔裡刻意留空）：

```json
"spreadsheetId": "<每張白牌 Jira 單「詳細網域配置參考」連結裡的那串 ID>"
```

沒設定的話，只有 `newGroup=true` 的單子會失敗，一般白牌不受影響。

驗證憑證有沒有設好：

```bash
cd ProjectTool
java -cp Project-Tool.jar tool.sheet.SheetTool check-auth
```

## 3. 環境需求

`jq`、`git`、`mvn`、`java`、`claude` 都要在 PATH 上。腳本啟動時會檢查，缺了會直接中止。

編譯那一步需要 **Java 8**；macOS 用 `brew install --cask zulu@8`。

## 4. 跑一張白牌單

```bash
# 從專案根目錄執行
./ProjectTool/script/unix/white-label-process.sh SACRIC-1234
```

Windows：`ProjectTool\script\windows\white-label-process.ps1 SACRIC-1234`

### 常用旗標

| 旗標 | 作用 |
|---|---|
| `-t` | 測試模式：跳過 step 5-8，不碰 DEV DB、不 commit、不動 Jira |
| `-c` | 客製模式：跳過 step 5-8（與 `-t` 的差別是仍以正式模式呼叫底層工具） |
| `-s N` | 從第 N 步開始重跑 |
| `--quiet` | 少印一點 |

**第一次用建議先加 `-t -c` 試跑**，確認環境沒問題再跑正式的。

### 流程做了什麼

| 步驟 | 內容 |
|---|---|
| 1 | 抓 Jira 單 |
| 2 | 轉成單子 JSON（呼叫 `claude` CLI） |
| 2.5 | `newGroup=true` 時，從試算表填入 `groupInfo` |
| 3 | 產出 Java 與 SQL |
| 4 | Java 8 編譯檢查 |
| 5 | 寫入 `src/release_sql/` 並執行 DEV SQL |
| 6 | git commit |
| 7-8 | 回 Jira 留言、轉成 DEV DONE |

## 5. 站台編號 (webSiteValue)

**流程會自己處理，你不用管。** step 2 每次都從 `WebSiteType.java` 推導出下一個可用的編號，
再寫回 `task/white-label-mapping-rule.md` —— 所以剛解壓縮時那個檔案裡是幾號並不重要。

想自己先查一下，或想確認有沒有重複，跑 citixchange 的測試：

```bash
mvn -Dmaven.test.skip=false -Dtest=TestWebSiteTypeRegistry -DfailIfNoSpecifiedTests=false test
```

輸出會列出目前最大值與下一個可用的編號，並在編號或站台名稱重複時失敗。
同目錄還有 `TestApiActionCertRegistry` 檢查 cert 是否跨站台重複。

> ⚠️ 這兩支測試不會在一般 build 跑到（`pom.xml` 設了 `maven.test.skip=true`），
> 必須像上面那樣手動覆寫。

## 6. 出錯了怎麼辦

1. 看 `ProjectTool/log/{單號}.txt`，以及主控台裡 `❌ Step N` 那幾行。
2. 錯誤訊息會直接說下一步該做什麼，照著做。
3. 修好之後用 `-s N` 從失敗那步重跑，不要從頭跑。

⚠️ **唯一需要特別小心的是 step 3 報「重複的 webSiteValue 或 cert」**：
那代表錯誤的內容已經寫進 `src/` 了。**先 `git checkout src/` 還原**，
改掉單子 JSON 裡的值，再 `-s 3`。直接重跑會插入第二份。

其餘失敗（step 1、2、2.5，以及 step 3 的 placeholder 錯誤）都是**什麼都沒寫出去**，
修好直接重跑是安全的。

## 7. 更新工具

拿到新的壓縮檔時，直接覆蓋解壓即可。以下是你自己的東西，**覆蓋前先備份**：

- `application.properties`（你的 Jira 帳密）
- `config/service-account.json`（金鑰）
- `config/sheet-config.json` 的 `spreadsheetId`（如果你填過）

`sample/`、`log/`、`result/` 是產出目錄，不在壓縮檔裡，不會被覆蓋。

目前版本見同目錄的 `VERSION`。
