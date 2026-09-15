package tool.http;

/**
 * Jira 工具的 checked exception，比照 {@link tool.sheet.SheetToolException} 的定位。
 *
 * <p>刻意用 checked exception 而不是在各處直接 {@code System.exit(1)}：
 * 退出碼一律由 {@link JiraTool#main(String[])} 統一負責，其餘方法只負責把錯誤往上拋，
 * 這樣每一層都能單獨單元測試。
 *
 * <p>會丟這個例外的是<b>工具自己判定的失敗</b>（參數不足、單子狀態不對），
 * 不含底層的 IO / HTTP 例外 —— 那些原樣往上拋，由 {@code main} 的大 catch 處理。
 *
 * @author Wilson.Wang
 * @version 1.5.1
 */
public class JiraToolException extends Exception {

	private static final long serialVersionUID = 1L;

	public JiraToolException(String message) {
		super(message);
	}

	public JiraToolException(String message, Throwable cause) {
		super(message, cause);
	}
}
