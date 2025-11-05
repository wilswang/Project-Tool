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
}
