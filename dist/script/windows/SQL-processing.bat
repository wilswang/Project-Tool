@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM SQL-processing.bat - SQL 處理腳本（步驟 9）
REM 版本: v1.5
REM 用途:
REM   步驟1: 將生成的 SQL 追加到 release_sql 目錄
REM   步驟2: 執行 SQL 到開發環境資料庫（帶事務控制）
REM
REM 用法: SQL-processing.bat <json-file-name>
REM 示例: SQL-processing.bat SACRIC-978.json
REM
REM 相依性:
REM   - jq (必須) - 用於解析 JSON
REM     下載: https://stedolan.github.io/jq/download/
REM   - mysql (可選) - 用於步驟2執行SQL
REM     下載: https://dev.mysql.com/downloads/mysql/
REM
REM 注意事項:
REM   - 支援 Windows 作業系統
REM   - 如果 sqlOnly=true，跳過步驟2
REM   - 步驟2使用事務控制，失敗會自動ROLLBACK
REM =============================================================================

REM =============================================================================
REM 參數驗證
REM =============================================================================
if "%~1"=="" (
    echo Error: JSON file name required
    echo Usage: %~nx0 ^<json-file-name^>
    echo Example: %~nx0 SACRIC-978.json
    exit /b 1
)

REM =============================================================================
REM 設定路徑
REM 說明: 自動解析腳本目錄和專案根目錄
REM =============================================================================
set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
for %%I in ("%SCRIPT_DIR%\..\..") do set "TOOL_DIR=%%~fI"
for %%I in ("%TOOL_DIR%\..") do set "PROJECT_ROOT=%%~fI"
set "JSON_FILE=%TOOL_DIR%\sample\%~1"

REM Search subdirectories as fallback
if not exist "%JSON_FILE%" set "JSON_FILE=%TOOL_DIR%\sample\SingleWallet\%~1"
if not exist "!JSON_FILE!" set "JSON_FILE=%TOOL_DIR%\sample\ApiWallet\%~1"
if not exist "!JSON_FILE!" set "JSON_FILE=%TOOL_DIR%\sample\New Group\%~1"
if not exist "!JSON_FILE!" set "JSON_FILE=%TOOL_DIR%\sample\New Site\%~1"

REM =============================================================================
REM JSON 檔案驗證
REM =============================================================================
if not exist "!JSON_FILE!" (
    echo Error: JSON file not found: %TOOL_DIR%\sample\%~1
    exit /b 1
)
set "JSON_FILE=!JSON_FILE!"

echo === SQL Processing Script Start ===
echo JSON file: %JSON_FILE%
echo.

REM =============================================================================
REM 依賴檢查: jq 工具
REM 說明: jq 是必須的，用於解析 JSON 配置文件
REM =============================================================================
where jq >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: jq tool required for JSON parsing
    echo Install: Download jq.exe from https://stedolan.github.io/jq/download/
    echo And add it to PATH environment variable
    exit /b 1
)

REM =============================================================================
REM 解析 JSON 配置
REM 說明: 從 JSON 文件中提取關鍵參數
REM =============================================================================
for /f "delims=" %%i in ('jq -r .ticketNo "%JSON_FILE%"') do set "TICKET_NO=%%i"
for /f "delims=" %%i in ('jq -r .fixVersion "%JSON_FILE%"') do set "FIX_VERSION=%%i"
for /f "delims=" %%i in ('jq -r .webSiteValue "%JSON_FILE%"') do set "WEB_SITE_VALUE=%%i"
for /f "delims=" %%i in ('jq -r .sqlOnly "%JSON_FILE%"') do set "SQL_ONLY=%%i"
for /f "delims=" %%i in ('jq -r .project "%JSON_FILE%"') do set "PROJECT=%%i"
if "%PROJECT%"=="null" set "PROJECT=SACRIC"
for /f "delims=" %%i in ('jq -r .apiWhiteLabel "%JSON_FILE%"') do set "API_WHITE_LABEL=%%i"
for /f "delims=" %%i in ('jq -r .apiWalletInfo.newGroup "%JSON_FILE%"') do set "NEW_GROUP=%%i"
REM apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有（jq 回 null）→ 落回原本的判斷
for /f "delims=" %%i in ('jq -r .apiWalletType "%JSON_FILE%"') do set "API_WALLET_TYPE=%%i"

set "SQL_SUBDIR=New Site"
if /i "%API_WHITE_LABEL%"=="true" set "SQL_SUBDIR=ApiWallet"
if /i "%API_WHITE_LABEL%"=="true" if /i "%NEW_GROUP%"=="true" set "SQL_SUBDIR=New Group"
REM SingleWallet 不分 newGroup，一律放同一個目錄（必須排在 New Group 之後才能蓋掉它）
if /i "%API_WHITE_LABEL%"=="true" if /i "%API_WALLET_TYPE%"=="Single" set "SQL_SUBDIR=SingleWallet"

echo Ticket: SACRIC-%TICKET_NO%
echo Fix Version: %FIX_VERSION%
echo Site ID: %WEB_SITE_VALUE%
echo SQL Only: %SQL_ONLY%
echo.

REM ============================================
REM 步驟1: 處理SQL
REM ============================================
echo === Step 1: Process SQL ===

REM =============================================================================
REM SQL 檔案路徑設定
REM 說明:
REM   - SQL_DIR: release_sql 目錄 (目標位置)
REM   - SIM_SOURCE: 用於 SIM 環境的 SQL (步驟11追加用)
REM   - DEV_SOURCE: 用於 DEV 環境的 SQL (步驟12執行用)
REM =============================================================================
set "SQL_DIR=%PROJECT_ROOT%\src\release_sql\%FIX_VERSION%"
set "DB01_FILE=%SQL_DIR%\MyDB01.sql"
set "DB41_FILE=%SQL_DIR%\MyDB41.sql"

set "SIM_SOURCE_DB01=%TOOL_DIR%\result\sql\%SQL_SUBDIR%\%PROJECT%-%TICKET_NO%-SIM-DB-01.sql"
set "SIM_SOURCE_DB41=%TOOL_DIR%\result\sql\%SQL_SUBDIR%\%PROJECT%-%TICKET_NO%-SIM-DB-41.sql"

set "DEV_SOURCE_DB01=%TOOL_DIR%\result\sql\%SQL_SUBDIR%\%PROJECT%-%TICKET_NO%-DEV-DB-01.sql"
set "DEV_SOURCE_DB41=%TOOL_DIR%\result\sql\%SQL_SUBDIR%\%PROJECT%-%TICKET_NO%-DEV-DB-41.sql"

REM =============================================================================
REM 來源檔案驗證
REM 說明: 確保步驟6生成的 SQL 檔案存在
REM =============================================================================
if not exist "%SIM_SOURCE_DB01%" (
    echo Error: Source SQL file not found: %SIM_SOURCE_DB01%
    exit /b 1
)

if not exist "%SIM_SOURCE_DB41%" (
    echo Error: Source SQL file not found: %SIM_SOURCE_DB41%
    exit /b 1
)

REM =============================================================================
REM 確保目標目錄和檔案存在
REM 說明: 如果目錄或檔案不存在，自動創建
REM =============================================================================
echo Ensuring directory exists: %SQL_DIR%
if not exist "%SQL_DIR%" mkdir "%SQL_DIR%"

if not exist "%DB01_FILE%" (
    echo Creating file: %DB01_FILE%
    type nul > "%DB01_FILE%"
)

if not exist "%DB41_FILE%" (
    echo Creating file: %DB41_FILE%
    type nul > "%DB41_FILE%"
)

REM =============================================================================
REM 追加 SQL 內容到 release_sql
REM 說明: 將生成的 SIM-SQL 追加到對應的 release_sql 檔案
REM       這些 SQL 將用於 SIM/UAT/PROD 環境的正式發布
REM =============================================================================
REM 已存在該 ticket 區塊則跳過，內容不同則報錯（避免重跑產生重複區塊）
set /a APPENDED_COUNT=0
set /a SKIPPED_COUNT=0

call :append_sql_once "%SIM_SOURCE_DB01%" "%DB01_FILE%" "MyDB01.sql"
if errorlevel 1 exit /b 1

call :append_sql_once "%SIM_SOURCE_DB41%" "%DB41_FILE%" "MyDB41.sql"
if errorlevel 1 exit /b 1

if !APPENDED_COUNT! equ 0 (
    echo ✅ Step 1 Complete: 區塊已存在於 %FIX_VERSION%，未追加任何內容 ^(skipped !SKIPPED_COUNT!^)
) else (
    echo ✅ Step 1 Complete: SQL appended to %FIX_VERSION% ^(appended !APPENDED_COUNT!, skipped !SKIPPED_COUNT!^)
)
echo.

REM ============================================
REM 步驟2: 開發環境SQL執行 (可選)
REM ============================================

REM 如果 sqlOnly=true，跳過步驟2
if /i "%SQL_ONLY%"=="true" (
    echo sqlOnly=true, skipping dev environment SQL execution
    echo === SQL Processing Complete ===
    exit /b 0
)

echo === Step 2: Dev Environment SQL Execution ===

REM =============================================================================
REM MySQL 連接參數
REM 說明: 開發環境資料庫連接配置
REM       DB01 (Port 3101): websitesetting 表
REM       DB41 (Port 3141): marketliquidity 表
REM =============================================================================
set "DB_HOST=10.100.56.131"
set "DB_USER=cricket"
set "DB_PASS=cricket123"
set "DB_NAME=cricketdb"
set "DB01_PORT=3101"
set "DB41_PORT=3141"

REM =============================================================================
REM 依賴檢查: MySQL 客戶端
REM 說明: MySQL 是可選的，僅用於步驟12執行 SQL 到開發環境
REM =============================================================================
where mysql >nul 2>&1
if %errorlevel% neq 0 (
    echo Warning: MySQL client not installed, skipping SQL execution
    echo Install MySQL client and re-run
    exit /b 0
)

REM =============================================================================
REM 環境安全檢查：驗證MySQL連接
REM 說明: 嘗試連接到資料庫，失敗則跳過步驟2（不影響步驟1）
REM =============================================================================
echo Checking MySQL connection...
mysql -h %DB_HOST% -P %DB01_PORT% -u %DB_USER% -p%DB_PASS% -e "SELECT 1;" 2>nul
if %errorlevel% neq 0 (
    echo Warning: Cannot connect to dev database, skipping SQL execution
    echo.
    echo Connection parameters:
    echo   - Host: %DB_HOST%
    echo   - Port: %DB01_PORT%
    echo   - User: %DB_USER%
    echo   - Database: %DB_NAME%
    echo.
    echo Possible causes:
    echo   1. Database service not running
    echo   2. Network connection issue
    echo   3. Authentication credentials error
    echo   4. Firewall blocking connection
    echo.
    echo Hint: Check database status and retry, or contact system admin
    exit /b 0
)

echo ✅ MySQL connection OK
echo.

REM =============================================================================
REM 重複執行檢查
REM 說明: 查詢資料庫，確保相同 Site ID 的 SQL 未曾執行過
REM       這可以防止重複插入導致的數據錯誤
REM =============================================================================
echo Checking if SQL already executed...

for /f "delims=" %%i in ('mysql -h %DB_HOST% -P %DB01_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% -N -e "SELECT site FROM websitesetting WHERE site = %WEB_SITE_VALUE% LIMIT 1;" 2^>nul') do set "DB01_CHECK=%%i"

for /f "delims=" %%i in ('mysql -h %DB_HOST% -P %DB41_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% -N -e "SELECT site FROM marketliquidity WHERE site = %WEB_SITE_VALUE% LIMIT 1;" 2^>nul') do set "DB41_CHECK=%%i"

if defined DB01_CHECK (
    echo ⚠️  SQL already executed ^(Site: %WEB_SITE_VALUE%^), skipping
    echo   - DB01 record exists: site=%DB01_CHECK%
    if defined DB41_CHECK echo   - DB41 record exists: site=%DB41_CHECK%
    echo Execution cancelled
    exit /b 0
)

if defined DB41_CHECK (
    echo ⚠️  SQL already executed ^(Site: %WEB_SITE_VALUE%^), skipping
    echo   - DB41 record exists: site=%DB41_CHECK%
    echo Execution cancelled
    exit /b 0
)

echo ✅ No duplicate records
echo.
echo Starting SQL execution...
echo.

REM =============================================================================
REM 執行 DB01 SQL（帶事務控制）
REM 說明: 將 DEV-DB-01.sql 執行到開發環境的 DB01 (websitesetting)
REM       使用事務包裹，任何錯誤會自動 ROLLBACK
REM =============================================================================
echo Executing DB01 SQL ^(%DB_HOST%:%DB01_PORT%^)...

REM 創建臨時事務 SQL 文件
set "DB01_TEMP=%TEMP%\db01_transaction_%RANDOM%.sql"
(
    echo SET autocommit=0;
    echo START TRANSACTION;
    type "%DEV_SOURCE_DB01%"
    echo COMMIT;
) > "%DB01_TEMP%"

REM 執行事務並檢查結果
mysql -h %DB_HOST% -P %DB01_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% < "%DB01_TEMP%" 2>nul
set "DB01_RESULT=%errorlevel%"
del "%DB01_TEMP%" 2>nul

if %DB01_RESULT% equ 0 (
    echo ✅ DB01 executed successfully, transaction committed
) else (
    echo ❌ DB01 execution failed, transaction rolled back
    echo.
    set /p "CONTINUE=DB01 failed, continue to next steps? (y/n): "
    if /i not "!CONTINUE!"=="y" (
        echo Process terminated
        exit /b 1
    ) else (
        echo Warning: Skipping DB01, continuing to DB41
    )
)

echo.

REM =============================================================================
REM 執行 DB41 SQL（帶事務控制）
REM 說明: 將 DEV-DB-41.sql 執行到開發環境的 DB41 (marketliquidity)
REM       使用事務包裹，任何錯誤會自動 ROLLBACK
REM =============================================================================
echo Executing DB41 SQL ^(%DB_HOST%:%DB41_PORT%^)...

REM 創建臨時事務 SQL 文件
set "DB41_TEMP=%TEMP%\db41_transaction_%RANDOM%.sql"
(
    echo SET autocommit=0;
    echo START TRANSACTION;
    type "%DEV_SOURCE_DB41%"
    echo COMMIT;
) > "%DB41_TEMP%"

REM 執行事務並檢查結果
mysql -h %DB_HOST% -P %DB41_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% < "%DB41_TEMP%" 2>nul
set "DB41_RESULT=%errorlevel%"
del "%DB41_TEMP%" 2>nul

if %DB41_RESULT% equ 0 (
    echo ✅ DB41 executed successfully, transaction committed
) else (
    echo ❌ DB41 execution failed, transaction rolled back
    echo.
    set /p "CONTINUE=DB41 failed, continue to next steps? (y/n): "
    if /i not "!CONTINUE!"=="y" (
        echo Process terminated
        exit /b 1
    ) else (
        echo Warning: DB41 failed, but continuing process
    )
)

echo.

REM =============================================================================
REM 數據驗證
REM 說明: 查詢資料庫，確認數據已正確插入
REM =============================================================================
echo Verifying data insertion...

REM 驗證 DB01 (websitesetting 表)
for /f "delims=" %%i in ('mysql -h %DB_HOST% -P %DB01_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% -N -e "SELECT site FROM websitesetting WHERE site = %WEB_SITE_VALUE% LIMIT 1;" 2^>nul') do set "DB01_VERIFY=%%i"

if "%DB01_VERIFY%"=="%WEB_SITE_VALUE%" (
    echo ✅ DB01 data verified ^(site=%WEB_SITE_VALUE%^)
) else (
    echo ❌ DB01 data verification failed
)

REM 驗證 DB41 (marketliquidity 表)
for /f "delims=" %%i in ('mysql -h %DB_HOST% -P %DB41_PORT% -u %DB_USER% -p%DB_PASS% %DB_NAME% -N -e "SELECT site FROM marketliquidity WHERE site = %WEB_SITE_VALUE% LIMIT 1;" 2^>nul') do set "DB41_VERIFY=%%i"

if defined DB41_VERIFY (
    echo ✅ DB41 data verified ^(site=%WEB_SITE_VALUE%^)
) else (
    echo ❌ DB41 data verification failed
)

echo.
echo === SQL Processing Complete ===

endlocal
exit /b 0

REM =============================================================================
REM :append_sql_once  %~1=來源SQL  %~2=release檔  %~3=顯示名稱
REM   區塊不存在         → 照常 append
REM   區塊存在且內容相同 → 跳過（重跑不會產生重複區塊）
REM   區塊存在但內容不同 → 報錯中止，避免舊區塊被靜默留下
REM 內容比對用內嵌 PowerShell 完成（批次檔無法做多行區塊比對）
REM =============================================================================
:append_sql_once
findstr /b /c:"-- %PROJECT%-%TICKET_NO% " "%~2" >nul 2>&1
if errorlevel 1 (
    echo Appending SQL to: %~2
    type "%~1" >> "%~2"
    echo. >> "%~2"
    echo. >> "%~2"
    set /a APPENDED_COUNT+=1
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$tk = '-- %PROJECT%-%TICKET_NO% '; $pfx = '-- %PROJECT%-';" ^
 "$block = @(); $found = $false;" ^
 "foreach ($line in Get-Content -LiteralPath '%~2') {" ^
 "  if ($line.StartsWith($tk)) { $found = $true; $block += $line; continue }" ^
 "  if ($found -and $line.StartsWith($pfx)) { break }" ^
 "  if ($found) { $block += $line } };" ^
 "function Trim-Tail($a) { $i = $a.Count - 1; while ($i -ge 0 -and $a[$i].Trim() -eq '') { $i-- }; if ($i -lt 0) { @() } else { $a[0..$i] } };" ^
 "$b = Trim-Tail $block; $s = Trim-Tail @(Get-Content -LiteralPath '%~1');" ^
 "if (($b -join \"`n\") -eq ($s -join \"`n\")) { exit 0 } else { exit 1 }"

if errorlevel 1 (
    echo ❌ %PROJECT%-%TICKET_NO% already in %~3 but content differs
    echo    release SQL 內既有的區塊與本次產出不一致
    echo    常見原因: site 編號或欄位被修改過
    echo    請人工確認並移除或更新既有區塊後再執行
    exit /b 1
)

echo ⚠️  %PROJECT%-%TICKET_NO% already in %~3, skipping append
set /a SKIPPED_COUNT+=1
exit /b 0
