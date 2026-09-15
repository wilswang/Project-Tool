package tool.sheet;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 載入 {@code sheet-config.json}。
 *
 * <p>用 <b>Jackson 而非 FileReader</b> —— 與 {@code EnvValuesLoader} 同樣的理由：
 * {@code FileReader} 走平台預設編碼，而 JSON 規定 UTF-8。分頁名含中文
 * （{@code API 2.0Domain配置_20260814}），用錯編碼會變成對不上線上分頁的亂碼。
 *
 * <p>與 {@code EnvValuesLoader} 的一個刻意差異：<b>檔案不存在時 fail fast（丟例外）</b>，
 * 不學它印 ⚠️ 回空 Map。env-values 那樣做是為了向後相容舊環境；這裡沒有舊環境，
 * 而且「設定檔沒讀到卻繼續跑」正是會產生錯誤資料的情境。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class SheetConfigLoader {

	public static final String DEFAULT_FILE_NAME = "sheet-config.json";

	private SheetConfigLoader() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 依 {@link ConfigPathResolver} 的搜尋順序載入設定檔。
	 *
	 * @param cliPath CLI 的 --config，null 表示走預設搜尋
	 */
	public static SheetConfig load(String cliPath) throws SheetToolException {
		File file;
		if (cliPath != null && !cliPath.trim().isEmpty()) {
			file = new File(cliPath.trim());
			if (!file.isFile()) {
				throw new SheetToolException("--config 指定的設定檔不存在: " + file.getAbsolutePath());
			}
		} else {
			file = ConfigPathResolver.resolveRequired(DEFAULT_FILE_NAME);
		}
		return loadFile(file);
	}

	/** 從指定檔案載入，主要給測試與已解析路徑的呼叫端用 */
	public static SheetConfig loadFile(File file) throws SheetToolException {
		if (file == null || !file.isFile()) {
			throw new SheetToolException("設定檔不存在: " + (file == null ? "null" : file.getAbsolutePath()));
		}
		if (file.length() == 0) {
			throw new SheetToolException("設定檔為空檔: " + file.getAbsolutePath());
		}
		SheetConfig config;
		try {
			config = new ObjectMapper().readValue(file, SheetConfig.class);
		} catch (IOException e) {
			throw new SheetToolException("設定檔解析失敗 (" + file.getAbsolutePath() + "): " + e.getMessage(), e);
		}
		if (config == null) {
			throw new SheetToolException("設定檔解析結果為空: " + file.getAbsolutePath());
		}
		validate(config, file.getAbsolutePath());
		return config;
	}

	/**
	 * 結構驗證。只驗「不管跑哪個指令都必須成立」的部分 ——
	 * 個別 target 的 spreadsheetId 留空是允許的（check-auth 不需要它），
	 * 那一層由 {@link SheetConfig#validateForRead(String)} 在讀取類指令時才檢查。
	 */
	static void validate(SheetConfig config, String sourceLabel) throws SheetToolException {
		if (config.getTargets() == null || config.getTargets().isEmpty()) {
			throw new SheetToolException(sourceLabel + " 沒有定義任何 target");
		}
		String defaultTarget = config.getDefaultTarget();
		if (defaultTarget != null && !defaultTarget.trim().isEmpty()
				&& !config.getTargets().containsKey(defaultTarget.trim())) {
			throw new SheetToolException(sourceLabel + " 的 defaultTarget '" + defaultTarget
				+ "' 不在 targets 裡。可用的 target: " + config.getTargets().keySet());
		}
		for (java.util.Map.Entry<String, SheetConfig.Target> entry : config.getTargets().entrySet()) {
			SheetConfig.Target target = entry.getValue();
			if (target == null) {
				throw new SheetToolException(sourceLabel + " 的 target '" + entry.getKey() + "' 是空的");
			}
			if (target.getGroupInfo() != null) {
				target.getGroupInfo().validate(sourceLabel + " 的 target '" + entry.getKey() + "'");
			}
		}
	}
}
