package dto;

import lombok.Data;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;


@Data
public class GroupInfo {
	
	@NotBlank(message = "privateIpSetId 不可為空")
	private String privateIpSetId;
	
	@NotNull(message = "privateIp 不可為空")
//	@Size(min = 1, message = "privateIp 至少要有一筆")
	@JsonProperty("privateIp")
	private List<String> privateIp;
	
	@NotNull(message = "bkIpSetId 不可為空")
//	@Size(min = 2, message = "bkIpSetId 至少要有兩筆")
	@JsonProperty("bkIpSetId")
	private List<String> bkIpSetId;
	
	@NotBlank(message = "apiInfoBkIpSetId 不可為空")
	private String apiInfoBkIpSetId;
	
	@NotNull(message = "backup 不可為空")
//	@Size(min = 1, message = "backup 至少要有一筆")
	@JsonProperty("backup")
	private List<String> backup;
	
	@Override
	public java.lang.String toString() {
		return "GroupInfo{" + "privateIpSetId='" + privateIpSetId + '\'' + ", privateIp=" + privateIp + ", bkIpSetId=" + bkIpSetId
			+ ", apiInfoBkIpSetId='" + apiInfoBkIpSetId + '\'' + ", backup=" + backup + '}';
	}
}
