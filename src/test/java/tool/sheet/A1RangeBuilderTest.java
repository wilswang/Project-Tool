package tool.sheet;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A1RangeBuilder 單元測試
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class A1RangeBuilderTest {

	@Test
	public void testColumnLetterToIndex_SingleAndMultiLetter() {
		// 准备测试数据：邊界與進位點
		// 执行与验证
		assertEquals(0, A1RangeBuilder.columnLetterToIndex("A"));
		assertEquals(7, A1RangeBuilder.columnLetterToIndex("H"));
		assertEquals(25, A1RangeBuilder.columnLetterToIndex("Z"));
		assertEquals(26, A1RangeBuilder.columnLetterToIndex("AA"));
		assertEquals(51, A1RangeBuilder.columnLetterToIndex("AZ"));
		assertEquals(52, A1RangeBuilder.columnLetterToIndex("BA"));
		assertEquals(701, A1RangeBuilder.columnLetterToIndex("ZZ"));
	}

	@Test
	public void testColumnLetterToIndex_LowerCaseAndWhitespaceAccepted() {
		assertEquals(7, A1RangeBuilder.columnLetterToIndex("h"));
		assertEquals(26, A1RangeBuilder.columnLetterToIndex(" aa "));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testColumnLetterToIndex_NullRejected() {
		A1RangeBuilder.columnLetterToIndex(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testColumnLetterToIndex_EmptyRejected() {
		A1RangeBuilder.columnLetterToIndex("  ");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testColumnLetterToIndex_NonLetterRejected() {
		A1RangeBuilder.columnLetterToIndex("A1");
	}

	@Test
	public void testIndexToColumnLetter_RoundTrip() {
		// 从 0 到 1000 全部 round-trip，一次釘死兩個方向的換算
		for (int i = 0; i <= 1000; i++) {
			String letter = A1RangeBuilder.indexToColumnLetter(i);
			assertEquals("index " + i + " round-trip 失敗", i, A1RangeBuilder.columnLetterToIndex(letter));
		}
		assertEquals("A", A1RangeBuilder.indexToColumnLetter(0));
		assertEquals("Z", A1RangeBuilder.indexToColumnLetter(25));
		assertEquals("AA", A1RangeBuilder.indexToColumnLetter(26));
		assertEquals("ZZ", A1RangeBuilder.indexToColumnLetter(701));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIndexToColumnLetter_NegativeRejected() {
		A1RangeBuilder.indexToColumnLetter(-1);
	}

	@Test
	public void testParseColumnSpec_SingleColumn() {
		assertEquals(Arrays.asList("H"), A1RangeBuilder.parseColumnSpec("H"));
	}

	@Test
	public void testParseColumnSpec_RangeWithDashAndColon() {
		assertEquals(Arrays.asList("J", "K"), A1RangeBuilder.parseColumnSpec("J-K"));
		assertEquals(Arrays.asList("U", "V"), A1RangeBuilder.parseColumnSpec("U:V"));
	}

	@Test
	public void testParseColumnSpec_MixedListPreservesOrder() {
		// 这是 groupInfo 实际会用到的形式
		assertEquals(Arrays.asList("B", "H", "J", "K", "M", "S", "U", "V", "W", "X", "Y"),
			A1RangeBuilder.parseColumnSpec("B,H,J-K,M,S,U-Y"));
	}

	@Test
	public void testParseColumnSpec_ReversedRangeNormalized() {
		// 写反了要自动正规化，不报错
		assertEquals(Arrays.asList("C", "D", "E", "F"), A1RangeBuilder.parseColumnSpec("F-C"));
	}

	@Test
	public void testParseColumnSpec_DuplicateColumnsDeduped() {
		// LinkedHashSet：保顺序、去重复
		assertEquals(Arrays.asList("H", "J", "K"), A1RangeBuilder.parseColumnSpec("H,J-K,H,K"));
	}

	@Test
	public void testParseColumnSpec_WhitespaceAndEmptySegmentsIgnored() {
		assertEquals(Arrays.asList("H", "S"), A1RangeBuilder.parseColumnSpec(" H , , S "));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseColumnSpec_NullRejected() {
		A1RangeBuilder.parseColumnSpec(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseColumnSpec_TooManyBoundsRejected() {
		A1RangeBuilder.parseColumnSpec("A-B-C");
	}

	@Test
	public void testQuoteSheetTitle_RealTabNameNeedsQuoting() {
		// 实际分页名含空白与中文，必须加引号，否则 range 会被 Sheets API 解析错
		assertEquals("'API 2.0Domain配置_20260814'",
			A1RangeBuilder.quoteSheetTitle("API 2.0Domain配置_20260814"));
	}

	@Test
	public void testQuoteSheetTitle_SafeTitleNotQuoted() {
		assertEquals("Sheet1", A1RangeBuilder.quoteSheetTitle("Sheet1"));
		assertEquals("my_tab", A1RangeBuilder.quoteSheetTitle("my_tab"));
	}

	@Test
	public void testQuoteSheetTitle_SingleQuoteDoubled() {
		assertEquals("'Bob''s tab'", A1RangeBuilder.quoteSheetTitle("Bob's tab"));
	}

	@Test
	public void testBuildCoveringRange_WithRowBounds() {
		List<String> columns = A1RangeBuilder.parseColumnSpec("H,J-K,M,S,U-Y");
		assertEquals("'API 2.0Domain配置_20260814'!H4:Y200",
			A1RangeBuilder.buildCoveringRange("API 2.0Domain配置_20260814", columns, 4, 200));
	}

	@Test
	public void testBuildCoveringRange_NullRowsMeansWholeColumns() {
		List<String> columns = A1RangeBuilder.parseColumnSpec("H,V");
		assertEquals("'My Tab'!H:V", A1RangeBuilder.buildCoveringRange("My Tab", columns, null, null));
	}

	@Test
	public void testBuildCoveringRange_OnlyStartRow() {
		assertEquals("Sheet1!H4:H", A1RangeBuilder.buildCoveringRange("Sheet1", Arrays.asList("H"), 4, null));
	}

	@Test
	public void testBuildCoveringRange_UnorderedColumnsStillSpanMinToMax() {
		// 欄位顺序不影响涵盖范围，min~max 就好
		assertEquals("Sheet1!B:Y",
			A1RangeBuilder.buildCoveringRange("Sheet1", Arrays.asList("S", "B", "Y", "H"), null, null));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuildCoveringRange_EndRowBeforeStartRowRejected() {
		A1RangeBuilder.buildCoveringRange("Sheet1", Arrays.asList("A"), 10, 5);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuildCoveringRange_ZeroStartRowRejected() {
		A1RangeBuilder.buildCoveringRange("Sheet1", Arrays.asList("A"), 0, null);
	}

	@Test
	public void testCoveringRangeBaseColumn() {
		// 投影时要用这个把绝对 index 换算成相对 index
		assertEquals(7, A1RangeBuilder.coveringRangeBaseColumn(Arrays.asList("S", "H", "Y")));
		assertEquals(1, A1RangeBuilder.coveringRangeBaseColumn(Arrays.asList("B", "H")));
	}
}
