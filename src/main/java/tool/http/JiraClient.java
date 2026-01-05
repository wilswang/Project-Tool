package tool.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Jira API Client
 * 提供便利的方法進行常見的 Jira 操作
 * 使用 Jira REST API v3
 */
public class JiraClient {

	private static final String DEFAULT_POST_COMMENT_TEMPLATE = "post-comment.json";
	private static final String DEFAULT_TRANSITION_ISSUE_TEMPLATE = "transition-issue.json";
	private static final String DEFAULT_ENHANCED_SEARCH_TEMPLATE = "enhanced-search.json";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	/**
	 * 建構子，接受配置
	 */
	public JiraClient(HttpClientConfig config) {
		this.httpClient = new HttpClient(config);
		this.objectMapper = new ObjectMapper();
	}

	// ==================== GET 方法 ====================

	/**
	 * 取得 Jira issue 詳情
	 *
	 * @param issueKey Issue key（例如 "SA_CRIC-123"）
	 * @return Issue 詳情 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getIssue(String issueKey) throws IOException {
		return getIssue(issueKey, null);
	}

	/**
	 * 取得 Jira issue 詳情，包含 Query Parameters
	 *
	 * @param issueKey    Issue key（例如 "SA_CRIC-123"）
	 * @param queryParams Query parameters（可為 null）
	 * @return Issue 詳情 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getIssue(String issueKey, Map<String, String> queryParams) throws IOException {
		HttpResponse response = httpClient.get("/rest/api/3/issue/" + issueKey, queryParams);
		response.throwIfNotSuccessful();
		return objectMapper.readTree(response.getBody());
	}

	/**
	 * 取得 issue 的所有留言
	 *
	 * @param issueKey Issue key（例如 "SA_CRIC-123"）
	 * @return 留言列表 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getComments(String issueKey) throws IOException {
		return getComments(issueKey, null);
	}

	/**
	 * 取得 issue 的所有留言，包含 Query Parameters
	 *
	 * @param issueKey    Issue key（例如 "SA_CRIC-123"）
	 * @param queryParams Query parameters（可為 null）
	 * @return 留言列表 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getComments(String issueKey, Map<String, String> queryParams) throws IOException {
		HttpResponse response = httpClient.get("/rest/api/3/issue/" + issueKey + "/comment", queryParams);
		response.throwIfNotSuccessful();
		return objectMapper.readTree(response.getBody());
	}

	/**
	 * 取得 issue 可用的狀態轉換
	 *
	 * @param issueKey Issue key（例如 "SA_CRIC-123"）
	 * @return 可用轉換列表 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getTransitions(String issueKey) throws IOException {
		return getTransitions(issueKey, null);
	}

	/**
	 * 取得 issue 可用的狀態轉換，包含 Query Parameters
	 *
	 * @param issueKey    Issue key（例如 "SA_CRIC-123"）
	 * @param queryParams Query parameters（可為 null）
	 * @return 可用轉換列表 JSON
	 * @throws IOException 網路或解析錯誤時
	 */
	public JsonNode getTransitions(String issueKey, Map<String, String> queryParams) throws IOException {
		HttpResponse response = httpClient.get("/rest/api/3/issue/" + issueKey + "/transitions", queryParams);
		response.throwIfNotSuccessful();
		return objectMapper.readTree(response.getBody());
	}

	// ==================== POST 方法 ====================

	/**
	 * 在 issue 上新增留言
	 * 成功時回傳 201 Created
	 *
	 * @param issueKey Issue key（例如 "SA_CRIC-123"）
	 * @param jsonBody JSON 請求 body 字串（已修改完成的最終內容）
	 * @return 回應
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse postComment(String issueKey, String jsonBody) throws IOException {
		HttpResponse response = httpClient.post("/rest/api/3/issue/" + issueKey + "/comment", jsonBody);
		// Post Comment 成功時回傳 201 Created
		if (response.getStatusCode() != 201) {
			throw new RuntimeException("Post Comment 失敗: " + response.getStatusCode() + " - " + response.getBody());
		}
		return response;
	}

	/**
	 * 轉換 issue 狀態
	 * 成功時回傳 204 No Content
	 *
	 * @param issueKey Issue key（例如 "SA_CRIC-123"）
	 * @param jsonBody JSON 請求 body 字串（已修改完成的最終內容）
	 * @return 回應
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse transitionIssue(String issueKey, String jsonBody) throws IOException {
		HttpResponse response = httpClient.post("/rest/api/3/issue/" + issueKey + "/transitions", jsonBody);
		// Transition Issue 成功時回傳 204 No Content
		if (response.getStatusCode() != 204) {
			throw new RuntimeException("Transition Issue 失敗: " + response.getStatusCode() + " - " + response.getBody());
		}
		return response;
	}

	/**
	 * 使用 JQL 進行增強搜尋
	 *
	 * @param jsonBody JSON 請求 body 字串（已修改完成的最終內容）
	 * @return 回應
	 * @throws IOException 網路錯誤時
	 */
	public HttpResponse enhancedSearch(String jsonBody) throws IOException {
		HttpResponse response = httpClient.post("/rest/api/3/search/jql", jsonBody);
		response.throwIfNotSuccessful();
		return response;
	}

	// ==================== 工具方法：載入模板 ====================

	/**
	 * 從檔案載入 JSON 模板內容
	 * 支援外部檔案路徑和 classpath resources
	 * 載入後可進行修改，再傳入 POST 方法
	 *
	 * @param templatePath 模板檔案路徑
	 * @return JSON 字串
	 * @throws IOException 檔案不存在或讀取失敗時
	 */
	public String loadTemplate(String templatePath) throws IOException {
		File file = new File(templatePath);

		if (file.exists()) {
			// 外部檔案
			return objectMapper.readTree(file).toString();
		} else {
			// 嘗試從 classpath resource 載入
			InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath);
			if (is == null) {
				throw new IOException("模板檔案不存在: " + templatePath);
			}
			return objectMapper.readTree(is).toString();
		}
	}

	/**
	 * 從預設的 post-comment.json 載入模板
	 *
	 * @return JSON 字串
	 * @throws IOException 檔案不存在或讀取失敗時
	 */
	public String loadPostCommentTemplate() throws IOException {
		return loadTemplate(DEFAULT_POST_COMMENT_TEMPLATE);
	}

	/**
	 * 從預設的 transition-issue.json 載入模板
	 *
	 * @return JSON 字串
	 * @throws IOException 檔案不存在或讀取失敗時
	 */
	public String loadTransitionIssueTemplate() throws IOException {
		return loadTemplate(DEFAULT_TRANSITION_ISSUE_TEMPLATE);
	}

	/**
	 * 從預設的 enhanced-search.json 載入模板
	 *
	 * @return JSON 字串
	 * @throws IOException 檔案不存在或讀取失敗時
	 */
	public String loadEnhancedSearchTemplate() throws IOException {
		return loadTemplate(DEFAULT_ENHANCED_SEARCH_TEMPLATE);
	}

	/**
	 * 取得底層 HttpClient（進階使用）
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}
}
