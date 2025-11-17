package tool.whiteLabel;

import constant.EnvEnumType;
import util.TemplateEngine;
import util.placeholder.PlaceholderMapper;
import util.placeholder.Transformers;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	/**
	 * 基础占位符映射缓存（不含环境相关参数）
	 * 用于避免重复计算相同的基础映射
	 */
	private static Map<String, String> baseReplacementsCache = null;

	/**
	 * 环境相关占位符映射缓存（按环境类型区分）
	 * Key: EnvEnumType (DEV/UAT/SIM)
	 * Value: 包含环境相关占位符的完整映射
	 */
	private static final Map<EnvEnumType, Map<String, String>> envReplacementsCache = new HashMap<>();
	private static final String CONST_JS = "WebSiteType.{$webSiteName} = {\n" + "\t\"value\": {$webSiteValue},\n" + "\t\"shortCode\": \"{$webSiteName}\",\n"
		+ "\t\"displayName\": \"{$webSiteName}\"\n" + "};\n";
	private static final String TS_FINANCIAL = "\n@HttpUpdate\n" + "public static boolean ENABLE_TS_FINANCIAL_{$webSiteName} = %s;\n\n";
	private static final String ELECTION_FANCY_BET = "\n@HttpUpdate\n" + "public static boolean ENABLE_ELECTION_FANCY_BET_{$webSiteName} = %s;\n\n";
	// apiDomainType 1: public, 0:private
	private static final List<String> UAT_PUBLIC_DOMAIN_LIST = Arrays.asList("qqkk77.net", "qqkk77.live");
	private static final List<String> UAT_PRIVATE_DOMAIN_LIST = Arrays.asList("cckk77.net", "cckk77.live");
	
	private static final Map<String, String> TEMPLATE_PATHS;
	private static final String DOMAIN_TYPE_PACKAGE = "../src/main/java/com/nv/commons/code/domain/";
	private static final String WEBSITE_PAGE_PACKAGE = "../src/main/java/com/nv/commons/website/page/";
	private static final String WEBSITE_TYPE_PATH = "../src/main/java/com/nv/commons/code/WebSiteType.java";
	static {
		Map<String, String> tempMap = new HashMap<>();
		tempMap.put("DB_01", "./template/NewSite-DB-01-template.txt");
		tempMap.put("DB_41", "./template/NewSite-DB-41-template.txt");
		tempMap.put("API_DB_01", "./template/ApiWallet-DB-01-template.txt");
		tempMap.put("API_DB_41", "./template/ApiWallet-DB-41-template.txt");
		tempMap.put("DOMAIN_TYPE", "./template/DomainTypeTemplate.txt");
		tempMap.put("WEB_SITE_PAGE", "./template/WebSitePageTemplate.txt");
		tempMap.put("API_WALLET_WEB_SITE_PAGE", "./template/ApiWalletWebSitePageTemplate.txt");
		tempMap.put("NEW_SITE_OTHER", "./template/NewSite-WST.txt");
		tempMap.put("API_OTHER", "./template/ApiWallet-WST.txt");
		tempMap.put("UPDATE_GROUP_SQL", "./template/UpdateGroup-SQL-template.txt");
		tempMap.put("NEW_GROUP_SQL_SIM", "./template/NewGroup-SQL-SIM-template.txt");
		tempMap.put("NEW_GROUP_SQL_UAT", "./template/NewGroup-SQL-UAT-template.txt");
		tempMap.put("NEW_GROUP_SQL_DEV", "./template/NewGroup-SQL-DEV-template.txt");

		TEMPLATE_PATHS = Collections.unmodifiableMap(tempMap);
	}
	
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("請提供配置檔案路徑作為參數");
            System.err.println("使用方式: java WhiteLabelTool <configFilePath>");
            System.exit(1);
        }

		// 清空缓存，确保每次运行都是干净的状态
		clearReplacementsCache();

        String configFilePath = args[0];
        try {
            ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            WhiteLabelConfig whiteLabelConfig = objectMapper.readValue(new File(configFilePath), WhiteLabelConfig.class);
			System.out.println(whiteLabelConfig.toString());
			whiteLabelConfig.validate();
			String webSitePageKey = "WEB_SITE_PAGE";
			generateSqlFile(whiteLabelConfig, true);
			generateSqlFile(whiteLabelConfig, false);
			if (!whiteLabelConfig.isSqlOnly()) {
				if (!whiteLabelConfig.isApiWhiteLabel()) {
					generateFromTemplate(whiteLabelConfig, "DOMAIN_TYPE", DOMAIN_TYPE_PACKAGE, "DomainType.java");
//					generateFromTemplate(whiteLabel, "COUNTRY_TYPE", "../src/main/java/com/nv/commons/code/domain/", "CountryType.java");
				} else {
					webSitePageKey = "API_WALLET_WEB_SITE_PAGE";
				}
				generateFromTemplate(whiteLabelConfig, webSitePageKey, WEBSITE_PAGE_PACKAGE, "WebSitePage.java");
				insertIntoJava(whiteLabelConfig);
			}
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
		for (EnvEnumType envEnumType : EnvEnumType.values()) {
			
			String suffix = "-" + envEnumType.name();
			suffix += isDb01 ? "-DB-01.sql": "-DB-41.sql";
			String outputFileName = OUTPUT_PATH + PROJECT_PREFIX + whiteLabelConfig.getTicketNo() + suffix;
			
			Map<String, String> replacements = buildReplacements(whiteLabelConfig, envEnumType);
			
			String content;
			String subContent = TemplateEngine.fillFile(templateFile, replacements);
			if (isDb01) {
				StringBuilder sb = new StringBuilder(subContent);
				sb.append("\n");
				// if sql is for DB01, need to check if new group or not
				if (Objects.nonNull(whiteLabelConfig.getApiWalletInfo()) && whiteLabelConfig.getApiWalletInfo().isNewGroup()) {
					sb.append(generateNewGroupSql(whiteLabelConfig, envEnumType));
				} else if (whiteLabelConfig.isApiWhiteLabel()) {
					sb.append(generateUpdateGroupSql(whiteLabelConfig));
				}
				content = sb.toString();
			} else {
				content = subContent;
			}
			
			TemplateEngine.writeToFile(outputFileName, content);
		}
    }
	
    private static void generateFromTemplate(WhiteLabelConfig whiteLabelConfig, String templateKey, String outputPath, String suffix) {
        String templateFile = TEMPLATE_PATHS.get(templateKey);
        String className = convertSnakeToCamel(whiteLabelConfig.getWebSiteName());
        String outputFileName = outputPath + className + suffix;

        Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		TemplateEngine.writeToFile(outputFileName, TemplateEngine.fillFile(templateFile, replacements));
    }
	
	/**
	 * 清空占位符映射缓存
	 * 在每次 main() 执行开始时调用，确保缓存一致性
	 */
	private static void clearReplacementsCache() {
		baseReplacementsCache = null;
		envReplacementsCache.clear();
	}

	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig) {
		return buildReplacements(whiteLabelConfig, null);
	}

	/**
	 * 构建基础占位符映射（不含环境相关参数）
	 * 此方法的结果会被缓存，避免重复计算
	 *
	 * @param whiteLabelConfig 白标配置
	 * @return 基础占位符映射
	 */
	private static Map<String, String> buildBaseReplacements(WhiteLabelConfig whiteLabelConfig) {
		return PlaceholderMapper.builder(whiteLabelConfig)
			.autoMap()  // 自动映射所有字段（包含动态字段）
			// 添加派生映射（需要转换的字段）
			.derived("{$webSiteName}", config -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(config.getWebSiteName()))
			.derived("{$className}", config -> Transformers.SNAKE_TO_CAMEL.transform(config.getWebSiteName()))
			.derived("{$lowerCase}", config -> Transformers.SNAKE_TO_CAMEL_LOWER.transform(config.getWebSiteName()))
			// 条件映射：仅在 host 不为空时添加
			.derivedIf("{$enumName}",
				config -> StringUtils.isNotBlank(config.getHost()),
				config -> Transformers.DOT_TO_UNDERSCORE_UPPER.transform(config.getHost()))
			.derivedIf("{$url}",
				config -> !config.isApiWhiteLabel() && StringUtils.isNotBlank(config.getHost()),
				WhiteLabelConfig::getHost)
			// API 白标相关映射
			.derivedIf("{$group}",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getGroup())
			.derivedIf("$cert",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getCert())
			.build();
	}

	/**
	 * 构建占位符映射（优化版本 - 使用缓存机制）
	 *
	 * @param whiteLabelConfig 白标配置
	 * @param envEnumType 环境类型（可选）
	 * @return 占位符映射
	 */
	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		// 1. 获取或创建基础映射缓存
		if (baseReplacementsCache == null) {
			baseReplacementsCache = buildBaseReplacements(whiteLabelConfig);
			System.out.println("✅ 基础占位符映射已缓存（" + baseReplacementsCache.size() + " 个）");
		}

		// 2. 如果没有环境参数，直接返回基础映射的副本
		if (envEnumType == null) {
			return new LinkedHashMap<>(baseReplacementsCache);
		}

		// 3. 检查环境映射缓存，如果不存在则创建
		return envReplacementsCache.computeIfAbsent(envEnumType, env -> {
			// 复制基础映射
			Map<String, String> replacements = new LinkedHashMap<>(baseReplacementsCache);

			// 添加环境相关的动态占位符（仅 2 个）
			if (StringUtils.isNotBlank(whiteLabelConfig.getHost())) {
				replacements.put("{$corsDomainValues}", getCorsDomainValue(whiteLabelConfig, env));
				replacements.put("{$enableFrontendBackendSeparationByDomainValues}",
					getEnableFrontendBackendSeparationByDomainValue(whiteLabelConfig, env));
			}

			System.out.println("✅ " + env.name() + " 环境占位符映射已缓存（" + replacements.size() + " 个）");
			return replacements;
		});
	}
	
	private static void insertIntoJava(WhiteLabelConfig whiteLabelConfig) {
		String templateKey = whiteLabelConfig.isApiWhiteLabel() ? "API_OTHER" : "NEW_SITE_OTHER";
		String templateFile = TEMPLATE_PATHS.get(templateKey);
		String outputFileName = OUTPUT_PATH + PROJECT_PREFIX + whiteLabelConfig.getTicketNo() + "-other.txt";
		List<String> requiredImports = new ArrayList<>();
		String className = convertSnakeToCamel(whiteLabelConfig.getWebSiteName());
		// 将路径转换为 import 语句
		requiredImports.add(convertPathToPackage(WEBSITE_PAGE_PACKAGE, className + "WebSitePage.java"));
		
		if (!whiteLabelConfig.isApiWhiteLabel()) {
			requiredImports.add(convertPathToPackage(DOMAIN_TYPE_PACKAGE, className + "DomainType.java"));
		}
		
		Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		String content = TemplateEngine.fillFile(templateFile, replacements);
		try {
			// === 示例：在插入代碼前，先插入需要的 import 語句 ===

			// 方式1: 直接傳入完整的 import 語句
			// insertImportStatement(
			//     Paths.get("../src/main/java/com/nv/commons/code/WebSiteType.java"),
			//     "import java.util.concurrent.ConcurrentHashMap;"
			// );

			// 方式2: 只傳入類別的完整路徑（方法會自動加上 import 和分號）
			// insertImportStatement(
			//     Paths.get("../src/main/java/com/nv/commons/model/Setting.java"),
			//     "com.nv.commons.util.DateHelper"
			// );

			// 方式3: 批量插入多個 import（如果需要）
			// Path settingJavaFile = Paths.get("../src/main/java/com/nv/commons/model/Setting.java");
			// insertImportStatement(settingJavaFile, "java.util.Arrays");
			// insertImportStatement(settingJavaFile, "java.util.Collections");
			// insertImportStatement(settingJavaFile, "com.nv.commons.annotation.HttpUpdate");

			//insert into WebSiteType.java
			insertAtMarker(Paths.get(WEBSITE_TYPE_PATH), "// insert New White Label", content, false);
			//insert import
			for (String importPath : requiredImports) {
				insertImportStatement(Paths.get(WEBSITE_TYPE_PATH), importPath);
			}
			
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
	 * 在 Java 檔案中智能插入 import 語句，自動排序並避免重複
	 * Import 排序規則：java.* -> javax.* -> org.* -> com.* -> 其他（各組內按字母順序）
	 *
	 * @param javaFile Java 檔案路徑
	 * @param importStatement 要插入的 import 語句（例如: "import java.util.List;" 或 "java.util.List"）
	 * @throws IOException 讀寫檔案錯誤
	 */
	private static void insertImportStatement(Path javaFile, String importStatement) throws IOException {
		// 標準化 import 語句格式
		String normalizedImport = importStatement.trim();
		if (!normalizedImport.startsWith("import ")) {
			normalizedImport = "import " + normalizedImport;
		}
		if (!normalizedImport.endsWith(";")) {
			normalizedImport = normalizedImport + ";";
		}

		List<String> lines = Files.readAllLines(javaFile);
		List<String> result = new ArrayList<>();

		// 找到 import 區域的開始和結束位置
		int firstImportIndex = -1;
		int lastImportIndex = -1;
		int packageIndex = -1;

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("package ")) {
				packageIndex = i;
			}
			if (line.startsWith("import ")) {
				if (firstImportIndex == -1) {
					firstImportIndex = i;
				}
				lastImportIndex = i;

				// 檢查是否已存在相同的 import
				if (line.equals(normalizedImport)) {
					System.out.println("⚠️  Import 已存在，跳過: " + normalizedImport);
					return;
				}
			}
		}

		// 如果沒有找到任何 import，在 package 語句後插入
		if (firstImportIndex == -1) {
			int insertPosition = packageIndex + 1;
			for (int i = 0; i < lines.size(); i++) {
				result.add(lines.get(i));
				if (i == insertPosition) {
					result.add("");
					result.add(normalizedImport);
				}
			}
		} else {
			// 找到正確的插入位置
			int insertPosition = findImportInsertPosition(lines, firstImportIndex, lastImportIndex, normalizedImport);

			for (int i = 0; i < lines.size(); i++) {
				if (i == insertPosition) {
					result.add(normalizedImport);
				}
				result.add(lines.get(i));
			}
		}

		String fileName = javaFile.getFileName().toString();
		System.out.println("✅ Import 已成功插入至 " + fileName + ": " + normalizedImport);
		Files.write(javaFile, result);
	}

	/**
	 * 根據 import 排序規則找到正確的插入位置
	 *
	 * @param lines 檔案所有行
	 * @param firstImportIndex 第一個 import 的索引
	 * @param lastImportIndex 最後一個 import 的索引
	 * @param newImport 要插入的 import 語句
	 * @return 應該插入的位置索引
	 */
	private static int findImportInsertPosition(List<String> lines, int firstImportIndex, int lastImportIndex, String newImport) {
		String newPackage = extractPackageFromImport(newImport);
		int importGroup = getImportGroup(newPackage);

		// 從第一個 import 開始尋找
		for (int i = firstImportIndex; i <= lastImportIndex; i++) {
			String currentLine = lines.get(i).trim();
			if (!currentLine.startsWith("import ")) {
				continue;
			}

			String currentPackage = extractPackageFromImport(currentLine);
			int currentGroup = getImportGroup(currentPackage);

			// 如果當前 import 屬於更後面的分組，或同組但字母順序在後，則插入在此行之前
			if (currentGroup > importGroup) {
				return i;
			} else if (currentGroup == importGroup && currentPackage.compareTo(newPackage) > 0) {
				return i;
			}
		}

		// 如果沒有找到合適的位置，插入在最後一個 import 之後
		return lastImportIndex + 1;
	}

	/**
	 * 從 import 語句中提取 package 名稱
	 *
	 * @param importStatement import 語句
	 * @return package 名稱
	 */
	private static String extractPackageFromImport(String importStatement) {
		String trimmed = importStatement.trim();
		trimmed = trimmed.replace("import ", "").replace(";", "").trim();
		return trimmed;
	}

	/**
	 * 根據 package 名稱判斷所屬的分組
	 * 0: java.*
	 * 1: javax.*
	 * 2: org.* (Apache, Spring 等第三方庫)
	 * 3: com.* (公司內部或其他第三方庫)
	 * 4: 其他
	 *
	 * @param packageName package 名稱
	 * @return 分組編號
	 */
	private static int getImportGroup(String packageName) {
		if (packageName.startsWith("java.")) {
			return 0;
		} else if (packageName.startsWith("javax.")) {
			return 1;
		} else if (packageName.startsWith("org.")) {
			return 2;
		} else if (packageName.startsWith("com.")) {
			return 3;
		} else {
			return 4;
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
		String templateFile = TEMPLATE_PATHS.get("UPDATE_GROUP_SQL");
		Map<String, String> replacements = buildReplacements(whiteLabelConfig);
		return TemplateEngine.fillFile(templateFile, replacements);
	}
	
	private static String generateNewGroupSql(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		boolean isUat = envEnumType == EnvEnumType.UAT;
		ApiWalletInfo apiWalletInfo = whiteLabelConfig.getApiWalletInfo();
		GroupInfo groupInfo = apiWalletInfo.getGroupInfo();

		// 选择模板
		String templateKey = "NEW_GROUP_SQL_" + envEnumType.name();
		String templateFile = TEMPLATE_PATHS.get(templateKey);

		// 构建基础替换
		Map<String, String> replacements = buildReplacements(whiteLabelConfig);

		replacements.put("{$privateIpSetId}", groupInfo.getPrivateIpSetId());
		replacements.put("{$wwwgaIpSetId}", groupInfo.getBkIpSetId().get(0));
		replacements.put("{$wwwcfIpSetId}", groupInfo.getBkIpSetId().get(1));
		replacements.put("{$apiInfoBkIpSetId}", groupInfo.getApiInfoBkIpSetId());

		// 生成 apidomainname VALUES 部分
		StringBuilder valuesSb = new StringBuilder();
		
		// 生成 corsdomain VALUES 部分
		StringBuilder corsDomainSb = new StringBuilder();
		
		// 生成 EnableFrontendBackendSeparationByDomain VALUES 部分
		StringBuilder enableFrontendBackendSeparationByDomainSb = new StringBuilder();

		// privateIpList
		List<String> privateIpList = new ArrayList<>(groupInfo.getPrivateIp());
		if (isUat) {
			privateIpList.addAll(UAT_PRIVATE_DOMAIN_LIST);
		}
		for (int i = 0; i < privateIpList.size(); i++) {
			int active = 1;
			if (isUat) {
				active = UAT_PRIVATE_DOMAIN_LIST.contains(privateIpList.get(i)) ? 1 : 0;
			}
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 0, NOW(6), NOW(6)),",
				apiWalletInfo.getGroup(), privateIpList.get(i), active, i + 1);
			valuesSb.append(value);
		}

		// backupList
		List<String> backupList = new ArrayList<>(groupInfo.getBackup());
		if (isUat) {
			backupList.addAll(UAT_PUBLIC_DOMAIN_LIST);
		}
		for (int i = 0; i < backupList.size(); i++) {
			int active = i >= 2 ? 0 : 1;
			if (isUat) {
				active = UAT_PUBLIC_DOMAIN_LIST.contains(backupList.get(i)) ? 1 : 0;
			}
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, NOW(6), NOW(6))",
				apiWalletInfo.getGroup(), backupList.get(i), active, i + 1);
			valuesSb.append(value);
			
			
			String subDomainStatic = envEnumType.getSubDomainStatic();
			String subDomainApi = envEnumType.getSubDomainApi();
			String corsDomainValue = String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
				backupList.get(i), subDomainStatic, subDomainApi);
			corsDomainSb.append(corsDomainValue);
			
			String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1",
				backupList.get(i));
			enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
			
			if (i < (backupList.size() - 1)) {
				valuesSb.append(",");
				corsDomainSb.append(",");
				enableFrontendBackendSeparationByDomainSb.append(",");
			} else {
				valuesSb.append(";");
				corsDomainSb.append(";");
			}
		}

		// 将 VALUES 添加到替换中
		replacements.put("{$apiDomainValues}", valuesSb.toString());
		replacements.put("{$corsDomainValues}", corsDomainSb.toString());
		replacements.put("{$enableFrontendBackendSeparationByDomainValues}", enableFrontendBackendSeparationByDomainSb.toString());

		// 使用模板填充
		return TemplateEngine.fillFile(templateFile, replacements);
	}
	
	private static String getCorsDomainValue(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		// 生成 corsdomain VALUES 部分
		StringBuilder corsDomainSb = new StringBuilder();
		
		String corsDomainValue = String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
			whiteLabelConfig.getHost(), envEnumType.getSubDomainStatic(), envEnumType.getSubDomainApi());
		corsDomainSb.append(corsDomainValue);
		
		return corsDomainSb.toString();
		
	}
	
	private static String getEnableFrontendBackendSeparationByDomainValue(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		// 生成 EnableFrontendBackendSeparationByDomain VALUES 部分
		StringBuilder enableFrontendBackendSeparationByDomainSb = new StringBuilder();
		
		String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1",
			whiteLabelConfig.getHost());
		enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
		
		return enableFrontendBackendSeparationByDomainSb.toString();
	}
	
	private static String convertPathToPackage(String path, String className) {
		// 标准化路径分隔符
		String normalized = path.replace("\\", "/");
		
		// 找到 "com" 的起始位置
		int comIndex = normalized.indexOf("com/");
		if (comIndex == -1) {
			comIndex = normalized.indexOf("com");
		}
		
		if (comIndex == -1) {
			throw new IllegalArgumentException("路径中未找到 'com': " + path);
		}
		
		// 提取从 com 开始的部分
		String packagePath = normalized.substring(comIndex);
		
		// 移除结尾的 "/"
		packagePath = packagePath.replaceAll("/+$", "");
		
		// 将 "/" 替换为 "."
		String packageName = packagePath.replace("/", ".");
		
		// 如果提供了类名，添加到包名后面
		if (className != null && !className.isEmpty()) {
			// 移除 .java 扩展名
			String classNameWithoutExt = className.replace(".java", "");
			packageName = packageName + "." + classNameWithoutExt;
		}
		
		return packageName;
	}
	
}

