package util.placeholder;

import java.util.List;

/**
 * 填值後仍有 placeholder 沒被解析。代表模板寫錯名字、或設定檔／單號 JSON 漏給值。
 *
 * 這種情況下產出的檔案是壞的，而下游 SQL-processing.sh 會把 SIM 檔 append 進 release_sql、
 * 把 DEV 檔灌進真實資料庫，所以一律在寫檔之前擋掉，不寫出去。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class UnresolvedPlaceholderException extends Exception {

	private final String label;
	private final List<PlaceholderValidator.Unresolved> unresolvedList;

	public UnresolvedPlaceholderException(String label, List<PlaceholderValidator.Unresolved> unresolvedList) {
		super(buildMessage(label, unresolvedList));
		this.label = label;
		this.unresolvedList = unresolvedList;
	}

	public String getLabel() {
		return label;
	}

	public List<PlaceholderValidator.Unresolved> getUnresolvedList() {
		return unresolvedList;
	}

	private static String buildMessage(String label, List<PlaceholderValidator.Unresolved> unresolvedList) {
		StringBuilder sb = new StringBuilder();
		sb.append("未解析的 placeholder，已中止寫入: ").append(label);
		for (PlaceholderValidator.Unresolved unresolved : unresolvedList) {
			sb.append("\n   line ").append(unresolved.getLineNumber()).append(": ").append(unresolved.getToken());
			sb.append("\n     ").append(unresolved.getLineText());
		}
		return sb.toString();
	}
}
