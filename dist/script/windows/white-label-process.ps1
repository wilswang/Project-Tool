#Requires -Version 5.1

<#
.SYNOPSIS
    White Label Process - Complete White Label Workflow (Steps 1-8)

.DESCRIPTION
    PowerShell version of white-label-process.sh
    Automates the complete white label setup workflow for SACRIC tickets.

.PARAMETER TicketNo
    The Jira ticket number (format: SACRIC-XXX)

.PARAMETER Customized
    Customized mode - skip steps 5-8

.PARAMETER TestMode
    Test mode - skip actual Jira updates

.PARAMETER Quiet
    Suppress verbose output

.PARAMETER FromStep
    Start from specific step (1-8)

.EXAMPLE
    .\white-label-process.ps1 -TicketNo SACRIC-1062
    .\white-label-process.ps1 SACRIC-1062 -Customized -TestMode
    .\white-label-process.ps1 SACRIC-1062 -FromStep 3
#>

param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidatePattern('^SACRIC-\d+$')]
    [string]$TicketNo,

    [Alias('c')]
    [switch]$Customized,

    [Alias('t')]
    [switch]$TestMode,

    [switch]$Quiet,

    [Alias('s')]
    [ValidateRange(1,8)]
    [int]$FromStep = 1
)

# =============================================================================
# Initialization
# =============================================================================

$ErrorActionPreference = "Stop"
$script:ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:ToolDir = Split-Path -Parent (Split-Path -Parent $script:ScriptDir)
$script:ProjectRoot = Split-Path -Parent $script:ToolDir

# File paths
$script:JarFile = Join-Path $script:ToolDir "Project-Tool.jar"
$script:JiraFile = Join-Path $script:ToolDir "result\jira\$TicketNo-jira.txt"
$script:LogFile = Join-Path $script:ToolDir "log\$TicketNo.txt"
$script:ClaudeMd = Join-Path $script:ToolDir "task\white-label-mapping-rule.md"

# Find JSON file - search subdirectories first, fallback to root (new tickets)
$script:Subdir = ""
foreach ($s in @("SingleWallet", "ApiWallet", "New Group", "New Site")) {
    $candidate = Join-Path $script:ToolDir "sample\$s\$TicketNo.json"
    if (Test-Path $candidate) {
        $script:Subdir = $s
        break
    }
}
if ($script:Subdir) {
    $script:JsonFile = Join-Path $script:ToolDir "sample\$($script:Subdir)\$TicketNo.json"
} else {
    $script:JsonFile = Join-Path $script:ToolDir "sample\$TicketNo.json"
}

# Create directories
@("result\jira", "result\sql\ApiWallet", "result\sql\New Group", "result\sql\New Site",
  "result\sql\SingleWallet", "log", "sample\ApiWallet", "sample\New Group",
  "sample\New Site", "sample\SingleWallet") | ForEach-Object {
    $dir = Join-Path $script:ToolDir $_
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# Record start time
$script:StartTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

# Test flag for JAR tool calls
$script:TestFlag = if ($TestMode) { "-t" } else { "" }

# =============================================================================
# Color Output Functions
# =============================================================================

function Write-ColorOutput {
    param(
        [string]$Message,
        [ConsoleColor]$ForegroundColor = [ConsoleColor]::White,
        [switch]$NoNewline
    )

    if ($NoNewline) {
        Write-Host $Message -ForegroundColor $ForegroundColor -NoNewline
    } else {
        Write-Host $Message -ForegroundColor $ForegroundColor
    }
}

function Write-Verbose-Output {
    param([string]$Message)
    if (-not $Quiet) {
        Write-Host $Message
    }
}

function Write-Info {
    param([string]$Message)
    if (-not $Quiet) {
        Write-Host $Message
    }
}

function Write-Critical {
    param(
        [string]$Message,
        [ConsoleColor]$Color = [ConsoleColor]::White
    )
    Write-Host $Message -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Green
}

function Write-Warning-Msg {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Yellow
}

function Write-Error-Msg {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Red
}

function Write-Blue {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Cyan
}

# =============================================================================
# Helper Functions
# =============================================================================

function Read-JsonField {
    param([string]$Field)

    if (-not (Test-Path $script:JsonFile)) {
        return ""
    }

    try {
        $result = & jq -r ".$Field" $script:JsonFile 2>$null
        if ($result -eq "null") { return "" }
        return $result
    } catch {
        return ""
    }
}

function Resolve-Subdir {
    param([string]$JsonPath)
    try {
        $apiWhiteLabel = & jq -r '.apiWhiteLabel' $JsonPath 2>$null
        # apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有 → 落回原本的判斷
        $apiWalletType = & jq -r '.apiWalletType // empty' $JsonPath 2>$null
        $newGroup = & jq -r '.apiWalletInfo.newGroup // false' $JsonPath 2>$null
        if ($apiWhiteLabel -eq "true") {
            if ($apiWalletType -eq "Single") {
                # SingleWallet 不分 newGroup，一律放同一個目錄
                return "SingleWallet"
            } elseif ($newGroup -eq "true") {
                return "New Group"
            } else {
                return "ApiWallet"
            }
        } else {
            return "New Site"
        }
    } catch {
        return "ApiWallet"
    }
}

function Log-Step {
    param(
        # 用 string 而非 int：step 2.5 不是整數，[int] 會把它默默變成 2
        [string]$Step,
        [string]$Message
    )
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Host "[$timestamp] Step $Step : $Message"
}

# =============================================================================
# Dependency Check
# =============================================================================

function Test-Dependencies {
    Write-Blue "=== Checking dependencies ==="

    $missingDeps = @()
    $optionalDeps = @()

    # Check required dependencies
    if (-not (Get-Command jq -ErrorAction SilentlyContinue)) { $missingDeps += "jq" }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { $missingDeps += "git" }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { $missingDeps += "mvn" }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { $missingDeps += "java" }
    if (-not (Get-Command claude -ErrorAction SilentlyContinue)) { $missingDeps += "claude" }

    # Check optional dependencies
    if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) { $optionalDeps += "mysql" }

    # Report missing required dependencies
    if ($missingDeps.Count -gt 0) {
        Write-Error-Msg "Error: Missing required tools"
        Write-Host "Please install:"
        foreach ($dep in $missingDeps) {
            switch ($dep) {
                "jq" { Write-Host "  - jq: choco install jq OR scoop install jq" }
                "git" { Write-Host "  - git: choco install git OR https://git-scm.com/" }
                "mvn" { Write-Host "  - maven: choco install maven" }
                "java" { Write-Host "  - java: choco install openjdk" }
                "claude" { Write-Host "  - claude: npm install -g @anthropic-ai/claude-cli" }
            }
        }
        return $false
    }

    # Report missing optional dependencies
    if ($optionalDeps.Count -gt 0) {
        Write-Warning-Msg "Warning: Optional tools not installed:"
        foreach ($dep in $optionalDeps) {
            Write-Host "  - $dep"
        }
        Write-Host ""
    }

    # Verify JAR file exists
    if (-not (Test-Path $script:JarFile)) {
        Write-Error-Msg "Error: JAR file not found: $($script:JarFile)"
        return $false
    }

    Write-Success "Dependencies check passed"
    Write-Host ""

    return $true
}

# =============================================================================
# Cert Helper Functions
# =============================================================================

function Get-CertFromComments {
    param([string]$JiraFile)

    try {
        $comments = & jq -r '.fields.comment.comments[]?.body.content[]?.content[]?.text // empty' $JiraFile 2>$null
        if ($comments) {
            $certLine = $comments | Select-String -Pattern '^Cert: [A-Za-z0-9]{16}$' | Select-Object -First 1
            if ($certLine) {
                return ($certLine.Line -replace '^Cert: ', '').Trim()
            }
        }
    } catch {
        # Ignore errors
    }
    return $null
}

function Get-CertFromRandomOrg {
    try {
        $response = Invoke-WebRequest -Uri "https://www.random.org/strings/?num=1&len=16&digits=on&upperalpha=on&loweralpha=on&unique=on&format=html&rnd=new" -UseBasicParsing

        # Extract content between <pre class="data"> and </pre>
        if ($response.Content -match '<pre class="data">\s*([A-Za-z0-9]{16})\s*</pre>') {
            return $Matches[1]
        }

        # Alternative: multi-line extraction
        $lines = $response.Content -split "`n"
        $inPre = $false
        foreach ($line in $lines) {
            if ($line -match '<pre class="data">') {
                $inPre = $true
                continue
            }
            if ($inPre -and $line -match '^[A-Za-z0-9]{16}$') {
                return $line.Trim()
            }
            if ($line -match '</pre>') {
                $inPre = $false
            }
        }
    } catch {
        Write-Verbose-Output "Error fetching from RANDOM.ORG: $_"
    }
    return $null
}

function Update-WebSiteValueInMappingRule {
    param([int]$NewValue)

    $content = Get-Content $script:ClaudeMd -Raw
    $pattern = '(- \*\*webSiteValue\*\*: Next available value )\d+'
    $replacement = "`${1}$NewValue"
    $newContent = $content -replace $pattern, $replacement
    Set-Content -Path $script:ClaudeMd -Value $newContent -NoNewline
}

# WebSiteType.java 路徑（重複檢查用）
function Get-WebSiteTypeFile {
    return (Join-Path $script:ProjectRoot "src\main\java\com\nv\commons\code\WebSiteType.java")
}

# 找出 WebSiteType 內重複的 webSiteValue
# 取每個 enum 條目的第一個參數（site 編號），列出出現超過一次的值
function Get-DuplicatedWebSiteValues {
    $file = Get-WebSiteTypeFile
    if (-not (Test-Path $file)) {
        return @()
    }

    return @(Get-Content $file |
        Select-String -Pattern '^\s*[A-Z0-9_]+\((\d+), "' |
        ForEach-Object { $_.Matches[0].Groups[1].Value } |
        Group-Object |
        Where-Object { $_.Count -gt 1 } |
        ForEach-Object { $_.Name })
}

# 找出 WebSiteType 內重複的 enum 常數名（品牌名）
# 重跑同一張單會插入同名條目，Java 會編譯失敗，但 step 4 是非致命的擋不住
function Get-DuplicatedWebSiteNames {
    $file = Get-WebSiteTypeFile
    if (-not (Test-Path $file)) {
        return @()
    }

    return @(Get-Content $file |
        Select-String -Pattern '^\s*([A-Z0-9_]+)\(\d+, "' |
        ForEach-Object { $_.Matches[0].Groups[1].Value } |
        Group-Object |
        Where-Object { $_.Count -gt 1 } |
        ForEach-Object { $_.Name })
}

# 找出 WebSiteType 內重複的 cert
# 取 certCodeSet 宣告行上的所有字串，列出出現超過一次的值
function Get-DuplicatedCertCodes {
    $file = Get-WebSiteTypeFile
    if (-not (Test-Path $file)) {
        return @()
    }

    return @(Get-Content $file |
        Where-Object { $_ -match 'certCodeSet = new' } |
        ForEach-Object { [regex]::Matches($_, '"([A-Za-z0-9]+)"') } |
        ForEach-Object { $_ } |
        ForEach-Object { $_.Groups[1].Value } |
        Group-Object |
        Where-Object { $_.Count -gt 1 } |
        ForEach-Object { $_.Name })
}

# =============================================================================
# Step 1: Start Jira Issue
# =============================================================================

function Invoke-Step1 {
    Write-Blue "=== Step 1: Start Jira Issue ==="
    Log-Step 1 "Start Jira issue retrieval"

    Push-Location $script:ToolDir

    try {
        Write-Verbose-Output "Executing JiraTool start-jira-issue..."

        $consoleOutput = Join-Path $env:TEMP "jira-console-$TicketNo.txt"

        $args = @("-cp", $script:JarFile, "tool.http.JiraTool", "start-jira-issue", $TicketNo)
        if ($TestMode) { $args += "-t" }

        & java @args 2>&1 | Out-File -FilePath $consoleOutput -Encoding utf8

        if ($LASTEXITCODE -eq 0 -and (Test-Path $script:JiraFile)) {
            # Validate JSON structure
            try {
                & jq empty $script:JiraFile 2>$null
                if ($LASTEXITCODE -eq 0) {
                    Write-Success "Step 1 Success: Issue data retrieved"
                    Log-Step 1 "Success"
                    Write-Verbose-Output "Jira file: $($script:JiraFile)"
                    Remove-Item $consoleOutput -ErrorAction SilentlyContinue
                    return $true
                }
            } catch {}

            Write-Error-Msg "Step 1 Failed: Invalid JSON in output file"
            Log-Step 1 "Failed (Invalid JSON)"
            return $false
        } else {
            Write-Error-Msg "Step 1 Failed: JAR tool did not create output file"
            Log-Step 1 "Failed (No output file)"
            if (-not $Quiet -and (Test-Path $consoleOutput)) {
                Write-Verbose-Output "Console output:"
                Get-Content $consoleOutput
            }
            return $false
        }
    } catch {
        Write-Error-Msg "Step 1 Failed: JiraTool execution error"
        Write-Error-Msg $_.Exception.Message
        Log-Step 1 "Failed (Execution error)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 2: Transform to JSON
# =============================================================================

function Invoke-Step2 {
    Write-Blue "=== Step 2: Transform Jira Data to JSON ==="
    Log-Step 2 "Start JSON transformation"

    Push-Location $script:ToolDir

    try {
        # Validate Jira file exists
        if (-not (Test-Path $script:JiraFile)) {
            Write-Error-Msg "Step 2 Failed: Jira file not found"
            Log-Step 2 "Failed (Jira file not found)"
            return $false
        }

        $claudeRawOutput = Join-Path $env:TEMP "claude-raw-$TicketNo.txt"
        $claudeOutput = Join-Path $env:TEMP "claude-output-$TicketNo.json"

        # === Pre-process Cert (API white label only) ===
        $preCert = ""
        $summary = & jq -r '.fields.summary' $script:JiraFile 2>$null
        $isApiWhitelabel = $summary -match '\[ApiWallet\]\[(TransferWallet|SingleWallet)\]'

        if ($isApiWhitelabel) {
            Write-Verbose-Output "Detected API white label, checking for Cert..."

            # Try to extract from comments
            $preCert = Get-CertFromComments -JiraFile $script:JiraFile

            if ($preCert) {
                Write-Verbose-Output "Found Cert in comments: $preCert"
            } else {
                Write-Verbose-Output "No Cert in comments, fetching from RANDOM.ORG..."
                $preCert = Get-CertFromRandomOrg

                if ($preCert) {
                    Write-Verbose-Output "Generated Cert from RANDOM.ORG: $preCert"
                } else {
                    Write-Error-Msg "Failed to fetch Cert from RANDOM.ORG"
                    Log-Step 2 "Failed (Could not fetch cert from RANDOM.ORG)"
                    return $false
                }
            }
        }

        Write-Verbose-Output "Calling Claude CLI for transformation..."

        # Build cert instruction if needed
        $certInstruction = ""
        if ($preCert) {
            $certInstruction = @"

IMPORTANT: The Cert code has been pre-determined: $preCert
Use this exact value for apiWalletInfo.cert field. Do NOT generate a new cert.
"@
        }

        # Read mapping rules and Jira data
        $mappingRules = Get-Content $script:ClaudeMd -Raw
        $jiraData = Get-Content $script:JiraFile -Raw

        # Create transform prompt
        $transformPrompt = @"
You are a data transformation tool. Your ONLY task is to transform Jira JSON to white label configuration JSON.

DO NOT respond conversationally. DO NOT acknowledge. DO NOT explain.
OUTPUT ONLY THE JSON OBJECT. Nothing else.
$certInstruction

MAPPING RULES FROM white-label-mapping-rule.md:
$mappingRules

INPUT JIRA DATA:
``````json
$jiraData
``````

CUSTOMIZED MODE: $Customized

TASK: Transform the Jira JSON above into white label configuration JSON following the Core Mapping Rules.
OUTPUT: Valid JSON only. No markdown. No explanations. Just the JSON object.
"@

        # Call Claude
        $transformPrompt | & claude 2>$null | Out-File -FilePath $claudeRawOutput -Encoding utf8

        # Try multiple extraction methods
        $rawContent = Get-Content $claudeRawOutput -Raw
        $jsonContent = ""

        # Method 1: Extract from markdown code block
        if ($rawContent -match '```json\s*([\s\S]*?)\s*```') {
            $jsonContent = $Matches[1]
        }
        # Method 2: Extract from code block
        elseif ($rawContent -match '```\s*([\s\S]*?)\s*```') {
            $jsonContent = $Matches[1]
        }
        # Method 3: Extract JSON object
        elseif ($rawContent -match '(\{[\s\S]*\})') {
            $jsonContent = $Matches[1]
        }

        if ($jsonContent) {
            $jsonContent | Out-File -FilePath $claudeOutput -Encoding utf8
        }

        # Validate and format JSON
        if ((Test-Path $claudeOutput) -and (Get-Item $claudeOutput).Length -gt 0) {
            try {
                & jq empty $claudeOutput 2>$null
                if ($LASTEXITCODE -eq 0) {
                    & jq . $claudeOutput | Out-File -FilePath $script:JsonFile -Encoding utf8

                    # Verify JSON has required fields
                    $webSiteName = & jq -r '.webSiteName // empty' $script:JsonFile 2>$null
                    if ([string]::IsNullOrEmpty($webSiteName)) {
                        Write-Error-Msg "Step 2 Failed: Generated JSON missing required fields"
                        Log-Step 2 "Failed (Missing required fields)"
                        if (-not $Quiet) {
                            Write-Verbose-Output "Claude raw output:"
                            Get-Content $claudeRawOutput
                        }
                        return $false
                    }

                    $apiWhitelabel = & jq -r '.apiWhiteLabel' $script:JsonFile 2>$null

                    Write-Success "Step 2 Success: JSON generated"
                    Log-Step 2 "Success"

                    # Display key fields for verification
                    if (-not $Quiet) {
                        Write-Verbose-Output "Generated JSON:"
                        Write-Verbose-Output "  - webSiteName: $(& jq -r '.webSiteName' $script:JsonFile)"
                        Write-Verbose-Output "  - webSiteValue: $(& jq -r '.webSiteValue' $script:JsonFile)"
                        Write-Verbose-Output "  - apiWhiteLabel: $(& jq -r '.apiWhiteLabel' $script:JsonFile)"
                        Write-Verbose-Output "  - fixVersion: $(& jq -r '.fixVersion' $script:JsonFile)"
                        if ($apiWhitelabel -eq "true") {
                            Write-Verbose-Output "  - cert: $(& jq -r '.apiWalletInfo.cert' $script:JsonFile)"
                        }
                    }

                    Remove-Item $claudeOutput, $claudeRawOutput -ErrorAction SilentlyContinue

                    # Move JSON to correct subdirectory based on type
                    $script:Subdir = Resolve-Subdir -JsonPath $script:JsonFile
                    $newJson = Join-Path $script:ToolDir "sample\$($script:Subdir)\$TicketNo.json"
                    Move-Item -Path $script:JsonFile -Destination $newJson -Force
                    $script:JsonFile = $newJson

                    return $true
                }
            } catch {}
        }

        Write-Error-Msg "Step 2 Failed: Invalid JSON from Claude"
        Log-Step 2 "Failed (Invalid JSON from Claude)"
        if (-not $Quiet -and (Test-Path $claudeRawOutput)) {
            Write-Verbose-Output "Claude raw output:"
            Get-Content $claudeRawOutput
        }
        return $false

    } catch {
        Write-Error-Msg "Step 2 Failed: $_"
        Log-Step 2 "Failed (Exception)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 2.5: Fill groupInfo from the API 2.0 domain spreadsheet
# =============================================================================
# 只在 newGroup=true 時執行。以前這四個欄位（privateIpSetId / bkIpSetId /
# apiInfoBkIpSetId / backup）是 step 2 依 mapping rule 產出字面假值、再由人工從
# task/api-2.0-group-info.md 複製真值進來，漏掉時 step 3 與 step 4 都會報成功。
#
# 不受 -TestMode / -Customized 影響：兩者的語義是「不動 Jira / 不做收尾」，
# 都不該讓 SQL 產出變得不正確。

function Invoke-Step2_5 {
    # 「單子 JSON 必須存在」是 step 3 的契約，不是這一步的。檔案不在就安靜跳過，
    # 讓 step 3 去報它原本就會報的錯
    #
    # newGroup 不是 true 就整步跳過（一般白牌沒有群組要建）
    $newGroup = & jq -r '.apiWalletInfo.newGroup // false' $script:JsonFile 2>$null
    if ($newGroup -ne "true") {
        return $true
    }

    Write-Blue "=== Step 2.5: Fill groupInfo from spreadsheet ==="
    Log-Step 2.5 "Start groupInfo lookup"

    # 群組代號是查表的鍵，Jira 單必須明確寫出來
    $group = & jq -r '.apiWalletInfo.group // empty' $script:JsonFile 2>$null
    if ([string]::IsNullOrWhiteSpace($group)) {
        Write-Error-Msg "Step 2.5 Failed: newGroup is true but no group code was found"
        Write-Error-Msg "  Jira description must state the API 2.0 group code explicitly (e.g. A69)"
        Write-Error-Msg "  Fix the description, then re-run with -FromStep 2"
        Log-Step 2.5 "Failed (missing apiWalletInfo.group)"
        return $false
    }

    Push-Location $script:ToolDir

    try {
        Write-Verbose-Output "Looking up group $group in the API 2.0 domain spreadsheet..."

        $giOut = Join-Path $env:TEMP "groupinfo-$TicketNo.json"
        $giErr = Join-Path $env:TEMP "groupinfo-$TicketNo.txt"

        # -DprojectTool.configDir 明講設定檔在哪，不靠 CWD
        $sheetArgs = @(
            "-DprojectTool.configDir=$(Join-Path $script:ToolDir 'config')",
            "-cp", $script:JarFile,
            "tool.sheet.SheetTool", "group-info", $group,
            "--patch", $script:JsonFile
        )

        # stdout 與 stderr 必須分開接：狀態訊息走 stderr，合併會污染 JSON
        & java @sheetArgs > $giOut 2> $giErr

        if ($LASTEXITCODE -eq 0) {
            # 填入與未變動的明細、以及衝突警告都在 stderr，原樣轉出來
            if (-not $Quiet -and (Test-Path $giErr)) {
                Get-Content $giErr
            }
            Write-Success "Step 2.5 Success: groupInfo resolved for $group"
            Log-Step 2.5 "Success (group $group)"
            Remove-Item $giOut, $giErr -ErrorAction SilentlyContinue
            return $true
        } else {
            # 先放工具的訊息再放結論，讀起來才是事情發生的順序
            if (Test-Path $giErr) {
                Get-Content $giErr
            }
            Write-Error-Msg "Step 2.5 Failed: could not resolve groupInfo for $group"
            Write-Error-Msg "  Usual causes: credential not set up, spreadsheet not shared with the"
            Write-Error-Msg "  service account, unknown group code, or a spreadsheet value in a bad format"
            Write-Error-Msg "  The ticket JSON was not modified; re-running with -FromStep 3 is safe"
            Log-Step 2.5 "Failed (group $group)"
            Remove-Item $giOut, $giErr -ErrorAction SilentlyContinue
            return $false
        }
    } catch {
        Write-Error-Msg "Step 2.5 Failed: SheetTool execution error"
        Write-Error-Msg $_.Exception.Message
        Log-Step 2.5 "Failed (Execution error)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 3: Generate Code & SQL
# =============================================================================

function Invoke-Step3 {
    Write-Blue "=== Step 3: Generate Code & SQL ==="
    Log-Step 3 "Start code generation"

    Push-Location $script:ToolDir

    try {
        # Verify JSON file exists
        if (-not (Test-Path $script:JsonFile)) {
            Write-Error-Msg "Step 3 Failed: JSON file not found"
            Log-Step 3 "Failed (JSON file not found)"
            return $false
        }

        Write-Verbose-Output "Executing project-tool.sh..."

        # Execute project-tool.sh (using bash if available, or PowerShell equivalent)
        if (Get-Command bash -ErrorAction SilentlyContinue) {
            if ($Quiet) {
                & bash -c "./project-tool.sh A `"sample/$($script:Subdir)/$TicketNo.json`"" 2>&1 | Out-Null
            } else {
                & bash -c "./project-tool.sh A `"sample/$($script:Subdir)/$TicketNo.json`""
            }
        } else {
            # Try Git Bash
            $gitBash = "C:\Program Files\Git\bin\bash.exe"
            if (Test-Path $gitBash) {
                if ($Quiet) {
                    & $gitBash -c "./project-tool.sh A `"sample/$($script:Subdir)/$TicketNo.json`"" 2>&1 | Out-Null
                } else {
                    & $gitBash -c "./project-tool.sh A `"sample/$($script:Subdir)/$TicketNo.json`""
                }
            } else {
                Write-Error-Msg "Step 3 Failed: bash not found (required for project-tool.sh)"
                Log-Step 3 "Failed (bash not found)"
                return $false
            }
        }

        if ($LASTEXITCODE -eq 0) {
            Write-Success "Step 3 Success: Code & SQL generated"
            Log-Step 3 "Success"

            # 產出後檢查: WebSiteType 內不得有重複的 webSiteValue、品牌名或 cert
            $duplicatedValues = Get-DuplicatedWebSiteValues
            $duplicatedNames = Get-DuplicatedWebSiteNames
            $duplicatedCerts = Get-DuplicatedCertCodes
            if ($duplicatedValues.Count -gt 0 -or $duplicatedNames.Count -gt 0 -or $duplicatedCerts.Count -gt 0) {
                if ($duplicatedValues.Count -gt 0) {
                    Write-Error-Msg "Step 3 Failed: WebSiteType 有重複的 webSiteValue: $($duplicatedValues -join ' ')"
                }
                if ($duplicatedNames.Count -gt 0) {
                    Write-Error-Msg "Step 3 Failed: WebSiteType 有重複的品牌名: $($duplicatedNames -join ' ')"
                }
                if ($duplicatedCerts.Count -gt 0) {
                    Write-Error-Msg "Step 3 Failed: WebSiteType 有重複的 cert: $($duplicatedCerts -join ' ')"
                }
                Write-Error-Msg "   本次產出的檔案已寫入磁碟，不可直接以 -s 3 重跑（會再插入一筆）"
                Write-Error-Msg "   復原步驟:"
                Write-Error-Msg "     1. git checkout src/ 還原本次產出，或手動移除新增的 enum 區塊"
                Write-Error-Msg "     2. 修正 sample JSON 的 webSiteValue / cert"
                Write-Error-Msg "     3. 重新執行 -s 3"
                Log-Step 3 "Failed (duplicated webSiteValue: $($duplicatedValues -join ' '), name: $($duplicatedNames -join ' '), cert: $($duplicatedCerts -join ' '))"
                return $false
            }

            # 更新 mapping rule 中的 webSiteValue（寫回「下一個可用編號」= 本次用掉的 + 1）
            $currentValue = Read-JsonField -Field "webSiteValue"
            if ($currentValue -match '^\d+$') {
                $nextValue = [int]$currentValue + 1
                Update-WebSiteValueInMappingRule -NewValue $nextValue
                Write-Verbose-Output "Updated webSiteValue in mapping rule: used $currentValue -> next available $nextValue"
            }

            return $true
        } else {
            Write-Error-Msg "Step 3 Failed: project-tool.sh execution failed"
            Log-Step 3 "Failed"
            return $false
        }
    } catch {
        Write-Error-Msg "Step 3 Failed: $_"
        Log-Step 3 "Failed (Exception)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 4: Compile & Test
# =============================================================================

function Invoke-Step4 {
    Write-Blue "=== Step 4: Compile & Test ==="
    Log-Step 4 "Start build check"

    Push-Location $script:ProjectRoot

    try {
        $maxRetry = 3
        $retryCount = 0
        $buildLog = Join-Path $env:TEMP "build_$TicketNo.log"

        while ($retryCount -lt $maxRetry) {
            Write-Verbose-Output "Compiling (attempt $($retryCount + 1)/$maxRetry)"

            & mvn test-compile 2>&1 | Out-File -FilePath $buildLog -Encoding utf8

            if ($LASTEXITCODE -eq 0) {
                Write-Success "Step 4 Success: Build passed"
                Log-Step 4 "Success"
                Remove-Item $buildLog -ErrorAction SilentlyContinue
                return $true
            } else {
                Write-Warning-Msg "Build failed, checking error log..."

                $logContent = Get-Content $buildLog -Raw
                if ($logContent -match 'cannot find symbol.*class|package.*does not exist') {
                    Write-Warning-Msg "Detected import errors"

                    if (-not $Quiet) {
                        Write-Verbose-Output "Error details:"
                        $logContent | Select-String -Pattern 'cannot find symbol.*class|package.*does not exist' | Select-Object -First 10
                    }

                    $retryCount++
                } else {
                    Write-Error-Msg "Build failed (non-import error)"
                    Log-Step 4 "Failed (non-import error)"
                    if (-not $Quiet) {
                        Write-Verbose-Output "Error details:"
                        Get-Content $buildLog | Select-Object -Last 20
                    }
                    return $false
                }
            }
        }

        Write-Warning-Msg "Step 4 Warning: Build failed after $maxRetry attempts"
        Log-Step 4 "Warning (manual fixes needed)"
        Write-Verbose-Output "Manual import fixes may be required"
        return $false

    } catch {
        Write-Error-Msg "Step 4 Failed: $_"
        Log-Step 4 "Failed (Exception)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 5: Execute DEV SQL
# =============================================================================

function Invoke-Step5 {
    Write-Blue "=== Step 5: Execute DEV SQL ==="
    Log-Step 5 "Start SQL processing"

    # Check customized or test mode
    if ($Customized) {
        Write-Warning-Msg "Customized mode: Skipping step 5"
        Log-Step 5 "Skipped (customized)"
        return $true
    }

    if ($TestMode) {
        Write-Warning-Msg "Test mode: Skipping step 5"
        Log-Step 5 "Skipped (test mode)"
        return $true
    }

    Push-Location $script:ToolDir

    try {
        Write-Verbose-Output "Executing SQL-processing.sh..."

        # Execute SQL-processing.sh
        if (Get-Command bash -ErrorAction SilentlyContinue) {
            if ($Quiet) {
                & bash -c "./SQL-processing.sh `"$($script:Subdir)/$TicketNo.json`"" 2>&1 | Out-Null
            } else {
                & bash -c "./SQL-processing.sh `"$($script:Subdir)/$TicketNo.json`""
            }
        } else {
            $gitBash = "C:\Program Files\Git\bin\bash.exe"
            if (Test-Path $gitBash) {
                if ($Quiet) {
                    & $gitBash -c "./SQL-processing.sh `"$($script:Subdir)/$TicketNo.json`"" 2>&1 | Out-Null
                } else {
                    & $gitBash -c "./SQL-processing.sh `"$($script:Subdir)/$TicketNo.json`""
                }
            } else {
                Write-Error-Msg "Step 5 Failed: bash not found (required for SQL-processing.sh)"
                Log-Step 5 "Failed (bash not found)"
                return $false
            }
        }

        if ($LASTEXITCODE -eq 0) {
            Log-Step 5 "Success"
            Write-Success "Step 5 Success: SQL processed & executed"
            return $true
        } else {
            Log-Step 5 "Failed"
            Write-Error-Msg "Step 5 Failed: SQL processing failed"

            if (-not $Quiet) {
                $response = Read-Host "Continue to step 6 (git commit)? (y/n)"
                if ($response -notmatch '^[Yy]') {
                    return $false
                }
            }
            return $true
        }
    } catch {
        Write-Error-Msg "Step 5 Failed: $_"
        Log-Step 5 "Failed (Exception)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 6: Git Commit
# =============================================================================

function Invoke-Step6 {
    Write-Blue "=== Step 6: Git Commit ==="
    Log-Step 6 "Start git commit"

    # Check customized or test mode
    if ($Customized) {
        Write-Warning-Msg "Customized mode: Skipping git commit"
        Log-Step 6 "Skipped (customized)"
        return $true
    }

    if ($TestMode) {
        Write-Warning-Msg "Test mode: Skipping git commit"
        Log-Step 6 "Skipped (test mode)"
        return $true
    }

    Push-Location $script:ProjectRoot

    try {
        # Read JSON for commit message
        $apiWhitelabel = Read-JsonField -Field "apiWhiteLabel"
        $webSiteName = Read-JsonField -Field "webSiteName"

        # Determine commit message format
        if ($apiWhitelabel -eq "true") {
            # apiWalletType 是後來才加的欄位，舊單子的 JSON 沒有（Read-JsonField 會回 ""）
            # → 一律視為 TransferWallet，維持原本行為
            $apiWalletType = Read-JsonField -Field "apiWalletType"
            if ($apiWalletType -eq "Single") {
                $commitMsg = "[$TicketNo][ApiWallet][SingleWallet] $webSiteName"
            } else {
                $commitMsg = "[$TicketNo][ApiWallet][TransferWallet] $webSiteName"
            }
        } else {
            $commitMsg = "[$TicketNo] new Site $webSiteName"
        }

        Write-Verbose-Output "Commit message: $commitMsg"

        # Stage files
        # 只 stage src/：ProjectTool 已不在 citixchange 的版控內（由 zip 提供）
        $filesToAdd = @(
            "src/"
        )

        if ($Quiet) {
            & git add @filesToAdd 2>&1 | Out-Null
            & git commit -m $commitMsg 2>&1 | Out-Null
        } else {
            & git add @filesToAdd
            & git commit -m $commitMsg
        }

        if ($LASTEXITCODE -eq 0) {
            $commitHash = & git rev-parse --short HEAD
            Log-Step 6 "Success ($commitHash)"
            Write-Success "Step 6 Success: Git committed ($commitHash)"
            return $true
        } else {
            Log-Step 6 "Failed"
            Write-Error-Msg "Step 6 Failed: Git commit failed"
            return $false
        }
    } catch {
        Write-Error-Msg "Step 6 Failed: $_"
        Log-Step 6 "Failed (Exception)"
        return $false
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 7: Post Comment
# =============================================================================

function Get-CommentMsg {
    $apiWhitelabel = Read-JsonField -Field "apiWhiteLabel"
    $webSiteName = Read-JsonField -Field "webSiteName"
    $webSiteValue = Read-JsonField -Field "webSiteValue"
    $cert = Read-JsonField -Field "apiWalletInfo.cert"

    if ($apiWhitelabel -eq "true" -and $cert -and $cert -ne "null") {
        return "Site: $webSiteName ($webSiteValue)\nCert: $cert"
    } else {
        return "Site: $webSiteName ($webSiteValue)"
    }
}

function Invoke-Step7 {
    Write-Blue "=== Step 7: Post Comment to Jira ==="
    Log-Step 7 "Start post comment"

    # Check customized mode
    if ($Customized) {
        Write-Warning-Msg "Customized mode: Skipping Jira comment"
        Log-Step 7 "Skipped (customized)"
        return $true
    }

    Push-Location $script:ToolDir

    try {
        $commentMsg = Get-CommentMsg
        Write-Verbose-Output "Comment: $commentMsg"

        if ($TestMode) {
            Write-Warning-Msg "Test mode: Skipping Jira comment"
            Log-Step 7 "Skipped (test mode)"
            return $true
        }

        $jarOutput = Join-Path $env:TEMP "jira-comment-$TicketNo.txt"

        $args = @("-cp", $script:JarFile, "tool.http.JiraTool", "post-comment", $TicketNo, $commentMsg)
        if ($TestMode) { $args += "-t" }

        & java @args 2>&1 | Out-File -FilePath $jarOutput -Encoding utf8

        if ($LASTEXITCODE -eq 0) {
            Log-Step 7 "Success"
            Write-Success "Step 7 Success: Comment posted"
            Remove-Item $jarOutput -ErrorAction SilentlyContinue
            return $true
        } else {
            Log-Step 7 "Warning (manual required)"
            Write-Warning-Msg "Step 7 Warning: Comment posting failed"
            Write-Verbose-Output "Manual action required: Add comment to $TicketNo"
            Write-Verbose-Output "Comment text: $commentMsg"

            if (-not $Quiet -and (Test-Path $jarOutput)) {
                Write-Verbose-Output "JAR error output:"
                Get-Content $jarOutput
            }

            Remove-Item $jarOutput -ErrorAction SilentlyContinue
            return $true  # Non-fatal
        }
    } catch {
        Write-Error-Msg "Step 7 Warning: $_"
        Log-Step 7 "Warning (Exception)"
        return $true  # Non-fatal
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Step 8: Update Status
# =============================================================================

function Invoke-Step8 {
    Write-Blue "=== Step 8: Update Jira Status to DEV DONE ==="
    Log-Step 8 "Start status update"

    # Check customized mode
    if ($Customized) {
        Write-Warning-Msg "Customized mode: Skipping Jira status update"
        Log-Step 8 "Skipped (customized)"
        return $true
    }

    if ($TestMode) {
        Write-Warning-Msg "Test mode: Skipping Jira status update"
        Log-Step 8 "Skipped (test mode)"
        return $true
    }

    Push-Location $script:ToolDir

    try {
        $jarOutput = Join-Path $env:TEMP "jira-transition-$TicketNo.txt"

        $args = @("-cp", $script:JarFile, "tool.http.JiraTool", "transition-issue", $TicketNo, "DEV_DONE")
        if ($TestMode) { $args += "-t" }

        & java @args 2>&1 | Out-File -FilePath $jarOutput -Encoding utf8

        if ($LASTEXITCODE -eq 0) {
            Log-Step 8 "Success"
            Write-Success "Step 8 Success: Status updated to DEV DONE"
            Remove-Item $jarOutput -ErrorAction SilentlyContinue
            return $true
        } else {
            Log-Step 8 "Warning (manual required)"
            Write-Warning-Msg "Step 8 Warning: Status transition failed"
            Write-Verbose-Output "Manual action required: Transition $TicketNo to DEV DONE"
            Show-ManualInstructions

            if (-not $Quiet -and (Test-Path $jarOutput)) {
                Write-Verbose-Output "JAR error output:"
                Get-Content $jarOutput
            }

            Remove-Item $jarOutput -ErrorAction SilentlyContinue
            return $true  # Non-fatal
        }
    } catch {
        Write-Error-Msg "Step 8 Warning: $_"
        Log-Step 8 "Warning (Exception)"
        return $true  # Non-fatal
    } finally {
        Pop-Location
    }
}

# =============================================================================
# Display Manual Instructions
# =============================================================================

function Show-ManualInstructions {
    $commentMsg = Get-CommentMsg

    Write-Warning-Msg "========================================"
    Write-Warning-Msg "Manual Jira Update Required"
    Write-Warning-Msg "========================================"
    Write-Host "Ticket: $TicketNo"
    Write-Host "Transition: IN DEV -> DEV DONE"
    Write-Host ""
    Write-Host "Comment to add (if not added yet):"
    Write-Host "   $commentMsg"
    Write-Host ""
    Write-Blue "Steps:"
    Write-Host "   1. Open Jira ticket: $TicketNo"
    Write-Host "   2. Click 'Transition'"
    Write-Host "   3. Select 'DEV DONE'"
    if (-not $Customized) {
        Write-Host "   4. Add above comment (if missing)"
    }
    Write-Warning-Msg "========================================"
}

# =============================================================================
# Log File Generation
# =============================================================================

function Update-FinalLog {
    $webSiteName = Read-JsonField -Field "webSiteName"
    $webSiteValue = Read-JsonField -Field "webSiteValue"
    $fixVersion = Read-JsonField -Field "fixVersion"
    $developer = Read-JsonField -Field "developer"
    $apiWhitelabel = Read-JsonField -Field "apiWhiteLabel"
    $currentTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

    try {
        $currentBranch = & git rev-parse --abbrev-ref HEAD 2>$null
    } catch {
        $currentBranch = "unknown"
    }

    # Determine step statuses
    $step2_5Status = "Fill groupInfo - Skipped (newGroup is not true)"
    if ((Read-JsonField -Field "apiWalletInfo.newGroup") -eq "true") {
        $step2_5Status = "Fill groupInfo - Resolved from spreadsheet (group $(Read-JsonField -Field 'apiWalletInfo.group'))"
    }

    $step4Status = "Compile & Test - Build passed"
    $step5Status = if ($Customized -or $TestMode) { "Skipped" } else { "Execute DEV SQL - SQL processed & executed" }

    $step6Status = "Skipped"
    if (-not $Customized -and -not $TestMode) {
        try {
            $commitHash = & git rev-parse --short HEAD 2>$null
            if ($commitHash) {
                $step6Status = "Git Commit - $commitHash"
            }
        } catch {}
    }

    $step7Status = if ($Customized) { "Skipped" } else { "Post Comment - Jira updated" }
    $step8Status = if ($Customized) { "Skipped" } else { "Update Status - DEV DONE" }

    $logContent = @"
=== $TicketNo White Label Workflow Execution Log ===
Start time: $($script:StartTime)
End time: $currentTime
Branch: $currentBranch
Mode: customized=$Customized, test=$TestMode
Status: Success

=== Steps Executed ===
1. Start Jira Issue - Retrieved issue data
2. Transform to JSON - Generated configuration
2.5. $step2_5Status
3. Generate Code & SQL - project-tool.sh
4. $step4Status
5. $step5Status
6. $step6Status
7. $step7Status
8. $step8Status

=== Execution Details ===
* Ticket: $TicketNo
* Site name: $webSiteName
* Site ID: $webSiteValue
* API White Label: $(if ($apiWhitelabel -eq 'true') { 'Yes' } else { 'No' })
* Fix Version: $fixVersion
* Developer: $developer
* Customized: $(if ($Customized) { 'Yes' } else { 'No' })
* Test Mode: $(if ($TestMode) { 'Yes' } else { 'No' })

=== Generated Files ===
* Jira JSON: $($script:JiraFile)
* Config JSON: $($script:JsonFile)
* SQL Files: ProjectTool/result/$TicketNo-*-DB-*.sql
* Java Files: src/main/java/...
$(if (-not $Customized -and -not $TestMode) { "* Release SQL: src/release_sql/$fixVersion/MyDB*.sql" })
* Log: $($script:LogFile)
"@

    Set-Content -Path $script:LogFile -Value $logContent -Encoding utf8
    Write-Verbose-Output "Log file updated: $($script:LogFile)"
}

# =============================================================================
# Completion Summary
# =============================================================================

function Show-CompletionSummary {
    $endTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

    Write-Host ""
    Write-Success "========================================"
    Write-Success "Workflow Complete"
    Write-Success "========================================"
    Write-Verbose-Output "Ticket: $TicketNo"
    Write-Verbose-Output "Start time: $($script:StartTime)"
    Write-Verbose-Output "End time: $endTime"
    Write-Verbose-Output "Log file: $($script:LogFile)"
    Write-Host ""
}

# =============================================================================
# Main Execution Flow
# =============================================================================

function Main {
    $overallStatus = 0

    # Display configuration
    Write-Blue "========================================"
    Write-Blue "Configuration"
    Write-Blue "========================================"
    Write-Verbose-Output "Ticket: $TicketNo"
    Write-Verbose-Output "Start time: $($script:StartTime)"
    Write-Verbose-Output ""
    Write-Verbose-Output "Mode Flags:"
    Write-Verbose-Output "  - TEST_MODE: $TestMode"
    Write-Verbose-Output "  - CUSTOMIZED_MODE: $Customized"
    Write-Verbose-Output "  - QUIET_MODE: $Quiet"
    Write-Verbose-Output "  - FROM_STEP: $FromStep"
    Write-Verbose-Output ""
    Write-Verbose-Output "Directories:"
    Write-Verbose-Output "  - SCRIPT_DIR: $($script:ScriptDir)"
    Write-Verbose-Output "  - TOOL_DIR: $($script:ToolDir)"
    Write-Verbose-Output "  - PROJECT_ROOT: $($script:ProjectRoot)"
    Write-Verbose-Output ""
    Write-Verbose-Output "File Paths:"
    Write-Verbose-Output "  - JAR_FILE: $($script:JarFile)"
    Write-Verbose-Output "  - JIRA_FILE: $($script:JiraFile)"
    Write-Verbose-Output "  - JSON_FILE: $($script:JsonFile)"
    Write-Verbose-Output "  - LOG_FILE: $($script:LogFile)"
    Write-Verbose-Output "  - CLAUDE_MD: $($script:ClaudeMd)"
    Write-Verbose-Output ""

    Write-Blue "=== White Label Process: $TicketNo ==="
    Write-Verbose-Output "Mode: customized=$Customized, test=$TestMode, quiet=$Quiet"
    Write-Verbose-Output ""

    # Pre-flight checks
    if (-not (Test-Dependencies)) {
        Write-Error-Msg "Dependency check failed"
        exit 1
    }

    # Show starting step if not from beginning
    if ($FromStep -gt 1) {
        Write-Warning-Msg "Starting from step $FromStep (skipping steps 1-$($FromStep - 1))"
    }

    # CRITICAL STEPS (fail fast)
    if ($FromStep -le 1) {
        if (-not (Invoke-Step1)) { exit 1 }
    }
    if ($FromStep -le 2) {
        if (-not (Invoke-Step2)) { exit 1 }
    }
    # 用 -le 3 而非 -le 2：-FromStep 3 重跑也要重新確認 groupInfo 已填
    if ($FromStep -le 3) {
        if (-not (Invoke-Step2_5)) { exit 1 }
    }
    if ($FromStep -le 3) {
        if (-not (Invoke-Step3)) { exit 1 }
    }

    # PARTIAL FAILURE OK
    if ($FromStep -le 4) {
        if (-not (Invoke-Step4)) {
            Write-Warning-Msg "Step 4 failed (continuing)"
        }
    }

    # OPTIONAL (exit on critical failure, skip if customized)
    if ($FromStep -le 5) {
        if (-not (Invoke-Step5)) { exit 1 }
    }
    if ($FromStep -le 6) {
        if (-not (Invoke-Step6)) { exit 1 }
    }

    # JIRA UPDATES (non-fatal, skip if customized)
    if ($FromStep -le 7) {
        if (-not (Invoke-Step7)) { $overallStatus = 1 }
    }
    if ($FromStep -le 8) {
        if (-not (Invoke-Step8)) { $overallStatus = 1 }
    }

    # Update log file with execution summary
    Update-FinalLog

    Show-CompletionSummary
    return $overallStatus
}

# =============================================================================
# Execute Main Flow
# =============================================================================

$exitCode = Main
exit $exitCode
