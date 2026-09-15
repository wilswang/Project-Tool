#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOL_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$TOOL_DIR"

TARGET_DIR="$TOOL_DIR/result"

# 檢查是否存在，否則建立
if [ ! -d "$TARGET_DIR" ]; then
  echo "📁 建立資料夾 $TARGET_DIR"
  mkdir -p "$TARGET_DIR"
fi
JAR_FILE="$TOOL_DIR/Project-Tool.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "❌ JAR 檔案不存在，請先執行 'mvn package'"
  exit 1
fi

# 執行工具
java -jar "$JAR_FILE" "$@"