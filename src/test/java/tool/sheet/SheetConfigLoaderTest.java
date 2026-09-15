package tool.sheet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * SheetConfigLoader 單元測試
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class SheetConfigLoaderTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private File writeFile(String content) throws IOException {
		File file = temporaryFolder.newFile("sheet-config.json");
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private String validJson() {
		return "{\n"
			+ "  \"credentialPath\": \"./config/service-account.json\",\n"
			+ "  \"applicationName\": \"Project-Tool Sheet Reader\",\n"
			+ "  \"defaultTarget\": \"api20-domain\",\n"
			+ "  \"targets\": {\n"
			+ "    \"api20-domain\": {\n"
			+ "      \"_comment\": \"註解欄位，不可以讓解析炸掉\",\n"
			+ "      \"spreadsheetId\": \"\",\n"
			+ "      \"tab\": \"API 2.0Domain配置_20260814\",\n"
			+ "      \"startRow\": 4,\n"
			+ "      \"endRow\": null,\n"
			+ "      \"valueRenderOption\": \"FORMATTED_VALUE\",\n"
			+ "      \"groupInfo\": {\n"
			+ "        \"groupCodeColumn\": \"B\",\n"
			+ "        \"maxRowsPerGroup\": 5,\n"
			+ "        \"privateIpSetIdColumn\": \"H\",\n"
			+ "        \"privateIpColumns\": [\"J\", \"K\"],\n"
			+ "        \"bkIpSetIdColumn\": \"M\",\n"
			+ "        \"apiInfoBkIpSetIdColumn\": \"S\",\n"
			+ "        \"backupColumns\": [\"U\", \"V\", \"W\", \"X\", \"Y\"],\n"
			+ "        \"emptyMarkers\": [\"-\"]\n"
			+ "      }\n"
			+ "    }\n"
			+ "  }\n"
			+ "}";
	}

	@Test
	public void testLoadFile_ValidConfig() throws Exception {
		// 准备测试数据
		File file = writeFile(validJson());

		// 执行读取
		SheetConfig config = SheetConfigLoader.loadFile(file);

		// 验证
		assertEquals("api20-domain", config.getDefaultTarget());
		assertEquals(1, config.getTargets().size());
		SheetConfig.Target target = config.requireTarget(null);
		assertEquals(Integer.valueOf(4), target.getStartRow());
		assertNull(target.getEndRow());
		assertEquals("FORMATTED_VALUE", target.getValueRenderOption());
		assertEquals("B", target.getGroupInfo().getGroupCodeColumn());
		assertEquals(5, target.getGroupInfo().getBackupColumns().size());
	}

	@Test
	public void testLoadFile_Utf8TabNameNotMangled() throws Exception {
		// 这是「用 Jackson 而非 FileReader」的验证点：
		// FileReader 走平台预设编码，分页名含中文会变成对不上线上分页的乱码
		File file = writeFile(validJson());

		SheetConfig config = SheetConfigLoader.loadFile(file);

		assertEquals("API 2.0Domain配置_20260814", config.requireTarget(null).getTab());
	}

	@Test
	public void testLoadFile_UnknownFieldsIgnored() throws Exception {
		// 设定档里放了 _comment 这类注解栏位，不能让它炸掉解析
		File file = writeFile("{\n"
			+ "  \"_comment\": \"top level note\",\n"
			+ "  \"somethingNew\": 123,\n"
			+ "  \"targets\": { \"t\": { \"spreadsheetId\": \"x\", \"tab\": \"Sheet1\" } }\n"
			+ "}");

		SheetConfig config = SheetConfigLoader.loadFile(file);

		assertEquals(1, config.getTargets().size());
	}

	@Test
	public void testLoadFile_DefaultsApplied() throws Exception {
		File file = writeFile("{ \"targets\": { \"t\": { \"spreadsheetId\": \"x\", \"tab\": \"Sheet1\" } } }");

		SheetConfig config = SheetConfigLoader.loadFile(file);

		assertEquals("./config/service-account.json", config.getCredentialPath());
		assertEquals("FORMATTED_VALUE", config.requireTarget("t").getValueRenderOption());
	}

	@Test
	public void testLoadFile_MalformedJsonReportsPath() throws Exception {
		File file = writeFile("{ this is not json");

		try {
			SheetConfigLoader.loadFile(file);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue("錯誤訊息要含檔案路徑", e.getMessage().contains(file.getAbsolutePath()));
			assertTrue(e.getMessage().contains("解析失敗"));
		}
	}

	@Test
	public void testLoadFile_EmptyFileRejected() throws Exception {
		File file = writeFile("");

		try {
			SheetConfigLoader.loadFile(file);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("空檔"));
		}
	}

	@Test
	public void testLoadFile_MissingFileRejected() throws Exception {
		File missing = new File(temporaryFolder.getRoot(), "nope.json");

		try {
			SheetConfigLoader.loadFile(missing);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("不存在"));
		}
	}

	@Test
	public void testLoadFile_NoTargetsRejected() throws Exception {
		File file = writeFile("{ \"defaultTarget\": \"x\" }");

		try {
			SheetConfigLoader.loadFile(file);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("沒有定義任何 target"));
		}
	}

	@Test
	public void testLoadFile_DefaultTargetNotInTargetsRejected() throws Exception {
		File file = writeFile("{\n"
			+ "  \"defaultTarget\": \"missing\",\n"
			+ "  \"targets\": { \"api20-domain\": { \"spreadsheetId\": \"x\", \"tab\": \"Sheet1\" } }\n"
			+ "}");

		try {
			SheetConfigLoader.loadFile(file);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("defaultTarget"));
			assertTrue("錯誤訊息要列出可用的 target", e.getMessage().contains("api20-domain"));
		}
	}

	@Test
	public void testLoadFile_GroupInfoMissingRequiredColumnRejected() throws Exception {
		File file = writeFile("{\n"
			+ "  \"targets\": { \"t\": { \"spreadsheetId\": \"x\", \"tab\": \"Sheet1\",\n"
			+ "    \"groupInfo\": { \"privateIpColumns\": [\"J\"] } } }\n"
			+ "}");

		try {
			SheetConfigLoader.loadFile(file);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("backupColumns"));
		}
	}

	@Test
	public void testRequireTarget_UnknownNameListsAvailable() throws Exception {
		SheetConfig config = SheetConfigLoader.loadFile(writeFile(validJson()));

		try {
			config.requireTarget("nope");
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("api20-domain"));
		}
	}

	@Test
	public void testValidateForRead_EmptySpreadsheetIdRejectedButCheckAuthUnaffected() throws Exception {
		// check-auth 不需要任何 target，所以 spreadsheetId 留空不能让整份设定档载入失败；
		// 但读取类指令要挡下来
		SheetConfig config = SheetConfigLoader.loadFile(writeFile(validJson()));

		try {
			config.validateForRead(null);
			fail("spreadsheetId 留空時，讀取類指令應該要被擋下");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("spreadsheetId"));
		}
	}

	@Test
	public void testValidateForRead_PassesWhenSpreadsheetIdPresent() throws Exception {
		File file = writeFile(validJson().replace("\"spreadsheetId\": \"\"", "\"spreadsheetId\": \"abc123\""));

		SheetConfig config = SheetConfigLoader.loadFile(file);

		config.validateForRead(null);
	}
}
