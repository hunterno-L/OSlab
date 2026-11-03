# =====================================================================
# ZhiCore AI 升级 —— 端到端演示冒烟脚本（交付计划 §6.2 冻结接口 / 任务书 §5）
#
# 覆盖：
#   S1  学生 A / 学生 B 登录
#   S2  模型配置列表（平台默认项存在、密钥脱敏、响应不含 apiKey）
#   S3  创建学习会话（冻结契约字段齐备）
#   S4  读取会话
#   S5  修改会话（rowVersion 生效；陈旧 rowVersion 必须被拒绝）
#   S6  轮次幂等（同 clientRequestId 返回同一 turnId，不重复执行）
#   S7  轮次轮询到终态（RUNNING 可轮询；mode 只允许 AI / FALLBACK）
#   S8  工具与模型调用预算（<=3 / <=2）
#   S9  跨用户权限（会话、轮次、资料一律拒绝）
#   S10 下一步任务（3-5 个，带 reason 与 estimatedMinutes）
#   S11 活动 start（预测类只下发可见前缀，不含答案与解析）
#   S12 帮助等级在提交之前落库（HINT -> eligibleForObservation=false）
#   S13 提交幂等（同 submissionKey 重放不产生新记录）
#   S14 个人资料增删查
#   S15 旧入口 /chat/ask 与 /learning/tutor/ask 仍可用且响应兼容
#
# 判定原则：
#   - 每个请求一律断言业务 code，HTTP 200 本身不算成功；
#   - 未配置模型时 mode=FALLBACK 是"通过"（降级如实标注），
#     但脚本会明确打印"本轮为规则降级"，绝不把它当作模型回答验收；
#   - 配置真实对话模型与 embedding 后使用 -RequireAiMode：
#     S7/S15b 出现 FALLBACK 即判定失败，防止降级回归被误当验收通过；
#   - 不伪造任何模型输出；缺 key 时相关断言只验结构、权限、幂等与降级。
#
# 安全：口令只从参数或环境变量读取，不写日志、不进报告。
# 报告：artifacts/ai-platform-demo/smoke-<RunKey>.json
# 退出码：0 全部通过（含如实降级，除非指定 -RequireAiMode）；1 存在失败项
# 编码：必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 兼容）
# =====================================================================
param(
    [string]$BaseUrl = $(if ($env:OSLAB_AI_BASE_URL) { $env:OSLAB_AI_BASE_URL } else { 'http://localhost:8121/api' }),
    [string]$StudentAccount = $(if ($env:OSLAB_STUDENT_A_ACCOUNT) { $env:OSLAB_STUDENT_A_ACCOUNT } else { 'student_demo1' }),
    [string]$StudentPassword = $(if ($env:OSLAB_STUDENT_A_PASSWORD) { $env:OSLAB_STUDENT_A_PASSWORD } else { $env:OSLAB_DEMO_PASSWORD }),
    [string]$OtherAccount = $(if ($env:OSLAB_STUDENT_B_ACCOUNT) { $env:OSLAB_STUDENT_B_ACCOUNT } else { 'student_demo2' }),
    [string]$OtherPassword = $(if ($env:OSLAB_STUDENT_B_PASSWORD) { $env:OSLAB_STUDENT_B_PASSWORD } else { $env:OSLAB_DEMO_PASSWORD }),
    [string]$CourseCode = $(if ($env:OSLAB_AI_COURSE) { $env:OSLAB_AI_COURSE } else { 'course.operating-systems' }),
    [string]$SeedNodeCode = 'os.memory.lru',
    [string]$Question = '三个物理块，访问序列 1,2,3,1,4，按 LRU 访问 4 时应该换出哪一页？为什么？',
    [int]$WaitTimeoutSec = 90,
    # 配置了真实模型与 embedding 后使用：本轮出现 FALLBACK 即失败，防止"降级也算通过"。
    [switch]$RequireAiMode,
    [string]$RunKey = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$ReportDir = 'artifacts/ai-platform-demo'
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $repoRoot '后端代码'))) { $repoRoot = Split-Path -Parent $PSScriptRoot }
$reportDirFull = Join-Path $repoRoot $ReportDir
if (-not (Test-Path $reportDirFull)) { New-Item -ItemType Directory -Path $reportDirFull -Force | Out-Null }
$reportPath = Join-Path $reportDirFull "smoke-$RunKey.json"

$script:failures = 0
$script:steps = @()
$script:degraded = $false

function Add-Step([string]$id, [string]$name, [string]$status, [string]$detail) {
    $script:steps += [pscustomobject]@{ id = $id; name = $name; status = $status; detail = $detail }
    $mark = switch ($status) { 'PASS' { '[ OK ]' } 'FAIL' { '[FAIL]' } default { '[SKIP]' } }
    Write-Host "$mark $id $name$(if ($detail) { " —— $detail" })"
    if ($status -eq 'FAIL') { $script:failures++ }
}

function Invoke-Json([object]$WebSession, [string]$Method, [string]$Path, [object]$Body) {
    $uri = "$BaseUrl$Path"
    if ($null -ne $Body) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
        return Invoke-RestMethod -Uri $uri -Method $Method -Body $bytes `
            -ContentType 'application/json; charset=utf-8' -WebSession $WebSession -TimeoutSec 120
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -WebSession $WebSession -TimeoutSec 120
}

function Invoke-Text([object]$WebSession, [string]$Path, [string]$Text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    return Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -Body $bytes `
        -ContentType 'text/plain; charset=utf-8' -WebSession $WebSession -TimeoutSec 120
}

function Invoke-Login([string]$account, [string]$password) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((@{ userAccount = $account; userPassword = $password } | ConvertTo-Json))
    # -SessionVariable 在函数作用域内创建局部变量，必须随响应一起返回，
    # 否则调用方拿到的是空会话，后续请求会被当成"未登录"。
    $resp = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body $bytes `
        -ContentType 'application/json; charset=utf-8' -SessionVariable loginSession -TimeoutSec 30
    return [pscustomobject]@{ resp = $resp; session = $loginSession }
}

Write-Host "=== ZhiCore AI 冒烟 RunKey=$RunKey ==="
Write-Host "后端：$BaseUrl    课程：$CourseCode    节点：$SeedNodeCode"

# ---------------------------------------------------------------------
# S1 登录
# ---------------------------------------------------------------------
$sessionA = $null
$sessionB = $null
if ([string]::IsNullOrWhiteSpace($StudentPassword)) {
    Add-Step 'S1' '学生登录' 'FAIL' '未提供 StudentPassword / OSLAB_STUDENT_A_PASSWORD / OSLAB_DEMO_PASSWORD'
    Write-Host 'SMOKE ABORTED' -ForegroundColor Red
    exit 1
}
try {
    $loginA = Invoke-Login $StudentAccount $StudentPassword
    if ($loginA.resp.code -ne 0) {
        Add-Step 'S1' '学生 A 登录' 'FAIL' "业务码 $($loginA.resp.code)：$($loginA.resp.message)"
        Write-Host 'SMOKE ABORTED' -ForegroundColor Red
        exit 1
    }
    $sessionA = $loginA.session
    Add-Step 'S1' '学生 A 登录' 'PASS' $StudentAccount
} catch {
    Add-Step 'S1' '学生 A 登录' 'FAIL' "后端不可达：$($_.Exception.Message)"
    Write-Host 'SMOKE ABORTED' -ForegroundColor Red
    exit 1
}
if ([string]::IsNullOrWhiteSpace($OtherPassword)) {
    Add-Step 'S1b' '学生 B 登录' 'SKIP' '未提供第二个账号口令，跨用户权限检查将跳过'
} else {
    try {
        $loginB = Invoke-Login $OtherAccount $OtherPassword
        if ($loginB.resp.code -eq 0) {
            $sessionB = $loginB.session
            Add-Step 'S1b' '学生 B 登录' 'PASS' $OtherAccount
        } else {
            Add-Step 'S1b' '学生 B 登录' 'FAIL' "业务码 $($loginB.resp.code)：$($loginB.resp.message)"
        }
    } catch {
        Add-Step 'S1b' '学生 B 登录' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
}

# ---------------------------------------------------------------------
# S2 模型配置列表：脱敏
# ---------------------------------------------------------------------
$profilesOk = $false
try {
    $profiles = Invoke-Json -WebSession $sessionA -Method Get -Path '/ai/model-profiles'
    if ($profiles.code -ne 0) {
        Add-Step 'S2' '模型配置列表' 'FAIL' "业务码 $($profiles.code)：$($profiles.message)"
    } else {
        $items = @($profiles.data)
        $platform = $items | Where-Object { $_.platformDefault } | Select-Object -First 1
        $leaked = $items | Where-Object { $_.PSObject.Properties.Name -contains 'apiKey' }
        $badMask = $items | Where-Object { $_.apiKeyMasked -and $_.apiKeyMasked -notmatch '\*' }
        if (-not $platform) { Add-Step 'S2' '模型配置列表' 'FAIL' '缺少平台默认项' }
        elseif ($leaked) { Add-Step 'S2' '模型配置列表' 'FAIL' '响应含 apiKey 明文字段' }
        elseif ($badMask) { Add-Step 'S2' '模型配置列表' 'FAIL' 'apiKeyMasked 未脱敏' }
        else {
            $profilesOk = $true
            Add-Step 'S2' '模型配置列表' 'PASS' "共 $($items.Count) 项；平台默认 $($platform.model)（$($platform.status)）"
        }
    }
} catch {
    Add-Step 'S2' '模型配置列表' 'FAIL' "请求异常：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S3 创建会话
# ---------------------------------------------------------------------
$sessionId = $null
$rowVersion = 0
try {
    $created = Invoke-Json -WebSession $sessionA -Method Post -Path '/learning/ai/sessions' -Body @{
        courseCode        = $CourseCode
        goal              = '搞懂页面置换：LRU 与 FIFO 的换出差异'
        focus             = 'LEARN'
        timeBudgetMinutes = 20
    }
    if ($created.code -ne 0) {
        Add-Step 'S3' '创建学习会话' 'FAIL' "业务码 $($created.code)：$($created.message)"
    } else {
        $sessionId = $created.data.sessionId
        $rowVersion = $created.data.rowVersion
        $missing = @()
        foreach ($field in @('sessionId', 'courseCode', 'focus', 'status', 'rowVersion', 'createdAt')) {
            if ($null -eq $created.data.$field) { $missing += $field }
        }
        if ($missing.Count -gt 0) { Add-Step 'S3' '创建学习会话' 'FAIL' "冻结契约缺字段：$($missing -join ', ')" }
        else { Add-Step 'S3' '创建学习会话' 'PASS' "sessionId=$sessionId focus=$($created.data.focus) status=$($created.data.status)" }
    }
} catch {
    Add-Step 'S3' '创建学习会话' 'FAIL' "请求异常：$($_.Exception.Message)"
}

if (-not $sessionId) {
    Write-Host 'SMOKE ABORTED（无会话，后续步骤无法执行）' -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------
# S4 读取会话
# ---------------------------------------------------------------------
try {
    $read = Invoke-Json -WebSession $sessionA -Method Get -Path "/learning/ai/sessions/$sessionId"
    if ($read.code -eq 0 -and $read.data.sessionId -eq $sessionId) {
        Add-Step 'S4' '读取学习会话' 'PASS' "courseCode=$($read.data.courseCode)"
    } else {
        Add-Step 'S4' '读取学习会话' 'FAIL' "业务码 $($read.code)：$($read.message)"
    }
} catch {
    Add-Step 'S4' '读取学习会话' 'FAIL' "请求异常：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S5 修改会话：rowVersion 乐观锁
# ---------------------------------------------------------------------
try {
    $stale = Invoke-Json -WebSession $sessionA -Method Patch -Path "/learning/ai/sessions/$sessionId" -Body @{
        goal = '陈旧版本写入'; rowVersion = 999
    }
    if ($stale.code -eq 0) {
        Add-Step 'S5a' '陈旧 rowVersion 被拒绝' 'FAIL' '并发保护失效：陈旧版本竟然写入成功'
    } else {
        Add-Step 'S5a' '陈旧 rowVersion 被拒绝' 'PASS' "业务码 $($stale.code)"
    }
} catch {
    Add-Step 'S5a' '陈旧 rowVersion 被拒绝' 'FAIL' "请求异常：$($_.Exception.Message)"
}
try {
    $patched = Invoke-Json -WebSession $sessionA -Method Patch -Path "/learning/ai/sessions/$sessionId" -Body @{
        timeBudgetMinutes = 15; rowVersion = $rowVersion
    }
    if ($patched.code -eq 0 -and $patched.data.rowVersion -eq ($rowVersion + 1)) {
        $rowVersion = $patched.data.rowVersion
        Add-Step 'S5b' '修改会话（时间预算）' 'PASS' "rowVersion -> $rowVersion，预算 $($patched.data.timeBudgetMinutes) 分钟"
    } else {
        Add-Step 'S5b' '修改会话（时间预算）' 'FAIL' "业务码 $($patched.code)：$($patched.message)"
    }
} catch {
    Add-Step 'S5b' '修改会话（时间预算）' 'FAIL' "请求异常：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S6 + S7 + S8 轮次：幂等、轮询到终态、预算
# ---------------------------------------------------------------------
$clientRequestId = "smoke-$RunKey-1"
$turnId = $null
try {
    $turnBody = @{
        clientRequestId = $clientRequestId
        message         = $Question
        context         = @{ courseCode = $CourseCode; nodeCodes = @($SeedNodeCode) }
    }
    $first = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/sessions/$sessionId/turns" -Body $turnBody
    $second = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/sessions/$sessionId/turns" -Body $turnBody
    if ($first.code -ne 0) {
        Add-Step 'S6' '轮次幂等（clientRequestId）' 'FAIL' "业务码 $($first.code)：$($first.message)"
    } elseif ($second.code -ne 0) {
        Add-Step 'S6' '轮次幂等（clientRequestId）' 'FAIL' "重放业务码 $($second.code)：$($second.message)"
    } elseif ($first.data.turnId -ne $second.data.turnId) {
        Add-Step 'S6' '轮次幂等（clientRequestId）' 'FAIL' "重放产生了新轮次：$($first.data.turnId) vs $($second.data.turnId)"
    } else {
        $turnId = $first.data.turnId
        Add-Step 'S6' '轮次幂等（clientRequestId）' 'PASS' "两次提交同一 turnId=$turnId"
    }
} catch {
    Add-Step 'S6' '轮次幂等（clientRequestId）' 'FAIL' "请求异常：$($_.Exception.Message)"
}

if ($turnId) {
    $deadline = (Get-Date).AddSeconds($WaitTimeoutSec)
    $final = $null
    $polled = 0
    while ((Get-Date) -lt $deadline) {
        $poll = Invoke-Json -WebSession $sessionA -Method Get -Path "/learning/ai/turns/$turnId"
        $polled++
        if ($poll.code -ne 0) { break }
        $final = $poll.data
        if ($final.status -ne 'RUNNING') { break }
        Start-Sleep -Milliseconds 800
    }
    if (-not $final) {
        Add-Step 'S7' '轮次轮询到终态' 'FAIL' '轮询未取到轮次'
    } elseif ($final.status -eq 'RUNNING') {
        Add-Step 'S7' '轮次轮询到终态' 'FAIL' "超过 $WaitTimeoutSec 秒仍为 RUNNING（轮次不允许永远挂着）"
    } else {
        $modeOk = ($final.mode -eq 'AI' -or $final.mode -eq 'FALLBACK')
        $detail = "status=$($final.status) mode=$($final.mode) 轮询 $polled 次"
        if ($final.status -ne 'SUCCEEDED') {
            $detail += "；错误：$($final.errorMessage)"
            Add-Step 'S7' '轮次轮询到终态' 'FAIL' $detail
        } elseif (-not $modeOk) {
            Add-Step 'S7' '轮次轮询到终态' 'FAIL' "mode 非法：$($final.mode)"
        } elseif ([string]::IsNullOrWhiteSpace($final.answer)) {
            Add-Step 'S7' '轮次轮询到终态' 'FAIL' '回答为空：降级也必须给出规则回答'
        } else {
            if ($final.mode -eq 'FALLBACK') {
                $script:degraded = $true
                $reason = ($final.toolTrace | Where-Object { $_.summary } | Select-Object -First 1).summary
                if ($RequireAiMode) {
                    Add-Step 'S7' '轮次轮询到终态（要求模型回答）' 'FAIL' "$detail；本轮为 FALLBACK，原因：$reason"
                } else {
                    Add-Step 'S7' '轮次轮询到终态（规则降级）' 'PASS' "$detail；本轮为 FALLBACK，不是模型回答，原因：$reason"
                }
            } else {
                Add-Step 'S7' '轮次轮询到终态（模型回答）' 'PASS' $detail
            }
        }
        # S8 预算
        if ($final) {
            $usage = $final.usage
            $modelCalls = if ($null -ne $usage.modelCalls) { $usage.modelCalls } else { 0 }
            $toolCalls = if ($null -ne $usage.toolCalls) { $usage.toolCalls } else { 0 }
            if ($modelCalls -gt 2 -or $toolCalls -gt 3) {
                Add-Step 'S8' '工具/模型调用预算' 'FAIL' "modelCalls=$modelCalls（上限 2），toolCalls=$toolCalls（上限 3）"
            } else {
                Add-Step 'S8' '工具/模型调用预算' 'PASS' "modelCalls=$modelCalls，toolCalls=$toolCalls，来源 $(@($final.sources).Count) 条"
            }
        }
    }
} else {
    Add-Step 'S7' '轮次轮询到终态' 'SKIP' '无 turnId'
    Add-Step 'S8' '工具/模型调用预算' 'SKIP' '无 turnId'
}

# ---------------------------------------------------------------------
# S9 跨用户权限
# ---------------------------------------------------------------------
if ($sessionB) {
    $denied = 0
    $total = 0
    try {
        $total++
        $r = Invoke-Json -WebSession $sessionB -Method Get -Path "/learning/ai/sessions/$sessionId"
        if ($r.code -ne 0) { $denied++ } else { Write-Host "      越权读取会话竟然成功" }
    } catch { $denied++ }
    if ($turnId) {
        try {
            $total++
            $r = Invoke-Json -WebSession $sessionB -Method Get -Path "/learning/ai/turns/$turnId"
            if ($r.code -ne 0) { $denied++ } else { Write-Host "      越权读取轮次竟然成功" }
        } catch { $denied++ }
    }
    if ($denied -eq $total -and $total -gt 0) {
        Add-Step 'S9' '跨用户访问被拒绝' 'PASS' "$denied/$total 项均返回非 0 业务码"
    } else {
        Add-Step 'S9' '跨用户访问被拒绝' 'FAIL' "$denied/$total 项被拒绝，存在越权"
    }
} else {
    Add-Step 'S9' '跨用户访问被拒绝' 'SKIP' '无第二个账号会话'
}

# ---------------------------------------------------------------------
# S10 下一步任务
# ---------------------------------------------------------------------
$actions = @()
try {
    $next = Invoke-Json -WebSession $sessionA -Method Get -Path "/learning/ai/next-actions?courseCode=$CourseCode&limit=5"
    if ($next.code -ne 0) {
        Add-Step 'S10' '下一步任务' 'FAIL' "业务码 $($next.code)：$($next.message)"
    } else {
        $actions = @($next.data.actions)
        $badReason = $actions | Where-Object { [string]::IsNullOrWhiteSpace($_.reason) }
        $badMinutes = $actions | Where-Object { -not $_.estimatedMinutes -or $_.estimatedMinutes -le 0 }
        $badRef = $actions | Where-Object { -not $_.targetRef }
        if ($actions.Count -lt 1) { Add-Step 'S10' '下一步任务' 'FAIL' '未返回任何任务（课程索引或发布内容可能为空）' }
        elseif ($actions.Count -gt 5) { Add-Step 'S10' '下一步任务' 'FAIL' "返回 $($actions.Count) 个任务，超出 3-5 上限" }
        elseif ($badReason) { Add-Step 'S10' '下一步任务' 'FAIL' '存在缺少 reason 的任务' }
        elseif ($badMinutes) { Add-Step 'S10' '下一步任务' 'FAIL' '存在缺少 estimatedMinutes 的任务' }
        elseif ($badRef) { Add-Step 'S10' '下一步任务' 'FAIL' '存在缺少 targetRef 的任务' }
        else {
            $kinds = ($actions | ForEach-Object { $_.type }) -join ','
            Add-Step 'S10' '下一步任务' 'PASS' "$($actions.Count) 个任务（$kinds），到期复习 $($next.data.dueReviewCount) 项"
        }
    }
} catch {
    Add-Step 'S10' '下一步任务' 'FAIL' "请求异常：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S11 + S12 + S13 活动：start / 帮助先落库 / 提交幂等
# ---------------------------------------------------------------------
$assessed = $actions | Where-Object { $_.type -in @('QUESTION', 'PREDICT', 'RETEST') } | Select-Object -First 1
if (-not $assessed) {
    Add-Step 'S11' '活动 start' 'SKIP' '本轮下一步里没有评估类任务（QUESTION/PREDICT/RETEST）'
    Add-Step 'S12' '帮助等级先于提交落库' 'SKIP' '无评估任务'
    Add-Step 'S13' '提交幂等（submissionKey）' 'SKIP' '无评估任务'
} else {
    $actionId = $assessed.actionId
    $activityRef = $null
    try {
        $started = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/actions/$actionId/start" -Body @{}
        if ($started.code -ne 0) {
            Add-Step 'S11' '活动 start' 'FAIL' "业务码 $($started.code)：$($started.message)"
        } else {
            $activityRef = $started.data.activityRef
            $leak = @()
            if ($activityRef.PSObject.Properties.Name -contains 'analysis') { $leak += 'analysis' }
            if ($activityRef.PSObject.Properties.Name -contains 'correct') { $leak += 'correct' }
            if ($activityRef.type -eq 'PREDICT' -or $null -ne $activityRef.predictStepIndex) {
                $limit = [int]$activityRef.predictStepIndex
                if (@($activityRef.visibleSteps).Count -gt $limit) { $leak += '可见步骤越过待预测步' }
                if (@($activityRef.visibleSequence).Count -gt ($limit + 1)) { $leak += '可见序列越过待预测步' }
            }
            if ($leak.Count -gt 0) {
                Add-Step 'S11' '活动 start' 'FAIL' "作答前泄露答案：$($leak -join '; ')"
            } else {
                Add-Step 'S11' '活动 start' 'PASS' "type=$($activityRef.type) helpLevel=$($activityRef.helpLevel) 标题=$($activityRef.title)"
            }
        }
    } catch {
        Add-Step 'S11' '活动 start' 'FAIL' "请求异常：$($_.Exception.Message)"
    }

    if ($activityRef) {
        # S12 帮助必须在提交之前落库，并直接影响证据资格
        try {
            $help = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/actions/$actionId/help" -Body @{
                helpLevel = 'HINT'; note = '演示：先要一个提示再作答'
            }
            if ($help.code -ne 0) {
                Add-Step 'S12' '帮助等级先于提交落库' 'FAIL' "业务码 $($help.code)：$($help.message)"
            } elseif ($help.data.helpLevel -ne 'HINT') {
                Add-Step 'S12' '帮助等级先于提交落库' 'FAIL' "帮助等级未生效：$($help.data.helpLevel)"
            } elseif ($help.data.eligibleForObservation) {
                Add-Step 'S12' '帮助等级先于提交落库' 'FAIL' '带提示作答仍被计为独立观测（掌握度会被误写）'
            } else {
                Add-Step 'S12' '帮助等级先于提交落库' 'PASS' "helpLevel=HINT，eligibleForObservation=false（$($help.data.eligibilityReason)）"
            }
        } catch {
            Add-Step 'S12' '帮助等级先于提交落库' 'FAIL' "请求异常：$($_.Exception.Message)"
        }

        # S13 提交幂等
        $submissionKey = if ($activityRef.submissionKey) { $activityRef.submissionKey } else { "smoke-sub-$RunKey" }
        $submitBody = @{ submissionKey = $submissionKey; selfReportedHelp = 'NONE' }
        if ($activityRef.type -eq 'PREDICT') { $submitBody.predictedProcess = '2' }
        elseif ($activityRef.expectsText) { $submitBody.text = '命中后 LRU 会把该页移到最近使用端，因此换出的是最久未用的页。' }
        else { $submitBody.choiceKey = 'A' }
        try {
            $submit1 = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/actions/$actionId/submit" -Body $submitBody
            $submit2 = Invoke-Json -WebSession $sessionA -Method Post -Path "/learning/ai/actions/$actionId/submit" -Body $submitBody
            if ($submit1.code -ne 0) {
                Add-Step 'S13' '提交幂等（submissionKey）' 'FAIL' "业务码 $($submit1.code)：$($submit1.message)"
            } elseif ($submit2.code -ne 0) {
                Add-Step 'S13' '提交幂等（submissionKey）' 'FAIL' "重放业务码 $($submit2.code)：$($submit2.message)"
            } else {
                $evidence = $submit1.data.evidence
                $detail = "result=$($submit1.data.result) correct=$($submit1.data.correct) 重放 result=$($submit2.data.result)"
                if ($evidence -and $evidence.eligibleForObservation) {
                    Add-Step 'S13' '提交幂等（submissionKey）' 'FAIL' "已记录 HINT 仍被计为独立观测：$detail"
                } elseif ($submit1.data.result -ne $submit2.data.result) {
                    Add-Step 'S13' '提交幂等（submissionKey）' 'FAIL' "重放结果不一致：$detail"
                } else {
                    Add-Step 'S13' '提交幂等（submissionKey）' 'PASS' "$detail；证据资格=$($evidence.eligibleForObservation)（$($evidence.eligibilityReason)）"
                }
            }
        } catch {
            Add-Step 'S13' '提交幂等（submissionKey）' 'FAIL' "请求异常：$($_.Exception.Message)"
        }
    }
}

# ---------------------------------------------------------------------
# S14 个人资料：增 / 查 / 删（跨用户删除必须失败）
# ---------------------------------------------------------------------
$materialId = $null
try {
    $createdMaterial = Invoke-Json -WebSession $sessionA -Method Post -Path '/learning/ai/materials' -Body @{
        courseCode = $CourseCode
        title      = "演示笔记-$RunKey"
        text       = "LRU 命中后要把该页移到最近使用端。`n`nFIFO 按装入顺序换出，与命中无关。"
        nodeCodes  = $SeedNodeCode
    }
    if ($createdMaterial.code -ne 0) {
        Add-Step 'S14a' '创建个人资料' 'FAIL' "业务码 $($createdMaterial.code)：$($createdMaterial.message)"
    } else {
        $materialId = $createdMaterial.data.materialId
        Add-Step 'S14a' '创建个人资料' 'PASS' "materialId=$materialId 块数=$($createdMaterial.data.chunkCount) 索引=$($createdMaterial.data.indexStatus) 模型=$($createdMaterial.data.embeddingModel)"
    }
} catch {
    Add-Step 'S14a' '创建个人资料' 'FAIL' "请求异常：$($_.Exception.Message)"
}

if ($materialId) {
    try {
        $list = Invoke-Json -WebSession $sessionA -Method Get -Path "/learning/ai/materials?courseCode=$CourseCode"
        $mine = @($list.data) | Where-Object { $_.materialId -eq $materialId }
        if ($mine) { Add-Step 'S14b' '个人资料列表' 'PASS' "共 $(@($list.data).Count) 份，含本次创建的资料" }
        else { Add-Step 'S14b' '个人资料列表' 'FAIL' '列表中找不到刚创建的资料' }
    } catch {
        Add-Step 'S14b' '个人资料列表' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
    if ($sessionB) {
        try {
            $foreign = Invoke-Json -WebSession $sessionB -Method Delete -Path "/learning/ai/materials/$materialId"
            if ($foreign.code -ne 0) { Add-Step 'S14c' '他人资料不可删除' 'PASS' "业务码 $($foreign.code)" }
            else { Add-Step 'S14c' '他人资料不可删除' 'FAIL' '越权删除成功' }
        } catch {
            Add-Step 'S14c' '他人资料不可删除' 'PASS' '请求被拒绝'
        }
    } else {
        Add-Step 'S14c' '他人资料不可删除' 'SKIP' '无第二个账号会话'
    }
    try {
        $deleted = Invoke-Json -WebSession $sessionA -Method Delete -Path "/learning/ai/materials/$materialId"
        if ($deleted.code -eq 0) { Add-Step 'S14d' '删除个人资料' 'PASS' '演示资料已清理，不留残余' }
        else { Add-Step 'S14d' '删除个人资料' 'FAIL' "业务码 $($deleted.code)：$($deleted.message)" }
    } catch {
        Add-Step 'S14d' '删除个人资料' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
} else {
    Add-Step 'S14b' '个人资料列表' 'SKIP' '未创建资料'
    Add-Step 'S14c' '他人资料不可删除' 'SKIP' '未创建资料'
    Add-Step 'S14d' '删除个人资料' 'SKIP' '未创建资料'
}

# ---------------------------------------------------------------------
# S15 旧入口兼容
# ---------------------------------------------------------------------
try {
    $chat = Invoke-Text -WebSession $sessionA -Path '/chat/ask' -Text '什么是页面置换？'
    if ($chat.code -eq 0 -and -not [string]::IsNullOrWhiteSpace($chat.data)) {
        Add-Step 'S15a' '旧入口 /chat/ask' 'PASS' "回答长度 $($chat.data.Length) 字符（经统一网关）"
    } elseif ($chat.code -ne 0 -and $chat.message -match '未配置|不可用') {
        $script:degraded = $true
        Add-Step 'S15a' '旧入口 /chat/ask' 'PASS' "如实返回未配置/不可用：$($chat.message)（不伪造回答）"
    } else {
        Add-Step 'S15a' '旧入口 /chat/ask' 'FAIL' "业务码 $($chat.code)：$($chat.message)"
    }
} catch {
    Add-Step 'S15a' '旧入口 /chat/ask' 'FAIL' "请求异常：$($_.Exception.Message)"
}

try {
    $tutor = Invoke-Json -WebSession $sessionA -Method Post -Path '/learning/tutor/ask' -Body @{
        nodeCode = $SeedNodeCode; question = 'LRU 命中后为什么要更新顺序？'
    }
    if ($tutor.code -ne 0) {
        Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'FAIL' "业务码 $($tutor.code)：$($tutor.message)"
    } else {
        $legacyMissing = @()
        foreach ($field in @('answer', 'sources', 'retrievalTrace', 'mode')) {
            if ($null -eq $tutor.data.$field) { $legacyMissing += $field }
        }
        if ($legacyMissing.Count -gt 0) {
            Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'FAIL' "旧契约缺字段：$($legacyMissing -join ', ')"
        } elseif ($tutor.data.mode -ne 'AI' -and $tutor.data.mode -ne 'FALLBACK') {
            Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'FAIL' "mode 非法：$($tutor.data.mode)"
        } elseif ($tutor.data.mode -eq 'FALLBACK' -and $RequireAiMode) {
            $script:degraded = $true
            Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'FAIL' "要求 mode=AI，实际 FALLBACK；来源 $(@($tutor.data.sources).Count) 条"
        } else {
            if ($tutor.data.mode -eq 'FALLBACK') { $script:degraded = $true }
            Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'PASS' "mode=$($tutor.data.mode) 来源 $(@($tutor.data.sources).Count) 条，回答长度 $($tutor.data.answer.Length)"
        }
    }
} catch {
    Add-Step 'S15b' '旧入口 /learning/tutor/ask' 'FAIL' "请求异常：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# 报告
# ---------------------------------------------------------------------
$report = [pscustomobject]@{
    runKey      = $RunKey
    baseUrl     = $BaseUrl
    courseCode  = $CourseCode
    sessionId   = $sessionId
    turnId      = $turnId
    degraded    = $script:degraded
    generatedAt = (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz')
    failures    = $script:failures
    steps       = $script:steps
}
$report | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host ""
Write-Host "报告：$reportPath"
if ($script:failures -gt 0) {
    Write-Host "SMOKE FAILED（$($script:failures) 项）" -ForegroundColor Red
    exit 1
}
if ($script:degraded) {
    Write-Host 'SMOKE PASSED WITH FALLBACK（链路正确，但本轮为规则降级：配置 AI_ARK_API_KEY 与 AI_ARK_EMBEDDING_MODEL 后重跑才算模型验收）' -ForegroundColor Yellow
    exit 0
}
Write-Host 'SMOKE PASSED' -ForegroundColor Green
exit 0
