import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class TemplateEngine {
	
	public static String fillFile(String filePath, Map<String, String> replacements) {
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = fill(line, replacements);
				content.append(line).append("\n");
			}
		} catch (IOException e) {
			System.err.println("讀取 " + filePath + " 模板發生錯誤: " + e.getMessage());
		}
		return content.toString();
	}
	
	public static String fill(String line, Map<String, String> replacements) {
		for (Map.Entry<String, String> entry : replacements.entrySet()) {
			line = line.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
		}
		return line;
	}
	
	public static void writeToFile(String fileName, String content) {
		try (FileWriter writer = new FileWriter(fileName)) {
			writer.write(content);
			System.out.println("✅ 文字已成功儲存至 " + fileName);
		} catch (IOException e) {
			System.err.println("寫入檔案時發生錯誤: " + e.getMessage());
		}
	}

}
