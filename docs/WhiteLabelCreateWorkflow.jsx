import React from 'react';

const APIWhiteLabelCreateWorkflow = () => {
// 設置全局背景色
React.useEffect(() => {
document.body.style.backgroundColor = '#1a1a1a';
document.body.style.margin = '0';
document.body.style.padding = '0';
document.documentElement.style.backgroundColor = '#1a1a1a';
return () => {
document.body.style.backgroundColor = '';
document.body.style.margin = '';
document.body.style.padding = '';
document.documentElement.style.backgroundColor = '';
};
}, []);

return (
<div style={{
	position: 'fixed',
top: 0,
left: 0,
right: 0,
bottom: 0,
backgroundColor: '#1a1a1a',
overflow: 'auto'
}}>
<div style={{ padding: '20px', minHeight: '100vh', width: '100%', boxSizing: 'border-box' }}>
<h1 style={{ textAlign: 'center', color: '#ffffff', marginBottom: '10px', fontSize: '32px' }}>
🏗️ API Create Workflow
</h1>
<div style={{ textAlign: 'center', color: '#aaaaaa', marginBottom: '30px', fontSize: '14px' }}>
Version 2.0 - 2025年11月 | 模組化架構重構版
</div>

<svg width="100%" height="2800" viewBox="0 0 1600 2800" preserveAspectRatio="xMidYMid meet"
	style={{ backgroundColor: '#2d2d2d', borderRadius: '10px', boxShadow: '0 2px 10px rgba(255,255,255,0.1)', maxWidth: '1600px', margin: '0 auto', display: 'block' }}>
{/* 背景矩形 - 確保整個SVG區域都是深灰色 */}
<rect x="0" y="0" width="1600" height="2800" fill="#2d2d2d" />

<defs>
	{/* 定義箭頭標記 */}
	<marker id="arrowhead" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
		<polygon points="0 0, 10 3, 0 6" fill="#aaaaaa" />
	</marker>
	
	{/* 定義漸變效果 */}
	<linearGradient id="commandGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#9C27B0', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#7B1FA2', stopOpacity: 1 }} />
	</linearGradient>
	
	<linearGradient id="stepGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#2196F3', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#1976D2', stopOpacity: 1 }} />
	</linearGradient>
	
	<linearGradient id="highlightGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#00BCD4', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#0097A7', stopOpacity: 1 }} />
	</linearGradient>
	
	<linearGradient id="decisionGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#FFC107', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#FFA000', stopOpacity: 1 }} />
	</linearGradient>
	
	<linearGradient id="errorGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#FF9800', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#F57C00', stopOpacity: 1 }} />
	</linearGradient>
	
	<linearGradient id="startGradient" x1="0%" y1="0%" x2="0%" y2="100%">
		<stop offset="0%" style={{ stopColor: '#4CAF50', stopOpacity: 1 }} />
		<stop offset="100%" style={{ stopColor: '#45a049', stopOpacity: 1 }} />
	</linearGradient>
</defs>

{/* 開始節點 */}
<circle cx="800" cy="50" r="30" fill="url(#startGradient)" />
<text x="800" y="55" textAnchor="middle" fill="white" fontWeight="bold">開始</text>

{/* ========== 階段 1：whiteLabel-start (步驟 1-5) ========== */}

{/* Slash Command: whiteLabel-start */}
<rect x="650" y="100" width="300" height="70" rx="10" fill="url(#commandGradient)" />
<text x="800" y="125" textAnchor="middle" fill="white" fontWeight="bold" fontSize="16">/whiteLabel-start</text>
<text x="800" y="145" textAnchor="middle" fill="white" fontSize="13">SACRIC-XXX</text>
<text x="800" y="160" textAnchor="middle" fill="white" fontSize="11">步驟 1-5：前期準備</text>

{/* 步驟 1：檢查分支 + 醒目提示 */}
<rect x="650" y="200" width="300" height="110" rx="10" fill="url(#highlightGradient)" stroke="#00BCD4" strokeWidth="3" />
<text x="800" y="225" textAnchor="middle" fill="white" fontWeight="bold">步驟 1：檢查分支</text>
<text x="800" y="245" textAnchor="middle" fill="white" fontSize="12">╔═══════════════════════╗</text>
<text x="800" y="265" textAnchor="middle" fill="white" fontSize="12">║ 🔍 白牌流程執行環境檢查 ║</text>
<text x="800" y="285" textAnchor="middle" fill="white" fontSize="12">║ 分支: [當前分支]        ║</text>
<text x="800" y="300" textAnchor="middle" fill="white" fontSize="12">╚═══════════════════════╝</text>

{/* 步驟 2：確認客製化 flag */}
<rect x="650" y="340" width="300" height="60" rx="10" fill="url(#stepGradient)" />
<text x="800" y="365" textAnchor="middle" fill="white" fontWeight="bold">步驟 2：確認客製化 flag</text>
<text x="800" y="385" textAnchor="middle" fill="white" fontSize="12">customized = true/false</text>

{/* 步驟 3：建立日誌 */}
<rect x="650" y="430" width="300" height="60" rx="10" fill="url(#stepGradient)" />
<text x="800" y="455" textAnchor="middle" fill="white" fontWeight="bold">步驟 3：建立日誌</text>
<text x="800" y="475" textAnchor="middle" fill="white" fontSize="12">log/SACRIC-XXX.txt</text>

{/* 步驟 4：處理 Jira */}
<rect x="650" y="520" width="300" height="80" rx="10" fill="url(#stepGradient)" />
<text x="800" y="545" textAnchor="middle" fill="white" fontWeight="bold">步驟 4：處理 Jira</text>
<text x="800" y="565" textAnchor="middle" fill="white" fontSize="12">getJiraIssue(SACRIC-XXX)</text>
<text x="800" y="585" textAnchor="middle" fill="white" fontSize="12">Ready to DEV → IN DEV</text>

{/* 步驟 5：生成 JSON */}
<rect x="650" y="630" width="300" height="80" rx="10" fill="url(#stepGradient)" />
<text x="800" y="655" textAnchor="middle" fill="white" fontWeight="bold">步驟 5：生成 JSON</text>
<text x="800" y="675" textAnchor="middle" fill="white" fontSize="12">根據映射規則轉換</text>
<text x="800" y="695" textAnchor="middle" fill="white" fontSize="12">sample/SACRIC-XXX.json</text>

{/* ========== 階段 2：whiteLabel-finish 或整合腳本 (步驟 6-10) ========== */}

{/* 執行方式選擇 */}
<polygon points="800,740 860,770 800,800 740,770" fill="url(#decisionGradient)" />
<text x="800" y="775" textAnchor="middle" fill="black" fontSize="12">選擇執行方式</text>

{/* 方式 1: /whiteLabel-finish */}
<rect x="1050" y="740" width="200" height="60" rx="10" fill="url(#commandGradient)" />
<text x="1150" y="765" textAnchor="middle" fill="white" fontWeight="bold">/whiteLabel-finish</text>
<text x="1150" y="785" textAnchor="middle" fill="white" fontSize="11">步驟 6-10</text>

{/* 方式 2: 整合腳本 */}
<rect x="450" y="740" width="200" height="60" rx="10" fill="#00796B" />
<text x="550" y="760" textAnchor="middle" fill="white" fontSize="12">white-label-process.sh</text>
<text x="550" y="780" textAnchor="middle" fill="white" fontSize="12">SACRIC-XXX</text>
<text x="550" y="795" textAnchor="middle" fill="white" fontSize="10">整合腳本</text>

{/* 步驟 6 開始提示框 */}
<rect x="650" y="840" width="300" height="100" rx="10" fill="url(#highlightGradient)" stroke="#00BCD4" strokeWidth="3" />
<text x="800" y="865" textAnchor="middle" fill="white" fontWeight="bold">步驟 6 開始</text>
<text x="800" y="885" textAnchor="middle" fill="white" fontSize="12">╔═══════════════════════╗</text>
<text x="800" y="905" textAnchor="middle" fill="white" fontSize="12">║ ⚙️ 開始執行技術步驟 6-10 ║</text>
<text x="800" y="925" textAnchor="middle" fill="white" fontSize="12">╚═══════════════════════╝</text>

{/* 步驟 6：代碼生成 */}
<rect x="650" y="970" width="300" height="70" rx="10" fill="url(#stepGradient)" />
<text x="800" y="995" textAnchor="middle" fill="white" fontWeight="bold">步驟 6：代碼生成</text>
<text x="800" y="1015" textAnchor="middle" fill="white" fontSize="12">project-tool.sh/bat</text>
<text x="800" y="1030" textAnchor="middle" fill="white" fontSize="12">生成代碼和 SQL</text>

{/* 步驟 7：編譯檢查 */}
<rect x="650" y="1070" width="300" height="80" rx="10" fill="url(#stepGradient)" />
<text x="800" y="1095" textAnchor="middle" fill="white" fontWeight="bold">步驟 7：編譯檢查</text>
<text x="800" y="1115" textAnchor="middle" fill="white" fontSize="12">mvn test-compile</text>
<text x="800" y="1135" textAnchor="middle" fill="white" fontSize="12">自動修復 import（最多3次）</text>

{/* 步驟 8：更新 webSiteValue */}
<rect x="650" y="1180" width="300" height="70" rx="10" fill="url(#stepGradient)" />
<text x="800" y="1205" textAnchor="middle" fill="white" fontWeight="bold">步驟 8：更新 webSiteValue</text>
<text x="800" y="1225" textAnchor="middle" fill="white" fontSize="12">CLAUDE.md 自動 +1</text>
<text x="800" y="1240" textAnchor="middle" fill="white" fontSize="12">跨平台兼容</text>

{/* 步驟 9：SQL 處理 */}
<rect x="650" y="1280" width="300" height="100" rx="10" fill="url(#stepGradient)" />
<text x="800" y="1305" textAnchor="middle" fill="white" fontWeight="bold">步驟 9：SQL 處理</text>
<text x="800" y="1325" textAnchor="middle" fill="white" fontSize="12">9-1：追加到 release_sql</text>
<text x="800" y="1345" textAnchor="middle" fill="white" fontSize="12">9-2：執行到 DEV 環境</text>
<text x="800" y="1365" textAnchor="middle" fill="white" fontSize="11">客製化白牌：⏭️ 跳過</text>

{/* 步驟 10：Git Commit */}
<rect x="650" y="1410" width="300" height="90" rx="10" fill="url(#stepGradient)" />
<text x="800" y="1435" textAnchor="middle" fill="white" fontWeight="bold">步驟 10：Git Commit</text>
<text x="800" y="1455" textAnchor="middle" fill="white" fontSize="12">智能生成 commit 訊息</text>
<text x="800" y="1475" textAnchor="middle" fill="white" fontSize="12">自動 commit</text>
<text x="800" y="1490" textAnchor="middle" fill="white" fontSize="11">客製化白牌：⏭️ 跳過</text>

{/* ========== 階段 3：whiteLabel-complete (步驟 11) ========== */}

{/* 執行方式選擇 */}
<polygon points="800,1535 860,1565 800,1595 740,1565" fill="url(#decisionGradient)" />
<text x="800" y="1560" textAnchor="middle" fill="black" fontSize="11">執行步驟 11?</text>
<text x="800" y="1575" textAnchor="middle" fill="black" fontSize="10">客製化跳過</text>

{/* 客製化跳過分支 */}
<circle cx="1100" cy="1565" r="30" fill="url(#startGradient)" />
<text x="1100" y="1570" textAnchor="middle" fill="white" fontSize="12">結束</text>
<text x="1100" y="1615" textAnchor="middle" fill="#aaaaaa" fontSize="11">(客製化)</text>

{/* Slash Command: whiteLabel-complete */}
<rect x="650" y="1640" width="300" height="70" rx="10" fill="url(#commandGradient)" />
<text x="800" y="1665" textAnchor="middle" fill="white" fontWeight="bold" fontSize="16">/whiteLabel-complete</text>
<text x="800" y="1685" textAnchor="middle" fill="white" fontSize="13">SACRIC-XXX [Comment]</text>
<text x="800" y="1700" textAnchor="middle" fill="white" fontSize="11">步驟 11：更新 Jira</text>

{/* 步驟 11 提示框 */}
<rect x="650" y="1740" width="300" height="100" rx="10" fill="url(#highlightGradient)" stroke="#00BCD4" strokeWidth="3" />
<text x="800" y="1765" textAnchor="middle" fill="white" fontWeight="bold">步驟 11：完成流程</text>
<text x="800" y="1785" textAnchor="middle" fill="white" fontSize="12">╔═══════════════════════╗</text>
<text x="800" y="1805" textAnchor="middle" fill="white" fontSize="12">║ ✅ 完成白牌流程 (步驟 11) ║</text>
<text x="800" y="1825" textAnchor="middle" fill="white" fontSize="12">╚═══════════════════════╝</text>

{/* 步驟 11-1：準備 Comment */}
<rect x="650" y="1870" width="300" height="80" rx="10" fill="url(#stepGradient)" />
<text x="800" y="1895" textAnchor="middle" fill="white" fontWeight="bold">11-1：準備 Comment</text>
<text x="800" y="1915" textAnchor="middle" fill="white" fontSize="12">優先使用第二個參數</text>
<text x="800" y="1935" textAnchor="middle" fill="white" fontSize="12">否則從 JSON 讀取</text>

{/* 步驟 11-2：檢查 Jira 狀態 */}
<rect x="650" y="1980" width="300" height="60" rx="10" fill="url(#stepGradient)" />
<text x="800" y="2005" textAnchor="middle" fill="white" fontWeight="bold">11-2：檢查 Jira 狀態</text>
<text x="800" y="2025" textAnchor="middle" fill="white" fontSize="12">確認為 IN DEV</text>

{/* 步驟 11-3：更新 Jira 狀態 */}
<rect x="650" y="2070" width="300" height="70" rx="10" fill="url(#stepGradient)" />
<text x="800" y="2095" textAnchor="middle" fill="white" fontWeight="bold">11-3：更新 Jira 狀態</text>
<text x="800" y="2115" textAnchor="middle" fill="white" fontSize="12">IN DEV → DEV DONE</text>
<text x="800" y="2130" textAnchor="middle" fill="white" fontSize="12">transitionJiraIssue(121)</text>

{/* 步驟 11-4：添加 Comment */}
<rect x="650" y="2170" width="300" height="70" rx="10" fill="url(#stepGradient)" />
<text x="800" y="2195" textAnchor="middle" fill="white" fontWeight="bold">11-4：添加 Comment</text>
<text x="800" y="2215" textAnchor="middle" fill="white" fontSize="12">檢查是否已存在</text>
<text x="800" y="2230" textAnchor="middle" fill="white" fontSize="12">Site: [name] ([value])</text>

{/* 步驟 11-5：回覆結果 */}
<rect x="650" y="2270" width="300" height="60" rx="10" fill="#4CAF50" />
<text x="800" y="2295" textAnchor="middle" fill="white" fontWeight="bold">11-5：回覆結果</text>
<text x="800" y="2315" textAnchor="middle" fill="white" fontSize="12">SUCCESS 或 ERROR</text>

{/* 執行成功決策 */}
<polygon points="800,2360 860,2390 800,2420 740,2390" fill="url(#decisionGradient)" />
<text x="800" y="2395" textAnchor="middle" fill="black" fontSize="12">成功？</text>

{/* 成功結束 */}
<circle cx="800" cy="2490" r="35" fill="url(#startGradient)" />
<text x="800" y="2495" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">完成</text>

{/* 錯誤處理 */}
<rect x="950" y="2360" width="200" height="60" rx="10" fill="url(#errorGradient)" />
<text x="1050" y="2385" textAnchor="middle" fill="white" fontWeight="bold">錯誤處理</text>
<text x="1050" y="2405" textAnchor="middle" fill="white" fontSize="11">顯示手動提醒</text>

{/* ========== 連接線 ========== */}

{/* 開始 → whiteLabel-start */}
<line x1="800" y1="80" x2="800" y2="100" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* whiteLabel-start → 步驟 1 */}
<line x1="800" y1="170" x2="800" y2="200" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 1 → 步驟 2 */}
<line x1="800" y1="310" x2="800" y2="340" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 2 → 步驟 3 */}
<line x1="800" y1="400" x2="800" y2="430" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 3 → 步驟 4 */}
<line x1="800" y1="490" x2="800" y2="520" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 4 → 步驟 5 */}
<line x1="800" y1="600" x2="800" y2="630" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 5 → 選擇執行方式 */}
<line x1="800" y1="710" x2="800" y2="740" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 選擇 → whiteLabel-finish */}
<path d="M 860 770 L 1050 770" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" fill="none" />
<text x="950" y="760" textAnchor="middle" fill="#ffffff" fontSize="11">方式 1</text>

{/* 選擇 → 整合腳本 */}
<path d="M 740 770 L 650 770" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" fill="none" />
<text x="680" y="760" textAnchor="middle" fill="#ffffff" fontSize="11">方式 2</text>

{/* 兩種方式匯合到步驟 6 */}
<path d="M 1150 800 L 1150 890 L 950 890" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" fill="none" />
<path d="M 550 800 L 550 890 L 650 890" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" fill="none" />

{/* 步驟 6 提示 → 步驟 6 */}
<line x1="800" y1="940" x2="800" y2="970" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 6 → 步驟 7 */}
<line x1="800" y1="1040" x2="800" y2="1070" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 7 → 步驟 8 */}
<line x1="800" y1="1150" x2="800" y2="1180" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 8 → 步驟 9 */}
<line x1="800" y1="1250" x2="800" y2="1280" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 9 → 步驟 10 */}
<line x1="800" y1="1380" x2="800" y2="1410" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 10 → 選擇 11 */}
<line x1="800" y1="1500" x2="800" y2="1535" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 選擇 → 客製化結束 */}
<path d="M 860 1565 L 1070 1565" stroke="#aaaaaa" strokeWidth="2" strokeDasharray="5,5" markerEnd="url(#arrowhead)" fill="none" />
<text x="950" y="1555" textAnchor="middle" fill="#ffffff" fontSize="11">客製化</text>

{/* 選擇 → whiteLabel-complete */}
<line x1="800" y1="1595" x2="800" y2="1640" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />
<text x="750" y="1620" textAnchor="middle" fill="#ffffff" fontSize="11">非客製化</text>

{/* whiteLabel-complete → 步驟 11 提示 */}
<line x1="800" y1="1710" x2="800" y2="1740" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 步驟 11 提示 → 11-1 */}
<line x1="800" y1="1840" x2="800" y2="1870" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 11-1 → 11-2 */}
<line x1="800" y1="1950" x2="800" y2="1980" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 11-2 → 11-3 */}
<line x1="800" y1="2040" x2="800" y2="2070" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 11-3 → 11-4 */}
<line x1="800" y1="2140" x2="800" y2="2170" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 11-4 → 11-5 */}
<line x1="800" y1="2240" x2="800" y2="2270" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 11-5 → 成功決策 */}
<line x1="800" y1="2330" x2="800" y2="2360" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />

{/* 成功 → 完成 */}
<line x1="800" y1="2420" x2="800" y2="2455" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" />
<text x="750" y="2440" textAnchor="middle" fill="#ffffff" fontSize="11">SUCCESS</text>

{/* 失敗 → 錯誤處理 */}
<path d="M 860 2390 L 950 2390" stroke="#aaaaaa" strokeWidth="2" markerEnd="url(#arrowhead)" fill="none" />
<text x="900" y="2380" textAnchor="middle" fill="#ffffff" fontSize="11">ERROR</text>

{/* 版本標記 */}
<text x="1550" y="30" textAnchor="end" fill="#aaaaaa" fontSize="12">v2.0 (2025-11-18)</text>

{/* 右側資訊區塊 */}

{/* Slash Commands 說明 */}
<g transform="translate(1200, 100)">
	<rect x="0" y="0" width="350" height="300" rx="10" fill="#3a3a3a" stroke="#9C27B0" strokeWidth="2" />
	<text x="175" y="25" fontSize="14" fontWeight="bold" fill="#ff80ff" textAnchor="middle">📋 Slash Commands</text>
	
	<text x="10" y="55" fontSize="12" fill="#ffffff" fontWeight="bold">/whiteLabel-start SACRIC-XXX</text>
	<text x="10" y="75" fontSize="10" fill="#aaaaaa">執行步驟 1-5：Jira處理、JSON生成</text>
	
	<text x="10" y="105" fontSize="12" fill="#ffffff" fontWeight="bold">/whiteLabel-finish SACRIC-XXX</text>
	<text x="10" y="125" fontSize="10" fill="#aaaaaa">執行步驟 6-10：代碼生成、編譯、SQL、Git</text>
	
	<text x="10" y="155" fontSize="12" fill="#ffffff" fontWeight="bold">/whiteLabel-run SACRIC-XXX</text>
	<text x="10" y="175" fontSize="10" fill="#aaaaaa">執行步驟 1-10：完整技術流程</text>
	
	<text x="10" y="205" fontSize="12" fill="#ffffff" fontWeight="bold">/whiteLabel-complete SACRIC-XXX [Comment]</text>
	<text x="10" y="225" fontSize="10" fill="#aaaaaa">執行步驟 11：更新Jira為DEV DONE</text>
	
	<rect x="10" y="245" width="330" height="1" fill="#555" />
	
	<text x="10" y="265" fontSize="11" fill="#4da6ff">💡 執行建議：</text>
	<text x="10" y="280" fontSize="10" fill="#cccccc">• 完全自動：/run → /complete</text>
	<text x="10" y="295" fontSize="10" fill="#cccccc">• 分階段：/start → /finish → /complete</text>
</g>

{/* v2.0 改進重點 */}
<g transform="translate(1200, 450)">
	<rect x="0" y="0" width="350" height="280" rx="10" fill="#2a4a2a" stroke="#66bb6a" strokeWidth="2" />
	<text x="175" y="25" fontSize="14" fontWeight="bold" fill="#66bb6a" textAnchor="middle">🎯 v2.0 改進重點</text>
	
	<text x="10" y="50" fontSize="11" fill="#aaaaaa" fontWeight="bold">步驟調整：</text>
	<text x="10" y="70" fontSize="10" fill="#cccccc">• 步驟 8：更新webSiteValue（原步驟9）</text>
	<text x="10" y="85" fontSize="10" fill="#cccccc">• 步驟 9：SQL處理（原步驟11）</text>
	<text x="10" y="100" fontSize="10" fill="#cccccc">• 步驟10：Git Commit（新增）</text>
	<text x="10" y="115" fontSize="10" fill="#cccccc">• 步驟11：更新Jira（原步驟8）⭐</text>
	
	<text x="10" y="140" fontSize="11" fill="#aaaaaa" fontWeight="bold">關鍵改進：</text>
	<text x="10" y="160" fontSize="10" fill="#cccccc">• Jira更新移到最後，允許手動確認</text>
	<text x="10" y="175" fontSize="10" fill="#cccccc">• 模組化命令，執行更靈活</text>
	<text x="10" y="190" fontSize="10" fill="#cccccc">• 醒目提示關鍵步驟（1, 6, 11）</text>
	<text x="10" y="205" fontSize="10" fill="#cccccc">• 參數傳遞支援外部Comment</text>
	
	<text x="10" y="230" fontSize="11" fill="#aaaaaa" fontWeight="bold">成功驗證：</text>
	<text x="10" y="250" fontSize="10" fill="#cccccc">• 步驟11回覆SUCCESS/ERROR</text>
	<text x="10" y="265" fontSize="10" fill="#cccccc">• 整合腳本可檢查執行結果</text>
</g>

{/* 客製化白牌處理 */}
<g transform="translate(1200, 780)">
	<rect x="0" y="0" width="350" height="200" rx="10" fill="#4a3a2a" stroke="#ff9800" strokeWidth="2" />
	<text x="175" y="25" fontSize="14" fontWeight="bold" fill="#ff9800" textAnchor="middle">⚙️ 客製化白牌處理</text>
	
	<text x="10" y="50" fontSize="11" fill="#ffffff">執行狀態：</text>
	<text x="10" y="70" fontSize="10" fill="#cccccc">• 步驟 1-8：正常執行 ✅</text>
	<text x="10" y="85" fontSize="10" fill="#cccccc">• 步驟 9（SQL）：自動跳過 ⏭️</text>
	<text x="10" y="100" fontSize="10" fill="#cccccc">• 步驟10（Git）：自動跳過 ⏭️</text>
	<text x="10" y="115" fontSize="10" fill="#cccccc">• 步驟11（Jira）：自動跳過 ⏭️</text>
	
	<rect x="10" y="130" width="330" height="1" fill="#555" />
	
	<text x="10" y="155" fontSize="11" fill="#ff9800">⚠️ 注意事項：</text>
	<text x="10" y="175" fontSize="10" fill="#cccccc">客製化白牌需手動處理SQL、Git、Jira</text>
</g>

{/* 整合腳本說明 */}
<g transform="translate(1200, 1020)">
	<rect x="0" y="0" width="350" height="180" rx="10" fill="#3a4a3a" stroke="#00796B" strokeWidth="2" />
	<text x="175" y="25" fontSize="14" fontWeight="bold" fill="#4db6ac" textAnchor="middle">🔧 整合腳本</text>
	
	<text x="10" y="50" fontSize="11" fill="#aaaaaa" fontWeight="bold">white-label-process.sh：</text>
	<text x="10" y="70" fontSize="10" fill="#cccccc">• 自動執行步驟 1-10</text>
	<text x="10" y="85" fontSize="10" fill="#cccccc">• 支援客製化判斷</text>
	<text x="10" y="100" fontSize="10" fill="#cccccc">• 錯誤自動處理</text>
	
	<text x="10" y="125" fontSize="11" fill="#aaaaaa" fontWeight="bold">執行流程：</text>
	<text x="10" y="145" fontSize="10" fill="#cccccc">1. 調用 /whiteLabel-start</text>
	<text x="10" y="160" fontSize="10" fill="#cccccc">2. 調用 /whiteLabel-finish</text>
</g>
</svg>

{/* 底部說明區域 */}
<div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#2d2d2d', borderRadius: '10px', boxShadow: '0 2px 5px rgba(255,255,255,0.1)', maxWidth: '1600px', margin: '30px auto 0 auto' }}>
<h2 style={{ color: '#ffffff', marginBottom: '15px' }}>流程說明</h2>

<div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', gap: '20px' }}>
{/* 主要改進 */}
<div style={{ background: '#3a3a3a', padding: '15px', borderRadius: '8px', border: '1px solid #555' }}>
<h3 style={{ color: '#4da6ff', fontSize: '16px', marginTop: 0 }}>🎯 主要改進 (v2.0)</h3>
<ul style={{ color: '#cccccc', fontSize: '14px', lineHeight: '1.8' }}>
<li><strong>步驟11獨立：</strong>Jira更新移到最後，允許Git commit後手動確認</li>
<li><strong>Slash Commands：</strong>4個模組化命令，執行更靈活</li>
<li><strong>醒目提示：</strong>關鍵步驟（1, 6, 11）都有視覺提示框</li>
<li><strong>參數傳遞：</strong>步驟11支援從命令參數提取資訊</li>
<li><strong>成功驗證：</strong>步驟11回覆SUCCESS/ERROR供腳本檢查</li>
</ul>
</div>

{/* 執行方式 */}
<div style={{ background: '#3a3a3a', padding: '15px', borderRadius: '8px', border: '1px solid #555' }}>
<h3 style={{ color: '#ffd54f', fontSize: '16px', marginTop: 0 }}>🔀 執行方式選擇</h3>
<ol style={{ color: '#cccccc', fontSize: '14px', lineHeight: '1.8' }}>
<li>
	<strong>完全自動化（推薦）</strong><br />
	<code style={{ color: '#4da6ff' }}>/whiteLabel-run → /whiteLabel-complete</code>
</li>
<li style={{ marginTop: '10px' }}>
<strong>分階段執行（更靈活）</strong><br />
<code style={{ color: '#4da6ff' }}>/whiteLabel-start → /whiteLabel-finish → /whiteLabel-complete</code>
</li>
<li style={{ marginTop: '10px' }}>
<strong>混合執行</strong><br />
<code style={{ color: '#4da6ff' }}>/whiteLabel-start → white-label-process.sh → /whiteLabel-complete</code>
</li>
</ol>
</div>

{/* 版本對比 */}
<div style={{ background: '#3a3a3a', padding: '15px', borderRadius: '8px', border: '1px solid #555' }}>
<h3 style={{ color: '#66bb6a', fontSize: '16px', marginTop: 0 }}>📊 版本對比</h3>
<table style={{ width: '100%', color: '#cccccc', fontSize: '12px', borderCollapse: 'collapse' }}>
<thead>
<tr style={{ borderBottom: '2px solid #555' }}>
<th style={{ padding: '8px', textAlign: 'left' }}>項目</th>
<th style={{ padding: '8px', textAlign: 'left' }}>v1.2</th>
<th style={{ padding: '8px', textAlign: 'left', color: '#4CAF50' }}>v2.0</th>
</tr>
</thead>
<tbody>
<tr style={{ borderBottom: '1px solid #444' }}>
<td style={{ padding: '8px' }}>步驟8</td>
<td style={{ padding: '8px' }}>更新Jira</td>
<td style={{ padding: '8px', color: '#4CAF50' }}>更新webSiteValue</td>
</tr>
<tr style={{ borderBottom: '1px solid #444' }}>
<td style={{ padding: '8px' }}>步驟11</td>
<td style={{ padding: '8px' }}>SQL追加</td>
<td style={{ padding: '8px', color: '#4CAF50' }}>更新Jira ⭐</td>
</tr>
<tr>
	<td style={{ padding: '8px' }}>執行方式</td>
	<td style={{ padding: '8px' }}>手動執行</td>
	<td style={{ padding: '8px', color: '#4CAF50' }}>Slash Commands</td>
</tr>
</tbody>
</table>
</div>
</div>

{/* 關鍵變更提示 */}
<div style={{
	marginTop: '20px',
padding: '15px',
background: '#4a3a2a',
borderRadius: '8px',
border: '1px solid #ff9800'
}}>
<h3 style={{ color: '#ff9800', marginTop: 0 }}>🆕 步驟11的關鍵變更</h3>
<ul style={{ color: '#cccccc', lineHeight: '1.8', fontSize: '14px' }}>
<li><strong>獨立執行：</strong>不在步驟8，而是移到最後（步驟11）</li>
<li><strong>條件式Comment：</strong>優先使用命令參數，否則從JSON讀取</li>
<li><strong>參數提取：</strong>第一個參數為票務號碼，第二個參數為Comment內容</li>
<li><strong>執行驗證：</strong>回覆"SUCCESS"或"ERROR"供整合腳本檢查</li>
<li><strong>錯誤處理：</strong>失敗時顯示手動提醒，降級為手動操作</li>
</ul>
</div>
</div>
</div>
</div>
);
};

export default APIWhiteLabelCreateWorkflow;