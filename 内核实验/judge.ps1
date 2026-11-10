# xv6 判题脚本：提交目录 → 覆盖到 starter → WSL 构建 → QEMU 跑测试 → PASS/FAIL
param(
  [Parameter(Mandatory = $true)][string]$Submission,
  [string]$Test = "usertests -q",
  [int]$TimeoutSec = 240,
  [string]$WorkDir = "G:\ZhiCore\kernel-lab\judge-run"
)

$ErrorActionPreference = "Stop"
$starter = "G:\ZhiCore\kernel-lab\xv6-lab\xv6-riscv"
if (-not (Test-Path $starter)) { throw "starter 不存在: $starter" }

if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
Copy-Item $starter $WorkDir -Recurse
if (Test-Path $Submission) {
  Copy-Item "$Submission\*" $WorkDir -Recurse -Force
}

$wslDir = "/mnt/" + $WorkDir.Substring(0, 1).ToLower() + $WorkDir.Substring(2).Replace("\", "/")
$testCmd = $Test
$script = @"
cd $wslDir || exit 2
make clean >/dev/null 2>&1
if ! make TOOLPREFIX=riscv64-linux-gnu- -j2 >build.log 2>&1; then echo BUILD_FAILED; tail -20 build.log; exit 3; fi
make TOOLPREFIX=riscv64-linux-gnu- fs.img >>build.log 2>&1 || { echo FS_FAILED; exit 4; }
(sleep 4; printf '$testCmd\n'; sleep $TimeoutSec) | timeout $($TimeoutSec + 40) make TOOLPREFIX=riscv64-linux-gnu- qemu > run.log 2>&1
if grep -q 'ALL TESTS PASSED' run.log; then echo RESULT=PASS; exit 0; fi
echo RESULT=FAIL
tail -15 run.log
exit 1
"@
$start = Get-Date
$out = wsl -d Ubuntu-24.04 -u root bash -lc $script 2>&1
$code = $LASTEXITCODE
$seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)

$result = if ($out -match "RESULT=PASS") { "PASS" } elseif ($out -match "BUILD_FAILED") { "BUILD_ERROR" } else { "FAIL" }
[pscustomobject]@{
  result  = $result
  seconds = $seconds
  exit    = $code
  logTail = ($out | Select-Object -Last 12) -join "`n"
} | ConvertTo-Json -Depth 3
