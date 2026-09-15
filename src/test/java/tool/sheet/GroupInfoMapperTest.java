package tool.sheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import tool.sheet.SheetConfig.GroupInfoMapping;
import tool.whiteLabel.GroupInfo;

import static org.junit.Assert.*;

/**
 * GroupInfoMapper 單元測試
 *
 * <p>用手寫的假列資料驗證欄位對應與 backup 串接，完全不需要憑證或網路。
 * 區塊定位（locate）的測試待階段 3 確定規則後再補。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class GroupInfoMapperTest {

	/** 涵蓋範圍從 B(1) 起算，與實際會用的 range 一致 */
	private static final int BASE_COLUMN = 1;

	private static final List<String> EMPTY_MARKERS = Collections.singletonList("-");

	private GroupInfoMapping mapping() {
		GroupInfoMapping m = new GroupInfoMapping();
		m.setGroupCodeColumn("B");
		m.setPrivateIpSetIdColumn("H");
		m.setPrivateIpColumns(Arrays.asList("J", "K"));
		m.setBkIpSetIdColumn("M");
		m.setApiInfoBkIpSetIdColumn("S");
		m.setBackupColumns(Arrays.asList("U", "V", "W", "X", "Y"));
		m.setEmptyMarkers(EMPTY_MARKERS);
		return m;
	}

	/** 造一列資料，index 0 對應 B 欄 */
	private List<Object> row(String... cellsFromB) {
		List<Object> row = new ArrayList<>();
		Collections.addAll(row, cellsFromB);
		return row;
	}

	/** 把欄位字母換算成相對 index，讓測資好讀 */
	private int rel(String column) {
		return A1RangeBuilder.columnLetterToIndex(column) - BASE_COLUMN;
	}

	/** 造一列空白資料，再把指定欄位填值 */
	private List<Object> rowWith(String... columnAndValue) {
		List<Object> row = new ArrayList<>();
		for (int i = 0; i <= rel("Y"); i++) {
			row.add("");
		}
		for (int i = 0; i < columnAndValue.length; i += 2) {
			row.set(rel(columnAndValue[i]), columnAndValue[i + 1]);
		}
		return row;
	}

	// ---------- splitCell ----------

	@Test
	public void testSplitCell_NewlineSeparated() {
		// 分隔符实际是换行还是逗号尚未确认，两者都要吃
		List<String> values = GroupInfoMapper.splitCell("a.click\nb.click\nc.link", EMPTY_MARKERS);
		assertEquals(Arrays.asList("a.click", "b.click", "c.link"), values);
	}

	@Test
	public void testSplitCell_CommaSeparated() {
		assertEquals(Arrays.asList("a.click", "b.click"),
			GroupInfoMapper.splitCell("a.click, b.click", EMPTY_MARKERS));
	}

	@Test
	public void testSplitCell_MixedSeparatorsAndWhitespace() {
		assertEquals(Arrays.asList("a.click", "b.click", "c.link"),
			GroupInfoMapper.splitCell("  a.click ,\n b.click ;c.link  ", EMPTY_MARKERS));
	}

	@Test
	public void testSplitCell_SingleValue() {
		assertEquals(Arrays.asList("only.click"), GroupInfoMapper.splitCell("only.click", EMPTY_MARKERS));
	}

	@Test
	public void testSplitCell_DashMeansNoData() {
		// 使用者明确指定：cell 内为 "-" 判断为无资料
		assertTrue(GroupInfoMapper.splitCell("-", EMPTY_MARKERS).isEmpty());
		assertTrue(GroupInfoMapper.splitCell("  -  ", EMPTY_MARKERS).isEmpty());
	}

	@Test
	public void testSplitCell_DashAmongValuesDropped() {
		assertEquals(Arrays.asList("a.click", "b.click"),
			GroupInfoMapper.splitCell("a.click\n-\nb.click", EMPTY_MARKERS));
	}

	@Test
	public void testSplitCell_NullAndBlank() {
		assertTrue(GroupInfoMapper.splitCell(null, EMPTY_MARKERS).isEmpty());
		assertTrue(GroupInfoMapper.splitCell("", EMPTY_MARKERS).isEmpty());
		assertTrue(GroupInfoMapper.splitCell("   ", EMPTY_MARKERS).isEmpty());
	}

	@Test
	public void testSplitCell_NullEmptyMarkersTolerated() {
		assertEquals(Arrays.asList("a.click"), GroupInfoMapper.splitCell("a.click", null));
	}

	// ---------- map ----------

	@Test
	public void testMap_FieldColumnsGoToCorrectGroupInfoFields() throws Exception {
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("H", "priv-set-id", "J", "pra69x.xyz", "K", "pra69x.space",
			"M", "bk-ga-id", "S", "apiinfo-id"));
		block.add(rowWith("M", "bk-cf-id"));

		GroupInfo info = GroupInfoMapper.map(block, BASE_COLUMN, mapping()).getGroupInfo();

		assertEquals("priv-set-id", info.getPrivateIpSetId());
		assertEquals(Arrays.asList("pra69x.xyz", "pra69x.space"), info.getPrivateIp());
		assertEquals(Arrays.asList("bk-ga-id", "bk-cf-id"), info.getBkIpSetId());
		assertEquals("apiinfo-id", info.getApiInfoBkIpSetId());
	}

	@Test
	public void testMap_BackupTraversalIsColumnMajorThenRow() throws Exception {
		// 使用者指定的顺序：U1 → U2 → V1 → V2 → W1 → W2 → X1 → X2 → Y1 → Y2
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("U", "u1a\nu1b", "V", "v1a", "W", "w1a", "X", "x1a", "Y", "y1a"));
		block.add(rowWith("U", "u2a", "V", "v2a", "W", "w2a", "X", "x2a", "Y", "y2a"));

		GroupInfo info = GroupInfoMapper.map(block, BASE_COLUMN, mapping()).getGroupInfo();

		assertEquals(Arrays.asList(
			"u1a", "u1b", "u2a",
			"v1a", "v2a",
			"w1a", "w2a",
			"x1a", "x2a",
			"y1a", "y2a"), info.getBackup());
	}

	@Test
	public void testMap_BackupCellValueCountsVary() throws Exception {
		// api-2.0 表的实际形状：U1 有 5 个、U2 有 3 个、V1 有 5 个 → 13 个
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("U", "u1\nu2\nu3\nu4\nu5", "V", "v1\nv2\nv3\nv4\nv5"));
		block.add(rowWith("U", "u6\nu7\nu8", "V", "-"));

		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, mapping());

		assertEquals(13, result.getGroupInfo().getBackup().size());
		assertEquals("u1", result.getGroupInfo().getBackup().get(0));
		assertEquals("u6", result.getGroupInfo().getBackup().get(5));
		assertEquals("v1", result.getGroupInfo().getBackup().get(8));
		assertEquals("v5", result.getGroupInfo().getBackup().get(12));
	}

	@Test
	public void testMap_BackupSourcesRecordedForTestMode() throws Exception {
		// -t 模式要印出每一格的来源与数量，这是之后对不上时唯一能定位「哪一格切错」的资讯
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("U", "u1\nu2\nu3\nu4\nu5"));
		block.add(rowWith("U", "u6\nu7\nu8"));

		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, mapping());

		assertEquals(Arrays.asList("U1(5)", "U2(3)"), result.getBackupSources());
	}

	@Test
	public void testMap_AllDashBackupGivesEmptyList() throws Exception {
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("U", "-", "V", "-"));
		block.add(rowWith("U", "-", "V", "-"));

		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, mapping());

		assertTrue(result.getGroupInfo().getBackup().isEmpty());
		assertTrue(result.getBackupSources().isEmpty());
	}

	@Test
	public void testMap_NonUuidApiInfoBkIpSetIdReturnedWithWarning() throws Exception {
		// 已知会发生：A58 的 S 栏值是 "Cloudfront" 而不是 UUID。原样回传，不当错误
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("S", "Cloudfront"));

		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, mapping());

		assertEquals("Cloudfront", result.getGroupInfo().getApiInfoBkIpSetId());
		assertEquals(1, result.getWarnings().size());
		assertTrue(result.getWarnings().get(0).contains("Cloudfront"));
	}

	@Test
	public void testMap_ValidUuidProducesNoWarning() throws Exception {
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("S", "23e05854-b6c2-40d2-a472-2bdf64a265e0"));

		assertTrue(GroupInfoMapper.map(block, BASE_COLUMN, mapping()).getWarnings().isEmpty());
	}

	@Test
	public void testMap_RaggedRowsDoNotThrow() throws Exception {
		// Sheets API 截掉尾端空白储存格，短列取 U~Y 一定越界
		List<List<Object>> block = new ArrayList<>();
		block.add(row("A69", "", "", "", "", "", "priv-set-id"));
		block.add(row("", ""));

		GroupInfo info = GroupInfoMapper.map(block, BASE_COLUMN, mapping()).getGroupInfo();

		assertEquals("priv-set-id", info.getPrivateIpSetId());
		assertTrue(info.getBackup().isEmpty());
		assertTrue(info.getPrivateIp().isEmpty());
	}

	@Test
	public void testMap_SingleRowBlock() throws Exception {
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("H", "h", "M", "m", "U", "u1\nu2"));

		GroupInfo info = GroupInfoMapper.map(block, BASE_COLUMN, mapping()).getGroupInfo();

		assertEquals("h", info.getPrivateIpSetId());
		assertEquals(Arrays.asList("m"), info.getBkIpSetId());
		assertEquals(Arrays.asList("u1", "u2"), info.getBackup());
	}

	// ---------- locate ----------

	/** 造一片資料：每個元素是 {群組代號, 標記} —— 只需要 B 欄就能測定位 */
	private List<List<Object>> sheet(String... groupCodes) {
		List<List<Object>> rows = new ArrayList<>();
		for (String code : groupCodes) {
			rows.add(rowWith("B", code));
		}
		return rows;
	}

	@Test
	public void testLocate_ContiguousTwoRowBlock() throws Exception {
		// 实测的常见形状：B 栏在群组的每一列都有值，代号排列跳号
		List<List<Object>> rows = sheet("A04", "A04", "A05", "A05", "A07", "A07");

		GroupInfoMapper.GroupBlock block =
			GroupInfoMapper.locate(rows, 4, BASE_COLUMN, "A05", mapping());

		assertEquals("A05", block.getGroupCode());
		assertEquals(2, block.size());
		assertEquals(6, block.getStartRow());
		assertEquals(7, block.getEndRow());
	}

	@Test
	public void testLocate_SingleRowBlock() throws Exception {
		// 实测有三个单列群组：A17 / A18 / A22
		List<List<Object>> rows = sheet("A16", "A16", "A17", "A18", "A19", "A19");

		GroupInfoMapper.GroupBlock block =
			GroupInfoMapper.locate(rows, 24, BASE_COLUMN, "A17", mapping());

		assertEquals(1, block.size());
		assertEquals(26, block.getStartRow());
		assertEquals(26, block.getEndRow());
	}

	@Test
	public void testLocate_ThreeRowBlock() throws Exception {
		// 实测 A56 有三列
		List<List<Object>> rows = sheet("A55", "A55", "A56", "A56", "A56", "A57");

		assertEquals(3, GroupInfoMapper.locate(rows, 100, BASE_COLUMN, "A56", mapping()).size());
	}

	@Test
	public void testLocate_BlockAtLastRow() throws Exception {
		List<List<Object>> rows = sheet("A68", "A68", "A69", "A69");

		GroupInfoMapper.GroupBlock block =
			GroupInfoMapper.locate(rows, 134, BASE_COLUMN, "A69", mapping());

		assertEquals(2, block.size());
		assertEquals(137, block.getEndRow());
	}

	@Test
	public void testLocate_CaseInsensitiveAndTrimmed() throws Exception {
		List<List<Object>> rows = sheet("A68", " A69 ", "A69");

		assertEquals(2, GroupInfoMapper.locate(rows, 1, BASE_COLUMN, "a69", mapping()).size());
	}

	@Test
	public void testLocate_NotFoundReportsSearchedRange() {
		List<List<Object>> rows = sheet("A61", "A61", "A62", "A62");

		try {
			GroupInfoMapper.locate(rows, 120, BASE_COLUMN, "A99", mapping());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("A99"));
			assertTrue("要告訴使用者搜尋了哪些列", e.getMessage().contains("120"));
			assertTrue(e.getMessage().contains("123"));
		}
	}

	@Test
	public void testLocate_NonAdjacentDuplicateBlocksRejected() {
		// 实测 79 个区块里只有 B01 出现在两个不相邻的位置，要明确报错而不是默默取第一个
		List<List<Object>> rows = sheet("B01", "B01", "B02", "B02", "B01", "B01");

		try {
			GroupInfoMapper.locate(rows, 151, BASE_COLUMN, "B01", mapping());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("2 個不相鄰"));
			assertTrue("要列出兩個區塊的列號", e.getMessage().contains("151"));
			assertTrue(e.getMessage().contains("155"));
		}
	}

	@Test
	public void testLocate_ExceedingMaxRowsPerGroupRejected() {
		List<List<Object>> rows = sheet("C01", "C01", "C01", "C01", "C01", "C01");

		try {
			GroupInfoMapper.locate(rows, 189, BASE_COLUMN, "C01", mapping());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("maxRowsPerGroup"));
			assertTrue(e.getMessage().contains("6 列"));
		}
	}

	@Test(expected = SheetToolException.class)
	public void testLocate_BlankGroupCodeRejected() throws Exception {
		GroupInfoMapper.locate(sheet("A61"), 1, BASE_COLUMN, "  ", mapping());
	}

	@Test
	public void testLocate_ThenMapWorksEndToEnd() throws Exception {
		// locate → map 的接線，模擬 A69 的實際形狀
		List<List<Object>> rows = new ArrayList<>();
		rows.add(rowWith("B", "A68"));
		rows.add(rowWith("B", "A68"));
		rows.add(rowWith("B", "A69", "H", "priv-id", "J", "pra69x.xyz", "K", "pra69x.space",
			"M", "ga-id", "S", "apiinfo-id", "U", "b1\nb2\nb3\nb4\nb5", "V", "-", "W", "-"));
		rows.add(rowWith("B", "A69", "M", "cf-id", "U", "b6\nb7\nb8", "V", "-", "W", "-"));

		GroupInfoMapper.GroupBlock block =
			GroupInfoMapper.locate(rows, 134, BASE_COLUMN, "A69", mapping());
		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, mapping());
		GroupInfo info = result.getGroupInfo();

		assertEquals(136, block.getStartRow());
		assertEquals("priv-id", info.getPrivateIpSetId());
		assertEquals(Arrays.asList("ga-id", "cf-id"), info.getBkIpSetId());
		assertEquals(8, info.getBackup().size());
		assertEquals(Arrays.asList("U1(5)", "U2(3)"), result.getBackupSources());
	}

	// ---------- WAF Name 交叉驗證 ----------

	@Test
	public void testMap_WafNameMismatchWarns() throws Exception {
		GroupInfoMapping m = mapping();
		m.setBkIpSetIdWafNameColumn("N");
		List<List<Object>> block = new ArrayList<>();
		// 顺序反了：第 1 列是 CF、第 2 列是 GA
		block.add(rowWith("M", "cf-id", "N", "A69-BK-WEB-CF-RU3"));
		block.add(rowWith("M", "ga-id", "N", "A69-BK-WEB-GA-RU3"));

		GroupInfoMapper.MapResult result = GroupInfoMapper.map(block, BASE_COLUMN, m);

		assertEquals(2, result.getWarnings().size());
		assertTrue(result.getWarnings().get(0).contains("-GA-"));
	}

	@Test
	public void testMap_WafNameUnderscoreVariantAccepted() throws Exception {
		// 实测两种分隔符并存：A69-BK-WEB-GA-RU3 与 A04_BK_WEB_GA_RU2
		GroupInfoMapping m = mapping();
		m.setBkIpSetIdWafNameColumn("N");
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("M", "ga-id", "N", "A04_BK_WEB_GA_RU2"));
		block.add(rowWith("M", "cf-id", "N", "A04_BK_WEB_CF_RU2"));

		assertTrue(GroupInfoMapper.map(block, BASE_COLUMN, m).getWarnings().isEmpty());
	}

	@Test(expected = SheetToolException.class)
	public void testMap_EmptyBlockRejected() throws Exception {
		GroupInfoMapper.map(new ArrayList<List<Object>>(), BASE_COLUMN, mapping());
	}

	@Test(expected = SheetToolException.class)
	public void testMap_NullMappingRejected() throws Exception {
		List<List<Object>> block = new ArrayList<>();
		block.add(rowWith("H", "h"));
		GroupInfoMapper.map(block, BASE_COLUMN, null);
	}
}
