package util.placeholder;

/**
 * 占位符值转换器接口
 * 用于将配置字段值转换为占位符所需的格式
 *
 * @param <T> 输入值类型
 *
 * @author MCP
 * @version 1.0.0
 *
 * @example
 * <pre>
 * // 定义转换器
 * Transformer<String> upperCase = String::toUpperCase;
 *
 * // 使用转换器
 * String result = upperCase.transform("hello"); // "HELLO"
 * </pre>
 */
@FunctionalInterface
public interface Transformer<T> {

	/**
	 * 转换输入值为字符串
	 *
	 * @param input 输入值
	 * @return 转换后的字符串
	 */
	String transform(T input);

	/**
	 * 返回一个恒等转换器（不做任何转换，直接调用 toString）
	 *
	 * @param <T> 输入类型
	 * @return 恒等转换器
	 */
	static <T> Transformer<T> identity() {
		return input -> input == null ? "" : input.toString();
	}

	/**
	 * 链式组合两个转换器
	 *
	 * @param after 后续转换器
	 * @param <R> 后续转换器的输入类型
	 * @return 组合后的转换器
	 *
	 * @example
	 * <pre>
	 * Transformer<String> snakeToCamel = Transformers.SNAKE_TO_CAMEL;
	 * Transformer<String> upper = String::toUpperCase;
	 * Transformer<String> combined = snakeToCamel.andThen(upper);
	 * </pre>
	 */
	default <R> Transformer<T> andThen(Transformer<String> after) {
		return input -> after.transform(this.transform(input));
	}
}
