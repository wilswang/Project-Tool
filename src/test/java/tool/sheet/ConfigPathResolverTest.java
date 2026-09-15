package tool.sheet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * ConfigPathResolver 單元測試
 *
 * <p>測試改用系統屬性 {@code projectTool.configDir} 指定目錄，避免依賴實際的
 * CWD 佈局（開發期是 repo 根、部署期是 ProjectTool/，測試不該被這個差異綁住）。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class ConfigPathResolverTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@After
	public void clearProperty() {
		System.clearProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY);
	}

	private File writeFile(File dir, String name, String content) throws IOException {
		assertTrue(dir.exists() || dir.mkdirs());
		File file = new File(dir, name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	@Test
	public void testResolve_FoundInOverrideDir() throws Exception {
		// 准备测试数据
		File dir = temporaryFolder.newFolder("config");
		File expected = writeFile(dir, "sheet-config.json", "{}");
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY, dir.getAbsolutePath());

		// 执行
		Optional<File> found = ConfigPathResolver.resolve("sheet-config.json");

		// 验证
		assertTrue(found.isPresent());
		assertEquals(expected.getAbsolutePath(), found.get().getAbsolutePath());
	}

	@Test
	public void testResolve_NotFoundReturnsEmpty() {
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY,
			temporaryFolder.getRoot().getAbsolutePath());

		assertFalse(ConfigPathResolver.resolve("does-not-exist.json").isPresent());
	}

	@Test
	public void testResolve_DefaultDirOrderIsConfigThenSrcConfig() {
		// 部署期 CWD 是 ProjectTool/（有 ./config），开发期是 repo 根（只有 ./src/config）
		assertEquals("./config", ConfigPathResolver.DEFAULT_DIRS.get(0));
		assertEquals("./src/config", ConfigPathResolver.DEFAULT_DIRS.get(1));
	}

	@Test
	public void testResolveRequired_ErrorListsAllAttemptedAbsolutePaths() {
		// EnvValuesLoader 目前只说「找不到」，使用者不知道该把档案放哪。这里要列出所有试过的路径。
		// 刻意用一个确定不存在的档名 —— repo 的 src/config/ 里真的有 sheet-config.json，
		// 拿它当测资会让这个测试随着 repo 内容改变而时好时坏
		String missingName = "definitely-missing-config.json";
		File emptyDir = temporaryFolder.getRoot();
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY, emptyDir.getAbsolutePath());

		try {
			ConfigPathResolver.resolveRequired(missingName);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			String message = e.getMessage();
			assertTrue("要列出 override 目錄", message.contains(emptyDir.getAbsolutePath()));
			assertTrue("要列出 ./config", message.contains(new File("./config", missingName).getAbsolutePath()));
			assertTrue("要列出 ./src/config",
				message.contains(new File("./src/config", missingName).getAbsolutePath()));
			assertTrue("要提示可用系統屬性覆寫", message.contains(ConfigPathResolver.CONFIG_DIR_PROPERTY));
		}
	}

	@Test
	public void testResolveRequired_FindsRepoConfigWhenRunFromProjectRoot() throws Exception {
		// 反向确认：不设 override 时，开发期（CWD = repo 根）应该能从 ./src/config/ 找到设定档。
		// 这正是 ConfigPathResolver 存在的理由 —— 部署期在 ./config/，开发期在 ./src/config/
		System.clearProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY);

		Optional<File> found = ConfigPathResolver.resolve("sheet-config.json");

		assertTrue("預期能在 ./src/config/ 找到 sheet-config.json", found.isPresent());
		assertTrue(found.get().getAbsolutePath().replace('\\', '/').contains("/src/config/"));
	}

	@Test
	public void testResolveCredential_CliPathWins() throws Exception {
		File dir = temporaryFolder.newFolder("cli");
		File cliFile = writeFile(dir, "my-key.json", "{}");
		File configDir = temporaryFolder.newFolder("config");
		writeFile(configDir, "service-account.json", "{}");
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY, configDir.getAbsolutePath());

		File resolved = ConfigPathResolver.resolveCredential(cliFile.getAbsolutePath(),
			"./config/service-account.json");

		assertEquals(cliFile.getAbsolutePath(), resolved.getAbsolutePath());
	}

	@Test
	public void testResolveCredential_CliPathMissingReportsAbsolutePath() {
		File missing = new File(temporaryFolder.getRoot(), "nope.json");

		try {
			ConfigPathResolver.resolveCredential(missing.getAbsolutePath(), null);
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains(missing.getAbsolutePath()));
			assertTrue(e.getMessage().contains("--credential"));
		}
	}

	@Test
	public void testResolveCredential_ConfiguredPathFallsBackToSearchByFileName() throws Exception {
		// 设定档写的是 ./config/service-account.json，开发期那个路径不存在，
		// 要能退到搜寻目录里同名的档案
		File configDir = temporaryFolder.newFolder("fallback");
		File expected = writeFile(configDir, "service-account.json", "{}");
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY, configDir.getAbsolutePath());

		File resolved = ConfigPathResolver.resolveCredential(null, "./config/service-account.json");

		assertEquals(expected.getAbsolutePath(), resolved.getAbsolutePath());
	}

	@Test
	public void testResolveCredential_NothingConfiguredSearchesDefaultName() throws Exception {
		File configDir = temporaryFolder.newFolder("bare");
		File expected = writeFile(configDir, "service-account.json", "{}");
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY, configDir.getAbsolutePath());

		File resolved = ConfigPathResolver.resolveCredential(null, null);

		assertEquals(expected.getAbsolutePath(), resolved.getAbsolutePath());
	}

	@Test
	public void testResolveCredential_NotFoundAnywhereReportsConfiguredPath() {
		// 刻意用不存在的檔名 —— 開發機的 src/config/ 底下可能真的放著 service-account.json，
		// 拿它當測資會讓這個測試隨著環境時好時壞
		System.setProperty(ConfigPathResolver.CONFIG_DIR_PROPERTY,
			temporaryFolder.getRoot().getAbsolutePath());

		try {
			ConfigPathResolver.resolveCredential(null, "./config/no-such-credential.json");
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("credentialPath"));
			assertTrue(e.getMessage().contains("no-such-credential.json"));
		}
	}
}
