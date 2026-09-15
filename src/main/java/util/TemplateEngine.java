package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * 模板填值與讀寫。
 *
 * <p><b>IO 例外一律往上拋，不在這裡吞掉。</b>原本 {@link #fillFile} 與 {@link #writeToFile}
 * 都 catch 住 {@link IOException} 只印一行 stderr：前者回傳空字串，呼叫端照樣寫檔並印
 * {@code ✅ Created}，於是產生 0-byte 檔而整個流程回報成功。2026-09-09 實際發生過 ——
 * 模板路徑指向不存在的 {@code RacingOnly/DB-41-template.txt}，三個 DB-41 都是空的，
 * step 3 與 step 4 卻都是綠的。
 *
 * <p>{@code PlaceholderValidator} 攔不住這種情況：空字串裡沒有任何 {@code {$token}}，
 * 驗證當然通過。所以唯一的防線是讓錯誤浮上來。
 */
public class TemplateEngine {

	public static String fillFile(String filePath, Map<String, String> replacements) throws IOException {
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = fill(line, replacements);
				content.append(line).append("\n");
			}
		}
		return content.toString();
	}

	public static String fill(String line, Map<String, String> replacements) {
		for (Map.Entry<String, String> entry : replacements.entrySet()) {
			line = line.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
		}
		return line;
	}

	public static void writeToFile(String fileName, String content) throws IOException {
		try (FileWriter writer = new FileWriter(fileName)) {
			writer.write(content);
		}
		System.out.println("✅ 文字已成功儲存至 " + fileName);
	}

}
