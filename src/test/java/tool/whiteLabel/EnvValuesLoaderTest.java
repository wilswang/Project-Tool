package tool.whiteLabel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * EnvValuesLoader 单元测试
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class EnvValuesLoaderTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private String writeFile(String content) throws IOException {
		File file = temporaryFolder.newFile("env-values.json");
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getAbsolutePath();
	}

	@Test
	public void testLoadFile_ValidThreeEnvironments() throws Exception {
		// 准备测试数据：含 UAT 的陣列與布林
		String path = writeFile("{\n"
			+ "  \"DEV\": { \"subDomainStatic\": \"devnginx\", \"subDomainApi\": \"dev9wapi\" },\n"
			+ "  \"UAT\": { \"subDomainStatic\": \"tberwxsjyk\", \"subDomainApi\": \"uat9wapi\",\n"
			+ "             \"extraPrivateDomains\": [\"cckk77.net\", \"cckk77.live\"],\n"
			+ "             \"extraPublicDomains\": [\"qqkk77.net\"],\n"
			+ "             \"activateOwnDomains\": false },\n"
			+ "  \"SIM\": { \"subDomainStatic\": \"www\", \"subDomainApi\": \"saapipl\" }\n"
			+ "}");

		// 执行读取
		Map<String, Map<String, Object>> raw = EnvValuesLoader.loadFile(path);

		// 验证內容與順序
		assertEquals(3, raw.size());
		assertEquals(Arrays.asList("DEV", "UAT", "SIM"), new java.util.ArrayList<>(raw.keySet()));
		assertEquals("devnginx", raw.get("DEV").get("subDomainStatic"));
		assertEquals(Boolean.FALSE, raw.get("UAT").get("activateOwnDomains"));

		List<?> extraPrivateDomains = (List<?>) raw.get("UAT").get("extraPrivateDomains");
		assertEquals(2, extraPrivateDomains.size());
		assertEquals("cckk77.net", extraPrivateDomains.get(0));
	}

	@Test
	public void testLoadFile_MissingFileReturnsEmptyWithoutThrowing() throws Exception {
		// 檔案不存在是允許的（向後相容），只印警告
		Map<String, Map<String, Object>> raw =
			EnvValuesLoader.loadFile(temporaryFolder.getRoot().getAbsolutePath() + "/no-such-file.json");

		assertTrue(raw.isEmpty());
	}

	@Test
	public void testLoadFile_UnparseableJsonThrows() throws Exception {
		String path = writeFile("{ \"DEV\": { oops }");

		try {
			EnvValuesLoader.loadFile(path);
			fail("格式壞掉的 JSON 應該丟 EnvValuesException");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("解析失敗"));
		}
	}

	@Test
	public void testValidate_EnvBlockIsNotObject() {
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("DEV", null);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("環境內容為 null 應該丟例外");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("DEV"));
		}
	}

	@Test
	public void testValidate_NestedObjectValueRejected() {
		Map<String, Object> devBlock = new LinkedHashMap<>();
		devBlock.put("apiHost", new LinkedHashMap<String, Object>());
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("DEV", devBlock);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("巢狀物件應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("巢狀物件"));
			assertTrue(e.getMessage().contains("DEV.apiHost"));
		}
	}

	@Test
	public void testValidate_ArrayValueRejectedForNonListKey() {
		Map<String, Object> devBlock = new LinkedHashMap<>();
		devBlock.put("apiHost", Arrays.asList("a", "b"));
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("DEV", devBlock);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("非 list key 給陣列應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("{$apiHost}"));
		}
	}

	@Test
	public void testValidate_ListKeyMustBeArrayOfStrings() {
		Map<String, Object> uatBlock = new LinkedHashMap<>();
		uatBlock.put("extraPrivateDomains", "cckk77.net");
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("UAT", uatBlock);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("extraPrivateDomains 給字串應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("應為字串陣列"));
		}

		uatBlock.put("extraPrivateDomains", Arrays.asList("cckk77.net", 123));
		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("陣列含非字串元素應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("陣列元素應為字串"));
		}
	}

	@Test
	public void testValidate_BooleanKeyMustBeBoolean() {
		Map<String, Object> uatBlock = new LinkedHashMap<>();
		uatBlock.put("activateOwnDomains", "false");
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("UAT", uatBlock);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("activateOwnDomains 給字串應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("應為 true/false"));
		}
	}

	@Test
	public void testValidate_ReservedKeyRejected() {
		Map<String, Object> devBlock = new LinkedHashMap<>();
		devBlock.put("env", "DEV");
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("DEV", devBlock);

		try {
			EnvValuesLoader.validate(raw, "測試來源");
			fail("保留字 env 應該被拒絕");
		} catch (EnvValuesException e) {
			assertTrue(e.getMessage().contains("保留字"));
		}
	}

	@Test
	public void testValidate_UnknownScalarKeyAccepted() throws Exception {
		// 這就是「新增環境值不用重新打包」的契約：未知但純量的 key 必須被接受
		Map<String, Object> devBlock = new LinkedHashMap<>();
		devBlock.put("apiHost", "https://example.test/api");
		devBlock.put("someNewThing", 42);
		devBlock.put("someFlag", true);
		Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
		raw.put("DEV", devBlock);

		EnvValuesLoader.validate(raw, "測試來源");
	}
}
