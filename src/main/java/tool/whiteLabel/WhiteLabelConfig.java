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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

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
					if (CollectionUtils.isEmpty(apiWalletInfo.getGroupInfo().getBkIpSetId())) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，bkIpSetId 不可為 null");
						System.exit(1);
					}
					for (String item : apiWalletInfo.getGroupInfo().getBkIpSetId()) {
						if (StringUtils.isBlank(item)) {
							System.err.println("❌ 當 newGroup 為 true 時，bkIpSetId 中不可有 null 元素");
							System.exit(1);
						}
					}
					if (CollectionUtils.isEmpty(apiWalletInfo.getGroupInfo().getBackup())) {
						System.err.println("❌ 驗證錯誤: 當 newGroup 為 true 時，backup 不可為 null");
						System.exit(1);
					}
					for (String item : apiWalletInfo.getGroupInfo().getBackup()) {
						if (StringUtils.isBlank(item)) {
							System.err.println("❌ 當 newGroup 為 true 時，backup 中不可有 null 元素");
							System.exit(1);
						}
					}
					if (CollectionUtils.isEmpty(apiWalletInfo.getGroupInfo().getPrivateIp())) {
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
			+ apiWalletInfo + '}';
	}
	
}
