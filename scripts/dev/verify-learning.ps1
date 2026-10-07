# =====================================================================
# OSLab 学习闭环统一验收脚本（最终检查与收尾 §5 —— Task B-1 修订版）
#
# 规范入口：本脚本是唯一实现；scripts/smoke-learning.ps1 只是转调本脚本的
# 薄入口（透传参数），不再包含任何自恢复或复制逻辑。
#
# 前置：
#   1) MySQL / Redis / Neo4j 运行中；后端启动并监听 BaseUrl（默认 8121 端口）。
#   2) JudgeMode=Stub：先启动回显沙箱 python scripts/dev/sandbox_stub.py，
#      后端以 CODESANDBOX_TYPE=remote CODESANDBOX_REMOTE_URL=http://127.0.0.1:8090
#      CODESANDBOX_REMOTE_AUTH_SECRET=smoke-secret 启动；判题链按回显协议构造用例。
#   3) JudgeMode=Real：真实代码沙箱可达（脚本自动探测 SandboxUrl 的 TCP 连通性）；
#      编程实验样例读取 scripts/dev/samples/（lru_correct / lru_wrong / compile_error），
#      真实用例与回显用例的输入格式不同，不混用。
#   4) JudgeMode=None：跳过全部判题类用例并标注"未执行"，其余检查照常执行。
#
# 参数（全部可覆盖）：
#   BaseUrl / TeacherAccount / StudentAAccount / StudentBAccount / ClassName
#   JudgeMode(Stub|Real|None) / RunKey / ReportPath / SandboxUrl / WaitTimeoutSec
#   口令优先级：TeacherPassword/StudentAPassword/StudentBPassword 参数
#     > 环境变量 OSLAB_TEACHER_PASSWORD / OSLAB_STUDENT_A_PASSWORD / OSLAB_STUDENT_B_PASSWORD
#     > 既有演示口令（oslab123456，按现状保留，可被上述任一途径覆盖）。
#   种子题幂等 UPDATE（仅当种子题 910011 内容过期时才会用到）：
#     DbHost/DbPort/DbUser/DbName/DbPassword（口令只从参数或 OSLAB_DB_PASSWORD 读取，
#     经 MYSQL_PWD 传给 mysql 客户端，不落日志、不进进程命令行）。
#
# 结果与退出码：
#   - 每个请求一律断言业务 code，HTTP 200 本身不算成功；传输或解析异常终止本轮并计为失败。
#   - 失败计数>0 → exit 1；全部已执行断言通过且无跳过 → 打印 ALL PASSED；
#     存在未执行项时打印 PASSED WITH SKIPPED，判题项不伪造 AC/WA 结果。
#   - 报告写入 ReportPath：verify-learning-<RunKey>.json / .txt（UTF-8 BOM），
#     含用例编号/状态/预期/实际、生成的题目/作答/提交/事件 ID、时间与环境类型。
#
# BKT 契约（裁决 L3-R12：5 参模型，答错更新仍含学习转移项）：
#   posterior = p*pSlip / (p*pSlip + (1-p)*(1-pGuess))
#   updated   = posterior + (1-posterior)*pLearn
#   after     = before + weight*(updated-before)
#   pInit=0.10 pLearn=0.10 pGuess=0.20 pSlip=0.10；新节点权重 1 首次答错 ≈ 0.1123287671
#   且状态为薄弱（RED）。掌握度断言对照该公式与证据计数，不再使用"答错不升掌握度"、
#   固定题目数量、固定历史事件总数或固定掌握状态。
#
# 数据标记：本次运行创建的数据以 RunKey 标记（题目标题内嵌 + 报告记录生成 ID），
# 判断口径 = 本次运行前后差值 + 生成 ID 回读，不依赖旧账号的历史作答积累。
#
# 编码：本脚本必须保存为 UTF-8 with BOM（单个 BOM，Windows PowerShell 5.1 兼容）。
# =====================================================================
param(
    [string]$BaseUrl = 'http://localhost:8121/api',
    [string]$TeacherAccount = 'teacher_demo',
    [string]$StudentAAccount = 'student_demo1',
    [string]$StudentBAccount = 'student_demo2',
    [string]$TeacherPassword = $env:OSLAB_TEACHER_PASSWORD,
    [string]$StudentAPassword = $env:OSLAB_STUDENT_A_PASSWORD,
    [string]$StudentBPassword = $env:OSLAB_STUDENT_B_PASSWORD,
    [string]$ClassName = 'os2026',
    [ValidateSet('Stub', 'Real', 'None')]
    [string]$JudgeMode = 'Stub',
    [string]$RunKey = ('B1-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$ReportPath = '',
    [string]$SandboxUrl = 'http://127.0.0.1:8090',
    [int]$WaitTimeoutSec = 30,
    [string]$DbHost = 'localhost',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbName = 'oslab_db',
    [string]$DbPassword = $env:OSLAB_DB_PASSWORD
)

# 口令兜底：既有演示口令按现状保留（参数/环境变量可覆盖），不在报告中记录口令
if ([string]::IsNullOrEmpty($TeacherPassword)) { $TeacherPassword = 'oslab123456' }
if ([string]::IsNullOrEmpty($StudentAPassword)) { $StudentAPassword = 'oslab123456' }
if ([string]::IsNullOrEmpty($StudentBPassword)) { $StudentBPassword = 'oslab123456' }

# 仓库根目录（scripts/dev/ 向上两级）与默认报告目录
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrEmpty($ReportPath)) { $ReportPath = Join-Path $repoRoot 'artifacts/final-check' }

# 常量：主演示节点与种子题内容特征
$LruNode = 'LRU 页面置换算法'
$LruFixContentNeedle = '不需要更新该页的最近访问顺序'
$LruStaleNeedle = '是否需要更新'

# 运行状态
$script:Failures = 0
$script:Skipped = 0
$script:Cases = New-Object System.Collections.Generic.List[object]
$script:Ids = [ordered]@{}
$script:StartedAt = (Get-Date)
$script:SandboxReachable = $false

# =====================================================================
# 通用工具
# =====================================================================

function Set-Id([string]$Key, $Value) {
    if ($null -ne $Value -and "$Value" -ne '') { $script:Ids[$Key] = "$Value" }
}

function Add-Case([string]$Id, [string]$Name, [string]$Status, $Expected, $Actual, [string]$Note = '') {
    $case = [pscustomobject]@{
        id       = $Id
        name     = $Name
        status   = $Status
        expected = "$Expected"
        actual   = "$Actual"
        note     = $Note
    }
    $script:Cases.Add($case)
    $tag = '[PASS]'
    if ($Status -eq 'FAIL') { $tag = '[FAIL]'; $script:Failures++ }
    elseif ($Status -eq 'SKIP') { $tag = '[SKIP]'; $script:Skipped++ }
    $line = "$tag $Id $Name | 预期: $Expected | 实际: $Actual"
    if ($Note -ne '') { $line = "$line | $Note" }
    Write-Host $line
}

function Assert-True([bool]$Condition, [string]$Id, [string]$Name, $Expected, $Actual, [string]$Note = '') {
    if ($Condition) { Add-Case $Id $Name 'PASS' $Expected $Actual $Note }
    else { Add-Case $Id $Name 'FAIL' $Expected $Actual $Note }
}

function Skip-Case([string]$Id, [string]$Name, [string]$Reason) {
    Add-Case $Id $Name 'SKIP' '未执行' $Reason
}

function Assert-ApiOk($Resp, [string]$Id, [string]$Name, [switch]$Fatal) {
    $codeText = '无响应'
    $ok = $false
    if ($null -ne $Resp) { $codeText = "$($Resp.code)"; $ok = ($Resp.code -eq 0) }
    Assert-True $ok $Id $Name 'code=0' "code=$codeText"
    if ($Fatal -and -not $ok) { throw "致命请求失败：$Name（code=$codeText）" }
    return $ok
}

function Invoke-Api([object]$WebSession, [string]$Method, [string]$Path, [object]$Body) {
    $uri = "$BaseUrl$Path"
    try {
        if ($Method -eq 'Post') {
            $json = $Body | ConvertTo-Json -Depth 8
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            return Invoke-RestMethod -Uri $uri -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -WebSession $WebSession -TimeoutSec 60
        }
        return Invoke-RestMethod -Uri $uri -Method Get -WebSession $WebSession -TimeoutSec 60
    } catch {
        # 业务错误可能伴随非 200 状态：尽量取回响应体按 BaseResponse 处理，取不回才按异常终止
        $respObj = $null
        try {
            $raw = $null
            $httpResp = $_.Exception.Response
            if ($null -ne $httpResp) {
                if ($httpResp -is [System.Net.HttpWebResponse]) {
                    $stream = $httpResp.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                    $raw = $reader.ReadToEnd()
                } elseif ($null -ne $httpResp.Content) {
                    $raw = $httpResp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                }
            }
            if (-not [string]::IsNullOrEmpty($raw)) { $respObj = $raw | ConvertFrom-Json }
        } catch { }
        if ($null -ne $respObj -and $null -ne $respObj.PSObject.Properties['code']) { return $respObj }
        throw
    }
}

function Connect-User([string]$Account, [string]$Password) {
    $body = @{ userAccount = $Account; userPassword = $Password } | ConvertTo-Json
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $resp = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -SessionVariable loginSession -TimeoutSec 30
    return @{ resp = $resp; session = $loginSession }
}

function Test-SandboxReachable([string]$Url) {
    try {
        $u = [System.Uri]$Url
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $task = $client.ConnectAsync($u.Host, $u.Port)
            if ($task.Wait(2000) -and $client.Connected) { return $true }
            return $false
        } finally { $client.Close() }
    } catch { return $false }
}

function Wait-SubmitTerminal([object]$Session, [long]$SubmitId, [int]$MaxSec) {
    $path = '/question/question_submit/get?id=' + $SubmitId
    $deadline = (Get-Date).AddSeconds($MaxSec)
    while ((Get-Date) -lt $deadline) {
        $resp = Invoke-Api -WebSession $Session -Method Get -Path $path
        if ($resp.code -eq 0 -and $null -ne $resp.data -and ($resp.data.status -eq 2 -or $resp.data.status -eq 3)) { return $resp }
        Start-Sleep -Milliseconds 600
    }
    return $null
}

function Wait-JudgeEvent([object]$TeacherSess, [long]$SubmitId, [int]$MaxSec) {
    $path = '/learning/teacher/timeline?className=' + [System.Uri]::EscapeDataString($ClassName) + '&eventType=JUDGE&nodeName=' + [System.Uri]::EscapeDataString($LruNode) + '&pageSize=50'
    $deadline = (Get-Date).AddSeconds($MaxSec)
    while ((Get-Date) -lt $deadline) {
        $resp = Invoke-Api -WebSession $TeacherSess -Method Get -Path $path
        if ($resp.code -eq 0 -and $null -ne $resp.data -and $null -ne $resp.data.events) {
            $ev = @($resp.data.events | Where-Object { "$($_.resourceId)" -eq "$SubmitId" })
            if ($ev.Count -ge 1) { return $ev[0] }
        }
        Start-Sleep -Milliseconds 800
    }
    return $null
}

function Submit-Oj([object]$Session, [long]$QuestionId, [string]$Language, [string]$Code) {
    # 提交限流（3 次/60s/用户）时等待重试，不把限流误判为失败
    $body = @{ questionId = $QuestionId; language = $Language; code = $Code }
    $resp = $null
    for ($i = 1; $i -le 5; $i++) {
        $resp = Invoke-Api -WebSession $Session -Method Post -Path '/question/question_submit/do' -Body $body
        if ($resp.code -eq 0) { return $resp }
        $msg = "$($resp.message)"
        if ($msg -like '*频繁*') {
            Write-Host "[WAIT] 提交限流（$msg），等待 16s 后重试（第 $i 次）"
            Start-Sleep -Seconds 16
            continue
        }
        break
    }
    return $resp
}

function Get-BktAfterWrong([double]$Before, [double]$Weight) {
    # 5 参 BKT（契约 §3.3 + 裁决 L3-R12）：答错后验 + 学习转移 + 权重混合
    $pSlip = 0.10
    $pGuess = 0.20
    $pLearn = 0.10
    $posterior = ($Before * $pSlip) / ($Before * $pSlip + (1 - $Before) * (1 - $pGuess))
    $updated = $posterior + (1 - $posterior) * $pLearn
    return $Before + $Weight * ($updated - $Before)
}

function Get-MasteryState([object]$Session) {
    # 读取学生在 LRU 节点的掌握状态摘要；无掌握行时按服务端默认 pInit=0.10 处理
    $path = '/learning/mastery/node?nodeName=' + [System.Uri]::EscapeDataString($LruNode)
    $resp = Invoke-Api -WebSession $Session -Method Get -Path $path
    if ($resp.code -ne 0) { return $null }
    $m = $resp.data.mastery
    if ($null -eq $m) {
        return [pscustomobject]@{ pKnown = 0.10; conceptEvidenceCount = 0; practiceEvidenceCount = 0; lastEventId = 0; status = 'GRAY' }
    }
    $pk = 0.10
    if ($null -ne $m.pKnown) { $pk = [double]$m.pKnown }
    $cc = 0
    if ($null -ne $m.conceptEvidenceCount) { $cc = [int]$m.conceptEvidenceCount }
    $pc = 0
    if ($null -ne $m.practiceEvidenceCount) { $pc = [int]$m.practiceEvidenceCount }
    $le = 0
    if ($null -ne $m.lastEventId) { $le = [long]$m.lastEventId }
    $st = 'GRAY'
    if ($null -ne $m.status) { $st = "$($m.status)" }
    return [pscustomobject]@{ pKnown = $pk; conceptEvidenceCount = $cc; practiceEvidenceCount = $pc; lastEventId = $le; status = $st }
}

function Test-MasteryEqual($a, $b) {
    if ($null -eq $a -or $null -eq $b) { return $false }
    return ([Math]::Abs($a.pKnown - $b.pKnown) -le 1e-12 -and
        $a.conceptEvidenceCount -eq $b.conceptEvidenceCount -and
        $a.practiceEvidenceCount -eq $b.practiceEvidenceCount -and
        $a.lastEventId -eq $b.lastEventId -and
        $a.status -eq $b.status)
}

function Format-MasteryState($s) {
    if ($null -eq $s) { return 'null' }
    return "pKnown=$($s.pKnown); concept=$($s.conceptEvidenceCount); practice=$($s.practiceEvidenceCount); lastEventId=$($s.lastEventId); status=$($s.status)"
}

# =====================================================================
# 主流程
# =====================================================================

function Invoke-MainFlow {
    # ---------- S0 环境准备 ----------
    Write-Host "=== S0 环境准备（RunKey=$RunKey, JudgeMode=$JudgeMode） ==="
    if (-not (Test-Path $ReportPath)) { New-Item -ItemType Directory -Path $ReportPath -Force | Out-Null }
    Assert-True (Test-Path $ReportPath) 'S0.1' '报告目录可用（ReportPath）' "目录存在：$ReportPath" "Test-Path=$(Test-Path $ReportPath)"

    if ($JudgeMode -eq 'None') {
        Skip-Case 'S0.2' '判题沙箱可达性探测' 'JudgeMode=None（判题类用例整体标为未执行）'
    } else {
        $script:SandboxReachable = Test-SandboxReachable $SandboxUrl
        if ($script:SandboxReachable) {
            Assert-True $true 'S0.2' '判题沙箱可达性探测（SandboxUrl）' 'TCP 可达' $SandboxUrl
        } else {
            Skip-Case 'S0.2' '判题沙箱可达性探测' "SandboxUrl 不可达：$SandboxUrl（判题类用例将标为未执行，不伪造 AC/WA）"
        }
    }

    # ---------- S1 登录 ----------
    Write-Host '=== S1 账号登录（口令来源：参数/环境变量/既有演示口令） ==='
    $teacherLogin = Connect-User $TeacherAccount $TeacherPassword
    Assert-ApiOk $teacherLogin.resp 'S1.1' '教师登录（user/login）' -Fatal
    Set-Id 'teacherUserId' $teacherLogin.resp.data.id
    $teacherSess = $teacherLogin.session

    $stuA = Connect-User $StudentAAccount $StudentAPassword
    Assert-ApiOk $stuA.resp 'S1.2' '学生A登录（user/login）' -Fatal
    Set-Id 'studentAUserId' $stuA.resp.data.id
    $studentASess = $stuA.session

    $stuB = Connect-User $StudentBAccount $StudentBPassword
    Assert-ApiOk $stuB.resp 'S1.3' '学生B登录（user/login）' -Fatal
    Set-Id 'studentBUserId' $stuB.resp.data.id
    $studentBSess = $stuB.session

    # ---------- S2 个人图谱结构（无固定历史数量断言） ----------
    Write-Host '=== S2 个人图谱结构（学生A，仅结构断言，不依赖历史数量） ==='
    $graph = Invoke-Api -WebSession $studentASess -Method Get -Path '/learning/mastery/graph'
    Assert-ApiOk $graph 'S2.1' '个人图谱加载（learning/mastery/graph）' -Fatal
    $nodeCount = @($graph.data.nodes).Count
    Assert-True ($nodeCount -ge 1) 'S2.2' '图谱节点非空' 'nodes.Count>=1' "nodes.Count=$nodeCount"
    Assert-True ($graph.data.totalNodes -eq $nodeCount) 'S2.3' 'totalNodes 与节点列表一致' "totalNodes=$nodeCount" "totalNodes=$($graph.data.totalNodes), nodes.Count=$nodeCount"
    $statusKeyCount = 0
    if ($null -ne $graph.data.statusCounts) { $statusKeyCount = @($graph.data.statusCounts.PSObject.Properties).Count }
    Assert-True ($statusKeyCount -ge 1) 'S2.4' '状态计数存在（statusCounts）' 'statusCounts 至少 1 个键' "statusCounts 键数=$statusKeyCount"

    # ---------- S3 RunKey 与种子题守护 ----------
    Write-Host '=== S3 RunKey 数据标记与种子题（910011）内容守护 ==='
    Assert-True ($RunKey -ne '') 'S3.1' 'RunKey 已生成（本轮新增数据标记）' 'RunKey 非空' $RunKey

    $seedResp = Invoke-Api -WebSession $teacherSess -Method Get -Path '/learning/questions/manage/get?questionId=910011'
    if ($seedResp.code -ne 0) {
        Skip-Case 'S3.2' '种子题 910011 存在性' "manage/get code=$($seedResp.code)（该库未应用 v3 种子，无种子题可守护）"
        Skip-Case 'S3.3' '种子题内容四元组守护' '种子题不存在'
        Skip-Case 'S3.4' '种子题幂等 UPDATE' '种子题不存在'
    } else {
        Assert-True $true 'S3.2' '种子题 910011 存在（manage/get）' 'code=0' "code=$($seedResp.code)"
        $contentOk = ("$($seedResp.data.content)" -like "*$LruFixContentNeedle*")
        $analysisOk = ("$($seedResp.data.analysis)" -like '*每次访问*')
        $answerOk = ("$($seedResp.data.correctAnswer)" -eq '"false"')
        $staleForm = ("$($seedResp.data.content)" -like "*$LruStaleNeedle*")
        $allOk = ($contentOk -and $analysisOk -and $answerOk -and -not $staleForm)
        $actualText = "answer=$($seedResp.data.correctAnswer); content 含修正语句=$contentOk; analysis 含每次访问=$analysisOk; 旧问句形态=$staleForm"
        if ($allOk) {
            Assert-True $true 'S3.3' '种子题四元组守护（题干陈述句 / 答案 false / 解析含每次访问更新）' 'content 含修正语句且 analysis 含每次访问且 answer="false" 且无旧问句形态' $actualText
            Skip-Case 'S3.4' '种子题幂等 UPDATE' '内容已一致，无需修复'
        } else {
            Assert-True $false 'S3.3' '种子题四元组守护（题干陈述句 / 答案 false / 解析含每次访问更新）' 'content 含修正语句且 analysis 含每次访问且 answer="false" 且无旧问句形态' $actualText
            # 幂等 UPDATE：已有库不重建，仅把该题修正为与 oslab_demo_seed_v3.sql 一致的内容
            $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
            if ([string]::IsNullOrEmpty($DbPassword) -or $null -eq $mysqlCmd) {
                Assert-True $false 'S3.4' '种子题幂等 UPDATE（修正过期题干，已有库不重建）' 'UPDATE 成功且回读一致' "缺少前置：mysql 命令可用=$($null -ne $mysqlCmd); DbPassword 已配置=$(-not [string]::IsNullOrEmpty($DbPassword))；请配置 OSLAB_DB_PASSWORD 后重跑，或手工执行 oslab_demo_seed_v3.sql 中 910011 的修正 UPDATE"
            } else {
                $sql = @"
UPDATE learning_question SET
  title = '判断：LRU 命中后无需更新访问顺序',
  content = '使用 LRU 页面置换算法时，若访问的页面已在内存中（命中），由于没有页面被淘汰，因此不需要更新该页的最近访问顺序。请判断该说法是否正确。',
  answer = '"false"',
  analysis = 'LRU 依据"最近最久未使用"做淘汰决策，每次访问（无论命中或缺页调入）都必须把该页更新为"最近使用"，否则下次淘汰判断就会出错。',
  misconception = '常见误区：把"命中页也要更新访问顺序"当成多余步骤——漏更新是 LRU 实现中最典型的错误。'
WHERE id = 910011;
"@
                $env:MYSQL_PWD = $DbPassword
                $mysqlOk = $false
                try {
                    & mysql -h $DbHost -P $DbPort -u $DbUser --default-character-set=utf8mb4 $DbName -e $sql 2>$null | Out-Null
                    $mysqlOk = ($LASTEXITCODE -eq 0)
                } finally {
                    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
                }
                if ($mysqlOk) {
                    $recheck = Invoke-Api -WebSession $teacherSess -Method Get -Path '/learning/questions/manage/get?questionId=910011'
                    $reOk = ($recheck.code -eq 0 -and "$($recheck.data.correctAnswer)" -eq '"false"' -and "$($recheck.data.content)" -like "*$LruFixContentNeedle*")
                    Assert-True $reOk 'S3.4' '种子题幂等 UPDATE（修正过期题干，已有库不重建）' 'UPDATE 成功且回读一致' "mysql exit=$LASTEXITCODE; 回读一致=$reOk"
                } else {
                    Assert-True $false 'S3.4' '种子题幂等 UPDATE（修正过期题干，已有库不重建）' 'UPDATE 成功且回读一致' "mysql 执行失败 exit=$LASTEXITCODE"
                }
            }
        }
    }

    # ---------- S4 创建并发布 LRU 判断题（RunKey 内嵌标记） ----------
    Write-Host '=== S4 创建并发布 LRU 判断题（题干=陈述句，答案=false，学生错误答案=true） ==='
    $addBody = @{
        title         = "验收题：LRU 命中后无需更新访问顺序 [$RunKey]"
        content       = 'LRU 命中一个页面后，无需更新该页的最近访问顺序。请判断该说法是否正确。'
        type          = 'JUDGE'
        answer        = 'false'
        analysis      = '每次访问都更新最近访问顺序，包括命中。LRU 依据"最近最久未使用"做淘汰决策，漏更新会使下一次淘汰判断出错。'
        misconception = '把命中当成不需要更新顺序。'
        chapter       = '内存管理'
        difficulty    = 2
        nodeNames     = @($LruNode)
    }
    $add = Invoke-Api -WebSession $teacherSess -Method Post -Path '/learning/questions/add' -Body $addBody
    Assert-ApiOk $add 'S4.1' '创建判断题（learning/questions/add，RunKey 标记）' -Fatal
    $qid = $add.data
    Set-Id 'lruJudgeQuestionId' $qid

    foreach ($action in @('SUBMIT', 'APPROVE', 'PUBLISH')) {
        $r = Invoke-Api -WebSession $teacherSess -Method Post -Path '/learning/questions/review' -Body @{ questionId = $qid; action = $action }
        Assert-ApiOk $r "S4.2.$action" "审核动作 $action（learning/questions/review）" -Fatal
    }

    # ---------- S5 答案隔离契约（作答前）与事前快照 ----------
    Write-Host '=== S5 答案隔离（作答前）与作答前掌握快照 ==='
    $rawBefore = Invoke-Api -WebSession $studentASess -Method Get -Path "/learning/questions/get?questionId=$qid"
    Assert-ApiOk $rawBefore 'S5.1' '学生读取题面（learning/questions/get）' -Fatal
    $propsBefore = $rawBefore.data.PSObject.Properties
    Assert-True ($null -eq $propsBefore['answer'] -and $null -eq $propsBefore['correctAnswer'] -and $null -eq $propsBefore['analysis']) 'S5.2' '作答前题面不含答案与解析（答案隔离）' '无 answer/correctAnswer/analysis 键' "answer=$($null -ne $propsBefore['answer']); correctAnswer=$($null -ne $propsBefore['correctAnswer']); analysis=$($null -ne $propsBefore['analysis'])"
    Assert-True ($rawBefore.data.hasAttempted -eq $false) 'S5.3' '作答前 hasAttempted=false' 'False' "$($rawBefore.data.hasAttempted)"

    $preState = Get-MasteryState $studentASess
    if ($null -eq $preState) { throw '作答前工作台快照获取失败（learning/mastery/node）' }

    # ---------- S6 首次作答与 BKT 契约 ----------
    Write-Host '=== S6 首次作答（提交错误答案 true）与 5 参 BKT 断言 ==='
    $key1 = "b1-$RunKey-first"
    $ans1 = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/questions/answer' -Body @{ questionId = $qid; answer = 'true'; submissionKey = $key1 }
    Assert-ApiOk $ans1 'S6.1' '首次作答（learning/questions/answer，submissionKey=v1）' -Fatal
    Set-Id 'lruAttemptId1' $ans1.data.attemptId
    Assert-True ($ans1.data.correct -eq $false) 'S6.2' '服务端判分：提交 true 为错（correct=false）' 'False' "$($ans1.data.correct)"
    Assert-True ($ans1.data.isFirst -eq $true) 'S6.3' '首次作答 isFirst=true' 'True' "$($ans1.data.isFirst)"

    $chg1 = @($ans1.data.masteryChanges)
    $lruChg = $null
    foreach ($c in $chg1) { if ("$($c.nodeName)" -eq $LruNode) { $lruChg = $c; break } }
    Assert-True ($null -ne $lruChg) 'S6.4' "掌握度变化覆盖节点 $LruNode" "masteryChanges 含该节点" "条数=$($chg1.Count); 命中=$($null -ne $lruChg)"
    if ($null -eq $lruChg) { throw '首次作答未返回 LRU 节点掌握度变化，无法继续 BKT 断言' }

    Assert-True ([Math]::Abs([double]$lruChg.masteryBefore - $preState.pKnown) -le 1e-12) 'S6.5' '事件前 pKnown 与工作台事前快照一致' "masteryBefore=$($preState.pKnown)" "masteryBefore=$($lruChg.masteryBefore)"

    $expectedAfter = Get-BktAfterWrong ([double]$lruChg.masteryBefore) 1.0
    Assert-True ([Math]::Abs([double]$lruChg.masteryAfter - $expectedAfter) -le 1e-9) 'S6.6' 'after 符合 5 参 BKT 公式（答错含学习转移项，映射权重 1）' "after=$expectedAfter" "after=$($lruChg.masteryAfter)"

    if ([Math]::Abs([double]$lruChg.masteryBefore - 0.10) -le 1e-12) {
        $canonOk = ([Math]::Abs([double]$lruChg.masteryAfter - 0.1123287671232877) -le 1e-7 -and "$($lruChg.status)" -eq 'RED')
        Assert-True $canonOk 'S6.7' '新节点规范值：首次答错 after≈0.1123288 且状态薄弱(RED)' 'after=0.1123287671 且 status=RED' "after=$($lruChg.masteryAfter); status=$($lruChg.status)"
    } else {
        Skip-Case 'S6.7' '新节点规范值（after≈0.1123288 且 RED）' "节点非全新（before=$($lruChg.masteryBefore)），按公式断言 S6.6 执行，不依赖历史积累"
    }

    Assert-True ($lruChg.countedAsObservation -eq $true) 'S6.8' '首次作答计为独立观测（countedAsObservation=true）' 'True' "$($lruChg.countedAsObservation)"

    $postState = Get-MasteryState $studentASess
    if ($null -eq $postState) { throw '作答后工作台快照获取失败（learning/mastery/node）' }
    $evCountOk = ($postState.conceptEvidenceCount -eq ($preState.conceptEvidenceCount + 1) -and
        $postState.practiceEvidenceCount -eq $preState.practiceEvidenceCount -and
        [Math]::Abs($postState.pKnown - [double]$lruChg.masteryAfter) -le 1e-12)
    Assert-True $evCountOk 'S6.9' '证据计数回读：概念 +1、实践不变、pKnown=after' "concept=$($preState.conceptEvidenceCount + 1); practice=$($preState.practiceEvidenceCount); pKnown=$($lruChg.masteryAfter)" (Format-MasteryState $postState)

    $tlMine = Invoke-Api -WebSession $studentASess -Method Get -Path ('/learning/mastery/timeline?nodeName=' + [System.Uri]::EscapeDataString($LruNode) + '&pageSize=50')
    Assert-ApiOk $tlMine 'S6.10' '本人时间线读取（learning/mastery/timeline）' -Fatal
    $attemptEvent = $null
    foreach ($e in @($tlMine.data.events)) { if ("$($e.eventGroupKey)" -eq "attempt:$($ans1.data.attemptId)") { $attemptEvent = $e; break } }
    $attemptEventOk = ($null -ne $attemptEvent -and
        "$($attemptEvent.eventType)" -eq 'ATTEMPT' -and
        $attemptEvent.isFirst -eq $true -and
        "$($attemptEvent.resourceId)" -eq "$qid" -and
        "$($attemptEvent.result)" -eq 'FAIL' -and
        [Math]::Abs([double]$attemptEvent.masteryBefore - [double]$lruChg.masteryBefore) -le 1e-12 -and
        [Math]::Abs([double]$attemptEvent.masteryAfter - [double]$lruChg.masteryAfter) -le 1e-12)
    Assert-True $attemptEventOk 'S6.11' '首次作答写入 ATTEMPT 事件（eventGroupKey=attempt:{attemptId}，账本可回读）' 'eventType=ATTEMPT 且 isFirst=true 且 resourceId=qid 且 result=FAIL 且 before/after 一致' $(if ($null -ne $attemptEvent) { "eventId=$($attemptEvent.id); eventType=$($attemptEvent.eventType); isFirst=$($attemptEvent.isFirst); resourceId=$($attemptEvent.resourceId); result=$($attemptEvent.result)" } else { '时间线未找到该 attempt 的事件' })
    if ($null -ne $attemptEvent) { Set-Id 'lruAttemptEventId' $attemptEvent.id }

    # ---------- S7 submissionKey 幂等与重复作答 ----------
    Write-Host '=== S7 submissionKey 幂等与主动重复作答 ==='
    $replay = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/questions/answer' -Body @{ questionId = $qid; answer = 'true'; submissionKey = $key1 }
    Assert-ApiOk $replay 'S7.1' '同 submissionKey 重发（服务端去重）' -Fatal
    $replayChgCount = @($replay.data.masteryChanges).Count
    Assert-True ("$($replay.data.attemptId)" -eq "$($ans1.data.attemptId)" -and $replayChgCount -eq 0) 'S7.2' '同 key 重发不新增观测：attemptId 不变且 masteryChanges 为空' "attemptId=$($ans1.data.attemptId) 且 changes=0" "attemptId=$($replay.data.attemptId); changes=$replayChgCount"

    $postReplayState = Get-MasteryState $studentASess
    if ($null -eq $postReplayState) { throw '重发后工作台快照获取失败（learning/mastery/node）' }
    Assert-True (Test-MasteryEqual $postState $postReplayState) 'S7.3' '重发后掌握状态零变化' (Format-MasteryState $postState) (Format-MasteryState $postReplayState)

    $key2 = "b1-$RunKey-second"
    $ans2 = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/questions/answer' -Body @{ questionId = $qid; answer = 'false'; submissionKey = $key2 }
    Assert-ApiOk $ans2 'S7.4' '新 key 主动纠错作答（answer=false）' -Fatal
    Set-Id 'lruAttemptId2' $ans2.data.attemptId
    Assert-True ("$($ans2.data.isFirst)" -eq 'False' -and "$($ans2.data.correct)" -eq 'True') 'S7.5' '重复作答 isFirst=false 且服务端判分 correct=true' 'isFirst=False 且 correct=True' "isFirst=$($ans2.data.isFirst); correct=$($ans2.data.correct)"

    $chg2 = @($ans2.data.masteryChanges)
    $lruChg2 = $null
    foreach ($c in $chg2) { if ("$($c.nodeName)" -eq $LruNode) { $lruChg2 = $c; break } }
    Assert-True ($null -ne $lruChg2 -and [Math]::Abs([double]$lruChg2.masteryBefore - [double]$lruChg2.masteryAfter) -le 1e-12) 'S7.6' '重复作答按现有重复证据规则：事件 before==after（不更新模型）' 'before==after' $(if ($null -ne $lruChg2) { "before=$($lruChg2.masteryBefore); after=$($lruChg2.masteryAfter)" } else { 'masteryChanges 未含 LRU 节点' })

    $postRepeatState = Get-MasteryState $studentASess
    if ($null -eq $postRepeatState) { throw '重复作答后工作台快照获取失败（learning/mastery/node）' }
    Assert-True ($postRepeatState.conceptEvidenceCount -eq $postState.conceptEvidenceCount) 'S7.7' '重复作答不增加概念证据计数' "concept=$($postState.conceptEvidenceCount)" "concept=$($postRepeatState.conceptEvidenceCount)"

    $rawAfter = Invoke-Api -WebSession $studentASess -Method Get -Path "/learning/questions/get?questionId=$qid"
    Assert-ApiOk $rawAfter 'S7.8' '作答后题面回读（learning/questions/get）' -Fatal
    $propsAfter = $rawAfter.data.PSObject.Properties
    Assert-True ($null -ne $propsAfter['analysis'] -and $null -eq $propsAfter['answer'] -and $null -eq $propsAfter['correctAnswer'] -and $rawAfter.data.hasAttempted -eq $true) 'S7.9' '解析契约：作答后下发 analysis，仍不含答案字段' 'analysis 键出现且无 answer/correctAnswer 且 hasAttempted=True' "analysis=$($null -ne $propsAfter['analysis']); answer=$($null -ne $propsAfter['answer']); correctAnswer=$($null -ne $propsAfter['correctAnswer']); hasAttempted=$($rawAfter.data.hasAttempted)"

    # ---------- S8 工作台、归因与交互模拟 ----------
    Write-Host '=== S8 节点工作台、归因与交互模拟（FIFO/LRU/OPT/CPU） ==='
    $wb = Invoke-Api -WebSession $studentASess -Method Get -Path ('/learning/mastery/node?nodeName=' + [System.Uri]::EscapeDataString($LruNode))
    Assert-ApiOk $wb 'S8.1' '节点工作台加载（learning/mastery/node）' -Fatal
    $lf = $wb.data.lastFailure
    Assert-True ($null -ne $lf -and "$($lf.errorType)" -eq 'WRONG_ANSWER' -and $null -ne $lf.resourceId) 'S8.2' '最近失败归因存在且指向真实资源' 'errorType=WRONG_ANSWER 且 resourceId 非空' $(if ($null -ne $lf) { "errorType=$($lf.errorType); resourceId=$($lf.resourceId); failureState=$($lf.failureState)" } else { 'lastFailure 为空' })
    Assert-True ($null -ne $wb.data.simulationEntry) 'S8.3' '页面置换模拟器入口存在' 'simulationEntry 非空' "simulationEntry=$($null -ne $wb.data.simulationEntry)"

    $SEQ = @(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2)
    $simFifo = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/simulation/page' -Body @{
        algorithm       = 'FIFO'
        frames          = 3
        accessSequence  = $SEQ
        nodeName        = $LruNode
        predictStep     = 3
        predictedFault  = $true
        predictedEvicted = 7
    }
    Assert-ApiOk $simFifo 'S8.4' 'FIFO 模拟运行（learning/simulation/page，带预测）' -Fatal
    Assert-True ($simFifo.data.pageFaults -eq 10) 'S8.5' 'FIFO 缺页数（固定样例）' '10' "$($simFifo.data.pageFaults)"
    Assert-True ($simFifo.data.prediction.faultCorrect -and $simFifo.data.prediction.evictedCorrect) 'S8.6' 'FIFO 预测判分正确（t3 缺页且淘汰 7）' 'faultCorrect=True 且 evictedCorrect=True' "faultCorrect=$($simFifo.data.prediction.faultCorrect); evictedCorrect=$($simFifo.data.prediction.evictedCorrect)"
    Assert-True ("$($simFifo.data.prediction.eventId)" -ne '' -and "$($simFifo.data.prediction.eventId)" -ne '0') 'S8.7' 'FIFO 预测写入学习事件（eventId>0）' 'eventId>0' "eventId=$($simFifo.data.prediction.eventId)"
    if ($null -ne $simFifo.data.prediction.eventId) { Set-Id 'simFifoEventId' $simFifo.data.prediction.eventId }

    $simLru = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/simulation/page' -Body @{
        algorithm      = 'LRU'
        frames         = 3
        accessSequence = $SEQ
    }
    Assert-ApiOk $simLru 'S8.8' 'LRU 模拟运行（learning/simulation/page）' -Fatal
    Assert-True ($simLru.data.pageFaults -eq 9) 'S8.9' 'LRU 缺页数（固定样例）' '9' "$($simLru.data.pageFaults)"

    $simOpt = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/simulation/page' -Body @{
        algorithm      = 'OPT'
        frames         = 3
        accessSequence = $SEQ
    }
    Assert-ApiOk $simOpt 'S8.10' 'OPT 模拟运行（learning/simulation/page）' -Fatal
    Assert-True ($simOpt.data.pageFaults -eq 7) 'S8.11' 'OPT 缺页数（固定样例）' '7' "$($simOpt.data.pageFaults)"

    $simOptPred = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/simulation/page' -Body @{
        algorithm       = 'OPT'
        frames          = 3
        accessSequence  = $SEQ
        nodeName        = $LruNode
        predictStep     = 3
        predictedFault  = $true
        predictedEvicted = 7
    }
    Assert-ApiOk $simOptPred 'S8.12' 'OPT 预测运行（对比用）' -Fatal
    Assert-True ($simOptPred.data.prediction.faultCorrect -and "$($simOptPred.data.prediction.eventId)" -eq '') 'S8.13' 'OPT 预测判分正确且不写学习事件（eventId=null）' 'faultCorrect=True 且 eventId=null' "faultCorrect=$($simOptPred.data.prediction.faultCorrect); eventId=$($simOptPred.data.prediction.eventId)"

    $simCpu = Invoke-Api -WebSession $studentASess -Method Post -Path '/learning/simulation/cpu' -Body @{
        algorithm    = 'FCFS'
        processes    = @(
            @{ name = 'P1'; arrivalTime = 0; burstTime = 7 },
            @{ name = 'P2'; arrivalTime = 2; burstTime = 4 }
        )
        nodeName     = 'CPU 调度'
        predictIndex = 0
        predictedProcess = 'P1'
    }
    Assert-ApiOk $simCpu 'S8.14' 'CPU 调度模拟（learning/simulation/cpu，带预测）' -Fatal
    Assert-True ($simCpu.data.prediction.correct -and "$($simCpu.data.prediction.eventId)" -ne '' -and "$($simCpu.data.prediction.eventId)" -ne '0') 'S8.15' 'CPU 预测判分正确且计入学习事件（FCFS t0=P1）' 'correct=True 且 eventId>0' "correct=$($simCpu.data.prediction.correct); eventId=$($simCpu.data.prediction.eventId)"
    if ($null -ne $simCpu.data.prediction.eventId) { Set-Id 'simCpuEventId' $simCpu.data.prediction.eventId }

    # ---------- S9 教师端（ClassName 参数化） ----------
    Write-Host "=== S9 教师端权限与下钻（className=$ClassName） ==="
    $deny = Invoke-Api -WebSession $studentASess -Method Get -Path ('/learning/teacher/dashboard?className=' + [System.Uri]::EscapeDataString($ClassName))
    Assert-True ($deny.code -eq 40101) 'S9.1' '学生访问教师端被拒' 'code=40101' "code=$($deny.code)"

    $dash = Invoke-Api -WebSession $teacherSess -Method Get -Path ('/learning/teacher/dashboard?className=' + [System.Uri]::EscapeDataString($ClassName))
    Assert-ApiOk $dash 'S9.2' '教师看板（learning/teacher/dashboard）' -Fatal
    $studentCountOk = ($null -ne $dash.data.studentCount -and [long]$dash.data.studentCount -ge 1)
    Assert-True $studentCountOk 'S9.3' '班级学生数>=1' 'studentCount>=1' "studentCount=$($dash.data.studentCount)"

    $tlTeacher = Invoke-Api -WebSession $teacherSess -Method Get -Path ('/learning/teacher/timeline?className=' + [System.Uri]::EscapeDataString($ClassName) + '&pageSize=10')
    Assert-ApiOk $tlTeacher 'S9.4' '教师班级时间线（learning/teacher/timeline）' -Fatal
    $timelineTotalOk = ($null -ne $tlTeacher.data.total -and [long]$tlTeacher.data.total -ge 1)
    Assert-True $timelineTotalOk 'S9.5' '班级时间线含事件（本次运行已产生数据）' 'total>=1' "total=$($tlTeacher.data.total)"

    $classes = Invoke-Api -WebSession $teacherSess -Method Get -Path '/learning/teacher/classes'
    Assert-ApiOk $classes 'S9.6' '教师班级列表（learning/teacher/classes）' -Fatal
    $hasClass = $false
    foreach ($cls in @($classes.data)) { if ("$($cls.className)" -eq $ClassName) { $hasClass = $true; break } }
    Assert-True $hasClass "S9.7" "班级列表含 $ClassName" "className=$ClassName" "命中=$hasClass"

    $drill = Invoke-Api -WebSession $teacherSess -Method Get -Path ('/learning/teacher/node/detail?className=' + [System.Uri]::EscapeDataString($ClassName) + '&nodeName=' + [System.Uri]::EscapeDataString($LruNode))
    Assert-ApiOk $drill 'S9.8' '教师节点下钻（learning/teacher/node/detail）' -Fatal
    $drillOk = (@($drill.data.questions).Count -ge 1 -and $null -ne $drill.data.experiments -and $null -ne $drill.data.misconceptionStats)
    Assert-True $drillOk 'S9.9' '节点下钻：关联题目/实验/误区统计非空' 'questions>=1 且 experiments/misconceptionStats 非空键' "questions=$(@($drill.data.questions).Count); experiments=$($null -ne $drill.data.experiments); misconceptionStats=$($null -ne $drill.data.misconceptionStats)"

    # ---------- S10 编程实验判题链（JudgeMode 依赖，不伪造结果） ----------
    Write-Host "=== S10 编程实验判题链（JudgeMode=$JudgeMode） ==="
    $judgeCaseIds = @('S10.1', 'S10.2', 'S10.3', 'S10.4', 'S10.5', 'S10.6', 'S10.7', 'S10.8', 'S10.9', 'S10.10', 'S10.11', 'S10.12', 'S10.13', 'S10.14', 'S10.15', 'S10.16', 'S10.17', 'S10.18')
    $realOnlyCaseIds = @('S10.19', 'S10.20', 'S10.21', 'S10.22', 'S10.23')
    $judgeRunnable = ($JudgeMode -ne 'None' -and $script:SandboxReachable)

    if (-not $judgeRunnable) {
        $reason = 'JudgeMode=None（判题类用例按要求标为未执行）'
        if ($JudgeMode -ne 'None') { $reason = "沙箱不可达：$SandboxUrl（判题类用例标为未执行，不伪造 AC/WA）" }
        foreach ($cid in $judgeCaseIds) { Skip-Case $cid '编程实验判题链' $reason }
        foreach ($cid in $realOnlyCaseIds) { Skip-Case $cid '真实沙箱编译错误样例（CE）' '仅 JudgeMode=Real 执行' }
        return
    }

    # 样例与判题用例：Stub=回显协议（单行输入）；Real=真实 LRU 程序（两行输入），不混用
    $samplesDir = Join-Path $PSScriptRoot 'samples'
    $echoCode = @'
#include <stdio.h>
int main(void) { char b[128]; if (scanf("%127s", b) == 1) { printf("%s", b); } return 0; }
'@
    $waCode = $null
    $acCode = $null
    $ceCode = $null
    if ($JudgeMode -eq 'Stub') {
        $waCode = $echoCode
        $acCode = $echoCode
    } else {
        try {
            $waCode = [System.IO.File]::ReadAllText((Join-Path $samplesDir 'lru_wrong.c'))
            $acCode = [System.IO.File]::ReadAllText((Join-Path $samplesDir 'lru_correct.c'))
            $ceCode = [System.IO.File]::ReadAllText((Join-Path $samplesDir 'compile_error.c'))
        } catch {
            foreach ($cid in ($judgeCaseIds + $realOnlyCaseIds)) { Skip-Case $cid '编程实验判题链' "样例读取失败：$($_.Exception.Message)（$samplesDir）" }
            return
        }
    }
    if ($JudgeMode -eq 'Stub') {
        $waJudgeCase = @{ input = '3 7,0,1,2,0,3,0,4,2,3,0,3,2'; output = '9' }
        $acJudgeCase = @{ input = 'echo-word-123'; output = 'echo-word-123' }
    } else {
        $realInput = "3`n7,0,1,2,0,3,0,4,2,3,0,3,2"
        $waJudgeCase = @{ input = $realInput; output = '9' }
        $acJudgeCase = @{ input = $realInput; output = '9' }
    }

    # --- S10.1-S10.7 WA 链：判题用例期望 9，回显/错误实现产生 Wrong Answer ---
    $ojAddWa = Invoke-Api -WebSession $teacherSess -Method Post -Path '/question/add' -Body @{
        title       = "验收题：统计 LRU 缺页次数（编程，WA 用例）[$RunKey]"
        content     = '给定物理块数与页面访问序列，用 LRU 算法统计缺页次数。'
        tags        = @($LruNode)
        answer      = ''
        judgeCase   = @($waJudgeCase)
        judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
    }
    Assert-ApiOk $ojAddWa 'S10.1' '创建 WA 用例编程题（question/add）' -Fatal
    $waQid = $ojAddWa.data
    Set-Id 'ojWaQuestionId' $waQid

    $waSubmit = Submit-Oj $studentASess $waQid 'c' $waCode
    Assert-ApiOk $waSubmit 'S10.2' '提交 WA 用例代码（question/question_submit/do）' -Fatal
    $waId = [long]$waSubmit.data
    Set-Id 'ojWaSubmitId' $waId

    $waTerminal = Wait-SubmitTerminal $studentASess $waId $WaitTimeoutSec
    Assert-True ($null -ne $waTerminal) 'S10.3' "留页轮询 ${WaitTimeoutSec}s 内到达终态" 'status=2/3' $(if ($null -ne $waTerminal) { "status=$($waTerminal.data.status)" } else { '超时未终态' })
    $waOk = ($null -ne $waTerminal -and $waTerminal.data.status -eq 2 -and "$($waTerminal.data.judgeInfo.message)" -eq 'Wrong Answer')
    Assert-True $waOk 'S10.4' 'Wrong Answer 终态（status=2 且 message=Wrong Answer）' 'status=2 且 message=Wrong Answer' $(if ($null -ne $waTerminal) { "status=$($waTerminal.data.status); message=$($waTerminal.data.judgeInfo.message)" } else { '无终态' })

    $denyView = Invoke-Api -WebSession $studentBSess -Method Get -Path "/question/question_submit/get?id=$waId"
    Assert-True ($denyView.code -eq 40101) 'S10.5' '学生B 查看他人提交详情被拒' 'code=40101' "code=$($denyView.code)"
    $adminView = Invoke-Api -WebSession $teacherSess -Method Get -Path "/question/question_submit/get?id=$waId"
    Assert-True ($adminView.code -eq 0 -and "$($adminView.data.id)" -eq "$waId") 'S10.6' '管理员可查看该提交详情且 id 一致' 'code=0 且 id 一致' "code=$($adminView.code); id=$($adminView.data.id)"

    $waEvent = Wait-JudgeEvent $teacherSess $waId 10
    $waEventOk = ($null -ne $waEvent -and "$($waEvent.result)" -eq 'FAIL' -and "$($waEvent.resourceType)" -eq 'QUESTION_SUBMIT' -and "$($waEvent.eventGroupKey)" -eq "judge:$waId")
    Assert-True $waEventOk 'S10.7' 'WA 观测可追溯本次提交（教师时间线 JUDGE 事件）' 'result=FAIL 且 resourceType=QUESTION_SUBMIT 且 eventGroupKey=judge:{submitId}' $(if ($null -ne $waEvent) { "eventId=$($waEvent.id); result=$($waEvent.result); resourceId=$($waEvent.resourceId)" } else { '10s 内未找到 JUDGE 事件' })
    if ($null -ne $waEvent) { Set-Id 'judgeWaEventId' $waEvent.id }

    # --- S10.8-S10.11 AC 链：回显用例或真实正确实现产生 Accepted ---
    $ojAddAc = Invoke-Api -WebSession $teacherSess -Method Post -Path '/question/add' -Body @{
        title       = "验收题：LRU 缺页统计（编程，AC 用例）[$RunKey]"
        content     = '给定物理块数与页面访问序列，用 LRU 算法统计缺页次数（正确实现应输出期望值）。'
        tags        = @($LruNode)
        answer      = ''
        judgeCase   = @($acJudgeCase)
        judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
    }
    Assert-ApiOk $ojAddAc 'S10.8' '创建 AC 用例编程题（question/add）' -Fatal
    $acQid = $ojAddAc.data
    Set-Id 'ojAcQuestionId' $acQid

    $acSubmit = Submit-Oj $studentASess $acQid 'c' $acCode
    Assert-ApiOk $acSubmit 'S10.9' '提交 AC 用例代码（question/question_submit/do）' -Fatal
    $acId = [long]$acSubmit.data
    Set-Id 'ojAcSubmitId' $acId

    $acTerminal = Wait-SubmitTerminal $studentASess $acId $WaitTimeoutSec
    $acOk = ($null -ne $acTerminal -and $acTerminal.data.status -eq 2 -and "$($acTerminal.data.judgeInfo.message)" -eq 'Accepted')
    Assert-True $acOk 'S10.10' 'Accepted 终态（status=2 且 message=Accepted）' 'status=2 且 message=Accepted' $(if ($null -ne $acTerminal) { "status=$($acTerminal.data.status); message=$($acTerminal.data.judgeInfo.message)" } else { '无终态' })

    $acEvent = Wait-JudgeEvent $teacherSess $acId 10
    Assert-True ($null -ne $acEvent -and "$($acEvent.result)" -eq 'PASS') 'S10.11' 'AC 观测可追溯本次提交（JUDGE 事件 result=PASS）' 'result=PASS' $(if ($null -ne $acEvent) { "eventId=$($acEvent.id); result=$($acEvent.result); resourceId=$($acEvent.resourceId)" } else { '10s 内未找到 JUDGE 事件' })
    if ($null -ne $acEvent) { Set-Id 'judgeAcEventId' $acEvent.id } else { throw 'AC JUDGE 事件未按时落账，后续 SE 零变化断言不可靠' }

    # --- S10.12-S10.18 SYSTEM_ERROR 链：空判题用例触发平台故障路径，诊断可记录但掌握度零变化 ---
    $preSeState = Get-MasteryState $studentASess
    if ($null -eq $preSeState) { throw 'SE 用例提交前工作台快照获取失败（learning/mastery/node）' }

    $ojAddSe = Invoke-Api -WebSession $teacherSess -Method Post -Path '/question/add' -Body @{
        title       = "验收题：无判题用例（系统错误路径）[$RunKey]"
        content     = '该题用于触发判题系统错误路径（题目没有可用的判题用例）。'
        tags        = @($LruNode)
        answer      = ''
        judgeCase   = @()
        judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
    }
    Assert-ApiOk $ojAddSe 'S10.12' '创建无判题用例编程题（SE 路径）' -Fatal
    $seQid = $ojAddSe.data
    Set-Id 'ojSeQuestionId' $seQid

    $seSubmit = Submit-Oj $studentASess $seQid 'c' $echoCode
    Assert-ApiOk $seSubmit 'S10.13' '提交 SE 路径代码（question/question_submit/do）' -Fatal
    $seId = [long]$seSubmit.data
    Set-Id 'ojSeSubmitId' $seId

    $seTerminal = Wait-SubmitTerminal $studentASess $seId $WaitTimeoutSec
    $seOk = ($null -ne $seTerminal -and $seTerminal.data.status -eq 3 -and "$($seTerminal.data.judgeInfo.message)" -eq '系统错误')
    Assert-True $seOk 'S10.14' '系统错误终态（status=3 且 message=系统错误，不伪造 WA/AC）' 'status=3 且 message=系统错误' $(if ($null -ne $seTerminal) { "status=$($seTerminal.data.status); message=$($seTerminal.data.judgeInfo.message)" } else { '无终态' })

    $seEvent = Wait-JudgeEvent $teacherSess $seId 10
    $seEventOk = ($null -ne $seEvent -and "$($seEvent.result)" -eq 'SYSTEM_ERROR' -and [Math]::Abs([double]$seEvent.masteryBefore - [double]$seEvent.masteryAfter) -le 1e-12)
    Assert-True $seEventOk 'S10.15' 'SE 诊断事件可追溯且 before==after（不计知识失误）' 'result=SYSTEM_ERROR 且 before==after' $(if ($null -ne $seEvent) { "eventId=$($seEvent.id); result=$($seEvent.result); before=$($seEvent.masteryBefore); after=$($seEvent.masteryAfter)" } else { '10s 内未找到 JUDGE 事件' })
    if ($null -ne $seEvent) { Set-Id 'judgeSeEventId' $seEvent.id }

    $postSeState = Get-MasteryState $studentASess
    if ($null -eq $postSeState) { throw 'SE 用例提交后工作台快照获取失败（learning/mastery/node）' }
    Assert-True (Test-MasteryEqual $preSeState $postSeState) 'S10.16' 'SE 不改变掌握度/分项证据计数/lastEventId' (Format-MasteryState $preSeState) (Format-MasteryState $postSeState)

    if ($null -ne $seEvent) {
        $snap = Invoke-Api -WebSession $studentASess -Method Get -Path ('/learning/mastery/snapshot?atEventId=' + $seEvent.id)
        Assert-ApiOk $snap 'S10.17' '历史快照回放（learning/mastery/snapshot，锚定 SE 事件）' -Fatal
        $snapNode = $null
        foreach ($n in @($snap.data.graph.nodes)) { if ("$($n.name)" -eq $LruNode) { $snapNode = $n; break } }
        Assert-True ($null -ne $snapNode -and [Math]::Abs([double]$snapNode.pKnown - $preSeState.pKnown) -le 1e-12) 'S10.18' 'SE 时点快照与提交前掌握状态一致（快照零漂移）' "pKnown=$($preSeState.pKnown)" $(if ($null -ne $snapNode) { "pKnown=$($snapNode.pKnown)" } else { '快照未含 LRU 节点' })
    } else {
        Skip-Case 'S10.17' '历史快照回放（锚定 SE 事件）' '未找到 SE 诊断事件'
        Skip-Case 'S10.18' 'SE 时点快照与提交前掌握状态一致' '未找到 SE 诊断事件'
    }

    # --- S10.19-S10.23 编译错误链（仅 Real：回显沙箱无法产生 CE，不做假断言） ---
    if ($JudgeMode -ne 'Real') {
        foreach ($cid in $realOnlyCaseIds) { Skip-Case $cid '真实沙箱编译错误样例（CE）' '仅 JudgeMode=Real 执行（回显沙箱无法产生 Compile Error）' }
        return
    }

    $preCeState = Get-MasteryState $studentASess
    if ($null -eq $preCeState) { throw 'CE 用例提交前工作台快照获取失败（learning/mastery/node）' }

    $ojAddCe = Invoke-Api -WebSession $teacherSess -Method Post -Path '/question/add' -Body @{
        title       = "验收题：编译错误样例（CE）[$RunKey]"
        content     = '该题用于验证编译错误诊断路径（真实沙箱 Compile Error）。'
        tags        = @($LruNode)
        answer      = ''
        judgeCase   = @(@{ input = '1'; output = '1' })
        judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
    }
    Assert-ApiOk $ojAddCe 'S10.19' '创建 CE 用例编程题（question/add）' -Fatal
    $ceQid = $ojAddCe.data
    Set-Id 'ojCeQuestionId' $ceQid

    $ceSubmit = Submit-Oj $studentASess $ceQid 'c' $ceCode
    Assert-ApiOk $ceSubmit 'S10.20' '提交 CE 样例代码（question/question_submit/do）' -Fatal
    $ceId = [long]$ceSubmit.data
    Set-Id 'ojCeSubmitId' $ceId

    $ceTerminal = Wait-SubmitTerminal $studentASess $ceId $WaitTimeoutSec
    $ceOk = ($null -ne $ceTerminal -and $ceTerminal.data.status -eq 2 -and "$($ceTerminal.data.judgeInfo.message)" -eq 'Compile Error')
    Assert-True $ceOk 'S10.21' '编译错误终态（status=2 且 message=Compile Error）' 'status=2 且 message=Compile Error' $(if ($null -ne $ceTerminal) { "status=$($ceTerminal.data.status); message=$($ceTerminal.data.judgeInfo.message)" } else { '无终态' })

    $ceEvent = Wait-JudgeEvent $teacherSess $ceId 10
    $ceEventOk = ($null -ne $ceEvent -and "$($ceEvent.result)" -eq 'COMPILE_ERROR' -and [Math]::Abs([double]$ceEvent.masteryBefore - [double]$ceEvent.masteryAfter) -le 1e-12)
    Assert-True $ceEventOk 'S10.22' 'CE 诊断事件可追溯且 before==after（保留诊断不计失误）' 'result=COMPILE_ERROR 且 before==after' $(if ($null -ne $ceEvent) { "eventId=$($ceEvent.id); result=$($ceEvent.result)" } else { '10s 内未找到 JUDGE 事件' })
    if ($null -ne $ceEvent) { Set-Id 'judgeCeEventId' $ceEvent.id }

    $postCeState = Get-MasteryState $studentASess
    if ($null -eq $postCeState) { throw 'CE 用例提交后工作台快照获取失败（learning/mastery/node）' }
    Assert-True (Test-MasteryEqual $preCeState $postCeState) 'S10.23' 'CE 不改变掌握度/分项证据计数/lastEventId' (Format-MasteryState $preCeState) (Format-MasteryState $postCeState)
}

# =====================================================================
# 报告与退出码
# =====================================================================

function Write-Report {
    $finishedAt = Get-Date
    $pass = $script:Cases.Count - $script:Failures - $script:Skipped
    $meta = [pscustomobject]@{
        runKey            = $RunKey
        startedAt         = $script:StartedAt.ToString('yyyy-MM-dd HH:mm:ss')
        finishedAt        = $finishedAt.ToString('yyyy-MM-dd HH:mm:ss')
        baseUrl           = $BaseUrl
        environmentType   = "local-api + JudgeMode=$JudgeMode"
        judgeMode         = $JudgeMode
        sandboxUrl        = $SandboxUrl
        sandboxReachable  = $script:SandboxReachable
        className         = $ClassName
        accounts          = [pscustomobject]@{ teacher = $TeacherAccount; studentA = $StudentAAccount; studentB = $StudentBAccount }
        generatedIds      = [pscustomobject]$script:Ids
        summary           = [pscustomobject]@{ pass = $pass; fail = $script:Failures; skip = $script:Skipped; total = $script:Cases.Count }
    }
    $doc = [pscustomobject]@{ meta = $meta; cases = @($script:Cases) }
    $json = $doc | ConvertTo-Json -Depth 6

    $safeRunKey = ($RunKey -replace '[\\/:*?"<>|]', '-')
    $jsonPath = Join-Path $ReportPath ("verify-learning-{0}.json" -f $safeRunKey)
    $txtPath = Join-Path $ReportPath ("verify-learning-{0}.txt" -f $safeRunKey)

    $utf8Bom = New-Object System.Text.UTF8Encoding($true)
    [System.IO.File]::WriteAllText($jsonPath, $json, $utf8Bom)

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("OSLab 学习闭环验收运行清单（RunKey=$RunKey）")
    $lines.Add("时间：$($script:StartedAt.ToString('yyyy-MM-dd HH:mm:ss')) ~ $($finishedAt.ToString('yyyy-MM-dd HH:mm:ss'))  环境：local-api + JudgeMode=$JudgeMode  BaseUrl=$BaseUrl")
    $lines.Add("账号：教师=$TeacherAccount 学生A=$StudentAAccount 学生B=$StudentBAccount 班级=$ClassName（口令不记录）")
    $lines.Add("沙箱：$SandboxUrl 可达=$($script:SandboxReachable)")
    $lines.Add("生成 ID：")
    foreach ($k in $script:Ids.Keys) { $lines.Add("  $k = $($script:Ids[$k])") }
    $lines.Add('')
    foreach ($c in $script:Cases) {
        $noteText = ''
        if ("$($c.note)" -ne '') { $noteText = " | $($c.note)" }
        $lines.Add(("[{0}] {1} {2} | 预期: {3} | 实际: {4}{5}" -f $c.status, $c.id, $c.name, $c.expected, $c.actual, $noteText))
    }
    $lines.Add('')
    $lines.Add("PASS=$pass FAIL=$($script:Failures) SKIP=$($script:Skipped) TOTAL=$($script:Cases.Count)")
    [System.IO.File]::WriteAllText($txtPath, (($lines -join "`r`n") + "`r`n"), $utf8Bom)

    Write-Host '报告已写入：'
    Write-Host "  $jsonPath"
    Write-Host "  $txtPath"
    return $pass
}

try {
    Invoke-MainFlow
} catch {
    $script:Failures++
    Add-Case 'X999' '脚本异常终止（请求或解析异常）' 'FAIL' '无未处理异常' $_.Exception.Message
    Write-Host "[ABORT] $($_.Exception.Message)"
}

$passCount = Write-Report
Write-Host '=== 结果 ==='
Write-Host "PASS=$passCount FAIL=$($script:Failures) SKIP=$($script:Skipped) TOTAL=$($script:Cases.Count)"
$exitCode = 0
if ($script:Failures -gt 0) {
    $exitCode = 1
    Write-Host 'RESULT = FAILED（存在失败用例）'
} elseif ($script:Skipped -gt 0) {
    Write-Host 'RESULT = PASSED WITH SKIPPED（全部已执行断言通过；未执行项已标注，未伪造判题结果）'
} else {
    Write-Host 'ALL PASSED'
}
exit $exitCode
