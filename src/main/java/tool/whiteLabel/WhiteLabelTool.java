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

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class WhiteLabelTool {

	private static Map<String, String> baseReplacementsCache = null;
	private static final Map<EnvEnumType, Map<String, String>> envReplacementsCache = new HashMap<>();

	// apiDomainType 1: public, 0:private
	private static final List<String> UAT_PUBLIC_DOMAIN_LIST = Arrays.asList("qqkk77.net", "qqkk77.live", "ppkk77.net");
	private static final List<String> UAT_PRIVATE_DOMAIN_LIST = Arrays.asList("cckk77.net", "cckk77.live");

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Please provide config file path as argument");
			System.err.println("Usage: java WhiteLabelTool <configFilePath>");
			System.exit(1);
		}

		clearReplacementsCache();

		String configFilePath = args[0];
		try {
			ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
			WhiteLabelConfig whiteLabelConfig = objectMapper.readValue(new File(configFilePath), WhiteLabelConfig.class);
			System.out.println(whiteLabelConfig.toString());
			whiteLabelConfig.validate();
			processDynamicFiles(whiteLabelConfig);
		} catch (IOException e) {
			System.err.println("Error processing JSON file: " + e.getMessage());
		}
	}

	private static void clearReplacementsCache() {
		baseReplacementsCache = null;
		envReplacementsCache.clear();
	}

	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig) {
		return buildReplacements(whiteLabelConfig, null);
	}

	private static Map<String, String> buildBaseReplacements(WhiteLabelConfig whiteLabelConfig) {
		return PlaceholderMapper.builder(whiteLabelConfig)
			.autoMap()
			.derived("{$webSiteName}", config -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(config.getWebSiteName()))
			.derived("{$className}", config -> Transformers.SNAKE_TO_CAMEL.transform(config.getWebSiteName()))
			.derived("{$lowerCase}", config -> Transformers.SNAKE_TO_CAMEL_LOWER.transform(config.getWebSiteName()))
			.derivedIf("{$enumName}",
				config -> StringUtils.isNotBlank(config.getHost()),
				config -> Transformers.DOT_TO_UNDERSCORE_UPPER.transform(config.getHost()))
			.derivedIf("{$url}",
				config -> !config.isApiWhiteLabel() && StringUtils.isNotBlank(config.getHost()),
				WhiteLabelConfig::getHost)
			.derivedIf("{$group}",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getGroup())
			.derivedIf("{$cert}",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getCert())
			.build();
	}

	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		if (baseReplacementsCache == null) {
			baseReplacementsCache = buildBaseReplacements(whiteLabelConfig);
			System.out.println("✅ Base placeholder mappings cached (" + baseReplacementsCache.size() + " items)");
		}

		if (envEnumType == null) {
			return new LinkedHashMap<>(baseReplacementsCache);
		}

		return envReplacementsCache.computeIfAbsent(envEnumType, env -> {
			Map<String, String> replacements = new LinkedHashMap<>(baseReplacementsCache);

			if (StringUtils.isNotBlank(whiteLabelConfig.getHost())) {
				replacements.put("{$corsDomainValues}", getCorsDomainValue(whiteLabelConfig, env));
				replacements.put("{$enableFrontendBackendSeparationByDomainValues}",
					getEnableFrontendBackendSeparationByDomainValue(whiteLabelConfig, env));
			}

			System.out.println("✅ " + env.name() + " environment placeholder mappings cached (" + replacements.size() + " items)");
			return replacements;
		});
	}

	private static void processDynamicFiles(WhiteLabelConfig config) {
		Map<String, String> baseReplacements = buildReplacements(config);
		for (FileConfig fc : config.getFiles()) {
			try {
				if (fc.isNew()) {
					if (fc.getEnvironments() != null && !fc.getEnvironments().isEmpty()) {
						processNewFilePerEnv(config, fc);
					} else {
						processNewFile(fc, baseReplacements);
					}
				} else {
					processInsertFile(fc, baseReplacements);
				}
			} catch (Exception e) {
				System.err.println("❌ Error processing '" + fc.getName() + "': " + e.getMessage());
			}
		}
	}

	private static void processNewFilePerEnv(WhiteLabelConfig config, FileConfig fc) {
		for (String envName : fc.getEnvironments()) {
			try {
				EnvEnumType envEnumType = EnvEnumType.valueOf(envName);
				Map<String, String> replacements = buildReplacements(config, envEnumType);
				replacements.put("{$env}", envName);

				if (config.getApiWalletInfo() != null && config.getApiWalletInfo().isNewGroup()) {
					replacements.putAll(buildNewGroupSqlReplacements(config, envEnumType));
				}

				String resolvedTemplate = TemplateEngine.fill(fc.getTemplate(), replacements);
				String resolvedName = TemplateEngine.fill(fc.getName(), replacements);

				String location = fc.getLocation().endsWith("/") ? fc.getLocation() : fc.getLocation() + "/";
				Files.createDirectories(Paths.get(location));
				String outputPath = location + resolvedName;
				TemplateEngine.writeToFile(outputPath, TemplateEngine.fillFile(resolvedTemplate, replacements));
				System.out.println("✅ Created (" + envName + "): " + outputPath);
			} catch (Exception e) {
				System.err.println("❌ Error processing env " + envName + " for '" + fc.getName() + "': " + e.getMessage());
			}
		}
	}

	private static void processNewFile(FileConfig fc, Map<String, String> replacements) throws IOException {
		String resolvedName = TemplateEngine.fill(fc.getName(), replacements);
		String location = fc.getLocation().endsWith("/") ? fc.getLocation() : fc.getLocation() + "/";
		Files.createDirectories(Paths.get(location));
		String outputPath = location + resolvedName;
		TemplateEngine.writeToFile(outputPath, TemplateEngine.fillFile(fc.getTemplate(), replacements));
		System.out.println("✅ Created: " + outputPath);
	}

	private static void processInsertFile(FileConfig fc, Map<String, String> replacements) throws IOException {
		String content = TemplateEngine.fillFile(fc.getTemplate(), replacements);
		String marker = StringUtils.isNotBlank(fc.getMarker()) ? fc.getMarker() : "// insert New White Label";
		Path target = Paths.get(fc.getLocation());
		insertAtMarker(target, marker, content, fc.isInsertAfter());
		if (fc.getImports() != null) {
			for (String imp : fc.getImports()) {
				insertImportStatement(target, TemplateEngine.fill(imp, replacements));
			}
		}
	}

	private static Map<String, String> buildNewGroupSqlReplacements(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		boolean isUat = envEnumType == EnvEnumType.UAT;
		ApiWalletInfo apiWalletInfo = whiteLabelConfig.getApiWalletInfo();
		GroupInfo groupInfo = apiWalletInfo.getGroupInfo();

		Map<String, String> replacements = new LinkedHashMap<>();

		replacements.put("{$privateIpSetId}", groupInfo.getPrivateIpSetId());
		replacements.put("{$wwwgaIpSetId}", !groupInfo.getBkIpSetId().isEmpty() ? groupInfo.getBkIpSetId().get(0) : null);
		replacements.put("{$wwwcfIpSetId}", !groupInfo.getBkIpSetId().isEmpty() ? groupInfo.getBkIpSetId().get(1) : null);
		replacements.put("{$apiInfoBkIpSetId}", groupInfo.getApiInfoBkIpSetId());

		String subDomainStatic = envEnumType.getSubDomainStatic();
		String subDomainApi = envEnumType.getSubDomainApi();

		StringBuilder valuesSb = new StringBuilder();
		StringBuilder corsDomainSb = new StringBuilder();
		StringBuilder enableFrontendBackendSeparationByDomainSb = new StringBuilder();
		StringBuilder enableDesktopFrontendBackendSeparationByDomainSb = new StringBuilder();

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

			if (!UAT_PRIVATE_DOMAIN_LIST.contains(privateIpList.get(i))) {
				String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1", privateIpList.get(i));
				enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
				enableFrontendBackendSeparationByDomainSb.append(",");
			}
		}

		// backupList
		List<String> backupList = new ArrayList<>(groupInfo.getBackup());
		for (int i = 0; i < backupList.size(); i++) {
			if (i > 0) {
				valuesSb.append(",");
				corsDomainSb.append(",");
				enableFrontendBackendSeparationByDomainSb.append(",");
				enableDesktopFrontendBackendSeparationByDomainSb.append(",");
			}
			int active = i >= 2 ? 0 : 1;
			if (isUat) {
				active = 0;
			}
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, sysdate(6), sysdate(6))",
				apiWalletInfo.getGroup(), backupList.get(i), active, i + 1);
			valuesSb.append(value);

			String corsDomainValue = String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
				backupList.get(i), subDomainStatic, subDomainApi);
			corsDomainSb.append(corsDomainValue);

			String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1", backupList.get(i));
			enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
			enableDesktopFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
		}
		if (isUat) {
			for (int i = 0; i < UAT_PUBLIC_DOMAIN_LIST.size(); i++) {
				valuesSb.append(",");
				String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, sysdate(6), sysdate(6))",
					apiWalletInfo.getGroup(), UAT_PUBLIC_DOMAIN_LIST.get(i), 1, backupList.size() + i + 1);
				valuesSb.append(value);
			}
		}

		valuesSb.append(";");
		corsDomainSb.append(";");

		replacements.put("{$apiDomainValues}", valuesSb.toString());
		replacements.put("{$corsDomainValues}", corsDomainSb.toString());
		replacements.put("{$enableFrontendBackendSeparationByDomainValues}", enableFrontendBackendSeparationByDomainSb.toString());
		replacements.put("{$enableDesktopFrontendBackendSeparationByDomainValues}", enableDesktopFrontendBackendSeparationByDomainSb.toString());

		return replacements;
	}

	private static String getCorsDomainValue(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		return String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
			whiteLabelConfig.getHost(), envEnumType.getSubDomainStatic(), envEnumType.getSubDomainApi());
	}

	private static String getEnableFrontendBackendSeparationByDomainValue(WhiteLabelConfig whiteLabelConfig, EnvEnumType envEnumType) {
		return String.format("\n\t\t\"%s\": 1", whiteLabelConfig.getHost());
	}

	/**
	 * 在 Java 檔案中智能插入 import 語句，自動排序並避免重複
	 * Import 排序規則：java.* -> javax.* -> org.* -> com.* -> 其他（各組內按字母順序）
	 */
	private static void insertImportStatement(Path javaFile, String importStatement) throws IOException {
		String normalizedImport = importStatement.trim();
		if (!normalizedImport.startsWith("import ")) {
			normalizedImport = "import " + normalizedImport;
		}
		if (!normalizedImport.endsWith(";")) {
			normalizedImport = normalizedImport + ";";
		}

		List<String> lines = Files.readAllLines(javaFile);
		List<String> result = new ArrayList<>();

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

				if (line.equals(normalizedImport)) {
					System.out.println("⚠️  Import already exists, skipping: " + normalizedImport);
					return;
				}
			}
		}

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
			int insertPosition = findImportInsertPosition(lines, firstImportIndex, lastImportIndex, normalizedImport);
			for (int i = 0; i < lines.size(); i++) {
				if (i == insertPosition) {
					result.add(normalizedImport);
				}
				result.add(lines.get(i));
			}
		}

		String fileName = javaFile.getFileName().toString();
		System.out.println("✅ Import successfully inserted to " + fileName + ": " + normalizedImport);
		Files.write(javaFile, result);
	}

	private static int findImportInsertPosition(List<String> lines, int firstImportIndex, int lastImportIndex, String newImport) {
		String newPackage = extractPackageFromImport(newImport);
		int importGroup = getImportGroup(newPackage);

		for (int i = firstImportIndex; i <= lastImportIndex; i++) {
			String currentLine = lines.get(i).trim();
			if (!currentLine.startsWith("import ")) {
				continue;
			}

			String currentPackage = extractPackageFromImport(currentLine);
			int currentGroup = getImportGroup(currentPackage);

			if (currentGroup > importGroup) {
				return i;
			} else if (currentGroup == importGroup && currentPackage.compareTo(newPackage) > 0) {
				return i;
			}
		}

		return lastImportIndex + 1;
	}

	private static String extractPackageFromImport(String importStatement) {
		return importStatement.trim().replace("import ", "").replace(";", "").trim();
	}

	private static int getImportGroup(String packageName) {
		if (packageName.startsWith("java.")) return 0;
		else if (packageName.startsWith("javax.")) return 1;
		else if (packageName.startsWith("org.")) return 2;
		else if (packageName.startsWith("com.")) return 3;
		else return 4;
	}

	/**
	 * 在檔案中找到包含 keyword 的行，並依該行的縮排，在前或後插入 insertContent。
	 */
	private static void insertAtMarker(Path javaFile, String keyword, String insertContent, boolean insertAfter) throws IOException {
		List<String> result = new ArrayList<>();

		List<String> insertLinesRaw = Arrays.asList(insertContent.split("\\R"));
		try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
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
		System.out.println("✅ Content successfully written to " + fileName);
		Files.write(javaFile, result);
	}

	private static String getIndent(String line) {
		int index = 0;
		while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
			index++;
		}
		return line.substring(0, index);
	}
}
