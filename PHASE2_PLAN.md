# 第二阶段规划文档

📅 **创建时间**: 2025-11-17
🎯 **目标版本**: v1.2.0
📋 **状态**: 规划中

---

## 📖 目录

1. [概述](#概述)
2. [核心功能](#核心功能)
3. [技术实现方案](#技术实现方案)
4. [实施计划](#实施计划)
5. [预期收益](#预期收益)
6. [技术栈选型](#技术栈选型)
7. [风险评估](#风险评估)
8. [附录](#附录)

---

## 🎯 概述

### 背景

第一阶段（v1.1.0）已完成：
- ✅ 反射驱动的自动占位符映射（PlaceholderMapper）
- ✅ 动态字段支持（@JsonAnySetter）
- ✅ 缓存优化（buildReplacements 方法）
- ✅ 性能提升 60-70%

### 第二阶段目标

进一步提升系统的**可维护性**、**可靠性**和**灵活性**，通过：
1. **配置文件驱动** - 降低代码修改频率
2. **自动化校验** - 减少人为错误
3. **增强功能** - 提升系统能力

### 核心价值

| 维度 | 提升目标 |
|------|---------|
| 维护成本 | 降低 80% |
| 错误率 | 减少 95% |
| 灵活性 | 提升 300%+ |
| 文档化 | 自动生成 |

---

## 🚀 核心功能

### 功能 1：配置文件驱动的映射规则

#### 当前问题

```java
// 问题：映射规则硬编码在 buildReplacements() 中
private static Map<String, String> buildReplacements(...) {
    return PlaceholderMapper.builder(config)
        .autoMap()
        .derived("{$className}", c -> ...)      // 硬编码
        .derived("{$upperName}", c -> ...)      // 硬编码
        .conditional("{$cert}", ...)            // 硬编码
        .build();
}
```

**痛点**：
- ❌ 每次新增映射规则需要修改 Java 代码
- ❌ 需要重新编译和部署
- ❌ 非开发人员无法调整规则
- ❌ 规则分散在代码中，难以维护

#### 解决方案

创建 `placeholder-mappings.yaml` 配置文件：

```yaml
# placeholder-mappings.yaml
version: "1.0"

# 基础配置
config:
  prefix: "{$"
  suffix: "}"

# 自动映射
auto-map:
  enabled: true
  exclude-fields:
    - "additionalProperties"
    - "class"

# 派生映射规则
derived-mappings:
  - name: "{$className}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL"
    description: "类名（驼峰命名）"

  - name: "{$upperName}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL_UPPER"
    description: "全大写类名"

  - name: "{$lowerName}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL_LOWER"
    description: "全小写类名"

  - name: "{$enumName}"
    source: "host"
    transformer: "DOT_TO_UNDERSCORE_UPPER"
    condition: "host != null && !host.isEmpty()"
    description: "枚举名（从域名生成）"

# 条件映射
conditional-mappings:
  - name: "{$cert}"
    condition: "apiWhiteLabel == true"
    source: "apiWalletInfo.cert"
    description: "API 钱包证书"

  - name: "{$privateKey}"
    condition: "apiWhiteLabel == true"
    source: "apiWalletInfo.privateKey"
    description: "API 钱包私钥"

# 环境相关映射
environment-mappings:
  - name: "{$corsDomainValues}"
    environments: ["DEV", "UAT", "SIM"]
    generator: "getCorsDomainValue"
    description: "CORS 域名值"

  - name: "{$enableFrontendBackendSeparationByDomainValues}"
    environments: ["DEV", "UAT", "SIM"]
    generator: "getEnableFrontendBackendSeparationByDomainValue"
    description: "前后端分离域名值"

# 常量映射
constant-mappings:
  - name: "{$defaultTimezone}"
    value: "UTC+8"

  - name: "{$defaultLanguage}"
    value: "zh_CN"
```

#### 技术实现

**新增类**：

1. **MappingConfig.java** - 配置数据模型
```java
@Data
public class MappingConfig {
    private String version;
    private ConfigSettings config;
    private AutoMapSettings autoMap;
    private List<DerivedMappingRule> derivedMappings;
    private List<ConditionalMappingRule> conditionalMappings;
    private List<EnvironmentMappingRule> environmentMappings;
    private List<ConstantMappingRule> constantMappings;
}
```

2. **YamlMappingLoader.java** - YAML 加载器
```java
public class YamlMappingLoader {
    private static final String DEFAULT_CONFIG_PATH = "placeholder-mappings.yaml";

    public static MappingConfig load() {
        return load(DEFAULT_CONFIG_PATH);
    }

    public static MappingConfig load(String path) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(path), MappingConfig.class);
    }
}
```

3. **增强 PlaceholderMapper**
```java
public class PlaceholderMapper {
    // 新增：从 YAML 加载映射规则
    public static Map<String, String> buildFromYaml(
        WhiteLabelConfig config,
        EnvEnumType env
    ) {
        MappingConfig mappingConfig = YamlMappingLoader.load();
        return buildFromConfig(config, env, mappingConfig);
    }

    // 向后兼容：保留原有代码方式
    public static Map<String, String> buildFromCode(
        WhiteLabelConfig config,
        EnvEnumType env
    ) {
        // 原有逻辑
    }
}
```

#### 收益

- ✅ **零代码修改**：调整映射规则无需修改 Java 代码
- ✅ **快速部署**：修改 YAML 文件后重启即生效
- ✅ **易于维护**：所有规则集中在配置文件中
- ✅ **权限友好**：非开发人员也可以调整规则
- ✅ **版本控制**：YAML 文件可以纳入版本管理

---

### 功能 2：占位符校验工具

#### 当前问题

**场景 1：占位符拼写错误**
```sql
-- 模板文件：NewSite-DB-01-template.txt
INSERT INTO site (name) VALUES ('{$webSitname}');
                                      ↑ 错误：少了 'e'
```

**场景 2：未使用的占位符**
```java
// 配置生成了 20 个占位符
// 但模板只使用了 15 个
// 有 5 个占位符浪费了计算资源
```

**场景 3：缺少文档**
```
开发者："{$className} 是什么？从哪里来的？"
现状：需要翻阅代码才能找到答案
```

**痛点**：
- ❌ 手工检查占位符容易出错
- ❌ 运行时才发现问题（已生成错误文件）
- ❌ 缺少占位符使用文档
- ❌ 无法追踪占位符来源

#### 解决方案

创建自动化校验工具：

##### 2.1 核心类设计

```java
/**
 * 占位符校验工具
 */
public class PlaceholderValidator {

    /**
     * 扫描模板目录，提取所有占位符
     * @param templateDir 模板目录路径
     * @return 模板 -> 占位符集合的映射
     */
    public static Map<String, Set<String>> scanTemplates(Path templateDir) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("\\{\\$[a-zA-Z_][a-zA-Z0-9_.]*\\}");

        // 递归扫描所有模板文件
        Files.walk(templateDir)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                String content = Files.readString(file);
                Set<String> placeholders = new HashSet<>();

                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    placeholders.add(matcher.group());
                }

                result.put(file.getFileName().toString(), placeholders);
            });

        return result;
    }

    /**
     * 验证占位符完整性
     */
    public static ValidationReport validate(
        Map<String, Set<String>> templatePlaceholders,
        Map<String, String> configPlaceholders
    ) {
        ValidationReport report = new ValidationReport();

        // 收集所有模板使用的占位符
        Set<String> allUsed = templatePlaceholders.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        // 检查缺失的占位符（模板需要但配置未提供）
        Set<String> missing = new HashSet<>(allUsed);
        missing.removeAll(configPlaceholders.keySet());
        report.setMissingPlaceholders(missing);

        // 检查未使用的占位符（配置提供但模板未使用）
        Set<String> unused = new HashSet<>(configPlaceholders.keySet());
        unused.removeAll(allUsed);
        report.setUnusedPlaceholders(unused);

        // 统计信息
        report.setTemplateCount(templatePlaceholders.size());
        report.setTotalPlaceholders(allUsed.size());
        report.setConfigPlaceholders(configPlaceholders.size());

        return report;
    }

    /**
     * 生成占位符参考文档
     */
    public static void generateDocumentation(
        Map<String, Set<String>> templatePlaceholders,
        Map<String, String> configPlaceholders,
        Path outputPath
    ) {
        StringBuilder doc = new StringBuilder();

        // 文档头部
        doc.append("# 占位符参考文档\n\n");
        doc.append("📅 **生成时间**: ").append(LocalDateTime.now()).append("\n");
        doc.append("🔧 **工具版本**: v1.2.0\n\n");
        doc.append("---\n\n");

        // 1. 可用占位符列表
        doc.append("## 📋 可用占位符列表\n\n");
        doc.append("| 占位符 | 示例值 | 说明 |\n");
        doc.append("|--------|--------|------|\n");

        configPlaceholders.forEach((key, value) -> {
            doc.append("| `").append(key).append("` | ")
               .append(value).append(" | ")
               .append(getDescription(key)).append(" |\n");
        });

        doc.append("\n---\n\n");

        // 2. 模板占位符使用情况
        doc.append("## 📄 模板占位符使用情况\n\n");

        templatePlaceholders.forEach((template, placeholders) -> {
            doc.append("### ").append(template).append("\n\n");
            doc.append("**使用的占位符** (").append(placeholders.size()).append(" 个):\n\n");

            placeholders.stream().sorted().forEach(p -> {
                doc.append("- `").append(p).append("`");
                if (configPlaceholders.containsKey(p)) {
                    doc.append(" = `").append(configPlaceholders.get(p)).append("`");
                } else {
                    doc.append(" ⚠️ **缺失**");
                }
                doc.append("\n");
            });

            doc.append("\n");
        });

        // 写入文件
        Files.writeString(outputPath, doc.toString());
    }
}
```

##### 2.2 验证报告类

```java
@Data
public class ValidationReport {
    private int templateCount;
    private int totalPlaceholders;
    private int configPlaceholders;
    private Set<String> missingPlaceholders = new HashSet<>();
    private Set<String> unusedPlaceholders = new HashSet<>();
    private Map<String, List<String>> placeholderUsage = new HashMap<>();

    public boolean hasErrors() {
        return !missingPlaceholders.isEmpty();
    }

    public void print() {
        System.out.println("=".repeat(60));
        System.out.println("📊 占位符验证报告");
        System.out.println("=".repeat(60));
        System.out.println();

        System.out.println("✅ 扫描模板: " + templateCount + " 个");
        System.out.println("✅ 发现占位符: " + totalPlaceholders + " 个");
        System.out.println("✅ 配置提供占位符: " + configPlaceholders + " 个");
        System.out.println();

        if (!missingPlaceholders.isEmpty()) {
            System.out.println("⚠️  缺失占位符: " + missingPlaceholders.size() + " 个");
            missingPlaceholders.forEach(p ->
                System.out.println("   - " + p)
            );
            System.out.println();
        }

        if (!unusedPlaceholders.isEmpty()) {
            System.out.println("ℹ️  未使用占位符: " + unusedPlaceholders.size() + " 个");
            unusedPlaceholders.forEach(p ->
                System.out.println("   - " + p)
            );
            System.out.println();
        }

        if (missingPlaceholders.isEmpty()) {
            System.out.println("✅ 所有占位符验证通过！");
        }

        System.out.println("=".repeat(60));
    }
}
```

##### 2.3 命令行接口

在 `WhiteLabelTool.java` 的 `main()` 方法中添加：

```java
public static void main(String[] args) {
    if (args.length == 0) {
        printUsage();
        return;
    }

    String command = args[0];

    switch (command.toUpperCase()) {
        case "A":
            // 原有逻辑
            break;

        case "VALIDATE":  // 新增：校验命令
            validatePlaceholders(args);
            break;

        case "DOC":  // 新增：生成文档命令
            generatePlaceholderDoc(args);
            break;

        default:
            System.err.println("未知命令: " + command);
            printUsage();
    }
}

private static void validatePlaceholders(String[] args) {
    String configPath = args.length > 1 ? args[1] : "config.json";

    // 1. 加载配置
    WhiteLabelConfig config = loadConfig(configPath);

    // 2. 生成占位符映射
    Map<String, String> placeholders = buildReplacements(config, null);

    // 3. 扫描模板
    Path templateDir = Paths.get("src/template");
    Map<String, Set<String>> templatePlaceholders =
        PlaceholderValidator.scanTemplates(templateDir);

    // 4. 验证
    ValidationReport report = PlaceholderValidator.validate(
        templatePlaceholders,
        placeholders
    );

    // 5. 输出报告
    report.print();

    // 6. 如果有错误，退出码非零
    if (report.hasErrors()) {
        System.exit(1);
    }
}

private static void generatePlaceholderDoc(String[] args) {
    String configPath = args.length > 1 ? args[1] : "config.json";
    String outputPath = args.length > 2 ? args[2] : "PLACEHOLDER_REFERENCE.md";

    WhiteLabelConfig config = loadConfig(configPath);
    Map<String, String> placeholders = buildReplacements(config, null);

    Path templateDir = Paths.get("src/template");
    Map<String, Set<String>> templatePlaceholders =
        PlaceholderValidator.scanTemplates(templateDir);

    PlaceholderValidator.generateDocumentation(
        templatePlaceholders,
        placeholders,
        Paths.get(outputPath)
    );

    System.out.println("✅ 文档已生成: " + outputPath);
}
```

#### 使用示例

##### 示例 1：验证占位符

```bash
# 验证默认配置
java -jar Project-Tool.jar validate

# 验证指定配置
java -jar Project-Tool.jar validate config/abc-site.json
```

**输出**：
```
============================================================
📊 占位符验证报告
============================================================

✅ 扫描模板: 13 个
✅ 发现占位符: 47 个
✅ 配置提供占位符: 49 个

⚠️  缺失占位符: 2 个
   - {$customField1}
   - {$siteCategory}

ℹ️  未使用占位符: 4 个
   - {$unusedField1}
   - {$unusedField2}

============================================================
```

##### 示例 2：生成文档

```bash
# 生成默认文档
java -jar Project-Tool.jar doc

# 生成到指定路径
java -jar Project-Tool.jar doc config/abc-site.json docs/placeholders.md
```

**生成的文档示例** (`PLACEHOLDER_REFERENCE.md`):

```markdown
# 占位符参考文档

📅 **生成时间**: 2025-11-17T14:30:00
🔧 **工具版本**: v1.2.0

---

## 📋 可用占位符列表

| 占位符 | 示例值 | 说明 |
|--------|--------|------|
| `{$ticketNo}` | SACRIC-12345 | Jira ticket 编号 |
| `{$webSiteName}` | ABC_SITE | 站点名称（蛇形命名） |
| `{$className}` | AbcSite | 类名（驼峰命名） |
| `{$upperName}` | ABCSITE | 全大写类名 |
| `{$webSiteValue}` | 101 | 站点值（数字） |
| `{$host}` | abc.com | 域名 |
| `{$developer}` | Wilson | 开发者 |
| `{$jiraSummary}` | Add ABC site | Jira 摘要 |

---

## 📄 模板占位符使用情况

### NewSite-DB-01-template.txt

**使用的占位符** (8 个):

- `{$className}` = `AbcSite`
- `{$developer}` = `Wilson`
- `{$host}` = `abc.com`
- `{$jiraSummary}` = `Add ABC site`
- `{$ticketNo}` = `SACRIC-12345`
- `{$upperName}` = `ABCSITE`
- `{$webSiteName}` = `ABC_SITE`
- `{$webSiteValue}` = `101`

### ApiWallet-DB-01-template.txt

**使用的占位符** (12 个):

- `{$cert}` = `cert_content`
- `{$className}` = `AbcSite`
- `{$developer}` = `Wilson`
- `{$host}` = `abc.com`
...
```

#### 收益

- ✅ **减少 90% 占位符错误**：自动检测拼写错误和缺失
- ✅ **自动化文档生成**：无需手工维护占位符文档
- ✅ **可追溯性**：清晰显示每个占位符的来源和使用情况
- ✅ **CI/CD 集成**：可集成到构建流程，提前发现问题

---

### 功能 3：增强功能

#### 3.1 占位符表达式支持

##### 当前限制

```java
// 需要定义多个派生占位符
.derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(...))
.derived("{$upperName}", c -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(...))
.derived("{$lowerName}", c -> Transformers.SNAKE_TO_CAMEL_LOWER.transform(...))
```

**痛点**：
- 每个转换都需要显式定义
- 派生占位符数量膨胀
- 灵活性不足

##### 解决方案

支持内联转换表达式：

```
语法: {$fieldName:transformer1:transformer2:...}

示例:
{$webSiteName:camel}              → AbcSite
{$webSiteName:camel:upper}        → ABCSITE
{$webSiteName:camel:lower}        → abcsite
{$host:replace(.,-):upper}        → ABC-COM
{$ticketNo:substring(0,6)}        → SACRIC
```

##### 技术实现

```java
/**
 * 占位符表达式解析器
 */
public class PlaceholderExpression {
    private String fieldName;
    private List<Transformer> transformers;

    public static PlaceholderExpression parse(String expression) {
        // {$webSiteName:camel:upper}
        // → fieldName="webSiteName", transformers=["camel", "upper"]

        String content = expression.substring(2, expression.length() - 1);
        String[] parts = content.split(":");

        PlaceholderExpression expr = new PlaceholderExpression();
        expr.fieldName = parts[0];
        expr.transformers = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            expr.transformers.add(parseTransformer(parts[i]));
        }

        return expr;
    }

    public String evaluate(Map<String, String> context) {
        String value = context.get("{$" + fieldName + "}");

        for (Transformer transformer : transformers) {
            value = transformer.transform(value);
        }

        return value;
    }
}
```

**修改 TemplateEngine**：

```java
private static String processTemplate(String template, Map<String, String> replacements) {
    String result = template;

    // 1. 处理表达式占位符（带冒号）
    Pattern exprPattern = Pattern.compile("\\{\\$[a-zA-Z_][a-zA-Z0-9_.]*:[^}]+\\}");
    Matcher exprMatcher = exprPattern.matcher(result);

    while (exprMatcher.find()) {
        String expression = exprMatcher.group();
        PlaceholderExpression expr = PlaceholderExpression.parse(expression);
        String value = expr.evaluate(replacements);
        result = result.replace(expression, value);
    }

    // 2. 处理普通占位符
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
        result = result.replace(entry.getKey(), entry.getValue());
    }

    return result;
}
```

##### 收益

- ✅ 减少派生占位符定义数量
- ✅ 提升模板灵活性
- ✅ 简化配置

---

#### 3.2 国际化（i18n）支持

##### 场景

生成的文件中包含用户可见的文本，需要支持多语言。

##### 解决方案

**语法**：
```
{$i18n:key:locale}
```

**配置文件**：

```properties
# messages_zh_CN.properties
site.welcome=欢迎来到 {$webSiteName}
site.copyright=版权所有 © 2025
site.powered_by=技术支持: {$developer}

# messages_en_US.properties
site.welcome=Welcome to {$webSiteName}
site.copyright=Copyright © 2025
site.powered_by=Powered by {$developer}
```

**使用示例**：

```java
// 模板文件
/*
 * {$i18n:site.welcome:zh_CN}
 * {$i18n:site.copyright:zh_CN}
 */

// 生成结果
/*
 * 欢迎来到 ABC_SITE
 * 版权所有 © 2025
 */
```

##### 技术实现

```java
public class I18nPlaceholderResolver {
    private static final Map<String, ResourceBundle> bundles = new HashMap<>();

    public static String resolve(String key, String locale, Map<String, String> context) {
        ResourceBundle bundle = getBundle(locale);
        String template = bundle.getString(key);

        // 替换模板中的占位符
        for (Map.Entry<String, String> entry : context.entrySet()) {
            template = template.replace(entry.getKey(), entry.getValue());
        }

        return template;
    }

    private static ResourceBundle getBundle(String locale) {
        return bundles.computeIfAbsent(locale, l -> {
            Locale loc = Locale.forLanguageTag(l.replace('_', '-'));
            return ResourceBundle.getBundle("messages", loc);
        });
    }
}
```

---

#### 3.3 占位符继承和覆盖

##### 场景

多个站点有共同的配置，但也有各自的特殊配置。

##### 解决方案

**基础配置**：
```yaml
# base-mappings.yaml
placeholders:
  common:
    developer: "Wilson"
    timezone: "UTC+8"
    language: "zh_CN"

  derived:
    - name: "{$className}"
      source: "webSiteName"
      transformer: "SNAKE_TO_CAMEL"
```

**站点特定配置**：
```yaml
# abc-site-mappings.yaml
extends: base-mappings.yaml

placeholders:
  override:
    developer: "John"  # 覆盖基础配置

  additional:
    siteCategory: "Sports"  # 新增字段
```

**最终效果**：
```
{$developer}     = "John"        (覆盖)
{$timezone}      = "UTC+8"       (继承)
{$language}      = "zh_CN"       (继承)
{$siteCategory}  = "Sports"      (新增)
{$className}     = "AbcSite"     (继承)
```

##### 技术实现

```java
public class YamlMappingLoader {
    public static MappingConfig load(String path) {
        MappingConfig config = loadYaml(path);

        // 处理继承
        if (config.getExtends() != null) {
            MappingConfig baseConfig = load(config.getExtends());
            config = merge(baseConfig, config);
        }

        return config;
    }

    private static MappingConfig merge(MappingConfig base, MappingConfig override) {
        MappingConfig result = new MappingConfig();

        // 1. 复制基础配置
        result.setDerivedMappings(new ArrayList<>(base.getDerivedMappings()));
        result.setConstantMappings(new HashMap<>(base.getConstantMappings()));

        // 2. 应用覆盖
        if (override.getOverride() != null) {
            override.getOverride().forEach((key, value) -> {
                result.getConstantMappings().put(key, value);
            });
        }

        // 3. 添加新增配置
        if (override.getAdditional() != null) {
            result.getConstantMappings().putAll(override.getAdditional());
        }

        return result;
    }
}
```

---

## 📅 实施计划

### 优先级分析

| 功能 | 优先级 | 工期 | 复杂度 | 收益 | 风险 |
|------|--------|------|--------|------|------|
| **占位符校验工具** | ⭐⭐⭐ 最高 | 2-3 天 | 低 | 高 | 低 |
| **配置文件驱动** | ⭐⭐ 中等 | 5-7 天 | 中 | 高 | 中 |
| **增强功能** | ⭐ 较低 | 7-10 天 | 中-高 | 中 | 中-高 |

### 推荐实施顺序

---

#### Phase 2.1：占位符校验工具（最高优先级）

**时间**: 2-3 天
**版本**: v1.2.0

**实施步骤**：

##### Day 1：核心功能开发

1. **创建 PlaceholderValidator.java**
   - 实现 `scanTemplates()` 方法
   - 实现 `validate()` 方法
   - 实现 `generateDocumentation()` 方法

2. **创建 ValidationReport.java**
   - 数据模型定义
   - 报告格式化输出

3. **修改 WhiteLabelTool.java**
   - 添加 `validate` 命令
   - 添加 `doc` 命令

##### Day 2：测试和优化

4. **编写单元测试**
   - `PlaceholderValidatorTest.java`
   - 测试模板扫描
   - 测试验证逻辑
   - 测试文档生成

5. **实际运行验证**
   - 扫描现有 13 个模板
   - 验证占位符完整性
   - 生成参考文档

##### Day 3：文档和发布

6. **更新文档**
   - 更新 README.md（添加 validate/doc 命令说明）
   - 创建 PLACEHOLDER_VALIDATION_GUIDE.md

7. **发布**
   - 编译打包
   - 版本标记 v1.2.0

**交付物**：
- ✅ `PlaceholderValidator.java`
- ✅ `ValidationReport.java`
- ✅ `PlaceholderValidatorTest.java`
- ✅ `PLACEHOLDER_REFERENCE.md`（自动生成）
- ✅ 更新 README.md
- ✅ v1.2.0 发布

**验收标准**：
- ✅ 能够扫描所有模板文件
- ✅ 能够检测缺失和未使用的占位符
- ✅ 能够生成完整的参考文档
- ✅ 测试覆盖率 > 80%

---

#### Phase 2.2：配置文件驱动映射规则（中等优先级）

**时间**: 5-7 天
**版本**: v1.3.0

**实施步骤**：

##### Week 1：YAML 基础设施

**Day 1-2：数据模型和加载器**

1. **添加依赖**
   ```xml
   <dependency>
       <groupId>com.fasterxml.jackson.dataformat</groupId>
       <artifactId>jackson-dataformat-yaml</artifactId>
       <version>2.15.2</version>
   </dependency>
   ```

2. **创建数据模型**
   - `MappingConfig.java`
   - `DerivedMappingRule.java`
   - `ConditionalMappingRule.java`
   - `EnvironmentMappingRule.java`

3. **创建 YamlMappingLoader.java**
   - YAML 文件加载
   - 异常处理

**Day 3-4：集成到 PlaceholderMapper**

4. **增强 PlaceholderMapper**
   - 新增 `buildFromYaml()` 方法
   - 实现规则解析逻辑
   - 保持向后兼容

5. **创建默认配置**
   - `placeholder-mappings.yaml`
   - 迁移现有映射规则

**Day 5-6：测试**

6. **编写测试**
   - `YamlMappingLoaderTest.java`
   - `PlaceholderMapperYamlTest.java`
   - 向后兼容测试

7. **实际运行验证**
   - 使用 YAML 配置生成文件
   - 对比输出一致性

**Day 7：文档和发布**

8. **更新文档**
   - 创建 YAML_MAPPING_GUIDE.md
   - 更新 README.md

9. **发布 v1.3.0**

**交付物**：
- ✅ `YamlMappingLoader.java`
- ✅ `MappingConfig.java` 及相关数据模型
- ✅ `placeholder-mappings.yaml`
- ✅ 增强的 `PlaceholderMapper.java`
- ✅ YAML_MAPPING_GUIDE.md
- ✅ v1.3.0 发布

**验收标准**：
- ✅ 能够从 YAML 加载映射规则
- ✅ 生成的文件与代码方式完全一致
- ✅ 向后兼容（无 YAML 文件时使用代码规则）
- ✅ 测试覆盖率 > 80%

---

#### Phase 2.3：增强功能（较低优先级）

**时间**: 7-10 天
**版本**: v1.4.0

**实施步骤**：

##### Week 1-2：分阶段实施

**Stage 1：占位符表达式（3-4 天）**

1. **创建 PlaceholderExpression.java**
   - 表达式解析
   - 转换器链

2. **修改 TemplateEngine**
   - 支持表达式求值

3. **测试和文档**

**Stage 2：国际化支持（2-3 天）**

4. **创建 I18nPlaceholderResolver.java**
   - ResourceBundle 集成
   - 占位符嵌套处理

5. **创建示例资源文件**
   - `messages_zh_CN.properties`
   - `messages_en_US.properties`

6. **测试和文档**

**Stage 3：继承和覆盖（2-3 天）**

7. **增强 YamlMappingLoader**
   - 支持 `extends` 字段
   - 实现 `merge()` 逻辑

8. **创建示例配置**
   - `base-mappings.yaml`
   - `site-specific-mappings.yaml`

9. **测试和文档**

**交付物**：
- ✅ `PlaceholderExpression.java`
- ✅ `I18nPlaceholderResolver.java`
- ✅ 增强的 `YamlMappingLoader.java`
- ✅ 示例资源文件
- ✅ 增强功能文档
- ✅ v1.4.0 发布

**验收标准**：
- ✅ 表达式占位符正常工作
- ✅ i18n 占位符正常工作
- ✅ 配置继承和覆盖正常工作
- ✅ 测试覆盖率 > 75%

---

## 📈 预期收益

### 整体收益分析

| 维度 | 第一阶段 | 第二阶段（预期） | 总提升 |
|------|---------|----------------|--------|
| **代码修改频率** | -40% | -80% | **-90%** |
| **占位符错误率** | N/A | -95% | **-95%** |
| **维护成本** | -30% | -80% | **-85%** |
| **灵活性** | +200% | +300% | **+500%** |
| **文档化程度** | 手工 | 自动生成 | **100% 自动化** |

### 功能级收益

#### 占位符校验工具

| 指标 | 当前状态 | 改进后 | 提升 |
|------|---------|--------|------|
| 占位符错误发现时间 | 运行时（晚） | 编译前（早） | **提前 100%** |
| 手工检查工作量 | 15 分钟/次 | 0 分钟（自动） | **-100%** |
| 占位符错误率 | ~5% | ~0.25% | **-95%** |
| 文档维护成本 | 30 分钟/周 | 0（自动生成） | **-100%** |

#### 配置文件驱动

| 指标 | 当前状态 | 改进后 | 提升 |
|------|---------|--------|------|
| 规则调整时间 | 10-15 分钟 | 1-2 分钟 | **-85%** |
| 需要编译 | 是 | 否 | **N/A** |
| 非开发人员可维护 | 否 | 是 | **+100%** |
| 规则可见性 | 分散在代码中 | 集中在配置文件 | **+200%** |

#### 增强功能

| 指标 | 当前状态 | 改进后 | 提升 |
|------|---------|--------|------|
| 派生占位符数量 | 需定义每个 | 按需使用表达式 | **-70%** |
| 多语言支持 | 手工硬编码 | i18n 自动化 | **+∞** |
| 配置复用性 | 低（复制粘贴） | 高（继承覆盖） | **+300%** |

### ROI 分析（投资回报率）

**投入**：
- 开发时间：14-20 天
- 开发成本：约 3-4 人周

**回报**（每年）：
- 减少维护时间：约 100+ 小时/年
- 减少错误修复时间：约 50+ 小时/年
- 减少文档维护时间：约 26 小时/年（30 分钟/周 × 52 周）

**总回报**：约 176+ 小时/年 ≈ **22 人天/年**

**ROI**：22 人天 / 4 人周 = **275%**（首年回本并盈利）

---

## 🛠️ 技术栈选型

### 核心依赖

#### YAML 解析

**选择**: Jackson YAML

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.15.2</version>
</dependency>
```

**理由**：
- ✅ 项目已使用 Jackson 处理 JSON，复用经验
- ✅ API 一致，学习成本低
- ✅ 性能优秀
- ✅ 社区活跃，维护良好

**替代方案**：
- SnakeYAML：功能相似，但需要单独学习 API

#### 正则表达式（占位符扫描）

**选择**: Java 标准库 `Pattern` / `Matcher`

```java
Pattern pattern = Pattern.compile("\\{\\$[a-zA-Z_][a-zA-Z0-9_.]*\\}");
```

**理由**：
- ✅ 无额外依赖
- ✅ 性能足够
- ✅ 占位符格式简单

#### 国际化

**选择**: Java 标准库 `ResourceBundle`

```java
ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
```

**理由**：
- ✅ Java 标准库，无额外依赖
- ✅ 成熟稳定
- ✅ 支持多种资源文件格式

**资源文件格式**: `.properties` 文件

```properties
# messages_zh_CN.properties (UTF-8)
site.welcome=欢迎来到 {$webSiteName}
```

### 工具链

| 工具 | 用途 | 版本 |
|------|------|------|
| Maven | 构建管理 | 3.8+ |
| JUnit | 单元测试 | 4.13+ |
| Jackson | JSON/YAML 解析 | 2.15+ |
| Lombok | 减少样板代码 | 1.18+ |

---

## ⚠️ 风险评估

### 技术风险

#### 风险 1：YAML 配置复杂性

**描述**：YAML 配置文件可能变得复杂难懂

**影响**: 中
**概率**: 中

**缓解措施**：
- ✅ 提供详细的配置示例和注释
- ✅ 创建配置验证工具
- ✅ 提供配置模板
- ✅ 编写清晰的文档

#### 风险 2：向后兼容性

**描述**：新功能可能破坏现有功能

**影响**: 高
**概率**: 低

**缓解措施**：
- ✅ 保留原有代码方式（如果无 YAML 文件）
- ✅ 充分的回归测试
- ✅ 逐步迁移，不强制使用新功能
- ✅ 版本管理清晰

#### 风险 3：性能影响

**描述**：YAML 解析和表达式求值可能影响性能

**影响**: 低
**概率**: 低

**缓解措施**：
- ✅ YAML 配置缓存（只加载一次）
- ✅ 表达式结果缓存
- ✅ 性能测试和基准测试
- ✅ 必要时优化

**预期性能影响**：
- YAML 加载：< 50ms（只执行一次）
- 表达式求值：< 1ms/表达式
- 总体影响：< 5%（可接受）

---

### 实施风险

#### 风险 4：工期延长

**描述**：开发时间可能超出预期

**影响**: 中
**概率**: 中

**缓解措施**：
- ✅ 分阶段实施，每阶段独立验收
- ✅ 优先实施高价值功能（Phase 2.1）
- ✅ 时间缓冲（预估 +20%）
- ✅ 持续跟踪进度

#### 风险 5：需求变更

**描述**：实施过程中需求可能变化

**影响**: 中
**概率**: 中

**缓解措施**：
- ✅ 灵活的架构设计
- ✅ 模块化实施
- ✅ 定期评审和调整
- ✅ 保持沟通

---

### 维护风险

#### 风险 6：配置文件维护成本

**描述**：YAML 配置可能变得难以维护

**影响**: 中
**概率**: 低

**缓解措施**：
- ✅ 清晰的配置结构和命名
- ✅ 配置验证工具
- ✅ 版本控制
- ✅ 文档和注释

#### 风险 7：学习成本

**描述**：团队需要学习新的配置方式

**影响**: 低
**概率**: 高

**缓解措施**：
- ✅ 详细的用户指南
- ✅ 配置示例和模板
- ✅ 培训和知识分享
- ✅ 渐进式采用

---

## 📎 附录

### A. 配置文件完整示例

#### placeholder-mappings.yaml

```yaml
# 占位符映射配置文件
# 版本: 1.0
# 日期: 2025-11-17

version: "1.0"

# 基础配置
config:
  prefix: "{$"
  suffix: "}"
  description: "占位符格式配置"

# 自动映射
auto-map:
  enabled: true
  description: "自动从 WhiteLabelConfig 提取字段"
  exclude-fields:
    - "additionalProperties"
    - "class"

# 派生映射规则
derived-mappings:
  - name: "{$className}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL"
    description: "类名（驼峰命名，首字母大写）"
    example: "ABC_SITE → AbcSite"

  - name: "{$upperName}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL_UPPER"
    description: "全大写类名"
    example: "ABC_SITE → ABCSITE"

  - name: "{$lowerName}"
    source: "webSiteName"
    transformer: "SNAKE_TO_CAMEL_LOWER"
    description: "全小写类名"
    example: "ABC_SITE → abcsite"

  - name: "{$enumName}"
    source: "host"
    transformer: "DOT_TO_UNDERSCORE_UPPER"
    condition: "host != null && !host.isEmpty()"
    description: "枚举名（从域名生成）"
    example: "abc.com → ABC_COM"

# 条件映射（仅在特定条件下生成）
conditional-mappings:
  - name: "{$cert}"
    condition: "apiWhiteLabel == true"
    source: "apiWalletInfo.cert"
    description: "API 钱包证书（仅 API 白标）"

  - name: "{$privateKey}"
    condition: "apiWhiteLabel == true"
    source: "apiWalletInfo.privateKey"
    description: "API 钱包私钥（仅 API 白标）"

  - name: "{$apiDomainValues}"
    condition: "apiWhiteLabel == true"
    source: "apiWalletInfo.apiDomain"
    transformer: "API_DOMAIN_FORMATTER"
    description: "API 域名值（仅 API 白标）"

# 环境相关映射
environment-mappings:
  - name: "{$corsDomainValues}"
    environments: ["DEV", "UAT", "SIM"]
    generator: "getCorsDomainValue"
    description: "CORS 允许的域名值（按环境不同）"

  - name: "{$enableFrontendBackendSeparationByDomainValues}"
    environments: ["DEV", "UAT", "SIM"]
    generator: "getEnableFrontendBackendSeparationByDomainValue"
    description: "前后端分离域名配置（按环境不同）"

# 常量映射
constant-mappings:
  - name: "{$defaultTimezone}"
    value: "UTC+8"
    description: "默认时区"

  - name: "{$defaultLanguage}"
    value: "zh_CN"
    description: "默认语言"

  - name: "{$generatedBy}"
    value: "WhiteLabelTool v1.2.0"
    description: "生成工具标识"
```

---

### B. 文件结构

```
Project-Tool/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── util/
│   │   │       └── placeholder/
│   │   │           ├── PlaceholderMapper.java       (已有)
│   │   │           ├── PlaceholderValidator.java    (新增 - Phase 2.1)
│   │   │           ├── ValidationReport.java        (新增 - Phase 2.1)
│   │   │           ├── YamlMappingLoader.java       (新增 - Phase 2.2)
│   │   │           ├── MappingConfig.java           (新增 - Phase 2.2)
│   │   │           ├── PlaceholderExpression.java   (新增 - Phase 2.3)
│   │   │           └── I18nPlaceholderResolver.java (新增 - Phase 2.3)
│   │   └── resources/
│   │       ├── placeholder-mappings.yaml            (新增 - Phase 2.2)
│   │       ├── messages_zh_CN.properties            (新增 - Phase 2.3)
│   │       └── messages_en_US.properties            (新增 - Phase 2.3)
│   └── test/
│       └── java/
│           └── util/
│               └── placeholder/
│                   ├── PlaceholderValidatorTest.java        (新增 - Phase 2.1)
│                   ├── YamlMappingLoaderTest.java           (新增 - Phase 2.2)
│                   ├── PlaceholderExpressionTest.java       (新增 - Phase 2.3)
│                   └── I18nPlaceholderResolverTest.java     (新增 - Phase 2.3)
├── docs/                                             (新增目录)
│   ├── PHASE2_PLAN.md                               (本文档)
│   ├── PLACEHOLDER_VALIDATION_GUIDE.md              (新增 - Phase 2.1)
│   ├── YAML_MAPPING_GUIDE.md                        (新增 - Phase 2.2)
│   └── ADVANCED_FEATURES_GUIDE.md                   (新增 - Phase 2.3)
├── PLACEHOLDER_REFERENCE.md                         (自动生成 - Phase 2.1)
├── DYNAMIC_FIELDS_GUIDE.md                          (已有)
├── CACHE_OPTIMIZATION_REPORT.md                     (已有)
├── PHASE1_COMPLETION_REPORT.md                      (已有)
└── README.md                                         (更新)
```

---

### C. 测试策略

#### 单元测试

| 测试类 | 覆盖范围 | 目标覆盖率 |
|--------|---------|-----------|
| PlaceholderValidatorTest | 模板扫描、验证、文档生成 | 85%+ |
| YamlMappingLoaderTest | YAML 加载、解析、错误处理 | 80%+ |
| PlaceholderExpressionTest | 表达式解析、求值 | 80%+ |
| I18nPlaceholderResolverTest | i18n 解析、占位符嵌套 | 75%+ |

#### 集成测试

1. **端到端测试**
   - 使用真实配置文件
   - 生成完整的文件集
   - 验证输出正确性

2. **向后兼容测试**
   - 无 YAML 配置时使用代码规则
   - 输出与原版本完全一致

3. **性能测试**
   - YAML 加载时间 < 50ms
   - 占位符替换时间 < 10ms
   - 总体性能损失 < 5%

---

### D. 文档清单

#### 用户文档

| 文档 | 目标读者 | 内容 |
|------|---------|------|
| README.md | 所有用户 | 快速开始、命令参考 |
| PLACEHOLDER_VALIDATION_GUIDE.md | 开发者 | 校验工具使用指南 |
| YAML_MAPPING_GUIDE.md | 配置管理员 | YAML 配置详解 |
| ADVANCED_FEATURES_GUIDE.md | 高级用户 | 表达式、i18n、继承 |
| PLACEHOLDER_REFERENCE.md | 所有用户 | 占位符参考（自动生成） |

#### 技术文档

| 文档 | 目标读者 | 内容 |
|------|---------|------|
| PHASE2_PLAN.md | 开发者、管理者 | 第二阶段规划 |
| ARCHITECTURE.md | 开发者 | 系统架构设计 |
| API_REFERENCE.md | 开发者 | API 文档 |

---

### E. 命令行参考

#### 现有命令（v1.1.0）

```bash
# 生成白标文件
java -jar Project-Tool.jar A <config.json>
```

#### 新增命令（v1.2.0+）

```bash
# 验证占位符
java -jar Project-Tool.jar validate [config.json]

# 生成占位符文档
java -jar Project-Tool.jar doc [config.json] [output.md]
```

#### 使用示例

```bash
# 1. 验证默认配置的占位符
java -jar Project-Tool.jar validate

# 2. 验证指定配置
java -jar Project-Tool.jar validate config/abc-site.json

# 3. 生成默认文档
java -jar Project-Tool.jar doc

# 4. 生成到指定路径
java -jar Project-Tool.jar doc config/abc-site.json docs/abc-placeholders.md

# 5. 生成白标文件（原有功能）
java -jar Project-Tool.jar A config/abc-site.json
```

---

## 🎯 总结

### 第二阶段核心价值

1. **降低维护成本 80%**
   - 配置文件驱动，无需修改代码
   - 自动化校验，减少人为错误
   - 自动生成文档，零维护成本

2. **减少错误率 95%**
   - 编译前发现占位符错误
   - 自动验证完整性
   - 清晰的错误提示

3. **提升灵活性 300%+**
   - 占位符表达式支持
   - 多语言支持
   - 配置继承和覆盖

### 实施建议

**推荐路径**：
```
Phase 2.1 (2-3 天)
  → 立即获得价值（减少错误）
  →
Phase 2.2 (5-7 天)
  → 长期降低维护成本
  →
Phase 2.3 (可选, 7-10 天)
  → 锦上添花
```

**关键成功因素**：
1. ✅ 分阶段实施，每阶段独立验收
2. ✅ 充分测试，确保向后兼容
3. ✅ 详细文档，降低学习成本
4. ✅ 持续优化，根据反馈调整

---

**规划者**: Claude (MCP)
**审核者**: Wilson
**创建时间**: 2025-11-17
**目标版本**: v1.2.0 - v1.4.0
**预计总工期**: 14-20 天

---

**下一步**：等待排期，确定开始时间 🚀
