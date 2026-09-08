package util.placeholder;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * PlaceholderValidator 单元测试
 *
 * 重點是「不誤判真實 SQL 語法」那一組反例 —— 那是這道檢查敢上線的依據。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class PlaceholderValidatorTest {

	@Test
	public void testFullyResolvedContentPasses() throws Exception {
		String sql = "INSERT INTO APISITEPROPERTIES (site, propertiesname, propertiesvalue)\n"
			+ "values (526, 'host', 'https://example.test/api/9wicket');";

		PlaceholderValidator.requireFullyResolved("test.sql", sql);
	}

	@Test
	public void testSingleUnresolvedPlaceholderReportsFileLineAndToken() {
		String sql = "-- header\n"
			+ "values  (526, 'host', '{$apiHost}', 'system', SYSDATE(6)),";

		try {
			PlaceholderValidator.requireFullyResolved("SACRIC-1400-DEV-DB-01.sql", sql);
			fail("殘留 placeholder 應該丟例外");
		} catch (UnresolvedPlaceholderException e) {
			assertTrue(e.getMessage().contains("SACRIC-1400-DEV-DB-01.sql"));
			assertTrue(e.getMessage().contains("line 2"));
			assertTrue(e.getMessage().contains("{$apiHost}"));
			assertEquals(1, e.getUnresolvedList().size());
			assertEquals(2, e.getUnresolvedList().get(0).getLineNumber());
		}
	}

	@Test
	public void testMultipleUnresolvedOnMultipleLinesAllReported() {
		String sql = "values ('{$apiHost}'),\n"
			+ "       ('{$cert}');";

		List<PlaceholderValidator.Unresolved> unresolvedList = PlaceholderValidator.findUnresolved(sql);

		assertEquals(2, unresolvedList.size());
		assertEquals("{$apiHost}", unresolvedList.get(0).getToken());
		assertEquals(1, unresolvedList.get(0).getLineNumber());
		assertEquals("{$cert}", unresolvedList.get(1).getToken());
		assertEquals(2, unresolvedList.get(1).getLineNumber());
	}

	@Test
	public void testRealSqlSyntaxIsNotFlagged() throws Exception {
		// MySQL JSON path：$ 前面是單引號，不是 {
		PlaceholderValidator.requireFullyResolved("a",
			"UPDATE systemsetting SET jsonvalue = JSON_ARRAY_APPEND(jsonvalue, '$.\"44\"', 489) WHERE syskey = 'WebSiteGroup';");

		// JSON 物件字面值：{ 後面是雙引號，不是 $
		PlaceholderValidator.requireFullyResolved("b",
			"UPDATE systemsetting SET jsonvalue = JSON_MERGE(jsonvalue, '{\"489\":\"0\"}');");

		// 多行 JSON 物件：{ 後面是換行／空白
		PlaceholderValidator.requireFullyResolved("c",
			"VALUES (489, '{\n\t\"applyBetLimitTime\": 1,\n\t\"offerBetfairExchange\": 0\n}', 1, 1);");
	}

	@Test
	public void testMalformedShapesAreDeliberatelyIgnored() throws Exception {
		// 這些形狀刻意不匹配，把誤判面積壓到零
		PlaceholderValidator.requireFullyResolved("a", "{$ foo}");
		PlaceholderValidator.requireFullyResolved("b", "{$}");
		PlaceholderValidator.requireFullyResolved("c", "{$1abc}");
		PlaceholderValidator.requireFullyResolved("d", "${foo}");
		PlaceholderValidator.requireFullyResolved("e", "{ $foo }");
	}

	@Test
	public void testMalformedMapperKeyIsFlagged() {
		// PlaceholderMapper 對 Collection 產生的 {$field}.size 內含合法 token，會被抓到
		assertEquals(1, PlaceholderValidator.findUnresolved("{$files}.size").size());
		assertEquals("{$files}", PlaceholderValidator.findUnresolved("{$files}.size").get(0).getToken());
	}

	@Test
	public void testDottedPlaceholderNameIsFlagged() {
		// 巢狀 config 物件會產生 {$apiWalletInfo.cert} 這種帶點的名稱
		assertEquals(1, PlaceholderValidator.findUnresolved("('{$apiWalletInfo.cert}')").size());
	}

	@Test
	public void testNullAndEmptyContent() {
		assertTrue(PlaceholderValidator.findUnresolved(null).isEmpty());
		assertTrue(PlaceholderValidator.findUnresolved("").isEmpty());
	}
}
