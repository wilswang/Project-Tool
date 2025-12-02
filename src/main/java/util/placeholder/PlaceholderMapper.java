package util.placeholder;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 占位符自动映射引擎
 * 使用反射自动将配置对象的字段映射为占位符键值对
 *
 * <p>支持的功能：</p>
 * <ul>
 *   <li>基础类型自动映射（String, Integer, Boolean 等）</li>
 *   <li>嵌套对象递归映射</li>
 *   <li>自定义派生映射</li>
 *   <li>条件映射</li>
 *   <li>空值安全处理</li>
 * </ul>
 *
 * @author MCP
 * @version 1.0.0
 *
 * @example
 * <pre>
 * // 基础用法
 * WhiteLabelConfig config = loadConfig();
 * Map<String, String> placeholders = PlaceholderMapper.autoMap(config);
 *
 * // 添加派生映射
 * Map<String, String> allPlaceholders = PlaceholderMapper.builder(config)
 *     .autoMap()
 *     .derived("{$className}", c -> Transformers.SNAKE_TO_CAMEL.transform(c.getWebSiteName()))
 *     .derived("{$enumName}", c -> c.getHost().replace(".", "_").toUpperCase())
 *     .build();
 * </pre>
 */
public class PlaceholderMapper {

	private PlaceholderMapper() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 自动映射配置对象的所有字段为占位符
	 * 占位符格式: {$fieldName}
	 *
	 * @param config 配置对象
	 * @return 占位符映射 Map
	 */
	public static Map<String, String> autoMap(Object config) {
		return autoMap(config, "");
	}

	/**
	 * 自动映射配置对象的所有字段为占位符（带前缀）
	 *
	 * @param config 配置对象
	 * @param prefix 前缀（用于嵌套对象，如 "apiWalletInfo."）
	 * @return 占位符映射 Map
	 */
	public static Map<String, String> autoMap(Object config, String prefix) {
		Map<String, String> result = new LinkedHashMap<>();

		if (config == null) {
			return result;
		}

		Class<?> clazz = config.getClass();

		// 遍历所有字段（包括继承的字段）
		for (Field field : getAllFields(clazz)) {
			// 跳过 static 和 transient 字段
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
				continue;
			}

			field.setAccessible(true);
			String fieldName = field.getName();
			Object fieldValue;

			try {
				fieldValue = field.get(config);
			} catch (IllegalAccessException e) {
				// 无法访问，跳过
				continue;
			}

			// 跳过 null 值
			if (fieldValue == null) {
				continue;
			}

			String placeholderKey = "{$" + prefix + fieldName + "}";

			// 基础类型和字符串直接映射
			if (isPrimitiveOrWrapper(field.getType()) || fieldValue instanceof String) {
				result.put(placeholderKey, String.valueOf(fieldValue));
			}
			// 枚举类型
			else if (fieldValue instanceof Enum) {
				result.put(placeholderKey, ((Enum<?>) fieldValue).name());
			}
			// 集合类型（暂不展开，仅记录 size）
			else if (fieldValue instanceof Collection) {
				// 集合类型通常需要自定义处理，这里仅记录其存在
				result.put(placeholderKey + ".size", String.valueOf(((Collection<?>) fieldValue).size()));
			}
			// 嵌套对象递归映射
			else if (isConfigObject(fieldValue)) {
				// 递归映射嵌套对象
				Map<String, String> nestedMap = autoMap(fieldValue, prefix + fieldName + ".");
				result.putAll(nestedMap);
			}
		}

		// 处理额外属性（支持动态字段）
		result.putAll(extractAdditionalProperties(config, prefix));

		return result;
	}

	/**
	 * 提取对象中的额外属性（通过 getAdditionalProperties 方法）
	 * 支持 @JsonAnySetter 和 @JsonAnyGetter 模式
	 *
	 * @param config 配置对象
	 * @param prefix 前缀
	 * @return 额外属性映射
	 */
	private static Map<String, String> extractAdditionalProperties(Object config, String prefix) {
		Map<String, String> result = new LinkedHashMap<>();

		if (config == null) {
			return result;
		}

		// 尝试查找 getAdditionalProperties() 方法
		try {
			java.lang.reflect.Method method = config.getClass().getMethod("getAdditionalProperties");
			Object additionalProps = method.invoke(config);

			if (additionalProps instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> propsMap = (Map<String, Object>) additionalProps;

				for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
					String key = entry.getKey();
					Object value = entry.getValue();

					if (value != null) {
						String placeholderKey = "{$" + prefix + key + "}";
						result.put(placeholderKey, String.valueOf(value));
					}
				}
			}
		} catch (NoSuchMethodException e) {
			// 没有 getAdditionalProperties 方法，忽略
		} catch (Exception e) {
			// 其他异常，记录但不中断
			System.err.println("⚠️  提取额外属性时发生错误: " + e.getMessage());
		}

		return result;
	}

	/**
	 * 添加派生映射到现有映射中
	 *
	 * @param baseMap 基础映射
	 * @param config 配置对象
	 * @param derivedMappings 派生映射数组
	 * @param <T> 配置对象类型
	 * @return 合并后的映射
	 */
	@SafeVarargs
	public static <T> Map<String, String> addDerived(
		Map<String, String> baseMap,
		T config,
		DerivedMapping<T>... derivedMappings
	) {
		Map<String, String> result = new LinkedHashMap<>(baseMap);

		for (DerivedMapping<T> mapping : derivedMappings) {
			String value = mapping.extractValue(config);
			// 如果派生值为 null，不添加（用于条件映射）
			if (value != null) {
				result.put(mapping.getPlaceholderName(), value);
			}
		}

		return result;
	}

	/**
	 * 创建映射构建器
	 *
	 * @param config 配置对象
	 * @param <T> 配置对象类型
	 * @return 映射构建器
	 */
	public static <T> Builder<T> builder(T config) {
		return new Builder<>(config);
	}

	/**
	 * 占位符映射构建器
	 * 提供流式 API 构建复杂的占位符映射
	 *
	 * @param <T> 配置对象类型
	 */
	public static class Builder<T> {
		private final T config;
		private final Map<String, String> mappings;

		private Builder(T config) {
			this.config = config;
			this.mappings = new LinkedHashMap<>();
		}

		/**
		 * 添加自动映射
		 *
		 * @return 构建器
		 */
		public Builder<T> autoMap() {
			mappings.putAll(PlaceholderMapper.autoMap(config));
			return this;
		}

		/**
		 * 添加派生映射
		 *
		 * @param mapping 派生映射
		 * @return 构建器
		 */
		public Builder<T> derived(DerivedMapping<T> mapping) {
			String value = mapping.extractValue(config);
			if (value != null) {
				mappings.put(mapping.getPlaceholderName(), value);
			}
			return this;
		}

		/**
		 * 添加派生映射（快捷方法）
		 *
		 * @param placeholderName 占位符名称
		 * @param extractor 值提取函数
		 * @return 构建器
		 */
		public Builder<T> derived(String placeholderName, java.util.function.Function<T, String> extractor) {
			return derived(DerivedMapping.of(placeholderName, extractor));
		}

		/**
		 * 添加条件派生映射（快捷方法）
		 *
		 * @param placeholderName 占位符名称
		 * @param condition 条件
		 * @param extractor 值提取函数
		 * @return 构建器
		 */
		public Builder<T> derivedIf(
			String placeholderName,
			java.util.function.Predicate<T> condition,
			java.util.function.Function<T, String> extractor
		) {
			if (condition.test(config)) {
				String value = extractor.apply(config);
				if (value != null) {
					mappings.put(placeholderName, value);
				}
			}
			return this;
		}

		/**
		 * 添加常量占位符
		 *
		 * @param placeholderName 占位符名称
		 * @param value 常量值
		 * @return 构建器
		 */
		public Builder<T> constant(String placeholderName, String value) {
			if (value != null) {
				mappings.put(placeholderName, value);
			}
			return this;
		}

		/**
		 * 手动添加占位符
		 *
		 * @param placeholderName 占位符名称
		 * @param value 值
		 * @return 构建器
		 */
		public Builder<T> put(String placeholderName, String value) {
			if (value != null) {
				mappings.put(placeholderName, value);
			}
			return this;
		}

		/**
		 * 批量添加占位符
		 *
		 * @param additionalMappings 额外的映射
		 * @return 构建器
		 */
		public Builder<T> putAll(Map<String, String> additionalMappings) {
			if (additionalMappings != null) {
				mappings.putAll(additionalMappings);
			}
			return this;
		}

		/**
		 * 构建最终的占位符映射
		 *
		 * @return 占位符映射 Map
		 */
		public Map<String, String> build() {
			return new LinkedHashMap<>(mappings);
		}
	}

	// ========== 辅助方法 ==========

	/**
	 * 获取类的所有字段（包括继承的字段）
	 *
	 * @param clazz 类
	 * @return 字段列表
	 */
	private static List<Field> getAllFields(Class<?> clazz) {
		List<Field> fields = new ArrayList<>();
		while (clazz != null && clazz != Object.class) {
			fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
			clazz = clazz.getSuperclass();
		}
		return fields;
	}

	/**
	 * 判断类型是否为基础类型或包装类型
	 *
	 * @param type 类型
	 * @return 是否为基础类型
	 */
	private static boolean isPrimitiveOrWrapper(Class<?> type) {
		return type.isPrimitive()
			|| type == Boolean.class
			|| type == Integer.class
			|| type == Long.class
			|| type == Float.class
			|| type == Double.class
			|| type == Byte.class
			|| type == Short.class
			|| type == Character.class;
	}

	/**
	 * 判断对象是否为配置对象（需要递归映射）
	 * 排除 JDK 内置类型和集合类型
	 *
	 * @param obj 对象
	 * @return 是否为配置对象
	 */
	private static boolean isConfigObject(Object obj) {
		if (obj == null) {
			return false;
		}

		Class<?> clazz = obj.getClass();
		String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";

		// 排除 JDK 内置类型
		if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
			return false;
		}

		// 排除集合、Map 等
		if (obj instanceof Collection || obj instanceof Map || obj.getClass().isArray()) {
			return false;
		}

		return true;
	}
}
