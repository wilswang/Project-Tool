package tool.whiteLabel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 產生新群組（newGroup=true）需要的多列 SQL 片段。
 *
 * 這裡的列數由單號 JSON 的 groupInfo.privateIp / groupInfo.backup 陣列長度決定 ——
 * 那本來就是資料，加減 domain 不需要重新打包。
 *
 * 各環境的差異則全部來自 EnvValues 的三個欄位，不再有 isUat 這種寫死在 code 裡的分支：
 *   extraPrivateDomains  該環境要額外插入的 private domain（apidomaintype 0）
 *   extraPublicDomains   該環境要額外插入的 public domain（apidomaintype 1）
 *   activateOwnDomains   自家 domain 與 backup 的 isactive 要不要開啟
 * 三者的預設值 []/[]/true 重現不需要特殊處理的環境（DEV / SIM）的行為。
 *
 * 仍留在 Java 的部分：單列的 SQL 格式與編號規則。模板引擎沒有迴圈語法，
 * 「一個陣列展開成 N 列」只能在這裡做。
 *
 * @author Wilson.Wang
 * @version 1.4.0
 */
public final class NewGroupSqlBuilder {

	private NewGroupSqlBuilder() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	public static Map<String, String> build(WhiteLabelConfig whiteLabelConfig, EnvValues envValues) {
		ApiWalletInfo apiWalletInfo = whiteLabelConfig.getApiWalletInfo();
		GroupInfo groupInfo = apiWalletInfo.getGroupInfo();

		List<String> extraPrivateDomains = envValues.getExtraPrivateDomains();
		List<String> extraPublicDomains = envValues.getExtraPublicDomains();
		boolean activateOwnDomains = envValues.isActivateOwnDomains();

		Map<String, String> replacements = new LinkedHashMap<>();

		replacements.put("{$privateIpSetId}", groupInfo.getPrivateIpSetId());
		replacements.put("{$wwwgaIpSetId}", !groupInfo.getBkIpSetId().isEmpty() ? groupInfo.getBkIpSetId().get(0) : null);
		replacements.put("{$wwwcfIpSetId}", !groupInfo.getBkIpSetId().isEmpty() ? groupInfo.getBkIpSetId().get(1) : null);
		replacements.put("{$apiInfoBkIpSetId}", groupInfo.getApiInfoBkIpSetId());

		String subDomainStatic = envValues.getSubDomainStatic();
		String subDomainApi = envValues.getSubDomainApi();

		StringBuilder valuesSb = new StringBuilder();
		StringBuilder corsDomainSb = new StringBuilder();
		StringBuilder enableFrontendBackendSeparationByDomainSb = new StringBuilder();
		StringBuilder enableDesktopFrontendBackendSeparationByDomainSb = new StringBuilder();

		// privateIpList：自家 private domain，加上該環境要額外插入的
		List<String> privateIpList = new ArrayList<>(groupInfo.getPrivateIp());
		privateIpList.addAll(extraPrivateDomains);
		for (int i = 0; i < privateIpList.size(); i++) {
			boolean isExtraDomain = extraPrivateDomains.contains(privateIpList.get(i));
			// 額外插入的一律開啟；自家的看 activateOwnDomains
			int active = 1;
			if (!isExtraDomain && !activateOwnDomains) {
				active = 0;
			}
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 0, NOW(6), NOW(6)),",
				apiWalletInfo.getGroup(), privateIpList.get(i), active, i + 1);
			valuesSb.append(value);

			if (!isExtraDomain) {
				String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1", privateIpList.get(i));
				enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
				enableFrontendBackendSeparationByDomainSb.append(",");
			}
		}

		// backupList
		List<String> backupList = new ArrayList<>(groupInfo.getBackup());
		for (int i = 0; i < backupList.size(); i++) {
			if (i > 0) {
				valuesSb.append(",");
				corsDomainSb.append(",");
				enableFrontendBackendSeparationByDomainSb.append(",");
				enableDesktopFrontendBackendSeparationByDomainSb.append(",");
			}
			int active = 0;
			if (activateOwnDomains) {
				active = i >= 2 ? 0 : 1;
			}
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, sysdate(6), sysdate(6))",
				apiWalletInfo.getGroup(), backupList.get(i), active, i + 1);
			valuesSb.append(value);

			String corsDomainValue = String.format("\n\t('%s', 1, '%s', '%s', sysdate(6), sysdate(6))",
				backupList.get(i), subDomainStatic, subDomainApi);
			corsDomainSb.append(corsDomainValue);

			String frontendBackendSeparation = String.format("\n\t\t\"%s\": 1", backupList.get(i));
			enableFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
			enableDesktopFrontendBackendSeparationByDomainSb.append(frontendBackendSeparation);
		}

		// 該環境要額外插入的 public domain，接在 backup 之後繼續編號
		for (int i = 0; i < extraPublicDomains.size(); i++) {
			valuesSb.append(",");
			String value = String.format("\n\t(apidomainname_id_seq_nextval(), '%s', '%s', %s, %s, 'SYSTEM', 1, sysdate(6), sysdate(6))",
				apiWalletInfo.getGroup(), extraPublicDomains.get(i), 1, backupList.size() + i + 1);
			valuesSb.append(value);
		}

		valuesSb.append(";");
		corsDomainSb.append(";");

		replacements.put("{$apiDomainValues}", valuesSb.toString());
		replacements.put("{$corsDomainValues}", corsDomainSb.toString());
		replacements.put("{$enableFrontendBackendSeparationByDomainValues}", enableFrontendBackendSeparationByDomainSb.toString());
		replacements.put("{$enableDesktopFrontendBackendSeparationByDomainValues}", enableDesktopFrontendBackendSeparationByDomainSb.toString());

		return replacements;
	}
}
