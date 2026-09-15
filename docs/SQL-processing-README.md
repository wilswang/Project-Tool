# SQL-processing 使用說明

> 支援 Linux/macOS (.sh) 和 Windows (.bat) 兩種版本

## 功能說明

此腳本實現了 CLAUDE.md 工作流程中的**步驟11（處理SQL）**和**步驟12（開發環境SQL執行）**。

### 步驟11: 處理SQL
- 從 `ProjectTool/result/` 讀取生成的 SQL 檔案
- 自動創建 `src/release_sql/[fixVersion]/` 目錄
- 將 SQL 內容追加到對應的 MyDB01.sql 和 MyDB41.sql
- **追加前會檢查該 ticket 的區塊是否已存在**（以區塊標題 `-- {PROJECT}-{TICKET_NO} ` 為標記），避免重跑產生重複區塊：

  | 情況 | 行為 |
  |------|------|
  | 區塊不存在 | 照常追加 |
  | 區塊存在且內容相同 | 顯示警告後跳過，不視為失敗 |
  | 區塊存在但內容不同 | 報錯並中止，需人工確認（常見原因：site 編號或欄位被修改過） |

### 步驟12: 開發環境SQL執行（可選）
- 檢查 `sqlOnly` 參數，如果為 true 則跳過此步驟
- 執行前進行重複檢查，避免重複執行
- 需要用戶確認後才執行
- 執行 SQL 到開發環境資料庫
- 驗證數據是否正確插入

## 上游保證（Project-Tool JAR v1.4.0+）

本腳本會把 SIM 檔 `append` 進 `src/release_sql/{fixVersion}/`，並把 DEV 檔灌進開發資料庫，
所以它拿到的 `.sql` 必須是完整填值的。

v1.4.0 起，Project-Tool 在寫檔之前會掃描殘留的 `{$...}` placeholder：有殘留就不寫該檔，
並讓整批 exit 1。`white-label-process.sh` step 3 以 `if [ $? -eq 0 ]` 把關，因此本腳本
**不可能**再拿到含未解析 placeholder 的 `.sql`。

舊版（≤ v1.3.2）會把字面 `{$typo}` 寫進 `.sql` 且照樣印 ✅ 並 exit 0 —— 那份壞檔會一路
append 進 release SQL。若在舊產出的檔案裡看到 `{$`，那是這個既有缺陷的殘留，不要執行它。

---

## 前置需求

### Linux/macOS 環境

#### 必要工具
1. **jq** - JSON 解析工具
   ```bash
   # macOS
   brew install jq

   # Ubuntu/Debian
   sudo apt-get install jq

   # CentOS/RHEL
   sudo yum install jq
   ```

2. **mysql** - MySQL 客戶端（僅步驟12需要）
   ```bash
   # macOS
   brew install mysql-client

   # Ubuntu/Debian
   sudo apt-get install mysql-client
   ```

### Windows 環境

#### 必要工具
1. **jq for Windows** - JSON 解析工具
   - 下載頁面: https://stedolan.github.io/jq/download/
   - 下載 `jq-win64.exe`（或適合您系統的版本）
   - 重命名為 `jq.exe`
   - 將 `jq.exe` 複製到以下任一位置：
     - `C:\Windows\System32` (推薦)
     - 或加入到 PATH 環境變數的任何目錄中

   **驗證安裝**：
   ```cmd
   jq --version
   ```

2. **MySQL Client for Windows**（僅步驟12需要）

   **方法一：使用 MySQL Installer**
   - 下載: https://dev.mysql.com/downloads/mysql/
   - 執行安裝程式，選擇「MySQL Command Line Client」
   - 安裝後 `mysql.exe` 通常位於：`C:\Program Files\MySQL\MySQL Server X.X\bin\`
   - 將此路徑加入 PATH 環境變數

   **方法二：使用 Chocolatey**（推薦）
   ```cmd
   choco install mysql-cli
   ```

   **驗證安裝**：
   ```cmd
   mysql --version
   ```

#### 如何設定 PATH 環境變數（Windows）

1. 開啟「系統內容」→「環境變數」
2. 在「系統變數」中找到 `Path`
3. 點擊「編輯」→「新增」
4. 加入 MySQL bin 目錄路徑（例如：`C:\Program Files\MySQL\MySQL Server 8.0\bin`）
5. 點擊「確定」儲存
6. 重新開啟命令提示字元（CMD）以套用變更

## 使用方法

### Linux/macOS 環境

#### 基本用法
```bash
cd ProjectTool
./SQL-processing.sh <json-file-name>
```

#### 示例
```bash
# 處理 SACRIC-978 的 SQL
./SQL-processing.sh SACRIC-978.json

# 處理 SACRIC-979 的 SQL
./SQL-processing.sh SACRIC-979.json
```

### Windows 環境

#### 基本用法
```cmd
cd ProjectTool
SQL-processing.bat <json-file-name>
```

或使用完整路徑：
```cmd
C:\path\to\ProjectTool\SQL-processing.bat SACRIC-978.json
```

#### 示例
```cmd
REM 處理 SACRIC-978 的 SQL
SQL-processing.bat SACRIC-978.json

REM 處理 SACRIC-979 的 SQL
SQL-processing.bat SACRIC-979.json
```

#### Windows 使用注意事項

1. **命令提示字元（CMD）vs PowerShell**
   - 推薦使用「命令提示字元」（CMD）執行 .bat 腳本
   - 在 PowerShell 中執行需要加 `.\` 前綴：
     ```powershell
     .\SQL-processing.bat SACRIC-978.json
     ```

2. **路徑格式**
   - Windows 使用反斜線 `\` 作為路徑分隔符
   - 腳本會自動處理路徑轉換

3. **權限要求**
   - 一般使用者權限即可執行
   - 不需要系統管理員權限

## 執行流程

### 1. 腳本啟動
```
=== SQL處理腳本開始執行 ===
JSON檔案: /path/to/ProjectTool/sample/SACRIC-978.json

票號: SACRIC-978
Fix Version: release-4.9.0
Site ID: 439
僅SQL: false
```

### 2. 步驟11執行
```
=== 步驟11: 處理SQL ===
確保目錄存在: /path/to/src/release_sql/release-4.9.0
追加SQL到: /path/to/src/release_sql/release-4.9.0/MyDB01.sql
追加SQL到: /path/to/src/release_sql/release-4.9.0/MyDB41.sql
✅ Step 1 Complete: SQL appended to release-4.9.0 (appended 2, skipped 0)
```

重跑同一張單時（區塊已存在且內容相同）：
```
=== 步驟11: 處理SQL ===
確保目錄存在: /path/to/src/release_sql/release-4.9.0
⚠️  SACRIC-978 already in MyDB01.sql, skipping append
⚠️  SACRIC-978 already in MyDB41.sql, skipping append
✅ Step 1 Complete: 區塊已存在於 release-4.9.0，未追加任何內容 (skipped 2)
```

區塊已存在但內容不同時（中止，離開碼 1）：
```
❌ SACRIC-978 already in MyDB01.sql but content differs
   release SQL 內既有的區塊與本次產出不一致
   常見原因: site 編號或欄位被修改過
   請人工確認並移除或更新既有區塊後再執行
```

### 3. 步驟12執行（可選）
如果 `sqlOnly=false`，會繼續執行步驟12：

```
=== 步驟12: 開發環境SQL執行 ===
檢查MySQL連接...
✅ MySQL連接正常

檢查SQL是否已執行過...
✅ 無重複記錄

是否要將SQL執行到開發環境資料庫？ (y/n): y

開始執行SQL...

執行DB01 SQL (10.100.56.131:3101)...
✅ DB01執行成功

執行DB41 SQL (10.100.56.131:3141)...
✅ DB41執行成功並提交事務

驗證數據插入...
✅ DB01數據驗證通過 (site=439)
✅ DB41數據驗證通過 (site=439)

=== SQL處理完成 ===
```

### 4. 執行失敗時的處理
如果 SQL 執行失敗：

```
執行DB01 SQL (10.100.56.131:3101)...
❌ DB01執行失敗，事務已自動回滾

錯誤詳情：
ERROR 1062 (23000): Duplicate entry '439' for key 'PRIMARY'

DB01執行失敗，是否繼續執行後續步驟？ (y/n): n
流程終止
```

選擇繼續執行：
```
DB01執行失敗，是否繼續執行後續步驟？ (y/n): y
警告: 跳過DB01，繼續執行DB41
```

## 參數說明

腳本從 JSON 檔案讀取以下參數：

| 參數 | 說明 | 示例 |
|------|------|------|
| `ticketNo` | JIRA 票號 | "978" |
| `fixVersion` | 版本號 | "release-4.9.0" |
| `webSiteValue` | Site ID | 439 |
| `sqlOnly` | 是否僅處理SQL（不執行到開發環境） | false |

## 安全特性

1. **事務控制與自動 ROLLBACK**
   - 使用 MySQL 事務確保數據一致性
   - 執行流程：`SET autocommit=0` → `START TRANSACTION` → 執行SQL → `COMMIT`
   - 如果執行失敗，事務自動回滾（ROLLBACK）
   - 失敗的 SQL 不會影響資料庫，保證原子性操作

2. **重複執行檢查**
   - 執行前檢查 Site ID 是否已存在
   - 如果已存在，會提示用戶確認是否重複執行

3. **用戶確認**
   - 執行到開發環境前需要用戶明確確認
   - SQL執行失敗時會詢問是否繼續後續步驟
   - 可以在任何確認環節取消執行

4. **錯誤處理與日誌**
   - 執行失敗時顯示錯誤詳情（前5條錯誤訊息）
   - 使用臨時日誌文件記錄完整輸出
   - DB01 失敗可選擇是否繼續 DB41
   - 執行後會驗證數據是否正確插入

5. **連接檢查**
   - 執行前檢查 MySQL 連接是否正常
   - 如果連接失敗，會跳過步驟12

## 錯誤處理

### 常見錯誤及解決方法

1. **jq 工具未安裝**
   ```
   錯誤: 需要安裝 jq 工具來解析JSON
   ```
   解決方法：安裝 jq 工具（見前置需求）

2. **JSON 檔案不存在**
   ```
   錯誤: JSON檔案不存在: /path/to/file.json
   ```
   解決方法：確認檔案名稱正確，檔案位於 `ProjectTool/sample/` 目錄

3. **來源SQL檔案不存在**
   ```
   錯誤: 來源SQL檔案不存在: ProjectTool/result/SACRIC-XXX-DB-01.sql
   ```
   解決方法：先執行 project-tool.sh 生成 SQL 檔案

4. **MySQL連接失敗**
   ```
   警告: 無法連接到開發環境資料庫，跳過SQL執行
   ```
   解決方法：
   - 檢查網絡連接
   - 確認 VPN 是否連接
   - 確認資料庫連接參數是否正確

## 資料庫連接設定

腳本預設連接參數：

```bash
DB_HOST="10.100.56.131"
DB_USER="cricket"
DB_PASS="cricket123"
DB_NAME="cricketdb"
DB01_PORT="3101"
DB41_PORT="3141"
```

如需修改，請編輯腳本中的對應變數。

## 整合到工作流程

### Linux/macOS 環境

```bash
# 1. 執行 project-tool.sh 生成代碼和 SQL
./project-tool.sh A sample/SACRIC-978.json

# 2. 執行編譯檢查
mvn test-compile

# 3. 執行 SQL 處理（步驟11和步驟12）
./SQL-processing.sh SACRIC-978.json

# 4. 提交變更
git add src/ ProjectTool/sample/
git commit -m "[SACRIC-978] ..."
```

### Windows 環境

```cmd
REM 1. 執行 project-tool 生成代碼和 SQL
project-tool.bat A sample\SACRIC-978.json

REM 2. 執行編譯檢查
mvn test-compile

REM 3. 執行 SQL 處理（步驟11和步驟12）
SQL-processing.bat SACRIC-978.json

REM 4. 提交變更
git add src/ ProjectTool/sample/
git commit -m "[SACRIC-978] ..."
```

### 跨平台自動化建議

如需在團隊中同時支援 Windows 和 Linux/macOS，建議：

1. **版本控制**
   - 同時維護 `.sh` 和 `.bat` 兩個版本
   - 保持功能同步更新

2. **文檔統一**
   - 使用此 README 作為統一文檔
   - 明確標示各平台差異

3. **測試覆蓋**
   - 在兩個平台上都進行功能測試
   - 確保行為一致性

## 日誌輸出

### Linux/macOS 版本
腳本使用 ANSI 顏色碼標記不同類型的訊息：
- 🔵 藍色：步驟標題
- 🟢 綠色：成功訊息（✅）
- 🟡 黃色：警告訊息（⚠️）
- 🔴 紅色：錯誤訊息（❌）

### Windows 版本
由於 Windows CMD 對 ANSI 顏色支援有限，Windows 版本使用純文字輸出：
- 步驟標題：`=== 步驟標題 ===`
- 成功訊息：`✅ 訊息內容`
- 警告訊息：`⚠️ 訊息內容`
- 錯誤訊息：`❌ 訊息內容`

> **注意**: 功能完全相同，僅視覺呈現略有不同

## 注意事項

### 通用注意事項（所有平台）

1. **只在開發環境使用**
   - 此腳本連接的是開發環境資料庫
   - 不要用於生產環境

2. **執行前先備份**
   - 建議在執行前備份資料庫
   - 或在測試環境先驗證

3. **檢查 SQL 內容**
   - 執行前建議先檢查生成的 SQL 內容
   - 確認無誤後再執行

4. **Git 提交**
   - 執行後記得提交 src/release_sql/ 下的變更
   - 包含在 git commit 中

### Windows 特定注意事項

1. **路徑格式**
   - JSON 檔案參數使用檔名即可，無需包含路徑
   - 範例：`SQL-processing.bat SACRIC-978.json`（正確）
   - 避免：`SQL-processing.bat sample\SACRIC-978.json`（會查找錯誤路徑）

2. **字元編碼**
   - 如果中文顯示為亂碼，執行 `chcp 65001` 切換到 UTF-8
   - 或執行 `chcp 950` 使用繁體中文編碼

3. **防毒軟體**
   - 某些防毒軟體可能阻止 .bat 腳本執行
   - 如遇到問題，請暫時停用防毒軟體或將腳本加入白名單

4. **網路連線**
   - 確認已連接 VPN（如需要）
   - 確認可以 ping 到資料庫主機 `10.100.56.131`

## Windows 版本

### 已發布 ✅

Windows 版本 (`SQL-processing.bat`) 已完成開發並測試，提供與 Linux/macOS 版本相同的完整功能。

### 功能對比

| 功能特性 | Linux/macOS (.sh) | Windows (.bat) |
|---------|------------------|----------------|
| **步驟11**: SQL 追加到 release_sql | ✅ | ✅ |
| **步驟12**: 開發環境 SQL 執行 | ✅ | ✅ |
| JSON 參數解析 (jq) | ✅ | ✅ |
| MySQL 連接檢查 | ✅ | ✅ |
| 重複執行檢查 | ✅ | ✅ |
| 重複追加檢查（步驟11 區塊防呆） | ✅ | ⚠️ 已實作，尚未在 Windows 實機驗證 |
| 事務控制與自動 ROLLBACK | ✅ | ✅ |
| 錯誤處理與互動式確認 | ✅ | ✅ |
| 數據驗證 | ✅ | ✅ |
| 顏色輸出 | ✅ | ⚠️ 僅文字 |

### 快速開始 (Windows)

1. **安裝前置工具**
   ```cmd
   REM 下載並安裝 jq.exe 到 PATH
   REM 下載並安裝 MySQL Client

   REM 驗證安裝
   jq --version
   mysql --version
   ```

2. **執行腳本**
   ```cmd
   cd C:\path\to\citixchange_work\ProjectTool
   SQL-processing.bat SACRIC-978.json
   ```

3. **執行流程**
   - 自動解析 JSON 參數
   - 步驟11: 追加 SQL 到 `src/release_sql/[version]/`
   - 步驟12: 執行到開發環境（如果 `sqlOnly=false`）
   - 驗證數據插入成功

### 已知差異

1. **顏色輸出**
   - Linux/macOS: 支援 ANSI 顏色碼（藍、綠、黃、紅）
   - Windows: 僅顯示純文字（無顏色）
   - 不影響功能，僅視覺呈現不同

2. **臨時文件處理**
   - Linux/macOS: 使用 `mktemp` 創建臨時文件
   - Windows: 使用 `%TEMP%\file_%RANDOM%.sql`
   - 功能相同，路徑不同

3. **路徑分隔符**
   - Linux/macOS: `/`
   - Windows: `\`
   - 腳本自動處理，無需手動轉換

### 語法轉換參考

如需自行修改或維護腳本，以下為主要語法對照：

| 功能 | Bash | Batch |
|-----|------|-------|
| 變量賦值 | `VAR="value"` | `set "VAR=value"` |
| 讀取變量 | `echo $VAR` | `echo %VAR%` |
| 執行命令並捕獲輸出 | `VAR=$(cmd)` | `for /f %%i in ('cmd') do set VAR=%%i` |
| 條件判斷 | `if [ condition ]; then` | `if condition (` |
| 檢查文件存在 | `if [ -f file ]; then` | `if exist file (` |
| 追加文件內容 | `cat file >> target` | `type file >> target` |
| 空設備 | `/dev/null` | `nul` |

### 錯誤排除 (Windows)

**問題：找不到 jq 命令**
```
'jq' 不是內部或外部命令，也不是可執行的程式或批次檔。
```
**解決方法**：
- 確認 jq.exe 已下載並重命名
- 確認 jq.exe 位於 PATH 環境變數的目錄中
- 重新開啟 CMD 以載入新的 PATH 設定

**問題：找不到 mysql 命令**
```
'mysql' 不是內部或外部命令
```
**解決方法**：
- 確認 MySQL Client 已安裝
- 將 MySQL bin 目錄加入 PATH 環境變數
- 範例路徑：`C:\Program Files\MySQL\MySQL Server 8.0\bin`

**問題：權限不足**
```
拒絕存取
```
**解決方法**：
- 確認對 ProjectTool 目錄有讀寫權限
- 確認對 src/release_sql 目錄有寫入權限

## 維護者

此腳本基於 ProjectTool/CLAUDE.md 工作流程步驟11和步驟12實現。

如有問題或建議，請聯繫開發團隊。
