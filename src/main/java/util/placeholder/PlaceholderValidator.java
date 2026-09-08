package util.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 檢查填值後的內容有沒有殘留 placeholder。
 *
 * 比對刻意用嚴格規則：{ 之後必須緊接 $，$ 之後必須是字母或底線。
 * 這樣才不會誤判真實 SQL 語法 ——
 *   MySQL JSON path  '$."44"'         $ 前面是單引號，不是 {
 *   JSON 物件字面值  '{"key": 1}'      { 後面是雙引號，不是 $
 * 也刻意不匹配 {$ foo}、{$}、{$1abc} 這類不合命名規則的形狀，把誤判面積壓到零；
 * 而真正要抓的「placeholder 名字打錯」一定符合命名規則，所以抓得到。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public final class PlaceholderValidator {

	/** 嚴格比對：{$ 後必須是字母或底線 */
	public static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\$[A-Za-z_][A-Za-z0-9_.]*\\}");

	private PlaceholderValidator() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 掃描已填值的內容，回傳所有殘留的 placeholder（含行號與該行原文）。
	 */
	public static List<Unresolved> findUnresolved(String content) {
		List<Unresolved> unresolvedList = new ArrayList<>();
		if (content == null || content.isEmpty()) {
			return unresolvedList;
		}

		String[] lines = content.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			Matcher matcher = PLACEHOLDER_PATTERN.matcher(lines[i]);
			while (matcher.find()) {
				unresolvedList.add(new Unresolved(i + 1, matcher.group(), lines[i].trim()));
			}
		}
		return unresolvedList;
	}

	/**
	 * 有殘留就丟例外；沒有則什麼都不做。
	 *
	 * @param label 出現在錯誤訊息裡的識別字串，通常是輸出檔路徑
	 */
	public static void requireFullyResolved(String label, String content) throws UnresolvedPlaceholderException {
		List<Unresolved> unresolvedList = findUnresolved(content);
		if (!unresolvedList.isEmpty()) {
			throw new UnresolvedPlaceholderException(label, unresolvedList);
		}
	}

	/**
	 * 一筆殘留的 placeholder。
	 */
	public static class Unresolved {

		private final int lineNumber;
		private final String token;
		private final String lineText;

		Unresolved(int lineNumber, String token, String lineText) {
			this.lineNumber = lineNumber;
			this.token = token;
			this.lineText = lineText;
		}

		/** 1 起算 */
		public int getLineNumber() {
			return lineNumber;
		}

		public String getToken() {
			return token;
		}

		public String getLineText() {
			return lineText;
		}
	}
}
