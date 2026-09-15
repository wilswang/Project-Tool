package tool.whiteLabel;

import util.TemplateEngine;
import util.placeholder.PlaceholderMapper;
import util.placeholder.PlaceholderValidator;
import util.placeholder.Transformers;
import util.placeholder.UnresolvedPlaceholderException;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class WhiteLabelTool {

	private static Map<String, String> baseReplacementsCache = null;
	private static final Map<String, Map<String, String>> envReplacementsCache = new HashMap<>();

	private static EnvValuesResolver envValuesResolver = null;

	/** 任一檔案因未解析 placeholder 而失敗，整批視為失敗（讓 shell 的 $? 判斷擋下後續步驟） */
	private static boolean hasError = false;

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Please provide config file path as argument");
			System.err.println("Usage: java WhiteLabelTool <configFilePath> [envValuesFilePath]");
			System.exit(1);
		}

		clearReplacementsCache();

		String configFilePath = args[0];
		String envValuesPath = args.length > 1 ? args[1] : EnvValuesLoader.DEFAULT_PATH;
		try {
			ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
			WhiteLabelConfig whiteLabelConfig = objectMapper.readValue(new File(configFilePath), WhiteLabelConfig.class);
			System.out.println(whiteLabelConfig.toString());
			whiteLabelConfig.validate();
			// 設定檔格式錯誤在這裡就中止，不會產出任何檔案
			envValuesResolver = EnvValuesResolver.create(envValuesPath, whiteLabelConfig);
			requireExplicitEnvValuesForNewGroup(whiteLabelConfig, envValuesPath);
			processDynamicFiles(whiteLabelConfig);
		} catch (EnvValuesException e) {
			System.err.println("❌ 環境值設定錯誤: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Error processing JSON file: " + e.getMessage());
			System.exit(1);
		}

		if (hasError) {
			System.err.println("❌ 有檔案產生失敗，請檢查上方訊息；本次產出不可視為完成");
			System.exit(1);
		}
	}

	/**
	 * newGroup 的產出依賴設定檔提供的 extraPrivateDomains / extraPublicDomains / activateOwnDomains，
	 * 這三者沒有內建 fallback，缺了會產出「看起來合法但錯的」SQL 且不留任何 {$token} 痕跡。
	 * 所以這裡要求每個環境都有明確條目，並在產出任何檔案之前中止。
	 */
	private static void requireExplicitEnvValuesForNewGroup(WhiteLabelConfig config, String envValuesPath)
			throws EnvValuesException {
		if (config.getApiWalletInfo() == null || !config.getApiWalletInfo().isNewGroup()) {
			return;
		}

		Set<String> missing = new LinkedHashSet<>();
		for (FileConfig fc : config.getFiles()) {
			if (fc.getEnvironments() == null) {
				continue;
			}
			for (String envName : fc.getEnvironments()) {
				if (!envValuesResolver.hasExplicitEntry(envName)) {
					missing.add(envName);
				}
			}
		}

		if (!missing.isEmpty()) {
			throw new EnvValuesException("newGroup 的單子要求每個環境都要有明確的環境值設定，但以下環境找不到: "
				+ missing + "\n   請在 " + envValuesPath + " 或單號 JSON 的 envValues 補上這些環境。"
				+ "\n   原因: apidomainname 的額外 domain 與 isactive 規則只存在設定檔，"
				+ "沒有內建預設值可退回；缺了會產出看起來合法但錯誤的 SQL。");
		}
	}

	private static void clearReplacementsCache() {
		baseReplacementsCache = null;
		envReplacementsCache.clear();
		envValuesResolver = null;
		hasError = false;
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
			.derived("{$lowerCamelCase}", config -> Transformers.SNAKE_TO_LOWER_CAMEL.transform(config.getWebSiteName()))
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

	private static Map<String, String> buildReplacements(WhiteLabelConfig whiteLabelConfig, EnvValues envValues) {
		if (baseReplacementsCache == null) {
			baseReplacementsCache = buildBaseReplacements(whiteLabelConfig);
			System.out.println("✅ Base placeholder mappings cached (" + baseReplacementsCache.size() + " items)");
		}

		if (envValues == null) {
			return new LinkedHashMap<>(baseReplacementsCache);
		}

		return envReplacementsCache.computeIfAbsent(envValues.getEnvName().toUpperCase(), name -> {
			Map<String, String> replacements = new LinkedHashMap<>(baseReplacementsCache);

			if (StringUtils.isNotBlank(whiteLabelConfig.getHost())) {
				replacements.put("{$corsDomainValues}", getCorsDomainValue(whiteLabelConfig, envValues));
				replacements.put("{$enableFrontendBackendSeparationByDomainValues}",
					getEnableFrontendBackendSeparationByDomainValue(whiteLabelConfig));
			}

			System.out.println("✅ " + name + " environment placeholder mappings cached (" + replacements.size() + " items)");
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
			} catch (UnresolvedPlaceholderException e) {
				hasError = true;
				System.err.println("❌ " + e.getMessage());
			} catch (Exception e) {
				// 一定要設 hasError：以前這裡只印訊息，step 3 照樣 exit 0，
				// 於是 fillFile / insertAtMarker 丟出來的錯會被靜靜吃掉
				hasError = true;
				System.err.println("❌ Error processing '" + fc.getName() + "': " + e.getMessage());
			}
		}
	}

	private static void processNewFilePerEnv(WhiteLabelConfig config, FileConfig fc) {
		for (String envName : fc.getEnvironments()) {
			try {
				// 1. 解析環境值：單號 envValues -> 共用檔 -> EnvEnumType 內建值
				EnvValues envValues = envValuesResolver.resolve(envName);

				// 2. 防禦性複製：buildReplacements 回傳的是 envReplacementsCache 裡的同一份 map，
				//    直接 put 會污染快取，讓後面的檔案／環境讀到別人的值
				Map<String, String> replacements = new LinkedHashMap<>(buildReplacements(config, envValues));

				// 3. 環境值蓋在 base 之上（同名 key 以環境值為準）
				warnShadowedKeys(replacements, envValues);
				replacements.putAll(envValues.toPlaceholders());

				// 4. {$env} 是工具保留字，最後才寫入，確保不會被設定檔蓋掉
				replacements.put("{$env}", envName);

				// 5. 新群組 SQL 片段衍生自已解析的環境值，所以必須排在環境值之後
				if (config.getApiWalletInfo() != null && config.getApiWalletInfo().isNewGroup()) {
					replacements.putAll(NewGroupSqlBuilder.build(config, envValues));
				}

				String resolvedTemplate = TemplateEngine.fill(fc.getTemplate(), replacements);
				String resolvedName = TemplateEngine.fill(fc.getName(), replacements);
				PlaceholderValidator.requireFullyResolved("template 路徑 (" + fc.getTemplate() + ")", resolvedTemplate);
				PlaceholderValidator.requireFullyResolved("輸出檔名 (" + fc.getName() + ")", resolvedName);

				String location = fc.getLocation().endsWith("/") ? fc.getLocation() : fc.getLocation() + "/";
				Files.createDirectories(Paths.get(location));
				String outputPath = location + resolvedName;

				// 6. 先填值、驗證通過才寫檔，避免把 {$typo} 寫進 .sql
				String content = TemplateEngine.fillFile(resolvedTemplate, replacements);
				requireNonBlankContent(resolvedTemplate, outputPath, content);
				PlaceholderValidator.requireFullyResolved(outputPath, content);

				TemplateEngine.writeToFile(outputPath, content);
				System.out.println("✅ Created (" + envName + "): " + outputPath);
			} catch (UnresolvedPlaceholderException e) {
				hasError = true;
				System.err.println("❌ " + e.getMessage());
			} catch (UnknownEnvironmentException e) {
				// 環境名打錯（舊版是 EnvEnumType.valueOf 丟 IllegalArgumentException 後 exit 0）
				hasError = true;
				System.err.println("❌ " + e.getMessage());
			} catch (Exception e) {
				hasError = true;
				System.err.println("❌ Error processing env " + envName + " for '" + fc.getName() + "': " + e.getMessage());
			}
		}
	}

	/**
	 * 環境值蓋掉既有 base placeholder 且值不同時印警告 ——
	 * 抓「好心在 env-values.json 加了 developer 結果劫走 {$developer}」這種意外。
	 */
	private static void warnShadowedKeys(Map<String, String> baseReplacements, EnvValues envValues) {
		Set<String> shadowed = envValuesResolver.findShadowedKeys(baseReplacements, envValues);
		if (!shadowed.isEmpty()) {
			System.err.println("⚠️  環境 " + envValues.getEnvName() + " 的環境值覆蓋了既有 placeholder: " + shadowed);
		}
	}

	/**
	 * 白牌產出不存在「合法的空檔案」。模板讀得到但內容是空的（或只有空白）時，
	 * {@code PlaceholderValidator} 會放行 —— 空字串沒有任何 {@code {$token}} —— 所以要另外擋。
	 */
	private static void requireNonBlankContent(String templatePath, String outputPath, String content)
			throws IOException {
		if (content == null || content.trim().isEmpty()) {
			throw new IOException("模板 " + templatePath + " 填值後內容是空的，不寫出 " + outputPath
				+ "。請確認模板檔是否為空、或路徑是否指錯");
		}
	}

	private static void processNewFile(FileConfig fc, Map<String, String> replacements)
			throws IOException, UnresolvedPlaceholderException {
		String resolvedName = TemplateEngine.fill(fc.getName(), replacements);
		PlaceholderValidator.requireFullyResolved("輸出檔名 (" + fc.getName() + ")", resolvedName);

		String location = fc.getLocation().endsWith("/") ? fc.getLocation() : fc.getLocation() + "/";
		Files.createDirectories(Paths.get(location));
		String outputPath = location + resolvedName;

		String content = TemplateEngine.fillFile(fc.getTemplate(), replacements);
		requireNonBlankContent(fc.getTemplate(), outputPath, content);
		PlaceholderValidator.requireFullyResolved(outputPath, content);

		TemplateEngine.writeToFile(outputPath, content);
		System.out.println("✅ Created: " + outputPath);
	}

	private static void processInsertFile(FileConfig fc, Map<String, String> replacements)
			throws IOException, UnresolvedPlaceholderException, MarkerNotFoundException {
		String content = TemplateEngine.fillFile(fc.getTemplate(), replacements);
		requireNonBlankContent(fc.getTemplate(), fc.getLocation(), content);
		PlaceholderValidator.requireFullyResolved(fc.getLocation() + " <- " + fc.getTemplate(), content);

		String marker = StringUtils.isNotBlank(fc.getMarker()) ? fc.getMarker() : "// insert New White Label";
		Path target = Paths.get(fc.getLocation());
		insertAtMarker(target, marker, content, fc.isInsertAfter());
		if (fc.getImports() != null) {
			for (String imp : fc.getImports()) {
				String resolvedImport = TemplateEngine.fill(imp, replacements);
				PlaceholderValidator.requireFullyResolved("import (" + imp + ")", resolvedImport);
				insertImportStatement(target, resolvedImport);
			}
		}
	}

	private static String getCorsDomainValue(WhiteLabelConfig whiteLabelConfig, EnvValues envValues) {
		return String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
			whiteLabelConfig.getHost(), envValues.getSubDomainStatic(), envValues.getSubDomainApi());
	}

	private static String getEnableFrontendBackendSeparationByDomainValue(WhiteLabelConfig whiteLabelConfig) {
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
		Files.write(javaFile, result);
		System.out.println("✅ Import successfully inserted to " + fileName + ": " + normalizedImport);
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
	static void insertAtMarker(Path javaFile, String keyword, String insertContent, boolean insertAfter)
			throws IOException, MarkerNotFoundException {
		List<String> result = new ArrayList<>();
		int markerHits = 0;

		List<String> insertLinesRaw = Arrays.asList(insertContent.split("\\R"));
		try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String indent = getIndent(line);
				boolean isMarker = line.contains(keyword);
				if (isMarker) {
					markerHits++;
				}

				if (!insertAfter && isMarker) {
					for (String insertLine : insertLinesRaw) {
						result.add(indent + insertLine);
					}
				}

				result.add(line);

				if (insertAfter && isMarker) {
					for (String insertLine : insertLinesRaw) {
						result.add(indent + insertLine);
					}
				}
			}
		}

		// 找不到 marker 就中止，而且是在 Files.write 之前 —— 目標檔一個 byte 都不會動。
		// 以前這裡照樣印 ✅ 並原樣寫回，少掉的那段程式碼不編譯錯，只是執行期少一個站台
		if (markerHits == 0) {
			throw new MarkerNotFoundException("在 " + javaFile + " 找不到插入點 marker: \"" + keyword
				+ "\"。目標檔未被修改。請確認 marker 是否被重構掉，或設定檔的 marker 是否打錯字");
		}
		if (markerHits > 1) {
			System.err.println("⚠️  marker \"" + keyword + "\" 在 " + javaFile + " 出現 " + markerHits
				+ " 次，同一段內容會被插入 " + markerHits + " 次");
		}

		String fileName = javaFile.toString().substring(javaFile.toString().lastIndexOf('/') + 1);
		Files.write(javaFile, result);
		System.out.println("✅ Content successfully written to " + fileName);
	}

	private static String getIndent(String line) {
		int index = 0;
		while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
			index++;
		}
		return line.substring(0, index);
	}
}
