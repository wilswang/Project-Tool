package tool.http;

import lombok.Data;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.Set;

/**
 * HTTP Client 配置類別
 * 包含 baseUrl、認證資訊、timeout 設定等
 */
@Data
public class HttpClientConfig {

	@NotBlank(message = "baseUrl 不可為空")
	private String baseUrl;

	@NotBlank(message = "account 不可為空")
	private String account;

	@NotBlank(message = "token 不可為空")
	private String token;

	@Min(value = 1000, message = "connectTimeout 不得少於 1 秒")
	private Integer connectTimeout = 30000;  // 預設 30 秒

	@Min(value = 1000, message = "readTimeout 不得少於 1 秒")
	private Integer readTimeout = 60000;     // 預設 60 秒

	@Min(value = 1000, message = "writeTimeout 不得少於 1 秒")
	private Integer writeTimeout = 60000;    // 預設 60 秒

	private Integer maxIdleConnections = 5;
	private Long keepAliveDuration = 300000L; // 5 分鐘

	/**
	 * 驗證配置使用 Hibernate Validator
	 * 參考 WhiteLabelConfig.java 的 validate() 模式
	 */
	public void validate() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<HttpClientConfig>> violations = validator.validate(this);

		if (!violations.isEmpty()) {
			for (ConstraintViolation<HttpClientConfig> violation : violations) {
				System.err.println("❌ 驗證錯誤: " + violation.getPropertyPath() + " - " + violation.getMessage());
			}
			throw new IllegalArgumentException("HttpClientConfig 驗證失敗");
		}
	}
}
