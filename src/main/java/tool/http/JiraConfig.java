package tool.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Jira 配置載入工具
 * 支援從 .mcp.json 或 properties 檔案載入配置
 */
public class JiraConfig {

	/**
	 * 從 .mcp.json 檔案載入配置
	 *
	 * @param mcpJsonPath .mcp.json 檔案路徑
	 * @return HttpClientConfig
	 * @throws IOException 如果檔案不存在或解析失敗
	 */
	public static HttpClientConfig fromMcpJson(String mcpJsonPath) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(new File(mcpJsonPath));
		JsonNode jira = root.get("jira");

		if (jira == null) {
			throw new IllegalArgumentException(".mcp.json 中缺少 'jira' 區塊");
		}

		HttpClientConfig config = new HttpClientConfig();
		config.setBaseUrl(jira.get("baseUrl").asText());
		config.setAccount(jira.get("email").asText());
		config.setToken(jira.get("apiToken").asText());

		return config;
	}

	/**
	 * 從 properties 檔案載入配置
	 *
	 * @param propertiesPath properties 檔案路徑
	 * @return HttpClientConfig
	 * @throws IOException 如果檔案不存在
	 */
	public static HttpClientConfig fromProperties(String propertiesPath) throws IOException {
		Properties props = new Properties();
		try (InputStream input = new FileInputStream(propertiesPath)) {
			props.load(input);
		}

		HttpClientConfig config = new HttpClientConfig();
		config.setBaseUrl(props.getProperty("baseUrl"));
		config.setAccount(props.getProperty("account"));
		config.setToken(props.getProperty("token"));

		// 選擇性 timeout 設定
		if (props.containsKey("connectTimeout")) {
			config.setConnectTimeout(Integer.parseInt(props.getProperty("connectTimeout")));
		}
		if (props.containsKey("readTimeout")) {
			config.setReadTimeout(Integer.parseInt(props.getProperty("readTimeout")));
		}
		if (props.containsKey("writeTimeout")) {
			config.setWriteTimeout(Integer.parseInt(props.getProperty("writeTimeout")));
		}
		if (props.containsKey("maxIdleConnections")) {
			config.setMaxIdleConnections(Integer.parseInt(props.getProperty("maxIdleConnections")));
		}
		if (props.containsKey("keepAliveDuration")) {
			config.setKeepAliveDuration(Long.parseLong(props.getProperty("keepAliveDuration")));
		}

		return config;
	}
}
