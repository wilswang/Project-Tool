package tool.whiteLabel;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * 產生 SingleWallet 白牌 {@code APISITEPROPERTIES} 的 VALUES 列，填進 {@code {$apiSitePropertiesValues}}。
 *
 * <p>{@code securityCode}（cert）每個環境都要有；{@code host}（廠商端點）只在該環境有 {@code apiHost} 時才產出。
 * 需求單的「UAT端點URL」常寫「無」—— 廠商沒有 UAT 環境，DEV / UAT 就不該寫入 host。
 * 以前模板把兩列寫死，沒有 apiHost 只能隨便填一個值騙過 placeholder 檢查（曾填過 SIM 端點、
 * 也曾被 LLM 填成字面上的「無」），兩種都會把錯的 host 寫進 DB。
 *
 * <p>規則：
 * <ul>
 *   <li>apiHost 沒有或空白 → 只產出 securityCode 一列</li>
 *   <li>apiHost 不是 http(s):// 開頭 → 丟錯（擋下「無」、「N/A」這類非網址值）</li>
 *   <li>SIM 必須有 apiHost —— SIM 對應正式上線的端點，缺了是需求單或轉檔漏掉</li>
 * </ul>
 *
 * @author Wilson.Wang
 * @version 1.5.9
 */
public final class ApiSitePropertiesSqlBuilder {

	static final String API_HOST = "apiHost";
	static final String REQUIRED_ENV = "SIM";
	private static final String ROW_SEPARATOR = ",\n        ";

	private ApiSitePropertiesSqlBuilder() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * @param envName      環境名稱（只用來判斷是否為 SIM 與組錯誤訊息）
	 * @param apiHost      該環境解析後的 apiHost，可為 null
	 * @param webSiteValue 站台編號
	 * @param cert         securityCode
	 * @return 不含結尾分號的 VALUES 列，例如 {@code (535, 'host', '...', 'system', SYSDATE(6)),\n        (535, ...)}
	 */
	public static String build(String envName, String apiHost, int webSiteValue, String cert) {
		if (StringUtils.isBlank(cert)) {
			throw new IllegalStateException(envName + "：cert 不可為空，APISITEPROPERTIES 一定要有 securityCode");
		}

		List<String> rows = new ArrayList<>();
		if (StringUtils.isBlank(apiHost)) {
			if (REQUIRED_ENV.equalsIgnoreCase(envName)) {
				throw new IllegalStateException(envName + " 必須有 envValues." + envName + ".apiHost（SIM端點URL），"
					+ "請確認需求單「相關文件附件」的 SIM端點URL 是否有轉進單號 JSON");
			}
			System.out.println("ℹ️  " + envName + " 沒有 apiHost，APISITEPROPERTIES 不產出 host 列");
		} else {
			String host = apiHost.trim();
			if (!host.startsWith("http://") && !host.startsWith("https://")) {
				throw new IllegalStateException(envName + " 的 apiHost 不是網址: \"" + apiHost + "\"。"
					+ "需求單寫「無」時請直接省略 envValues." + envName + ".apiHost，不要填值");
			}
			rows.add(row(webSiteValue, "host", host));
		}
		rows.add(row(webSiteValue, "securityCode", cert));
		return String.join(ROW_SEPARATOR, rows);
	}

	private static String row(int webSiteValue, String name, String value) {
		return "(" + webSiteValue + ", '" + name + "', '" + value + "', 'system', SYSDATE(6))";
	}
}
