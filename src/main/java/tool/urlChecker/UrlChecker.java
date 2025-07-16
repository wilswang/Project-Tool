package tool.urlChecker;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlChecker {
	
	private static final int DEFAULT_TIMEOUT = 15000; // 15秒
	
	public static void main(String[] args) {
		try {
			// 載入 JSON 設定檔
			ObjectMapper mapper = new ObjectMapper();
			UrlCheckerConfig urlCheckerConfig = mapper.readValue(new File("sample-urlChecker.json"), UrlCheckerConfig.class);
			
			// 使用 Hibernate Validator 驗證配置
			urlCheckerConfig.validate();
			
			// 設定 timeout 值（預設為 15 秒）
			int connectTimeout = urlCheckerConfig.connectTimeout != null ? urlCheckerConfig.connectTimeout : DEFAULT_TIMEOUT;
			int readTimeout = urlCheckerConfig.readTimeout != null ? urlCheckerConfig.readTimeout : DEFAULT_TIMEOUT;
			
			System.out.println("Total " + urlCheckerConfig.domainList.size());
			for (int i = 0; i < urlCheckerConfig.domainList.size(); i++) {
				String domain = urlCheckerConfig.domainList.get(i);
				String protocol = urlCheckerConfig.isHttps ? "https" : "http";
				String url;
				
				if (urlCheckerConfig.subdomain == null || urlCheckerConfig.subdomain.trim().isEmpty()) {
					url = String.format("%s://%s", protocol, domain);
				} else {
					url = String.format("%s://%s.%s", protocol, urlCheckerConfig.subdomain, domain);
				}
				System.out.print("第" + (i + 1) + ": ");
				checkUrl(url, connectTimeout, readTimeout);
			}
		} catch (Exception e) {
			System.err.println("發生錯誤: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static void checkUrl(String urlString, int connectTimeout, int readTimeout) {
		try {
			System.out.print("檢查 URL: " + urlString + " ... ");
			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(connectTimeout);
			conn.setReadTimeout(readTimeout);
			int responseCode = conn.getResponseCode();
			
			if (responseCode == 200) {
				System.out.println("✅ 200 OK");
			} else {
				System.out.println("❌ 回傳碼: " + responseCode);
			}
			
			conn.disconnect();
		} catch (Exception e) {
			System.out.println("⚠️ 連線失敗: " + e.getMessage());
		}
	}
	
}