package util.placeholder;

import org.junit.Test;
import tool.whiteLabel.ApiWalletInfo;
import tool.whiteLabel.GroupInfo;
import tool.whiteLabel.WhiteLabelConfig;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * PlaceholderMapper 单元测试
 *
 * @author MCP
 * @version 1.0.0
 */
public class PlaceholderMapperTest {

	@Test
	public void testAutoMap_SimpleFields() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("12345");
		config.setWebSiteName("test_site");
		config.setWebSiteValue(101);
		config.setHost("example.com");
		config.setApiWhiteLabel(false);
		config.setJiraSummary("Test Summary");
		config.setDeveloper("TestDev");

		// 执行映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证基础字段映射
		assertEquals("12345", result.get("{$ticketNo}"));
		assertEquals("test_site", result.get("{$webSiteName}"));
		assertEquals("101", result.get("{$webSiteValue}"));
		assertEquals("example.com", result.get("{$host}"));
		assertEquals("false", result.get("{$apiWhiteLabel}"));
		assertEquals("Test Summary", result.get("{$jiraSummary}"));
		assertEquals("TestDev", result.get("{$developer}"));
	}

	@Test
	public void testAutoMap_NestedObject() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("12345");
		config.setWebSiteName("test_site");
		config.setWebSiteValue(101);
		config.setApiWhiteLabel(true);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");

		ApiWalletInfo apiWalletInfo = new ApiWalletInfo();
		apiWalletInfo.setCert("TEST-CERT");
		apiWalletInfo.setGroup("A48");
		apiWalletInfo.setNewGroup(true);

		GroupInfo groupInfo = new GroupInfo();
		groupInfo.setPrivateIpSetId("ipset-001");
		groupInfo.setPrivateIp(Arrays.asList("192.168.1.1"));
		groupInfo.setBkIpSetId(Arrays.asList("bk-001", "bk-002"));
		groupInfo.setApiInfoBkIpSetId("api-bk-001");
		groupInfo.setBackup(Arrays.asList("backup1.com", "backup2.com"));

		apiWalletInfo.setGroupInfo(groupInfo);
		config.setApiWalletInfo(apiWalletInfo);

		// 执行映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证嵌套对象映射
		assertEquals("TEST-CERT", result.get("{$apiWalletInfo.cert}"));
		assertEquals("A48", result.get("{$apiWalletInfo.group}"));
		assertEquals("true", result.get("{$apiWalletInfo.newGroup}"));
		assertEquals("ipset-001", result.get("{$apiWalletInfo.groupInfo.privateIpSetId}"));
		assertEquals("api-bk-001", result.get("{$apiWalletInfo.groupInfo.apiInfoBkIpSetId}"));
	}

	@Test
	public void testAutoMap_NullValues() {
		// 准备测试数据（部分字段为 null）
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("12345");
		config.setWebSiteName("test_site");
		config.setWebSiteValue(101);
		config.setJiraSummary("Test");
		// host 为 null
		// apiWalletInfo 为 null

		// 执行映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证：null 字段不应出现在映射中
		assertFalse(result.containsKey("{$host}"));
		assertFalse(result.containsKey("{$apiWalletInfo.cert}"));

		// 验证：非 null 字段正常映射
		assertEquals("12345", result.get("{$ticketNo}"));
		assertEquals("test_site", result.get("{$webSiteName}"));
	}

	@Test
	public void testBuilder_WithDerivedMappings() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setWebSiteName("hello_world");
		config.setHost("example.com");
		config.setTicketNo("12345");
		config.setWebSiteValue(101);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");

		// 使用 Builder 构建映射
		Map<String, String> result = PlaceholderMapper.builder(config)
			.autoMap()
			.derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName()))
			.derived("{$upperName}", c -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(c.getWebSiteName()))
			.derived("{$enumName}", c -> Transformers.DOT_TO_UNDERSCORE_UPPER.transform(c.getHost()))
			.build();

		// 验证自动映射
		assertEquals("hello_world", result.get("{$webSiteName}"));
		assertEquals("example.com", result.get("{$host}"));

		// 验证派生映射
		assertEquals("HelloWorld", result.get("{$className}"));
		assertEquals("HELLOWORLD", result.get("{$upperName}"));
		assertEquals("EXAMPLE_COM", result.get("{$enumName}"));
	}

	@Test
	public void testBuilder_ConditionalMapping() {
		// 准备测试数据 - API 白标场景
		WhiteLabelConfig apiConfig = new WhiteLabelConfig();
		apiConfig.setWebSiteName("test_api");
		apiConfig.setWebSiteValue(201);
		apiConfig.setApiWhiteLabel(true);
		apiConfig.setTicketNo("12345");
		apiConfig.setJiraSummary("Test");
		apiConfig.setDeveloper("MCP");

		ApiWalletInfo apiWalletInfo = new ApiWalletInfo();
		apiWalletInfo.setCert("API-CERT");
		apiWalletInfo.setGroup("B99");
		apiConfig.setApiWalletInfo(apiWalletInfo);

		// 使用条件映射
		Map<String, String> result = PlaceholderMapper.builder(apiConfig)
			.autoMap()
			.derivedIf("$cert",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getCert())
			.derivedIf("$group",
				WhiteLabelConfig::isApiWhiteLabel,
				config -> config.getApiWalletInfo().getGroup())
			.derivedIf("$url",
				config -> !config.isApiWhiteLabel(),
				WhiteLabelConfig::getHost)
			.build();

		// 验证：API 白标场景下应该有 cert 和 group
		assertEquals("API-CERT", result.get("$cert"));
		assertEquals("B99", result.get("$group"));
		// 验证：API 白标场景下不应该有 url
		assertFalse(result.containsKey("$url"));
	}

	@Test
	public void testBuilder_ConstantMapping() {
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("12345");
		config.setWebSiteName("test");
		config.setWebSiteValue(101);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");

		Map<String, String> result = PlaceholderMapper.builder(config)
			.autoMap()
			.constant("{$systemName}", "WhiteLabelSystem")
			.constant("{$version}", "1.0.0")
			.build();

		assertEquals("WhiteLabelSystem", result.get("{$systemName}"));
		assertEquals("1.0.0", result.get("{$version}"));
	}

	@Test
	public void testTransformers_SnakeToCamel() {
		assertEquals("HelloWorld", Transformers.SNAKE_TO_CAMEL.transform("hello_world"));
		assertEquals("TestSite", Transformers.SNAKE_TO_CAMEL.transform("test_site"));
		assertEquals("A", Transformers.SNAKE_TO_CAMEL.transform("a"));
		assertEquals("", Transformers.SNAKE_TO_CAMEL.transform(""));
	}

	@Test
	public void testTransformers_SnakeToCamelUpper() {
		assertEquals("HELLOWORLD", Transformers.SNAKE_TO_CAMEL_UPPER.transform("hello_world"));
		assertEquals("TESTSITE", Transformers.SNAKE_TO_CAMEL_UPPER.transform("test_site"));
	}

	@Test
	public void testTransformers_SnakeToCamelLower() {
		assertEquals("helloworld", Transformers.SNAKE_TO_CAMEL_LOWER.transform("hello_world"));
		assertEquals("testsite", Transformers.SNAKE_TO_CAMEL_LOWER.transform("test_site"));
	}

	@Test
	public void testTransformers_DotToUnderscoreUpper() {
		assertEquals("EXAMPLE_COM", Transformers.DOT_TO_UNDERSCORE_UPPER.transform("example.com"));
		assertEquals("SUB_DOMAIN_EXAMPLE_COM", Transformers.DOT_TO_UNDERSCORE_UPPER.transform("sub.domain.example.com"));
	}

	@Test
	public void testTransformers_Replace() {
		Transformer<String> replacer = Transformers.replace(".", "_");
		assertEquals("example_com", replacer.transform("example.com"));
	}

	@Test
	public void testTransformers_AddPrefix() {
		Transformer<String> prefixer = Transformers.addPrefix("PREFIX_");
		assertEquals("PREFIX_value", prefixer.transform("value"));
	}

	@Test
	public void testTransformers_AddSuffix() {
		Transformer<String> suffixer = Transformers.addSuffix("_SUFFIX");
		assertEquals("value_SUFFIX", suffixer.transform("value"));
	}

	@Test
	public void testTransformers_ChainedTransformers() {
		// 测试转换器链式组合
		Transformer<String> snakeToUpper = Transformers.SNAKE_TO_CAMEL.andThen(Transformers.TO_UPPER);
		assertEquals("HELLOWORLD", snakeToUpper.transform("hello_world"));
	}

	@Test
	public void testDerivedMapping_Simple() {
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setWebSiteName("test_site");

		DerivedMapping<WhiteLabelConfig> mapping = DerivedMapping.of(
			"{$className}",
			c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName())
		);

		assertEquals("{$className}", mapping.getPlaceholderName());
		assertEquals("TestSite", mapping.extractValue(config));
	}

	@Test
	public void testDerivedMapping_WithTransformer() {
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setWebSiteName("hello_world");

		DerivedMapping<WhiteLabelConfig> mapping = DerivedMapping.of(
			"{$upperName}",
			WhiteLabelConfig::getWebSiteName,
			Transformers.SNAKE_TO_CAMEL_UPPER
		);

		assertEquals("HELLOWORLD", mapping.extractValue(config));
	}

	@Test
	public void testDerivedMapping_Conditional() {
		// 满足条件的情况
		WhiteLabelConfig config1 = new WhiteLabelConfig();
		config1.setApiWhiteLabel(true);
		config1.setJiraSummary("Test");
		config1.setDeveloper("MCP");
		ApiWalletInfo info = new ApiWalletInfo();
		info.setCert("CERT-123");
		config1.setApiWalletInfo(info);

		DerivedMapping<WhiteLabelConfig> mapping = DerivedMapping.ofConditional(
			"$cert",
			WhiteLabelConfig::isApiWhiteLabel,
			c -> c.getApiWalletInfo().getCert()
		);

		assertEquals("CERT-123", mapping.extractValue(config1));

		// 不满足条件的情况
		WhiteLabelConfig config2 = new WhiteLabelConfig();
		config2.setApiWhiteLabel(false);
		config2.setHost("example.com");
		config2.setJiraSummary("Test");
		config2.setDeveloper("MCP");

		assertNull(mapping.extractValue(config2));
	}

	@Test
	public void testDerivedMapping_Constant() {
		WhiteLabelConfig config = new WhiteLabelConfig();

		DerivedMapping<WhiteLabelConfig> mapping = DerivedMapping.constant(
			"{$version}",
			"1.0.0"
		);

		assertEquals("1.0.0", mapping.extractValue(config));
	}

	@Test
	public void testRealWorldScenario_RegularWhiteLabel() {
		// 模拟真实场景：一般白标配置
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("SACRIC-12345");
		config.setWebSiteName("ABC_SITE");
		config.setWebSiteValue(101);
		config.setHost("abc.com");
		config.setApiWhiteLabel(false);
		config.setJiraSummary("Add ABC site");
		config.setDeveloper("Wilson");

		Map<String, String> result = PlaceholderMapper.builder(config)
			.autoMap()
			.derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName()))
			.derived("{$webSiteName}", c -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(c.getWebSiteName()))
			.derived("{$lowerCase}", c -> Transformers.SNAKE_TO_CAMEL_LOWER.transform(c.getWebSiteName()))
			.derived("{$enumName}", c -> Transformers.DOT_TO_UNDERSCORE_UPPER.transform(c.getHost()))
			.derivedIf("$url", c -> !c.isApiWhiteLabel(), WhiteLabelConfig::getHost)
			.build();

		// 验证结果
		assertEquals("SACRIC-12345", result.get("{$ticketNo}"));
		assertEquals("ABCSITE", result.get("{$webSiteName}"));
		assertEquals("AbcSite", result.get("{$className}"));
		assertEquals("abcsite", result.get("{$lowerCase}"));
		assertEquals("ABC_COM", result.get("{$enumName}"));
		assertEquals("abc.com", result.get("$url"));
		assertEquals("101", result.get("{$webSiteValue}"));
	}
}
