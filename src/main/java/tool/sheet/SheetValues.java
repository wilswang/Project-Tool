package tool.sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sheets API 回傳值的正規化與欄位投影。
 *
 * <p>存在的理由是 Sheets API 兩個一定會踩到的行為：
 * <ol>
 *   <li>範圍全空時 {@code ValueRange.getValues()} 回 <b>null</b> 而不是空 List</li>
 *   <li>API 會<b>截掉每列尾端的空白儲存格</b>，所以回來的每列長度參差不齊。
 *       任何 {@code row.get(i)} 都必須經過 {@link #cell(List, int)}，否則讀後面的欄位
 *       （例如 backup 的 U~Y）幾乎一定會 IndexOutOfBounds</li>
 * </ol>
 *
 * <p>刻意不吃 {@code ValueRange} 型別，只吃 {@code List<List<Object>>}，
 * 讓這一層完全不相依 Google 套件、可以離線測。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class SheetValues {

	private SheetValues() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/** null 值一律轉成空 List，讓呼叫端不用到處判空 */
	public static List<List<Object>> nullSafe(List<List<Object>> values) {
		if (values == null) {
			return Collections.emptyList();
		}
		return values;
	}

	/**
	 * 安全取值：列為 null、index 越界、值為 null 一律回空字串。
	 *
	 * @param row   一列資料，可為 null
	 * @param index 該列內的 0-based index（已換算過 range base）
	 */
	public static String cell(List<Object> row, int index) {
		if (row == null || index < 0 || index >= row.size()) {
			return "";
		}
		Object value = row.get(index);
		return value == null ? "" : value.toString();
	}

	/**
	 * 從涵蓋範圍的資料裡投影出指定的欄位。
	 *
	 * @param rows                 涵蓋範圍回傳的列資料
	 * @param absoluteColumnIndexes 要取的欄位「絕對」0-based index（A=0）
	 * @param rangeBaseColumnIndex  涵蓋範圍起始欄的絕對 index，用來換算相對位置
	 * @return 每列一個 List，元素順序與 absoluteColumnIndexes 相同，缺值為空字串
	 */
	public static List<List<String>> project(List<List<Object>> rows,
			List<Integer> absoluteColumnIndexes, int rangeBaseColumnIndex) {
		List<List<String>> result = new ArrayList<>();
		for (List<Object> row : nullSafe(rows)) {
			List<String> projected = new ArrayList<>(absoluteColumnIndexes.size());
			for (Integer absolute : absoluteColumnIndexes) {
				projected.add(cell(row, absolute - rangeBaseColumnIndex));
			}
			result.add(projected);
		}
		return result;
	}
}
