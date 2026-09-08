package tool.whiteLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import constant.EnvEnumType;

/**
 * 把三層環境值合併成一份 EnvValues。
 *
 * 優先序（高 -> 低）：
 *   1. 單號 JSON 的 envValues 區塊（逐單客製）
 *   2. 共用設定檔 config/env-values.json（全站共用）
 *   3. EnvEnumType 內建值（向後相容的 fallback，只有 subDomainStatic / subDomainApi）
 *
 * 三層都沒有該環境時丟 UnknownEnvironmentException。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class EnvValuesResolver {

	private static final String SUB_DOMAIN_STATIC = "subDomainStatic";
	private static final String SUB_DOMAIN_API = "subDomainApi";
	private static final String EXTRA_PRIVATE_DOMAINS = "extraPrivateDomains";
	private static final String EXTRA_PUBLIC_DOMAINS = "extraPublicDomains";
	private static final String ACTIVATE_OWN_DOMAINS = "activateOwnDomains";

	/** 環境名稱大寫 -> 該環境的 key/value，供不分大小寫查詢 */
	private final Map<String, Map<String, Object>> shared;
	private final Map<String, Map<String, Object>> perTicket;

	private EnvValuesResolver(Map<String, Map<String, Object>> shared, Map<String, Map<String, Object>> perTicket) {
		this.shared = shared;
		this.perTicket = perTicket;
	}

	/**
	 * 讀共用設定檔並驗證單號的 envValues 區塊。
	 * 共用檔不存在只會印警告；格式錯誤則丟例外，讓呼叫端在產出任何檔案之前中止。
	 */
	public static EnvValuesResolver create(String envValuesPath, WhiteLabelConfig whiteLabelConfig)
			throws EnvValuesException {
		Map<String, Map<String, Object>> sharedRaw = EnvValuesLoader.loadFile(envValuesPath);

		Map<String, Map<String, Object>> ticketRaw = whiteLabelConfig.getEnvValues();
		if (ticketRaw == null) {
			ticketRaw = new LinkedHashMap<>();
		} else {
			EnvValuesLoader.validate(ticketRaw, "單號 JSON 的 envValues");
		}

		return new EnvValuesResolver(indexByUpperCase(sharedRaw), indexByUpperCase(ticketRaw));
	}

	/**
	 * 解析一個環境的所有值。envName 原樣保留（{$env} 與輸出檔名用的是原字串）。
	 */
	public EnvValues resolve(String envName) throws UnknownEnvironmentException {
		String key = envName == null ? "" : envName.toUpperCase();
		EnvEnumType builtIn = EnvEnumType.findByName(envName);
		Map<String, Object> sharedBlock = shared.get(key);
		Map<String, Object> ticketBlock = perTicket.get(key);

		if (builtIn == null && sharedBlock == null && ticketBlock == null) {
			throw new UnknownEnvironmentException("未知的環境 " + envName
				+ "：共用設定檔 (" + EnvValuesLoader.DEFAULT_PATH + ")、單號 envValues、EnvEnumType 內建值都沒有這個名稱");
		}

		// 第 3 層：內建值墊底
		Map<String, String> scalars = new LinkedHashMap<>();
		if (builtIn != null) {
			putIfNotNull(scalars, SUB_DOMAIN_STATIC, builtIn.getSubDomainStatic());
			putIfNotNull(scalars, SUB_DOMAIN_API, builtIn.getSubDomainApi());
		}

		// 第 2 層再蓋第 1 層
		List<String> extraPrivateDomains = new ArrayList<>();
		List<String> extraPublicDomains = new ArrayList<>();
		boolean activateOwnDomains = true;

		for (Map<String, Object> block : Arrays.asList(sharedBlock, ticketBlock)) {
			if (block == null) {
				continue;
			}
			for (Map.Entry<String, Object> entry : block.entrySet()) {
				String name = entry.getKey();
				Object value = entry.getValue();
				if (EXTRA_PRIVATE_DOMAINS.equals(name)) {
					extraPrivateDomains = toStringList(value);
				} else if (EXTRA_PUBLIC_DOMAINS.equals(name)) {
					extraPublicDomains = toStringList(value);
				} else if (ACTIVATE_OWN_DOMAINS.equals(name)) {
					activateOwnDomains = (Boolean) value;
				} else {
					scalars.put(name, String.valueOf(value));
				}
			}
		}

		return new EnvValues(envName, scalars, extraPrivateDomains, extraPublicDomains, activateOwnDomains);
	}

	/**
	 * 該環境在共用設定檔或單號 envValues 裡有沒有明確的條目（不含 EnvEnumType 內建值）。
	 *
	 * newGroup 的產出會用到 extraPrivateDomains / extraPublicDomains / activateOwnDomains，
	 * 而這三者只存在設定檔、內建值沒有對應資料。少了它們產出的 SQL 是「看起來合法但錯的」
	 * ——不會留下 {$token} 讓 guard 抓到——所以 newGroup 必須要求明確條目，不能默默用預設值。
	 */
	public boolean hasExplicitEntry(String envName) {
		String key = envName == null ? "" : envName.toUpperCase();
		return shared.containsKey(key) || perTicket.containsKey(key);
	}

	/**
	 * 回報哪些 key 會蓋掉既有的 base placeholder 且值不同 —— 用來抓「好心在 env-values.json
	 * 加了 developer 結果劫走 {$developer}」這種意外。
	 */
	public Set<String> findShadowedKeys(Map<String, String> baseReplacements, EnvValues envValues) {
		Set<String> shadowed = new LinkedHashSet<>();
		for (Map.Entry<String, String> entry : envValues.toPlaceholders().entrySet()) {
			String existing = baseReplacements.get(entry.getKey());
			if (existing != null && !existing.equals(entry.getValue())) {
				shadowed.add(entry.getKey());
			}
		}
		return shadowed;
	}

	private static Map<String, Map<String, Object>> indexByUpperCase(Map<String, Map<String, Object>> raw) {
		Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
			indexed.put(entry.getKey().toUpperCase(), entry.getValue());
		}
		return indexed;
	}

	private static void putIfNotNull(Map<String, String> target, String key, String value) {
		if (value != null) {
			target.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> toStringList(Object value) {
		return new ArrayList<>((List<String>) value);
	}
}
