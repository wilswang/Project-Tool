package tool.sheet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * {@code config/sheet-config.json} 的對應物件。
 *
 * <p>與 {@code WhiteLabelConfig} / {@code UrlCheckerConfig} 的一個刻意差異：
 * {@link #validateForRead(String)} <b>丟例外而不是 {@code System.exit(1)}</b>。
 * 把 exit 寫在 config 類別裡會讓它完全無法單元測試（{@code UrlCheckerConfig} 至今沒有測試
 * 就是這個原因），所以退出碼一律交給 {@link SheetTool#main(String[])} 負責。
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 是必要的：設定檔裡放了
 * {@code _comment} 這類註解欄位，不能讓它炸掉解析。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SheetConfig {

	/** 憑證路徑，CWD 相對。CLI 的 --credential 可覆寫 */
	private String credentialPath = "./config/service-account.json";

	/** 送給 Google API 的 application name，只影響對方的 log */
	private String applicationName = "Project-Tool Sheet Reader";

	/** 沒指定 --target 時用哪一個 */
	private String defaultTarget;

	/** 具名的讀取目標。用字典而不是單一目標，之後加第二張表不用改結構 */
	private Map<String, Target> targets = new LinkedHashMap<>();

	/**
	 * 取出指定的 target；name 為 null 時用 {@link #defaultTarget}。
	 *
	 * @throws SheetToolException target 不存在，或沒給 name 又沒設 defaultTarget
	 */
	public Target requireTarget(String name) throws SheetToolException {
		String key = (name == null || name.trim().isEmpty()) ? defaultTarget : name.trim();
		if (key == null || key.trim().isEmpty()) {
			throw new SheetToolException("未指定 target，且設定檔沒有 defaultTarget。"
				+ "可用的 target: " + targets.keySet());
		}
		Target target = targets.get(key);
		if (target == null) {
			throw new SheetToolException("找不到 target '" + key + "'。可用的 target: " + targets.keySet());
		}
		return target;
	}

	/**
	 * 讀取類指令（list-tabs / read-tab / read-columns / group-info）之前的驗證。
	 *
	 * <p>刻意<b>不做全域驗證</b>：{@code check-auth} 不需要任何 target，
	 * 所以 spreadsheetId 留空不能讓整份設定檔驗證失敗。
	 */
	public void validateForRead(String targetName) throws SheetToolException {
		if (credentialPath == null || credentialPath.trim().isEmpty()) {
			throw new SheetToolException("credentialPath 不可為空");
		}
		requireTarget(targetName).validate(targetName == null ? defaultTarget : targetName);
	}

	/** 單一讀取目標 */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Target {

		/** 試算表 ID。留空表示還沒設定，讀取類指令會擋下 */
		private String spreadsheetId;

		/** 分頁名，要與線上逐字相符（含空白與全半形） */
		private String tab;

		/** 1-based inclusive，null 表不設下限 */
		private Integer startRow;

		/** 1-based inclusive，null 表不設上限 */
		private Integer endRow;

		/**
		 * 預設 FORMATTED_VALUE。UNFORMATTED_VALUE 會把數字回成 Double（1.0E10 這種形式），
		 * 而這個工具要的就是人在畫面上看到的字。
		 */
		private String valueRenderOption = "FORMATTED_VALUE";

		/** groupInfo 對應用的欄位設定 */
		private GroupInfoMapping groupInfo;

		public void validate(String targetName) throws SheetToolException {
			String label = "target '" + targetName + "'";
			if (spreadsheetId == null || spreadsheetId.trim().isEmpty()) {
				throw new SheetToolException(label + " 的 spreadsheetId 尚未設定。"
					+ "請填入 config/sheet-config.json，或用 --spreadsheetId 指定");
			}
			if (tab == null || tab.trim().isEmpty()) {
				throw new SheetToolException(label + " 的 tab（分頁名）不可為空");
			}
			if (startRow != null && startRow < 1) {
				throw new SheetToolException(label + " 的 startRow 必須 >= 1: " + startRow);
			}
			if (endRow != null && endRow < 1) {
				throw new SheetToolException(label + " 的 endRow 必須 >= 1: " + endRow);
			}
			if (startRow != null && endRow != null && endRow < startRow) {
				throw new SheetToolException(label + " 的 endRow 不可小於 startRow: " + startRow + ":" + endRow);
			}
		}
	}

	/**
	 * groupInfo 的欄位對應。
	 *
	 * <p>這裡<b>只放欄位字母</b>。backup 的「欄優先、欄內依列」走訪順序與 cell 內的切分規則
	 * 放在 {@link GroupInfoMapper} —— 那是演算法而不是設定，塞進 JSON 只會變成一個很難讀的 schema。
	 * 欄位字母搬動是最常見的變化，走訪規則變動則幾乎等於試算表改版（那本來就該改 code）。
	 */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GroupInfoMapping {

		/** 群組代號所在的欄。值不連續（只出現在群組的某些列），區塊判定規則見 GroupInfoMapper */
		private String groupCodeColumn = "B";

		/** 防護上限：區塊判定出錯時不要一路吃到下一個群組的資料 */
		private int maxRowsPerGroup = 5;

		private String privateIpSetIdColumn = "H";

		private List<String> privateIpColumns;

		private String bkIpSetIdColumn = "M";

		/**
		 * 選用：www BK 的 WAF Name 欄。設了就會驗證第 1 列是 GA、第 2 列是 CF，不符只警告不擋。
		 * 名稱分隔符不一致（A69-BK-WEB-GA-RU3 vs A04_BK_WEB_GA_RU2），比對時要正規化。
		 */
		private String bkIpSetIdWafNameColumn;

		private String apiInfoBkIpSetIdColumn = "S";

		private List<String> backupColumns;

		/** cell 內容等於這些值時視為無資料，整格跳過。試算表用 "-" 表示沒有 */
		private List<String> emptyMarkers;

		public void validate(String label) throws SheetToolException {
			requireNotBlank(label, "groupCodeColumn", groupCodeColumn);
			requireNotBlank(label, "privateIpSetIdColumn", privateIpSetIdColumn);
			requireNotBlank(label, "bkIpSetIdColumn", bkIpSetIdColumn);
			requireNotBlank(label, "apiInfoBkIpSetIdColumn", apiInfoBkIpSetIdColumn);
			requireNotEmpty(label, "privateIpColumns", privateIpColumns);
			requireNotEmpty(label, "backupColumns", backupColumns);
			if (maxRowsPerGroup < 1) {
				throw new SheetToolException(label + " 的 maxRowsPerGroup 必須 >= 1: " + maxRowsPerGroup);
			}
		}

		private static void requireNotBlank(String label, String field, String value) throws SheetToolException {
			if (value == null || value.trim().isEmpty()) {
				throw new SheetToolException(label + " 的 groupInfo." + field + " 不可為空");
			}
		}

		private static void requireNotEmpty(String label, String field, List<String> value) throws SheetToolException {
			if (value == null || value.isEmpty()) {
				throw new SheetToolException(label + " 的 groupInfo." + field + " 不可為空");
			}
		}
	}
}
