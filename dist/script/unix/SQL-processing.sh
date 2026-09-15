#!/bin/bash

# =============================================================================
# SQL-processing.sh - SQL 處理腳本（步驟 9）
# 版本: v1.5
# 用途:
#   步驟1: 將生成的 SQL 追加到 release_sql 目錄
#   步驟2: 執行 SQL 到開發環境資料庫（帶事務控制）
#
# 用法: ./SQL-processing.sh <json-file-name>
# 示例: ./SQL-processing.sh SACRIC-978.json
#
# 相依性:
#   - jq (必須) - 用於解析 JSON
#   - mysql (可選) - 用於步驟2執行SQL
#
# 注意事項:
#   - 支援 macOS 和 Linux
#   - 如果 sqlOnly=true，跳過步驟2
#   - 步驟2使用事務控制，失敗會自動ROLLBACK
# =============================================================================

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 檢查參數
if [ $# -ne 1 ]; then
    echo -e "${RED}Error: JSON file name required${NC}"
    echo "Usage: $0 <json-file-name>"
    echo "Example: $0 SACRIC-978.json"
    exit 1
fi

# 設定路徑
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT_ROOT="$(cd "$TOOL_DIR/.." && pwd)"

# Find JSON file - supports both "SACRIC-978.json" and "ApiWallet/SACRIC-978.json"
JSON_FILE="$TOOL_DIR/sample/$1"
if [ ! -f "$JSON_FILE" ]; then
    for _s in "SingleWallet" "ApiWallet" "New Group" "New Site"; do
        if [ -f "$TOOL_DIR/sample/$_s/$1" ]; then
            JSON_FILE="$TOOL_DIR/sample/$_s/$1"
            break
        fi
    done
    unset _s
fi

# 檢查JSON檔案是否存在
if [ ! -f "$JSON_FILE" ]; then
    echo -e "${RED}Error: JSON file not found: $JSON_FILE${NC}"
    exit 1
fi

resolve_subdir() {
    local json_file="$1"
    local api_white_label api_wallet_type new_group
    api_white_label=$(jq -r '.apiWhiteLabel' "$json_file")
    # apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有 → 落回原本的判斷
    api_wallet_type=$(jq -r '.apiWalletType // empty' "$json_file")
    new_group=$(jq -r '.apiWalletInfo.newGroup // false' "$json_file")
    if [ "$api_white_label" = "true" ]; then
        if [ "$api_wallet_type" = "Single" ]; then
            # SingleWallet 不分 newGroup，一律放同一個目錄
            echo "SingleWallet"
        elif [ "$new_group" = "true" ]; then
            echo "New Group"
        else
            echo "ApiWallet"
        fi
    else
        echo "New Site"
    fi
}

echo "=== SQL Processing Script Start ==="
echo "JSON file: $JSON_FILE"
echo ""

# 讀取JSON內容（需要jq工具）
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq tool required for JSON parsing${NC}"
    echo "Install: brew install jq (macOS) or apt-get install jq (Linux)"
    exit 1
fi

# 解析JSON
TICKET_NO=$(jq -r '.ticketNo' "$JSON_FILE")
FIX_VERSION=$(jq -r '.fixVersion' "$JSON_FILE")
WEB_SITE_VALUE=$(jq -r '.webSiteValue' "$JSON_FILE")
SQL_ONLY=$(jq -r '.sqlOnly' "$JSON_FILE")
PROJECT=$(jq -r '.project // "SACRIC"' "$JSON_FILE")
SQL_SUBDIR=$(resolve_subdir "$JSON_FILE")

echo "Ticket: SACRIC-$TICKET_NO"
echo "Fix Version: $FIX_VERSION"
echo "Site ID: $WEB_SITE_VALUE"
echo "SQL Only: $SQL_ONLY"
echo ""

# ============================================
# 步驟1: 處理SQL
# ============================================
echo -e "${BLUE}=== Step 1: Process SQL ===${NC}"

# SQL檔案路徑
SQL_DIR="$PROJECT_ROOT/src/release_sql/$FIX_VERSION"
DB01_FILE="$SQL_DIR/MyDB01.sql"
DB41_FILE="$SQL_DIR/MyDB41.sql"

SIM_SOURCE_DB01="$TOOL_DIR/result/sql/$SQL_SUBDIR/${PROJECT}-${TICKET_NO}-SIM-DB-01.sql"
SIM_SOURCE_DB41="$TOOL_DIR/result/sql/$SQL_SUBDIR/${PROJECT}-${TICKET_NO}-SIM-DB-41.sql"

DEV_SOURCE_DB01="$TOOL_DIR/result/sql/$SQL_SUBDIR/${PROJECT}-${TICKET_NO}-DEV-DB-01.sql"
DEV_SOURCE_DB41="$TOOL_DIR/result/sql/$SQL_SUBDIR/${PROJECT}-${TICKET_NO}-DEV-DB-41.sql"

# 檢查來源SQL檔案
if [ ! -f "$SIM_SOURCE_DB01" ]; then
    echo -e "${RED}Error: Source SQL file not found: $SIM_SOURCE_DB01${NC}"
    exit 1
fi

if [ ! -f "$SIM_SOURCE_DB41" ]; then
    echo -e "${RED}Error: Source SQL file not found: $SIM_SOURCE_DB41${NC}"
    exit 1
fi

# 確保目錄存在
echo "Ensuring directory exists: $SQL_DIR"
mkdir -p "$SQL_DIR"

# 確保檔案存在
if [ ! -f "$DB01_FILE" ]; then
    echo "Creating file: $DB01_FILE"
    touch "$DB01_FILE"
fi

if [ ! -f "$DB41_FILE" ]; then
    echo "Creating file: $DB41_FILE"
    touch "$DB41_FILE"
fi

# 從 release SQL 取出指定 ticket 的區塊（到下一個 ticket 標題或檔尾為止）
extract_ticket_block() {
    local target_file="$1"

    awk -v tk="-- ${PROJECT}-${TICKET_NO} " -v pfx="-- ${PROJECT}-" '
        index($0, tk) == 1 { found = 1; print; next }
        found && index($0, pfx) == 1 { exit }
        found { print }
    ' "$target_file"
}

# 去掉尾端空白行，append 時會多補空行，比對前需先正規化
strip_trailing_blank_lines() {
    awk '{ lines[NR] = $0 }
         END {
             last = NR
             while (last > 0 && lines[last] ~ /^[[:space:]]*$/) last--
             for (i = 1; i <= last; i++) print lines[i]
         }'
}

# 追加 SQL 到 release 檔
#   區塊不存在         → 照常 append
#   區塊存在且內容相同 → 跳過（重跑不會產生重複區塊）
#   區塊存在但內容不同 → 報錯中止，避免舊區塊被靜默留下
append_sql_once() {
    local source_file="$1"
    local target_file="$2"
    local label="$3"

    if grep -qE "^-- ${PROJECT}-${TICKET_NO} " "$target_file"; then
        if diff -q <(extract_ticket_block "$target_file" | strip_trailing_blank_lines) \
                   <(strip_trailing_blank_lines < "$source_file") > /dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  ${PROJECT}-${TICKET_NO} already in ${label}, skipping append${NC}"
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            return 0
        fi

        echo -e "${RED}❌ ${PROJECT}-${TICKET_NO} already in ${label} but content differs${NC}"
        echo -e "${RED}   release SQL 內既有的區塊與本次產出不一致${NC}"
        echo -e "${RED}   常見原因: site 編號或欄位被修改過${NC}"
        echo -e "${RED}   請人工確認並移除或更新既有區塊後再執行${NC}"
        return 1
    fi

    echo "Appending SQL to: $target_file"
    cat "$source_file" >> "$target_file"
    echo -e "\n" >> "$target_file"
    APPENDED_COUNT=$((APPENDED_COUNT + 1))
    return 0
}

# 追加SQL內容（已存在則跳過，避免重跑造成重複區塊）
APPENDED_COUNT=0
SKIPPED_COUNT=0

if ! append_sql_once "$SIM_SOURCE_DB01" "$DB01_FILE" "MyDB01.sql"; then
    exit 1
fi

if ! append_sql_once "$SIM_SOURCE_DB41" "$DB41_FILE" "MyDB41.sql"; then
    exit 1
fi

if [ "$APPENDED_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✅ Step 1 Complete: 區塊已存在於 ${FIX_VERSION}，未追加任何內容 (skipped ${SKIPPED_COUNT})${NC}"
else
    echo -e "${GREEN}✅ Step 1 Complete: SQL appended to $FIX_VERSION (appended $APPENDED_COUNT, skipped $SKIPPED_COUNT)${NC}"
fi
echo ""

# ============================================
# 步驟2: 開發環境SQL執行 (可選)
# ============================================

# 如果 sqlOnly=true，跳過步驟2
if [ "$SQL_ONLY" = "true" ]; then
    echo -e "${YELLOW}sqlOnly=true, skipping dev environment SQL execution${NC}"
    echo -e "${GREEN}=== SQL Processing Complete ===${NC}"
    exit 0
fi

echo -e "${BLUE}=== Step 2: Dev Environment SQL Execution ===${NC}"

# MySQL連接參數
DB_HOST="10.100.56.131"
DB_USER="cricket"
DB_PASS="cricket123"
DB_NAME="cricketdb"
DB01_PORT="3101"
DB41_PORT="3141"

# 檢查MySQL是否可用
if ! command -v mysql &> /dev/null; then
    echo -e "${YELLOW}Warning: MySQL client not installed, skipping SQL execution${NC}"
    echo -e "${YELLOW}Install MySQL client and re-run${NC}"
    exit 0
fi

# =============================================================================
# 環境安全檢查：驗證MySQL連接
# 說明: 嘗試連接到資料庫，失敗則跳過步驟2（不影響步驟1）
# =============================================================================
echo "Checking MySQL connection..."
if ! mysql -h "$DB_HOST" -P "$DB01_PORT" -u "$DB_USER" -p"$DB_PASS" -e "SELECT 1;" 2>/dev/null; then
    echo -e "${YELLOW}Warning: Cannot connect to dev database, skipping SQL execution${NC}"
    echo ""
    echo "Connection parameters:"
    echo "  - Host: $DB_HOST"
    echo "  - Port: $DB01_PORT"
    echo "  - User: $DB_USER"
    echo "  - Database: $DB_NAME"
    echo ""
    echo "Possible causes:"
    echo "  1. Database service not running"
    echo "  2. Network connection issue"
    echo "  3. Authentication credentials error"
    echo "  4. Firewall blocking connection"
    echo ""
    echo "Hint: Check database status and retry, or contact system admin"
    exit 0
fi

echo -e "${GREEN}✅ MySQL connection OK${NC}"
echo ""

# 重複執行檢查
echo "Checking if SQL already executed..."

DB01_CHECK=$(mysql -h "$DB_HOST" -P "$DB01_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT site FROM websitesetting WHERE site = $WEB_SITE_VALUE LIMIT 1;" 2>&1 | grep -v "Warning")

DB41_CHECK=$(mysql -h "$DB_HOST" -P "$DB41_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT site FROM marketliquidity WHERE site = $WEB_SITE_VALUE LIMIT 1;" 2>&1 | grep -v "Warning")

if [ ! -z "$DB01_CHECK" ] || [ ! -z "$DB41_CHECK" ]; then
    echo -e "${YELLOW}⚠️  SQL already executed (Site: $WEB_SITE_VALUE), skipping${NC}"
    if [ ! -z "$DB01_CHECK" ]; then
        echo "  - DB01 record exists: site=$DB01_CHECK"
    fi
    if [ ! -z "$DB41_CHECK" ]; then
        echo "  - DB41 record exists: site=$DB41_CHECK"
    fi
    echo "Execution cancelled"
    exit 0
else
    echo -e "${GREEN}✅ No duplicate records${NC}"
fi

echo ""
echo "Starting SQL execution..."
echo ""

# 執行DB01 SQL（帶事務控制）
echo "Executing DB01 SQL (${DB_HOST}:${DB01_PORT})..."

# 創建臨時日誌文件
DB01_LOG=$(mktemp)

# 在單一連接中執行事務
{
    echo "SET autocommit=0;"
    echo "START TRANSACTION;"
    cat "$DEV_SOURCE_DB01"
    echo "COMMIT;"
} | mysql -h "$DB_HOST" -P "$DB01_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" 2>&1 | tee "$DB01_LOG" | grep -v "Warning" | grep -v "^$"

# 檢查執行結果
if [ ${PIPESTATUS[1]} -eq 0 ]; then
    echo -e "${GREEN}✅ DB01 executed successfully, transaction committed${NC}"
    rm -f "$DB01_LOG"
else
    echo -e "${RED}❌ DB01 execution failed, transaction rolled back${NC}"
    echo ""
    echo "Error details:"
    cat "$DB01_LOG" | grep -i "error" | head -5
    rm -f "$DB01_LOG"

    echo ""
    read -p "DB01 failed, continue to next steps? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Process terminated"
        exit 1
    else
        echo -e "${YELLOW}Warning: Skipping DB01, continuing to DB41${NC}"
    fi
fi

echo ""

# 執行DB41 SQL（帶事務控制）
echo "Executing DB41 SQL (${DB_HOST}:${DB41_PORT})..."

# 創建臨時日誌文件
DB41_LOG=$(mktemp)

# 在單一連接中執行事務
{
    echo "SET autocommit=0;"
    echo "START TRANSACTION;"
    cat "$DEV_SOURCE_DB41"
    echo "COMMIT;"
} | mysql -h "$DB_HOST" -P "$DB41_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" 2>&1 | tee "$DB41_LOG" | grep -v "Warning" | grep -v "^$"

# 檢查執行結果
if [ ${PIPESTATUS[1]} -eq 0 ]; then
    echo -e "${GREEN}✅ DB41 executed successfully, transaction committed${NC}"
    rm -f "$DB41_LOG"
else
    echo -e "${RED}❌ DB41 execution failed, transaction rolled back${NC}"
    echo ""
    echo "Error details:"
    cat "$DB41_LOG" | grep -i "error" | head -5
    rm -f "$DB41_LOG"

    echo ""
    read -p "DB41 failed, continue to next steps? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Process terminated"
        exit 1
    else
        echo -e "${YELLOW}Warning: DB41 failed, but continuing process${NC}"
    fi
fi

echo ""
echo "Verifying data insertion..."

# 驗證DB01
DB01_VERIFY=$(mysql -h "$DB_HOST" -P "$DB01_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT site FROM websitesetting WHERE site = $WEB_SITE_VALUE LIMIT 1;" 2>&1 | grep -v "Warning")

if [ "$DB01_VERIFY" = "$WEB_SITE_VALUE" ]; then
    echo -e "${GREEN}✅ DB01 data verified (site=$WEB_SITE_VALUE)${NC}"
else
    echo -e "${RED}❌ DB01 data verification failed${NC}"
fi

# 驗證DB41
DB41_VERIFY=$(mysql -h "$DB_HOST" -P "$DB41_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT site FROM marketliquidity WHERE site = $WEB_SITE_VALUE LIMIT 1;" 2>&1 | grep -v "Warning")

if [ ! -z "$DB41_VERIFY" ]; then
    echo -e "${GREEN}✅ DB41 data verified (site=$WEB_SITE_VALUE)${NC}"
else
    echo -e "${RED}❌ DB41 data verification failed${NC}"
fi

echo ""
echo -e "${GREEN}=== SQL Processing Complete ===${NC}"
