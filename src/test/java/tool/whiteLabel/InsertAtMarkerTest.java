package tool.whiteLabel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * {@link WhiteLabelTool#insertAtMarker} 的測試，重點在 <b>marker 不存在時不再靜默 no-op</b>。
 *
 * <p>原本找不到 marker 就什麼都不插，然後無條件印 {@code ✅ Content successfully written}
 * 並把原內容照樣寫回：檔案看起來被處理過，實際少了一整段註冊程式碼，而且不會編譯錯。
 *
 * @author Wilson.Wang
 * @version 1.5.2
 */
public class InsertAtMarkerTest {

	private static final String MARKER = "// insert New White Label";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path javaFile(String content) throws IOException {
		File file = folder.newFile("Target.java");
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.toPath();
	}

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	// marker 存在 → 插在 marker 之前，並沿用 marker 那行的縮排
	@Test
	public void testInsertsBeforeMarkerWithMatchingIndent() throws Exception {
		Path target = javaFile("class T {\n\t\t" + MARKER + "\n}\n");
		WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
		assertEquals("class T {\n\t\tFOO(1),\n\t\t" + MARKER + "\n}\n", read(target));
	}

	// insertAfter=true → 插在 marker 之後
	@Test
	public void testInsertsAfterMarkerWhenRequested() throws Exception {
		Path target = javaFile("class T {\n\t" + MARKER + "\n}\n");
		WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", true);
		assertEquals("class T {\n\t" + MARKER + "\n\tFOO(1),\n}\n", read(target));
	}

	// 多行內容每一行都套上同樣的縮排
	@Test
	public void testMultiLineContentKeepsIndentOnEveryLine() throws Exception {
		Path target = javaFile("class T {\n\t" + MARKER + "\n}\n");
		WhiteLabelTool.insertAtMarker(target, MARKER, "A a = new A();\nMAP.put(K, a);", false);
		assertEquals("class T {\n\tA a = new A();\n\tMAP.put(K, a);\n\t" + MARKER + "\n}\n", read(target));
	}

	// marker 不存在 → 丟 MarkerNotFoundException
	@Test
	public void testThrowsWhenMarkerIsMissing() throws Exception {
		Path target = javaFile("class T {\n\t// some other comment\n}\n");
		try {
			WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
			fail("marker 不存在卻沒有丟例外");
		} catch (MarkerNotFoundException expected) {
			assertTrue("訊息應帶上 marker 字串", expected.getMessage().contains(MARKER));
		}
	}

	// marker 不存在時，目標檔必須逐 byte 不變 —— 例外要丟在 Files.write 之前
	@Test
	public void testTargetFileIsUntouchedWhenMarkerIsMissing() throws Exception {
		String original = "class T {\n\t// some other comment\n}\n";
		Path target = javaFile(original);
		byte[] before = Files.readAllBytes(target);
		try {
			WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
			fail("marker 不存在卻沒有丟例外");
		} catch (MarkerNotFoundException expected) {
			// 預期行為
		}
		assertArrayEquals("目標檔不該被改動", before, Files.readAllBytes(target));
		assertEquals(original, read(target));
	}

	// 空檔案也算找不到 marker
	@Test
	public void testEmptyFileCountsAsMarkerMissing() throws Exception {
		Path target = javaFile("");
		try {
			WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
			fail("空檔案卻沒有丟例外");
		} catch (MarkerNotFoundException expected) {
			// 預期行為
		}
	}

	// marker 出現兩次 → 照插兩次（維持既有行為），但這是刻意保留的，不是沒注意到
	@Test
	public void testMarkerAppearingTwiceInsertsTwice() throws Exception {
		Path target = javaFile("class T {\n\t" + MARKER + "\n\tint x;\n\t" + MARKER + "\n}\n");
		WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
		String result = read(target);
		int occurrences = result.split("FOO\\(1\\),", -1).length - 1;
		assertEquals(2, occurrences);
	}

	// marker 是子字串比對：整行包含即可，不必等於
	@Test
	public void testMarkerMatchesAsSubstringOfTheLine() throws Exception {
		Path target = javaFile("class T {\n\t" + MARKER + " SINGLE_WALLET\n}\n");
		WhiteLabelTool.insertAtMarker(target, MARKER, "FOO(1),", false);
		assertTrue(read(target).contains("\tFOO(1),\n\t" + MARKER + " SINGLE_WALLET"));
	}
}
