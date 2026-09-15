package tool.sheet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import tool.whiteLabel.GroupInfo;

/**
 * 把試算表查到的 {@link GroupInfo} 填進白牌單 JSON 的 {@code apiWalletInfo.groupInfo}。
 *
 * <h2>為什麼合併邏輯放 Java 而不是 shell</h2>
 * 偵測字面假值、逐欄比對、分類輸出是真正的邏輯。寫進 jq 會很難讀，
 * 而且 bash 與 PowerShell 要各寫一次、無法單元測試。
 *
 * <h2>合併規則</h2>
 * 逐欄獨立判斷：
 * <ul>
 *   <li><b>未填</b>（null、空、或 mapping rule 的字面假值）→ 填入試算表的值。這是新群組的正常路徑</li>
 *   <li><b>已填且相同</b> → 不動（{@code -s 3} 重跑會走到這裡）</li>
 *   <li><b>已填且不同</b> → <b>不覆寫</b>，記為衝突。正常流程不該發生 ——
 *       step 2 產出的形狀是固定的，會走到這裡只可能是有人手改過</li>
 * </ul>
 *
 * @author Wilson.Wang
 * @version 1.6.0
 */
public final class GroupInfoPatcher {

	/** GroupInfo 的欄位順序，與白牌單 JSON 及 task/api-2.0-group-info.md 的區塊一致 */
	static final List<String> FIELDS = Collections.unmodifiableList(java.util.Arrays.asList(
		"privateIpSetId", "privateIp", "bkIpSetId", "apiInfoBkIpSetId", "backup"));

	/**
	 * mapping rule 產出的字面假值樣式：欄位名本身，或欄位名加流水號。
	 * 例如 {@code "privateIpSetId"}、{@code "bkIpSetId1"}、{@code "backup6"}。
	 */
	private static final Map<String, Pattern> PLACEHOLDER_PATTERNS = buildPlaceholderPatterns();

	private GroupInfoPatcher() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	private static Map<String, Pattern> buildPlaceholderPatterns() {
		Map<String, Pattern> map = new LinkedHashMap<>();
		for (String field : java.util.Arrays.asList(
				"privateIpSetId", "privateIp", "bkIpSetId", "apiInfoBkIpSetId", "backup")) {
			map.put(field, Pattern.compile("^" + Pattern.quote(field) + "\\d*$"));
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * 讀取單子 JSON、合併、寫回原檔。
	 *
	 * <p>先寫暫存檔再 rename，避免寫到一半失敗把原檔弄壞。
	 *
	 * @param ticketJson 白牌單設定 JSON
	 * @param fromSheet  從試算表查到的值
	 */
	public static PatchResult patchFile(File ticketJson, GroupInfo fromSheet) throws SheetToolException {
		if (ticketJson == null || !ticketJson.isFile()) {
			throw new SheetToolException("單子 JSON 不存在: "
				+ (ticketJson == null ? "null" : ticketJson.getAbsolutePath()));
		}
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode root;
		try {
			JsonNode parsed = mapper.readTree(ticketJson);
			if (!parsed.isObject()) {
				throw new SheetToolException("單子 JSON 的最外層不是物件: " + ticketJson.getAbsolutePath());
			}
			root = (ObjectNode) parsed;
		} catch (IOException e) {
			throw new SheetToolException("單子 JSON 解析失敗 (" + ticketJson.getAbsolutePath()
				+ "): " + e.getMessage(), e);
		}

		JsonNode apiWalletInfo = root.get("apiWalletInfo");
		if (apiWalletInfo == null || !apiWalletInfo.isObject()) {
			throw new SheetToolException("單子 JSON 沒有 apiWalletInfo 物件，無法填入 groupInfo: "
				+ ticketJson.getAbsolutePath());
		}
		ObjectNode groupInfoNode = ensureGroupInfoNode((ObjectNode) apiWalletInfo, mapper);

		PatchResult result = merge(groupInfoNode, fromSheet, mapper);

		if (result.getFilled().isEmpty()) {
			// 沒有任何欄位被填，就不要動檔案 —— 避免只因為格式化而產生無意義的改動
			return result;
		}
		writeBack(ticketJson, root, mapper);
		return result;
	}

	/** 取出既有的 groupInfo；不存在或不是物件就建一個空的掛上去 */
	private static ObjectNode ensureGroupInfoNode(ObjectNode apiWalletInfo, ObjectMapper mapper) {
		JsonNode existing = apiWalletInfo.get("groupInfo");
		if (existing != null && existing.isObject()) {
			return (ObjectNode) existing;
		}
		ObjectNode created = mapper.createObjectNode();
		apiWalletInfo.set("groupInfo", created);
		return created;
	}

	/**
	 * 逐欄合併，直接修改傳入的 node。
	 *
	 * @param groupInfoNode 單子裡的 groupInfo（可能是空物件）
	 * @param fromSheet     試算表的值
	 */
	static PatchResult merge(ObjectNode groupInfoNode, GroupInfo fromSheet, ObjectMapper mapper)
			throws SheetToolException {
		if (fromSheet == null) {
			throw new SheetToolException("試算表查到的 groupInfo 為 null");
		}
		List<String> filled = new ArrayList<>();
		List<String> unchanged = new ArrayList<>();
		List<Conflict> conflicts = new ArrayList<>();

		for (String field : FIELDS) {
			JsonNode sheetValue = toNode(field, fromSheet, mapper);
			JsonNode current = groupInfoNode.get(field);

			if (isUnfilled(field, current)) {
				groupInfoNode.set(field, sheetValue);
				filled.add(field);
			} else if (current.equals(sheetValue)) {
				unchanged.add(field);
			} else {
				conflicts.add(new Conflict(field, current.toString(), sheetValue.toString()));
			}
		}
		reorder(groupInfoNode, mapper);
		return new PatchResult(filled, unchanged, conflicts);
	}

	/**
	 * 單子裡的值是不是「等於沒填」。
	 *
	 * <p>三種情況算沒填：
	 * <ol>
	 *   <li>欄位不存在或是 null</li>
	 *   <li>空字串、空陣列</li>
	 *   <li>mapping rule 的字面假值 —— 字串等於欄位名（或欄位名加流水號），
	 *       陣列則是<b>每個</b>元素都長這樣</li>
	 * </ol>
	 */
	static boolean isUnfilled(String field, JsonNode value) {
		if (value == null || value.isNull() || value.isMissingNode()) {
			return true;
		}
		Pattern placeholder = PLACEHOLDER_PATTERNS.get(field);
		if (value.isTextual()) {
			String text = value.asText().trim();
			return text.isEmpty() || (placeholder != null && placeholder.matcher(text).matches());
		}
		if (value.isArray()) {
			if (value.size() == 0) {
				return true;
			}
			if (placeholder == null) {
				return false;
			}
			for (JsonNode element : value) {
				if (!element.isTextual() || !placeholder.matcher(element.asText().trim()).matches()) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static JsonNode toNode(String field, GroupInfo info, ObjectMapper mapper) {
		switch (field) {
			case "privateIpSetId":
				return mapper.getNodeFactory().textNode(nullToEmpty(info.getPrivateIpSetId()));
			case "apiInfoBkIpSetId":
				return mapper.getNodeFactory().textNode(nullToEmpty(info.getApiInfoBkIpSetId()));
			case "privateIp":
				return toArray(info.getPrivateIp(), mapper);
			case "bkIpSetId":
				return toArray(info.getBkIpSetId(), mapper);
			case "backup":
				return toArray(info.getBackup(), mapper);
			default:
				throw new IllegalArgumentException("未知的 groupInfo 欄位: " + field);
		}
	}

	private static ArrayNode toArray(List<String> values, ObjectMapper mapper) {
		ArrayNode array = mapper.createArrayNode();
		if (values != null) {
			for (String value : values) {
				array.add(value);
			}
		}
		return array;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	/** 把欄位排成 {@link #FIELDS} 的順序，其餘未知欄位保留在後面 */
	private static void reorder(ObjectNode groupInfoNode, ObjectMapper mapper) {
		ObjectNode ordered = mapper.createObjectNode();
		for (String field : FIELDS) {
			if (groupInfoNode.has(field)) {
				ordered.set(field, groupInfoNode.get(field));
			}
		}
		java.util.Iterator<String> names = groupInfoNode.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			if (!FIELDS.contains(name)) {
				ordered.set(name, groupInfoNode.get(name));
			}
		}
		groupInfoNode.removeAll();
		groupInfoNode.setAll(ordered);
	}

	/**
	 * 寫回檔案。用 jq 的排版風格（2 空格縮排、{@code "key": value}、陣列逐行展開），
	 * 因為 step 2 是用 {@code jq .} 產生這個檔的，patch 之後不該讓格式變樣。
	 */
	private static void writeBack(File ticketJson, ObjectNode root, ObjectMapper mapper)
			throws SheetToolException {
		File temp = new File(ticketJson.getAbsolutePath() + ".patch.tmp");
		try {
			String json = mapper.writer(jqStylePrinter()).writeValueAsString(root);
			Files.write(temp.toPath(), (json + "\n").getBytes(StandardCharsets.UTF_8));
			Files.move(temp.toPath(), ticketJson.toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new SheetToolException("寫回單子 JSON 失敗 (" + ticketJson.getAbsolutePath()
				+ "): " + e.getMessage(), e);
		} finally {
			if (temp.exists() && !temp.delete()) {
				System.err.println("⚠️  暫存檔未能刪除: " + temp.getAbsolutePath());
			}
		}
	}

	/** jq 風格的排版：物件與陣列都逐行縮排 2 空格，欄位名與值之間只有冒號後一個空格 */
	static DefaultPrettyPrinter jqStylePrinter() {
		DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
		DefaultPrettyPrinter printer = new JqStylePrettyPrinter();
		printer.indentObjectsWith(indenter);
		printer.indentArraysWith(indenter);
		return printer;
	}

	/** Jackson 預設會寫成 {@code "key" : value}，jq 寫成 {@code "key": value} */
	private static final class JqStylePrettyPrinter extends DefaultPrettyPrinter {

		private static final long serialVersionUID = 1L;

		JqStylePrettyPrinter() {
			super();
		}

		JqStylePrettyPrinter(JqStylePrettyPrinter base) {
			super(base);
		}

		@Override
		public DefaultPrettyPrinter createInstance() {
			return new JqStylePrettyPrinter(this);
		}

		@Override
		public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
			generator.writeRaw(": ");
		}
	}

	/** 某個欄位單子與試算表不一致 */
	public static final class Conflict {

		private final String field;
		private final String ticketValue;
		private final String sheetValue;

		Conflict(String field, String ticketValue, String sheetValue) {
			this.field = field;
			this.ticketValue = ticketValue;
			this.sheetValue = sheetValue;
		}

		public String getField() {
			return field;
		}

		public String getTicketValue() {
			return ticketValue;
		}

		public String getSheetValue() {
			return sheetValue;
		}
	}

	/** 合併結果 */
	public static final class PatchResult {

		private final List<String> filled;
		private final List<String> unchanged;
		private final List<Conflict> conflicts;

		PatchResult(List<String> filled, List<String> unchanged, List<Conflict> conflicts) {
			this.filled = Collections.unmodifiableList(filled);
			this.unchanged = Collections.unmodifiableList(unchanged);
			this.conflicts = Collections.unmodifiableList(conflicts);
		}

		/** 這次被填入試算表值的欄位 */
		public List<String> getFilled() {
			return filled;
		}

		/** 已經是正確值、沒有變動的欄位 */
		public List<String> getUnchanged() {
			return unchanged;
		}

		/** 單子已有值但與試算表不同的欄位，值沒有被覆寫 */
		public List<Conflict> getConflicts() {
			return conflicts;
		}
	}
}
