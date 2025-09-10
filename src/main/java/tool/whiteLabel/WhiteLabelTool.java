package tool.whiteLabel;

import util.TemplateEngine;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class WhiteLabelTool {
    private static final String PROJECT_PREFIX = "SACRIC-";
    private static final String OUTPUT_PATH = "./result/";
	private static final String CONST_JS = "WebSiteType.{$webSiteName} = {\n" + "\t\"value\": {$webSiteValue},\n" + "\t\"shortCode\": \"{$webSiteName}\",\n"
		+ "\t\"displayName\": \"{$webSiteName}\"\n" + "};\n";
	private static final String TS_FINANCIAL = "\n@HttpUpdate\n" + "public static boolean ENABLE_TS_FINANCIAL_{$webSiteName} = %s;\n\n";
	private static final String ELECTION_FANCY_BET = "\n@HttpUpdate\n" + "public static boolean ENABLE_ELECTION_FANCY_BET_{$webSiteName} = %s;\n\n";
	// apiDomainType 1: public, 0:private
	private static final List<String> UAT_PUBLIC_DOMAIN_LIST = Arrays.asList("qqkk77.net", "qqkk77.live");
	private static final List<String> UAT_PRIVATE_DOMAIN_LIST = Arrays.asList("cckk77.net", "cckk77.live");
	
	private static final Map<String, String> TEMPLATE_PATHS;
	static {
		Map<String, String> tempMap = new HashMap<>();
		tempMap.put("DB_01", "./template/NewSite-DB-01-template.txt");
		tempMap.put("DB_41", "./template/NewSite-DB-41-template.txt");
		tempMap.put("API_DB_01", "./template/ApiWallet-DB-01-template.txt");
		tempMap.put("API_DB_41", "./template/ApiWallet-DB-41-template.txt");
//		tempMap.put("ADD_NEW_GROUP", "./template/ApiWallet-newGroup-DB-01-template.txt");
//		tempMap.put("UPDATE_GROUP", "./template/ApiWallet-updateGroup-DB-01-template.txt");
		tempMap.put("DOMAIN_TYPE", "./template/DomainTypeTemplate.txt");
		tempMap.put("WEB_SITE_PAGE", "./template/WebSitePageTemplate.txt");
		tempMap.put("API_WALLET_WEB_SITE_PAGE", "./template/ApiWalletWebSitePageTemplate.txt");
		tempMap.put("NEW_SITE_OTHER", "./template/NewSite-WST.txt");
		tempMap.put("API_OTHER", "./template/ApiWallet-WST.txt");
		
		TEMPLATE_PATHS = Collections.unmodifiableMap(tempMap);
	}
	
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("請提供配置檔案路徑作為參數");
            System.err.println("使用方式: java WhiteLabelTool <configFilePath>");
            System.exit(1);
        }
        
        String configFilePath = args[0];
        try {
            ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            WhiteLabelConfig whiteLabelConfig = objectMapper.readValue(new File(configFilePath), WhiteLabelConfig.class);
			System.out.println(whiteLabelConfig.toString());
			whiteLabelConfig.validate();
			String webSitePageKey = "WEB_SITE_PAGE";
//            if (isValidInput(whiteLabel)) {
                generateSqlFile(whiteLabelConfig, true);
                generateSqlFile(whiteLabelConfig, false);
                if (!whiteLabelConfig.isSqlOnly()) {
                    if (!whiteLabelConfig.isApiWhiteLabel()) {
                        generateFromTemplate(whiteLabelConfig, "DOMAIN_TYPE", "../src/main/java/com/nv/commons/code/domain/", "DomainType.java");
//                        generateFromTemplate(whiteLabel, "COUNTRY_TYPE", "../src/main/java/com/nv/commons/code/domain/", "CountryType.java");
                    } else {
						webSitePageKey = "API_WALLET_WEB_SITE_PAGE";
					}
                    generateFromTemplate(whiteLabelConfig, webSitePageKey, "../src/main/java/com/nv/commons/website/page/", "WebSitePage.java");
					insertIntoJava(whiteLabelConfig);
				}
				//                generateOther(whiteLabel);
//            }
        } catch (IOException e) {
            System.err.println("處理 JSON 檔案時發生錯誤: " + e.getMessage());
        }
    }

    private static void generateSqlFile(WhiteLabelConfig whiteLabelConfig, boolean isDb01) {
        String key = isDb01 ? "DB_01" : "DB_41";
        if (whiteLabelConfig.isApiWhiteLabel()) {
            key = isDb01 ? "API_DB_01" : "API_DB_41";
        }
		String templateFile = TEMPLATE_PATHS.get(key);
        String suffix = isDb01 ? "-DB-01.sql" : "-DB-41.sql";
        String outputFileName = OUTPUT_PATH + PROJECT_PREFIX + whiteLabelConfig.getTicketNo() + suffix;

        Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		
		String content;
		String subContent = TemplateEngine.fillFile(templateFile, replacements);
		if (isDb01) {
			StringBuilder sb = new StringBuilder(subContent);
			sb.append("\n");
			// if sql is for DB01, need to check if new group or not
			if (Objects.nonNull(whiteLabelConfig.getApiWalletInfo()) && whiteLabelConfig.getApiWalletInfo().isNewGroup()) {
				sb.append(generateNewGroupSql(whiteLabelConfig, false));
				sb.append(generateNewGroupSql(whiteLabelConfig, true));
			} else if (whiteLabelConfig.isApiWhiteLabel()) {
				sb.append(generateUpdateGroupSql(whiteLabelConfig));
			}
			content = sb.toString();
		} else {
			content = subContent;
		}
		
		TemplateEngine.writeToFile(outputFileName, content);
    }
	
    private static void generateFromTemplate(WhiteLabelConfig whiteLabelConfig, String templateKey, String outputPath, String suffix) {
        String templateFile = TEMPLATE_PATHS.get(templateKey);
        String className = convertSnakeToCamel(whiteLabelConfig.getWebSiteName());
        String outputFileName = outputPath + className + suffix;

        Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		TemplateEngine.writeToFile(outputFileName, TemplateEngine.fillFile(templateFile, replacements));
    }
	
	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig) {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("{$webSiteName}", convertSnakeToCamel(whiteLabelConfig.getWebSiteName()).toUpperCase());
		replacements.put("{$webSiteValue}", whiteLabelConfig.getWebSiteValue().toString());
		replacements.put("{$className}", convertSnakeToCamel(whiteLabelConfig.getWebSiteName()));
		replacements.put("{$ticketNo}", whiteLabelConfig.getTicketNo());
		replacements.put("{$jiraSummary}", whiteLabelConfig.getJiraSummary());
		replacements.put("{$developer}", whiteLabelConfig.getDeveloper());
		if (StringUtils.isNotBlank(whiteLabelConfig.getHost())) {
			replacements.put("$enumName", whiteLabelConfig.getHost().replace(".", "_").toUpperCase());
		}
		replacements.put("{$lowerCase}", convertSnakeToCamel(whiteLabelConfig.getWebSiteName()).toLowerCase());
		if (whiteLabelConfig.isApiWhiteLabel()) {
			replacements.put("$group", whiteLabelConfig.getApiWalletInfo().getGroup());
			replacements.put("$cert", whiteLabelConfig.getApiWalletInfo().getCert());
		} else {
			replacements.put("$url", whiteLabelConfig.getHost());
		}
		return replacements;
	}
	
	private static void insertIntoJava(WhiteLabelConfig whiteLabelConfig) {
		String templateKey = whiteLabelConfig.isApiWhiteLabel() ? "API_OTHER" : "NEW_SITE_OTHER";
		String templateFile = TEMPLATE_PATHS.get(templateKey);
		String outputFileName = OUTPUT_PATH + PROJECT_PREFIX + whiteLabelConfig.getTicketNo() + "-other.txt";
		
		Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		String content = TemplateEngine.fillFile(templateFile, replacements);
		try {
			//insert into WebSiteType.java
			insertAtMarker(Paths.get("../src/main/java/com/nv/commons/code/WebSiteType.java"), "// insert New White Label", content, false);
			//insert into Setting.java
			String isEnable = whiteLabelConfig.isApiWhiteLabel() ? "false" : "true";
			String setting1 = TemplateEngine.fill(String.format(TS_FINANCIAL, isEnable), replacements);
			insertAtMarker(Paths.get("../src/main/java/com/nv/commons/model/Setting.java"), "// insert New White Label setting-1", setting1, false);
			String setting2 = TemplateEngine.fill(String.format(ELECTION_FANCY_BET, isEnable), replacements);
			insertAtMarker(Paths.get("../src/main/java/com/nv/commons/model/Setting.java"), "// insert New White Label setting-2", setting2, false);
			//insert into Const.js
			String constJs = TemplateEngine.fill(CONST_JS, replacements);
			insertAtMarker(Paths.get("../src/main/webapp/js/const/Const.js"), "// insert New White Label", constJs, false);
		} catch (java.lang.Exception e) {
			System.err.println("複寫java時發生錯誤: " + e.getMessage());
		}
	}
	
	/**
	 * 在 .java 檔中找到包含 keyword 的行，並依該行的縮排，在前或後插入 insertContent。
	 *
	 * @param javaFile      要修改的 .java 檔案
	 * @param keyword       要插入的位置依據（例如 "// INSERT HERE"）
	 * @param insertContent 多行插入內容，用 \n 分隔
	 * @param insertAfter   true=插入在該行之後，false=插入在該行之前
	 * @throws IOException 讀寫檔案錯誤
	 */
	private static void insertAtMarker(Path javaFile, String keyword, String insertContent, boolean insertAfter) throws IOException {
		List<String> result = new ArrayList<>();
		
		List<String> insertLinesRaw = Arrays.asList(insertContent.split("\\R"));  // 支援跨平台換行
		try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				
				// 擷取原始縮排
				String indent = getIndent(line);
				
				if (!insertAfter && line.contains(keyword)) {
					for (String insertLine : insertLinesRaw) {
						result.add(indent + insertLine);
					}
				}
				
				result.add(line);
				
				if (insertAfter && line.contains(keyword)) {
					for (String insertLine : insertLinesRaw) {
						result.add(indent + insertLine);
					}
				}
			}
		}
		String fileName = javaFile.toString().substring(javaFile.toString().lastIndexOf('/') + 1);
		System.out.println("✅ 文字已成功寫入至 " + fileName);
		Files.write(javaFile, result);
	}
	
	/**
	 * 擷取一行開頭的空白字元（縮排）
	 */
	private static String getIndent(String line) {
		int index = 0;
		while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
			index++;
		}
		return line.substring(0, index);
	}

    private static void generateOther(WhiteLabelConfig whiteLabelConfig) {
        String templateKey = whiteLabelConfig.isApiWhiteLabel() ? "API_OTHER" : "NEW_SITE_OTHER";
        String templateFile = TEMPLATE_PATHS.get(templateKey);
        String outputFileName = OUTPUT_PATH + PROJECT_PREFIX + whiteLabelConfig.getTicketNo() + "-other.txt";

        Map<String, String> replacements = buildReplacements(whiteLabelConfig);
        TemplateEngine.writeToFile(outputFileName, TemplateEngine.fillFile(templateFile, replacements));
    }

    private static boolean isValidInput(WhiteLabelConfig whiteLabelConfig) {
        if (whiteLabelConfig == null || whiteLabelConfig.getTicketNo() == null || whiteLabelConfig.getWebSiteName() == null || whiteLabelConfig.getWebSiteValue() == null) {
            throw new IllegalArgumentException("輸入資訊不完整");
        }
		if (whiteLabelConfig.isApiWhiteLabel() && Objects.isNull(whiteLabelConfig.getApiWalletInfo())) {
			throw new IllegalArgumentException("ApiWallet 資訊不完整");
		} else if (whiteLabelConfig.isApiWhiteLabel()) {
			if ((whiteLabelConfig.getApiWalletInfo().getCert() == null
				|| whiteLabelConfig.getApiWalletInfo().getGroup() == null)) {
				throw new IllegalArgumentException("ApiWallet,  Cert 或 Group 不能為空");
			}
		}
        return true;
    }

    private static String convertSnakeToCamel(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) return snakeCase;
        String[] words = snakeCase.split("_");
        StringBuilder camelCaseString = new StringBuilder();
        for (String word : words) {
            camelCaseString.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return camelCaseString.toString();
    }
	
	private static String generateUpdateGroupSql(WhiteLabelConfig whiteLabelConfig) {
		StringBuilder sb = new StringBuilder();
		// domain group
		
		sb.append("-- API 2.0 Group :");
		sb.append("\n");
		String domainingGroupSql = "UPDATE domaingroup SET groupsite = JSON_ARRAY_APPEND(groupsite, '$',  '{$webSiteValue}') "
			+ "WHERE GROUPNAME = '$group';";
		sb.append(domainingGroupSql);
		Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		return TemplateEngine.fill(sb.toString(), replacements);
	}
	
	private static String generateNewGroupSql(WhiteLabelConfig whiteLabelConfig, boolean isUat) {
		StringBuilder sb = new StringBuilder();
		// domain group
		sb.append("\n");
		ApiWalletInfo apiWalletInfo = whiteLabelConfig.getApiWalletInfo();
		GroupInfo groupInfo = apiWalletInfo.getGroupInfo();
		if (!isUat) {
			sb.append("-- API 2.0 Group : (新建)");
			sb.append("\n");
			String domainingGroupSql = "INSERT INTO domaingroup\n"
				+ "(groupname, privateipsetid, wwwgaipsetid, wwwcfipsetid, apiinfoipsetid, groupsite, updator, updatedate, issinglegroup)\n"
				+ "VALUES('%s', '%s', '%s', '%s', '%s', '[\"%s\"]', 'system', NOW(6), 1);";
			String addDomain = String.format(domainingGroupSql, apiWalletInfo.getGroup(), groupInfo.getPrivateIpSetId(), groupInfo.getBkIpSetId().get(0),
				groupInfo.getBkIpSetId().get(1), groupInfo.getApiInfoBkIpSetId(), whiteLabelConfig.getWebSiteValue());
			sb.append(addDomain);
			sb.append("\n");
			sb.append("\n");
		}
		
		if (isUat) {
			sb.append("-- UAT Only");
		} else {
			sb.append("-- add api domain name SIM");
		}
		sb.append("\n");
		// apidomainname
		String apiDomainSql = "INSERT INTO apidomainname\n"
			+ "  (id, groupname, name, isactive, priority, remark, apidomaintype, updatedate, createdate)\n"
			+ "VALUES ";
		sb.append(apiDomainSql);
		List<String> privateIpList = groupInfo.getPrivateIp();
		if (isUat) {
			privateIpList.addAll(UAT_PRIVATE_DOMAIN_LIST);
		}
		for (int i = 0; i < privateIpList.size(); i++) {
			int active = 1;
			if (isUat) {
				active = UAT_PRIVATE_DOMAIN_LIST.contains(privateIpList.get(i)) ? 1: 0;
			}
			String value = "\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 0, NOW(6), NOW(6)),";
			String temp = String.format(value, apiWalletInfo.getGroup(), privateIpList.get(i), active, i + 1);
			sb.append(temp);
		}
		List<String> backupList = new ArrayList<String>(groupInfo.getBackup());
		if (isUat) {
			backupList.addAll(UAT_PUBLIC_DOMAIN_LIST);
		}
		for (int i = 0; i < backupList.size(); i++) {
			int active = i >=2? 0: 1;
			if (isUat) {
				active = UAT_PUBLIC_DOMAIN_LIST.contains(backupList.get(i)) ? 1: 0;
			}
			String value = "\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, NOW(6), NOW(6))";
			String temp = String.format(value, apiWalletInfo.getGroup(), backupList.get(i), active, i + 1);
			sb.append(temp);
			if (i < (backupList.size() - 1)) {
				sb.append(",");
			} else {
				sb.append(";\n");
			}
		}
		return sb.toString();
	}
}

//class WhiteLabel {
//	private boolean sqlOnly;
//	private String ticketNo;
//	private Integer webSiteValue;
//	private String webSiteName;
//	private String cert;
//	private boolean apiWhiteLabel;
//	private boolean newGroup;
//	private String group;
//	private String url;
//	@JsonProperty("groupInfo")
//	private GroupInfo groupInfo;
//	// Getters and Setters
//	public String getTicketNo() { return ticketNo; }
//	public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
//
//	public Integer getWebSiteValue() { return webSiteValue; }
//	public void setWebSiteValue(Integer webSiteValue) { this.webSiteValue = webSiteValue; }
//
//	public String getWebSiteName() { return webSiteName; }
//	public void setWebSiteName(String webSiteName) { this.webSiteName = webSiteName; }
//
//	public String getCert() { return cert; }
//	public void setCert(String cert) { this.cert = cert; }
//
//	public boolean isApiWhiteLabel() { return apiWhiteLabel; }
//	public void setApiWhiteLabel(boolean apiWhiteLabel) { this.apiWhiteLabel = apiWhiteLabel; }
//
//	public boolean isNewGroup() { return newGroup; }
//	public void setNewGroup(boolean newGroup) { this.newGroup = newGroup; }
//
//	public String getGroup() { return group; }
//	public void setGroup(String group) { this.group = group; }
//
//	public String getUrl() { return url; }
//	public void setUrl(String url) { this.url = url; }
//
//	public boolean isSqlOnly() { return sqlOnly; }
//	public void setSqlOnly(boolean sqlOnly) { this.sqlOnly = sqlOnly; }
//
//	public GroupInfo getGroupInfo() {
//		return groupInfo;
//	}
//
//	public void setGroupInfo(GroupInfo groupInfo) {
//		this.groupInfo = groupInfo;
//	}
//
//	@java.lang.Override
//	public java.lang.String toString() {
//		return "WhiteLabel{" + "sqlOnly=" + sqlOnly + ", ticketNo='" + ticketNo + '\'' + ", webSiteValue=" + webSiteValue + ", webSiteName='"
//			+ webSiteName + '\'' + ", cert='" + cert + '\'' + ", apiWhiteLabel=" + apiWhiteLabel + ", newGroup=" + newGroup + ", group='" + group
//			+ '\'' + ", url='" + url + '\'' + ", groupInfo=" + groupInfo + '}';
//	}
//
//	static class GroupInfo {
//		private String privateIpSetId;
//		@JsonProperty("privateIp")
//		private List<String> privateIp;
//		@JsonProperty("bkIpSetId")
//		private List<String> bkIpSetId;
//		private String apiInfoBkIpSetId;
//		@JsonProperty("backup")
//		private List<String> backup;
//
//		public String getPrivateIpSetId() {
//			return privateIpSetId;
//		}
//
//		public void setPrivateIpSetId(String privateIpSetId) {
//			this.privateIpSetId = privateIpSetId;
//		}
//
//		public List<String> getPrivateIp() {
//			return privateIp;
//		}
//
//		public void setPrivateIp(List<String> privateIp) {
//			this.privateIp = privateIp;
//		}
//
//		public List<String> getBkIpSetId() {
//			return bkIpSetId;
//		}
//
//		public void setBkIpSetId(List<String> bkIpSetId) {
//			this.bkIpSetId = bkIpSetId;
//		}
//
//		public String getApiInfoBkIpSetId() {
//			return apiInfoBkIpSetId;
//		}
//
//		public void setApiInfoBkIpSetId(String apiInfoBkIpSetId) {
//			this.apiInfoBkIpSetId = apiInfoBkIpSetId;
//		}
//
//		public List<String> getBackup() {
//			return backup;
//		}
//
//		public void setBackup(List<String> backup) {
//			this.backup = backup;
//		}
//
//		@java.lang.Override
//		public java.lang.String toString() {
//			return "GroupInfo{" + "privateIpSetId='" + privateIpSetId + '\'' + ", privateIp=" + privateIp + ", bkIpSetId='" + bkIpSetId + '\''
//				+ ", apiInfoBkIpSetId='" + apiInfoBkIpSetId + '\'' + ", backup=" + backup + '}';
//		}
//
//	}
//}
