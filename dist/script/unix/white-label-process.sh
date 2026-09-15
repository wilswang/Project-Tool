#!/bin/bash

# =============================================================================
# white-label-process.sh - Complete White Label Workflow (Steps 1-8)
# Usage: ./white-label-process.sh <TICKET_NO> [options]
# Example: ./white-label-process.sh SACRIC-1020
# Example: ./white-label-process.sh SACRIC-1020 -c -t
# =============================================================================

set -e  # Exit on error

# Color definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# =============================================================================
# Helper Functions
# =============================================================================

# Conditional output functions (respect --quiet mode)
echo_verbose() {
    if [ "$QUIET_MODE" = false ]; then
        echo -e "$@"
    fi
}

echo_info() {
    if [ "$QUIET_MODE" = false ]; then
        echo -e "$@"
    fi
}

echo_critical() {
    # Critical messages: always displayed
    echo -e "$@"
}

# =============================================================================
# Argument Parsing
# =============================================================================

# Default values
CUSTOMIZED_MODE=false
TEST_MODE=false
QUIET_MODE=false
TICKET_NO=""
FROM_STEP=1

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--customized)
            CUSTOMIZED_MODE=true
            shift
            ;;
        -t|--testMode)
            TEST_MODE=true
            shift
            ;;
        --quiet)
            QUIET_MODE=true
            shift
            ;;
        -s|--from-step)
            FROM_STEP="$2"
            shift 2
            ;;
        --from-step=*)
            FROM_STEP="${1#*=}"
            shift
            ;;
        SACRIC-*)
            TICKET_NO="$1"
            shift
            ;;
        *)
            echo -e "${RED}Error: Unknown parameter '$1'${NC}"
            echo "Usage: $0 <TICKET_NO> [options]"
            echo "Options:"
            echo "  -c, --customized  Customized mode (skip steps 5-8)"
            echo "  -t, --testMode    Test mode (skip actual Jira updates)"
            echo "  -s, --from-step N Start from step N (1-8)"
            echo "  --quiet           Suppress verbose output"
            echo ""
            echo "Examples:"
            echo "  $0 SACRIC-1020"
            echo "  $0 SACRIC-1020 -c"
            echo "  $0 SACRIC-1020 -s 3      # Start from step 3"
            echo "  $0 SACRIC-1020 -t --quiet"
            exit 1
            ;;
    esac
done

# Validate required parameters
if [ -z "$TICKET_NO" ]; then
    echo -e "${RED}Error: Missing ticket number${NC}"
    echo "Usage: $0 <TICKET_NO> [options]"
    echo "Example: $0 SACRIC-1020"
    exit 1
fi

# Validate ticket format
if [[ ! $TICKET_NO =~ ^SACRIC-[0-9]+$ ]]; then
    echo -e "${RED}Error: Invalid ticket format, expected SACRIC-XXX${NC}"
    exit 1
fi

# Validate FROM_STEP
if [[ ! $FROM_STEP =~ ^[1-8]$ ]]; then
    echo -e "${RED}Error: --from-step must be between 1 and 8${NC}"
    exit 1
fi

# Set TEST_FLAG for JAR tool calls
TEST_FLAG=""
if [ "$TEST_MODE" = true ]; then
    TEST_FLAG="-t"
fi

# =============================================================================
# Path Configuration
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT_ROOT="$(cd "$TOOL_DIR/.." && pwd)"

# File paths
JAR_FILE="$TOOL_DIR/Project-Tool.jar"
JIRA_FILE="$TOOL_DIR/result/jira/${TICKET_NO}-jira.txt"
LOG_FILE="$TOOL_DIR/log/${TICKET_NO}.txt"
CLAUDE_MD="$TOOL_DIR/task/white-label-mapping-rule.md"

# Find JSON file - search subdirectories first, fallback to root (new tickets)
SUBDIR=""
for _s in "SingleWallet" "ApiWallet" "New Group" "New Site"; do
    if [ -f "$TOOL_DIR/sample/$_s/${TICKET_NO}.json" ]; then
        SUBDIR="$_s"
        break
    fi
done
unset _s
JSON_FILE="$TOOL_DIR/sample${SUBDIR:+/$SUBDIR}/${TICKET_NO}.json"

# Create directories
mkdir -p "$TOOL_DIR/result/jira"
mkdir -p "$TOOL_DIR/result/sql/ApiWallet"
mkdir -p "$TOOL_DIR/result/sql/New Group"
mkdir -p "$TOOL_DIR/result/sql/New Site"
mkdir -p "$TOOL_DIR/result/sql/SingleWallet"
mkdir -p "$TOOL_DIR/log"
mkdir -p "$TOOL_DIR/sample/ApiWallet"
mkdir -p "$TOOL_DIR/sample/New Group"
mkdir -p "$TOOL_DIR/sample/New Site"
mkdir -p "$TOOL_DIR/sample/SingleWallet"

# Record start time
START_TIME=$(date '+%Y-%m-%d %H:%M:%S')

echo_critical "${BLUE}=== White Label Process: ${TICKET_NO} ===${NC}"
echo_verbose "Mode: customized=$CUSTOMIZED_MODE, test=$TEST_MODE, quiet=$QUIET_MODE"
echo_verbose ""

# =============================================================================
# System Detection & Cross-Platform Functions
# =============================================================================

detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "linux"
    else
        echo "unknown"
    fi
}

extract_number() {
    local text="$1"
    local pattern="$2"

    if [[ "$OS_TYPE" == "macos" ]]; then
        echo "$text" | sed -n "s/.*${pattern}\([0-9][0-9]*\).*/\1/p"
    else
        echo "$text" | grep -oP "${pattern}\\K[0-9]+"
    fi
}

safe_sed_replace() {
    local file="$1"
    local pattern="$2"
    local replacement="$3"

    if [[ "$OS_TYPE" == "macos" ]]; then
        sed -i '' "s/${pattern}/${replacement}/g" "$file"
    else
        sed -i "s/${pattern}/${replacement}/g" "$file"
    fi
}

check_dependencies() {
    echo_verbose "${BLUE}=== Checking dependencies ===${NC}"

    local missing_deps=()
    local optional_deps=()

    # Check required dependencies
    command -v jq >/dev/null 2>&1 || missing_deps+=("jq")
    command -v git >/dev/null 2>&1 || missing_deps+=("git")
    command -v mvn >/dev/null 2>&1 || missing_deps+=("mvn")
    command -v java >/dev/null 2>&1 || missing_deps+=("java")
    command -v claude >/dev/null 2>&1 || missing_deps+=("claude")

    # Check optional dependencies
    command -v mysql >/dev/null 2>&1 || optional_deps+=("mysql")

    # Report missing required dependencies
    if [ ${#missing_deps[@]} -gt 0 ]; then
        echo_critical "${RED}❌ Error: Missing required tools${NC}"
        echo "Please install:"
        for dep in "${missing_deps[@]}"; do
            case $dep in
                jq)
                    if [[ "$OS_TYPE" == "macos" ]]; then
                        echo "  - jq: brew install jq"
                    else
                        echo "  - jq: apt-get install jq"
                    fi
                    ;;
                git)
                    if [[ "$OS_TYPE" == "macos" ]]; then
                        echo "  - git: brew install git"
                    else
                        echo "  - git: apt-get install git"
                    fi
                    ;;
                mvn)
                    if [[ "$OS_TYPE" == "macos" ]]; then
                        echo "  - maven: brew install maven"
                    else
                        echo "  - maven: apt-get install maven"
                    fi
                    ;;
                java)
                    if [[ "$OS_TYPE" == "macos" ]]; then
                        echo "  - java: brew install openjdk"
                    else
                        echo "  - java: apt-get install default-jdk"
                    fi
                    ;;
                claude)
                    echo "  - claude: npm install -g @anthropic-ai/claude-cli"
                    ;;
            esac
        done
        return 1
    fi

    # Report missing optional dependencies
    if [ ${#optional_deps[@]} -gt 0 ]; then
        echo_verbose "${YELLOW}⚠️  Warning: Optional tools not installed:${NC}"
        for dep in "${optional_deps[@]}"; do
            echo_verbose "  - $dep"
        done
        echo_verbose ""
    fi

    # Verify JAR file exists
    if [ ! -f "$JAR_FILE" ]; then
        echo_critical "${RED}❌ Error: JAR file not found: $JAR_FILE${NC}"
        return 1
    fi

    echo_verbose "${GREEN}✅ Dependencies check passed${NC}"
    echo_verbose ""

    return 0
}

# Initialize OS detection
OS_TYPE=$(detect_os)

# =============================================================================
# Other Helper Functions
# =============================================================================

read_json_field() {
    local field="$1"
    jq -r ".${field}" "$JSON_FILE" 2>/dev/null || echo ""
}

resolve_subdir() {
    local json_file="$1"
    local api_white_label api_wallet_type new_group
    api_white_label=$(jq -r '.apiWhiteLabel' "$json_file")
    # apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有（jq 回 "null"）→ 落回原本的判斷
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

configure_java8() {
    local java8_home=""

    if [[ "$OS_TYPE" == "macos" ]]; then
        java8_home=$(/usr/libexec/java_home -v 1.8 2>/dev/null)
    else
        local candidates=(
            /usr/lib/jvm/java-8-openjdk-amd64
            /usr/lib/jvm/java-8-openjdk-arm64
            /usr/lib/jvm/java-8-openjdk
            /usr/lib/jvm/java-1.8.0-openjdk
        )
        for path in "${candidates[@]}"; do
            if [ -d "$path" ] && [ -f "$path/bin/java" ]; then
                java8_home="$path"
                break
            fi
        done
        if [ -z "$java8_home" ]; then
            for path in /usr/lib/jvm/jdk1.8.0_* /usr/lib/jvm/java-1.8.0-openjdk-*; do
                if [ -d "$path" ] && [ -f "$path/bin/java" ]; then
                    java8_home="$path"
                    break
                fi
            done
        fi
    fi

    if [ -n "$java8_home" ]; then
        export JAVA_HOME="$java8_home"
        echo_verbose "JAVA_HOME switched to Java 8: $JAVA_HOME"
    else
        local current_version
        current_version=$(java -version 2>&1 | head -1)
        echo_critical "${YELLOW}⚠️  Warning: Java 8 not found, using current JDK: ${current_version}${NC}"
    fi
}

# Log step function (writes timestamped messages to console)
log_step() {
    local step="$1"
    local message="$2"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 步驟${step}: ${message}"
}

# =============================================================================
# Log File Generation
# =============================================================================

update_final_log() {
    local customized="$CUSTOMIZED_MODE"
    local test_mode="$TEST_MODE"
    local web_site_name=$(read_json_field "webSiteName")
    local web_site_value=$(read_json_field "webSiteValue")
    local fix_version=$(read_json_field "fixVersion")
    local developer=$(read_json_field "developer")
    local api_whitelabel=$(read_json_field "apiWhiteLabel")
    local current_time=$(date '+%Y-%m-%d %H:%M:%S')
    local current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

    # Determine step statuses
    local step2_5_status="⏭️  Fill groupInfo - Skipped (newGroup is not true)"
    if [ "$(read_json_field "apiWalletInfo.newGroup")" = "true" ]; then
        step2_5_status="✅ Fill groupInfo - Resolved from spreadsheet (group $(read_json_field "apiWalletInfo.group"))"
    fi

    local step4_status="✅ Compile & Test - Build passed"
    if [ -f "/tmp/build_${TICKET_NO}.log" ]; then
        step4_status="⚠️  Compile & Test - Manual fixes may be needed"
    fi

    local step5_status="✅ Execute DEV SQL - SQL processed & executed"
    if [ "$customized" = "true" ] || [ "$test_mode" = "true" ]; then
        step5_status="⏭️  Execute DEV SQL - Skipped"
    fi

    local step6_status="⏭️  Git Commit - Skipped"
    if [ "$customized" = "false" ] && [ "$test_mode" = "false" ]; then
        local commit_hash=$(git rev-parse --short HEAD 2>/dev/null || echo "")
        if [ -n "$commit_hash" ]; then
            step6_status="✅ Git Commit - ${commit_hash}"
        fi
    fi

    local step7_status="⏭️  Post Comment - Skipped"
    if [ "$customized" = "false" ]; then
        step7_status="✅ Post Comment - Jira updated"
    fi

    local step8_status="⏭️  Update Status - Skipped"
    if [ "$customized" = "false" ]; then
        step8_status="✅ Update Status - DEV DONE"
    fi

    cat > "$LOG_FILE" << EOF
=== $TICKET_NO White Label Workflow Execution Log ===
Start time: $START_TIME
End time: $current_time
Branch: $current_branch
Mode: customized=$customized, test=$test_mode
Status: ✅ Success

=== Steps Executed ===
1. ✅ Start Jira Issue - Retrieved issue data
2. ✅ Transform to JSON - Generated configuration
2.5. $step2_5_status
3. ✅ Generate Code & SQL - project-tool.sh
4. $step4_status
5. $step5_status
6. $step6_status
7. $step7_status
8. $step8_status

=== Execution Details ===
• Ticket: $TICKET_NO
• Site name: $web_site_name
• Site ID: $web_site_value
• API White Label: $([ "$api_whitelabel" = "true" ] && echo "Yes" || echo "No")
• Fix Version: $fix_version
• Developer: $developer
• Customized: $([ "$customized" = "true" ] && echo "Yes" || echo "No")
• Test Mode: $([ "$test_mode" = "true" ] && echo "Yes" || echo "No")

=== Generated Files ===
• Jira JSON: $JIRA_FILE
• Config JSON: $JSON_FILE
• SQL Files: ProjectTool/result/${TICKET_NO}-*-DB-*.sql
• Java Files: src/main/java/...
$([ "$customized" = "false" ] && [ "$test_mode" = "false" ] && echo "• Release SQL: src/release_sql/${fix_version}/MyDB*.sql")
• Log: $LOG_FILE
EOF

    echo_verbose "Log file updated: $LOG_FILE"
}

# =============================================================================
# Step 1: Start Jira Issue
# =============================================================================

execute_step_1() {
    echo_critical "${BLUE}=== Step 1: Start Jira Issue ===${NC}"
    log_step 1 "Start Jira issue retrieval"

    cd "$TOOL_DIR"

    echo_verbose "Executing JiraTool start-jira-issue..."

    # Run JAR tool and capture console output for error checking
    # JAR tool will create output at: result/jira/${TICKET_NO}-jira.txt
    local console_output="/tmp/jira-console-${TICKET_NO}.txt"
    if java -cp "$JAR_FILE" tool.http.JiraTool start-jira-issue "${TICKET_NO}" ${TEST_FLAG} > "$console_output" 2>&1; then

        # Check if JAR tool created the output file
        if [ -f "$JIRA_FILE" ]; then
            # Validate JSON structure
            if jq empty "$JIRA_FILE" 2>/dev/null; then
                echo_critical "${GREEN}✅ Step 1 Success: Issue data retrieved${NC}"
                log_step 1 "✅ Success"
                echo_verbose "Jira file: $JIRA_FILE"
                rm -f "$console_output"
                return 0
            else
                echo_critical "${RED}❌ Step 1 Failed: Invalid JSON in output file${NC}"
                log_step 1 "❌ Failed (Invalid JSON)"
                echo_verbose "Check file: $JIRA_FILE"
                rm -f "$console_output"
                return 1
            fi
        else
            echo_critical "${RED}❌ Step 1 Failed: JAR tool did not create output file${NC}"
            log_step 1 "❌ Failed (No output file)"
            echo_verbose "Expected file: $JIRA_FILE"
            if [ "$QUIET_MODE" = false ] && [ -f "$console_output" ]; then
                echo_verbose "Console output:"
                cat "$console_output"
            fi
            rm -f "$console_output"
            return 1
        fi
    else
        echo_critical "${RED}❌ Step 1 Failed: JiraTool execution error${NC}"
        log_step 1 "❌ Failed (Execution error)"
        if [ "$QUIET_MODE" = false ] && [ -f "$console_output" ]; then
            echo_verbose "Error output:"
            cat "$console_output"
        fi
        rm -f "$console_output"
        return 1
    fi
}

# =============================================================================
# Cert Helper Functions
# =============================================================================

extract_cert_from_comments() {
    local jira_file="$1"
    # 從 ADF 格式的 comment body 中提取所有 text 節點
    # 然後搜索 "Cert: XXX" 格式
    jq -r '.fields.comment.comments[]?.body.content[]?.content[]?.text // empty' "$jira_file" 2>/dev/null | \
        grep -E '^Cert: [A-Za-z0-9]{16}$' | \
        head -1 | \
        sed 's/^Cert: //'
}

fetch_cert_from_random_org() {
    # 獲取 HTML 格式，提取 <pre class="data"> 後面一行的內容
    curl -s "https://www.random.org/strings/?num=1&len=16&digits=on&upperalpha=on&loweralpha=on&unique=on&format=html&rnd=new" | \
        sed -n '/<pre class="data">/,/<\/pre>/p' | \
        grep -E '^[A-Za-z0-9]{16}$' | \
        head -1 | \
        tr -d '\n\r '
}

update_webSiteValue_in_mapping_rule() {
    local new_value="$1"
    local mapping_file="$CLAUDE_MD"

    # 更新 white-label-mapping-rule.md 中的 webSiteValue
    # 格式: - **webSiteValue**: Next available value XXX (step 2 直接照抄，不需自行計算)
    if [[ "$OS_TYPE" == "macos" ]]; then
        sed -i '' "s/\(- \*\*webSiteValue\*\*: Next available value \)[0-9]*/\1${new_value}/" "$mapping_file"
    else
        sed -i "s/\(- \*\*webSiteValue\*\*: Next available value \)[0-9]*/\1${new_value}/" "$mapping_file"
    fi
}

# WebSiteType.java 路徑（重複檢查用）
get_website_type_file() {
    echo "$PROJECT_ROOT/src/main/java/com/nv/commons/code/WebSiteType.java"
}

# 找出 WebSiteType 內重複的 webSiteValue
# 取每個 enum 條目的第一個參數（site 編號），列出出現超過一次的值
# 無重複時回傳空字串
find_duplicated_webSiteValues() {
    local website_type_file=$(get_website_type_file)

    if [ ! -f "$website_type_file" ]; then
        return 0
    fi

    grep -oE '^[[:space:]]*[A-Z0-9_]+\([0-9]+, "' "$website_type_file" \
        | grep -oE '\([0-9]+' \
        | tr -d '(' \
        | sort -n \
        | uniq -d \
        | tr '\n' ' '
}

# 找出 WebSiteType 內重複的 enum 常數名（品牌名）
# 重跑同一張單會插入同名條目，Java 會編譯失敗，但 step 4 是非致命的擋不住
# 無重複時回傳空字串
find_duplicated_webSiteNames() {
    local website_type_file=$(get_website_type_file)

    if [ ! -f "$website_type_file" ]; then
        return 0
    fi

    grep -oE '^[[:space:]]*[A-Z0-9_]+\([0-9]+, "' "$website_type_file" \
        | grep -oE '[A-Z0-9_]+\(' \
        | tr -d '(' \
        | sort \
        | uniq -d \
        | tr '\n' ' '
}

# 找出 WebSiteType 內重複的 cert
# 取 certCodeSet 宣告行上的所有字串，列出出現超過一次的值
# 無重複時回傳空字串
find_duplicated_certCodes() {
    local website_type_file=$(get_website_type_file)

    if [ ! -f "$website_type_file" ]; then
        return 0
    fi

    grep -E 'certCodeSet = new' "$website_type_file" \
        | grep -oE '"[A-Za-z0-9]+"' \
        | tr -d '"' \
        | sort \
        | uniq -d \
        | tr '\n' ' '
}

# =============================================================================
# Step 2: Transform to JSON
# =============================================================================

execute_step_2() {
    echo_critical "${BLUE}=== Step 2: Transform Jira Data to JSON ===${NC}"
    log_step 2 "Start JSON transformation"

    cd "$TOOL_DIR"

    # Validate Jira file exists
    if [ ! -f "$JIRA_FILE" ]; then
        echo_critical "${RED}❌ Step 2 Failed: Jira file not found${NC}"
        log_step 2 "❌ Failed (Jira file not found)"
        return 1
    fi

    # Call Claude with embedded mapping rules and Jira data
    local claude_raw_output="/tmp/claude-raw-${TICKET_NO}.txt"
    local claude_output="/tmp/claude-output-${TICKET_NO}.json"

    # === 預處理 Cert（僅限 API 白標） ===
    local pre_cert=""
    local is_api_whitelabel=$(jq -r '.fields.summary' "$JIRA_FILE" | grep -qE '\[ApiWallet\]\[(TransferWallet|SingleWallet)\]' && echo "true" || echo "false")

    if [ "$is_api_whitelabel" = "true" ]; then
        echo_verbose "Detected API white label, checking for Cert..."

        # Step 1: 嘗試從 comments 提取
        pre_cert=$(extract_cert_from_comments "$JIRA_FILE")

        if [ -n "$pre_cert" ]; then
            echo_verbose "Found Cert in comments: $pre_cert"
        else
            echo_verbose "No Cert in comments, fetching from RANDOM.ORG..."
            pre_cert=$(fetch_cert_from_random_org)

            if [ -n "$pre_cert" ]; then
                echo_verbose "Generated Cert from RANDOM.ORG: $pre_cert"
            else
                echo_critical "${RED}❌ Failed to fetch Cert from RANDOM.ORG${NC}"
                log_step 2 "❌ Failed (Could not fetch cert from RANDOM.ORG)"
                return 1
            fi
        fi
    fi

    echo_verbose "Calling Claude CLI for transformation..."

    # 如果有預處理的 cert，加入 prompt
    local cert_instruction=""
    if [ -n "$pre_cert" ]; then
        cert_instruction="
IMPORTANT: The Cert code has been pre-determined: ${pre_cert}
Use this exact value for apiWalletInfo.cert field. Do NOT generate a new cert."
    fi

    # Create directive prompt with embedded rules
    local transform_prompt="You are a data transformation tool. Your ONLY task is to transform Jira JSON to white label configuration JSON.

DO NOT respond conversationally. DO NOT acknowledge. DO NOT explain.
OUTPUT ONLY THE JSON OBJECT. Nothing else.
${cert_instruction}

MAPPING RULES FROM white-label-mapping-rule.md:
$(cat "$CLAUDE_MD")

INPUT JIRA DATA:
\`\`\`json
$(cat "$JIRA_FILE")
\`\`\`

CUSTOMIZED MODE: $CUSTOMIZED_MODE

TASK: Transform the Jira JSON above into white label configuration JSON following the Core Mapping Rules.
OUTPUT: Valid JSON only. No markdown. No explanations. Just the JSON object."

    # Call Claude with the prompt
    if [ "$QUIET_MODE" = true ]; then
        echo "$transform_prompt" | claude 2>/dev/null > "$claude_raw_output"
    else
        echo "$transform_prompt" | claude > "$claude_raw_output"
    fi

    # Try multiple extraction methods
    # Method 1: Extract from markdown code block (```json ... ```)
    if grep -q '```json' "$claude_raw_output"; then
        sed -n '/```json/,/```/p' "$claude_raw_output" | sed '1d;$d' > "$claude_output"
    # Method 2: Extract from code block (``` ... ```)
    elif grep -q '```' "$claude_raw_output"; then
        sed -n '/```/,/```/p' "$claude_raw_output" | sed '1d;$d' > "$claude_output"
    # Method 3: Extract lines between first { and last }
    else
        sed -n '/^{/,/^}$/p' "$claude_raw_output" > "$claude_output"
    fi

    # If extraction failed, try to find any JSON-like content
    if [ ! -s "$claude_output" ] || ! jq empty "$claude_output" 2>/dev/null; then
        echo_verbose "Primary extraction failed, trying alternative method..."
        # Extract everything between first { and last }
        awk '/\{/,/\}/' "$claude_raw_output" > "$claude_output"
    fi

    # Validate and format JSON
    if [ -s "$claude_output" ] && jq empty "$claude_output" 2>/dev/null; then
        jq . "$claude_output" > "$JSON_FILE"

        # Verify JSON has required fields
        local web_site_name=$(jq -r '.webSiteName // empty' "$JSON_FILE")
        if [ -z "$web_site_name" ]; then
            echo_critical "${RED}❌ Step 2 Failed: Generated JSON missing required fields${NC}"
            log_step 2 "❌ Failed (Missing required fields)"
            echo_verbose "Claude raw output:"
            if [ "$QUIET_MODE" = false ]; then
                cat "$claude_raw_output"
            fi
            rm -f "$claude_output" "$claude_raw_output"
            return 1
        fi

        # Get API white label status for display
        local api_whitelabel=$(jq -r '.apiWhiteLabel' "$JSON_FILE")

        echo_critical "${GREEN}✅ Step 2 Success: JSON generated${NC}"
        log_step 2 "✅ Success"

        # Display key fields for verification
        if [ "$QUIET_MODE" = false ]; then
            echo_verbose "Generated JSON:"
            echo_verbose "  - webSiteName: $(jq -r '.webSiteName' "$JSON_FILE")"
            echo_verbose "  - webSiteValue: $(jq -r '.webSiteValue' "$JSON_FILE")"
            echo_verbose "  - apiWhiteLabel: $(jq -r '.apiWhiteLabel' "$JSON_FILE")"
            echo_verbose "  - fixVersion: $(jq -r '.fixVersion' "$JSON_FILE")"
            if [ "$api_whitelabel" = "true" ]; then
                echo_verbose "  - cert: $(jq -r '.apiWalletInfo.cert' "$JSON_FILE")"
            fi
        fi

        rm -f "$claude_output" "$claude_raw_output"

        # Move JSON to correct subdirectory based on type
        SUBDIR=$(resolve_subdir "$JSON_FILE")
        local new_json="$TOOL_DIR/sample/$SUBDIR/${TICKET_NO}.json"
        mv "$JSON_FILE" "$new_json"
        JSON_FILE="$new_json"

        return 0
    else
        echo_critical "${RED}❌ Step 2 Failed: Invalid JSON from Claude${NC}"
        log_step 2 "❌ Failed (Invalid JSON from Claude)"
        echo_verbose "Claude raw output:"
        if [ "$QUIET_MODE" = false ] && [ -f "$claude_raw_output" ]; then
            cat "$claude_raw_output"
        fi
        echo_verbose "Extracted output:"
        if [ "$QUIET_MODE" = false ] && [ -f "$claude_output" ]; then
            cat "$claude_output"
        fi
        rm -f "$claude_output" "$claude_raw_output"
        return 1
    fi
}

# =============================================================================
# Step 2.5: Fill groupInfo from the API 2.0 domain spreadsheet
# =============================================================================
# 只在 newGroup=true 時執行。以前這四個欄位（privateIpSetId / bkIpSetId /
# apiInfoBkIpSetId / backup）是 step 2 依 mapping rule 產出字面假值、再由人工從
# task/api-2.0-group-info.md 複製真值進來，漏掉時 step 3 與 step 4 都會報成功。
#
# 不受 -t / -c 影響：-t 的語義是「不動 Jira」、-c 是「跳過 DB/git/Jira 收尾」，
# 兩者都不該讓 SQL 產出變得不正確。

execute_step_2_5() {
    # 「單子 JSON 必須存在」是 step 3 的契約，不是這一步的。檔案不在就安靜跳過，
    # 讓 step 3 去報它原本就會報的錯，否則 newGroup=false 的單會看到莫名其妙的
    # 「Step 2.5 Failed」——那一步對它根本不適用
    #
    # newGroup 不是 true 就整步跳過（一般白牌沒有群組要建）
    local new_group=$(jq -r '.apiWalletInfo.newGroup // false' "$JSON_FILE" 2>/dev/null)
    if [ "$new_group" != "true" ]; then
        return 0
    fi

    echo_critical "${BLUE}=== Step 2.5: Fill groupInfo from spreadsheet ===${NC}"
    log_step 2.5 "Start groupInfo lookup"

    # 群組代號是查表的鍵，Jira 單必須明確寫出來
    local group=$(jq -r '.apiWalletInfo.group // empty' "$JSON_FILE" 2>/dev/null)
    if [ -z "$group" ]; then
        echo_critical "${RED}❌ Step 2.5 Failed: newGroup 為 true 但沒有群組代號${NC}"
        echo_critical "${RED}   Jira 單的描述必須明確指出 API 2.0 群組代號（例如 A69）${NC}"
        echo_critical "${RED}   確認描述後重新執行 -s 2${NC}"
        log_step 2.5 "❌ Failed (missing apiWalletInfo.group)"
        return 1
    fi

    echo_verbose "Looking up group ${group} in the API 2.0 domain spreadsheet..."

    local gi_out="/tmp/groupinfo-${TICKET_NO}.json"
    local gi_err="/tmp/groupinfo-${TICKET_NO}.txt"

    # -DprojectTool.configDir 明講設定檔在哪：sheet-config.json 與憑證的預設搜尋路徑
    # 是 CWD 相對的，而這一步跑在 step 3 的 cd "$TOOL_DIR" 之前，不能靠 CWD
    #
    # stdout 與 stderr 必須分開接：狀態訊息走 stderr，合併會污染 JSON
    if java -DprojectTool.configDir="$TOOL_DIR/config" -cp "$JAR_FILE" \
            tool.sheet.SheetTool group-info "$group" \
            --patch "$JSON_FILE" > "$gi_out" 2> "$gi_err"; then
        # 填入與未變動的明細、以及衝突警告都在 stderr，原樣轉出來
        if [ "$QUIET_MODE" = false ] && [ -f "$gi_err" ]; then
            cat "$gi_err"
        fi
        echo_critical "${GREEN}✅ Step 2.5 Success: groupInfo resolved for ${group}${NC}"
        log_step 2.5 "✅ Success (group ${group})"
        rm -f "$gi_out" "$gi_err"
        return 0
    else
        # 先放工具的訊息再放結論，讀起來才是事情發生的順序
        if [ -f "$gi_err" ]; then
            cat "$gi_err"
        fi
        echo_critical "${RED}❌ Step 2.5 Failed: 無法取得群組 ${group} 的 groupInfo${NC}"
        echo_critical "${RED}   常見原因: 憑證未設定、試算表未共用給 service account、${NC}"
        echo_critical "${RED}             群組代號不存在、或試算表的值不合格式${NC}"
        echo_critical "${RED}   單子 JSON 未被修改，修正後重新執行 -s 3 是安全的${NC}"
        log_step 2.5 "❌ Failed (group ${group})"
        rm -f "$gi_out" "$gi_err"
        return 1
    fi
}

# =============================================================================
# Step 3: Generate Code & SQL
# =============================================================================

execute_step_3() {
    echo_critical "${BLUE}=== Step 3: Generate Code & SQL ===${NC}"
    log_step 3 "Start code generation"

    cd "$TOOL_DIR"

    # Verify JSON file exists
    if [ ! -f "$JSON_FILE" ]; then
        echo_critical "${RED}❌ Step 3 Failed: JSON file not found${NC}"
        log_step 3 "❌ Failed (JSON file not found)"
        return 1
    fi

    echo_verbose "Executing project-tool.sh..."

    # Execute project-tool.sh
    if [ "$QUIET_MODE" = true ]; then
        "$SCRIPT_DIR/project-tool.sh" A "sample/$SUBDIR/${TICKET_NO}.json" > /dev/null 2>&1
    else
        "$SCRIPT_DIR/project-tool.sh" A "sample/$SUBDIR/${TICKET_NO}.json"
    fi

    if [ $? -eq 0 ]; then
        echo_critical "${GREEN}✅ Step 3 Success: Code & SQL generated${NC}"
        log_step 3 "✅ Success"

        # 產出後檢查: WebSiteType 內不得有重複的 webSiteValue、品牌名或 cert
        local duplicated_values=$(find_duplicated_webSiteValues)
        local duplicated_names=$(find_duplicated_webSiteNames)
        local duplicated_certs=$(find_duplicated_certCodes)
        if [ -n "$duplicated_values" ] || [ -n "$duplicated_names" ] || [ -n "$duplicated_certs" ]; then
            if [ -n "$duplicated_values" ]; then
                echo_critical "${RED}❌ Step 3 Failed: WebSiteType 有重複的 webSiteValue: ${duplicated_values}${NC}"
            fi
            if [ -n "$duplicated_names" ]; then
                echo_critical "${RED}❌ Step 3 Failed: WebSiteType 有重複的品牌名: ${duplicated_names}${NC}"
            fi
            if [ -n "$duplicated_certs" ]; then
                echo_critical "${RED}❌ Step 3 Failed: WebSiteType 有重複的 cert: ${duplicated_certs}${NC}"
            fi
            echo_critical "${RED}   本次產出的檔案已寫入磁碟，不可直接以 -s 3 重跑（會再插入一筆）${NC}"
            echo_critical "${RED}   復原步驟:${NC}"
            echo_critical "${RED}     1. git checkout src/ 還原本次產出，或手動移除新增的 enum 區塊${NC}"
            echo_critical "${RED}     2. 修正 sample/${SUBDIR}/${TICKET_NO}.json 的 webSiteValue / cert${NC}"
            echo_critical "${RED}     3. 重新執行 -s 3${NC}"
            log_step 3 "❌ Failed (duplicated webSiteValue: ${duplicated_values:-none}, name: ${duplicated_names:-none}, cert: ${duplicated_certs:-none})"
            return 1
        fi

        # 更新 mapping rule 中的 webSiteValue（寫回「下一個可用編號」= 本次用掉的 + 1）
        local current_value=$(read_json_field "webSiteValue")
        if [ -n "$current_value" ] && [[ "$current_value" =~ ^[0-9]+$ ]]; then
            local next_value=$((current_value + 1))
            update_webSiteValue_in_mapping_rule "$next_value"
            echo_verbose "Updated webSiteValue in mapping rule: used $current_value → next available $next_value"
        fi

        return 0
    else
        echo_critical "${RED}❌ Step 3 Failed: project-tool.sh execution failed${NC}"
        log_step 3 "❌ Failed"
        return 1
    fi
}

# =============================================================================
# Step 4: Compile & Test
# =============================================================================

execute_step_4() {
    echo_critical "${BLUE}=== Step 4: Compile & Test ===${NC}"
    log_step 4 "Start build check"

    cd "$PROJECT_ROOT"

    configure_java8

    local max_retry=3
    local retry_count=0

    while [ $retry_count -lt $max_retry ]; do
        echo_verbose "Compiling (attempt $((retry_count + 1))/$max_retry)"

        # Execute compile and capture output
        if mvn test-compile > /tmp/build_${TICKET_NO}.log 2>&1; then
            echo_critical "${GREEN}✅ Step 4 Success: Build passed${NC}"
            log_step 4 "✅ Success"
            rm -f /tmp/build_${TICKET_NO}.log
            return 0
        else
            echo_verbose "${YELLOW}⚠️  Build failed, checking error log...${NC}"

            # Check for import errors
            if grep -q "cannot find symbol.*class\|package.*does not exist" /tmp/build_${TICKET_NO}.log; then
                echo_verbose "${YELLOW}Detected import errors${NC}"

                if [ "$QUIET_MODE" = false ]; then
                    echo_verbose "Error details:"
                    grep "cannot find symbol.*class\|package.*does not exist" /tmp/build_${TICKET_NO}.log | head -10
                fi

                retry_count=$((retry_count + 1))
            else
                echo_critical "${RED}❌ Build failed (non-import error)${NC}"
                log_step 4 "❌ Failed (non-import error)"
                if [ "$QUIET_MODE" = false ]; then
                    echo_verbose "Error details:"
                    tail -20 /tmp/build_${TICKET_NO}.log
                fi
                return 1
            fi
        fi
    done

    echo_critical "${YELLOW}⚠️  Step 4 Warning: Build failed after $max_retry attempts${NC}"
    log_step 4 "⚠️  Warning (manual fixes needed)"
    echo_verbose "Manual import fixes may be required"
    return 1
}

# =============================================================================
# Step 5: Execute DEV SQL
# =============================================================================

execute_step_5() {
    echo_critical "${BLUE}=== Step 5: Execute DEV SQL ===${NC}"
    log_step 5 "Start SQL processing"

    # Check customized or test mode
    if [ "$CUSTOMIZED_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Customized mode: Skipping step 5${NC}"
        log_step 5 "⏭️  Skipped (customized)"
        return 0
    fi

    if [ "$TEST_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Test mode: Skipping step 5${NC}"
        log_step 5 "⏭️  Skipped (test mode)"
        return 0
    fi

    cd "$TOOL_DIR"

    echo_verbose "Executing SQL-processing.sh..."

    # Execute SQL-processing.sh
    if [ "$QUIET_MODE" = true ]; then
        "$SCRIPT_DIR/SQL-processing.sh" "$SUBDIR/${TICKET_NO}.json" > /dev/null 2>&1
    else
        "$SCRIPT_DIR/SQL-processing.sh" "$SUBDIR/${TICKET_NO}.json"
    fi

    if [ $? -eq 0 ]; then
        log_step 5 "✅ Success"
        echo_critical "${GREEN}✅ Step 5 Success: SQL processed & executed${NC}"
        return 0
    else
        log_step 5 "❌ Failed"
        echo_critical "${RED}❌ Step 5 Failed: SQL processing failed${NC}"

        # In non-quiet mode, ask whether to continue
        if [ "$QUIET_MODE" = false ]; then
            read -p "Continue to step 6 (git commit)? (y/n): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                return 1
            fi
        fi
        return 0
    fi
}

# =============================================================================
# Step 6: Git Commit
# =============================================================================

execute_step_6() {
    echo_critical "${BLUE}=== Step 6: Git Commit ===${NC}"
    log_step 6 "Start git commit"

    # Check customized or test mode
    if [ "$CUSTOMIZED_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Customized mode: Skipping git commit${NC}"
        log_step 6 "⏭️  Skipped (customized)"
        return 0
    fi

    if [ "$TEST_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Test mode: Skipping git commit${NC}"
        log_step 6 "⏭️  Skipped (test mode)"
        return 0
    fi

    cd "$PROJECT_ROOT"

    # Read JSON for commit message
    local api_whitelabel=$(read_json_field "apiWhiteLabel")
    local web_site_name=$(read_json_field "webSiteName")

    # Determine commit message format
    local commit_msg
    if [ "$api_whitelabel" = "true" ]; then
        # apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有（read_json_field 會回 "null"）
        # → 一律視為 TransferWallet，維持原本行為
        local api_wallet_type=$(read_json_field "apiWalletType")
        if [ "$api_wallet_type" = "Single" ]; then
            commit_msg="[${TICKET_NO}][ApiWallet][SingleWallet] ${web_site_name}"
        else
            commit_msg="[${TICKET_NO}][ApiWallet][TransferWallet] ${web_site_name}"
        fi
    else
        commit_msg="[${TICKET_NO}] new Site ${web_site_name}"
    fi

    echo_verbose "Commit message: $commit_msg"

    # Stage files
    # 只 stage src/：ProjectTool 已不在 citixchange 的版控內（由 zip 提供），
    # 把它留在 pathspec 裡會讓 git add 回非零，而下面檢查的是 git commit 的退出碼，
    # 於是失敗會被靜靜吞掉
    if [ "$QUIET_MODE" = true ]; then
        git add src/ > /dev/null 2>&1
    else
        git add src/
    fi

    # Commit
    if [ "$QUIET_MODE" = true ]; then
        git commit -m "$commit_msg" > /dev/null 2>&1
    else
        git commit -m "$commit_msg"
    fi

    if [ $? -eq 0 ]; then
        local commit_hash=$(git rev-parse --short HEAD)
        log_step 6 "✅ Success (${commit_hash})"
        echo_critical "${GREEN}✅ Step 6 Success: Git committed (${commit_hash})${NC}"
        return 0
    else
        log_step 6 "❌ Failed"
        echo_critical "${RED}❌ Step 6 Failed: Git commit failed${NC}"
        return 1
    fi
}

# =============================================================================
# Step 7: Post Comment
# =============================================================================

get_comment_msg() {
    local api_whitelabel=$(read_json_field "apiWhiteLabel")
    local web_site_name=$(read_json_field "webSiteName")
    local web_site_value=$(read_json_field "webSiteValue")
    local cert=$(read_json_field "apiWalletInfo.cert")

    if [ "$api_whitelabel" = "true" ] && [ -n "$cert" ] && [ "$cert" != "null" ]; then
        # Use literal \n for newline in JSON body
        echo "Site: ${web_site_name} (${web_site_value})\nCert: ${cert}"
    else
        echo "Site: ${web_site_name} (${web_site_value})"
    fi
}

execute_step_7() {
    echo_critical "${BLUE}=== Step 7: Post Comment to Jira ===${NC}"
    log_step 7 "Start post comment"

    # Check customized mode
    if [ "$CUSTOMIZED_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Customized mode: Skipping Jira comment${NC}"
        log_step 7 "⏭️  Skipped (customized)"
        return 0
    fi

    cd "$TOOL_DIR"

    # Generate comment message
    local COMMENT_MSG=$(get_comment_msg)
    echo_verbose "Comment: $COMMENT_MSG"

    if [ "$TEST_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Test mode: Skipping Jira comment${NC}"
        log_step 7 "⏭️  Skipped (test mode)"
        return 0
    fi

    # Post comment via JAR tool - capture output for error diagnosis
    local jar_output="/tmp/jira-comment-${TICKET_NO}.txt"

    if java -cp "$JAR_FILE" tool.http.JiraTool post-comment "${TICKET_NO}" "$COMMENT_MSG" ${TEST_FLAG} > "$jar_output" 2>&1; then
        log_step 7 "✅ Success"
        echo_critical "${GREEN}✅ Step 7 Success: Comment posted${NC}"
        rm -f "$jar_output"
        return 0
    else
        log_step 7 "⚠️  Warning (manual required)"
        echo_critical "${YELLOW}⚠️  Step 7 Warning: Comment posting failed${NC}"
        echo_verbose "Manual action required: Add comment to ${TICKET_NO}"
        echo_verbose "Comment text: ${COMMENT_MSG}"

        # Display JAR error output in non-quiet mode
        if [ "$QUIET_MODE" = false ] && [ -f "$jar_output" ]; then
            echo_verbose "JAR error output:"
            cat "$jar_output"
        fi

        rm -f "$jar_output"
        return 0  # Non-fatal
    fi
}

# =============================================================================
# Step 8: Update Status
# =============================================================================

execute_step_8() {
    echo_critical "${BLUE}=== Step 8: Update Jira Status to DEV DONE ===${NC}"
    log_step 8 "Start status update"

    # Check customized mode
    if [ "$CUSTOMIZED_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Customized mode: Skipping Jira status update${NC}"
        log_step 8 "⏭️  Skipped (customized)"
        return 0
    fi

    if [ "$TEST_MODE" = true ]; then
        echo_critical "${YELLOW}⚠️  Test mode: Skipping Jira status update${NC}"
        log_step 8 "⏭️  Skipped (test mode)"
        return 0
    fi

    cd "$TOOL_DIR"

    # Execute transition via JAR tool - capture output for error diagnosis
    local jar_output="/tmp/jira-transition-${TICKET_NO}.txt"

    if java -cp "$JAR_FILE" tool.http.JiraTool transition-issue "${TICKET_NO}" "DEV_DONE" ${TEST_FLAG} > "$jar_output" 2>&1; then
        log_step 8 "✅ Success"
        echo_critical "${GREEN}✅ Step 8 Success: Status updated to DEV DONE${NC}"
        rm -f "$jar_output"
        return 0
    else
        log_step 8 "⚠️  Warning (manual required)"
        echo_critical "${YELLOW}⚠️  Step 8 Warning: Status transition failed${NC}"
        echo_verbose "Manual action required: Transition ${TICKET_NO} to DEV DONE"
        display_manual_instructions

        # Display JAR error output in non-quiet mode
        if [ "$QUIET_MODE" = false ] && [ -f "$jar_output" ]; then
            echo_verbose "JAR error output:"
            cat "$jar_output"
        fi

        rm -f "$jar_output"
        return 0  # Non-fatal
    fi
}

# =============================================================================
# Display Manual Instructions
# =============================================================================

display_manual_instructions() {
    local COMMENT_MSG=$(get_comment_msg)

    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}⚠️  Manual Jira Update Required${NC}"
    echo -e "${YELLOW}========================================${NC}"
    echo "📋 Ticket: ${TICKET_NO}"
    echo "🔄 Transition: IN DEV → DEV DONE"
    echo ""
    echo "💬 Comment to add (if not added yet):"
    echo "   ${COMMENT_MSG//$'\n'/$'\n   '}"
    echo ""
    echo -e "${BLUE}📝 Steps:${NC}"
    echo "   1. Open Jira ticket: ${TICKET_NO}"
    echo "   2. Click 'Transition'"
    echo "   3. Select 'DEV DONE'"
    if [ "$CUSTOMIZED_MODE" = false ]; then
        echo "   4. Add above comment (if missing)"
    fi
    echo -e "${YELLOW}========================================${NC}"
}

# =============================================================================
# Completion Summary
# =============================================================================

display_completion_summary() {
    local end_time=$(date '+%Y-%m-%d %H:%M:%S')

    echo_verbose ""
    echo_critical "${GREEN}========================================${NC}"
    echo_critical "${GREEN}✅ Workflow Complete${NC}"
    echo_critical "${GREEN}========================================${NC}"
    echo_verbose "Ticket: $TICKET_NO"
    echo_verbose "Start time: $START_TIME"
    echo_verbose "End time: $end_time"
    echo_verbose "Log file: $LOG_FILE"
    echo_verbose ""
}

# =============================================================================
# Main Execution Flow
# =============================================================================

main() {
    local overall_status=0

    # Display configuration at the very beginning
    echo_critical "${BLUE}========================================${NC}"
    echo_critical "${BLUE}Configuration${NC}"
    echo_critical "${BLUE}========================================${NC}"
    echo_verbose "Ticket: $TICKET_NO"
    echo_verbose "Start time: $START_TIME"
    echo_verbose ""
    echo_verbose "Mode Flags:"
    echo_verbose "  - TEST_MODE: $TEST_MODE"
    echo_verbose "  - CUSTOMIZED_MODE: $CUSTOMIZED_MODE"
    echo_verbose "  - QUIET_MODE: $QUIET_MODE"
    echo_verbose "  - FROM_STEP: $FROM_STEP"
    echo_verbose ""
    echo_verbose "Directories:"
    echo_verbose "  - SCRIPT_DIR: $SCRIPT_DIR"
    echo_verbose "  - TOOL_DIR: $TOOL_DIR"
    echo_verbose "  - PROJECT_ROOT: $PROJECT_ROOT"
    echo_verbose ""
    echo_verbose "File Paths:"
    echo_verbose "  - JAR_FILE: $JAR_FILE"
    echo_verbose "  - JIRA_FILE: $JIRA_FILE"
    echo_verbose "  - JSON_FILE: $JSON_FILE"
    echo_verbose "  - LOG_FILE: $LOG_FILE"
    echo_verbose "  - CLAUDE_MD: $CLAUDE_MD"
    echo_verbose ""

    # Pre-flight checks
    check_dependencies || {
        echo_critical "${RED}❌ Dependency check failed${NC}"
        exit 1
    }

    # Show starting step if not from beginning
    if [ "$FROM_STEP" -gt 1 ]; then
        echo_critical "${YELLOW}⚠️  Starting from step ${FROM_STEP} (skipping steps 1-$((FROM_STEP-1)))${NC}"
    fi

    # CRITICAL STEPS (fail fast)
    if [ "$FROM_STEP" -le 1 ]; then
        execute_step_1 || exit 1
    fi
    if [ "$FROM_STEP" -le 2 ]; then
        execute_step_2 || exit 1
    fi
    # 用 -le 3 而非 -le 2：-s 3 重跑也要重新確認 groupInfo 已填
    if [ "$FROM_STEP" -le 3 ]; then
        execute_step_2_5 || exit 1
    fi
    if [ "$FROM_STEP" -le 3 ]; then
        execute_step_3 || exit 1
    fi

    # PARTIAL FAILURE OK
    if [ "$FROM_STEP" -le 4 ]; then
        execute_step_4 || echo_verbose "${YELLOW}⚠️  Step 4 failed (continuing)${NC}"
    fi

    # OPTIONAL (exit on critical failure, skip if customized)
    if [ "$FROM_STEP" -le 5 ]; then
        execute_step_5 || exit 1
    fi

    # Update log file BEFORE git commit so it gets included
    update_final_log

    if [ "$FROM_STEP" -le 6 ]; then
        execute_step_6 || exit 1
    fi

    # JIRA UPDATES (non-fatal, skip if customized)
    if [ "$FROM_STEP" -le 7 ]; then
        execute_step_7 || overall_status=1
    fi
    if [ "$FROM_STEP" -le 8 ]; then
        execute_step_8 || overall_status=1
    fi

    display_completion_summary
    return $overall_status
}

# =============================================================================
# Execute Main Flow
# =============================================================================

main
exit $?
