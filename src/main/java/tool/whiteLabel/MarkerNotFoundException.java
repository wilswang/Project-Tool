package tool.whiteLabel;

/**
 * 在目標檔案裡找不到插入點 marker。
 *
 * <p>原本 {@code insertAtMarker} 找不到 marker 就什麼都不插，然後<b>無條件</b>印
 * {@code ✅ Content successfully written} 並把原內容照樣寫回 —— 檔案看起來被處理過，
 * 實際上少了一整段程式碼，而 step 3 與後續的編譯都不會有任何抱怨
 * （少的那段通常是註冊用的 map put，不編譯錯，只是執行期少一個站台）。
 *
 * <p>marker 會不見的原因通常是目標檔重構過、註解被改寫、或設定檔的 marker 打錯字。
 * 這些都該當場中止，而不是靜靜產出一個不完整的結果。
 *
 * @author Wilson.Wang
 * @version 1.5.2
 */
public class MarkerNotFoundException extends Exception {

	private static final long serialVersionUID = 1L;

	public MarkerNotFoundException(String message) {
		super(message);
	}
}
