package tool.whiteLabel;

/**
 * 單號要求的環境名稱在共用設定檔與 EnvEnumType 內建值裡都找不到。
 * 取代舊版 EnvEnumType.valueOf 丟出的 IllegalArgumentException。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class UnknownEnvironmentException extends EnvValuesException {

	public UnknownEnvironmentException(String message) {
		super(message);
	}
}
