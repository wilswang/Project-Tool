package tool.whiteLabel;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link ApiSitePropertiesSqlBuilder} 的測試：沒有 apiHost 的環境不產出 host 列，
 * 非網址的 apiHost（例如需求單的「無」）要擋下，SIM 一定要有 apiHost。
 *
 * @author Wilson.Wang
 * @version 1.5.9
 */
public class ApiSitePropertiesSqlBuilderTest {

	private static final String HOST = "https://api.example.com/api/9wicket/aws011";
	private static final String CERT = "Gk5x07SemwXxjjWw";

	// 有 apiHost → host 與 securityCode 兩列，格式與舊模板逐字相同
	@Test
	public void testBuildsHostAndSecurityCodeRows() {
		String values = ApiSitePropertiesSqlBuilder.build("SIM", HOST, 535, CERT);
		assertEquals("(535, 'host', '" + HOST + "', 'system', SYSDATE(6)),\n"
			+ "        (535, 'securityCode', '" + CERT + "', 'system', SYSDATE(6))", values);
	}

	// 沒有 apiHost（需求單 UAT端點URL 寫「無」）→ 只有 securityCode
	@Test
	public void testOmitsHostRowWhenApiHostMissing() {
		assertEquals("(535, 'securityCode', '" + CERT + "', 'system', SYSDATE(6))",
			ApiSitePropertiesSqlBuilder.build("UAT", null, 535, CERT));
		assertEquals("(535, 'securityCode', '" + CERT + "', 'system', SYSDATE(6))",
			ApiSitePropertiesSqlBuilder.build("DEV", "  ", 535, CERT));
	}

	// 「無」這類非網址值要擋下，不能寫進 DB
	@Test
	public void testRejectsNonUrlApiHost() {
		try {
			ApiSitePropertiesSqlBuilder.build("UAT", "無", 535, CERT);
			fail("非網址的 apiHost 應該丟錯");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("不是網址"));
		}
	}

	// SIM 對應正式端點，缺 apiHost 一定是漏轉
	@Test
	public void testRequiresApiHostOnSim() {
		try {
			ApiSitePropertiesSqlBuilder.build("SIM", null, 535, CERT);
			fail("SIM 沒有 apiHost 應該丟錯");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("SIM"));
		}
	}

	// cert 是必要的
	@Test
	public void testRequiresCert() {
		try {
			ApiSitePropertiesSqlBuilder.build("DEV", null, 535, "");
			fail("cert 為空應該丟錯");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("cert"));
		}
	}
}
