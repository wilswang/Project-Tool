package tool.whiteLabel;

import lombok.Data;

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class WhiteLabelConfig {

	private boolean sqlOnly;
	
	@NotBlank(message = "ticketNo 不可為空")
	private String ticketNo;
	
	@NotBlank(message = "webSiteName 不可為空")
	private String webSiteName;
	
	@NotNull(message = "webSiteValue 不可為空")
	@Min(value = 1, message = "webSiteValue 必須大於 0")
	private Integer webSiteValue;
	
	private String host;
	
	private boolean apiWhiteLabel;
	
	private boolean customized;
	
	@NotNull(message = "jiraSummary 不可為空")
	private String jiraSummary;
	
	private String fixVersion;
	
	private String developer = "MCP";
	
	@Valid
	@JsonProperty("apiWalletInfo")
	private ApiWalletInfo apiWalletInfo;

	@JsonProperty("files")
	private List<FileConfig> files;

	/**
	 * 逐單的環境值覆寫，形狀為 環境名稱 -> key/value，例如
	 * "envValues": { "UAT": { "apiHost": "https://..." } }
	 *
	 * 優先序高於共用的 config/env-values.json。
	 * 型別刻意用 Map：PlaceholderMapper.isConfigObject 對 Map 回傳 false，
	 * 所以這個欄位會被自動映射靜靜跳過，不會產生 {$envValues} 這種無意義的 placeholder。
	 */
	@JsonProperty("envValues")
	private Map<String, Map<String, Object>> envValues;

	/**
	 * 额外的动态属性（JSON 中未在类中定义的字段）
	 * 支持在不修改类定义的情况下添加新的占位符
	 */
	private Map<String, Object> additionalProperties = new HashMap<>();

	/**
	 * Jackson 反序列化时，将未知字段存入 additionalProperties
	 *
	 * @param name 字段名
	 * @param value 字段值
	 */
	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	/**
	 * Jackson 序列化时，将 additionalProperties 中的字段输出到 JSON
	 *
	 * @return 额外属性 Map
	 */
	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	/**
	 * 获取额外属性的值
	 *
	 * @param name 属性名
	 * @return 属性值，不存在则返回 null
	 */
	public Object getAdditionalProperty(String name) {
		return this.additionalProperties.get(name);
	}

	/**
	 * 取代 commons-collections4 的 CollectionUtils.isEmpty。
	 *
	 * 全專案只有這個檔案用得到，為了三次呼叫背 1.6MB 的相依不划算，
	 * 所以把相依移除、行為原樣保留（null 或空集合都算 empty）。
	 */
	private static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	public void validate() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<WhiteLabelConfig>> violations = validator.validate(this);
		
		if (!violations.isEmpty()) {
			for (ConstraintViolation<WhiteLabelConfig> violation : violations) {
				System.err.println("❌ 驗證錯誤: " + violation.getPropertyPath() + " - " + violation.getMessage());
			}
			System.exit(1);
		}
		
		// 自定邏輯條件
		if (apiWhiteLabel) {
			if (apiWalletInfo == null) {
				System.err.println("❌ 驗證錯誤: 當 apiWhiteLabel 為 true 時，apiWalletInfo 不可為 null");
				System.exit(1);
			} else {
				if (apiWalletInfo.isNewGroup()) {
					if (apiWalletInfo.getGroupInfo() == null) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，groupInfo 不可為 null");
						System.exit(1);
					}
					if (isEmpty(apiWalletInfo.getGroupInfo().getBkIpSetId())) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，bkIpSetId 不可為 null");
						System.exit(1);
					}
					// 必須剛好兩個：第 1 個給 {$wwwgaIpSetId}、第 2 個給 {$wwwcfIpSetId}。
					// 只檢查非空是不夠的 —— GroupInfoMapper 逐列取試算表的 M 欄且會跳過空白，
					// 群組第二列沒填就只會回一個，以前會在 NewGroupSqlBuilder 直接 IndexOutOfBounds
					if (apiWalletInfo.getGroupInfo().getBkIpSetId().size() < 2) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，bkIpSetId 需要 2 個"
							+ "（第 1 個是 GA、第 2 個是 CF 的 IP Set ID），目前只有 "
							+ apiWalletInfo.getGroupInfo().getBkIpSetId().size() + " 個");
						System.exit(1);
					}
					for (String item : apiWalletInfo.getGroupInfo().getBkIpSetId()) {
						if (StringUtils.isBlank(item)) {
							System.err.println("❌ 當 newGroup 為 true 時，bkIpSetId 中不可有 null 元素");
							System.exit(1);
						}
					}
					if (isEmpty(apiWalletInfo.getGroupInfo().getBackup())) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，backup 不可為 null");
						System.exit(1);
					}
					for (String item : apiWalletInfo.getGroupInfo().getBackup()) {
						if (StringUtils.isBlank(item)) {
							System.err.println("❌ 當 newGroup 為 true 時，backup 中不可有 null 元素");
							System.exit(1);
						}
					}
					if (isEmpty(apiWalletInfo.getGroupInfo().getPrivateIp())) {
						System.err.println("❌ 當 newGroup 為 true 時，privateIp 不可為 null");
						System.exit(1);
					}
					for (String item : apiWalletInfo.getGroupInfo().getPrivateIp()) {
						if (StringUtils.isBlank(item)) {
							System.err.println("❌ 當 newGroup 為 true 時，privateIp 中不可有 null 元素");
							System.exit(1);
						}
					}
				}
			}
		} else {
			if (host == null || "".equals(host)) {
				System.err.println("❌ 驗證錯誤: 當 apiWhiteLabel 為 false 時，host 不可缺失");
				System.exit(1);
			}
		}
	}
	
	@Override
	public String toString() {
		return "WhiteLabelConfig{" + "sqlOnly=" + sqlOnly + ", ticketNo='" + ticketNo + '\'' + ", webSiteName='" + webSiteName + '\''
			+ ", webSiteValue=" + webSiteValue + ", host='" + host + '\'' + ", apiWhiteLabel=" + apiWhiteLabel + ", customized=" + customized
			+ ", jiraSummary='" + jiraSummary + '\'' + ", fixVersion='" + fixVersion + '\'' + ", developer='" + developer + '\'' + ", apiWalletInfo="
			+ apiWalletInfo + ", additionalProperties=" + additionalProperties + ", envValues=" + envValues
			+ ", files=" + files + '}';
	}
	
}
