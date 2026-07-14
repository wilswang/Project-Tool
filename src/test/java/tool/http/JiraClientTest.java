package tool.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * JiraClient 測試類
 * 測試 Jira API 調用和 Basic Auth
 */
public class JiraClientTest {

	private JiraClient jiraClient;
	private HttpClientConfig config;

	@Before
	public void setUp() throws IOException {
		// 從 application.properties 載入配置
		config = JiraConfig.fromProperties("application.properties");
		jiraClient = new JiraClient(config);

		System.out.println("\n=== 測試配置 ===");
		System.out.println("Base URL: " + config.getBaseUrl());
		System.out.println("Account: " + config.getAccount());
		System.out.println("Token: " + maskToken(config.getToken()));

		// 驗證 Basic Auth Header 格式
		String credentials = config.getAccount() + ":" + config.getToken();
		String expectedAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
		System.out.println("Expected Auth Header: Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()).substring(0, 20) + "...");
		System.out.println("================\n");
	}

	@Test
	public void testGetIssue_SA_CRIC_1020() throws IOException {
		System.out.println("=== 測試: Get Issue SA_CRIC-1020 ===");

		try {
			// 調用 getIssue（正確格式應該是 SA_CRIC-1020，中間有底線）
			JsonNode issue = jiraClient.getIssue("SACRIC-1020");

			// 驗證回應
			assertNotNull("Issue 不應為 null", issue);

			// 檢查基本欄位
			assertTrue("應包含 'key' 欄位", issue.has("key"));
			assertTrue("應包含 'fields' 欄位", issue.has("fields"));

			String issueKey = issue.get("key").asText();
			assertEquals("Issue key 應為 SACRIC-1020", "SACRIC-1020", issueKey);

			// 輸出 issue 資訊
			System.out.println("\n✅ 成功取得 Issue:");
			System.out.println("Key: " + issueKey);

			if (issue.get("fields").has("summary")) {
				String summary = issue.get("fields").get("summary").asText();
				System.out.println("Summary: " + summary);
			}

			if (issue.get("fields").has("status")) {
				String status = issue.get("fields").get("status").get("name").asText();
				System.out.println("Status: " + status);
			}

			System.out.println("\n完整 JSON 回應:");
			System.out.println(issue.toPrettyString());

		} catch (Exception e) {
			System.err.println("\n❌ 測試失敗:");
			System.err.println("錯誤訊息: " + e.getMessage());
			e.printStackTrace();
			fail("Get Issue 失敗: " + e.getMessage());
		}
	}

	@Test
	public void testJiraIssueFieldsConfig_LoadedFromProperties() throws IOException {
		System.out.println("=== 測試: jira.issue.fields 應從 application.properties 載入 ===");

		String fields = config.getFields();
		System.out.println("jira.issue.fields = " + fields);

		assertNotNull("config.getFields() 不應為 null（application.properties 需設定 jira.issue.fields）", fields);
		assertFalse("config.getFields() 不應為空字串", fields.trim().isEmpty());
		assertTrue("應包含 summary 欄位", fields.contains("summary"));

		System.out.println("✅ jira.issue.fields 讀取正確");
	}

	@Test
	public void testGetIssue_WithConfiguredFields_OnlyReturnsRequestedFields() throws IOException {
		System.out.println("=== 測試: 帶 fields 參數時，回應只包含指定欄位 ===");

		String issueKey = "SACRIC-1020";
		String fields = config.getFields();
		assertNotNull("測試前置條件: jira.issue.fields 需已設定", fields);

		Map<String, String> queryParams = new HashMap<>();
		queryParams.put("fields", fields);
		JsonNode issue = jiraClient.getIssue(issueKey, queryParams);

		JsonNode issueFields = issue.get("fields");
		assertNotNull("回應應包含 'fields' 節點", issueFields);

		Set<String> requestedFields = new HashSet<>(Arrays.asList(fields.split(",")));
		Iterator<String> actualFieldNames = issueFields.fieldNames();
		while (actualFieldNames.hasNext()) {
			String fieldName = actualFieldNames.next();
			assertTrue("回應欄位 '" + fieldName + "' 應在設定的 fields 清單內: " + fields,
				requestedFields.contains(fieldName));
		}

		System.out.println("✅ 回應欄位皆屬於設定清單，共 " + issueFields.size() + " 個欄位");
	}

	@Test
	public void testGetIssue_WithoutFieldsParam_ReturnsFullDefaultFieldSet() throws IOException {
		System.out.println("=== 測試: 不帶 fields 參數時，回應包含 Jira 預設完整欄位 ===");

		String issueKey = "SACRIC-1020";
		// 不傳 queryParams（相當於 handleGetIssue 在 jira.issue.fields 未設定時的行為）
		JsonNode issue = jiraClient.getIssue(issueKey);

		JsonNode issueFields = issue.get("fields");
		assertNotNull("回應應包含 'fields' 節點", issueFields);

		int configuredFieldCount = config.getFields().split(",").length;
		assertTrue("未帶 fields 參數時，回應欄位數量應多於設定清單筆數（Jira 會回傳完整預設欄位）",
			issueFields.size() > configuredFieldCount);

		System.out.println("✅ 未帶 fields 參數，Jira 回傳完整欄位，共 " + issueFields.size() + " 個欄位");
	}

	@Test
	public void testBasicAuthHeader() {
		System.out.println("=== 測試: Basic Auth Header 格式 ===");

		// 驗證 credentials 格式
		String credentials = config.getAccount() + ":" + config.getToken();
		System.out.println("Credentials 格式: account:token");
		System.out.println("Account 長度: " + config.getAccount().length());
		System.out.println("Token 長度: " + config.getToken().length());

		// 驗證 Base64 編碼
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
		System.out.println("Base64 編碼長度: " + encoded.length());
		System.out.println("Base64 編碼前 20 字元: " + encoded.substring(0, Math.min(20, encoded.length())) + "...");

		// 驗證可以解碼回原始值
		String decoded = new String(Base64.getDecoder().decode(encoded));
		assertEquals("解碼後應該等於原始 credentials", credentials, decoded);
		System.out.println("✅ Base64 編碼/解碼正確");

		// 驗證包含冒號分隔符
		assertTrue("Decoded credentials 應包含 ':'", decoded.contains(":"));
		String[] parts = decoded.split(":", 2);
		assertEquals("Account 應該相符", config.getAccount(), parts[0]);
		assertEquals("Token 應該相符", config.getToken(), parts[1]);
		System.out.println("✅ Credentials 格式正確");
	}

	@Test
	public void testConfigValidation() {
		System.out.println("=== 測試: Config 驗證 ===");

		assertNotNull("Config 不應為 null", config);
		assertNotNull("Base URL 不應為 null", config.getBaseUrl());
		assertNotNull("Account 不應為 null", config.getAccount());
		assertNotNull("Token 不應為 null", config.getToken());

		assertFalse("Base URL 不應為空", config.getBaseUrl().isEmpty());
		assertFalse("Account 不應為空", config.getAccount().isEmpty());
		assertFalse("Token 不應為空", config.getToken().isEmpty());

		assertTrue("Base URL 應以 https:// 開頭", config.getBaseUrl().startsWith("https://"));
		assertTrue("Account 應包含 @", config.getAccount().contains("@"));

		System.out.println("✅ Config 驗證通過");
	}

	@Test
	public void testSearchIssues() throws IOException {
		System.out.println("=== 測試: 使用 JQL 搜尋 Issues ===");

		try {
			// 使用簡單的 JQL 搜尋最近更新的 issues
			String jqlBody = "{\"jql\": \"project = SA_CRIC ORDER BY updated DESC\", \"maxResults\": 5, \"fields\": [\"key\", \"summary\", \"status\"]}";

			HttpResponse response = jiraClient.enhancedSearch(jqlBody);

			assertNotNull("回應不應為 null", response);
			assertTrue("應該成功", response.isSuccessful());
			assertEquals("Status code 應為 200", 200, response.getStatusCode());

			// 解析結果
			JsonNode results = response.parseJson(JsonNode.class);
			System.out.println("\n✅ JQL 搜尋成功!");
			System.out.println("Total: " + results.get("total").asInt());

			if (results.has("issues")) {
				System.out.println("\n找到的 Issues:");
				for (JsonNode issue : results.get("issues")) {
					String key = issue.get("key").asText();
					String summary = issue.get("fields").get("summary").asText();
					String status = issue.get("fields").get("status").get("name").asText();
					System.out.println("  - " + key + ": " + summary + " [" + status + "]");
				}
			}

		} catch (Exception e) {
			System.err.println("\n❌ 搜尋失敗:");
			System.err.println("錯誤訊息: " + e.getMessage());
			e.printStackTrace();
			fail("JQL 搜尋失敗: " + e.getMessage());
		}
	}

	/**
	 * 隱藏 token 的輔助方法
	 */
	private String maskToken(String token) {
		if (token == null || token.length() < 10) {
			return "****";
		}
		return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
	}
}
