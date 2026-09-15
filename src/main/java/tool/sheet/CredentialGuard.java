package tool.sheet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 憑證檔的安全防呆。
 *
 * <p>存在的理由很具體：本專案已經有過「部署端的 {@code application.properties} 帶著真實
 * Jira token 被 commit 進版控」的前例。{@code .gitignore} 對<b>已追蹤</b>的檔案是無效的，
 * 所以光加 ignore 規則擋不住重演 —— 真正擋得住的是啟動時主動檢查。
 *
 * <p>三道檢查：
 * <ol>
 *   <li><b>內容嗅探</b>：不是 service account 金鑰就明確報錯，避免使用者放成 OAuth client
 *       secret 之後拿到一堆看不懂的 Google 例外</li>
 *   <li><b>git 追蹤檢查</b>：已被追蹤就擋下（這是唯一真正防得住誤 commit 的機制）</li>
 *   <li><b>權限檢查</b>：group/other 可讀就警告，但不擋</li>
 * </ol>
 *
 * <p>{@link Summary} <b>結構上不持有</b> {@code private_key}，杜絕誤印。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class CredentialGuard {

	private static final String EXPECTED_TYPE = "service_account";

	private CredentialGuard() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * 跑完三道檢查並回傳可安全輸出的摘要。
	 *
	 * @throws SheetToolException 內容不是 service account 金鑰，或該檔已被 git 追蹤
	 */
	public static Summary inspect(File credentialFile) throws SheetToolException {
		Summary summary = readSummary(credentialFile);
		requireNotGitTracked(credentialFile);
		warnIfPermissionsTooOpen(credentialFile);
		return summary;
	}

	/** 只取非敏感欄位，private_key 完全不進記憶體以外的地方 */
	static Summary readSummary(File credentialFile) throws SheetToolException {
		if (credentialFile == null || !credentialFile.isFile()) {
			throw new SheetToolException("憑證檔不存在: "
				+ (credentialFile == null ? "null" : credentialFile.getAbsolutePath()));
		}
		Map<String, Object> raw;
		try {
			raw = new ObjectMapper().readValue(credentialFile,
				new TypeReference<Map<String, Object>>() { });
		} catch (IOException e) {
			throw new SheetToolException("憑證檔不是合法的 JSON (" + credentialFile.getAbsolutePath()
				+ "): " + e.getMessage(), e);
		}
		String type = asString(raw.get("type"));
		String clientEmail = asString(raw.get("client_email"));
		if (!EXPECTED_TYPE.equals(type) || clientEmail.isEmpty()) {
			throw new SheetToolException(credentialFile.getAbsolutePath()
				+ " 看起來不是 service account 金鑰檔（缺少 type=\"service_account\" 或 client_email）。"
				+ "請確認下載的是 service account 金鑰，而不是 OAuth client secret");
		}
		return new Summary(clientEmail, asString(raw.get("project_id")),
			tail(asString(raw.get("private_key_id")), 6));
	}

	/**
	 * 已被 git 追蹤就擋下。git 不存在、不在 repo 內、或任何 IO 例外一律靜默略過 ——
	 * 沒裝 git 不該讓工具不能用。
	 */
	static void requireNotGitTracked(File credentialFile) throws SheetToolException {
		File parent = credentialFile.getAbsoluteFile().getParentFile();
		if (parent == null) {
			return;
		}
		try {
			ProcessBuilder builder = new ProcessBuilder(
				"git", "ls-files", "--error-unmatch", credentialFile.getAbsolutePath());
			builder.directory(parent);
			builder.redirectErrorStream(true);
			Process process = builder.start();
			// 不讀完輸出可能讓子行程卡在寫入，這裡輸出很短，直接排掉
			try (java.io.InputStream in = process.getInputStream()) {
				byte[] buffer = new byte[1024];
				while (in.read(buffer) != -1) {
					// 丟棄
				}
			}
			if (process.waitFor() == 0) {
				throw new SheetToolException("憑證檔已被 git 追蹤: " + credentialFile.getAbsolutePath()
					+ "\n   請先執行: git rm --cached \"" + credentialFile.getAbsolutePath() + "\""
					+ "\n   並確認 .gitignore 有擋下 service-account.json"
					+ "\n   （.gitignore 對已追蹤的檔案無效，所以必須先 rm --cached）");
			}
		} catch (IOException e) {
			// git 不在 PATH 上
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** group/other 可讀就警告。Windows 沒有 POSIX 權限，直接略過 */
	static void warnIfPermissionsTooOpen(File credentialFile) {
		try {
			Set<PosixFilePermission> permissions =
				Files.getPosixFilePermissions(credentialFile.toPath());
			boolean readableByOthers = permissions.contains(PosixFilePermission.GROUP_READ)
				|| permissions.contains(PosixFilePermission.OTHERS_READ);
			if (readableByOthers) {
				System.err.println("⚠️  憑證檔權限過寬，建議執行: chmod 600 \""
					+ credentialFile.getAbsolutePath() + "\"");
			}
		} catch (UnsupportedOperationException | IOException e) {
			// 非 POSIX 檔案系統，略過
		}
	}

	private static String asString(Object value) {
		return value == null ? "" : value.toString().trim();
	}

	private static String tail(String value, int length) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.length() <= length ? value : value.substring(value.length() - length);
	}

	/**
	 * 憑證的非敏感摘要。<b>刻意不持有 private_key 也不持有 access token</b>，
	 * 讓「不小心印出機密」在結構上就不可能發生。
	 */
	public static final class Summary {

		private final String clientEmail;
		private final String projectId;
		private final String privateKeyIdTail;

		Summary(String clientEmail, String projectId, String privateKeyIdTail) {
			this.clientEmail = clientEmail;
			this.projectId = projectId;
			this.privateKeyIdTail = privateKeyIdTail;
		}

		public String getClientEmail() {
			return clientEmail;
		}

		public String getProjectId() {
			return projectId;
		}

		/** private_key_id 的末 6 碼，足以辨識是哪把金鑰，又不足以還原 */
		public String getPrivateKeyIdTail() {
			return privateKeyIdTail;
		}
	}
}
