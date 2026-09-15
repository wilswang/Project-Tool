package tool.sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import tool.sheet.SheetConfig.GroupInfoMapping;
import tool.whiteLabel.GroupInfo;

/**
 * 把試算表的列資料對應成 {@link GroupInfo}。
 *
 * <p><b>刻意重用 {@code tool.whiteLabel.GroupInfo} 而不另建 DTO</b> ——
 * 它的欄位順序（privateIpSetId、privateIp、bkIpSetId、apiInfoBkIpSetId、backup）
 * Jackson 序列化後正好等於白牌單 JSON 與 {@code task/api-2.0-group-info.md} 的區塊形狀，
 * 產出可以直接貼進 {@code sample/**\/SACRIC-XXXX.json}。
 *
 * <h2>區塊定位規則（階段 3 實測後確定）</h2>
 * 讀了真實佈局才發現先前的假設是錯的：<b>B 欄在群組的每一列都有值</b>，不是只出現在第 1 列。
 * 「資料不連續」指的是<b>代號的排列跳號且不照順序</b>（A04 → A05 → A07 → … → A33 → A01 → …），
 * 不是欄位有空白。
 *
 * <p>所以定位就是「找出 B 欄等於該代號的<b>連續</b>列」。實測 79 個區塊中，
 * 只有 {@code B01} 出現在兩個不相鄰的位置，其餘代號都只有單一連續區塊。
 * 區塊列數不固定：多數 2 列，{@code A17}/{@code A18}/{@code A22} 只有 1 列，{@code A56} 有 3 列。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class GroupInfoMapper {

	/** cell 內多值的分隔符：換行、逗號、分號都吃（實際用哪一種待階段 3 確認） */
	private static final Pattern VALUE_SEPARATOR = Pattern.compile("[\\r\\n,;]+");

	private static final Pattern UUID_SHAPE =
		Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private GroupInfoMapper() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 把單一 cell 的內容切成個別值。
	 *
	 * <p>試算表的 backup 欄位<b>一格內含多個網域，數量不固定</b>（不是每格 5 個），
	 * 所以取出後必須先切分才能串接。
	 *
	 * <p>整格內容等於 emptyMarkers 之一（試算表用 {@code "-"} 表示沒有）或空白時回空 List；
	 * 切分後的個別項目若是 emptyMarker 也會被丟掉。
	 *
	 * @param raw          cell 原始內容，可為 null
	 * @param emptyMarkers 視為無資料的值，null 時只當空白處理
	 */
	public static List<String> splitCell(String raw, List<String> emptyMarkers) {
		if (raw == null) {
			return Collections.emptyList();
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty() || isEmptyMarker(trimmed, emptyMarkers)) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		for (String piece : VALUE_SEPARATOR.split(trimmed)) {
			String value = piece.trim();
			if (!value.isEmpty() && !isEmptyMarker(value, emptyMarkers)) {
				result.add(value);
			}
		}
		return result;
	}

	/**
	 * 找出某個群組代號所在的連續列區塊。
	 *
	 * @param rows        涵蓋範圍讀回來的列資料
	 * @param baseRow     rows 第 0 筆對應的試算表列號（1-based）
	 * @param baseColumn  每列第 0 個元素對應的欄位絕對 index（A=0）
	 * @param groupCode   要找的代號，例如 {@code A69}。比對時去空白、不分大小寫
	 * @throws SheetToolException 找不到、找到多個不相鄰區塊、或區塊列數超過上限
	 */
	public static GroupBlock locate(List<List<Object>> rows, int baseRow, int baseColumn,
			String groupCode, GroupInfoMapping mapping) throws SheetToolException {
		if (groupCode == null || groupCode.trim().isEmpty()) {
			throw new SheetToolException("群組代號不可為空");
		}
		if (mapping == null) {
			throw new SheetToolException("groupInfo 欄位對應設定為 null");
		}
		String wanted = groupCode.trim();
		int codeIndex = A1RangeBuilder.columnLetterToIndex(mapping.getGroupCodeColumn()) - baseColumn;
		List<List<Object>> safeRows = SheetValues.nullSafe(rows);

		List<int[]> runs = new ArrayList<>();
		int runStart = -1;
		for (int i = 0; i < safeRows.size(); i++) {
			boolean hit = wanted.equalsIgnoreCase(SheetValues.cell(safeRows.get(i), codeIndex).trim());
			if (hit && runStart < 0) {
				runStart = i;
			} else if (!hit && runStart >= 0) {
				runs.add(new int[] { runStart, i - 1 });
				runStart = -1;
			}
		}
		if (runStart >= 0) {
			runs.add(new int[] { runStart, safeRows.size() - 1 });
		}

		if (runs.isEmpty()) {
			throw new SheetToolException("在 " + mapping.getGroupCodeColumn() + " 欄找不到群組代號 '"
				+ wanted + "'。請確認代號是否正確，或用 --rows 放寬搜尋範圍"
				+ "（目前搜尋了第 " + baseRow + "~" + (baseRow + safeRows.size() - 1) + " 列）");
		}
		if (runs.size() > 1) {
			StringBuilder sb = new StringBuilder();
			for (int[] run : runs) {
				sb.append(" 第").append(baseRow + run[0]).append("-").append(baseRow + run[1]).append("列");
			}
			throw new SheetToolException("群組代號 '" + wanted + "' 出現在 " + runs.size()
				+ " 個不相鄰的區塊:" + sb + "。無法判斷該用哪一個，請用 --rows 縮小範圍");
		}

		int[] run = runs.get(0);
		int size = run[1] - run[0] + 1;
		if (size > mapping.getMaxRowsPerGroup()) {
			throw new SheetToolException("群組 '" + wanted + "' 佔了 " + size + " 列（第"
				+ (baseRow + run[0]) + "-" + (baseRow + run[1]) + "列），超過 maxRowsPerGroup="
				+ mapping.getMaxRowsPerGroup() + "。若這是正常的，請調高設定檔的 maxRowsPerGroup");
		}
		return new GroupBlock(wanted, new ArrayList<>(safeRows.subList(run[0], run[1] + 1)),
			baseRow + run[0], baseRow + run[1]);
	}

	/** 便利多載，讓 {@link #locate} 的結果可以直接往下接 */
	public static MapResult map(GroupBlock block, int rangeBaseColumnIndex, GroupInfoMapping mapping)
			throws SheetToolException {
		if (block == null) {
			throw new SheetToolException("群組區塊為 null");
		}
		return map(block.getRows(), rangeBaseColumnIndex, mapping);
	}

	/**
	 * 把一個群組區塊的列資料對應成 {@link GroupInfo}。
	 *
	 * @param blockRows            該群組的列資料，順序即列順序，第 0 個是群組的第 1 列
	 * @param rangeBaseColumnIndex 涵蓋範圍起始欄的絕對 0-based index（A=0）
	 * @param mapping              欄位對應設定
	 */
	public static MapResult map(List<List<Object>> blockRows, int rangeBaseColumnIndex,
			GroupInfoMapping mapping) throws SheetToolException {
		if (blockRows == null || blockRows.isEmpty()) {
			throw new SheetToolException("群組區塊沒有任何列資料");
		}
		if (mapping == null) {
			throw new SheetToolException("groupInfo 欄位對應設定為 null");
		}
		List<String> warnings = new ArrayList<>();
		List<String> backupSources = new ArrayList<>();
		List<Object> firstRow = blockRows.get(0);

		GroupInfo info = new GroupInfo();
		info.setPrivateIpSetId(
			valueAt(firstRow, mapping.getPrivateIpSetIdColumn(), rangeBaseColumnIndex));

		// privateIp：群組第 1 列的 J / K
		List<String> privateIp = new ArrayList<>();
		for (String column : mapping.getPrivateIpColumns()) {
			String value = valueAt(firstRow, column, rangeBaseColumnIndex);
			if (!value.isEmpty() && !isEmptyMarker(value, mapping.getEmptyMarkers())) {
				privateIp.add(value);
			}
		}
		info.setPrivateIp(privateIp);

		// bkIpSetId：同一欄（M）跨列取，依列順序。階段 3 實測確認第 1 列是 GA、第 2 列是 CF，
		// 位置與 WAF Name 都對得上，所以用位置取值，再用 WAF Name 交叉驗證（不符只警告）
		List<String> bkIpSetId = new ArrayList<>();
		for (int i = 0; i < blockRows.size(); i++) {
			String value = valueAt(blockRows.get(i), mapping.getBkIpSetIdColumn(), rangeBaseColumnIndex);
			if (value.isEmpty() || isEmptyMarker(value, mapping.getEmptyMarkers())) {
				continue;
			}
			bkIpSetId.add(value);
			verifyWafName(blockRows.get(i), i, bkIpSetId.size() - 1, rangeBaseColumnIndex,
				mapping, warnings);
		}
		info.setBkIpSetId(bkIpSetId);

		String apiInfoBkIpSetId =
			valueAt(firstRow, mapping.getApiInfoBkIpSetIdColumn(), rangeBaseColumnIndex);
		info.setApiInfoBkIpSetId(apiInfoBkIpSetId);
		if (!apiInfoBkIpSetId.isEmpty() && !UUID_SHAPE.matcher(apiInfoBkIpSetId).matches()) {
			// 已知會發生：A58 的 S 欄值是 "Cloudfront" 而不是 UUID。原樣回傳，不當錯誤
			warnings.add("apiInfoBkIpSetId 不是 UUID 格式，原樣回傳: " + apiInfoBkIpSetId);
		}

		// backup：欄優先、欄內依列 —— U1 → U2 → V1 → V2 → W1 → W2 → X1 → X2 → Y1 → Y2
		List<String> backup = new ArrayList<>();
		for (String column : mapping.getBackupColumns()) {
			for (int rowIndex = 0; rowIndex < blockRows.size(); rowIndex++) {
				String raw = valueAt(blockRows.get(rowIndex), column, rangeBaseColumnIndex);
				List<String> values = splitCell(raw, mapping.getEmptyMarkers());
				if (!values.isEmpty()) {
					backup.addAll(values);
					backupSources.add(column + (rowIndex + 1) + "(" + values.size() + ")");
				}
			}
		}
		info.setBackup(backup);

		return new MapResult(info, backupSources, warnings);
	}

	/**
	 * 用 WAF Name 交叉驗證 bkIpSetId 的順序：第 1 筆該是 GA、第 2 筆該是 CF。
	 *
	 * <p>只警告不擋 —— 順序判斷用的是列位置，這裡只是幫忙抓「試算表改版後順序換了」這種情況。
	 * 名稱的分隔符不一致（{@code A69-BK-WEB-GA-RU3} 與 {@code A04_BK_WEB_GA_RU2} 並存），
	 * 所以比對前先把底線正規化成連字號。
	 */
	private static void verifyWafName(List<Object> row, int rowIndex, int valueIndex,
			int rangeBaseColumnIndex, GroupInfoMapping mapping, List<String> warnings) {
		String column = mapping.getBkIpSetIdWafNameColumn();
		if (column == null || column.trim().isEmpty() || valueIndex > 1) {
			return;
		}
		String wafName = valueAt(row, column, rangeBaseColumnIndex);
		if (wafName.isEmpty()) {
			return;
		}
		String normalized = wafName.replace('_', '-').toUpperCase();
		String expected = valueIndex == 0 ? "-GA-" : "-CF-";
		if (!normalized.contains(expected)) {
			warnings.add("bkIpSetId[" + valueIndex + "] 取自第 " + (rowIndex + 1)
				+ " 列，但該列的 WAF Name 是 '" + wafName + "'，預期包含 " + expected
				+ "。試算表的列順序可能變了，請人工確認");
		}
	}

	/** 取某一列指定欄位的字串值，已換算 range base；越界或 null 回空字串 */
	private static String valueAt(List<Object> row, String columnLetter, int rangeBaseColumnIndex) {
		int absolute = A1RangeBuilder.columnLetterToIndex(columnLetter);
		return SheetValues.cell(row, absolute - rangeBaseColumnIndex).trim();
	}

	private static boolean isEmptyMarker(String value, List<String> emptyMarkers) {
		if (emptyMarkers == null) {
			return false;
		}
		for (String marker : emptyMarkers) {
			if (marker != null && marker.equals(value)) {
				return true;
			}
		}
		return false;
	}

	/** 一個群組所佔的連續列區塊 */
	public static final class GroupBlock {

		private final String groupCode;
		private final List<List<Object>> rows;
		private final int startRow;
		private final int endRow;

		GroupBlock(String groupCode, List<List<Object>> rows, int startRow, int endRow) {
			this.groupCode = groupCode;
			this.rows = Collections.unmodifiableList(rows);
			this.startRow = startRow;
			this.endRow = endRow;
		}

		public String getGroupCode() {
			return groupCode;
		}

		public List<List<Object>> getRows() {
			return rows;
		}

		/** 試算表列號（1-based） */
		public int getStartRow() {
			return startRow;
		}

		public int getEndRow() {
			return endRow;
		}

		public int size() {
			return rows.size();
		}
	}

	/**
	 * 對應結果：{@link GroupInfo} 加上診斷資訊。
	 *
	 * <p>{@code backupSources} 是給 {@code -t} 模式印的，形如 {@code U1(5) U2(3)} ——
	 * 之後 backup 與已知值對不上時，這是唯一能快速定位「哪一格切錯」的資訊。
	 */
	public static final class MapResult {

		private final GroupInfo groupInfo;
		private final List<String> backupSources;
		private final List<String> warnings;

		MapResult(GroupInfo groupInfo, List<String> backupSources, List<String> warnings) {
			this.groupInfo = groupInfo;
			this.backupSources = Collections.unmodifiableList(backupSources);
			this.warnings = Collections.unmodifiableList(warnings);
		}

		public GroupInfo getGroupInfo() {
			return groupInfo;
		}

		/** 每一格 backup 的來源與取出數量，例如 {@code ["U1(5)", "U2(3)"]} */
		public List<String> getBackupSources() {
			return backupSources;
		}

		public List<String> getWarnings() {
			return warnings;
		}
	}
}
