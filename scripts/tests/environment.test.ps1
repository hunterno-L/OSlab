$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $repoRoot 'scripts/lib/import-env.ps1')
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('oslab-env-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory | Out-Null
$assertions = 0
function Assert-EnvironmentTest {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
    $script:assertions++
}

try {
    $testFile = Join-Path $testDirectory '.env'
    [System.IO.File]::WriteAllText($testFile, @'
# fixture: assignment only, including literal shell syntax
OSLAB_ENV_TEST_QUOTED="a=b # 中文"
export OSLAB_ENV_TEST_EMPTY=
OSLAB_ENV_TEST_LITERAL=$(throw 'must not run')
'@)
    $values = Read-ProjectEnvironment $testFile
    Assert-EnvironmentTest ($values['OSLAB_ENV_TEST_QUOTED'] -eq 'a=b # 中文') 'Quoted data changed'
    Assert-EnvironmentTest ($values['OSLAB_ENV_TEST_LITERAL'] -eq '$(throw ''must not run'')') 'Shell syntax was evaluated'
    Assert-EnvironmentTest ($values['OSLAB_ENV_TEST_EMPTY'] -eq '') 'Empty assignment changed'
    $priorValue = [Environment]::GetEnvironmentVariable('OSLAB_ENV_TEST_QUOTED', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('OSLAB_ENV_TEST_QUOTED', 'process-wins', 'Process')
        Import-ProjectEnvironment $testFile
        Assert-EnvironmentTest ($env:OSLAB_ENV_TEST_QUOTED -eq 'process-wins') 'Existing process value was overridden'
        Assert-EnvironmentTest ($env:OSLAB_ENV_TEST_LITERAL -eq $values['OSLAB_ENV_TEST_LITERAL']) 'Literal assignment not imported'
    } finally {
        [Environment]::SetEnvironmentVariable('OSLAB_ENV_TEST_QUOTED', $priorValue, 'Process')
        [Environment]::SetEnvironmentVariable('OSLAB_ENV_TEST_LITERAL', $null, 'Process')
        [Environment]::SetEnvironmentVariable('OSLAB_ENV_TEST_EMPTY', $null, 'Process')
    }

    foreach ($invalid in @("OSLAB_ENV_TEST_ATOMIC=one`ninvalid line", "X=one`nX=two", 'X="unclosed')) {
        [System.IO.File]::WriteAllText($testFile, $invalid)
        $rejected = $false
        try { Import-ProjectEnvironment $testFile } catch { $rejected = $true }
        Assert-EnvironmentTest $rejected 'Invalid environment input was accepted'
        Assert-EnvironmentTest ($null -eq [Environment]::GetEnvironmentVariable('OSLAB_ENV_TEST_ATOMIC', 'Process')) 'Invalid file partially changed environment'
    }

    # Exercise the real generator functions with a temporary target, without starting Docker.
    $parseErrors = $null
    $tokens = $null
    $launcherAst = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $repoRoot 'run-local.ps1'), [ref]$tokens, [ref]$parseErrors)
    Assert-EnvironmentTest ($parseErrors.Count -eq 0) 'Compose launcher syntax failed'
    foreach ($name in @('New-LocalSecret', 'Initialize-EnvironmentFile')) {
        $functionAst = $launcherAst.Find({ param($item) $item -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $item.Name -eq $name }, $true)
        . ([scriptblock]::Create($functionAst.Extent.Text))
    }
    $environmentTemplate = Join-Path $repoRoot 'deploy/.env.example'
    $environmentFile = Join-Path $testDirectory 'compose.env'
    Initialize-EnvironmentFile
    $generated = Read-ProjectEnvironment $environmentFile
    $secretNames = @('MYSQL_ROOT_PASSWORD','MYSQL_APP_PASSWORD','REDIS_PASSWORD','NEO4J_PASSWORD','SANDBOX_AUTH_SECRET','OSLAB_AI_MASTER_KEY')
    foreach ($name in $secretNames) {
        Assert-EnvironmentTest ($generated[$name] -match '^[0-9a-f]{64}$') "Generator did not replace $name"
    }
    Assert-EnvironmentTest (@($secretNames | ForEach-Object { $generated[$_] } | Select-Object -Unique).Count -eq 6) 'Secrets are not independent'
    $before = [System.IO.File]::ReadAllText($environmentFile)
    Initialize-EnvironmentFile
    Assert-EnvironmentTest ($before -ceq [System.IO.File]::ReadAllText($environmentFile)) 'Existing environment was rewritten'
    Assert-EnvironmentTest ($generated['OSLAB_HTTP_PORT'] -eq '80') 'Port default changed'
    $startFunction = $launcherAst.Find({ param($item) $item -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $item.Name -eq 'Start-Platform' }, $true)
    . ([scriptblock]::Create($startFunction.Extent.Text))
    function Invoke-Compose { param([string[]]$ComposeArguments) $script:capturedCompose = $ComposeArguments }
    function Wait-ForPlatform { $script:observedPort = $Port }
    $previousPort = [Environment]::GetEnvironmentVariable('OSLAB_HTTP_PORT', 'Process')
    try {
        [System.IO.File]::WriteAllText($environmentFile, $before.Replace('OSLAB_HTTP_PORT=80', 'OSLAB_HTTP_PORT=8088'))
        [Environment]::SetEnvironmentVariable('OSLAB_HTTP_PORT', $null, 'Process')
        $portWasSpecified = $false
        $Port = 80
        $NoBuild = $true
        Start-Platform
        Assert-EnvironmentTest ($observedPort -eq 8088 -and $env:OSLAB_HTTP_PORT -eq '8088') 'Template port was ignored'
        Assert-EnvironmentTest ($capturedCompose -notcontains '--build') 'NoBuild was ignored'
        $portWasSpecified = $true
        $Port = 8090
        Start-Platform
        Assert-EnvironmentTest ($observedPort -eq 8090) 'Explicit port did not override file'
    } finally {
        [Environment]::SetEnvironmentVariable('OSLAB_HTTP_PORT', $previousPort, 'Process')
    }
    Write-Output "Environment checks passed: $assertions"
} finally {
    $resolvedTestDirectory = [System.IO.Path]::GetFullPath($testDirectory)
    $tempParent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTestDirectory.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedTestDirectory -Leaf) -like 'oslab-env-test-*') {
        Remove-Item -LiteralPath $resolvedTestDirectory -Recurse -Force
    }
}
