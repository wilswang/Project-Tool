package tool.sheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SheetValues 單元測試
 *
 * <p>重點在 Sheets API 兩個一定會踩到的行為：values 為 null、以及每列尾端空白儲存格被截掉
 * 造成的破格列。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public class SheetValuesTest {

	@Test
	public void testNullSafe_NullBecomesEmptyList() {
		// 范围全空时 Sheets API 回 null 而不是空 List
		assertTrue(SheetValues.nullSafe(null).isEmpty());
	}

	@Test
	public void testNullSafe_NonNullPassedThrough() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.<Object>asList("a"));
		assertSame(rows, SheetValues.nullSafe(rows));
	}

	@Test
	public void testCell_NormalRead() {
		List<Object> row = Arrays.<Object>asList("a", "b", "c");
		assertEquals("a", SheetValues.cell(row, 0));
		assertEquals("c", SheetValues.cell(row, 2));
	}

	@Test
	public void testCell_RaggedRowReturnsEmptyInsteadOfThrowing() {
		// Sheets API 会截掉尾端空白储存格，所以短列取后面的栏位一定越界。
		// 这是读 backup 的 U~Y 时 100% 会遇到的情境
		List<Object> shortRow = Arrays.<Object>asList("a", "b");
		assertEquals("", SheetValues.cell(shortRow, 6));
	}

	@Test
	public void testCell_NullRowAndNegativeIndex() {
		assertEquals("", SheetValues.cell(null, 0));
		assertEquals("", SheetValues.cell(Arrays.<Object>asList("a"), -1));
	}

	@Test
	public void testCell_NullValueBecomesEmpty() {
		List<Object> row = Arrays.asList("a", null, "c");
		assertEquals("", SheetValues.cell(row, 1));
	}

	@Test
	public void testCell_NonStringValueConverted() {
		// UNFORMATTED_VALUE 会回 Double，虽然预设用 FORMATTED_VALUE，还是要能处理
		List<Object> row = Arrays.<Object>asList(Integer.valueOf(42), Double.valueOf(1.5));
		assertEquals("42", SheetValues.cell(row, 0));
		assertEquals("1.5", SheetValues.cell(row, 1));
	}

	@Test
	public void testProject_BaseColumnOffsetApplied() {
		// 涵盖范围从 H(7) 起算，要取 H(7) 与 S(18)：相对 index 分别是 0 与 11
		List<List<Object>> rows = new ArrayList<>();
		List<Object> row = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			row.add("col" + i);
		}
		rows.add(row);

		List<List<String>> projected = SheetValues.project(rows, Arrays.asList(7, 18), 7);

		assertEquals(1, projected.size());
		assertEquals("col0", projected.get(0).get(0));
		assertEquals("col11", projected.get(0).get(1));
	}

	@Test
	public void testProject_RaggedRowsFilledWithEmpty() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.<Object>asList("h", "i", "j"));
		rows.add(Arrays.<Object>asList("h2"));

		List<List<String>> projected = SheetValues.project(rows, Arrays.asList(7, 9), 7);

		assertEquals(Arrays.asList("h", "j"), projected.get(0));
		assertEquals(Arrays.asList("h2", ""), projected.get(1));
	}

	@Test
	public void testProject_NullRowsGivesEmptyResult() {
		assertTrue(SheetValues.project(null, Arrays.asList(0), 0).isEmpty());
	}
}
