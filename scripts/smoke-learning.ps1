# =====================================================================
# OSLab 学习闭环冒烟入口（薄壳）—— 最终检查与收尾计划 §5.1 第 1 条
#
# 本文件只是 scripts/dev/verify-learning.ps1 的转调入口（参数透传），
# 不包含任何检查逻辑，也不包含历史日志恢复或自覆写行为（旧版混入的
# Python 恢复片段已全部删除）。
#
# 用法示例：
#   pwsh -File scripts/smoke-learning.ps1                       # 全默认（Stub 回显沙箱）
#   pwsh -File scripts/smoke-learning.ps1 -JudgeMode None       # 无沙箱环境，判题项标未执行
#   pwsh -File scripts/smoke-learning.ps1 -RunKey demo-1 -JudgeMode Stub
#   pwsh -File scripts/smoke-learning.ps1 -JudgeMode Real -SandboxUrl http://127.0.0.1:8090
#
# 退出码与运行报告由主脚本产生：
#   失败计数>0 或请求/解析异常 → exit 1；全部已执行断言通过才输出 ALL PASSED。
#   报告写入 artifacts/final-check/verify-learning-<RunKey>.json / .txt。
#
# 编码：UTF-8 with BOM（单个 BOM，Windows PowerShell 5.1 兼容）。
# =====================================================================

$mainScript = Join-Path $PSScriptRoot 'dev\verify-learning.ps1'
if (-not (Test-Path $mainScript)) {
    Write-Error "未找到主脚本：$mainScript"
    exit 1
}

# 以调用运算符执行主脚本并透传全部参数；主脚本末尾以 exit <code> 结束，
# 其退出码即本进程退出码（-File 场景下语义确定）。
& $mainScript @args
