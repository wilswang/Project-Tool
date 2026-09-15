package tool.sheet;

/**
 * Sheet 工具的 checked exception，比照 {@link tool.whiteLabel.EnvValuesException} 的定位。
 *
 * <p>刻意用 checked exception 而不是在各處直接 {@code System.exit(1)}：
 * 退出碼一律由 {@link SheetTool#main(String[])} 統一負責，其餘類別只負責把錯誤往上拋，
 * 這樣每一層都能單獨單元測試。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class SheetToolException extends Exception {

	private static final long serialVersionUID = 1L;

	public SheetToolException(String message) {
		super(message);
	}

	public SheetToolException(String message, Throwable cause) {
		super(message, cause);
	}
}
