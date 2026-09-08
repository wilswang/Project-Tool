package tool.whiteLabel;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * NewGroupSqlBuilder 单元测试
 *
 * 測資取自真實單號 SACRIC-1200（group A58、2 個 private IP、13 個 backup），
 * 期望值逐字對照 v1.3.2 產出的 SACRIC-1200-{DEV,UAT,SIM}-DB-01.sql —— 這是把
 * 「isUat 改成資料驅動」釘死的地方：三個行為（自家 domain 的 isactive、backup 的 isactive、
 * 額外 domain 的插入與編號）一次驗完。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class NewGroupSqlBuilderTest {

	private static final List<String> PRIVATE_IP_LIST =
		Arrays.asList("pra589wktsms.xyz", "pra589wktsms.space");

	private static final List<String> BACKUP_LIST = Arrays.asList(
		"wa1rirdbi9.click", "w8cdohbjy2.click", "w2ops1rpch.click", "wtofuqpptr.link",
		"wttupnrp13.link", "wv36gs9ugw.click", "wotwrwucqp.link", "wqbmgodu2h.link",
		"we2p3nkmzn.click", "wixstrb9pr.click", "wznk5ntt5l.link", "w8yw8ltm8o.link",
		"wxycqbidwi.link");

	private WhiteLabelConfig buildConfig() {
		GroupInfo groupInfo = new GroupInfo();
		groupInfo.setPrivateIpSetId("e2a08b33-ff89-426d-97e2-b6102b5f890d");
		groupInfo.setPrivateIp(PRIVATE_IP_LIST);
		groupInfo.setBkIpSetId(Arrays.asList("7d29acef-9a40-45da-b0ff-c0552d4fb8b6",
			"8c7572ef-5ac9-4e17-9b05-a8f9b008a5a6"));
		groupInfo.setApiInfoBkIpSetId("c9501fff-ed69-495d-9810-cb6f1ce08f77");
		groupInfo.setBackup(BACKUP_LIST);

		ApiWalletInfo apiWalletInfo = new ApiWalletInfo();
		apiWalletInfo.setCert("NzHPkeAL55ldnktC");
		apiWalletInfo.setNewGroup(true);
		apiWalletInfo.setGroup("A58");
		apiWalletInfo.setGroupInfo(groupInfo);

		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("1200");
		config.setWebSiteName("WKTS9MS");
		config.setWebSiteValue(489);
		config.setApiWhiteLabel(true);
		config.setJiraSummary("[ApiWallet][TransferWallet] WKTS9MS");
		config.setApiWalletInfo(apiWalletInfo);
		return config;
	}

	/** 沒有額外 domain、自家 domain 開啟 —— 重現 DEV / SIM 的行為 */
	private EnvValues plainEnv(String envName, String subDomainStatic, String subDomainApi) {
		Map<String, String> scalars = new LinkedHashMap<>();
		scalars.put("subDomainStatic", subDomainStatic);
		scalars.put("subDomainApi", subDomainApi);
		return new EnvValues(envName, scalars, Collections.<String>emptyList(),
			Collections.<String>emptyList(), true);
	}

	/** 額外 domain + 自家 domain 關閉 —— 重現 UAT 的行為 */
	private EnvValues uatEnv() {
		Map<String, String> scalars = new LinkedHashMap<>();
		scalars.put("subDomainStatic", "tberwxsjyk");
		scalars.put("subDomainApi", "uat9wapi");
		return new EnvValues("UAT", scalars,
			Arrays.asList("cckk77.net", "cckk77.live"),
			Arrays.asList("qqkk77.net", "qqkk77.live", "ppkk77.net"),
			false);
	}

	@Test
	public void testIpSetIdPlaceholders() {
		// 准备测试数据
		Map<String, String> result =
			NewGroupSqlBuilder.build(buildConfig(), plainEnv("DEV", "devnginx", "dev9wapi"));

		// 验证四个 IP set id
		assertEquals("e2a08b33-ff89-426d-97e2-b6102b5f890d", result.get("{$privateIpSetId}"));
		assertEquals("7d29acef-9a40-45da-b0ff-c0552d4fb8b6", result.get("{$wwwgaIpSetId}"));
		assertEquals("8c7572ef-5ac9-4e17-9b05-a8f9b008a5a6", result.get("{$wwwcfIpSetId}"));
		assertEquals("c9501fff-ed69-495d-9810-cb6f1ce08f77", result.get("{$apiInfoBkIpSetId}"));
	}

	@Test
	public void testApiDomainValues_Dev() {
		String apiDomainValues =
			NewGroupSqlBuilder.build(buildConfig(), plainEnv("DEV", "devnginx", "dev9wapi"))
				.get("{$apiDomainValues}");

		// 自家 private domain：isactive=1，priority 1..2，apidomaintype 0
		assertTrue(apiDomainValues.contains("'A58', 'pra589wktsms.xyz', 1, 1, 'SYSTEM', 0,"));
		assertTrue(apiDomainValues.contains("'A58', 'pra589wktsms.space', 1, 2, 'SYSTEM', 0,"));
		// backup：前兩筆 isactive=1，第 3 筆起 0，apidomaintype 1
		assertTrue(apiDomainValues.contains("'A58', 'wa1rirdbi9.click', 1, 1, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'w8cdohbjy2.click', 1, 2, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'w2ops1rpch.click', 0, 3, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'wxycqbidwi.link', 0, 13, 'SYSTEM', 1,"));
		// DEV 不該出現任何額外 domain
		assertFalse(apiDomainValues.contains("cckk77"));
		assertFalse(apiDomainValues.contains("qqkk77"));
		assertFalse(apiDomainValues.contains("ppkk77"));
		// 共 15 列（2 private + 13 backup）
		assertEquals(15, countOccurrences(apiDomainValues, "apidomainname_id_seq_nextval()"));
		assertTrue(apiDomainValues.endsWith(";"));
	}

	@Test
	public void testApiDomainValues_Uat() {
		String apiDomainValues =
			NewGroupSqlBuilder.build(buildConfig(), uatEnv()).get("{$apiDomainValues}");

		// 自家 private domain 被關閉
		assertTrue(apiDomainValues.contains("'A58', 'pra589wktsms.xyz', 0, 1, 'SYSTEM', 0,"));
		assertTrue(apiDomainValues.contains("'A58', 'pra589wktsms.space', 0, 2, 'SYSTEM', 0,"));
		// 額外 private domain 接在後面、isactive=1、priority 3..4、apidomaintype 0
		assertTrue(apiDomainValues.contains("'A58', 'cckk77.net', 1, 3, 'SYSTEM', 0,"));
		assertTrue(apiDomainValues.contains("'A58', 'cckk77.live', 1, 4, 'SYSTEM', 0,"));
		// 全部 backup 被關閉
		assertTrue(apiDomainValues.contains("'A58', 'wa1rirdbi9.click', 0, 1, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'w8cdohbjy2.click', 0, 2, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'wxycqbidwi.link', 0, 13, 'SYSTEM', 1,"));
		// 額外 public domain 接在 backup 之後繼續編號 14..16、apidomaintype 1
		assertTrue(apiDomainValues.contains("'A58', 'qqkk77.net', 1, 14, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'qqkk77.live', 1, 15, 'SYSTEM', 1,"));
		assertTrue(apiDomainValues.contains("'A58', 'ppkk77.net', 1, 16, 'SYSTEM', 1,"));
		// 共 20 列（2 private + 2 extra private + 13 backup + 3 extra public）
		assertEquals(20, countOccurrences(apiDomainValues, "apidomainname_id_seq_nextval()"));
	}

	@Test
	public void testCorsDomainValues_UsesEnvSubDomains() {
		String dev = NewGroupSqlBuilder.build(buildConfig(), plainEnv("DEV", "devnginx", "dev9wapi"))
			.get("{$corsDomainValues}");
		String uat = NewGroupSqlBuilder.build(buildConfig(), uatEnv()).get("{$corsDomainValues}");
		String sim = NewGroupSqlBuilder.build(buildConfig(), plainEnv("SIM", "www", "saapipl"))
			.get("{$corsDomainValues}");

		assertTrue(dev.contains("('wa1rirdbi9.click', 1, 'devnginx', 'dev9wapi', sysdate(6), sysdate(6))"));
		assertTrue(uat.contains("('wa1rirdbi9.click', 1, 'tberwxsjyk', 'uat9wapi', sysdate(6), sysdate(6))"));
		assertTrue(sim.contains("('wa1rirdbi9.click', 1, 'www', 'saapipl', sysdate(6), sysdate(6))"));

		// corsdomain 只列 backup，額外 domain 不進來
		assertEquals(13, countOccurrences(uat, "sysdate(6), sysdate(6))"));
		assertFalse(uat.contains("cckk77"));
		assertTrue(uat.endsWith(";"));
	}

	@Test
	public void testFrontendBackendSeparationExcludesExtraPrivateDomains() {
		String uat = NewGroupSqlBuilder.build(buildConfig(), uatEnv())
			.get("{$enableFrontendBackendSeparationByDomainValues}");

		assertTrue(uat.contains("\"pra589wktsms.xyz\": 1"));
		assertTrue(uat.contains("\"wa1rirdbi9.click\": 1"));
		// 額外插入的 private domain 不列入
		assertFalse(uat.contains("cckk77"));
	}

	@Test
	public void testDevAndSimDifferOnlyInSubDomains() {
		Map<String, String> dev =
			NewGroupSqlBuilder.build(buildConfig(), plainEnv("DEV", "devnginx", "dev9wapi"));
		Map<String, String> sim =
			NewGroupSqlBuilder.build(buildConfig(), plainEnv("SIM", "www", "saapipl"));

		// apidomainname 那段與環境無關，兩邊必須完全相同
		assertEquals(dev.get("{$apiDomainValues}"), sim.get("{$apiDomainValues}"));
		// corsdomain 才有 subdomain，所以不同
		assertNotEquals(dev.get("{$corsDomainValues}"), sim.get("{$corsDomainValues}"));
	}

	private static int countOccurrences(String text, String token) {
		int count = 0;
		int index = text.indexOf(token);
		while (index >= 0) {
			count++;
			index = text.indexOf(token, index + token.length());
		}
		return count;
	}
}
