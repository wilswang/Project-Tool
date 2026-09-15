package tool.sheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

/**
 * 建立 Google Sheets client 與驗證憑證。
 *
 * <p>與「讀資料」刻意拆成兩個類別：{@link SheetReader} 只吃一個建好的 {@link Sheets}，
 * 所以它可以注入 mock transport 離線測試；而且哪天要換掉官方 client（例如改用
 * 專案已有的 okhttp + Jackson 自己打 REST），只有這一個類別要重寫。
 *
 * @author Wilson.Wang
 * @version 1.5.0
 */
public final class GoogleSheetClientFactory {

	/** 只讀權限。這個工具不寫試算表，scope 就不要多給 */
	public static final String SCOPE = SheetsScopes.SPREADSHEETS_READONLY;

	private GoogleSheetClientFactory() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/** 載入憑證並套上只讀 scope */
	public static GoogleCredentials loadCredentials(File credentialFile) throws IOException {
		try (FileInputStream input = new FileInputStream(credentialFile)) {
			return GoogleCredentials.fromStream(input)
				.createScoped(Collections.singleton(SCOPE));
		}
	}

	/** 建立 Sheets client */
	public static Sheets create(File credentialFile, String applicationName)
			throws IOException, GeneralSecurityException {
		return create(GoogleNetHttpTransport.newTrustedTransport(),
			GsonFactory.getDefaultInstance(),
			new HttpCredentialsAdapter(loadCredentials(credentialFile)),
			applicationName);
	}

	/** 注入版本，給測試用 */
	static Sheets create(HttpTransport transport, JsonFactory jsonFactory,
			HttpRequestInitializer initializer, String applicationName) {
		return new Sheets.Builder(transport, jsonFactory, initializer)
			.setApplicationName(applicationName)
			.build();
	}

	/**
	 * 階段 0：驗證憑證可用。<b>完全不碰任何試算表</b> —— 只要有一個可讀的
	 * service-account.json 就能跑，所以連 spreadsheetId 都不需要。
	 *
	 * <p>刻意把「憑證有效」與「試算表有權限」分成兩件事：階段 0 通過但階段 1 拿 403，
	 * 幾乎一定是「沒把 service account 加進試算表共用」，不是憑證問題。
	 */
	public static AuthInfo verifyAuth(File credentialFile) throws SheetToolException {
		CredentialGuard.Summary summary = CredentialGuard.inspect(credentialFile);
		try {
			GoogleCredentials credentials = loadCredentials(credentialFile);
			credentials.refresh();
			AccessToken token = credentials.getAccessToken();
			if (token == null) {
				throw new SheetToolException("憑證載入成功但取不到 access token，請確認金鑰是否已被停用");
			}
			return new AuthInfo(summary, token.getExpirationTime());
		} catch (IOException e) {
			throw new SheetToolException("向 Google 取得 access token 失敗: " + e.getMessage()
				+ "\n   常見原因: 金鑰已被撤銷、系統時間偏差過大、或網路無法連到 oauth2.googleapis.com", e);
		}
	}

	/**
	 * 驗證結果。<b>不持有 access token 字串本身</b>，只留到期時間。
	 */
	public static final class AuthInfo {

		private final CredentialGuard.Summary credential;
		private final Date tokenExpiry;

		AuthInfo(CredentialGuard.Summary credential, Date tokenExpiry) {
			this.credential = credential;
			this.tokenExpiry = tokenExpiry == null ? null : new Date(tokenExpiry.getTime());
		}

		public CredentialGuard.Summary getCredential() {
			return credential;
		}

		public Date getTokenExpiry() {
			return tokenExpiry == null ? null : new Date(tokenExpiry.getTime());
		}
	}
}
