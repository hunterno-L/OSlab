﻿# OSLab 学习闭环统一验收脚本（Phase 5 §9.3 修订版）
# 前置：MySQL/Redis/Neo4j 服务运行中；后端以 CODESANDBOX_TYPE=remote
#       CODESANDBOX_REMOTE_URL=http://127.0.0.1:8090 CODESANDBOX_REMOTE_AUTH_SECRET=smoke-secret 启动；
#       回显沙箱：python scripts/dev/sandbox_stub.py
# 覆盖（与正文一致，无超范围声明）：
#   1 账号登录 | 2 个人图谱快照 | 3 测评题审核链
#   4 作答判分(correct)+答案隔离+幂等+BKT | 5 节点工作台 | 6 教师端权限与下钻
#   7 交互模拟：FIFO 预测判分并计入事件 / OPT 运行结果 / OPT 预测不写事件
#   8 编程实验：真实提交 → 留页轮询至终态（Accepted / Wrong Answer）
#               → 终态查询 → 本人详情约束（他人 40101、管理员放行）
#               → JUDGE 事件可追溯（教师时间线）
# 说明：本脚本验证判题协议与事件链（stub 回显语义）；提交代码的真实编译/运行
#       验收见 scripts/dev/samples/ 与交付报告“真实执行”章节，两者分开记录。
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8121/api'

function PostJson($session, $url, $body) {
    $json = $body | ConvertTo-Json -Depth 8
    return Invoke-RestMethod -Uri "$base$url" -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) `
        -ContentType 'application/json; charset=utf-8' -WebSession $session -TimeoutSec 60
}
function GetJson($session, $url) {
    return Invoke-RestMethod -Uri "$base$url" -Method Get -WebSession $session -TimeoutSec 60
}
function Assert($cond, $name) {
    if ($cond) { Write-Host "[PASS] $name" } else { Write-Host "[FAIL] $name"; $script:failures++ }
}
# 留页轮询：串行查询提交，直至终态（status 2/3）或超时；不伪造失败
function WaitSubmitTerminal($session, $id, $maxSec) {
    $deadline = (Get-Date).AddSeconds($maxSec)
    while ((Get-Date) -lt $deadline) {
        $r = GetJson $session "/question/question_submit/get?id=$id"
        if ($r.code -eq 0 -and ($r.data.status -eq 2 -or $r.data.status -eq 3)) { return $r }
        Start-Sleep -Milliseconds 500
    }
    return $null
}
$script:failures = 0

Write-Host '=== 1. 账号与班级 ==='
$teacher = $null
$login = Invoke-RestMethod -Uri "$base/user/login" -Method Post -Body (@{ userAccount = 'teacher_demo'; userPassword = 'oslab123456' } | ConvertTo-Json) -ContentType 'application/json' -SessionVariable teacher -TimeoutSec 30
$ts = $teacher
$s1 = $null
$login1 = Invoke-RestMethod -Uri "$base/user/login" -Method Post -Body (@{ userAccount = 'student_demo1'; userPassword = 'oslab123456' } | ConvertTo-Json) -ContentType 'application/json' -SessionVariable s1 -TimeoutSec 30
$ss1 = $s1
$s2 = $null
$login2 = Invoke-RestMethod -Uri "$base/user/login" -Method Post -Body (@{ userAccount = 'student_demo2'; userPassword = 'oslab123456' } | ConvertTo-Json) -ContentType 'application/json' -SessionVariable s2 -TimeoutSec 30
$ss2 = $s2
Assert ($login.code -eq 0) '教师登录'
Assert ($login1.code -eq 0) '学生A登录'

Write-Host '=== 2. 个人图谱（全量快照） ==='
$graph = GetJson $ss1 '/learning/mastery/graph'
Assert ($graph.code -eq 0 -and $graph.data.nodes.Count -ge 140) "图谱节点数=$($graph.data.nodes.Count)（快照全量，含孤立节点）"
Assert ($null -ne $graph.data.totalNodes -and $graph.data.totalNodes -eq $graph.data.nodes.Count) "totalNodes=$($graph.data.totalNodes)"
Assert ($null -ne $graph.data.statusCounts) '状态计数存在'

Write-Host '=== 3. 审核链 ==='
$add = PostJson $ts '/learning/questions/add' @{
    title = '验收题：LRU 命中后是否更新顺序'
    content = 'LRU 命中后是否需要更新该页的最近访问顺序？'
    type = 'JUDGE'
    answer = 'false'
    analysis = 'LRU 每次访问都必须更新顺序。'
    misconception = '把命中当成不需要更新顺序。'
    chapter = '内存管理'
    difficulty = 2
    nodeNames = @('LRU 页面置换算法')
}
Assert ($add.code -eq 0) "创建题目 code=$($add.code)"
$qid = $add.data
foreach ($action in @('SUBMIT', 'APPROVE', 'PUBLISH')) {
    $r = PostJson $ts '/learning/questions/review' @{ questionId = $qid; action = $action }
    Assert ($r.code -eq 0) "审核 $action code=$($r.code)"
}

Write-Host '=== 4. 作答闭环（correct 字段 + 答案隔离 + 幂等） ==='
$ans = PostJson $ss1 '/learning/questions/answer' @{ questionId = $qid; answer = 'true' }
Assert ($ans.code -eq 0 -and $ans.data.correct -eq $false) '服务端判分为错（correct）'
Assert ($ans.data.isFirst -eq $true) '首次作答'
$raw = GetJson $ss1 "/learning/questions/get?questionId=$qid"
Assert ($null -eq $raw.data.correctAnswer) '学生题面无 correctAnswer 键'
$chg = $ans.data.masteryChanges
Assert ($chg.Count -ge 1) "掌握度变化条数=$($chg.Count)"
# BKT 方向性断言：答错不会提升掌握度。
# 历史库多次运行后节点已累积大量事件（如 pKnown≈0.96/GREEN），固定断言 RED 不可复现。
Assert ($chg[0].masteryAfter -le $chg[0].masteryBefore) "BKT 答错不升掌握度（before=$($chg[0].masteryBefore) after=$($chg[0].masteryAfter) status=$($chg[0].status)）"

Write-Host '=== 5. 工作台（诊断/归因/实验） ==='
$wb = GetJson $ss1 ('/learning/mastery/node?nodeName=' + [System.Uri]::EscapeDataString('LRU 页面置换算法'))
Assert ($wb.code -eq 0) '工作台加载'
Assert ($null -ne $wb.data.lastFailure -and $wb.data.lastFailure.errorType -eq 'WRONG_ANSWER') "归因 errorType=$($wb.data.lastFailure.errorType)"
Assert ($null -ne $wb.data.simulationEntry) '模拟器入口存在'

Write-Host '=== 6. 教师端（权限 + 下钻数据） ==='
$r = GetJson $ss1 '/learning/teacher/dashboard?className=os2026'
Assert ($r.code -eq 40101) "学生访问教师端被拒 code=$($r.code)"
$dash = GetJson $ts '/learning/teacher/dashboard?className=os2026'
Assert ($dash.code -eq 0 -and $dash.data.studentCount -ge 1) "班级学生数=$($dash.data.studentCount)"
$tl = GetJson $ts '/learning/teacher/timeline?className=os2026&pageSize=10'
Assert ($tl.code -eq 0 -and $tl.data.total -gt 0) "时间轴 total=$($tl.data.total)"
# Phase 8 §12：真实班级列表 + 节点精确下钻（映射表口径）
$classes = GetJson $ts '/learning/teacher/classes'
Assert ($classes.code -eq 0 -and @($classes.data | Where-Object { $_.className -eq 'os2026' }).Count -eq 1) '班级列表含 os2026'
$drill = GetJson $ts ('/learning/teacher/node/detail?className=os2026&nodeName=' + [System.Uri]::EscapeDataString('LRU 页面置换算法'))
Assert ($drill.code -eq 0 -and $drill.data.studentCount -ge 2) "下钻全班人数=$($drill.data.studentCount)"
Assert ($drill.data.questions.Count -ge 1) "下钻关联测评题数=$($drill.data.questions.Count)"
Assert ($null -ne $drill.data.experiments) '下钻实验列表非空键'
Assert ($null -ne $drill.data.misconceptionStats) '下钻误区统计非空键'

Write-Host '=== 7. 交互模拟（FIFO/LRU/OPT：预测判分 + 事件写入） ==='
$SEQ = @(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2)
# 7.1 FIFO 预测：t3 访问 2，缺页且淘汰 7（正确 → 计入事件）
$simFifo = PostJson $ss1 '/learning/simulation/page' @{
    algorithm = 'FIFO'; frames = 3; accessSequence = $SEQ
    nodeName = 'LRU 页面置换算法'; predictStep = 3; predictedFault = $true; predictedEvicted = 7
}
Assert ($simFifo.code -eq 0 -and $simFifo.data.pageFaults -eq 10) "FIFO 运行缺页=$($simFifo.data.pageFaults)（期望 10）"
Assert ($simFifo.data.prediction.faultCorrect -and $simFifo.data.prediction.evictedCorrect) 'FIFO 预测判分正确'
Assert ($simFifo.data.prediction.eventId -gt 0) "FIFO 预测已写学习事件 eventId=$($simFifo.data.prediction.eventId)"
# 7.2 LRU / OPT 运行结果（固定样例交叉验证的接口层复验）
$simLru = PostJson $ss1 '/learning/simulation/page' @{ algorithm = 'LRU'; frames = 3; accessSequence = $SEQ }
Assert ($simLru.code -eq 0 -and $simLru.data.pageFaults -eq 9) "LRU 运行缺页=$($simLru.data.pageFaults)（期望 9）"
$simOpt = PostJson $ss1 '/learning/simulation/page' @{ algorithm = 'OPT'; frames = 3; accessSequence = $SEQ }
Assert ($simOpt.code -eq 0 -and $simOpt.data.pageFaults -eq 7) "OPT 运行缺页=$($simOpt.data.pageFaults)（期望 7）"
# 7.3 OPT 预测：判分但不写学习事件（对比用，Phase 5 §9.2）
$simOptPredict = PostJson $ss1 '/learning/simulation/page' @{
    algorithm = 'OPT'; frames = 3; accessSequence = $SEQ
    nodeName = 'LRU 页面置换算法'; predictStep = 3; predictedFault = $true; predictedEvicted = 7
}
Assert ($simOptPredict.data.prediction.faultCorrect) 'OPT 预测判分正确（t3 淘汰 7）'
Assert ($null -eq $simOptPredict.data.prediction.eventId) 'OPT 预测未写学习事件（eventId=null）'
# 7.4 CPU 预测：FCFS 下 t0 运行 P1
$simCpu = PostJson $ss1 '/learning/simulation/cpu' @{
    algorithm = 'FCFS'; processes = @(
        @{ name = 'P1'; arrivalTime = 0; burstTime = 7 },
        @{ name = 'P2'; arrivalTime = 2; burstTime = 4 }
    )
    nodeName = 'CPU 调度'; predictIndex = 0; predictedProcess = 'P1'
}
Assert ($simCpu.code -eq 0 -and $simCpu.data.prediction.correct) 'CPU 预测判分正确（FCFS t0=P1）'
Assert ($simCpu.data.prediction.eventId -gt 0) "CPU 预测已写学习事件 eventId=$($simCpu.data.prediction.eventId)"

Write-Host '=== 8. 编程实验（提交 → 留页轮询 → 终态 → 本人详情约束 → JUDGE 事件） ==='
# 8.1 教师建一道非回显用例的 LRU 编程题（用例期望 9，stub 回显输入 → Wrong Answer）
$ojAdd = PostJson $ts '/question/add' @{
    title = '验收题：统计 LRU 缺页次数（编程）'
    content = '给定物理块数与页面访问序列，用 LRU 算法统计缺页次数。'
    tags = @('LRU 页面置换算法')
    answer = ''
    judgeCase = @(@{ input = '3 7,0,1,2,0,3,0,4,2,3,0,3,2'; output = '9' })
    judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
}
Assert ($ojAdd.code -eq 0) "创建编程题 code=$($ojAdd.code)"
$ojId = $ojAdd.data
# 8.2 学生A 提交（echo stub：输出=输入≠9 → Wrong Answer，终态 status=2）
$submitWa = PostJson $ss1 '/question/question_submit/do' @{
    questionId = $ojId; language = 'c'
    code = '#include <stdio.h>
int main(void) { char b[128]; if (scanf("%127s", b) == 1) { printf("%s", b); } return 0; }'
}
Assert ($submitWa.code -eq 0) "提交 accepted code=$($submitWa.code) submitId=$($submitWa.data)"
$waId = $submitWa.data
# 8.3 留页轮询至终态
$waTerminal = WaitSubmitTerminal $ss1 $waId 15
Assert ($null -ne $waTerminal) '轮询 15s 内到达终态'
Assert ($waTerminal.data.status -eq 2) "Wrong Answer 提交终态 status=$($waTerminal.data.status)（期望 2）"
Assert ($waTerminal.data.judgeInfo.message -eq 'Wrong Answer') "判题消息=$($waTerminal.data.judgeInfo.message)"
# 8.4 本人详情约束：学生B 查学生A 的提交被拒；管理员放行；本人可查
$otherView = GetJson $ss2 "/question/question_submit/get?id=$waId"
Assert ($otherView.code -eq 40101) "学生查他人提交详情被拒 code=$($otherView.code)"
$adminView = GetJson $ts "/question/question_submit/get?id=$waId"
Assert ($adminView.code -eq 0) '管理员可查任意提交详情'
Assert ($waTerminal.code -eq 0 -and $waTerminal.data.id -eq $waId) '本人可查自己的提交详情'
# 8.5 教师建回显用例题（input=output 且 tags 命中 LRU 节点）：提交后判题 Accepted、PASS 事件落在 LRU 节点
$ojAddEcho = PostJson $ts '/question/add' @{
    title = '验收题：回显输入（编程）'
    content = '读取一个单词并原样输出（配合回显沙箱构造 Accepted 终态）。'
    tags = @('LRU 页面置换算法')
    answer = ''
    judgeCase = @(@{ input = 'echo-word-123'; output = 'echo-word-123' })
    judgeConfig = @{ timeLimit = 1000; memoryLimit = 256; stackLimit = 128 }
}
Assert ($ojAddEcho.code -eq 0) "创建回显用例题 code=$($ojAddEcho.code)"
$echoSubmit = PostJson $ss1 '/question/question_submit/do' @{
    questionId = $ojAddEcho.data; language = 'c'
    code = '#include <stdio.h>
int main(void) { char b[64]; if (scanf("%63s", b) == 1) { printf("%s", b); } return 0; }'
}
Assert ($echoSubmit.code -eq 0) "回显用例题提交 submitId=$($echoSubmit.data)"
$acTerminal = WaitSubmitTerminal $ss1 $echoSubmit.data 15
Assert ($null -ne $acTerminal) 'Accepted 提交轮询到达终态'
Assert ($acTerminal.data.status -eq 2 -and $acTerminal.data.judgeInfo.message -eq 'Accepted') `
    "Accepted 终态 status=$($acTerminal.data.status) message=$($acTerminal.data.judgeInfo.message)"
# 8.6 JUDGE 事件可追溯（FAIL=Wrong Answer → 知识失误；Accepted → PASS）
$judgeTl = GetJson $ts ('/learning/teacher/timeline?className=os2026&eventType=JUDGE&nodeName=' + [System.Uri]::EscapeDataString('LRU 页面置换算法') + '&pageSize=20')
Assert ($judgeTl.code -eq 0 -and $judgeTl.data.total -ge 1) "JUDGE 事件可追溯 total=$($judgeTl.data.total)"
$judgeEvents = @($judgeTl.data.events | Where-Object { $_.resourceId -eq $waId })
Assert ($judgeEvents.Count -ge 1 -and $judgeEvents[0].result -eq 'FAIL') "Wrong Answer 提交 JUDGE 事件 result=$($judgeEvents[0].result)"
$judgePass = @($judgeTl.data.events | Where-Object { $_.resourceId -eq $echoSubmit.data })
Assert ($judgePass.Count -ge 1 -and $judgePass[0].result -eq 'PASS') 'Accepted 提交 JUDGE 事件 result=PASS'

Write-Host '=== 结果 ==='
Write-Host "FAILURES = $($script:failures)"
if ($script:failures -eq 0) { Write-Host 'ALL PASSED' }
