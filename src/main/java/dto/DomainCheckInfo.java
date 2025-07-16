package dto;

import lombok.Data;

import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class DomainCheckInfo {
	public String subdomain;
	
	public boolean isHttps;
	
	@NotNull(message = "domainList 不可為空")
	@NotEmpty(message = "domainList 不可為空白")
	public List<String> domainList;
	
	@Min(value = 15000, message = "連線逾時時間不得少於 15 秒")
	@Max(value = 120000, message = "連線逾時時間不得超過 2 分鐘")
	public Integer connectTimeout;
	
	@Min(value = 15000, message = "讀取逾時時間不得少於 15 秒")
	@Max(value = 120000, message = "讀取逾時時間不得超過 2 分鐘")
	public Integer readTimeout;
	
	public void validate() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<DomainCheckInfo>> violations = validator.validate(this);
		
		if (!violations.isEmpty()) {
			for (ConstraintViolation<DomainCheckInfo> violation : violations) {
				System.err.println("❌ 驗證錯誤: " + violation.getPropertyPath() + " - " + violation.getMessage());
			}
			System.exit(1);
		}
	}

}
