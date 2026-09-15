# CLAUDE.md - White Label JSON Mapping Rule

# 🚫 Strict Execution Rules
1. No undefined/ambiguous actions. Ask when requirements are missing.
2. No guessing or assuming. Use only explicit user-provided information.
3. Only use explicitly authorized tools, APIs, and parameters.
4. Stop and ask if instruction risks errors or lacks definition.
5. No extra steps. Only what's explicitly requested.

When uncertain: "Please provide more details or clarification."

# Core Mapping Rules
## Purpose
Based on input JIRA info, follow below JSON Field Mapping rule to create JSON file under @ProjectTool/sample

## JSON File Name
- **name**: SACRIC-XXX → `SACRIC-XXX.json`
- **location**: `./sample/{subdir}/SACRIC-XXX.json`
    - SingleWallet (`apiWalletType=Single`, 不分 newGroup) → `./sample/SingleWallet/`
    - ApiWallet NewGroup → `./sample/New Group/`
    - ApiWallet not NewGroup → `./sample/ApiWallet/`
    - New Site → `./sample/New Site/`

## JSON Field Mapping
**Required**: project, ticketNo, webSiteValue, webSiteName, jiraSummary, apiWhiteLabel, fixVersion, developer, sqlOnly, customized, files
**Conditional**:
- host (non-API white labels)
- lowLiquidity (API white labels)
- supportInfo* (8 fields, non-API only)

- **project**: SACRIC-XXX → "SACRIC" (prefix before the hyphen)
- **ticketNo**: SACRIC-XXX → "XXX"
- **webSiteValue**: Next available value 534 — assign this exact number to this ticket. Copy it as-is: do not calculate, do not add anything to it, do not reuse a number from an earlier ticket. ⚠️ Auto-updated by step 3, do not modify manually
- **webSiteName**: Extract from summary (regular) or description (API)
- **jiraSummary**: Full ticket summary content
- **apiWhiteLabel**: 
  - Contains "[ApiWallet][TransferWallet]" → true
  - Contains "[ApiWallet][SingleWallet]" → true
- **apiWalletType**:
    - Contains "[ApiWallet][TransferWallet]" → "Transfer"
    - Contains "[ApiWallet][SingleWallet]" → "Single"
- **host**: Domain or URL in description (non-API only)
    - "URL : royalexc365.com" → "royalexc365.com"
    - "Domain : skyexchx.xyz" → "skyexchx.xyz" 
- **fixVersion**:
    - "Patch X.X.X" → "release-X.X.X" (lowercase + full version)
    - "Hotfix X.X.X" → "hotfix-X.X.X" (lowercase + full version)
- **developer**: customfield_10037 (required, never null — this field feeds the {$developer} placeholder in the generated SQL; if you cannot read it, stop and ask instead of emitting null)
- **sqlOnly**: false, **customized**: Ask user
- **lowLiquidity**:
    - default: 20000
    - `Market Low Liquidity: 所有sports 設定2萬U`: 20000
    - `Market Low Liquidity: 所有Sports 設定4000 U`: 4000
- **cssName**: CSS skin name, fills `{$cssName}` in all three `WST.txt`
    - "CSS Style : 9wickets.pro 公版" → "wicketspro"
    - "CSS 配色：No" → "wicketspro" (also matches "CSS配色：" without space, and "NO")
    - "CSS 配色：Yes（CSS: Gn03）" → webSiteName in lower case, e.g. "PBC888" → "pbc888"
    - New Site (apiWhiteLabel = false) → always webSiteName in lower case
    - The palette code (Gn03 / Y104 / Bu03) is not the skin name, never use it
    - If the 相關文件附件 table row `CSS客製配色` disagrees with the above, stop and ask
- **isRacingOnly**: horse/greyhound-only white label
    - 相關文件附件 table `是否為賽狗/賽馬白牌` = YES → true, NO → false
    - Row absent → false (only racing tickets carry this row)
- **envValues**: Per-environment values that override the shared `config/env-values.json`.
  Shape is `{"<ENV>": {"<key>": "<value>"}}` and every key becomes a `{$key}` placeholder
  resolved for the environment being generated.
    - `envValues.UAT.apiHost`: The value in the same row as "UAT端點URL", but next column
    - `envValues.SIM.apiHost`: The value in the same row as "SIM端點URL", but next column
    - `envValues.DEV.apiHost`: The value in the same row as "UAT端點URL", but next column
    - Only SingleWallet white labels need `apiHost` (it fills the APISITEPROPERTIES `host`
      row in `SingleWallet/DB-01-template.txt`). TransferWallet white labels must omit it.
    - ⚠️ If a SingleWallet ticket omits `apiHost`, the tool aborts with an unresolved-placeholder
      error and writes nothing — it will not fall back to another site's endpoint.

## Support Contact Fields (Regular White Labels Only)

**Scope**: Regular white labels only (apiWhiteLabel = false)
**Source**: Jira description
**Default**: All fields default to empty string ""

### Basic Fields (Keep Original Content)
- **supportInfoEmail**: Email address
- **supportInfoFb**: Facebook contact
- **supportInfoImo**: Imo contact
- **supportInfoSkype**: Skype contact
- **supportInfoTwitter**: Twitter contact
- **supportInfoIg**: Instagram contact

### Fields Requiring Conversion

**supportInfoTg** (Telegram):
- Format 1 (Phone):
  - Example: `Telegram：+44 7933 589188`
  - Rule: Remove `+` and spaces
  - Result: `"447933589188"`
- Format 2 (Link):
    - Example: `Telegram -  https://t.me/Amiribookofficial`
    - Rule: Extract the username after `t.me/` from the URL
    - Result: `"Amiribookofficial"`

**supportInfoWhatsApp**:
- Format 1 (Phone):
    - Example: `whatsapp：+44 7933 589188`
    - Rule: Remove `+` and spaces
    - Result: `"447933589188"`
- Format 2 (Link):
    - Example: `whatsapp：https://wa.link/bolbumexch`
    - Rule: Keep full URL
    - Result: `"https://wa.link/bolbumexch"`

**supportInfoIg**:
- Example: `https://www.instagram.com/amiribookofficial/`
- Rule: Extract the username after `instagram.com/`, remove trailing slash
- Result: `"amiribookofficial"`

### Important Notes
- **API white labels** (apiWhiteLabel = true): **Exclude all** supportInfo* fields
- **Regular white labels** (apiWhiteLabel = false): **Must include all 8** supportInfo* fields
- Contacts not mentioned in description: Field exists with value ""

## API White Label Additional Fields
- **apiWalletInfo.cert**: "Cert: XXX" if exist in comments,otherwise,  use RANDOM.ORG(https://www.random.org/strings/?num=10&len=16&digits=on&upperalpha=on&loweralpha=on&unique=on&format=html&rnd=new) 16 chars
- **apiWalletInfo.group**: "API 2.0 Group : XXX" in description
- **apiWalletInfo.newGroup**:
    - "API 2.0 Group : XXX (新群組)" contains「(新群組)」→ true
    - Otherwise → false (default)
- **apiWalletInfo.groupInfo** (only when newGroup = true):
    - **privateIp**: Extract domain list from "Private Domain:" section
        - Example source:
          ```
          Private Domain:
          1. prc07sr0101.xyz
          2. prc07sr0102.space
          ```
        - Result: `["prc07sr0101.xyz", "prc07sr0102.space"]`
    - **privateIpSetId**: `null`
    - **bkIpSetId**: `null`
    - **apiInfoBkIpSetId**: `null`
    - **backup**: `null`
    - The four fields above are filled by step 2.5 from the API 2.0 domain spreadsheet,
      keyed on `apiWalletInfo.group`. Emit them as `null` and let the tool do it.
    - ⚠️ Never invent these values and never copy them from another ticket. If the group
      code cannot be read from the description, stop and ask — step 2.5 aborts the run
      when `newGroup` is true but `group` is empty.

## Files Field

The `files` array depends on two conditions: `apiWhiteLabel`, `apiWalletType` and `apiWalletInfo.newGroup`.

### Scenario 1: ApiWallet NewGroup (`apiWhiteLabel=true`, `apiWalletType=Transfer`, `newGroup=true`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/New Group",
    "template": "./template/white-label/ApiWallet/DB-01-template-New-Group.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/New Group",
    "template": "./template/white-label/ApiWallet/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$className}ApiAction.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/transfer/",
    "template": "./template/white-label/ApiWallet/ApiAction.txt",
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/ApiWallet/WST.txt",
    "imports": ["com.nv.commons.website.page.{$className}WebSitePage"]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/ApiWallet/WebSitePageTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/ApiWallet/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/ApiWallet/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  },
  {
    "name": "ApiActionFactory",
    "isNew": false,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/ApiActionFactory.java",
    "template": "./template/white-label/ApiWallet/ApiActionFactory.txt",
    "marker": "// insert New White Label API_WALLET",
    "imports": ["com.nv.apiWallet.apiaction.transfer.{$className}ApiAction"]
  }
]
```

### Scenario 2: ApiWallet not NewGroup (`apiWhiteLabel=true`, `apiWalletType=Transfer`, `newGroup=false`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/ApiWallet",
    "template": "./template/white-label/ApiWallet/DB-01-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/ApiWallet",
    "template": "./template/white-label/ApiWallet/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$className}ApiAction.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/transfer/",
    "template": "./template/white-label/ApiWallet/ApiAction.txt",
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/ApiWallet/WST.txt",
    "imports": ["com.nv.commons.website.page.{$className}WebSitePage"]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/ApiWallet/WebSitePageTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/ApiWallet/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/ApiWallet/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  },
  {
    "name": "ApiActionFactory",
    "isNew": false,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/ApiActionFactory.java",
    "template": "./template/white-label/ApiWallet/ApiActionFactory.txt",
    "marker": "// insert New White Label API_WALLET",
    "imports": ["com.nv.apiWallet.apiaction.transfer.{$className}ApiAction"]
  }
]
```

### Scenario 3: New Site (`apiWhiteLabel=false`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/New Site",
    "template": "./template/white-label/NewSite/DB-01-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/New Site",
    "template": "./template/white-label/NewSite/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/NewSite/WST.txt",
    "imports": [
      "com.nv.commons.website.page.{$className}WebSitePage",
      "com.nv.commons.code.domain.{$className}DomainType"
    ]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/NewSite/WebSitePageTemplate.txt"
  },
  {
    "name": "{$className}DomainType.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/code/domain/",
    "template": "./template/white-label/NewSite/DomainTypeTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/NewSite/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/NewSite/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  }
]
```

### Scenario 4: SingleWallet NewGroup (`apiWhiteLabel=true`, `apiWalletType=Single`, `newGroup=true`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/SingleWallet/DB-01-template-New-Group.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/SingleWallet/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$className}ApiAction.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/single/",
    "template": "./template/white-label/SingleWallet/ApiAction.txt",
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/SingleWallet/WST.txt",
    "imports": ["com.nv.commons.website.page.{$className}WebSitePage"]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/SingleWallet/WebSitePageTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  },
  {
    "name": "ApiActionFactory",
    "isNew": false,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/ApiActionFactory.java",
    "template": "./template/white-label/SingleWallet/ApiActionFactory.txt",
    "marker": "// insert New White Label SINGLE_WALLET",
    "imports": ["com.nv.apiWallet.apiaction.single.{$className}ApiAction"]
  }
]
```

### Scenario 5: SingleWallet not NewGroup (`apiWhiteLabel=true`, `apiWalletType=Single`, `newGroup=false`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/SingleWallet/DB-01-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/SingleWallet/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$className}ApiAction.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/single/",
    "template": "./template/white-label/SingleWallet/ApiAction.txt",
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/SingleWallet/WST.txt",
    "imports": ["com.nv.commons.website.page.{$className}WebSitePage"]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/SingleWallet/WebSitePageTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  },
  {
    "name": "ApiActionFactory",
    "isNew": false,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/ApiActionFactory.java",
    "template": "./template/white-label/SingleWallet/ApiActionFactory.txt",
    "marker": "// insert New White Label SINGLE_WALLET",
    "imports": ["com.nv.apiWallet.apiaction.single.{$className}ApiAction"]
  }
]
```

### Scenario 6: SingleWallet NewGroup RacingOnly (`apiWhiteLabel=true`, `apiWalletType=Single`, `newGroup=true`, `isRacingOnly=true`)
```json
"files": [
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-01.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/RacingOnly/DB-01-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$project}-{$ticketNo}-{$env}-DB-41.sql",
    "isNew": true,
    "location": "./result/sql/SingleWallet",
    "template": "./template/white-label/SingleWallet/DB-41-template.txt",
    "environments": ["DEV", "UAT", "SIM"]
  },
  {
    "name": "{$className}ApiAction.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/single/",
    "template": "./template/white-label/SingleWallet/ApiAction.txt",
  },
  {
    "name": "WebSiteType",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/code/WebSiteType.java",
    "template": "./template/white-label/SingleWallet/WST.txt",
    "imports": ["com.nv.commons.website.page.{$className}WebSitePage"]
  },
  {
    "name": "{$className}WebSitePage.java",
    "isNew": true,
    "location": "../src/main/java/com/nv/commons/website/page/",
    "template": "./template/white-label/RacingOnly/WebSitePageTemplate.txt"
  },
  {
    "name": "Const",
    "isNew": false,
    "location": "../src/main/webapp/js/const/Const.js",
    "template": "./template/white-label/Const.txt"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting1.txt",
    "marker": "// insert New White Label setting-1"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/SingleWallet/Setting2.txt",
    "marker": "// insert New White Label setting-2"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/RacingOnly/Setting3.txt",
    "marker": "// insert New White Label setting-3"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/RacingOnly/Setting4.txt",
    "marker": "// insert New White Label setting-4"
  },
  {
    "name": "Setting",
    "isNew": false,
    "location": "../src/main/java/com/nv/commons/model/Setting.java",
    "template": "./template/white-label/RacingOnly/Setting5.txt",
    "marker": "// insert New White Label setting-5"
  },
  {
    "name": "ApiActionFactory",
    "isNew": false,
    "location": "../src/main/java/com/nv/apiWallet/apiaction/ApiActionFactory.java",
    "template": "./template/white-label/SingleWallet/ApiActionFactory.txt",
    "marker": "// insert New White Label SINGLE_WALLET",
    "imports": ["com.nv.apiWallet.apiaction.single.{$className}ApiAction"]
  }
]
```

### Decision Logic
| `apiWhiteLabel` | `apiWalletType` | `newGroup` | `isRacingOnly` | Scenario |
|---|---|---|---|---|
| `true` | `Transfer` | `true` | `false` | Scenario 1: ApiWallet NewGroup |
| `true` | `Transfer` | `false` | `false` | Scenario 2: ApiWallet not NewGroup |
| `false` | N/A | N/A | N/A | Scenario 3: New Site |
| `true` | `Single` | `true` | `false` | Scenario 4: SingleWallet NewGroup |
| `true` | `Single` | `false` | N/A | Scenario 5: SingleWallet not NewGroup |
| `true` | `Single` | `true` | `true` | Scenario 6: SingleWallet NewGroup RacingOnly |

`groupInfo` is only needed by the three scenarios whose DB-01 template references it —
Scenario 1, 4 and 6, i.e. exactly the `newGroup=true` rows. Those are the runs step 2.5
fills from the spreadsheet.