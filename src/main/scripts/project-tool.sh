#!/bin/bash
 cd "$(dirname "$0")"

TARGET_DIR="./result"

# 檢查是否存在，否則建立
if [ ! -d "$TARGET_DIR" ]; then
  echo "📁 建立資料夾 $TARGET_DIR"
  mkdir -p "$TARGET_DIR"
fi
JAR_FILE="./Project-Tool-1.0.4-jar-with-dependencies.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "❌ JAR 檔案不存在，請先執行 'mvn package'"
  exit 1
fi

# 執行工具
java -jar "$JAR_FILE" "$@"