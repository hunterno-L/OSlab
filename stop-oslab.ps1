# ============================================================
# OSLab 本地环境停止脚本
# 用法:  powershell -ExecutionPolicy Bypass -File .\stop-oslab.ps1
#        powershell -ExecutionPolicy Bypass -File .\stop-oslab.ps1 -Services
#
# 默认只停止 OSLab 自己的进程:
#   - 后端: java 进程, 命令行含 springboot-init-0.0.1-SNAPSHOT.jar
#   - 前端: node/esbuild 进程, 命令行含 osLab-frontend 路径
#   按"命令行特征"识别而非端口号, 因此不会误杀恰好占用 8080/8081 的其他程序
#
# 加 -Services 参数时, 同时停止 Windows 服务 MySQL / neo4j / Redis(需要管理员权限)
# ============================================================

param([switch]$Services)

$ErrorActionPreference = "Continue"

function Find-OslabProcesses {
    # 返回命令行匹配特征的所有进程(后端 java + 前端 node/esbuild)
    $result = @()
    $procs = Get-CimInstance Win32_Process | Where-Object {
        $_.CommandLine -and (
            ($_.Name -eq "java.exe" -and $_.CommandLine -match "springboot-init.*SNAPSHOT\.jar") -or
            ($_.Name -in @("node.exe", "esbuild.exe") -and $_.CommandLine -match "osLab-frontend")
        )
    }
    foreach ($p in $procs) { $result += $p }
    return $result
}

function Stop-OslabTree {
    param([int]$ProcessId, [string]$Label)
    # /T 连同子进程一起结束(vue-cli 的 worker、esbuild 服务等)
    & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
    Write-Host "[停止] $Label (PID $ProcessId)" -ForegroundColor Green
}

# ---------- 1. OSLab 应用进程 ----------

$targets = Find-OslabProcesses

if ($targets.Count -eq 0) {
    Write-Host "[信息] 没有发现运行中的 OSLab 后端/前端进程" -ForegroundColor DarkGray
} else {
    foreach ($p in $targets) {
        $label = if ($p.Name -eq "java.exe") { "后端 java" } else { "前端 $($p.Name)" }
        Stop-OslabTree -ProcessId $p.ProcessId -Label $label
    }
    # 等端口释放并复核
    Start-Sleep -Seconds 2
    foreach ($port in @(8080, 8081, 8121)) {
        $left = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Where-Object {
                $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($_.OwningProcess)" -ErrorAction SilentlyContinue
                $owner -and $owner.CommandLine -match "osLab-frontend|springboot-init.*SNAPSHOT\.jar"
            }
        if ($left) {
            Write-Host "[警告] 端口 $port 仍被 OSLab 进程占用: PID $($left[0].OwningProcess)" -ForegroundColor Yellow
            & taskkill.exe /PID $left[0].OwningProcess /T /F 2>&1 | Out-Null
        }
    }
}

# ---------- 2. (可选) Windows 服务 ----------

if ($Services) {
    foreach ($name in @("MySQL", "neo4j", "Redis")) {
        $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
        if (-not $svc) { continue }
        if ($svc.Status -eq "Running") {
            try {
                Stop-Service -Name $name -Force
                Write-Host "[服务] 已停止 $name" -ForegroundColor Green
            } catch {
                Write-Host "[警告] 无法停止服务 $name (需要管理员权限): $($_.Exception.Message)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "[服务] $name 未在运行" -ForegroundColor DarkGray
        }
    }
} else {
    Write-Host "[信息] Windows 服务 (MySQL/neo4j/Redis) 保持运行; 如需一并停止请加 -Services 参数" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "========== OSLab 已停止 ==========" -ForegroundColor Cyan
if (-not $Services) { Write-Host "  (数据库服务仍在后台运行)" }
Write-Host "==================================" -ForegroundColor Cyan
