[CmdletBinding()]
param([switch]$Services)

# Stop only this checkout's Java application and Vue development server.
$ErrorActionPreference = "Stop"
$BackendJar = Join-Path $PSScriptRoot "后端代码\osLab-backend\target\springboot-init-0.0.1-SNAPSHOT.jar"
$FrontendDir = Join-Path $PSScriptRoot "前端代码\osLab-frontend"
$FrontendCli = Join-Path $FrontendDir "node_modules\@vue\cli-service\bin\vue-cli-service.js"

try {
    $targets = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='node.exe'" | Where-Object {
        $_.CommandLine -and (
            ($_.Name -eq "java.exe" -and $_.CommandLine.IndexOf($BackendJar, [StringComparison]::OrdinalIgnoreCase) -ge 0) -or
            ($_.Name -eq "node.exe" -and $_.CommandLine.IndexOf($FrontendCli, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
                $_.CommandLine -match 'vue-cli-service.+\bserve\b')
        )
    })
    foreach ($process in $targets) {
        & taskkill.exe /PID $process.ProcessId /T /F 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue)) {
            throw "停止 PID $($process.ProcessId) 失败。"
        }
        Write-Host "[停止] $($process.Name)（PID $($process.ProcessId)）" -ForegroundColor Green
    }
    if ($targets.Count -eq 0) { Write-Host "[信息] 本项目的前端、后端均未运行。" }

    if ($Services) {
        foreach ($name in @("MySQL", "neo4j", "Redis")) {
            $service = Get-Service -Name $name -ErrorAction SilentlyContinue
            if ($service -and $service.Status -eq "Running") {
                Stop-Service -Name $name
                Write-Host "[服务] 已停止 $name"
            }
        }
    } else { Write-Host "[信息] 数据库服务保持运行。" }
    Write-Host "OSLab 本地应用已停止。" -ForegroundColor Green
} catch {
    Write-Host "[失败] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
