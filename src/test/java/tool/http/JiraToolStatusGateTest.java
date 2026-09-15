package tool.http;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import tool.http.JiraTool.StatusGate;

/**
 * {@link JiraTool#decideStatusGate(String)} 的測試。
 *
 * <p>只測這個方法是因為 {@code startJiraIssue} 的其餘部分都要 {@code JiraClient} 與網路；
 * 閘門的判斷本身是純邏輯，抽出來就能單獨驗。
 *
 * @author Wilson.Wang
 * @version 1.5.1
 */
public class JiraToolStatusGateTest {

	@Test
	// Ready to DEV：轉成 IN DEV 之後才抓取
	public void testReadyToDevTransitionsThenFetches() {
		assertEquals(StatusGate.TRANSITION_THEN_FETCH, JiraTool.decideStatusGate("Ready to DEV"));
	}

	@Test
	// IN DEV：不重複轉態，直接重新抓取（-s 1 重跑的情境）
	public void testInDevFetchesOnly() {
		assertEquals(StatusGate.FETCH_ONLY, JiraTool.decideStatusGate("IN DEV"));
	}

	@Test
	// 狀態比對不分大小寫
	public void testComparisonIsCaseInsensitive() {
		assertEquals(StatusGate.TRANSITION_THEN_FETCH, JiraTool.decideStatusGate("ready to dev"));
		assertEquals(StatusGate.TRANSITION_THEN_FETCH, JiraTool.decideStatusGate("READY TO DEV"));
		assertEquals(StatusGate.FETCH_ONLY, JiraTool.decideStatusGate("in dev"));
		assertEquals(StatusGate.FETCH_ONLY, JiraTool.decideStatusGate("In Dev"));
	}

	@Test
	// 前後空白不影響判斷
	public void testSurroundingWhitespaceIsIgnored() {
		assertEquals(StatusGate.TRANSITION_THEN_FETCH, JiraTool.decideStatusGate("  Ready to DEV  "));
		assertEquals(StatusGate.FETCH_ONLY, JiraTool.decideStatusGate("\tIN DEV\n"));
	}

	@Test
	// 其他工作流程狀態一律中止
	public void testOtherWorkflowStatusesAbort() {
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("Not Start Yet"));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("IN ANALYSIS"));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("DEV DONE"));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("Resolved"));
	}

	@Test
	// 未知狀態中止 —— 白名單而不是黑名單
	public void testUnknownStatusAborts() {
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("Waiting for QA"));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("Closed"));
	}

	@Test
	// null 與空字串中止，不會 NPE
	public void testNullOrBlankAborts() {
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate(null));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate(""));
		assertEquals(StatusGate.ABORT, JiraTool.decideStatusGate("   "));
	}

	@Test
	// 放行的狀態名稱與 JiraTransitionId 同源，不是各寫一份字串
	public void testAcceptedNamesComeFromTheEnum() {
		assertEquals(StatusGate.TRANSITION_THEN_FETCH,
			JiraTool.decideStatusGate(JiraTransitionId.REJECT.getResult()));
		assertEquals(StatusGate.FETCH_ONLY,
			JiraTool.decideStatusGate(JiraTransitionId.TO_DEV.getResult()));
	}
}
