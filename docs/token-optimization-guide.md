# Token 優化執行指引

## 版本資訊
- **版本**: 1.0
- **日期**: 2025-11-18
- **目標**: 將單次白牌流程的 token 消耗從 ~21K 降低至 ~9K（節省 57%）

---

## 優化策略總覽

| 方案 | 描述 | Token 節省 | 狀態 |
|------|------|-----------|------|
| 方案 1B | BashOutput 過濾優化 | ~4,000 | ✅ 已實施 |
| 方案 2 | Jira API 欄位過濾 | ~3,000 | ✅ 已實施 |
| 方案 3 | 精簡 Slash Command | ~2,500 | ✅ 已實施 |
| 方案 4 | Todo 管理優化 | ~1,500 | ✅ 已實施 |
| **總計** | - | **~11,000** | - |

---

## 方案詳細說明

### 方案 1B：BashOutput 過濾優化

**問題**：讀取完整的腳本輸出（包含編譯日誌、SQL 詳情）消耗大量 tokens

**解決方案**：使用 `filter` 參數只讀取關鍵狀態行

```javascript
// 執行 white-label-process.sh 時
BashOutput(bash_id, filter: "^(\[0;3[0-9]m)?===|✅|❌|⚠️|完成")
```

**實施位置**：
- `/whiteLabel-run`：監控腳本執行進度時
- `/whiteLabel-finish`：監控整合腳本時

**預期效果**：
- 過濾掉 70% 的輸出內容
- 保留所有關鍵狀態和錯誤信息
- 節省 ~4,000 tokens

---

### 方案 2：Jira API 欄位過濾

**問題**：`getJiraIssue` 返回 50+ 個欄位，大部分未使用

**解決方案**：使用 `fields` 參數只請求必要欄位

```javascript
// 步驟 4：獲取 ticket 資訊
mcp__Atlassian__getJiraIssue({
  cloudId: "...",
  issueIdOrKey: "SACRIC-XXX",
  fields: [
    "summary",           // jiraSummary
    "description",       // host, webSiteName, supportInfo*
    "status",            // 狀態檢查
    "fixVersions",       // fixVersion 映射
    "customfield_10037", // developer
    "comment"            // API 白牌的 cert
  ]
})

// 步驟 11：更新 Jira 狀態
mcp__Atlassian__getJiraIssue({
  fields: ["status", "comment"]
})
```

**實施位置**：
- `/whiteLabel-start`：步驟 4
- `/whiteLabel-run`：步驟 4
- `/whiteLabel-complete`：步驟 11

**預期效果**：
- 減少 70% 的 API 返回數據
- 所有 JSON 映射邏輯正常運作
- 節省 ~3,000 tokens

---

### 方案 3：精簡 Slash Command 文檔

**問題**：詳細的步驟說明、範例、格式化輸出佔用大量空間

**解決方案**：重構為精簡版，保留核心執行邏輯

**已優化文件**：

| 文件 | 原始行數 | 優化後 | 減少 |
|------|---------|--------|------|
| whiteLabel-run.md | 139 | 66 | -53% |
| whiteLabel-start.md | 119 | 44 | -63% |
| whiteLabel-finish.md | 129 | 42 | -67% |
| whiteLabel-complete.md | 65 | 35 | -46% |
| **總計** | **452** | **187** | **-59%** |

**優化原則**：
- 移除詳細步驟說明（參考 CLAUDE.md）
- 移除大量範例和格式化輸出
- 保留核心映射規則和優化提示
- 精簡錯誤處理說明

**預期效果**：節省 ~2,500 tokens

---

### 方案 4：Todo 管理優化

**問題**：頻繁更新 todo（7 次），每次傳遞完整 list

**解決方案**：減少更新頻率，批量標記狀態

**優化策略**：

```javascript
// 原始：7 次更新
// 1. 初始化 todos
// 2-6. 每個步驟完成後單獨更新
// 7. 全部完成

// 優化：3 次更新
1. 初始化 todos（6 個步驟）
2. 步驟 1-5 完成後批量更新為 completed
3. 全部完成後最終更新

// 節省：4 次調用 × ~375 tokens = ~1,500 tokens
```

**實施位置**：
- `/whiteLabel-start`：初始化 + 步驟 1-5 批量完成
- `/whiteLabel-run`：初始化 + 步驟 1-5 批量完成 + 全部完成

**預期效果**：節省 ~1,500 tokens

---

## 實施檢查清單

### 執行 `/whiteLabel-run` 時

- [ ] 步驟 1：檢查分支（顯示簡潔訊息）
- [ ] 步驟 2：確認客製化
- [ ] 步驟 3：建立日誌
- [ ] **步驟 4：Jira API 使用 fields 參數**
  ```javascript
  fields: ["summary", "description", "status", "fixVersions", "customfield_10037", "comment"]
  ```
- [ ] 步驟 5：生成 JSON
- [ ] **Todo：僅 3 次更新**（初始化 → 步驟1-5完成 → 全部完成）
- [ ] **執行 white-label-process.sh：使用 BashOutput filter**
  ```javascript
  BashOutput(bash_id, filter: "^(\[0;3[0-9]m)?===|✅|❌|⚠️|完成")
  ```

### 執行 `/whiteLabel-complete` 時

- [ ] **Jira API 使用最小 fields**
  ```javascript
  fields: ["status", "comment"]
  ```
- [ ] 更新狀態：IN DEV → DEV DONE
- [ ] 添加 comment
- [ ] 回覆 "SUCCESS"

---

## 效果驗證

### 優化前（基準）
- **總消耗**: ~21,000 tokens
- **主要消耗**：
  - BashOutput (3次): ~6,300 tokens
  - Jira API: ~4,850 tokens
  - Slash Command: ~4,500 tokens
  - Todo 管理: ~2,500 tokens
  - 其他: ~2,850 tokens

### 優化後（目標）
- **總消耗**: ~9,000-10,000 tokens
- **節省**: ~11,000-12,000 tokens (52-57%)
- **主要改善**：
  - BashOutput: ~2,300 tokens (-63%)
  - Jira API: ~1,850 tokens (-62%)
  - Slash Command: ~2,000 tokens (-56%)
  - Todo 管理: ~1,000 tokens (-60%)

---

## 功能完整性保證

✅ **所有核心功能保持不變**：
- Jira 處理（狀態轉換、數據提取）
- JSON 生成（所有映射規則）
- 代碼生成（完整文件）
- SQL 處理（追加 + 執行）
- Git 提交
- 錯誤處理

✅ **用戶體驗維持**：
- 關鍵進度可見
- 錯誤信息完整
- 最終結果詳細

✅ **向後兼容**：
- 現有腳本無需修改
- 只調整 Claude 的調用方式
- 可逐步實施

---

## 故障排除

### 問題：BashOutput filter 過濾了錯誤信息
**解決**：調整正則表達式，加入錯誤關鍵字
```javascript
filter: "^(\[0;3[0-9]m)?===|✅|❌|⚠️|完成|ERROR|FAILED|失敗"
```

### 問題：Jira API 缺少必要欄位
**解決**：根據具體需求添加到 fields 數組
```javascript
fields: [..., "customfield_XXXXX"]  // 新增欄位
```

### 問題：Todo 批量更新導致進度不清晰
**解決**：在關鍵節點添加簡短的文字說明
```
正在執行步驟 1-5...
✅ 步驟 1-5 完成，開始執行步驟 6-10...
```

---

## 維護建議

1. **定期檢查**：每季度檢查 token 消耗趨勢
2. **持續優化**：發現新的高消耗點時及時調整
3. **文檔更新**：優化策略變更時更新本文檔
4. **效果追蹤**：記錄每次優化的實際效果

---

## 相關文檔

- `@ProjectTool/CLAUDE.md`：白牌流程核心映射規則
- `.claude/commands/whiteLabel-*.md`：精簡後的 slash commands
- `ProjectTool/script/unix/white-label-process.sh`：整合腳本

---

**最後更新**: 2025-11-18
**維護者**: Wilson Wang
