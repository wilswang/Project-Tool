package tool.sheet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tool.whiteLabel.GroupInfo;

import static org.junit.Assert.*;

/**
 * GroupInfoPatcher 單元測試
 *
 * <p>一律用合成 JSON，不拿既有單子當測資 —— 那些是已上線的歷史資料，
 * 測起來反映的是當時的狀態，不是流程行為。
 *
 * @author Wilson.Wang
 * @version 1.6.0
 */
public class GroupInfoPatcherTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private final ObjectMapper mapper = new ObjectMapper();

	/** 試算表查到的值 */
	private GroupInfo sheetValues() {
		GroupInfo info = new GroupInfo();
		info.setPrivateIpSetId("cfc220fd-9387-4a58-af2e-259a7c8b4839");
		info.setPrivateIp(Arrays.asList("pra69x.xyz", "pra69x.space"));
		info.setBkIpSetId(Arrays.asList("145fe3d4-ga", "a5f5de02-cf"));
		info.setApiInfoBkIpSetId("23e05854-b6c2-40d2-a472-2bdf64a265e0");
		info.setBackup(Arrays.asList("b1.click", "b2.click", "b3.link"));
		return info;
	}

	/** mapping rule 產出的字面假值，也就是 step 2 目前實際會寫出來的形狀 */
	private String ticketWithPlaceholders() {
		return "{\n"
			+ "  \"ticketNo\": \"1402\",\n"
			+ "  \"webSiteName\": \"ROYALBETHD\",\n"
			+ "  \"apiWalletInfo\": {\n"
			+ "    \"cert\": \"K0hlRSgqJosrJ24n\",\n"
			+ "    \"newGroup\": true,\n"
			+ "    \"group\": \"A69\",\n"
			+ "    \"groupInfo\": {\n"
			+ "      \"privateIpSetId\": \"privateIpSetId\",\n"
			+ "      \"privateIp\": [\"pra69x.xyz\", \"pra69x.space\"],\n"
			+ "      \"bkIpSetId\": [\"bkIpSetId1\", \"bkIpSetId2\"],\n"
			+ "      \"apiInfoBkIpSetId\": \"apiInfoBkIpSetId\",\n"
			+ "      \"backup\": [\"backup1\", \"backup2\", \"backup3\", \"backup4\", \"backup5\", \"backup6\"]\n"
			+ "    }\n"
			+ "  },\n"
			+ "  \"envValues\": { \"UAT\": { \"apiHost\": \"https://example/api\" } },\n"
			+ "  \"files\": [ { \"name\": \"x.sql\" } ]\n"
			+ "}";
	}

	private File writeTicket(String content) throws IOException {
		File file = temporaryFolder.newFile("SACRIC-9999.json");
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private JsonNode groupInfoOf(File file) throws IOException {
		return mapper.readTree(file).path("apiWalletInfo").path("groupInfo");
	}

	// ---------- isUnfilled ----------

	@Test
	public void testIsUnfilled_NullAndMissing() {
		assertTrue(GroupInfoPatcher.isUnfilled("privateIpSetId", null));
		assertTrue(GroupInfoPatcher.isUnfilled("privateIpSetId", mapper.nullNode()));
		assertTrue(GroupInfoPatcher.isUnfilled("backup", mapper.createObjectNode().path("nope")));
	}

	@Test
	public void testIsUnfilled_EmptyStringAndArray() {
		assertTrue(GroupInfoPatcher.isUnfilled("privateIpSetId", mapper.getNodeFactory().textNode("")));
		assertTrue(GroupInfoPatcher.isUnfilled("privateIpSetId", mapper.getNodeFactory().textNode("   ")));
		assertTrue(GroupInfoPatcher.isUnfilled("backup", mapper.createArrayNode()));
	}

	@Test
	public void testIsUnfilled_MappingRulePlaceholders() {
		// 这些就是 white-label-mapping-rule.md 目前叫 LLM 产出的预设值
		assertTrue(GroupInfoPatcher.isUnfilled("privateIpSetId",
			mapper.getNodeFactory().textNode("privateIpSetId")));
		assertTrue(GroupInfoPatcher.isUnfilled("apiInfoBkIpSetId",
			mapper.getNodeFactory().textNode("apiInfoBkIpSetId")));
		assertTrue(GroupInfoPatcher.isUnfilled("bkIpSetId",
			mapper.createArrayNode().add("bkIpSetId1").add("bkIpSetId2")));
		assertTrue(GroupInfoPatcher.isUnfilled("backup",
			mapper.createArrayNode().add("backup1").add("backup2").add("backup6")));
	}

	@Test
	public void testIsUnfilled_RealValuesAreFilled() {
		assertFalse(GroupInfoPatcher.isUnfilled("privateIpSetId",
			mapper.getNodeFactory().textNode("cfc220fd-9387-4a58-af2e-259a7c8b4839")));
		assertFalse(GroupInfoPatcher.isUnfilled("backup",
			mapper.createArrayNode().add("woxdjsqwub.click")));
	}

	@Test
	public void testIsUnfilled_PartiallyRealArrayCountsAsFilled() {
		// 只要有一个元素是真值就不算「没填」—— 半填的状态要报冲突而不是默默盖掉
		assertFalse(GroupInfoPatcher.isUnfilled("backup",
			mapper.createArrayNode().add("backup1").add("real.click")));
	}

	// ---------- merge / patchFile ----------

	@Test
	public void testPatchFile_PlaceholdersReplaced() throws Exception {
		// 准备测试数据：step 2 产出的字面假值
		File ticket = writeTicket(ticketWithPlaceholders());

		// 执行
		GroupInfoPatcher.PatchResult result = GroupInfoPatcher.patchFile(ticket, sheetValues());

		// 验证：四个假值栏位被填，privateIp 本来就是真值所以不动
		assertEquals(Arrays.asList("privateIpSetId", "bkIpSetId", "apiInfoBkIpSetId", "backup"),
			result.getFilled());
		assertEquals(Arrays.asList("privateIp"), result.getUnchanged());
		assertTrue(result.getConflicts().isEmpty());

		JsonNode groupInfo = groupInfoOf(ticket);
		assertEquals("cfc220fd-9387-4a58-af2e-259a7c8b4839", groupInfo.get("privateIpSetId").asText());
		assertEquals("23e05854-b6c2-40d2-a472-2bdf64a265e0", groupInfo.get("apiInfoBkIpSetId").asText());
		assertEquals(2, groupInfo.get("bkIpSetId").size());
		assertEquals(3, groupInfo.get("backup").size());
		assertEquals("b1.click", groupInfo.get("backup").get(0).asText());
	}

	@Test
	public void testPatchFile_MissingGroupInfoIsCreated() throws Exception {
		// 新规则会叫 LLM 输出 null，所以 groupInfo 可能整个不存在
		File ticket = writeTicket("{\n"
			+ "  \"ticketNo\": \"9999\",\n"
			+ "  \"apiWalletInfo\": { \"newGroup\": true, \"group\": \"A69\" }\n"
			+ "}");

		GroupInfoPatcher.PatchResult result = GroupInfoPatcher.patchFile(ticket, sheetValues());

		assertEquals(5, result.getFilled().size());
		JsonNode groupInfo = groupInfoOf(ticket);
		assertFalse(groupInfo.isMissingNode());
		assertEquals("pra69x.xyz", groupInfo.get("privateIp").get(0).asText());
	}

	@Test
	public void testPatchFile_NullGroupInfoIsCreated() throws Exception {
		File ticket = writeTicket("{\n"
			+ "  \"apiWalletInfo\": { \"newGroup\": true, \"group\": \"A69\", \"groupInfo\": null }\n"
			+ "}");

		assertEquals(5, GroupInfoPatcher.patchFile(ticket, sheetValues()).getFilled().size());
		assertEquals(3, groupInfoOf(ticket).get("backup").size());
	}

	@Test
	public void testPatchFile_AlreadyCorrectIsUntouched() throws Exception {
		// -s 3 重跑会走到这里
		File ticket = writeTicket(ticketWithPlaceholders());
		GroupInfoPatcher.patchFile(ticket, sheetValues());
		byte[] afterFirst = Files.readAllBytes(ticket.toPath());

		GroupInfoPatcher.PatchResult second = GroupInfoPatcher.patchFile(ticket, sheetValues());

		assertTrue("第二次不該再填任何欄位", second.getFilled().isEmpty());
		assertEquals(5, second.getUnchanged().size());
		assertTrue(second.getConflicts().isEmpty());
		assertArrayEquals("第二次不該改動檔案", afterFirst, Files.readAllBytes(ticket.toPath()));
	}

	@Test
	public void testPatchFile_ConflictIsReportedAndNotOverwritten() throws Exception {
		File ticket = writeTicket(ticketWithPlaceholders()
			.replace("\"apiInfoBkIpSetId\": \"apiInfoBkIpSetId\"",
				"\"apiInfoBkIpSetId\": \"hand-edited-value\""));

		GroupInfoPatcher.PatchResult result = GroupInfoPatcher.patchFile(ticket, sheetValues());

		assertEquals(1, result.getConflicts().size());
		GroupInfoPatcher.Conflict conflict = result.getConflicts().get(0);
		assertEquals("apiInfoBkIpSetId", conflict.getField());
		assertTrue(conflict.getTicketValue().contains("hand-edited-value"));
		assertTrue(conflict.getSheetValue().contains("23e05854"));
		assertEquals("單子的值不可被覆寫", "hand-edited-value",
			groupInfoOf(ticket).get("apiInfoBkIpSetId").asText());
	}

	@Test
	public void testPatchFile_NoFilledFieldsLeavesFileByteIdentical() throws Exception {
		// 只有冲突、没有任何栏位被填 → 完全不要动档案，避免只因为格式化而产生改动
		String original = "{\n"
			+ "  \"apiWalletInfo\": {\n"
			+ "    \"groupInfo\": {\n"
			+ "      \"privateIpSetId\": \"x\",\n"
			+ "      \"privateIp\": [\"a\"],\n"
			+ "      \"bkIpSetId\": [\"b\"],\n"
			+ "      \"apiInfoBkIpSetId\": \"c\",\n"
			+ "      \"backup\": [\"d\"]\n"
			+ "    }\n"
			+ "  }\n"
			+ "}";
		File ticket = writeTicket(original);

		GroupInfoPatcher.PatchResult result = GroupInfoPatcher.patchFile(ticket, sheetValues());

		assertEquals(5, result.getConflicts().size());
		assertTrue(result.getFilled().isEmpty());
		assertEquals(original, new String(Files.readAllBytes(ticket.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void testPatchFile_OtherFieldsPreserved() throws Exception {
		File ticket = writeTicket(ticketWithPlaceholders());

		GroupInfoPatcher.patchFile(ticket, sheetValues());

		JsonNode root = mapper.readTree(ticket);
		assertEquals("1402", root.get("ticketNo").asText());
		assertEquals("ROYALBETHD", root.get("webSiteName").asText());
		assertEquals("K0hlRSgqJosrJ24n", root.path("apiWalletInfo").get("cert").asText());
		assertTrue(root.path("apiWalletInfo").get("newGroup").asBoolean());
		assertEquals("A69", root.path("apiWalletInfo").get("group").asText());
		assertEquals("https://example/api",
			root.path("envValues").path("UAT").get("apiHost").asText());
		assertEquals(1, root.get("files").size());
	}

	@Test
	public void testPatchFile_FieldOrderMatchesTicketConvention() throws Exception {
		File ticket = writeTicket("{\n"
			+ "  \"apiWalletInfo\": { \"groupInfo\": { \"backup\": null, \"privateIpSetId\": null } }\n"
			+ "}");

		GroupInfoPatcher.patchFile(ticket, sheetValues());

		StringBuilder order = new StringBuilder();
		java.util.Iterator<String> names = groupInfoOf(ticket).fieldNames();
		while (names.hasNext()) {
			order.append(names.next()).append(' ');
		}
		assertEquals("privateIpSetId privateIp bkIpSetId apiInfoBkIpSetId backup ", order.toString());
	}

	@Test
	public void testPatchFile_WrittenInJqStyle() throws Exception {
		// step 2 是用 `jq .` 产生这个档的，patch 之后格式不该变样
		File ticket = writeTicket(ticketWithPlaceholders());

		GroupInfoPatcher.patchFile(ticket, sheetValues());
		String written = new String(Files.readAllBytes(ticket.toPath()), StandardCharsets.UTF_8);

		assertTrue("欄位名與值之間不該有冒號前空格", written.contains("\"ticketNo\": \"1402\""));
		assertFalse(written.contains("\"ticketNo\" :"));
		assertTrue("陣列要逐行展開", written.contains("\"backup\": [\n"));
		assertTrue("頂層縮排 2 空格", written.contains("\n  \"ticketNo\""));
		assertTrue("檔尾要有換行", written.endsWith("\n"));
	}

	@Test
	public void testPatchFile_MissingFileRejected() {
		try {
			GroupInfoPatcher.patchFile(new File(temporaryFolder.getRoot(), "nope.json"), sheetValues());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("不存在"));
		}
	}

	@Test
	public void testPatchFile_NoApiWalletInfoRejected() throws Exception {
		File ticket = writeTicket("{ \"ticketNo\": \"9999\" }");

		try {
			GroupInfoPatcher.patchFile(ticket, sheetValues());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("apiWalletInfo"));
		}
	}

	@Test
	public void testPatchFile_MalformedJsonRejected() throws Exception {
		File ticket = writeTicket("{ this is not json");

		try {
			GroupInfoPatcher.patchFile(ticket, sheetValues());
			fail("應該要丟 SheetToolException");
		} catch (SheetToolException e) {
			assertTrue(e.getMessage().contains("解析失敗"));
			assertTrue(e.getMessage().contains(ticket.getAbsolutePath()));
		}
	}

	@Test
	public void testPatchFile_NoTempFileLeftBehind() throws Exception {
		File ticket = writeTicket(ticketWithPlaceholders());

		GroupInfoPatcher.patchFile(ticket, sheetValues());

		assertFalse(new File(ticket.getAbsolutePath() + ".patch.tmp").exists());
	}
}
