package tool.http;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jira Tool - CLI 範例工具
 * 展示如何使用 HttpClient 和 JiraClient
 */
public class JiraTool {

	public static void main(String[] args) {
		// 處理 help 參數
		if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
			printUsage();
			return;
		}

		try {
			// Set up UTF-8 encoding for console output (Java 8 compatible)
			System.setOut(new PrintStream(System.out, true, "UTF-8"));

			// 解析 testMode 參數
			boolean testMode = false;
			List<String> argList = new ArrayList<>();
			for (String arg : args) {
				if ("-t".equals(arg) || "--testMode".equals(arg)) {
					testMode = true;
				} else {
					argList.add(arg);
				}
			}
			String[] filteredArgs = argList.toArray(new String[0]);

			if (testMode) {
				System.out.println("⚠️  Test mode enabled");
			}

			// 載入配置（只使用 application.properties）
			System.out.println("Using application.properties configuration");
			HttpClientConfig config = JiraConfig.fromProperties("application.properties");

			if (testMode) {
				System.out.println("\n=== Current Jira Config ===");
				printConfigExcludingToken(config);
				System.out.println("================================\n");
			}

			JiraClient jira = new JiraClient(config);

			String command = filteredArgs[0];
			switch (command) {
				case "get-issue":
					handleGetIssue(jira, filteredArgs);
					break;

				case "get-comments":
					List<String> comments = handleGetComments(jira, filteredArgs);
					System.out.println("Comments: " + comments);
					break;

				case "get-transitions":
					List<String> transitionIds = handleGetTransitions(jira, filteredArgs);
					System.out.println("Transition IDs: " + transitionIds);
					break;

				case "post-comment":
					handlePostComment(jira, filteredArgs, testMode);
					break;

				case "transition-issue":
					handleTransitionIssue(jira, filteredArgs, testMode);
					break;

				case "enhanced-search":
					handleEnhancedSearch(jira, filteredArgs);
					break;

				case "start-jira-issue":
					startJiraIssue(jira, filteredArgs, testMode);
					break;

				default:
					System.err.println("Unknown command: " + command);
					printUsage();
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void handleGetIssue(JiraClient jira, String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool get-issue <issueKey>");
			return;
		}

		String issueKey = args[1];
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put("fields", "id,key,assignee,fixVersions,status,customfield_10072,customfield_10037,comment,description,summary");
		JsonNode issue = jira.getIssue(issueKey, queryParams);

		// 直接輸出完整 JSON response（已格式化）
		System.out.println(issue.toPrettyString());
	}

	private static List<String> handleGetComments(JiraClient jira, String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool get-comments <issueKey>");
			return Collections.emptyList();
		}

		String issueKey = args[1];
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put("expand", "names");
		JsonNode response = jira.getComments(issueKey);

		List<String> commentTexts = new ArrayList<>();

		if (response.has("comments")) {
			for (JsonNode comment : response.get("comments")) {
				String commentText = extractCommentText(comment);
				commentTexts.add(commentText);
			}
		}

		return commentTexts;
	}

	/**
	 * 從 comment JSON 中提取文字內容
	 * 處理路徑: body.content[].content[].text
	 * 多個 paragraph 用 \n 連接
	 */
	private static String extractCommentText(JsonNode comment) {
		StringBuilder sb = new StringBuilder();

		JsonNode body = comment.get("body");
		if (body != null && body.has("content")) {
			JsonNode contentArray = body.get("content");

			for (JsonNode paragraph : contentArray) {
				if (paragraph.has("content")) {
					for (JsonNode textNode : paragraph.get("content")) {
						String type = textNode.get("type").asText();

						if ("text".equals(type)) {
							String text = textNode.get("text").asText();
							sb.append(text);
						} else if ("hardBreak".equals(type)) {
							sb.append("\n");
						}
					}
				}
				// 每個 paragraph 結束後加換行
				sb.append("\n");
			}
		}

		// 移除最後多餘的換行
		String result = sb.toString();
		return result.endsWith("\n") ? result.substring(0, result.length() - 1) : result;
	}

	/**
	 * 檢查是否有任何留言包含指定關鍵字
	 *
	 * @param comments 從 handleGetComments 返回的留言列表
	 * @param keyword 要搜尋的關鍵字
	 * @return 如果任一留言包含關鍵字則返回 true
	 */
	public static boolean isCommentExist(List<String> comments, String keyword) {
		if (comments == null || keyword == null) {
			return false;
		}

		for (String comment : comments) {
			if (comment.contains(keyword)) {
				return true;
			}
		}

		return false;
	}

	private static List<String> handleGetTransitions(JiraClient jira, String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool get-transitions <issueKey>");
			return Collections.emptyList();
		}

		String issueKey = args[1];
		JsonNode response = jira.getTransitions(issueKey);

		List<String> transitionIds = new ArrayList<>();

		if (response.has("transitions")) {
			for (JsonNode transition : response.get("transitions")) {
				String id = transition.get("id").asText();
				transitionIds.add(id);
			}
		}

		return transitionIds;
	}

	private static void handlePostComment(JiraClient jira, String[] args, boolean testMode) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool post-comment <issueKey> [templatePath]");
			return;
		}

		String issueKey = args[1];
		String replacement = args[2];
		String templatePath = "./template/jira/post-comment.json";

		// 處理換行符號：將 \n 轉換為實際換行
		replacement = replacement.replace("\\n", "\n");

		// 先檢查留言是否已存在
		System.out.println("Checking if comment already exists...");
		System.out.println("Comment content to add:");
		System.out.println("---");
		System.out.println(replacement);
		System.out.println("---");
		System.out.println("(Length: " + replacement.length() + " characters)\n");

		List<String> existingComments = handleGetComments(jira, new String[]{"get-comments", issueKey});

		System.out.println("Existing comments count: " + existingComments.size());
//		for (int i = 0; i < existingComments.size(); i++) {
//			System.out.println("\nComment #" + (i + 1) + ":");
//			System.out.println("---");
//			System.out.println(existingComments.get(i));
//			System.out.println("---");
//			System.out.println("(Length: " + existingComments.get(i).length() + " characters)");
//		}

		if (isCommentExist(existingComments, replacement)) {
			System.out.println("\n⚠️  Comment already exists, skipping addition");
			return;
		}

		System.out.println("\n✅ Comment does not exist, proceeding with addition\n");

		System.out.println("Loading template: " + templatePath);
		String template = jira.loadTemplate(templatePath);

		// 處理換行符號：將文字分割成多個 paragraph
		String commentBody = buildCommentBody(replacement);

		// 替換 commentBody 佔位符
		template = template.replace("\"{$commentBody}\"", commentBody);

		System.out.println("Using template content:\n" + template);

		if (testMode) {
			System.out.println("\n⚠️  Test mode: Skipping actual API call");
			System.out.println("✅ Comment added (Simulated Status: 201)");
		} else {
			HttpResponse response = jira.postComment(issueKey, template);
			System.out.println("\n✅ Comment added (Status: " + response.getStatusCode() + ")");
		}
	}

	private static void handleTransitionIssue(JiraClient jira, String[] args, boolean testMode) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool transition-issue <issueKey> [templatePath]");
			return;
		}

		String issueKey = args[1];
		String key = args[2];
		JiraTransitionId jiraTransitionId = JiraTransitionId.valueOf(key);
		if (jiraTransitionId == null) {
			System.err.println("Status not exist");
			return;
		}

		// ===== 新增：驗證 transition ID 是否有效 =====
		System.out.println("Validating transition ID availability...");
		List<String> availableTransitionIds = handleGetTransitions(jira, new String[]{"get-transitions", issueKey});

		String targetTransitionId = jiraTransitionId.getIdAsString();
		if (!availableTransitionIds.contains(targetTransitionId)) {
			String errorMsg = String.format(
				"❌ Error: Transition ID '%s' (%s) is not in the available transition list.\n" +
				"Available transition IDs: %s",
				targetTransitionId,
				jiraTransitionId.name(),
				availableTransitionIds
			);
			throw new IllegalArgumentException(errorMsg);
		}
		System.out.println("✅ Transition ID validation passed: " + targetTransitionId + " (" + jiraTransitionId.name() + ")\n");
		// ===== 驗證邏輯結束 =====

		String templatePath = "./template/jira/transition-issue.json";
		String template = jira.loadTemplate(templatePath);

		// 這裡可以對模板進行修改
		template = template.replace("{$transitionId}", targetTransitionId);
		System.out.println("Using template content:\n" + template);

		if (testMode) {
			System.out.println("\n⚠️  Test mode: Skipping actual API call");
			System.out.println("✅ Issue status transitioned (Simulated Status: 204)");
		} else {
			HttpResponse response = jira.transitionIssue(issueKey, template);
			System.out.println("\n✅ Issue status transitioned (Status: " + response.getStatusCode() + ")");
		}
	}

	/**
	 * 啟動 Jira Issue 開發流程
	 * 1. Check issue status (is Ready to DEV) - 跳過如果 testMode
	 * 2. Update issue status (IN DEV) - 跳過如果 testMode
	 * 3. Get issue
	 */
	private static void startJiraIssue(JiraClient jira, String[] args, boolean testMode) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: JiraTool start-jira-issue <issueKey>");
			return;
		}

		String issueKey = args[1];
		System.out.println("\n=== Starting Jira Issue Development Workflow: " + issueKey + " ===\n");

		if (!testMode) {
			// Step 1: Check issue status (is Ready to DEV)
			System.out.println("Step 1: Checking issue status...");
			Map<String, String> queryParams = new HashMap<>();
			queryParams.put("fields", "status");
			JsonNode issueStatus = jira.getIssue(issueKey, queryParams);

			String currentStatus = issueStatus.get("fields").get("status").get("name").asText();
			System.out.println("Current status: " + currentStatus);

			if (!"Ready to DEV".equalsIgnoreCase(currentStatus)) {
				System.out.println("⚠️  Warning: Issue status is not 'Ready to DEV', current status is: " + currentStatus);
				System.out.println("Aborting execution");
				return;
			} else {
				System.out.println("✅ Status confirmed: Ready to DEV\n");
			}

			// Step 2: Update issue status (IN DEV)
			System.out.println("Step 2: Updating issue status to IN DEV...");
			handleTransitionIssue(jira, new String[]{"transition-issue", issueKey, "TO_DEV"}, false);
		} else {
			System.out.println("⚠️  Test mode: Skipping Step 1 (status check) and Step 2 (status update)\n");
		}

		// Step 3: Get issue (always execute)
		System.out.println("Step 3: Retrieving issue details...");
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put("fields", "id,key,assignee,fixVersions,status,customfield_10072,customfield_10037,comment,description,summary");
		JsonNode issue = jira.getIssue(issueKey, queryParams);

		// 寫入文件到 ./result/jira 目錄
		File resultDir = new File("./result/jira");
		if (!resultDir.exists()) {
			resultDir.mkdirs();
			System.out.println("Created directory: ./result/jira/");
		}

		String fileName = issueKey + "-jira.txt";
		File resultFile = new File(resultDir, fileName);
		try (FileWriter writer = new FileWriter(resultFile)) {
			writer.write(issue.toPrettyString());
			System.out.println("✅ Issue details saved to: " + resultFile.getPath());
		} catch (IOException e) {
			System.err.println("❌ Failed to save issue details to file: " + e.getMessage());
		}

		System.out.println("\n✅ Issue development workflow started");
	}

	private static void handleEnhancedSearch(JiraClient jira, String[] args) throws Exception {
		String templatePath = args.length > 1 ? args[1] : "enhanced-search.json";

		System.out.println("Loading template: " + templatePath);
		String template = jira.loadTemplate(templatePath);

		// 這裡可以對模板進行修改
		// 例如: template = template.replace("{{jql}}", "project = SA_CRIC");
		System.out.println("Using template content (unmodified):\n" + template);

		HttpResponse response = jira.enhancedSearch(template);
		JsonNode results = response.parseJson(JsonNode.class);

		System.out.println("\n=== Search Results ===");
		System.out.println("Total: " + results.get("total").asInt());

		if (results.has("issues")) {
			for (JsonNode issue : results.get("issues")) {
				String key = issue.get("key").asText();
				String summary = issue.get("fields").get("summary").asText();
				System.out.println(key + ": " + summary);
			}
		}
	}

	/**
	 * 構建 Jira Comment Body (Atlassian Document Format)
	 * 將換行符號轉換為多個 paragraph
	 */
	private static String buildCommentBody(String text) {
		String[] lines = text.split("\n");
		StringBuilder sb = new StringBuilder();
		sb.append("{\"type\":\"doc\",\"version\":1,\"content\":[");

		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"");
			// 轉義特殊字元
			sb.append(escapeJson(lines[i]));
			sb.append("\"}]}");
		}

		sb.append("]}");
		return sb.toString();
	}

	/**
	 * 轉義 JSON 特殊字元
	 */
	private static String escapeJson(String text) {
		return text.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\b", "\\b")
				.replace("\f", "\\f")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	/**
	 * 打印當次執行的 HttpClientConfig 內容，但排除 token
	 */
	private static void printConfigExcludingToken(HttpClientConfig config) {
		System.out.println("baseUrl              : " + config.getBaseUrl());
		System.out.println("account              : " + config.getAccount());
		System.out.println("token                : ****");
		System.out.println("connectTimeout       : " + config.getConnectTimeout() + " ms");
		System.out.println("readTimeout          : " + config.getReadTimeout() + " ms");
		System.out.println("writeTimeout         : " + config.getWriteTimeout() + " ms");
		System.out.println("maxIdleConnections   : " + config.getMaxIdleConnections());
		System.out.println("keepAliveDuration    : " + config.getKeepAliveDuration() + " ms");
	}

	private static void printUsage() {
		System.out.println("================================================================================");
		System.out.println("  Jira Tool - CLI Tool");
		System.out.println("  Command-line tool for interacting with Jira REST API");
		System.out.println("================================================================================");

		System.out.println("\nUsage:");
		System.out.println("  java -jar JiraTool.jar <command> [arguments] [options]");
		System.out.println("  java -jar JiraTool.jar --help");
		System.out.println("  java -jar JiraTool.jar -h");

		System.out.println("\nAvailable Commands:");
		System.out.println();

		System.out.println("  get-issue <issueKey>");
		System.out.println("      Retrieve detailed information of the specified issue (JSON format)");
		System.out.println("      Example: java -jar JiraTool.jar get-issue PROJ-123");
		System.out.println();

		System.out.println("  get-comments <issueKey>");
		System.out.println("      Retrieve all comments of the specified issue");
		System.out.println("      Example: java -jar JiraTool.jar get-comments PROJ-123");
		System.out.println();

		System.out.println("  get-transitions <issueKey>");
		System.out.println("      Retrieve available status transition options for the specified issue");
		System.out.println("      Example: java -jar JiraTool.jar get-transitions PROJ-123");
		System.out.println();

		System.out.println("  post-comment <issueKey> [templatePath]");
		System.out.println("      Add a comment to the specified issue");
		System.out.println("      templatePath: JSON template file path (default: post-comment.json)");
		System.out.println("      Example: java -jar JiraTool.jar post-comment PROJ-123");
		System.out.println("      Example: java -jar JiraTool.jar post-comment PROJ-123 my-comment.json");
		System.out.println("      Example: java -jar JiraTool.jar post-comment PROJ-123 -t (test mode)");
		System.out.println();

		System.out.println("  transition-issue <issueKey> [templatePath]");
		System.out.println("      Transition the status of the specified issue");
		System.out.println("      templatePath: JSON template file path (default: transition-issue.json)");
		System.out.println("      Example: java -jar JiraTool.jar transition-issue PROJ-123");
		System.out.println("      Example: java -jar JiraTool.jar transition-issue PROJ-123 my-transition.json");
		System.out.println("      Example: java -jar JiraTool.jar transition-issue PROJ-123 --testMode (test mode)");
		System.out.println();

		System.out.println("  start-jira-issue <issueKey>");
		System.out.println("      Start Jira Issue development workflow");
		System.out.println("      Steps: 1) Check status 2) Update to IN DEV 3) Retrieve issue information");
		System.out.println("      Example: java -jar JiraTool.jar start-jira-issue PROJ-123");
		System.out.println("      Example: java -jar JiraTool.jar start-jira-issue PROJ-123 -t (skip steps 1, 2)");
		System.out.println();

		System.out.println("  enhanced-search [templatePath]");
		System.out.println("      Perform advanced search using JQL");
		System.out.println("      templatePath: JSON template file path (default: enhanced-search.json)");
		System.out.println("      Example: java -jar JiraTool.jar enhanced-search");
		System.out.println("      Example: java -jar JiraTool.jar enhanced-search my-search.json");
		System.out.println();

		System.out.println("Options:");
		System.out.println("  -t, --testMode");
		System.out.println("      Enable test mode, some commands will skip actual API calls");
		System.out.println("      Applicable commands: post-comment, transition-issue, start-jira-issue");
		System.out.println();

		System.out.println("Configuration File:");
		System.out.println("  This tool requires an application.properties configuration file");
		System.out.println("  The configuration file should contain:");
		System.out.println("    - jira.base.url: Jira server URL");
		System.out.println("    - jira.auth.email: Authentication email");
		System.out.println("    - jira.auth.token: API Token");
		System.out.println();

		System.out.println("Help:");
		System.out.println("  --help, -h    Display this help message");
		System.out.println();
		System.out.println("================================================================================");
	}
}
