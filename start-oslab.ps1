[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$Status,
    [ValidateRange(1, 65535)][int]$BackendPort = 8121,
    [ValidateRange(1, 65525)][int]$FrontendPort = 8081,
    [string]$JavaHome
)

# Native Windows development launcher. Docker users should use run-local.ps1.
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$BackendDir = Join-Path $Root "后端代码\osLab-backend"
$BackendJar = Join-Path $BackendDir "target\springboot-init-0.0.1-SNAPSHOT.jar"
$FrontendDir = Join-Path $Root "前端代码\osLab-frontend"
$FrontendCli = Join-Path $FrontendDir "node_modules\@vue\cli-service\bin\vue-cli-service.js"
$LogDir = Join-Path $Root "tmp"
$RuntimeJar = Join-Path $LogDir "backend-runtime\springboot-init-0.0.1-SNAPSHOT.jar"
$StartedProcesses = @()

function Get-ProjectProcess {
    param([string]$Kind)
    $name = if ($Kind -eq "backend") { "java.exe" } else { "node.exe" }
    Get-CimInstance Win32_Process -Filter "Name='$name'" | Where-Object {
        if (-not $_.CommandLine) { return $false }
        if ($Kind -eq "backend") {
            $_.CommandLine.IndexOf($BackendJar, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                $_.CommandLine.IndexOf($RuntimeJar, [StringComparison]::OrdinalIgnoreCase) -ge 0
        } else {
            $_.CommandLine.IndexOf($FrontendDir, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
                $_.CommandLine -match 'node_modules[\\/](?:\.bin[\\/]+\.\.[\\/])?@vue[\\/]cli-service[\\/]bin[\\/]vue-cli-service\.js.*\bserve\b'
        }
    }
}

function Get-ProcessPort {
    param($ProjectProcess)
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.OwningProcess -in @($ProjectProcess | ForEach-Object { $_.ProcessId }) } |
        Select-Object -ExpandProperty LocalPort -Unique
}

function Resolve-JavaHome {
    $candidates = @($JavaHome, $env:JAVA_HOME)
    $jdks = Join-Path $env:USERPROFILE ".jdks"
    if (-not $JavaHome -and (Test-Path -LiteralPath $jdks)) {
        $candidates += @(Get-ChildItem -LiteralPath $jdks -Directory | Sort-Object Name | Select-Object -ExpandProperty FullName)
    }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $JavaHome -and $javaCommand) {
        $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
    if ($JavaHome) { $candidates = @($JavaHome) }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $release = Join-Path $candidate "release"
        if (-not (Test-Path -LiteralPath $release)) { continue }
        $version = Get-Content -LiteralPath $release | Select-String '^JAVA_VERSION="(\d+)'
        if ($version -and [int]$version.Matches[0].Groups[1].Value -ge 17 -and
            (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe"))) {
            return $candidate
        }
    }
    throw "需要 JDK 17 或更新版本。请设置 JAVA_HOME，或使用 -JavaHome 指定 JDK 目录。"
}

function Clear-EncodingMismatchedMavenStatus {
    param([string]$BackendDirectory)
    $statusRoot = Join-Path $BackendDirectory "target\maven-status"
    if (-not (Test-Path -LiteralPath $statusRoot)) { return }
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    foreach ($file in @(Get-ChildItem -LiteralPath $statusRoot -Filter "inputFiles.lst" -Recurse -File)) {
        try {
            [void]$utf8.GetCharCount([System.IO.File]::ReadAllBytes($file.FullName))
        } catch {
            # 增量状态由其他默认编码的 JDK 写入（如 GBK）时，当前 JDK 按 UTF-8 读取会报 Input length = 1
            Remove-Item -LiteralPath $statusRoot -Recurse -Force
            Write-Host "[后端] 已清理与当前 JDK 编码不一致的 Maven 增量状态。"
            return
        }
    }
}

function Start-ProjectProcess {
    param([string]$FilePath, [string[]]$Arguments, [string]$Directory, [string]$LogName, [hashtable]$Environment = @{})
    $previous = @{}
    try {
        foreach ($key in $Environment.Keys) {
            $previous[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
            [Environment]::SetEnvironmentVariable($key, $Environment[$key], "Process")
        }
        Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $Directory `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $LogDir "$LogName.log") `
            -RedirectStandardError (Join-Path $LogDir "$LogName.err.log")
    } finally {
        foreach ($key in $previous.Keys) {
            [Environment]::SetEnvironmentVariable($key, $previous[$key], "Process")
        }
    }
}

function Wait-ProjectHttp {
    param($ProjectProcess, [int]$Port, [string]$Path, [int]$TimeoutSec, [switch]$Health)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $uri = "http://localhost:$Port$Path"
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-Process -Id $ProjectProcess.Id -ErrorAction SilentlyContinue)) {
            throw "进程提前退出：$uri。请查看 tmp 下对应的 .log / .err.log。"
        }
        $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.OwningProcess -eq $ProjectProcess.Id }
        if ($listener) {
            try {
                if ($Health) {
                    $response = Invoke-RestMethod -Uri $uri -TimeoutSec 3
                    if ($response.code -eq 0) { return }
                } else {
                    $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 3
                    if ($response.StatusCode -eq 200) { return }
                }
            } catch { }
        }
        Start-Sleep -Seconds 2
    }
    throw "等待服务超时：$uri。请查看 tmp 下对应的 .log / .err.log。"
}

try {
    $backend = @(Get-ProjectProcess "backend")
    $frontend = @(Get-ProjectProcess "frontend")
    if ($Status) {
        foreach ($kind in @("backend", "frontend")) {
            $processes = if ($kind -eq "backend") { $backend } else { $frontend }
            $ports = @(Get-ProcessPort $processes)
            if (@($processes).Count -gt 0) {
                Write-Host "[$kind] PID $($processes.ProcessId -join ', ')；监听端口：$($ports -join ', ')"
            } else { Write-Host "[$kind] 未运行" }
        }
        Get-Service -Name MySQL, neo4j, Redis -ErrorAction SilentlyContinue | Format-Table Name, Status
        exit 0
    }

    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    if ($frontend.Count -gt 0) {
        $ports = @(Get-ProcessPort $frontend)
        if ($ports.Count -ne 1) { throw "本项目已有前端进程但监听状态不明确，请先运行 stop-oslab.ps1。" }
        $FrontendPort = $ports[0]
    } else {
        $freePort = $null
        foreach ($candidate in $FrontendPort..($FrontendPort + 10)) {
            if ($candidate -ne $BackendPort -and -not (Get-NetTCPConnection -LocalPort $candidate -State Listen -ErrorAction SilentlyContinue)) {
                $freePort = $candidate
                break
            }
        }
        if (-not $freePort) { throw "未找到空闲前端端口，请用 -FrontendPort 指定其他起始端口。" }
        $FrontendPort = $freePort
    }
    if ($backend.Count -gt 0) {
        $ports = @(Get-ProcessPort $backend)
        if ($ports.Count -ne 1) { throw "本项目已有后端进程但监听状态不明确，请先运行 stop-oslab.ps1。" }
        $BackendPort = $ports[0]
    } elseif (Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue) {
        throw "后端端口 $BackendPort 被其他程序占用，请用 -BackendPort 指定其他端口。"
    }

    $node = Get-Command node.exe -ErrorAction Stop
    if ($backend.Count -eq 0) { $resolvedJavaHome = Resolve-JavaHome }
    if ($frontend.Count -eq 0 -and -not (Test-Path -LiteralPath $FrontendCli)) {
        Write-Host "[前端] 安装依赖..."
        Push-Location $FrontendDir
        try {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci 失败。" }
        } finally { Pop-Location }
    }
    foreach ($name in @("MySQL", "neo4j", "Redis")) {
        $service = Get-Service -Name $name -ErrorAction SilentlyContinue
        if ($service -and $service.Status -ne "Running") {
            Write-Host "[服务] 启动 $name..."
            Start-Service -Name $name
        }
    }

    if ($backend.Count -eq 0) {
        if (-not $NoBuild) {
            Write-Host "[后端] 编译当前源码（JDK: $resolvedJavaHome）..."
            $oldJavaHome = $env:JAVA_HOME
            Push-Location $BackendDir
            try {
                $env:JAVA_HOME = $resolvedJavaHome
                Clear-EncodingMismatchedMavenStatus $BackendDir
                & .\mvnw.cmd -DskipTests package
                if ($LASTEXITCODE -ne 0) { throw "后端构建失败。" }
            } finally {
                $env:JAVA_HOME = $oldJavaHome
                Pop-Location
            }
        }
        if (-not (Test-Path -LiteralPath $BackendJar)) { throw "找不到后端 JAR，请去掉 -NoBuild 后重试。" }
        $origins = @($env:CORS_ALLOWED_ORIGINS)
        foreach ($port in @(8080..8090) + @($FrontendPort)) {
            $origins += "http://localhost:$port", "http://127.0.0.1:$port"
        }
        # A running JAR is locked on Windows; execute a copy so Maven can rebuild target.
        New-Item -ItemType Directory -Path (Split-Path $RuntimeJar -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $BackendJar -Destination $RuntimeJar -Force
        $backendArgs = @("-Dfile.encoding=UTF-8", "-jar", "`"$RuntimeJar`"", "--server.port=$BackendPort", "--cors.allowed-origins=$($origins -join ',')")
        Write-Host "[后端] 启动中；日志：tmp\backend.log"
        $process = Start-ProjectProcess (Join-Path $resolvedJavaHome "bin\java.exe") $backendArgs $Root "backend"
        $StartedProcesses += $process
        Wait-ProjectHttp $process $BackendPort "/api/health/liveness" 120 -Health
    } else {
        $response = Invoke-RestMethod -Uri "http://localhost:$BackendPort/api/health/liveness" -TimeoutSec 5
        if ($response.code -ne 0) { throw "已有后端进程未通过健康检查，请先停止后重新启动。" }
        Write-Host "[后端] 已在运行；如需载入代码修改，请先停止再启动。"
    }

    if ($frontend.Count -eq 0) {
        Write-Host "[前端] 启动中（首次编译可能需要几分钟）；日志：tmp\frontend.log"
        $frontendArgs = @("`"$FrontendCli`"", "serve", "--host", "localhost", "--port", "$FrontendPort")
        $process = Start-ProjectProcess $node.Source $frontendArgs $FrontendDir "frontend" @{
            VUE_APP_API_TARGET = "http://localhost:$BackendPort"
        }
        $StartedProcesses += $process
        Wait-ProjectHttp $process $FrontendPort "/" 600
    } else { Write-Host "[前端] 已在运行。" }
    $proxyHealth = Invoke-RestMethod -Uri "http://localhost:$FrontendPort/api/health/liveness" -TimeoutSec 10
    if ($proxyHealth.code -ne 0) { throw "前端到后端的 /api 代理健康检查未通过。" }

    Write-Host "`nOSLab 本地环境已就绪" -ForegroundColor Green
    Write-Host "  前端：http://localhost:$FrontendPort/"
    Write-Host "  后端：http://localhost:$BackendPort/api"
    Write-Host "  状态：.\start-oslab.ps1 -Status"
    Write-Host "  停止：.\stop-oslab.ps1"
} catch {
    foreach ($process in $StartedProcesses) {
        if (Get-Process -Id $process.Id -ErrorAction SilentlyContinue) {
            & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Null
        }
    }
    Write-Host "[失败] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
