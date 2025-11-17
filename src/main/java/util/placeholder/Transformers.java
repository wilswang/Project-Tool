package util.placeholder;

import org.apache.commons.lang3.StringUtils;

/**
 * 预定义的占位符转换器工具类
 * 提供常用的字符串转换器，用于占位符值的格式化
 *
 * @author MCP
 * @version 1.0.0
 */
public final class Transformers {

	private Transformers() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 蛇形命名转驼峰命名（首字母大写）
	 * 示例: "hello_world" → "HelloWorld"
	 */
	public static final Transformer<String> SNAKE_TO_CAMEL = input -> {
		if (StringUtils.isBlank(input)) {
			return "";
		}
		String[] words = input.split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (word.length() > 0) {
				result.append(Character.toUpperCase(word.charAt(0)))
					.append(word.substring(1).toLowerCase());
			}
		}
		return result.toString();
	};

	/**
	 * 蛇形命名转驼峰命名后全大写
	 * 示例: "hello_world" → "HELLOWORLD"
	 */
	public static final Transformer<String> SNAKE_TO_CAMEL_UPPER =
		SNAKE_TO_CAMEL.andThen(String::toUpperCase);

	/**
	 * 蛇形命名转驼峰命名后全小写
	 * 示例: "hello_world" → "helloworld"
	 */
	public static final Transformer<String> SNAKE_TO_CAMEL_LOWER =
		SNAKE_TO_CAMEL.andThen(String::toLowerCase);

	/**
	 * 点号替换为下划线后全大写
	 * 示例: "example.com" → "EXAMPLE_COM"
	 */
	public static final Transformer<String> DOT_TO_UNDERSCORE_UPPER = input -> {
		if (StringUtils.isBlank(input)) {
			return "";
		}
		return input.replace(".", "_").toUpperCase();
	};

	/**
	 * 转大写
	 * 示例: "hello" → "HELLO"
	 */
	public static final Transformer<String> TO_UPPER = input -> {
		if (StringUtils.isBlank(input)) {
			return "";
		}
		return input.toUpperCase();
	};

	/**
	 * 转小写
	 * 示例: "HELLO" → "hello"
	 */
	public static final Transformer<String> TO_LOWER = input -> {
		if (StringUtils.isBlank(input)) {
			return "";
		}
		return input.toLowerCase();
	};

	/**
	 * 移除所有空白字符
	 * 示例: "hello world" → "helloworld"
	 */
	public static final Transformer<String> REMOVE_WHITESPACE = input -> {
		if (StringUtils.isBlank(input)) {
			return "";
		}
		return input.replaceAll("\\s+", "");
	};

	/**
	 * 恒等转换（不做任何改变）
	 * 示例: "hello" → "hello"
	 */
	public static final Transformer<String> IDENTITY = Transformer.identity();

	/**
	 * 安全 toString（null 转为空字符串）
	 *
	 * @param <T> 输入类型
	 * @return 转换器
	 */
	public static <T> Transformer<T> safeToString() {
		return input -> input == null ? "" : input.toString();
	}

	/**
	 * 自定义字符串替换转换器
	 *
	 * @param target 要替换的字符串
	 * @param replacement 替换为的字符串
	 * @return 转换器
	 */
	public static Transformer<String> replace(String target, String replacement) {
		return input -> {
			if (StringUtils.isBlank(input)) {
				return "";
			}
			return input.replace(target, replacement);
		};
	}

	/**
	 * 添加前缀
	 *
	 * @param prefix 前缀
	 * @return 转换器
	 */
	public static Transformer<String> addPrefix(String prefix) {
		return input -> {
			if (StringUtils.isBlank(input)) {
				return "";
			}
			return prefix + input;
		};
	}

	/**
	 * 添加后缀
	 *
	 * @param suffix 后缀
	 * @return 转换器
	 */
	public static Transformer<String> addSuffix(String suffix) {
		return input -> {
			if (StringUtils.isBlank(input)) {
				return "";
			}
			return input + suffix;
		};
	}
}
