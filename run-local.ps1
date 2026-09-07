[CmdletBinding()]
param(
    [ValidateSet("start", "stop", "restart", "status", "logs")]
    [string]$Action = "start",

    [ValidateRange(1, 65535)]
    [int]$Port = 80,

    [switch]$NoBuild,

    [switch]$Follow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
$deployDirectory = Join-Path $workspaceRoot "deploy"
$composeFile = Join-Path $deployDirectory "docker-compose.yml"
$environmentTemplate = Join-Path $deployDirectory ".env.example"
$environmentFile = Join-Path $deployDirectory ".env"
$projectName = "oslab"

function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($bytes)
    } finally {
        $randomGenerator.Dispose()
    }

    return (($bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Test-DockerEnvironment {
    try {
        & docker version --format "{{.Server.Version}}" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker daemon is unavailable"
        }
        & docker compose version | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose v2 is unavailable"
        }
    } catch {
        throw "未检测到可用的 Docker Desktop / Docker Engine 与 Docker Compose v2。请先启动 Docker 后重试。"
    }
}

function Initialize-EnvironmentFile {
    if (-not (Test-Path -LiteralPath $environmentTemplate)) {
        throw "未找到环境变量模板：$environmentTemplate"
    }

    if (Test-Path -LiteralPath $environmentFile) {
        $existingContent = Get-Content -LiteralPath $environmentFile -Raw
        if ($existingContent.Contains("replace-with-a-long-unique")) {
            throw "deploy/.env 仍包含示例密钥。请替换示例值，或删除该文件后让脚本重新生成本地开发密钥。"
        }
        return
    }

    $content = Get-Content -LiteralPath $environmentTemplate -Raw
    $replacements = @{
        "MYSQL_ROOT_PASSWORD=replace-with-a-long-unique-root-password" = "MYSQL_ROOT_PASSWORD=$(New-LocalSecret)"
        "MYSQL_APP_PASSWORD=replace-with-a-long-unique-application-password" = "MYSQL_APP_PASSWORD=$(New-LocalSecret)"
        "REDIS_PASSWORD=replace-with-a-long-unique-redis-password" = "REDIS_PASSWORD=$(New-LocalSecret)"
        "NEO4J_PASSWORD=replace-with-a-long-unique-neo4j-password" = "NEO4J_PASSWORD=$(New-LocalSecret)"
        "SANDBOX_AUTH_SECRET=replace-with-a-long-unique-sandbox-secret" = "SANDBOX_AUTH_SECRET=$(New-LocalSecret)"
    }

    foreach ($source in $replacements.Keys) {
        $content = $content.Replace($source, $replacements[$source])
    }

    $content = $content.TrimEnd() + [Environment]::NewLine +
        [Environment]::NewLine + "# Local frontend host port. Override with -Port when running this script." +
        [Environment]::NewLine + "OSLAB_HTTP_PORT=80" + [Environment]::NewLine
    [System.IO.File]::WriteAllText($environmentFile, $content)
    Write-Host "已创建 deploy/.env，并生成仅供本地使用的随机密钥。" -ForegroundColor Green
}

function Assert-EnvironmentFile {
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        throw "未找到 deploy/.env。请先执行 ./run-local.ps1 启动环境。"
    }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ComposeArguments
    )

    Push-Location $deployDirectory
    try {
        & docker compose --env-file $environmentFile --project-name $projectName @ComposeArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose 命令执行失败。"
        }
    } finally {
        Pop-Location
    }
}

function Wait-ForPlatform {
    $healthUrl = "http://localhost:$Port/api/health/liveness"
    $deadline = (Get-Date).AddMinutes(4)
    Write-Host "正在等待服务就绪：$healthUrl"

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
            if ($response.code -eq 0) {
                Write-Host "OSLab 已就绪：" -ForegroundColor Green
                Write-Host "  平台地址：http://localhost:$Port/"
                Write-Host "  健康检查：$healthUrl"
                return
            }
        } catch {
            # Containers may still be starting; retry until the deadline.
        }
        Start-Sleep -Seconds 3
    }

    Invoke-Compose -ComposeArguments @("ps")
    throw "服务未能在 4 分钟内就绪。请执行 ./run-local.ps1 -Action logs 查看日志。"
}

function Start-Platform {
    Initialize-EnvironmentFile
    $env:OSLAB_HTTP_PORT = [string]$Port
    $arguments = @("up", "--detach")
    if (-not $NoBuild) {
        $arguments += "--build"
    }
    Invoke-Compose -ComposeArguments $arguments
    Wait-ForPlatform
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "未找到 Docker Compose 文件：$composeFile"
}

Test-DockerEnvironment

switch ($Action) {
    "start" {
        Start-Platform
        break
    }
    "stop" {
        Assert-EnvironmentFile
        Invoke-Compose -ComposeArguments @("down")
        Write-Host "OSLab 已停止，数据卷已保留。" -ForegroundColor Green
        break
    }
    "restart" {
        Assert-EnvironmentFile
        Invoke-Compose -ComposeArguments @("down")
        Start-Platform
        break
    }
    "status" {
        Assert-EnvironmentFile
        Invoke-Compose -ComposeArguments @("ps")
        break
    }
    "logs" {
        Assert-EnvironmentFile
        $arguments = @("logs", "--tail", "200")
        if ($Follow) {
            $arguments += "--follow"
        }
        Invoke-Compose -ComposeArguments $arguments
        break
    }
}
