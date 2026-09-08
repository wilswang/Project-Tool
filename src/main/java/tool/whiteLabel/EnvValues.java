package tool.whiteLabel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 單一環境解析完成後的值袋，由 EnvValuesResolver 依「單號 envValues → 共用設定檔 → EnvEnumType 內建值」
 * 的優先序組裝而成，組好之後不可變動。
 *
 * scalars 裡的每個 key 都會變成一個 {$key} placeholder；
 * extraPrivateDomains / extraPublicDomains / activateOwnDomains 則是給 NewGroupSqlBuilder
 * 決定要多插幾列、以及 isactive 要填什麼用的，不會變成 placeholder。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public class EnvValues {

	private static final String SUB_DOMAIN_STATIC = "subDomainStatic";
	private static final String SUB_DOMAIN_API = "subDomainApi";

	private final String envName;
	private final Map<String, String> scalars;
	private final List<String> extraPrivateDomains;
	private final List<String> extraPublicDomains;
	private final boolean activateOwnDomains;

	EnvValues(String envName, Map<String, String> scalars, List<String> extraPrivateDomains,
			  List<String> extraPublicDomains, boolean activateOwnDomains) {
		this.envName = envName;
		this.scalars = Collections.unmodifiableMap(new LinkedHashMap<>(scalars));
		this.extraPrivateDomains = Collections.unmodifiableList(new ArrayList<>(extraPrivateDomains));
		this.extraPublicDomains = Collections.unmodifiableList(new ArrayList<>(extraPublicDomains));
		this.activateOwnDomains = activateOwnDomains;
	}

	public String getEnvName() {
		return envName;
	}

	public String get(String key) {
		return scalars.get(key);
	}

	public String getSubDomainStatic() {
		return scalars.get(SUB_DOMAIN_STATIC);
	}

	public String getSubDomainApi() {
		return scalars.get(SUB_DOMAIN_API);
	}

	public List<String> getExtraPrivateDomains() {
		return extraPrivateDomains;
	}

	public List<String> getExtraPublicDomains() {
		return extraPublicDomains;
	}

	public boolean isActivateOwnDomains() {
		return activateOwnDomains;
	}

	/**
	 * 每個 scalar key 轉成 {$key} -> value，供 TemplateEngine 使用。
	 * 不含 {$env}：那是工具保留字，由 WhiteLabelTool 最後自己寫入。
	 */
	public Map<String, String> toPlaceholders() {
		Map<String, String> placeholders = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : scalars.entrySet()) {
			placeholders.put("{$" + entry.getKey() + "}", entry.getValue());
		}
		return placeholders;
	}

	@Override
	public String toString() {
		return "EnvValues{envName='" + envName + "', scalars=" + scalars
			+ ", extraPrivateDomains=" + extraPrivateDomains
			+ ", extraPublicDomains=" + extraPublicDomains
			+ ", activateOwnDomains=" + activateOwnDomains + "}";
	}
}
