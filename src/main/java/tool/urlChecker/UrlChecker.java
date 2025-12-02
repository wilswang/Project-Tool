package tool.urlChecker;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class UrlChecker {

	private static final int DEFAULT_TIMEOUT = 15000; // 15 seconds

	// Inner class to store failed URL results
	private static class FailedUrlResult {
		String host;
		String reason;

		FailedUrlResult(String host, String reason) {
			this.host = host;
			this.reason = reason;
		}
	}

	public static void main(String[] args) {
		try {
			// Set up UTF-8 encoding for console output (Java 8 compatible)
			System.setOut(new PrintStream(System.out, true, "UTF-8"));

			// Load JSON configuration file
			ObjectMapper mapper = new ObjectMapper();
			UrlCheckerConfig urlCheckerConfig = mapper.readValue(new File("sample-urlChecker.json"), UrlCheckerConfig.class);

			// Validate configuration using Hibernate Validator
			urlCheckerConfig.validate();

			// Set timeout values (default is 15 seconds)
			int connectTimeout = urlCheckerConfig.connectTimeout != null ? urlCheckerConfig.connectTimeout : DEFAULT_TIMEOUT;
			int readTimeout = urlCheckerConfig.readTimeout != null ? urlCheckerConfig.readTimeout : DEFAULT_TIMEOUT;

			// Create list to store failed URLs
			List<FailedUrlResult> failedUrls = new ArrayList<>();
			int total = urlCheckerConfig.domainList.size();

			System.out.println("Total " + total);
			for (int i = 0; i < total; i++) {
				String domain = urlCheckerConfig.domainList.get(i);
				String protocol = urlCheckerConfig.isHttps ? "https" : "http";
				String url;

				if (urlCheckerConfig.subdomain == null || urlCheckerConfig.subdomain.trim().isEmpty()) {
					url = String.format("%s://%s", protocol, domain);
				} else {
					url = String.format("%s://%s.%s", protocol, urlCheckerConfig.subdomain, domain);
				}
				System.out.print("[" + (i + 1) + "/" + total + "] ");
				checkUrl(url, connectTimeout, readTimeout, failedUrls);
			}

			// Print failure summary if there are any failures
			if (!failedUrls.isEmpty()) {
				System.out.println("\n========================================");
				System.out.println("Total failed: " + failedUrls.size() + " out of " + total);
				System.out.println("========================================");
				for (FailedUrlResult failed : failedUrls) {
					System.out.println(failed.host + " - " + failed.reason);
				}
				System.out.println("========================================");
			} else {
				System.out.println("\nAll URLs checked successfully!");
			}
		} catch (Exception e) {
			System.err.println("Error occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static void checkUrl(String urlString, int connectTimeout, int readTimeout, List<FailedUrlResult> failedUrls) {
		URL url = null;
		try {
			System.out.print("Checking URL: " + urlString + " ... ");
			url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(connectTimeout);
			conn.setReadTimeout(readTimeout);
			int responseCode = conn.getResponseCode();

			if (responseCode == 200) {
				System.out.println("✅ [OK] 200 OK");
			} else {
				String reason = "Response code: " + responseCode;
				System.out.println("❌ [FAILED] " + reason);
				failedUrls.add(new FailedUrlResult(url.getHost(), reason));
			}

			conn.disconnect();
		} catch (Exception e) {
			String reason = "Connection failed: " + e.getMessage();
			System.out.println("⚠️ [ERROR] " + reason);
			failedUrls.add(new FailedUrlResult(url == null? urlString: url.getHost(), reason));
		}
	}

}