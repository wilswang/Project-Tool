package tool.whiteLabel;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 讀取並驗證共用環境值設定檔（預設 ./config/env-values.json），以及單號 JSON 裡的 envValues 覆寫區塊。
 *
 * 檔案不存在時回傳空 Map 並印警告（向後相容：沒有這個檔的環境仍可跑）；
 * 檔案存在但格式錯誤時丟 EnvValuesException，由呼叫端在產出任何檔案之前中止。
 *
 * 用 Jackson 而非 FileReader：TemplateEngine 走平台預設編碼，而 JSON 規定用 UTF-8，環境值可能含非 ASCII 字元。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public final class EnvValuesLoader {

	public static final String DEFAULT_PATH = "./config/env-values.json";

	/** {$env} 由工具保留，不可由設定檔提供 */
	static final Set<String> RESERVED_KEYS = Collections.singleton("env");

	/** 值必須是字串陣列的 key */
	static final Set<String> LIST_KEYS =
		Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("extraPrivateDomains", "extraPublicDomains")));

	/** 值必須是布林的 key */
	static final Set<String> BOOLEAN_KEYS = Collections.singleton("activateOwnDomains");

	private EnvValuesLoader() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 讀共用設定檔。
	 *
	 * @return 環境名稱 -> 該環境的 key/value；檔案不存在時回傳空 Map
	 * @throws EnvValuesException JSON 解析失敗或結構不合
	 */
	public static Map<String, Map<String, Object>> loadFile(String filePath) throws EnvValuesException {
		File file = new File(filePath);
		if (!file.exists()) {
			System.err.println("⚠️  找不到環境值設定檔: " + filePath);
			System.err.println("⚠️  將改用 EnvEnumType 內建值 (DEV/UAT/SIM)。若模板需要檔案提供的 placeholder，產檔會失敗。");
			return new LinkedHashMap<>();
		}

		Map<String, Map<String, Object>> raw;
		try {
			raw = new ObjectMapper().readValue(file,
				new TypeReference<LinkedHashMap<String, Map<String, Object>>>() { });
		} catch (IOException e) {
			throw new EnvValuesException("環境值設定檔解析失敗 (" + filePath + "): " + e.getMessage(), e);
		}

		if (raw == null) {
			throw new EnvValuesException("環境值設定檔內容為空 (" + filePath + ")");
		}

		validate(raw, filePath);
		return raw;
	}

	/**
	 * 驗證單一來源（共用檔或單號覆寫）的結構。錯誤訊息一律帶上來源與環境名稱，方便直接定位。
	 */
	static void validate(Map<String, Map<String, Object>> raw, String sourceLabel) throws EnvValuesException {
		for (Map.Entry<String, Map<String, Object>> envEntry : raw.entrySet()) {
			String envName = envEntry.getKey();
			Object envBlock = envEntry.getValue();

			if (envBlock == null) {
				throw new EnvValuesException(sourceLabel + " 的環境 " + envName + " 內容為 null，應為物件");
			}
			if (!(envBlock instanceof Map)) {
				throw new EnvValuesException(sourceLabel + " 的環境 " + envName + " 應為物件，實際為 "
					+ envBlock.getClass().getSimpleName());
			}

			for (Map.Entry<String, Object> valueEntry : envEntry.getValue().entrySet()) {
				validateValue(sourceLabel, envName, valueEntry.getKey(), valueEntry.getValue());
			}
		}
	}

	private static void validateValue(String sourceLabel, String envName, String key, Object value)
			throws EnvValuesException {
		String where = sourceLabel + " 的 " + envName + "." + key;

		if (RESERVED_KEYS.contains(key)) {
			throw new EnvValuesException(where + " 使用了工具保留字，請改用其他名稱（保留字: " + RESERVED_KEYS + "）");
		}
		if (value == null) {
			throw new EnvValuesException(where + " 的值為 null，請填值或整個移除");
		}

		if (LIST_KEYS.contains(key)) {
			if (!(value instanceof List)) {
				throw new EnvValuesException(where + " 應為字串陣列，實際為 " + value.getClass().getSimpleName());
			}
			for (Object element : (List<?>) value) {
				if (!(element instanceof String)) {
					throw new EnvValuesException(where + " 的陣列元素應為字串，實際出現 "
						+ (element == null ? "null" : element.getClass().getSimpleName()));
				}
			}
			return;
		}

		if (BOOLEAN_KEYS.contains(key)) {
			if (!(value instanceof Boolean)) {
				throw new EnvValuesException(where + " 應為 true/false，實際為 " + value.getClass().getSimpleName());
			}
			return;
		}

		// 其餘 key 都會變成 {$key} placeholder，所以必須是純量
		if (value instanceof Map) {
			throw new EnvValuesException(where + " 是巢狀物件，環境值只支援純量（模板 placeholder 無法表達巢狀結構）");
		}
		if (value instanceof List) {
			throw new EnvValuesException(where + " 是陣列，但只有 " + LIST_KEYS + " 支援陣列；"
				+ "其餘 key 會變成 {$" + key + "} placeholder，必須是純量");
		}
	}
}
