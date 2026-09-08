package tool.whiteLabel;

/**
 * 環境值設定檔（config/env-values.json）或單號 envValues 區塊的格式錯誤。
 * 這類錯誤代表操作者本來就想設定某個值，所以一律在產出任何檔案之前中止，不做猜測。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class EnvValuesException extends Exception {

	public EnvValuesException(String message) {
		super(message);
	}

	public EnvValuesException(String message, Throwable cause) {
		super(message, cause);
	}
}
