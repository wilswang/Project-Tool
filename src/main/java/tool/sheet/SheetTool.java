package tool.sheet;

import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;

/**
 * Google Sheets 讀取工具的 CLI 進入點（MainSelector 代號 <b>D</b>）。
 *
 * <p>四個子指令對應四個驗收階段：{@code check-auth}（階段 0，不需要 spreadsheetId）、
 * {@code list-tabs}（階段 1）、{@code read-tab}（階段 2）、{@code read-columns}（階段 3）。
 *
 * <p>與 {@code JiraTool} 的一個刻意差異：<b>錯誤時 {@code System.exit(1)}</b>。
 * JiraTool 的大 catch 不呼叫 exit，所以永遠回 0 —— 對 shell 的
 * {@code if java -cp ... ; then} 用法是隱性缺陷（失敗被當成功）。新工具不複製這個缺陷。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class SheetTool {

	private static final String DEFAULT_FORMAT = "table";

	private SheetTool() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	public static void main(String[] args) {
		if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
			printUsage();
			return;
		}
		try {
			System.setOut(new PrintStream(System.out, true, "UTF-8"));
			System.setErr(new PrintStream(System.err, true, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			// UTF-8 一定存在，這裡不可能發生
		}

		Options options;
		try {
			options = Options.parse(args);
		} catch (SheetToolException e) {
			System.err.println("❌ " + e.getMessage());
			printUsage();
			System.exit(1);
			return;
		}

		try {
			switch (options.command) {
				case "check-auth":
					handleCheckAuth(options);
					break;
				case "list-tabs":
					handleListTabs(options);
					break;
				case "read-tab":
					handleReadTab(options);
					break;
				case "read-columns":
					handleReadColumns(options);
					break;
				case "group-info":
					handleGroupInfo(options);
					break;
				default:
					System.err.println("❌ 未知的指令: " + options.command);
					printUsage();
					System.exit(1);
			}
		} catch (Exception e) {
			System.err.println("❌ " + e.getMessage());
			if (options.testMode) {
				e.printStackTrace();
			}
			System.exit(1);
		}
	}

	// ---------- 階段 0 ----------

	private static void handleCheckAuth(Options options) throws Exception {
		// 刻意不讀 targets：只要有一個可讀的憑證就能跑，不需要 spreadsheetId
		String configuredPath = null;
		String applicationName = "Project-Tool Sheet Reader";
		try {
			SheetConfig config = SheetConfigLoader.load(options.configPath);
			configuredPath = config.getCredentialPath();
			applicationName = config.getApplicationName();
		} catch (SheetToolException e) {
			System.err.println("⚠️  讀不到設定檔，改用預設值繼續驗證憑證: " + e.getMessage());
		}
		File credential = ConfigPathResolver.resolveCredential(options.credentialPath, configuredPath);
		if (options.testMode) {
			System.out.println("ℹ️  憑證檔: " + credential.getAbsolutePath());
			System.out.println("ℹ️  applicationName: " + applicationName);
		}

		GoogleSheetClientFactory.AuthInfo auth = GoogleSheetClientFactory.verifyAuth(credential);
		CredentialGuard.Summary summary = auth.getCredential();

		System.out.println("✅ 憑證載入成功");
		System.out.println("   client_email : " + summary.getClientEmail());
		System.out.println("   project_id   : " + summary.getProjectId());
		System.out.println("   key_id(尾6)  : ..." + summary.getPrivateKeyIdTail());
		System.out.println("✅ Access token 取得成功，有效期至 "
			+ new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(auth.getTokenExpiry()));
		System.out.println("   scopes: " + GoogleSheetClientFactory.SCOPE);
		System.err.println("ℹ️  下一步：請把上面的 client_email 加為目標試算表的「檢視者」，再跑 list-tabs");
	}

	// ---------- 階段 1 ----------

	private static void handleListTabs(Options options) throws Exception {
		Context context = Context.create(options);
		Spreadsheet spreadsheet = context.reader.describe(context.spreadsheetId);

		System.out.println("✅ 試算表: " + spreadsheet.getProperties().getTitle());
		System.out.println("   locale=" + spreadsheet.getProperties().getLocale()
			+ "  timeZone=" + spreadsheet.getProperties().getTimeZone());
		System.out.println("   分頁清單:");
		System.out.print(SheetReader.describeTabs(spreadsheet));

		// 分頁名逐字相符是階段 1 的重點，設定對不上時要當場講明白
		String configured = context.tab;
		boolean matched = false;
		if (spreadsheet.getSheets() != null) {
			for (com.google.api.services.sheets.v4.model.Sheet tab : spreadsheet.getSheets()) {
				if (tab.getProperties().getTitle().equals(configured)) {
					matched = true;
					break;
				}
			}
		}
		if (matched) {
			System.out.println("✅ 設定的分頁名逐字相符: " + configured);
		} else {
			System.err.println("⚠️  設定的分頁名『" + configured + "』不在上面的清單裡。"
				+ "請對照後更新 sheet-config.json 的 tab（注意空白數量與全形／半形）");
		}
	}

	// ---------- 階段 2 ----------

	private static void handleReadTab(Options options) throws Exception {
		Context context = Context.create(options);
		List<List<Object>> rows = context.reader.readTab(context.spreadsheetId, context.tab,
			options.maxRows, context.target.getValueRenderOption());

		info(options, "✅ 讀到 " + rows.size() + " 列"
			+ (options.maxRows == null ? "" : "（--max-rows " + options.maxRows + "）"));
		printRows(toStringRows(rows), options.format, null);
	}

	/**
	 * 狀態訊息。{@code --format json} 時改走 stderr —— stdout 只留純 JSON，
	 * 這樣輸出才能直接 pipe 給 jq 或存檔。
	 */
	private static void info(Options options, String message) {
		if ("json".equalsIgnoreCase(options.format)) {
			System.err.println(message);
		} else {
			System.out.println(message);
		}
	}

	// ---------- 階段 3 ----------

	private static void handleReadColumns(Options options) throws Exception {
		Context context = Context.create(options);
		List<String> columns = resolveColumns(options, context);
		Integer startRow = options.startRow != null ? options.startRow : context.target.getStartRow();
		Integer endRow = options.endRow != null ? options.endRow : context.target.getEndRow();

		SheetReader.RangeResult result = context.reader.readColumns(context.spreadsheetId, context.tab,
			columns, startRow, endRow, context.target.getValueRenderOption());

		info(options, "✅ range " + result.getA1Range() + " 讀到 " + result.getRows().size() + " 列");
		if (options.testMode) {
			info(options, "ℹ️  baseRow=" + result.getBaseRow() + " baseColumn="
				+ A1RangeBuilder.indexToColumnLetter(result.getBaseColumn())
				+ " 投影欄位=" + columns);
		}

		List<Integer> absolute = new ArrayList<>();
		for (String column : columns) {
			absolute.add(A1RangeBuilder.columnLetterToIndex(column));
		}
		List<List<String>> projected =
			SheetValues.project(result.getRows(), absolute, result.getBaseColumn());
		printRows(projected, options.format, buildHeader(columns, result.getBaseRow()));
	}

	// ---------- 階段 4 ----------

	private static void handleGroupInfo(Options options) throws Exception {
		if (options.argument == null) {
			throw new SheetToolException("group-info 需要群組代號，例如: group-info A69");
		}
		Context context = Context.create(options);
		SheetConfig.GroupInfoMapping mapping = context.target.getGroupInfo();
		if (mapping == null) {
			throw new SheetToolException("target 沒有 groupInfo 欄位設定，無法對應");
		}

		List<String> columns = resolveColumns(options, context);
		Integer startRow = options.startRow != null ? options.startRow : context.target.getStartRow();
		Integer endRow = options.endRow != null ? options.endRow : context.target.getEndRow();
		SheetReader.RangeResult result = context.reader.readColumns(context.spreadsheetId, context.tab,
			columns, startRow, endRow, context.target.getValueRenderOption());

		GroupInfoMapper.GroupBlock block = GroupInfoMapper.locate(result.getRows(),
			result.getBaseRow(), result.getBaseColumn(), options.argument, mapping);
		GroupInfoMapper.MapResult mapped =
			GroupInfoMapper.map(block, result.getBaseColumn(), mapping);

		// group-info 的產出本來就是要拿去貼的 JSON，所以狀態訊息一律走 stderr，
		// stdout 永遠只有純 JSON，不需要額外指定 --format
		System.err.println("✅ 找到群組 " + block.getGroupCode() + "，區塊第 " + block.getStartRow()
			+ "-" + block.getEndRow() + " 列（" + block.size() + " 列）");
		if (options.testMode) {
			System.err.println("ℹ️  range " + result.getA1Range() + "，投影欄位 " + columns);
		}

		// 序列化成可以直接貼進白牌單 JSON 的形狀。GroupInfo 的欄位順序刻意與
		// task/api-2.0-group-info.md 的區塊一致，所以貼過去不用重排
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		Map<String, Object> wrapper = new LinkedHashMap<>();
		wrapper.put("groupInfo", mapped.getGroupInfo());
		System.out.println(mapper.writeValueAsString(wrapper));

		List<String> backup = mapped.getGroupInfo().getBackup();
		System.err.println("ℹ️  backup " + backup.size() + " 個 —— 來源 "
			+ (mapped.getBackupSources().isEmpty() ? "(無)" : joinWithSpace(mapped.getBackupSources())));

		if (options.patchPath == null) {
			for (String warning : mapped.getWarnings()) {
				System.err.println("⚠️  " + warning);
			}
			System.err.println("ℹ️  貼進 ProjectTool/sample/**/SACRIC-XXXX.json 的 apiWalletInfo.groupInfo，"
				+ "並確認 apiWalletInfo.newGroup = true");
			return;
		}

		// --patch：值有疑慮就不要寫進單子。只印出來讓人自己判斷還好，
		// 但自動填進去會一路變成正式 SQL，那是這整個功能要消滅的失敗模式
		if (!mapped.getWarnings().isEmpty()) {
			StringBuilder sb = new StringBuilder("試算表的值有疑慮，不自動填入單子:");
			for (String warning : mapped.getWarnings()) {
				sb.append("\n   ").append(warning);
			}
			sb.append("\n   請與 Infra 確認正確的值後手動填入 ")
				.append(new File(options.patchPath).getAbsolutePath());
			throw new SheetToolException(sb.toString());
		}
		applyPatch(options, mapped.getGroupInfo());
	}

	private static void applyPatch(Options options, tool.whiteLabel.GroupInfo fromSheet)
			throws SheetToolException {
		File target = new File(options.patchPath);
		GroupInfoPatcher.PatchResult result = GroupInfoPatcher.patchFile(target, fromSheet);

		if (!result.getFilled().isEmpty()) {
			System.err.println("✅ 已填入 " + target.getAbsolutePath());
			System.err.println("   填入欄位: " + join(result.getFilled(), ", "));
		}
		if (!result.getUnchanged().isEmpty()) {
			System.err.println("ℹ️  已是正確值、未變動: " + join(result.getUnchanged(), ", "));
		}
		for (GroupInfoPatcher.Conflict conflict : result.getConflicts()) {
			// 不覆寫。正常流程不該走到這裡 —— step 2 產出的形狀是固定的
			System.err.println("⚠️  " + conflict.getField() + " 單子已有值且與試算表不同，未覆寫:");
			System.err.println("       單子  : " + conflict.getTicketValue());
			System.err.println("       試算表: " + conflict.getSheetValue());
		}
		if (!result.getConflicts().isEmpty()) {
			System.err.println("⚠️  單子的值優先保留。若這是新群組，不該有既有值 —— 請人工確認");
		}
	}

	private static String joinWithSpace(List<String> values) {
		return join(values, " ");
	}

	private static List<String> resolveColumns(Options options, Context context)
			throws SheetToolException {
		if (options.columns != null) {
			return A1RangeBuilder.parseColumnSpec(options.columns);
		}
		SheetConfig.GroupInfoMapping mapping = context.target.getGroupInfo();
		if (mapping == null) {
			throw new SheetToolException("未指定 --columns，且 target 沒有 groupInfo 欄位設定可以推導");
		}
		// 沒給 --columns 時，用 groupInfo 設定裡所有會用到的欄位，方便階段 3 一次看全貌
		List<String> spec = new ArrayList<>();
		spec.add(mapping.getGroupCodeColumn());
		spec.add(mapping.getPrivateIpSetIdColumn());
		spec.addAll(mapping.getPrivateIpColumns());
		spec.add(mapping.getBkIpSetIdColumn());
		spec.add(mapping.getApiInfoBkIpSetIdColumn());
		spec.addAll(mapping.getBackupColumns());
		return A1RangeBuilder.parseColumnSpec(joinWithComma(spec));
	}

	private static String buildHeader(List<String> columns, int baseRow) {
		StringBuilder sb = new StringBuilder("row");
		for (String column : columns) {
			sb.append('\t').append(column);
		}
		return sb.toString() + " " + baseRow;
	}

	// ---------- 輸出 ----------

	private static List<List<String>> toStringRows(List<List<Object>> rows) {
		List<List<String>> result = new ArrayList<>();
		for (List<Object> row : rows) {
			List<String> line = new ArrayList<>();
			for (int i = 0; i < row.size(); i++) {
				line.add(SheetValues.cell(row, i));
			}
			result.add(line);
		}
		return result;
	}

	private static void printRows(List<List<String>> rows, String format, String headerSpec)
			throws Exception {
		String[] header = null;
		int baseRow = 1;
		if (headerSpec != null) {
			String[] parts = headerSpec.split(" ");
			header = parts[0].split("\t");
			baseRow = Integer.parseInt(parts[1]);
		}
		if ("json".equalsIgnoreCase(format)) {
			printJson(rows, header, baseRow);
			return;
		}
		boolean tsv = "tsv".equalsIgnoreCase(format);
		if (header != null) {
			System.out.println(tsv ? joinWithTab(Arrays.asList(header))
				: padJoin(Arrays.asList(header), rows));
		}
		int rowNumber = baseRow;
		for (List<String> row : rows) {
			List<String> withNumber = new ArrayList<>();
			if (header != null) {
				withNumber.add(String.valueOf(rowNumber));
			}
			withNumber.addAll(oneLine(row));
			System.out.println(tsv ? joinWithTab(withNumber) : padJoin(withNumber, rows));
			rowNumber++;
		}
	}

	private static void printJson(List<List<String>> rows, String[] header, int baseRow)
			throws Exception {
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		if (header == null) {
			System.out.println(mapper.writeValueAsString(rows));
			return;
		}
		List<Map<String, String>> result = new ArrayList<>();
		int rowNumber = baseRow;
		for (List<String> row : rows) {
			Map<String, String> entry = new LinkedHashMap<>();
			entry.put(header[0], String.valueOf(rowNumber));
			for (int i = 1; i < header.length; i++) {
				entry.put(header[i], i - 1 < row.size() ? row.get(i - 1) : "");
			}
			result.add(entry);
			rowNumber++;
		}
		System.out.println(mapper.writeValueAsString(result));
	}

	/** cell 內可能有換行，table / tsv 模式要壓成一行才不會破版 */
	private static List<String> oneLine(List<String> row) {
		List<String> result = new ArrayList<>(row.size());
		for (String value : row) {
			result.add(value.replace("\r", "").replace("\n", " ⏎ "));
		}
		return result;
	}

	private static String padJoin(List<String> row, List<List<String>> allRows) {
		StringBuilder sb = new StringBuilder("  ");
		for (String value : row) {
			String shown = value.length() > 38 ? value.substring(0, 37) + "…" : value;
			sb.append(String.format("%-40s", shown));
		}
		return sb.toString();
	}

	private static String joinWithTab(List<String> values) {
		return join(values, "\t");
	}

	private static String joinWithComma(List<String> values) {
		return join(values, ",");
	}

	private static String join(List<String> values, String separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(separator);
			}
			sb.append(values.get(i));
		}
		return sb.toString();
	}

	// ---------- 共用情境 ----------

	/** 讀取類指令共用的前置作業：載設定、驗證、建 client */
	private static final class Context {

		private final SheetConfig.Target target;
		private final SheetReader reader;
		private final String spreadsheetId;
		private final String tab;

		private Context(SheetConfig.Target target, SheetReader reader, String spreadsheetId, String tab) {
			this.target = target;
			this.reader = reader;
			this.spreadsheetId = spreadsheetId;
			this.tab = tab;
		}

		static Context create(Options options) throws Exception {
			SheetConfig config = SheetConfigLoader.load(options.configPath);
			SheetConfig.Target target = config.requireTarget(options.target);

			String spreadsheetId = options.spreadsheetId != null
				? options.spreadsheetId : target.getSpreadsheetId();
			String tab = options.tab != null ? options.tab : target.getTab();
			if (spreadsheetId == null || spreadsheetId.trim().isEmpty()) {
				throw new SheetToolException("spreadsheetId 尚未設定。"
					+ "請填入 config/sheet-config.json 的 targets，或用 --spreadsheetId 指定");
			}
			if (tab == null || tab.trim().isEmpty()) {
				throw new SheetToolException("分頁名尚未設定。請填入設定檔的 tab，或用 --tab 指定");
			}

			File credential =
				ConfigPathResolver.resolveCredential(options.credentialPath, config.getCredentialPath());
			// inspect 順便拿到 client_email，403 時要印給使用者去要權限
			CredentialGuard.Summary summary = CredentialGuard.inspect(credential);
			Sheets sheets = GoogleSheetClientFactory.create(credential, config.getApplicationName());
			if (options.testMode) {
				System.err.println("ℹ️  憑證檔: " + credential.getAbsolutePath());
				System.err.println("ℹ️  service account: " + summary.getClientEmail());
				System.err.println("ℹ️  spreadsheetId: " + spreadsheetId);
				System.err.println("ℹ️  tab: " + tab);
			}
			return new Context(target, new SheetReader(sheets, summary.getClientEmail()),
				spreadsheetId.trim(), tab);
		}
	}

	// ---------- 參數解析 ----------

	private static final class Options {

		private String command;
		/** 子指令後面的位置參數，目前只有 group-info 的群組代號會用到 */
		private String argument;
		private String configPath;
		private String credentialPath;
		private String target;
		private String spreadsheetId;
		private String tab;
		private String columns;
		/** group-info --patch <path>：把查到的 groupInfo 填進該白牌單 JSON */
		private String patchPath;
		private Integer startRow;
		private Integer endRow;
		private Integer maxRows;
		private String format = DEFAULT_FORMAT;
		private boolean testMode;

		static Options parse(String[] args) throws SheetToolException {
			Options options = new Options();
			List<String> positional = new ArrayList<>();
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if ("-t".equals(arg) || "--testMode".equals(arg)) {
					options.testMode = true;
				} else if (arg.startsWith("--")) {
					String value = valueOf(args, i, arg);
					i++;
					switch (arg) {
						case "--config":         options.configPath = value; break;
						case "--credential":     options.credentialPath = value; break;
						case "--target":         options.target = value; break;
						case "--spreadsheetId":  options.spreadsheetId = value; break;
						case "--tab":            options.tab = value; break;
						case "--columns":        options.columns = value; break;
						case "--patch":          options.patchPath = value; break;
						case "--format":         options.format = value; break;
						case "--max-rows":       options.maxRows = parseInt(arg, value); break;
						case "--rows":           options.applyRows(value); break;
						default:
							throw new SheetToolException("未知的選項: " + arg);
					}
				} else {
					positional.add(arg);
				}
			}
			if (positional.isEmpty()) {
				throw new SheetToolException("缺少子指令");
			}
			options.command = positional.get(0);
			if (positional.size() > 1) {
				options.argument = positional.get(1);
			}
			return options;
		}

		/** --rows 4:200 或 4: （不設上限） */
		private void applyRows(String value) throws SheetToolException {
			String[] parts = value.split(":", -1);
			if (parts.length != 2) {
				throw new SheetToolException("--rows 格式應為 start:end 或 start: ，收到: " + value);
			}
			this.startRow = parts[0].trim().isEmpty() ? null : parseInt("--rows", parts[0].trim());
			this.endRow = parts[1].trim().isEmpty() ? null : parseInt("--rows", parts[1].trim());
		}

		private static String valueOf(String[] args, int index, String flag) throws SheetToolException {
			if (index + 1 >= args.length) {
				throw new SheetToolException(flag + " 後面缺少值");
			}
			return args[index + 1];
		}

		private static Integer parseInt(String flag, String value) throws SheetToolException {
			try {
				return Integer.valueOf(value.trim());
			} catch (NumberFormatException e) {
				throw new SheetToolException(flag + " 需要數字，收到: " + value);
			}
		}
	}

	private static void printUsage() {
		System.out.println("================================================================================");
		System.out.println("  Sheet Tool - CLI Tool");
		System.out.println("  Read Google Spreadsheet data with a service account");
		System.out.println("================================================================================");
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  java -jar Project-Tool.jar D <command> [options]");
		System.out.println("  java -cp Project-Tool.jar tool.sheet.SheetTool <command> [options]");
		System.out.println();
		System.out.println("Available Commands:");
		System.out.println("  check-auth");
		System.out.println("      Verify the service account credential. Touches no spreadsheet.");
		System.out.println("      Example: SheetTool check-auth");
		System.out.println("      Example: SheetTool check-auth --credential /path/to/key.json");
		System.out.println();
		System.out.println("  list-tabs");
		System.out.println("      List every tab of the target spreadsheet and verify the configured name.");
		System.out.println("      Example: SheetTool list-tabs");
		System.out.println("      Example: SheetTool list-tabs --target api20-domain");
		System.out.println();
		System.out.println("  read-tab");
		System.out.println("      Read a whole tab.");
		System.out.println("      Example: SheetTool read-tab --max-rows 20");
		System.out.println("      Example: SheetTool read-tab --tab 'Sheet1' --format tsv");
		System.out.println();
		System.out.println("  read-columns");
		System.out.println("      Read selected columns. Columns are letters, ranges use - or :");
		System.out.println("      Example: SheetTool read-columns --columns B,H,J-K,M,S,U-Y --rows 4:200");
		System.out.println("      Example: SheetTool read-columns --format tsv");
		System.out.println();
		System.out.println("  group-info <groupCode>");
		System.out.println("      Return the groupInfo JSON needed to add that API 2.0 group.");
		System.out.println("      Paste it into apiWalletInfo.groupInfo of the white-label ticket JSON,");
		System.out.println("      or let --patch write it there for you.");
		System.out.println("      Example: SheetTool group-info A69");
		System.out.println("      Example: SheetTool group-info A69 --patch sample/SingleWallet/SACRIC-1402.json");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --config <path>         Config file path (default: ./config/sheet-config.json,");
		System.out.println("                          falls back to ./src/config/ when run from the repo root)");
		System.out.println("  --credential <path>     Service account JSON path, overrides the config file");
		System.out.println("  --target <name>         Which target to use (default: defaultTarget)");
		System.out.println("  --spreadsheetId <id>    Override the target's spreadsheetId");
		System.out.println("  --tab <title>           Override the target's tab");
		System.out.println("  --columns <spec>        Column letters, e.g. H or J-K or B,H,J-K,S");
		System.out.println("  --patch <ticket.json>   group-info only: write the groupInfo into that white-label");
		System.out.println("                          ticket JSON. Fields already holding a real value are left");
		System.out.println("                          alone and reported. Aborts instead of writing when the");
		System.out.println("                          spreadsheet value looks wrong (e.g. a non-UUID IP Set ID)");
		System.out.println("  --rows <start:end>      1-based inclusive, e.g. 4:200 or 4:");
		System.out.println("  --max-rows <n>          read-tab only: stop after N rows");
		System.out.println("  --format <fmt>          table (default) | tsv | json");
		System.out.println("  -t, --testMode          Print resolved paths and ranges, plus stack traces");
		System.out.println("  -h, --help              Show this help");
		System.out.println();
		System.out.println("Configuration File:");
		System.out.println("  config/sheet-config.json     spreadsheetId, tab, column mapping");
		System.out.println("  config/service-account.json  credential (git-ignored, never commit it)");
		System.out.println();
		System.out.println("Help:");
		System.out.println("  A 403 after check-auth succeeds means the spreadsheet is not shared with the");
		System.out.println("  service account. Share it with the client_email printed by check-auth.");
	}
}
