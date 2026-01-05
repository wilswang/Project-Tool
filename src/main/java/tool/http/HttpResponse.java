package tool.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;

/**
 * HTTP Response 包裝類別
 * 提供便利的方法處理回應
 */
@Data
public class HttpResponse {

	private final int statusCode;
	private final String body;
	private final boolean successful;
	private final long durationMs;

	public HttpResponse(int statusCode, String body, boolean successful, long durationMs) {
		this.statusCode = statusCode;
		this.body = body;
		this.successful = successful;
		this.durationMs = durationMs;
	}

	/**
	 * 將回應 body 解析為指定類型的 JSON 物件
	 *
	 * @param clazz 目標類別
	 * @return 反序列化的物件
	 * @throws IOException 如果解析失敗
	 */
	public <T> T parseJson(Class<T> clazz) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(body, clazz);
	}

	/**
	 * 檢查回應是否成功（2xx 狀態碼）
	 */
	public boolean isSuccessful() {
		return successful;
	}

	/**
	 * 如果回應不成功則拋出異常
	 */
	public void throwIfNotSuccessful() {
		if (!successful) {
			throw new RuntimeException("HTTP 請求失敗: " + statusCode + " - " + body);
		}
	}
}
