# =====================================================================
# ZhiCore AI 升级 —— 检索评价集（计划 §9）
#
# 20 条人工标注问句已冻结，覆盖：
#   - 同义表达（换页 / 淘汰页 / 牺牲页）            4 条
#   - 算法条件（FIFO / LRU / CLOCK / 改进型 CLOCK） 4 条
#   - 当前错题语境（预测题答错后的追问）           4 条
#   - 数据库课程：属性闭包 4 条 + 候选键 4 条      8 条
# 其中 8 条为主线关键问句（mainline=true）。
#
# 每条问句标注目标来源：
#   targetKind = NODE / UNIT / QUESTION
#   targetCode = 节点 / 单元 / 题 code
#   targetChunks = 冻结时从已发布内容解析出的稳定块 code（用于 Top-5 命中判定）
#
# 执行方式：对运行中的后端创建独立会话并提交导师轮次，读取真实联合检索结果。
# 结果：artifacts/ai-platform-demo/eval-retrieval-<RunKey>.json
# 说明：本评价集自此冻结，不再调参；未达 17/20 只记录真实值与差距，不修改检索代码。
#
# 安全：口令只从参数或环境变量读取，不写日志、不进报告。
# 编码：必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 兼容）
# =====================================================================
param(
    [string]$BaseUrl = $(if ($env:OSLAB_AI_BASE_URL) { $env:OSLAB_AI_BASE_URL } else { 'http://localhost:8121/api' }),
    [string]$StudentAccount = $(if ($env:OSLAB_STUDENT_A_ACCOUNT) { $env:OSLAB_STUDENT_A_ACCOUNT } else { 'student_demo1' }),
    [string]$StudentPassword = $(if ($env:OSLAB_STUDENT_A_PASSWORD) { $env:OSLAB_STUDENT_A_PASSWORD } else { $env:OSLAB_DEMO_PASSWORD }),
    [int]$WaitTimeoutSec = 90,
    [int]$PollMs = 700,
    [string]$RunKey = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$ReportDir = 'artifacts/ai-platform-demo'
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $repoRoot '后端代码'))) { $repoRoot = Split-Path -Parent $PSScriptRoot }
$reportDirFull = Join-Path $repoRoot $ReportDir
if (-not (Test-Path $reportDirFull)) { New-Item -ItemType Directory -Path $reportDirFull -Force | Out-Null }
$reportPath = Join-Path $reportDirFull "eval-retrieval-$RunKey.json"

# ---------------------------------------------------------------------
# 冻结评价集：20 条问句与目标来源（不得在评估后调参）
# ---------------------------------------------------------------------
$questionsJson = @'
[
  {
    "id": "os-syn-01", "category": "SYNONYM", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "LRU 里常说的换页，到底换出哪一页？",
    "targetKind": "NODE", "targetCode": "os.memory.lru",
    "targetChunks": ["chunk.wd25.memory.lru.001"]
  },
  {
    "id": "os-syn-02", "category": "SYNONYM", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "淘汰页的选择依据是什么？为什么不是随便挑一页？",
    "targetKind": "UNIT", "targetCode": "unit.memory.replacement-lru-clock",
    "targetChunks": ["chunk.wd25.memory.clock-enhanced.001", "chunk.wd25.memory.clock.001", "chunk.wd25.memory.demand-pte.001", "chunk.wd25.memory.difficulties.002", "chunk.wd25.memory.lru.001", "chunk.wd25.memory.page-fault.001", "chunk.wd25.memory.page-in-timing.001", "chunk.wd25.memory.performance.001"]
  },
  {
    "id": "os-syn-03", "category": "SYNONYM", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "页面淘汰和页面置换是一回事吗？LRU 如何决定被淘汰的页？",
    "targetKind": "NODE", "targetCode": "os.memory.lru",
    "targetChunks": ["chunk.wd25.memory.lru.001"]
  },
  {
    "id": "os-syn-04", "category": "SYNONYM", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "缺页时如果内存已满，按什么规则挑一个牺牲页？",
    "targetKind": "UNIT", "targetCode": "unit.memory.replacement-opt-fifo",
    "targetChunks": ["chunk.wd25.memory.clock.001", "chunk.wd25.memory.difficulties.002", "chunk.wd25.memory.fifo-belady.001", "chunk.wd25.memory.lru.001", "chunk.wd25.memory.opt.001", "chunk.wd25.memory.performance.001", "chunk.wd25.memory.summary-3-2.001", "chunk.wd25.memory.thrashing.001"]
  },
  {
    "id": "os-alg-fifo", "category": "ALGORITHM", "mainline": true,
    "courseCode": "course.operating-systems",
    "query": "FIFO 按什么顺序换出？命中时会不会改变队列？",
    "targetKind": "NODE", "targetCode": "os.memory.fifo",
    "targetChunks": ["chunk.wd25.memory.fifo-belady.001"]
  },
  {
    "id": "os-alg-lru", "category": "ALGORITHM", "mainline": true,
    "courseCode": "course.operating-systems",
    "query": "LRU 命中后为什么要更新该页的位置？换出时看的是哪一端？",
    "targetKind": "NODE", "targetCode": "os.memory.lru",
    "targetChunks": ["chunk.wd25.memory.lru.001"]
  },
  {
    "id": "os-alg-clock", "category": "ALGORITHM", "mainline": true,
    "courseCode": "course.operating-systems",
    "query": "简单 CLOCK 算法怎样用访问位决定换出页？扫描时访问位怎么变化？",
    "targetKind": "NODE", "targetCode": "os.memory.clock",
    "targetChunks": ["chunk.wd25.memory.clock.001"]
  },
  {
    "id": "os-alg-eclock", "category": "ALGORITHM", "mainline": true,
    "courseCode": "course.operating-systems",
    "query": "改进型 CLOCK 为什么还要看修改位？访问位和修改位四类页的处理顺序是什么？",
    "targetKind": "NODE", "targetCode": "os.memory.clock_enhanced",
    "targetChunks": ["chunk.wd25.memory.clock-enhanced.001", "chunk.wd25.memory.difficulties.002"]
  },
  {
    "id": "os-err-01", "category": "WRONG_ANSWER", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "我答错了：三个物理块，访问序列 1,2,3,1,4，按 LRU 访问 4 时到底换出哪一页？我选了 2。",
    "targetKind": "QUESTION", "targetCode": "q.self.diag.lru.hit-update",
    "targetChunks": ["chunk.wd25.memory.lru.001"]
  },
  {
    "id": "os-err-02", "category": "WRONG_ANSWER", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "刚才那道 FIFO 题我把命中页也当成换出页了，FIFO 命中时应该怎么处理？",
    "targetKind": "QUESTION", "targetCode": "q.self.diag.fifo.hit-order",
    "targetChunks": ["chunk.wd25.memory.fifo-belady.001"]
  },
  {
    "id": "os-err-03", "category": "WRONG_ANSWER", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "我做 CLOCK 题时忘了访问位会变化，能说说扫描过程吗？",
    "targetKind": "NODE", "targetCode": "os.memory.clock",
    "targetChunks": ["chunk.wd25.memory.clock.001"]
  },
  {
    "id": "os-err-04", "category": "WRONG_ANSWER", "mainline": false,
    "courseCode": "course.operating-systems",
    "query": "改进型 CLOCK 题我把修改位优先级弄反了，正确顺序是什么？",
    "targetKind": "NODE", "targetCode": "os.memory.clock_enhanced",
    "targetChunks": ["chunk.wd25.memory.clock-enhanced.001", "chunk.wd25.memory.difficulties.002"]
  },
  {
    "id": "db-closure-01", "category": "DATABASE_CLOSURE", "mainline": true,
    "courseCode": "course.database-systems",
    "query": "给定函数依赖集，如何求属性 A 的闭包？步骤是什么？",
    "targetKind": "QUESTION", "targetCode": "q.db.fd.closure-basic",
    "targetChunks": ["chunk.db.fd.closure"]
  },
  {
    "id": "db-closure-02", "category": "DATABASE_CLOSURE", "mainline": true,
    "courseCode": "course.database-systems",
    "query": "复合决定因素的闭包怎么算？比如 AB→C、C→D，求 AB 的闭包。",
    "targetKind": "QUESTION", "targetCode": "q.db.fd.closure-composite",
    "targetChunks": ["chunk.db.fd.closure"]
  },
  {
    "id": "db-closure-03", "category": "DATABASE_CLOSURE", "mainline": false,
    "courseCode": "course.database-systems",
    "query": "属性闭包的结果包含哪些属性？闭包和超键有什么关系？",
    "targetKind": "NODE", "targetCode": "db.fd.attribute-closure",
    "targetChunks": ["chunk.db.fd.closure"]
  },
  {
    "id": "db-closure-04", "category": "DATABASE_CLOSURE", "mainline": false,
    "courseCode": "course.database-systems",
    "query": "为什么求候选键要先求属性闭包？闭包能判断函数依赖是否成立吗？",
    "targetKind": "NODE", "targetCode": "db.fd.attribute-closure",
    "targetChunks": ["chunk.db.fd.closure"]
  },
  {
    "id": "db-key-01", "category": "DATABASE_CANDIDATE_KEY", "mainline": true,
    "courseCode": "course.database-systems",
    "query": "候选键和超键的区别是什么？候选键为什么要求最小性？",
    "targetKind": "QUESTION", "targetCode": "q.db.fd.superkey-not-candidate",
    "targetChunks": ["chunk.db.fd.minimality", "chunk.db.fd.superkey"]
  },
  {
    "id": "db-key-02", "category": "DATABASE_CANDIDATE_KEY", "mainline": true,
    "courseCode": "course.database-systems",
    "query": "怎样统计一个关系模式的候选键个数？请结合属性闭包说明。",
    "targetKind": "QUESTION", "targetCode": "q.db.fd.candidate-count",
    "targetChunks": ["chunk.db.fd.minimality", "chunk.db.fd.superkey"]
  },
  {
    "id": "db-key-03", "category": "DATABASE_CANDIDATE_KEY", "mainline": false,
    "courseCode": "course.database-systems",
    "query": "选择最小候选键时，如何判断某个属性能不能从候选键里去掉？",
    "targetKind": "QUESTION", "targetCode": "q.db.fd.candidate-minimal",
    "targetChunks": ["chunk.db.fd.minimality"]
  },
  {
    "id": "db-key-04", "category": "DATABASE_CANDIDATE_KEY", "mainline": false,
    "courseCode": "course.database-systems",
    "query": "超键的判定条件是什么？一个超键在什么情况下不是候选键？",
    "targetKind": "UNIT", "targetCode": "unit.db.fd-keys",
    "targetChunks": ["chunk.db.fd.closure", "chunk.db.fd.minimality", "chunk.db.fd.superkey"]
  }
]
'@
$questions = $questionsJson | ConvertFrom-Json
if (@($questions).Count -ne 20) { throw "冻结评价集应为 20 条，实际 $(@($questions).Count) 条" }

# ---------------------------------------------------------------------
# HTTP 辅助
# ---------------------------------------------------------------------
function Invoke-Login([string]$account, [string]$password) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((@{ userAccount = $account; userPassword = $password } | ConvertTo-Json))
    $resp = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body $bytes `
        -ContentType 'application/json; charset=utf-8' -SessionVariable loginSession -TimeoutSec 30
    return [pscustomobject]@{ resp = $resp; session = $loginSession }
}

function Invoke-Api($session, [string]$method, [string]$path, $body) {
    $params = @{
        Uri        = "$BaseUrl$path"
        Method     = $method
        WebSession = $session
        TimeoutSec = 120
    }
    if ($null -ne $body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8))
    }
    return Invoke-RestMethod @params
}

function Get-ToolSummary($turn, [string]$toolName) {
    foreach ($entry in @($turn.toolTrace)) {
        if ($entry -and $entry.tool -eq $toolName) { return [string]$entry.summary }
    }
    return ''
}

# ---------------------------------------------------------------------
# 登录
# ---------------------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($StudentPassword)) {
    throw '未提供 StudentPassword / OSLAB_DEMO_PASSWORD'
}
$login = Invoke-Login $StudentAccount $StudentPassword
if ($login.resp.code -ne 0) { throw "登录失败：业务码 $($login.resp.code)：$($login.resp.message)" }
$session = $login.session
Write-Host "=== 检索评价集 RunKey=$RunKey ==="
Write-Host "后端：$BaseUrl    账号：$StudentAccount    问句：$(@($questions).Count) 条"

# ---------------------------------------------------------------------
# 逐题执行真实导师轮次
# ---------------------------------------------------------------------
$results = @()
$index = 0
foreach ($question in $questions) {
    $index++
    $started = Get-Date
    $record = [ordered]@{
        id             = $question.id
        category       = $question.category
        mainline       = [bool]$question.mainline
        courseCode     = $question.courseCode
        query          = $question.query
        targetKind     = $question.targetKind
        targetCode     = $question.targetCode
        targetChunks   = @($question.targetChunks)
        hit            = $false
        hitRank        = 0
        mode           = ''
        turnStatus     = ''
        sourcesCount   = 0
        topSources     = @()
        courseIsolationOk = $true
        citationLocatorOk = $true
        retrievalSummary = ''
        elapsedMs      = 0
        executionError = ''
    }
    try {
        $sessionResp = Invoke-Api $session 'Post' '/learning/ai/sessions' @{
            courseCode = $question.courseCode
            focus      = 'LEARN'
            goal       = '检索评价集'
        }
        if ($sessionResp.code -ne 0) {
            throw "创建会话失败：业务码 $($sessionResp.code)：$($sessionResp.message)"
        }
        $sessionId = $sessionResp.data.sessionId
        $turnResp = Invoke-Api $session 'Post' "/learning/ai/sessions/$sessionId/turns" @{
            clientRequestId = "eval-$RunKey-$($question.id)"
            message         = $question.query
        }
        if ($turnResp.code -ne 0) {
            throw "提交轮次失败：业务码 $($turnResp.code)：$($turnResp.message)"
        }
        $turnId = $turnResp.data.turnId
        $final = $null
        $deadline = (Get-Date).AddSeconds($WaitTimeoutSec)
        while ((Get-Date) -lt $deadline) {
            $poll = Invoke-Api $session 'Get' "/learning/ai/turns/$turnId" $null
            if ($poll.code -ne 0) { throw "读取轮次失败：业务码 $($poll.code)：$($poll.message)" }
            $final = $poll.data
            if ($final.status -ne 'RUNNING') { break }
            Start-Sleep -Milliseconds $PollMs
        }
        if (-not $final) { throw '轮次轮询未取得结果' }
        if ($final.status -eq 'RUNNING') {
            throw "轮次超过 $WaitTimeoutSec 秒仍为 RUNNING"
        }
        $record.turnStatus = [string]$final.status
        $record.mode = [string]$final.mode
        $record.retrievalSummary = Get-ToolSummary $final 'retrieve_sources'
        if ($final.errorMessage) { $record.executionError = [string]$final.errorMessage }
        $sources = @()
        if ($null -ne $final.sources) { $sources = @($final.sources) }
        $record.sourcesCount = $sources.Count
        $top = @($sources | Select-Object -First 5)
        $rank = 0
        $topRecords = @()
        foreach ($source in $top) {
            $rank++
            $isTarget = @($question.targetChunks) -contains [string]$source.sourceId
            if ($isTarget -and -not $record.hit) {
                $record.hit = $true
                $record.hitRank = $rank
            }
            if ([string]$source.courseCode -ne [string]$question.courseCode) {
                $record.courseIsolationOk = $false
            }
            if ([string]::IsNullOrWhiteSpace([string]$source.blockId) -or
                [string]::IsNullOrWhiteSpace([string]$source.locator) -or
                $null -eq $source.revision) {
                $record.citationLocatorOk = $false
            }
            $topRecords += [ordered]@{
                rank      = $rank
                sourceId  = [string]$source.sourceId
                blockId   = [string]$source.blockId
                courseCode = [string]$source.courseCode
                ownerScope = [string]$source.ownerScope
                revision  = $source.revision
                locator   = [string]$source.locator
                excerpt   = if ($source.excerpt) { [string]$source.excerpt } else { '' }
            }
        }
        $record.topSources = $topRecords
    } catch {
        $record.executionError = $_.Exception.Message
    }
    $record.elapsedMs = [int]((Get-Date) - $started).TotalMilliseconds
    $results += [pscustomobject]$record
    $mark = if ($record.hit) { '[HIT ]' } elseif ($record.executionError) { '[ERR ]' } else { '[MISS]' }
    $rankText = if ($record.hit) { " rank=$($record.hitRank)" } else { '' }
    Write-Host ("$mark {0,-14} {1,-18} sources={2}{3}" -f $record.id, $record.targetCode, $record.sourcesCount, $rankText)
}

# ---------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------
$hitCount = @($results | Where-Object { $_.hit }).Count
$questionTotal = @($questions).Count
$mainline = @($results | Where-Object { $_.mainline })
$mainlineHits = @($mainline | Where-Object { $_.hit }).Count
$isolationViolations = @($results | Where-Object { -not $_.courseIsolationOk }).Count
$citationViolations = @($results | Where-Object { -not $_.citationLocatorOk }).Count
$executionErrors = @($results | Where-Object { $_.executionError -and $_.turnStatus -ne 'SUCCEEDED' }).Count
$categorySummary = @()
foreach ($category in ($results | Group-Object category)) {
    $categorySummary += [ordered]@{
        category = $category.Name
        total    = $category.Count
        hits     = @($category.Group | Where-Object { $_.hit }).Count
    }
}
$report = [ordered]@{
    runKey            = $RunKey
    generatedAt       = (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz')
    baseUrl           = $BaseUrl
    frozen            = $true
    questionCount     = @($questions).Count
    hits              = $hitCount
    hitRate           = [math]::Round($hitCount / [double]$questionTotal, 4)
    mainlineCount     = $mainline.Count
    mainlineHits      = $mainlineHits
    courseIsolationViolations = $isolationViolations
    citationLocatorViolations = $citationViolations
    executionErrors   = $executionErrors
    categorySummary   = $categorySummary
    mainlineDetail    = @($mainline | ForEach-Object {
        $hitRank = $_.hitRank
        [ordered]@{
            id         = $_.id
            query      = $_.query
            targetKind = $_.targetKind
            targetCode = $_.targetCode
            hit        = $_.hit
            hitRank    = $hitRank
            mode       = $_.mode
            topSource  = if ($_.topSources.Count -gt 0) { $_.topSources[0].sourceId } else { '' }
        }
    })
    results           = $results
}
$report | ConvertTo-Json -Depth 12 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host ''
Write-Host "汇总：命中 $hitCount/$(@($questions).Count)；主线 $mainlineHits/$($mainline.Count)；课程隔离违规 $isolationViolations；引用定位缺失 $citationViolations；执行错误 $executionErrors"
Write-Host "报告：$reportPath"
if ($hitCount -lt 17) {
    Write-Host "未达 17/20：仅记录真实值与差距，不修改检索代码。" -ForegroundColor Yellow
} else {
    Write-Host "达到 17/20 门槛。" -ForegroundColor Green
}
if ($executionErrors -gt 0) { exit 1 }
exit 0
