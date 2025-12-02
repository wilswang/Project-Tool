package util.placeholder;

import java.util.function.Function;

/**
 * 派生占位符映射定义
 * 用于定义从配置对象提取值并转换为占位符的自定义映射规则
 *
 * @param <T> 配置对象类型
 *
 * @author MCP
 * @version 1.0.0
 *
 * @example
 * <pre>
 * // 创建派生映射
 * DerivedMapping<WhiteLabelConfig> classNameMapping = DerivedMapping.of(
 *     "{$className}",
 *     config -> Transformers.SNAKE_TO_CAMEL.transform(config.getWebSiteName())
 * );
 *
 * // 使用转换器的简化方式
 * DerivedMapping<WhiteLabelConfig> upperMapping = DerivedMapping.of(
 *     "{$webSiteName}",
 *     WhiteLabelConfig::getWebSiteName,
 *     Transformers.SNAKE_TO_CAMEL_UPPER
 * );
 * </pre>
 */
public class DerivedMapping<T> {

	private final String placeholderName;
	private final Function<T, String> extractor;

	/**
	 * 构造派生映射
	 *
	 * @param placeholderName 占位符名称（如 "{$className}"）
	 * @param extractor 从配置对象提取值的函数
	 */
	private DerivedMapping(String placeholderName, Function<T, String> extractor) {
		this.placeholderName = placeholderName;
		this.extractor = extractor;
	}

	/**
	 * 获取占位符名称
	 *
	 * @return 占位符名称
	 */
	public String getPlaceholderName() {
		return placeholderName;
	}

	/**
	 * 从配置对象提取值
	 *
	 * @param config 配置对象
	 * @return 提取的值，如果条件不满足则返回 null
	 */
	public String extractValue(T config) {
		if (config == null) {
			return "";
		}
		String value = extractor.apply(config);
		// 保持 null 值不变（用于条件映射），空字符串转为空字符串
		return value;
	}

	/**
	 * 创建派生映射（工厂方法）
	 *
	 * @param placeholderName 占位符名称
	 * @param extractor 值提取函数
	 * @param <T> 配置对象类型
	 * @return 派生映射实例
	 */
	public static <T> DerivedMapping<T> of(String placeholderName, Function<T, String> extractor) {
		return new DerivedMapping<>(placeholderName, extractor);
	}

	/**
	 * 创建带转换器的派生映射（工厂方法）
	 *
	 * @param placeholderName 占位符名称
	 * @param fieldExtractor 字段提取函数（提取原始值）
	 * @param transformer 值转换器
	 * @param <T> 配置对象类型
	 * @param <R> 字段类型
	 * @return 派生映射实例
	 */
	public static <T, R> DerivedMapping<T> of(
		String placeholderName,
		Function<T, R> fieldExtractor,
		Transformer<R> transformer
	) {
		return new DerivedMapping<>(
			placeholderName,
			config -> {
				R fieldValue = fieldExtractor.apply(config);
				return transformer.transform(fieldValue);
			}
		);
	}

	/**
	 * 创建带条件的派生映射（工厂方法）
	 * 仅当条件满足时才生成占位符
	 *
	 * @param placeholderName 占位符名称
	 * @param condition 条件判断函数
	 * @param extractor 值提取函数
	 * @param <T> 配置对象类型
	 * @return 派生映射实例
	 */
	public static <T> DerivedMapping<T> ofConditional(
		String placeholderName,
		Function<T, Boolean> condition,
		Function<T, String> extractor
	) {
		return new DerivedMapping<>(
			placeholderName,
			config -> {
				if (condition.apply(config)) {
					return extractor.apply(config);
				}
				return null;  // 返回 null 表示不添加此占位符
			}
		);
	}

	/**
	 * 创建常量占位符映射（固定值）
	 *
	 * @param placeholderName 占位符名称
	 * @param constantValue 常量值
	 * @param <T> 配置对象类型
	 * @return 派生映射实例
	 */
	public static <T> DerivedMapping<T> constant(String placeholderName, String constantValue) {
		return new DerivedMapping<>(placeholderName, config -> constantValue);
	}

	@Override
	public String toString() {
		return "DerivedMapping{" +
			"placeholderName='" + placeholderName + '\'' +
			'}';
	}
}
