package util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * {@link TemplateEngine} 的測試，重點在 <b>IO 錯誤不再被吞掉</b>。
 *
 * <p>原本 {@code fillFile} 讀不到模板時只印一行 stderr 並回傳空字串，呼叫端照樣寫檔
 * 並印 {@code ✅ Created} —— 2026-09-09 因此產生三個 0-byte 的 DB-41.sql 而流程全綠。
 *
 * @author Wilson.Wang
 * @version 1.5.2
 */
public class TemplateEngineTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static Map<String, String> replacements(String... pairs) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private File writeTemplate(String name, String content) throws IOException {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	// 模板不存在 → 丟 IOException（以前是回傳空字串）
	@Test
	public void testFillFileThrowsWhenTemplateIsMissing() {
		String missing = new File(folder.getRoot(), "no-such-template.txt").getAbsolutePath();
		try {
			String content = TemplateEngine.fillFile(missing, replacements());
			fail("模板不存在卻沒有丟例外，回傳了: \"" + content + "\"");
		} catch (IOException expected) {
			assertTrue("訊息應指出是哪個檔案", expected.getMessage().contains("no-such-template.txt"));
		}
	}

	// 模板路徑指到目錄 → 一樣要丟，不能靜靜回空字串
	@Test
	public void testFillFileThrowsWhenPathIsADirectory() throws IOException {
		File dir = folder.newFolder("a-directory");
		try {
			TemplateEngine.fillFile(dir.getAbsolutePath(), replacements());
			fail("路徑是目錄卻沒有丟例外");
		} catch (IOException expected) {
			// 預期行為
		}
	}

	// 正常填值
	@Test
	public void testFillFileSubstitutesPlaceholders() throws IOException {
		File template = writeTemplate("ok.txt", "site={$webSiteName}\nvalue={$webSiteValue}");
		String content = TemplateEngine.fillFile(template.getAbsolutePath(),
			replacements("{$webSiteName}", "HEYVIP", "{$webSiteValue}", "533"));
		assertEquals("site=HEYVIP\nvalue=533\n", content);
	}

	// 空模板回傳空字串（不丟例外）—— 擋這種情況是 WhiteLabelTool 的責任，不是這一層
	@Test
	public void testFillFileOnEmptyTemplateReturnsEmpty() throws IOException {
		File template = writeTemplate("empty.txt", "");
		assertEquals("", TemplateEngine.fillFile(template.getAbsolutePath(), replacements()));
	}

	// fill：null 值視為空字串
	@Test
	public void testFillTreatsNullValueAsEmptyString() {
		Map<String, String> map = new HashMap<>();
		map.put("{$x}", null);
		assertEquals("a=", TemplateEngine.fill("a={$x}", map));
	}

	// fill：沒有對應的 placeholder 原樣保留，交給 PlaceholderValidator 去抓
	@Test
	public void testFillLeavesUnknownPlaceholderUntouched() {
		assertEquals("a={$typo}", TemplateEngine.fill("a={$typo}", replacements("{$x}", "1")));
	}

	// writeToFile 正常寫入
	@Test
	public void testWriteToFileWritesContent() throws IOException {
		File out = new File(folder.getRoot(), "out.sql");
		TemplateEngine.writeToFile(out.getAbsolutePath(), "INSERT INTO t VALUES (1);");
		assertEquals("INSERT INTO t VALUES (1);",
			new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
	}

	// 寫不出去 → 丟 IOException（以前只印一行 stderr 就當沒事）
	@Test
	public void testWriteToFileThrowsWhenPathIsNotWritable() {
		String unwritable = new File(folder.getRoot(), "missing-dir/out.sql").getAbsolutePath();
		try {
			TemplateEngine.writeToFile(unwritable, "content");
			fail("寫入不存在的目錄卻沒有丟例外");
		} catch (IOException expected) {
			// 預期行為
		}
	}
}
