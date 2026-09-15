package tool.sheet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A1 notation 的組裝與欄位字母換算。
 *
 * <p>純函數、零外部相依，所以不需要憑證就能完整單元測試。
 * 欄位一律用字母指定（A / H / J-K / U:V / H,J-K,S），不用標題列名稱。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class A1RangeBuilder {

	/** 只有字母、數字、底線的分頁名可以不加引號，其餘一律加 */
	private static final Pattern SAFE_TITLE = Pattern.compile("^[A-Za-z0-9_]+$");

	private static final Pattern COLUMN_LETTER = Pattern.compile("^[A-Za-z]+$");

	/** 欄位範圍的分隔符，`-` 與 `:` 都接受 */
	private static final Pattern RANGE_SEPARATOR = Pattern.compile("[-:]");

	private A1RangeBuilder() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 欄位字母轉 0-based index。A→0、Z→25、AA→26、AZ→51、BA→52。
	 *
	 * @throws IllegalArgumentException 空值或含非字母字元
	 */
	public static int columnLetterToIndex(String letter) {
		if (letter == null || letter.trim().isEmpty()) {
			throw new IllegalArgumentException("欄位字母不可為空");
		}
		String upper = letter.trim().toUpperCase();
		if (!COLUMN_LETTER.matcher(upper).matches()) {
			throw new IllegalArgumentException("欄位字母只能是英文字母: " + letter);
		}
		int index = 0;
		for (int i = 0; i < upper.length(); i++) {
			index = index * 26 + (upper.charAt(i) - 'A' + 1);
		}
		return index - 1;
	}

	/**
	 * 0-based index 轉欄位字母，是 {@link #columnLetterToIndex(String)} 的反向操作。
	 *
	 * @throws IllegalArgumentException index 為負數
	 */
	public static String indexToColumnLetter(int index) {
		if (index < 0) {
			throw new IllegalArgumentException("欄位 index 不可為負數: " + index);
		}
		StringBuilder sb = new StringBuilder();
		int remaining = index;
		while (remaining >= 0) {
			sb.insert(0, (char) ('A' + remaining % 26));
			remaining = remaining / 26 - 1;
		}
		return sb.toString();
	}

	/**
	 * 解析欄位指定字串，展開成個別欄位字母的清單（保順序、去重複）。
	 *
	 * <p>接受的形式：{@code "H"}、{@code "J-K"}、{@code "U:V"}、{@code "H,J-K,S"}。
	 * 範圍寫反（{@code "F-C"}）會自動正規化成 C..F。
	 *
	 * @throws IllegalArgumentException 空值或格式不合
	 */
	public static List<String> parseColumnSpec(String spec) {
		if (spec == null || spec.trim().isEmpty()) {
			throw new IllegalArgumentException("欄位指定不可為空");
		}
		// LinkedHashSet 同時做到「保順序」與「去重複」
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String part : spec.split(",")) {
			String token = part.trim();
			if (token.isEmpty()) {
				continue;
			}
			String[] bounds = RANGE_SEPARATOR.split(token);
			if (bounds.length == 1) {
				result.add(normalizeLetter(bounds[0]));
			} else if (bounds.length == 2) {
				int from = columnLetterToIndex(bounds[0]);
				int to = columnLetterToIndex(bounds[1]);
				// 寫反了就交換，不要報錯
				int lower = Math.min(from, to);
				int upper = Math.max(from, to);
				for (int i = lower; i <= upper; i++) {
					result.add(indexToColumnLetter(i));
				}
			} else {
				throw new IllegalArgumentException("欄位範圍格式不合，應為 A-C 或 A:C: " + token);
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("欄位指定解析後為空: " + spec);
		}
		return new ArrayList<>(result);
	}

	/**
	 * 分頁名跳脫。含空白、CJK 或任何非 {@code [A-Za-z0-9_]} 的字元時加單引號，
	 * 名稱內原有的單引號要 doubling。
	 *
	 * <p>例：{@code API 2.0Domain配置_20260814} → {@code 'API 2.0Domain配置_20260814'}
	 */
	public static String quoteSheetTitle(String title) {
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("分頁名不可為空");
		}
		String trimmed = title.trim();
		if (SAFE_TITLE.matcher(trimmed).matches()) {
			return trimmed;
		}
		return "'" + trimmed.replace("'", "''") + "'";
	}

	/**
	 * 組出一段「涵蓋範圍」—— 從所有指定欄位的最小欄到最大欄。
	 *
	 * <p>刻意讀成一段連續範圍而不是逐欄 batchGet，理由有兩個：一次 API 呼叫；
	 * 以及**保留列對齊**，讓後續能用相對列位取值，不必重新對齊列號。
	 *
	 * @param tab      分頁名，內部會做跳脫
	 * @param columns  欄位字母清單，至少一個
	 * @param startRow 1-based inclusive，null 表不設下限
	 * @param endRow   1-based inclusive，null 表不設上限
	 * @return 例如 {@code 'Tab'!H4:V200}；startRow/endRow 皆為 null 時是 {@code 'Tab'!H:V}
	 */
	public static String buildCoveringRange(String tab, List<String> columns, Integer startRow, Integer endRow) {
		if (columns == null || columns.isEmpty()) {
			throw new IllegalArgumentException("欄位清單不可為空");
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (String column : columns) {
			int index = columnLetterToIndex(column);
			min = Math.min(min, index);
			max = Math.max(max, index);
		}
		if (startRow != null && startRow < 1) {
			throw new IllegalArgumentException("startRow 必須 >= 1: " + startRow);
		}
		if (endRow != null && endRow < 1) {
			throw new IllegalArgumentException("endRow 必須 >= 1: " + endRow);
		}
		if (startRow != null && endRow != null && endRow < startRow) {
			throw new IllegalArgumentException("endRow 不可小於 startRow: " + startRow + ":" + endRow);
		}
		String firstColumn = indexToColumnLetter(min);
		String lastColumn = indexToColumnLetter(max);
		return quoteSheetTitle(tab) + "!"
			+ firstColumn + (startRow == null ? "" : String.valueOf(startRow))
			+ ":"
			+ lastColumn + (endRow == null ? "" : String.valueOf(endRow));
	}

	/**
	 * 涵蓋範圍的起始欄 index，用來把絕對欄位 index 換算成回傳資料裡的相對 index。
	 */
	public static int coveringRangeBaseColumn(List<String> columns) {
		if (columns == null || columns.isEmpty()) {
			throw new IllegalArgumentException("欄位清單不可為空");
		}
		int min = Integer.MAX_VALUE;
		for (String column : columns) {
			min = Math.min(min, columnLetterToIndex(column));
		}
		return min;
	}

	private static String normalizeLetter(String letter) {
		// 走一趟換算，順便驗證格式，回傳大寫正規形式
		return indexToColumnLetter(columnLetterToIndex(letter));
	}
}
