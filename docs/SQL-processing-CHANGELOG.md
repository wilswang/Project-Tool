# SQL-processing 更新日誌

## 上游變更 - 2026-09-08(Project-Tool JAR v1.4.0)

本腳本本身未改動,但上游保證變強了:Project-Tool 現在會在寫檔前擋下含未解析 `{$...}`
placeholder 的 SQL,並 exit 1,使 `white-label-process.sh` step 3 直接失敗。
因此本腳本不會再拿到「看起來完整但含字面 placeholder」的 `.sql`。詳見 README 的「上游保證」段。


## v1.5 - 2026-07-30

### 🛡 新增:步驟11 重複追加防呆

**問題**:步驟11 原本無條件把產出的 SIM SQL `append` 進 `MyDB01.sql` / `MyDB41.sql`,沒有任何檢查。只要重跑(`white-label-process.sh -s 5`、或單獨執行本腳本),release 檔就會多出一份同 ticket 的區塊。

**改動**:追加前以區塊標題 `-- {PROJECT}-{TICKET_NO} ` 為標記做判斷。

| 情況 | 行為 | 離開碼 |
|------|------|--------|
| 區塊不存在 | 照常追加 | 0 |
| 區塊存在且內容相同 | 顯示警告後跳過 | 0 |
| 區塊存在但內容不同 | 報錯中止 | 1 |

第三種情況特別重要:若只做「存在就跳過」,當既有區塊是舊的(例如 site 編號被改過),會被靜默略過而讓人誤以為已更新。

**比對方式**:取出既有區塊(從標題行到下一個 ticket 標題或檔尾),去除尾端空白行後與來源檔比對。`.sh` 用 `awk` + `diff`;`.bat` 因批次語法無法做多行區塊比對,改以內嵌 PowerShell 完成同樣邏輯。

**影響檔案**:`script/unix/SQL-processing.sh`、`script/windows/SQL-processing.bat`

**驗證**:`.sh` 三種情況皆已實測通過。`.bat` 尚未在 Windows 實機驗證。

### 🔧 步驟11 完成訊息改為反映實際結果

原本無論有沒有追加,結尾都固定印 `✅ Step 1 Complete: SQL appended to ...`,全部跳過時會誤導。改為依實際結果顯示:

```
全部追加: ✅ Step 1 Complete: SQL appended to release-4.42.0 (appended 2, skipped 0)
全部跳過: ✅ Step 1 Complete: 區塊已存在於 release-4.42.0，未追加任何內容 (skipped 2)
```

### 🐛 修正 macOS bash 3.2 的變數展開問題

`echo -e "...$FIX_VERSION，..."` 在 macOS 內建的 bash 3.2 下,會把 `$FIX_VERSION` 後緊接的全形逗號首位元組(`0xEF`)一併當成變數名,導致變數展開成空字串且輸出亂碼。改用 `${FIX_VERSION}` 加大括號界定。

⚠️ 此專案訊息大量使用中文,凡是 `$變數` 後緊接非 ASCII 字元都會踩到,撰寫時請一律加大括號。

## v1.2 - 2025-11-13

### ✨ 新增功能

#### 1. Windows 版本 (.bat) 正式發布
- **新增檔案**: `SQL-processing.bat`
- **完整移植**: 將所有 Bash 腳本功能移植到 Windows Batch 語法
- **相同功能**:
  - ✅ 步驟11: 處理SQL（追加到 release_sql）
  - ✅ 步驟12: 開發環境SQL執行（帶事務控制）
  - ✅ JSON 解析（需要 jq.exe）
  - ✅ 重複執行檢查
  - ✅ 數據驗證
  - ✅ 事務控制與自動 ROLLBACK
  - ✅ 錯誤處理與互動式確認

#### 2. 語法轉換對照

| 功能 | Bash (.sh) | Windows Batch (.bat) |
|------|-----------|---------------------|
| 變量賦值 | `VAR="value"` | `set "VAR=value"` |
| 變量引用 | `$VAR` 或 `${VAR}` | `%VAR%` 或 `!VAR!` |
| 顯示文件內容 | `cat file` | `type file` |
| 創建空文件 | `touch file` | `type nul > file` |
| 創建目錄 | `mkdir -p dir` | `if not exist dir mkdir dir` |
| 檢查命令存在 | `command -v cmd` | `where cmd` |
| 空設備 | `/dev/null` | `nul` |
| 路徑分隔符 | `/` | `\` |
| 臨時文件 | `mktemp` | `%TEMP%\file_%RANDOM%.tmp` |

#### 3. Windows 特定實現

**事務處理**:
```batch
REM 創建臨時SQL文件
set "DB01_TEMP=%TEMP%\db01_transaction_%RANDOM%.sql"
(
    echo SET autocommit=0;
    echo START TRANSACTION;
    type "%DEV_SOURCE_DB01%"
    echo COMMIT;
) > "%DB01_TEMP%"

REM 執行並檢查結果
mysql ... < "%DB01_TEMP%" 2>nul
set "DB01_RESULT=%errorlevel%"
del "%DB01_TEMP%" 2>nul
```

**延遲變量展開**:
```batch
setlocal enabledelayedexpansion
REM 使用 !VAR! 而非 %VAR% 來讀取循環內的變量變更
```

### 📋 使用方法

**Windows 環境**:
```cmd
cd ProjectTool
SQL-processing.bat SACRIC-978.json
```

**Linux/macOS 環境**:
```bash
cd ProjectTool
./SQL-processing.sh SACRIC-978.json
```

### 🔧 前置需求 (Windows)

1. **jq for Windows**
   - 下載: https://stedolan.github.io/jq/download/
   - 下載 `jq-win64.exe` 並重命名為 `jq.exe`
   - 將 `jq.exe` 放入 PATH 環境變數目錄中（如 `C:\Windows\System32`）

2. **MySQL Client for Windows**
   - 下載: https://dev.mysql.com/downloads/mysql/
   - 或使用 Chocolatey: `choco install mysql-cli`
   - 確保 `mysql.exe` 在 PATH 中

### 📖 文檔更新

- ✅ 更新 SQL-processing-CHANGELOG.md
  - 新增 v1.2 版本說明
  - 詳細說明 Windows 版本實現細節
- ✅ 更新 SQL-processing-README.md
  - 新增「Windows 版本使用說明」章節
  - 更新前置需求（包含 Windows）
  - 提供完整的安裝與使用指南

### 🎯 跨平台支援

| 功能特性 | Linux/macOS (.sh) | Windows (.bat) |
|---------|------------------|----------------|
| JSON 解析 | ✅ jq | ✅ jq.exe |
| SQL 執行 | ✅ mysql | ✅ mysql.exe |
| 事務控制 | ✅ | ✅ |
| 錯誤處理 | ✅ | ✅ |
| 互動式確認 | ✅ | ✅ |
| 顏色輸出 | ✅ | ⚠️ 僅文字 |

### 🚀 後續計劃

- [x] Windows 版本（.bat）✅ 已完成
- [ ] 考慮加入乾跑模式（--dry-run）
- [ ] 加入詳細日誌模式（--verbose）
- [ ] 統一跨平台包裝腳本

## v1.1 - 2025-10-29

### ✨ 新增功能

#### 1. 事務控制與自動 ROLLBACK
- **問題**: 原本的實現沒有事務保護，SQL執行失敗時無法回滾已執行的語句
- **解決方案**: 使用單一 MySQL 連接執行事務
  ```bash
  {
      echo "SET autocommit=0;"
      echo "START TRANSACTION;"
      cat "$SOURCE_SQL"
      echo "COMMIT;"
  } | mysql ...
  ```
- **效果**:
  - ✅ 執行成功 → 自動 COMMIT
  - ❌ 執行失敗 → 自動 ROLLBACK
  - 保證數據一致性和原子性

#### 2. 錯誤詳情記錄
- 使用臨時日誌文件（`mktemp`）記錄執行輸出
- 失敗時顯示前5條錯誤訊息
- 執行完成後自動清理臨時文件

#### 3. 靈活的錯誤處理
- 根據 CLAUDE.md 要求，失敗時詢問用戶是否繼續
- DB01 失敗可選擇:
  - 終止流程（n）
  - 跳過 DB01，繼續執行 DB41（y）
- DB41 失敗可選擇:
  - 終止流程（n）
  - 繼續後續步驟（y）

### 🔧 改進項目

#### 執行流程
**原本**:
```bash
mysql ... < "$SOURCE_DB01"
if [ $? -eq 0 ]; then
    echo "成功"
else
    echo "失敗"
    exit 1
fi
```

**現在**:
```bash
DB01_LOG=$(mktemp)
{
    echo "SET autocommit=0;"
    echo "START TRANSACTION;"
    cat "$SOURCE_DB01"
    echo "COMMIT;"
} | mysql ... 2>&1 | tee "$DB01_LOG"

if [ ${PIPESTATUS[1]} -eq 0 ]; then
    echo "✅ 執行成功並提交事務"
else
    echo "❌ 執行失敗，事務已自動回滾"
    cat "$DB01_LOG" | grep -i "error" | head -5
    read -p "是否繼續？(y/n): "
fi
rm -f "$DB01_LOG"
```

### 📋 符合 CLAUDE.md 規範

根據 CLAUDE.md 步驟12的錯誤處理要求：

> **錯誤處理**:
> - 執行成功 → 記錄到日誌，繼續步驟13
> - 執行失敗 → **自動ROLLBACK**，記錄錯誤詳情，詢問是否繼續後續步驟

✅ **已實現**:
- [x] 自動 ROLLBACK（通過事務控制）
- [x] 記錄錯誤詳情（臨時日誌文件）
- [x] 詢問是否繼續後續步驟（互動式確認）

### 🧪 測試建議

1. **正常流程測試**
   ```bash
   ./SQL-processing.sh SACRIC-978.json
   ```
   確認 SQL 正常執行並提交

2. **失敗回滾測試**
   - 準備一個會失敗的 SQL（如重複的 PRIMARY KEY）
   - 執行後確認數據未被插入（已 ROLLBACK）
   - 驗證錯誤訊息是否正確顯示

3. **中斷流程測試**
   - DB01 失敗時選擇 'n'，確認流程終止
   - DB01 失敗時選擇 'y'，確認繼續執行 DB41

### 📖 文檔更新

- ✅ 更新 SQL-processing-README.md
  - 新增「事務控制與自動 ROLLBACK」章節
  - 新增執行失敗時的處理說明
  - 更新安全特性說明

### 🚀 後續計劃

- [ ] Windows 版本（.bat）待實現
- [ ] 考慮加入乾跑模式（--dry-run）
- [ ] 加入詳細日誌模式（--verbose）

## v1.0 - 2025-10-29

### 初始版本
- 實現步驟11: 處理SQL
- 實現步驟12: 開發環境SQL執行
- JSON 參數解析
- 重複執行檢查
- 數據驗證
