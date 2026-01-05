package tool.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Client 工具類別，使用 OkHttp
 * 支援 GET 和 POST，包含 Basic Authentication
 */
public class HttpClient {

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private final OkHttpClient client;
	private final HttpClientConfig config;
	private final ObjectMapper objectMapper;
	private final String authHeader;

	/**
	 * 建構子，接受配置
	 */
	public HttpClient(HttpClientConfig config) {
		config.validate();
		this.config = config;
		this.objectMapper = new ObjectMapper();

		// 建構 Basic Auth header
		String credentials = config.getAccount() + ":" + config.getToken();
		this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

		// 建構 OkHttp client 並配置
		this.client = new OkHttpClient.Builder()
				.connectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS)
				.readTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS)
				.writeTimeout(config.getWriteTimeout(), TimeUnit.MILLISECONDS)
				.connectionPool(new ConnectionPool(
						config.getMaxIdleConnections(),
						config.getKeepAliveDuration(),
						TimeUnit.MILLISECONDS
				))
				.build();
	}

	/**
	 * 執行 GET 請求
	 *
	 * @param endpoint API endpoint（相對於 baseUrl）
	 * @return HttpResponse 包裝
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse get(String endpoint) throws IOException {
		return get(endpoint, null);
	}

	/**
	 * 執行 GET 請求，包含 Query Parameters
	 *
	 * @param endpoint    API endpoint（相對於 baseUrl）
	 * @param queryParams Query parameters（可為 null）
	 * @return HttpResponse 包裝
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse get(String endpoint, Map<String, String> queryParams) throws IOException {
		String url = buildUrlWithParams(endpoint, queryParams);

		Request request = new Request.Builder()
				.url(url)
				.header("Authorization", authHeader)
				.header("Accept", "application/json")
				.get()
				.build();

		System.out.println("GET " + url);
		return executeRequest(request);
	}

	/**
	 * 執行 POST 請求，包含 JSON body
	 *
	 * @param endpoint API endpoint（相對於 baseUrl）
	 * @param jsonBody JSON 請求 body 字串
	 * @return HttpResponse 包裝
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse post(String endpoint, String jsonBody) throws IOException {
		String url = buildUrl(endpoint);

		RequestBody body = RequestBody.create(jsonBody, JSON);
		Request request = new Request.Builder()
				.url(url)
				.header("Authorization", authHeader)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.post(body)
				.build();

		System.out.println("POST " + url);
		return executeRequest(request);
	}

	/**
	 * 執行 POST 請求，從檔案載入 JSON body
	 *
	 * @param endpoint     API endpoint（相對於 baseUrl）
	 * @param jsonFilePath JSON 檔案路徑（支援外部檔案和 classpath）
	 * @return HttpResponse 包裝
	 * @throws IOException 檔案讀取或網路錯誤時
	 */
	public HttpResponse postFromFile(String endpoint, String jsonFilePath) throws IOException {
		String jsonBody = loadJsonFromFile(jsonFilePath);
		return post(endpoint, jsonBody);
	}

	/**
	 * 執行 HTTP 請求並包裝回應
	 */
	private HttpResponse executeRequest(Request request) throws IOException {
		long startTime = System.currentTimeMillis();

		try (Response response = client.newCall(request).execute()) {
			long duration = System.currentTimeMillis() - startTime;

			String responseBody = response.body() != null ? response.body().string() : "";

			HttpResponse httpResponse = new HttpResponse(
					response.code(),
					responseBody,
					response.isSuccessful(),
					duration
			);

			// 輸出結果，模仿 UrlChecker.java 的風格
			if (response.isSuccessful()) {
				System.out.println("✅ [OK] " + response.code() + " (" + duration + "ms)");
			} else {
				System.err.println("❌ [FAILED] " + response.code() + " " + response.message() + " (" + duration + "ms)");
			}

			return httpResponse;
		}
	}

	/**
	 * 從檔案載入 JSON 內容
	 * 支援外部檔案路徑和 classpath resources
	 */
	private String loadJsonFromFile(String filePath) throws IOException {
		File file = new File(filePath);

		if (file.exists()) {
			// 外部檔案
			return objectMapper.readTree(file).toString();
		} else {
			// 嘗試從 classpath resource 載入
			InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
			if (is == null) {
				throw new IOException("JSON 檔案不存在: " + filePath);
			}
			return objectMapper.readTree(is).toString();
		}
	}

	/**
	 * 建構完整 URL（baseUrl + endpoint）
	 */
	private String buildUrl(String endpoint) {
		String base = config.getBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (!endpoint.startsWith("/")) {
			endpoint = "/" + endpoint;
		}
		return base + endpoint;
	}

	/**
	 * 建構完整 URL，包含 Query Parameters
	 */
	private String buildUrlWithParams(String endpoint, Map<String, String> queryParams) {
		String baseUrl = buildUrl(endpoint);

		if (queryParams == null || queryParams.isEmpty()) {
			return baseUrl;
		}

		HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
		for (Map.Entry<String, String> entry : queryParams.entrySet()) {
			urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
		}

		return urlBuilder.build().toString();
	}

	/**
	 * 取得底層 OkHttpClient（進階使用）
	 */
	public OkHttpClient getOkHttpClient() {
		return client;
	}

	/**
	 * 取得 ObjectMapper（JSON 操作）
	 */
	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}
}
