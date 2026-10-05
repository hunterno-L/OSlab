# ============================================================
# OSLab 本地环境启动脚本
# 用法:  powershell -ExecutionPolicy Bypass -File .\start-oslab.ps1
#
# 做三件事:
#   1. 确保 Windows 服务 MySQL / neo4j / Redis 在运行(需要管理员权限才能启动,
#      若服务本就在运行则无需管理员)
#   2. 启动后端 Spring Boot jar, 等待 :8121 就绪
#   3. 启动前端 Vue dev server, 等待 8080/8081 就绪(8080 被其他程序占用时自动用 8081)
#
# 日志: tmp\backend.log / tmp\backend.err.log / tmp\frontend.log
# ============================================================

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$JavaExe = "C:\Users\one\.jdks\ms-21.0.10\bin\java.exe"
$BackendJar = Join-Path $Root "后端代码\osLab-backend\target\springboot-init-0.0.1-SNAPSHOT.jar"
$FrontendDir = Join-Path $Root "前端代码\osLab-frontend"
$LogDir = Join-Path $Root "tmp"

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# ---------- 工具函数 ----------

function Test-PortOwned {
    # 端口被"我们自己的进程"监听才返回真(按命令行特征匹配, 避免认领别人的 8080)
    param([int]$Port, [string]$Pattern)
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($c.OwningProcess)" -ErrorAction SilentlyContinue
        if ($p -and $p.CommandLine -and ($p.CommandLine -match $Pattern)) { return $true }
    }
    return $false
}

function Wait-PortOwned {
    param([int]$Port, [string]$Pattern, [int]$TimeoutSec, [string]$What)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOwned -Port $Port -Pattern $Pattern) { return $true }
        Start-Sleep -Seconds 2
    }
    Write-Host "[失败] $What 在 ${TimeoutSec}s 内未就绪 (端口 $Port), 请查看日志" -ForegroundColor Red
    return $false
}

# ---------- 1. Windows 服务 ----------

$services = @("MySQL", "neo4j", "Redis")
foreach ($name in $services) {
    $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Host "[跳过] 未找到服务 $name" -ForegroundColor Yellow
        continue
    }
    if ($svc.Status -eq "Running") {
        Write-Host "[服务] $name 已在运行" -ForegroundColor DarkGray
    } else {
        try {
            Start-Service -Name $name
            Write-Host "[服务] 已启动 $name" -ForegroundColor Green
        } catch {
            Write-Host "[警告] 无法启动服务 $name (可能需要管理员权限): $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

# ---------- 2. 后端 (:8121, context-path /api) ----------

$BackendPattern = "springboot-init.*SNAPSHOT\.jar"

if (-not (Test-Path $BackendJar)) {
    Write-Host "[失败] 找不到后端 jar: $BackendJar" -ForegroundColor Red
    Write-Host "        请先构建: cd 后端代码\osLab-backend && .\mvnw package -DskipTests" -ForegroundColor Yellow
    exit 1
}

if (Test-PortOwned -Port 8121 -Pattern $BackendPattern) {
    Write-Host "[后端] 已在运行 (:8121), 跳过" -ForegroundColor DarkGray
} else {
    if (-not (Test-Path $JavaExe)) {
        $JavaExe = "java"   # 回退到 PATH 里的 java
    }
    # 本地环境必需 env(java 子进程会继承当前会话的环境变量):
    #   Neo4j 密码 yml 默认为空, 本机服务密码是 11111111, 不设则图谱类接口认证失败
    #   判题沙箱指向本地回显 stub(python scripts/dev/sandbox_stub.py, 端口 8090)
    $env:SPRING_NEO4J_AUTHENTICATION_PASSWORD = "11111111"
    $env:CODESANDBOX_TYPE = "remote"
    $env:CODESANDBOX_REMOTE_URL = "http://127.0.0.1:8090/executeCode"
    $env:CODESANDBOX_REMOTE_AUTH_SECRET = "smoke-secret"
    Write-Host "[后端] 启动中... 日志: tmp\backend.log"
    Start-Process -FilePath $JavaExe `
        -ArgumentList @("-jar", $BackendJar) `
        -WorkingDirectory $Root `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogDir "backend.log") `
        -RedirectStandardError (Join-Path $LogDir "backend.err.log")
    if (Wait-PortOwned -Port 8121 -Pattern $BackendPattern -TimeoutSec 90 -What "后端") {
        Write-Host "[后端] 就绪: http://localhost:8121/api" -ForegroundColor Green
    }
}

# ---------- 3. 前端 (8080, 被占用则 8081) ----------

$FrontendPattern = "osLab-frontend"

$ownedPort = $null
foreach ($port in @(8080, 8081)) {
    if (Test-PortOwned -Port $port -Pattern $FrontendPattern) { $ownedPort = $port; break }
}

if ($ownedPort) {
    Write-Host "[前端] 已在运行 (:$ownedPort), 跳过" -ForegroundColor DarkGray
} else {
    Write-Host "[前端] 启动中... 日志: tmp\frontend.log"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList @("/c", "npm run serve") `
        -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogDir "frontend.log") `
        -RedirectStandardError (Join-Path $LogDir "frontend.err.log")

    $deadline = (Get-Date).AddSeconds(150)
    while ((Get-Date) -lt $deadline -and -not $ownedPort) {
        foreach ($port in @(8080, 8081)) {
            if (Test-PortOwned -Port $port -Pattern $FrontendPattern) { $ownedPort = $port; break }
        }
        if (-not $ownedPort) { Start-Sleep -Seconds 3 }
    }
    if ($ownedPort) {
        Write-Host "[前端] 就绪: http://localhost:$ownedPort" -ForegroundColor Green
    } else {
        Write-Host "[失败] 前端 150s 内未就绪, 请查看 tmp\frontend.log" -ForegroundColor Red
    }
}

# ---------- 汇总 ----------

Write-Host ""
Write-Host "========== OSLab 本地环境 ==========" -ForegroundColor Cyan
if ($ownedPort) { Write-Host "  前端:  http://localhost:$ownedPort" }
Write-Host "  后端:  http://localhost:8121/api"
Write-Host "  演示账号: teacher_demo / student_demo1 (密码 oslab123456)"
Write-Host "  停止:  powershell -ExecutionPolicy Bypass -File .\stop-oslab.ps1"
Write-Host "====================================" -ForegroundColor Cyan
