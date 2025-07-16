package tool.whiteLabel;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ApiWalletInfo {
	
	@NotBlank(message = "cert 不可為空")
	private String cert;
	
	private boolean newGroup;
	
	@NotBlank(message = "group 不可為空")
	private String group;
	
	@Valid
	@JsonProperty("groupInfo")
	private GroupInfo groupInfo;
	
	@Override
	public String toString() {
		return "ApiWalletInfo{" + "cert='" + cert + '\'' + ", newGroup=" + newGroup + ", group='" + group + '\'' + ", groupInfo=" + groupInfo + '}';
	}
}
