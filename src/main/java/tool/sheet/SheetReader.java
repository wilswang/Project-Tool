package tool.sheet;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

/**
 * 讀取試算表內容。建構子注入 {@link Sheets}，所以可以塞 mock transport 離線測試。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class SheetReader {

	private final Sheets sheets;

	/** 只用在 403 的錯誤訊息裡。拿不到時為 null，訊息會退化成泛用說法 */
	private final String serviceAccountEmail;

	public SheetReader(Sheets sheets) {
		this(sheets, null);
	}

	/**
	 * @param serviceAccountEmail service account 的 client_email。403 時會直接印出來 ——
	 *                            那正是要貼給試算表擁有者、請他加為檢視者的地址
	 */
	public SheetReader(Sheets sheets, String serviceAccountEmail) {
		if (sheets == null) {
			throw new IllegalArgumentException("Sheets client 不可為 null");
		}
		this.sheets = sheets;
		this.serviceAccountEmail = serviceAccountEmail;
	}

	/**
	 * 階段 1：只取 metadata，不取 grid data。
	 *
	 * <p>{@code setFields} 限制回傳欄位，避免整張表的內容被一起拉下來。
	 */
	public Spreadsheet describe(String spreadsheetId) throws IOException, SheetToolException {
		try {
			return sheets.spreadsheets().get(spreadsheetId)
				.setFields("properties(title,locale,timeZone),"
					+ "sheets.properties(sheetId,title,index,gridProperties)")
				.execute();
		} catch (GoogleJsonResponseException e) {
			throw translate(e, spreadsheetId, null);
		}
	}

	/**
	 * 階段 2：讀整個分頁。
	 *
	 * @param maxRows 只取前 N 列，null 表不設限
	 */
	public List<List<Object>> readTab(String spreadsheetId, String tabTitle, Integer maxRows,
			String valueRenderOption) throws IOException, SheetToolException {
		String range = A1RangeBuilder.quoteSheetTitle(tabTitle);
		if (maxRows != null && maxRows > 0) {
			range = range + "!1:" + maxRows;
		}
		return SheetValues.nullSafe(fetch(spreadsheetId, range, valueRenderOption, tabTitle));
	}

	/**
	 * 階段 3 / 4：讀指定欄位。
	 *
	 * <p>用「一段涵蓋範圍」而不是逐欄 batchGet，理由是保留列對齊 —— 群組的資料橫跨多欄多列，
	 * 逐欄取回來要重新對齊列號，那正是人工抄寫出錯的同一類問題。
	 */
	public RangeResult readColumns(String spreadsheetId, String tabTitle, List<String> columnLetters,
			Integer startRow, Integer endRow, String valueRenderOption)
			throws IOException, SheetToolException {
		String range = A1RangeBuilder.buildCoveringRange(tabTitle, columnLetters, startRow, endRow);
		int baseColumn = A1RangeBuilder.coveringRangeBaseColumn(columnLetters);
		List<List<Object>> rows = SheetValues.nullSafe(
			fetch(spreadsheetId, range, valueRenderOption, tabTitle));
		int baseRow = startRow == null ? 1 : startRow;
		return new RangeResult(range, rows, baseRow, baseColumn);
	}

	private List<List<Object>> fetch(String spreadsheetId, String range, String valueRenderOption,
			String tabTitle) throws IOException, SheetToolException {
		try {
			Sheets.Spreadsheets.Values.Get request =
				sheets.spreadsheets().values().get(spreadsheetId, range);
			if (valueRenderOption != null && !valueRenderOption.trim().isEmpty()) {
				request.setValueRenderOption(valueRenderOption.trim());
			}
			ValueRange response = request.execute();
			return response.getValues();
		} catch (GoogleJsonResponseException e) {
			throw translate(e, spreadsheetId, tabTitle);
		}
	}

	/**
	 * 把 Google 的 HTTP 錯誤翻成看得懂的訊息。
	 *
	 * <p>403 與 400 是這個工具最常見的兩種失敗，而它們的真正原因都不在錯誤訊息字面上。
	 */
	private SheetToolException translate(GoogleJsonResponseException e, String spreadsheetId,
			String tabTitle) {
		int status = e.getStatusCode();
		String detail = e.getDetails() == null ? e.getMessage() : e.getDetails().getMessage();
		if (status == 403) {
			// 403 有兩種完全不同的成因，指錯方向會讓人白找很久。
			// Google 的回應裡其實有講，只是夾在一長串英文中間，所以在這裡分流
			String lower = detail == null ? "" : detail.toLowerCase();
			boolean apiDisabled = lower.contains("has not been used in project")
				|| lower.contains("service_disabled")
				|| lower.contains("is disabled");
			if (apiDisabled) {
				return new SheetToolException("Google Sheets API 尚未啟用 (403): " + detail
					+ "\n   這不是共用權限問題 —— 是該 service account 所屬的 GCP 專案沒有開啟 Sheets API。"
					+ "\n   請到上面那個 console 連結按「啟用」，等 1~2 分鐘生效後再試", e);
			}
			String who = serviceAccountEmail == null || serviceAccountEmail.trim().isEmpty()
				? "check-auth 印出的 client_email" : serviceAccountEmail;
			return new SheetToolException("讀取被拒 (403): " + detail
				+ "\n   最常見原因是「試算表沒有共用給 service account」。"
				+ "\n   請把下面這個地址加為該試算表的「檢視者」:"
				+ "\n       " + who
				+ "\n   spreadsheetId: " + spreadsheetId
				+ "\n   若無法請擁有者加入，剩下的選項是把試算表設成「知道連結的任何人可檢視」"
				+ "（等於對外公開，不建議），或改用使用者 OAuth 授權", e);
		}
		if (status == 404) {
			return new SheetToolException("找不到試算表 (404): " + detail
				+ "\n   請確認 spreadsheetId 是否正確: " + spreadsheetId
				+ "\n   若該檔案是 Excel 原生格式（網址是 drive.google.com/file/...），"
				+ "Sheets API 讀不到，需要先轉成 Google 試算表", e);
		}
		if (status == 400 && tabTitle != null) {
			return new SheetToolException("範圍解析失敗 (400): " + detail
				+ "\n   最常見原因是分頁名對不上。請先跑 list-tabs 確認線上的分頁名稱，"
				+ "注意空白數量與全形／半形" + "\n   目前設定的分頁名: " + tabTitle, e);
		}
		return new SheetToolException("Google Sheets API 錯誤 (" + status + "): " + detail, e);
	}

	/** 讀取結果加上位置資訊，讓呼叫端能把相對 index 換算回絕對列／欄 */
	public static final class RangeResult {

		private final String a1Range;
		private final List<List<Object>> rows;
		private final int baseRow;
		private final int baseColumn;

		RangeResult(String a1Range, List<List<Object>> rows, int baseRow, int baseColumn) {
			this.a1Range = a1Range;
			this.rows = Collections.unmodifiableList(rows);
			this.baseRow = baseRow;
			this.baseColumn = baseColumn;
		}

		/** 實際送出的 A1 range，-t 模式會印出來 */
		public String getA1Range() {
			return a1Range;
		}

		public List<List<Object>> getRows() {
			return rows;
		}

		/** 第 0 列對應的試算表列號（1-based） */
		public int getBaseRow() {
			return baseRow;
		}

		/** 每列第 0 個元素對應的欄位絕對 index（A=0） */
		public int getBaseColumn() {
			return baseColumn;
		}
	}

	/** 把 {@link Spreadsheet} 的分頁清單攤平成好印的形式 */
	public static String describeTabs(Spreadsheet spreadsheet) {
		StringBuilder sb = new StringBuilder();
		List<Sheet> tabs = spreadsheet.getSheets();
		if (tabs == null || tabs.isEmpty()) {
			return "  (沒有任何分頁)";
		}
		for (Sheet tab : tabs) {
			sb.append(String.format("  [%2d] %-40s gid=%-12s %d 列 x %d 欄%n",
				tab.getProperties().getIndex(),
				tab.getProperties().getTitle(),
				String.valueOf(tab.getProperties().getSheetId()),
				tab.getProperties().getGridProperties() == null ? 0
					: tab.getProperties().getGridProperties().getRowCount(),
				tab.getProperties().getGridProperties() == null ? 0
					: tab.getProperties().getGridProperties().getColumnCount()));
		}
		return sb.toString();
	}
}
