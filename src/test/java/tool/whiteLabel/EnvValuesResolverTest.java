package tool.whiteLabel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * EnvValuesResolver 单元测试 —— 重點在三層優先序
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class EnvValuesResolverTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static final String NO_FILE = "./no-such-env-values.json";

	private String writeSharedFile(String content) throws IOException {
		File file = temporaryFolder.newFile();
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getAbsolutePath();
	}

	private WhiteLabelConfig configWithEnvValues(String envName, String key, Object value) {
		Map<String, Object> block = new LinkedHashMap<>();
		block.put(key, value);
		Map<String, Map<String, Object>> envValues = new LinkedHashMap<>();
		envValues.put(envName, block);

		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setEnvValues(envValues);
		return config;
	}

	@Test
	public void testResolve_BuiltInOnly() throws Exception {
		// 共用檔不存在、單號也沒給 -> 完全退回 EnvEnumType 內建值（向後相容承諾）
		EnvValuesResolver resolver = EnvValuesResolver.create(NO_FILE, new WhiteLabelConfig());

		EnvValues dev = resolver.resolve("DEV");
		assertEquals("DEV", dev.getEnvName());
		assertEquals("devnginx", dev.getSubDomainStatic());
		assertEquals("dev9wapi", dev.getSubDomainApi());

		EnvValues uat = resolver.resolve("UAT");
		assertEquals("tberwxsjyk", uat.getSubDomainStatic());
		assertEquals("uat9wapi", uat.getSubDomainApi());

		EnvValues sim = resolver.resolve("SIM");
		assertEquals("www", sim.getSubDomainStatic());
		assertEquals("saapipl", sim.getSubDomainApi());
	}

	@Test
	public void testResolve_SharedFileOverridesBuiltIn() throws Exception {
		String path = writeSharedFile("{ \"DEV\": { \"subDomainApi\": \"from-file\" } }");

		EnvValuesResolver resolver = EnvValuesResolver.create(path, new WhiteLabelConfig());
		EnvValues dev = resolver.resolve("DEV");

		assertEquals("from-file", dev.getSubDomainApi());
		// 沒被覆寫的仍取內建值
		assertEquals("devnginx", dev.getSubDomainStatic());
	}

	@Test
	public void testResolve_PerTicketOverridesSharedFile() throws Exception {
		String path = writeSharedFile("{ \"UAT\": { \"apiHost\": \"https://shared/api\" } }");
		WhiteLabelConfig config = configWithEnvValues("UAT", "apiHost", "https://ticket-only/api");

		EnvValuesResolver resolver = EnvValuesResolver.create(path, config);

		assertEquals("https://ticket-only/api", resolver.resolve("UAT").get("apiHost"));
	}

	@Test
	public void testResolve_AllThreeLayersAtOnce() throws Exception {
		// subDomainStatic 只有內建有、subDomainApi 由檔案覆寫、apiHost 只有單號有
		String path = writeSharedFile("{ \"UAT\": { \"subDomainApi\": \"file-api\" } }");
		WhiteLabelConfig config = configWithEnvValues("UAT", "apiHost", "https://ticket/api");

		EnvValues uat = EnvValuesResolver.create(path, config).resolve("UAT");

		assertEquals("tberwxsjyk", uat.getSubDomainStatic());
		assertEquals("file-api", uat.getSubDomainApi());
		assertEquals("https://ticket/api", uat.get("apiHost"));
	}

	@Test
	public void testResolve_EnvironmentOnlyInFileNeedsNoJavaChange() throws Exception {
		// 這證明「新增第 4 個環境」不用改 enum、不用重新打包
		String path = writeSharedFile("{ \"PROD\": { \"subDomainStatic\": \"www\", \"subDomainApi\": \"prodapi\" } }");

		EnvValues prod = EnvValuesResolver.create(path, new WhiteLabelConfig()).resolve("PROD");

		assertEquals("PROD", prod.getEnvName());
		assertEquals("www", prod.getSubDomainStatic());
		assertEquals("prodapi", prod.getSubDomainApi());
	}

	@Test
	public void testResolve_UnknownEnvironmentThrows() throws Exception {
		EnvValuesResolver resolver = EnvValuesResolver.create(NO_FILE, new WhiteLabelConfig());

		try {
			resolver.resolve("UTA");
			fail("三層都沒有的環境應該丟 UnknownEnvironmentException");
		} catch (UnknownEnvironmentException e) {
			assertTrue(e.getMessage().contains("UTA"));
		}
	}

	@Test
	public void testResolve_EnvNameIsCaseInsensitive() throws Exception {
		String path = writeSharedFile("{ \"dev\": { \"apiHost\": \"https://lower/api\" } }");

		EnvValues dev = EnvValuesResolver.create(path, new WhiteLabelConfig()).resolve("DEV");

		assertEquals("https://lower/api", dev.get("apiHost"));
		// envName 原樣保留，因為 {$env} 與輸出檔名用的是原字串
		assertEquals("DEV", dev.getEnvName());
	}

	@Test
	public void testToPlaceholders_ScalarsOnly() throws Exception {
		String path = writeSharedFile("{ \"UAT\": { \"apiHost\": \"https://x/api\","
			+ " \"extraPrivateDomains\": [\"cckk77.net\"], \"activateOwnDomains\": false } }");

		EnvValues uat = EnvValuesResolver.create(path, new WhiteLabelConfig()).resolve("UAT");
		Map<String, String> placeholders = uat.toPlaceholders();

		assertEquals("https://x/api", placeholders.get("{$apiHost}"));
		assertEquals("tberwxsjyk", placeholders.get("{$subDomainStatic}"));
		// list / boolean key 是程式用的，不該變成 placeholder
		assertFalse(placeholders.containsKey("{$extraPrivateDomains}"));
		assertFalse(placeholders.containsKey("{$activateOwnDomains}"));
		// {$env} 由 WhiteLabelTool 自己寫入
		assertFalse(placeholders.containsKey("{$env}"));

		assertEquals(Arrays.asList("cckk77.net"), uat.getExtraPrivateDomains());
		assertFalse(uat.isActivateOwnDomains());
	}

	@Test
	public void testResolve_ListAndBooleanDefaults() throws Exception {
		// 不寫這三個 key 時的預設值必須重現現行 DEV/SIM 行為
		EnvValues dev = EnvValuesResolver.create(NO_FILE, new WhiteLabelConfig()).resolve("DEV");

		assertTrue(dev.getExtraPrivateDomains().isEmpty());
		assertTrue(dev.getExtraPublicDomains().isEmpty());
		assertTrue(dev.isActivateOwnDomains());
	}

	@Test
	public void testCreate_InvalidPerTicketEnvValuesThrows() {
		WhiteLabelConfig config = configWithEnvValues("UAT", "apiHost", new LinkedHashMap<String, Object>());

		try {
			EnvValuesResolver.create(NO_FILE, config);
			fail("單號 envValues 格式錯誤應該丟 EnvValuesException");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("單號 JSON 的 envValues"));
		}
	}

	@Test
	public void testHasExplicitEntry_OnlyCountsFileAndTicketNotBuiltIn() throws Exception {
		// 内建值不算「明確條目」——newGroup 需要的 extra domain / isactive 規則只存在設定檔
		EnvValuesResolver builtInOnly = EnvValuesResolver.create(NO_FILE, new WhiteLabelConfig());
		assertFalse(builtInOnly.hasExplicitEntry("DEV"));
		assertFalse(builtInOnly.hasExplicitEntry("UAT"));

		String path = writeSharedFile("{ \"DEV\": { \"subDomainApi\": \"x\" } }");
		EnvValuesResolver withFile = EnvValuesResolver.create(path, new WhiteLabelConfig());
		assertTrue(withFile.hasExplicitEntry("DEV"));
		assertTrue(withFile.hasExplicitEntry("dev"));
		assertFalse(withFile.hasExplicitEntry("UAT"));

		// 單號層也算
		EnvValuesResolver withTicket =
			EnvValuesResolver.create(NO_FILE, configWithEnvValues("SIM", "apiHost", "https://x"));
		assertTrue(withTicket.hasExplicitEntry("SIM"));
		assertFalse(withTicket.hasExplicitEntry("DEV"));
	}

	@Test
	public void testFindShadowedKeys() throws Exception {
		String path = writeSharedFile("{ \"DEV\": { \"developer\": \"env-file-dev\" } }");
		EnvValuesResolver resolver = EnvValuesResolver.create(path, new WhiteLabelConfig());
		EnvValues dev = resolver.resolve("DEV");

		Map<String, String> base = new LinkedHashMap<>();
		base.put("{$developer}", "Wilson Wang");
		base.put("{$ticketNo}", "1200");

		assertEquals(1, resolver.findShadowedKeys(base, dev).size());
		assertTrue(resolver.findShadowedKeys(base, dev).contains("{$developer}"));
	}
}
