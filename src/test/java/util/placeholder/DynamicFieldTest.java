package util.placeholder;

import org.junit.Test;
import tool.whiteLabel.WhiteLabelConfig;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * 动态字段支持测试
 * 验证 @JsonAnySetter 和自动映射的集成
 *
 * @author MCP
 * @version 1.0.0
 */
public class DynamicFieldTest {

	@Test
	public void testDynamicFields_AutoMapping() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("TEST-001");
		config.setWebSiteName("test_site");
		config.setWebSiteValue(999);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");
		config.setHost("example.com");

		// 模拟 JSON 中的额外字段（通过 @JsonAnySetter）
		config.setAdditionalProperty("newField", "This is a NEW field");
		config.setAdditionalProperty("customPlaceholder", "Custom value");
		config.setAdditionalProperty("priority", "HIGH");

		// 执行自动映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证：标准字段正常映射
		assertEquals("TEST-001", result.get("{$ticketNo}"));
		assertEquals("test_site", result.get("{$webSiteName}"));
		assertEquals("999", result.get("{$webSiteValue}"));
		assertEquals("example.com", result.get("{$host}"));

		// 验证：动态字段也被映射为占位符 ⭐
		assertEquals("This is a NEW field", result.get("{$newField}"));
		assertEquals("Custom value", result.get("{$customPlaceholder}"));
		assertEquals("HIGH", result.get("{$priority}"));

		System.out.println("✅ 动态字段测试通过！");
		System.out.println("映射结果包含 " + result.size() + " 个占位符");
		System.out.println("动态字段占位符：");
		System.out.println("  {$newField} = " + result.get("{$newField}"));
		System.out.println("  {$customPlaceholder} = " + result.get("{$customPlaceholder}"));
		System.out.println("  {$priority} = " + result.get("{$priority}"));
	}

	@Test
	public void testDynamicFields_WithBuilder() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("TEST-002");
		config.setWebSiteName("dynamic_test");
		config.setWebSiteValue(888);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");
		config.setHost("dynamic.com");

		// 添加动态字段
		config.setAdditionalProperty("environment", "production");
		config.setAdditionalProperty("region", "US-WEST");

		// 使用 Builder API
		Map<String, String> result = PlaceholderMapper.builder(config)
			.autoMap()  // 会自动包含动态字段
			.derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName()))
			.build();

		// 验证：动态字段被自动映射
		assertEquals("production", result.get("{$environment}"));
		assertEquals("US-WEST", result.get("{$region}"));

		// 验证：派生字段正常工作
		assertEquals("DynamicTest", result.get("{$className}"));
	}

	@Test
	public void testDynamicFields_NullValue() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("TEST-003");
		config.setWebSiteName("test");
		config.setWebSiteValue(777);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");

		// 添加动态字段，值为 null
		config.setAdditionalProperty("nullField", null);
		config.setAdditionalProperty("validField", "valid");

		// 执行映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证：null 值不应出现在映射中
		assertFalse("null 值不应被映射", result.containsKey("{$nullField}"));

		// 验证：非 null 值正常映射
		assertEquals("valid", result.get("{$validField}"));
	}

	@Test
	public void testDynamicFields_ComplexValue() {
		// 准备测试数据
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("TEST-004");
		config.setWebSiteName("test");
		config.setWebSiteValue(666);
		config.setJiraSummary("Test");
		config.setDeveloper("MCP");

		// 添加不同类型的动态字段
		config.setAdditionalProperty("stringField", "text value");
		config.setAdditionalProperty("intField", 12345);
		config.setAdditionalProperty("boolField", true);
		config.setAdditionalProperty("doubleField", 3.14);

		// 执行映射
		Map<String, String> result = PlaceholderMapper.autoMap(config);

		// 验证：所有类型都被转换为字符串
		assertEquals("text value", result.get("{$stringField}"));
		assertEquals("12345", result.get("{$intField}"));
		assertEquals("true", result.get("{$boolField}"));
		assertEquals("3.14", result.get("{$doubleField}"));
	}

	@Test
	public void testGetAdditionalProperty() {
		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setAdditionalProperty("key1", "value1");
		config.setAdditionalProperty("key2", "value2");

		// 测试获取方法
		assertEquals("value1", config.getAdditionalProperty("key1"));
		assertEquals("value2", config.getAdditionalProperty("key2"));
		assertNull(config.getAdditionalProperty("nonexistent"));

		// 测试获取所有额外属性
		Map<String, Object> allProps = config.getAdditionalProperties();
		assertEquals(2, allProps.size());
		assertTrue(allProps.containsKey("key1"));
		assertTrue(allProps.containsKey("key2"));
	}

	@Test
	public void testRealWorldScenario_DynamicPlaceholder() {
		// 模拟真实场景：用户想添加新的占位符，但不想修改 WhiteLabelConfig.java

		WhiteLabelConfig config = new WhiteLabelConfig();
		config.setTicketNo("SACRIC-99999");
		config.setWebSiteName("NEW_SITE");
		config.setWebSiteValue(1001);
		config.setHost("newsite.com");
		config.setApiWhiteLabel(false);
		config.setJiraSummary("Add new site with custom placeholders");
		config.setDeveloper("Wilson");

		// 用户在 JSON 中添加了这些新字段（无需修改 Java 类）
		config.setAdditionalProperty("siteCategory", "Sports");
		config.setAdditionalProperty("region", "Asia");
		config.setAdditionalProperty("launchDate", "2025-12-01");
		config.setAdditionalProperty("maxUsers", 100000);

		// 使用 PlaceholderMapper 自动映射
		Map<String, String> placeholders = PlaceholderMapper.builder(config)
			.autoMap()  // 会自动包含所有动态字段
			.derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName()))
			.derived("{$upperName}", c -> Transformers.SNAKE_TO_CAMEL_UPPER.transform(c.getWebSiteName()))
			.build();

		// 验证：所有标准字段都存在
		assertEquals("SACRIC-99999", placeholders.get("{$ticketNo}"));
		assertEquals("NEW_SITE", placeholders.get("{$webSiteName}"));
		assertEquals("NewSite", placeholders.get("{$className}"));
		assertEquals("NEWSITE", placeholders.get("{$upperName}"));

		// 验证：所有动态字段都被自动映射 ⭐
		assertEquals("Sports", placeholders.get("{$siteCategory}"));
		assertEquals("Asia", placeholders.get("{$region}"));
		assertEquals("2025-12-01", placeholders.get("{$launchDate}"));
		assertEquals("100000", placeholders.get("{$maxUsers}"));

		// 用户现在可以在模板中使用这些占位符：
		// {$siteCategory}
		// {$region}
		// {$launchDate}
		// {$maxUsers}

		System.out.println("✅ 真实场景测试通过！");
		System.out.println("用户可以在模板中使用以下新占位符：");
		System.out.println("  {$siteCategory} = " + placeholders.get("{$siteCategory}"));
		System.out.println("  {$region} = " + placeholders.get("{$region}"));
		System.out.println("  {$launchDate} = " + placeholders.get("{$launchDate}"));
		System.out.println("  {$maxUsers} = " + placeholders.get("{$maxUsers}"));
		System.out.println("\n无需修改任何 Java 代码！");
	}
}
