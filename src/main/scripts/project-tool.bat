@echo off
chcp 65001 >nul
REM 切換到此批次檔所在的目錄
cd /d "%~dp0"

set TARGET_DIR=result

REM 檢查資料夾是否存在，否則建立
if not exist "%TARGET_DIR%" (
    echo 📁 建立資料夾 %TARGET_DIR%
    mkdir "%TARGET_DIR%"
)

set JAR_FILE=Project-Tool-1.1.1-jar-with-dependencies.jar

REM 檢查 JAR 是否存在
if not exist "%JAR_FILE%" (
    echo ❌ JAR 檔案不存在，請先執行 'mvn package'
    pause
    exit /b 1
)

REM 執行工具，%* 代表傳入參數
echo.
echo 正在執行 Project Tool...
echo ========================================
java -jar "%JAR_FILE%" %*

REM 檢查執行結果
REM errorlevel 必須先存進變數：寫在 if (...) 括號區塊內的 %errorlevel% 會在 parse 期就展開，印出舊值
set "EXIT_CODE=%errorlevel%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo ========================================
    echo ❌ 執行失敗，錯誤碼: %EXIT_CODE%
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ✅ 執行完成
    echo ========================================
)

echo.
pause
exit /b %EXIT_CODE%
