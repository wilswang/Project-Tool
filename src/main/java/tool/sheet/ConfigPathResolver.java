package tool.sheet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 開發期與部署期設定檔路徑差異的<b>唯一</b>收斂點。其他類別一律不自己拼相對路徑。
 *
 * <p>差異來自兩邊的目錄佈局不同：
 * <ul>
 *   <li><b>部署期</b>：{@code script/unix/project-tool.sh} 會先 {@code cd} 到 {@code ProjectTool/}，
 *       設定檔在 {@code ./config/}</li>
 *   <li><b>開發期</b>：CWD 是 Project-Tool repo 根目錄，設定檔的主檔在 {@code ./src/config/}
 *       （repo 根目錄沒有 {@code config/}）</li>
 * </ul>
 *
 * <p>所以搜尋順序是 {@code ./config/<name>} → {@code ./src/config/<name>}，
 * 另外開一個系統屬性 {@code projectTool.configDir} 讓測試與特殊情境能指定目錄。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class ConfigPathResolver {

	/** 覆寫搜尋目錄的系統屬性，優先於所有預設路徑 */
	public static final String CONFIG_DIR_PROPERTY = "projectTool.configDir";

	/** 預設搜尋目錄，依序嘗試 */
	static final List<String> DEFAULT_DIRS = Arrays.asList("./config", "./src/config");

	private ConfigPathResolver() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 找出設定檔的實際位置。
	 *
	 * @param fileName 檔名，例如 {@code sheet-config.json}
	 * @return 存在的檔案，找不到時為 empty
	 */
	public static Optional<File> resolve(String fileName) {
		for (String dir : candidateDirs()) {
			File candidate = new File(dir, fileName);
			if (candidate.isFile()) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	/**
	 * 同 {@link #resolve(String)}，但找不到就丟例外。
	 *
	 * <p>錯誤訊息會列出<b>所有試過的絕對路徑</b> —— 這是 {@code EnvValuesLoader} 目前沒做
	 * 但很值得補的一點，否則使用者只會看到「找不到檔案」而不知道該把檔案放哪。
	 */
	public static File resolveRequired(String fileName) throws SheetToolException {
		Optional<File> found = resolve(fileName);
		if (found.isPresent()) {
			return found.get();
		}
		StringBuilder sb = new StringBuilder();
		sb.append("找不到設定檔 ").append(fileName).append("，已嘗試以下路徑:");
		for (String dir : candidateDirs()) {
			sb.append("\n   ").append(new File(dir, fileName).getAbsolutePath());
		}
		sb.append("\n   （可用 -D").append(CONFIG_DIR_PROPERTY).append("=<dir> 指定其他目錄）");
		throw new SheetToolException(sb.toString());
	}

	/**
	 * 解析憑證路徑，優先序：CLI 指定 &gt; 設定檔的 credentialPath &gt; 依預設目錄搜尋。
	 *
	 * @param cliPath        CLI 的 --credential，可為 null
	 * @param configuredPath 設定檔的 credentialPath，可為 null
	 */
	public static File resolveCredential(String cliPath, String configuredPath) throws SheetToolException {
		// CLI 給了就直接用，不做搜尋 —— 使用者明確指定的路徑不該被「聰明地」改掉
		if (isPresent(cliPath)) {
			return requireExistingFile(cliPath, "--credential 指定的憑證檔");
		}
		if (isPresent(configuredPath)) {
			File direct = new File(configuredPath.trim());
			if (direct.isFile()) {
				return direct;
			}
			// 設定檔寫的是 ./config/service-account.json，開發期要能退到 ./src/config/
			Optional<File> found = resolve(direct.getName());
			if (found.isPresent()) {
				return found.get();
			}
			throw new SheetToolException("找不到憑證檔。設定檔的 credentialPath 為 "
				+ configuredPath + "（絕對路徑 " + direct.getAbsolutePath() + "），"
				+ "預設目錄也找不到 " + direct.getName());
		}
		return resolveRequired("service-account.json");
	}

	private static List<String> candidateDirs() {
		List<String> dirs = new ArrayList<>();
		String override = System.getProperty(CONFIG_DIR_PROPERTY);
		if (isPresent(override)) {
			dirs.add(override.trim());
		}
		dirs.addAll(DEFAULT_DIRS);
		return dirs;
	}

	private static File requireExistingFile(String path, String label) throws SheetToolException {
		File file = new File(path.trim());
		if (!file.isFile()) {
			throw new SheetToolException(label + "不存在: " + file.getAbsolutePath());
		}
		return file;
	}

	private static boolean isPresent(String value) {
		return value != null && !value.trim().isEmpty();
	}
}
