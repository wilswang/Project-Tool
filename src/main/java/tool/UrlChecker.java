package tool;

import com.fasterxml.jackson.databind.ObjectMapper;

import dto.DomainCheckInfo;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlChecker {
	
	private static final int DEFAULT_TIMEOUT = 15000; // 15秒
	
	public static void main(String[] args) {
		try {
			// 載入 JSON 設定檔
			ObjectMapper mapper = new ObjectMapper();
			DomainCheckInfo domainCheckInfo = mapper.readValue(new File("checkDomain.json"), DomainCheckInfo.class);
			
			// 使用 Hibernate Validator 驗證配置
			domainCheckInfo.validate();
			
			// 設定 timeout 值（預設為 15 秒）
			int connectTimeout = domainCheckInfo.connectTimeout != null ? domainCheckInfo.connectTimeout : DEFAULT_TIMEOUT;
			int readTimeout = domainCheckInfo.readTimeout != null ? domainCheckInfo.readTimeout : DEFAULT_TIMEOUT;
			
			System.out.println("Total " + domainCheckInfo.domainList.size());
			for (int i = 0; i < domainCheckInfo.domainList.size(); i++) {
				String domain = domainCheckInfo.domainList.get(i);
				String protocol = domainCheckInfo.isHttps ? "https" : "http";
				String url;
				
				if (domainCheckInfo.subdomain == null || domainCheckInfo.subdomain.trim().isEmpty()) {
					url = String.format("%s://%s", protocol, domain);
				} else {
					url = String.format("%s://%s.%s", protocol, domainCheckInfo.subdomain, domain);
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