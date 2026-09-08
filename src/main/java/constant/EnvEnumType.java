package constant;

import lombok.Getter;

@Getter
public enum EnvEnumType {

	DEV("devnginx", "dev9wapi"),
	UAT("tberwxsjyk", "uat9wapi"),
	SIM("www", "saapipl"),
	;
	
	private String subDomainStatic;
	private String subDomainApi;
	
	EnvEnumType() {
	
	}
	
	EnvEnumType(String subDomainStatic, String subDomainApi) {
		this.subDomainStatic = subDomainStatic;
		this.subDomainApi = subDomainApi;
	}

	/**
	 * 依名稱查內建環境（不分大小寫）。找不到時回傳 null，而不是像 valueOf 直接丟例外 ——
	 * 環境現在可以只存在於 config/env-values.json，內建值只是 fallback。
	 */
	public static EnvEnumType findByName(String name) {
		if (name == null) {
			return null;
		}
		for (EnvEnumType type : values()) {
			if (type.name().equalsIgnoreCase(name)) {
				return type;
			}
		}
		return null;
	}
}
