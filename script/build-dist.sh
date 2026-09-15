#!/bin/bash
#
# 產生可直接解壓到 citixchange 專案底下使用的 ProjectTool 壓縮檔。
#
# 用法：
#   ./script/build-dist.sh            # 用 target/ 裡現成的 jar
#   ./script/build-dist.sh --package  # 先跑 mvn package 再打包
#
# 設計上的兩個硬性原則：
#
# 1. 白名單逐項複製，不用 `zip -r`。部署端的工作目錄裡有真實的 service account 金鑰、
#    填了 token 的 application.properties、以及指向 ~/Documents/workspace/agent-output/
#    的 symlink（sample、log、result 底下四個）。靠 exclude 清單擋這些，
#    只要哪天多一個檔案沒寫進清單就會外流；白名單則是預設不放行。
#
# 2. 打包前對 staging 目錄做安全檢查，違反就中止。白名單是第一道防線，
#    這是第二道 —— 萬一來源檔本身被污染（例如有人把 token 填進了 repo 根目錄那份
#    application.properties），白名單擋不住，但這裡擋得住。
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

OUT_DIR="$REPO_ROOT/dist-out"
STAGE="$OUT_DIR/.stage"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

die() {
    echo -e "${RED}❌ $*${NC}" >&2
    exit 1
}

# ---------------------------------------------------------------------------
# 版本
# ---------------------------------------------------------------------------

VERSION="$(python3 - <<'PY'
import xml.etree.ElementTree as ET
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse('pom.xml').getroot()
# 專案自己的 version，不是任何 dependency 的
version = root.find('m:version', ns)
print(version.text.strip() if version is not None else '')
PY
)"
[ -n "$VERSION" ] || die "從 pom.xml 讀不到專案版本"

JAR_SRC="target/Project-Tool-${VERSION}-jar-with-dependencies.jar"

if [ "${1:-}" = "--package" ]; then
    echo -e "${BLUE}=== mvn package ===${NC}"
    mvn -q -DskipTests package
fi

# jar 的版本必須與 pom 一致 —— 否則會包到上一版的 jar 而完全沒有徵兆
[ -f "$JAR_SRC" ] || die "找不到 $JAR_SRC。先跑 mvn package，或用 ./script/build-dist.sh --package"

# ---------------------------------------------------------------------------
# Staging：白名單
# ---------------------------------------------------------------------------

echo -e "${BLUE}=== 組裝 ProjectTool v${VERSION} ===${NC}"

rm -rf "$STAGE"
mkdir -p "$STAGE/ProjectTool"/{config,template,script/unix,script/windows,task}
P="$STAGE/ProjectTool"

copy() {
    local src="$1" dest="$2"
    [ -e "$src" ] || die "缺少來源檔: $src"
    cp -R "$src" "$dest"
}

# jar 與版本標記
copy "$JAR_SRC"                     "$P/Project-Tool.jar"
echo "$VERSION" > "$P/VERSION"

# 根目錄檔案。application.properties 取 repo 這份（帳密欄位為空的範本），
# 絕對不是部署端那份填了值的
copy "application.properties"       "$P/application.properties"
copy "dist/README.md"               "$P/README.md"
copy "dist/ProjectTool.gitignore"   "$P/.gitignore"

# 設定檔。service-account.sample.json 是範本（只有欄位名與 <REPLACE_ME>），要放；
# 真正的 service-account.json 不在 src/config/ 裡，所以不可能被帶進來
copy "src/config/env-values.json"              "$P/config/"
copy "src/config/sheet-config.json"            "$P/config/"
copy "src/config/service-account.sample.json"  "$P/config/"

# 模板（白牌 + jira）
copy "src/template/." "$P/template/"

# 腳本：啟動器在 src/main/scripts，白牌編排在 dist/script
copy "src/main/scripts/project-tool.sh"         "$P/script/unix/"
copy "dist/script/unix/white-label-process.sh"  "$P/script/unix/"
copy "dist/script/unix/SQL-processing.sh"       "$P/script/unix/"
copy "src/main/scripts/project-tool.bat"           "$P/script/windows/"
copy "dist/script/windows/white-label-process.ps1" "$P/script/windows/"
copy "dist/script/windows/SQL-processing.bat"      "$P/script/windows/"

# mapping rule。裡面的 webSiteValue 只是打包當下的快照 ——
# 正確號碼請用 citixchange 的 TestWebSiteTypeRegistry 查
copy "dist/task/white-label-mapping-rule.md" "$P/task/"

# 刻意不放：
#   docs/          給要改工具的人看的，去 repo 看即可
#   sample/ log/ result/   產出目錄，而且部署端那三個是指向 agent-output 的 symlink

# ---------------------------------------------------------------------------
# 行尾與權限
# ---------------------------------------------------------------------------

# citixchange 的 .gitattributes（*.sh text eol=lf）在脫離版控後不再適用，
# CRLF 的 .sh 在 Linux / WSL 會得到 /bin/bash^M: bad interpreter
find "$P" -name '*.sh' -print0 | while IFS= read -r -d '' f; do
    perl -i -pe 's/\r\n/\n/g' "$f"
    chmod +x "$f"
done

# ---------------------------------------------------------------------------
# 安全檢查：違反就中止，不產生壓縮檔
# ---------------------------------------------------------------------------

echo -e "${BLUE}=== 安全檢查 ===${NC}"

leaked=$(find "$P" -name '*service-account*.json' ! -name '*.sample.json' || true)
if [ -n "$leaked" ]; then
    die "staging 裡出現真實的 service account 金鑰:\n$leaked"
fi

for key in jira.account jira.token; do
    value=$(grep -E "^${key}=" "$P/application.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]' || true)
    if [ -n "$value" ]; then
        die "application.properties 的 ${key} 不是空的（長度 ${#value}）。壓縮檔只能放空值範本。"
    fi
done

for forbidden in sample log result docs; do
    if [ -e "$P/$forbidden" ]; then
        die "staging 裡不該出現 $forbidden/"
    fi
done

if find "$P" -type l | grep -q .; then
    die "staging 裡有 symlink:\n$(find "$P" -type l)"
fi

echo -e "${GREEN}✅ 無憑證、無產出目錄、無 symlink${NC}"

# ---------------------------------------------------------------------------
# 壓縮
# ---------------------------------------------------------------------------

ZIP="$OUT_DIR/ProjectTool-v${VERSION}.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -q -r "$ZIP" ProjectTool)
rm -rf "$STAGE"

file_count=$(unzip -l "$ZIP" | tail -1 | awk '{print $2}')
size=$(du -h "$ZIP" | cut -f1)

echo
echo -e "${GREEN}✅ $ZIP${NC}"
echo "   版本 ${VERSION}、${file_count} 個檔案、${size}"
echo
echo -e "${YELLOW}給使用者的一句話：解壓到 citixchange 專案根目錄底下，"
echo -e "資料夾名稱保持 ProjectTool，然後照 README 填自己的 Jira token。${NC}"
