# 本地章节补齐；默认仅预览。凭据读取已有配置/fixture，不执行 fixture 的验收操作。
[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$VerifyHttp,
    [uri]$ApiBase = 'http://localhost:8121/api',
    [uri]$FrontendBase = 'http://localhost:8081',
    [uri]$Neo4jHttpBase = 'http://localhost:7474',
    [string]$Neo4jDatabase
)
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$cypherPath = Join-Path $repoRoot '数据库文件/oslab_graph_chapters.cypher'
$fixturePath = Join-Path $repoRoot 'scripts/dev/verify-learning.ps1'
$runtimePath = Join-Path $repoRoot 'tmp/run-backend-e2e.ps1'
$applicationPath = Join-Path $repoRoot '后端代码/osLab-backend/src/main/resources/application.yml'

foreach ($baseUri in @($ApiBase, $FrontendBase, $Neo4jHttpBase)) {
    if ($baseUri.Host -notin @('localhost', '127.0.0.1', '[::1]', '::1') -or
        $baseUri.Scheme -notin @('http', 'https') -or $baseUri.UserInfo) {
        throw '仅允许没有内嵌凭据的本地 HTTP 地址。'
    }
}
if ($Neo4jDatabase -and $Neo4jDatabase -notmatch '^[a-zA-Z0-9_.-]+$') { throw '数据库名不合法。' }
$apiUrl = $ApiBase.AbsoluteUri.TrimEnd('/')
$frontendUrl = $FrontendBase.AbsoluteUri.TrimEnd('/')
$neoUrl = $Neo4jHttpBase.AbsoluteUri.TrimEnd('/')

function Get-FixtureBody([string]$variableName) {
    $tokens = $null; $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($fixturePath, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) { throw '已有登录 fixture 无法解析，停止而不猜测凭据。' }
    $assignments = @($ast.FindAll({ param($node)
        $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
        $node.Left.VariablePath.UserPath -eq $variableName
    }, $true))
    if ($assignments.Count -ne 1) { throw '没有找到唯一的已有登录 fixture。' }
    $hashAst = $assignments[0].Right.Find({ param($node)
        $node -is [System.Management.Automation.Language.HashtableAst]
    }, $true)
    if ($null -eq $hashAst) { throw '登录 fixture 缺少字面量请求体。' }
    $body = $hashAst.SafeGetValue()
    if (-not $body.userAccount -or -not $body.userPassword) { throw '登录 fixture 不完整。' }
    return $body
}

function Invoke-Api([string]$baseUrl, [string]$path, $session, $body = $null) {
    $requestArgs = @{ Uri = "$baseUrl$path"; TimeoutSec = 60 }
    if ($null -ne $session) { $requestArgs.WebSession = $session }
    if ($null -ne $body) {
        $requestArgs.Method = 'Post'
        $requestArgs.Body = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8))
        $requestArgs.ContentType = 'application/json; charset=utf-8'
    }
    $response = Invoke-RestMethod @requestArgs
    if ($response.code -ne 0) { throw "接口 $path 失败，code=$($response.code)。" }
    return $response
}

function Get-NeoHeaders {
    $neoPassword = $env:SPRING_NEO4J_AUTHENTICATION_PASSWORD
    if (-not $neoPassword) {
        $tokens = $null; $parseErrors = $null
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($runtimePath, [ref]$tokens, [ref]$parseErrors)
        if ($parseErrors.Count -gt 0) { throw '已有 Neo4j 启动配置无法解析。' }
        $assignment = @($ast.FindAll({ param($node)
            $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
            $node.Left.ToString() -eq '$env:SPRING_NEO4J_AUTHENTICATION_PASSWORD'
        }, $true))
        if ($assignment.Count -ne 1) { throw '缺少唯一的已有 Neo4j 本地密码配置。' }
        $literal = $assignment[0].Right.Find({ param($node)
            $node -is [System.Management.Automation.Language.StringConstantExpressionAst]
        }, $true)
        if ($null -eq $literal) { throw 'Neo4j 密码不是可读取的字面量配置。' }
        $neoPassword = $literal.SafeGetValue()
    }
    $neoUsername = $env:SPRING_NEO4J_AUTHENTICATION_USERNAME
    if (-not $neoUsername) {
        $applicationSource = Get-Content -LiteralPath $applicationPath -Raw -Encoding UTF8
        $usernameMatch = [regex]::Match($applicationSource, '\$\{SPRING_NEO4J_AUTHENTICATION_USERNAME:([^}]+)\}')
        if (-not $usernameMatch.Success) { throw '缺少已有 Neo4j 用户配置。' }
        $neoUsername = $usernameMatch.Groups[1].Value
    }
    $authBytes = [System.Text.Encoding]::UTF8.GetBytes("${neoUsername}:$neoPassword")
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($authBytes) }
}

function Invoke-Neo([string]$statement) {
    $body = @{ statements = @(@{ statement = $statement }) } | ConvertTo-Json -Depth 5
    $response = Invoke-RestMethod -Uri "$neoUrl/db/$Neo4jDatabase/tx/commit" -Method Post `
        -Headers $script:neoHeaders -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 60
    if (@($response.errors).Count -gt 0) { throw "Neo4j 执行失败，code=$($response.errors[0].code)。" }
    return $response.results[0]
}

function Get-Hash([string]$value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($value)))).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Get-NeoState {
    $nodeRows = (Invoke-Neo 'MATCH (n:Entity) RETURN id(n), properties(n) ORDER BY id(n)').data
    $edgeRows = (Invoke-Neo 'MATCH (a:Entity)-[r]->(b:Entity) RETURN id(r), id(a), type(r), id(b), properties(r) ORDER BY id(r)').data
    $nodeSignatures = @($nodeRows | ForEach-Object {
        $properties = [ordered]@{}
        foreach ($property in ($_.row[1].PSObject.Properties | Sort-Object Name)) {
            if ($property.Name -ne 'chapter') { $properties[$property.Name] = $property.Value }
        }
        ([ordered]@{ id = $_.row[0]; properties = $properties } | ConvertTo-Json -Compress -Depth 10)
    })
    $edgeSignatures = @($edgeRows | ForEach-Object { $_.row | ConvertTo-Json -Compress -Depth 10 })
    return [pscustomobject]@{
        nodes = @($nodeRows).Count; relationships = @($edgeRows).Count
        nodeIdentityHash = Get-Hash (@($nodeRows | ForEach-Object { "$($_.row[0])|$($_.row[1].name)" } | Sort-Object) -join "`n")
        nonChapterHash = Get-Hash ($nodeSignatures -join "`n")
        relationshipHash = Get-Hash ($edgeSignatures -join "`n")
    }
}

function Get-PublicSignature($graph) {
    $nodeIds = @($graph.nodes | ForEach-Object { [string]$_.id } | Sort-Object)
    $edgeKeys = @($graph.links | ForEach-Object { "$($_.source)|$($_.relationType)|$($_.target)" } | Sort-Object)
    return Get-Hash (($nodeIds -join "`n") + "`n---`n" + ($edgeKeys -join "`n"))
}

$cypher = Get-Content -LiteralPath $cypherPath -Raw -Encoding UTF8
$mapRows = @([regex]::Matches($cypher, "\{name: '([^']+)', chapter: '([^']+)'\}") | ForEach-Object {
    [pscustomobject]@{ name = $_.Groups[1].Value; chapter = $_.Groups[2].Value }
})
if ($mapRows.Count -eq 0 -or @($mapRows | Group-Object name | Where-Object Count -gt 1).Count -gt 0) {
    throw '章节映射为空或同名节点有重复归属。'
}
$before = (Invoke-Api $apiUrl '/knowledge_graph/getAll' $null).data
$missing = @($mapRows | Where-Object { @($before.nodes.name) -cnotcontains $_.name })
if ($missing.Count -gt 0) { throw "精确映射包含不存在的节点：$($missing.name -join '、')。" }
$candidates = @($before.nodes | Where-Object {
    @($mapRows.name) -ccontains $_.name -and ([string]::IsNullOrWhiteSpace($_.chapter) -or $_.chapter.Trim() -eq '未分类')
})
$preview = [ordered]@{
    stage = 'preview'; nodes = @($before.nodes).Count; businessLinks = @($before.links).Count
    mapped = $mapRows.Count; eligible = $candidates.Count
    chapters = @($mapRows | Group-Object chapter | ForEach-Object { [ordered]@{ chapter = $_.Name; mapped = $_.Count } })
    unmapped = @($before.nodes.name | Where-Object { @($mapRows.name) -cnotcontains $_ })
}
$preview | ConvertTo-Json -Depth 6

if ($Apply) {
    $adminSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $adminLogin = Invoke-Api $apiUrl '/user/login' $adminSession (Get-FixtureBody 'login')
    if ($adminLogin.data.userRole -ne 'admin') { throw '已有教师 fixture 不是管理员，停止写入。' }
    $script:neoHeaders = Get-NeoHeaders
    if (-not $Neo4jDatabase) {
        $Neo4jDatabase = 'system'
        $databases = Invoke-Neo 'SHOW DATABASES YIELD name, currentStatus, home WHERE home = true AND currentStatus = "online" RETURN name'
        if (@($databases.data).Count -ne 1) { throw '不能唯一确定当前账号的本地 Neo4j 数据库。' }
        $Neo4jDatabase = [string]$databases.data[0].row[0]
        if ($Neo4jDatabase -notmatch '^[a-zA-Z0-9_.-]+$') { throw '发现的数据库名不合法。' }
    }
    $neoBefore = Get-NeoState
    $publicNodeHash = Get-Hash (@($before.nodes | ForEach-Object { "$($_.id)|$($_.name)" } | Sort-Object) -join "`n")
    if ($neoBefore.nodeIdentityHash -ne $publicNodeHash) { throw 'Neo4j 数据库与当前后端图谱节点身份不一致，停止写入。' }
    $applied = Invoke-Neo $cypher
    $updated = [long](($applied.data | ForEach-Object { $_.row[1] } | Measure-Object -Sum).Sum)
    if ($updated -ne $candidates.Count) { throw '写入节点数与预览不一致，请检查本地并发修改。' }
    $after = (Invoke-Api $apiUrl '/knowledge_graph/getAll' $null).data
    $neoAfter = Get-NeoState
    if ((Get-PublicSignature $before) -ne (Get-PublicSignature $after) -or
        $neoBefore.nonChapterHash -ne $neoAfter.nonChapterHash -or
        $neoBefore.relationshipHash -ne $neoAfter.relationshipHash) {
        throw '补齐前后节点、非章节属性或业务关系不一致。'
    }
    foreach ($node in $before.nodes) {
        $current = @($after.nodes | Where-Object { $_.id -eq $node.id })[0]
        if ($candidates.id -contains $node.id) {
            $expected = @($mapRows | Where-Object { $_.name -ceq $node.name })[0].chapter
            if ($current.chapter -cne $expected) { throw '节点章节与精确映射不一致。' }
        } elseif ($current.chapter -cne $node.chapter) { throw '已有章节或未映射节点被覆盖。' }
    }
    $repeat = Invoke-Neo $cypher
    $repeatUpdated = [long](($repeat.data | ForEach-Object { $_.row[1] } | Measure-Object -Sum).Sum)
    if ($repeatUpdated -ne 0) { throw '重复执行仍写入节点，幂等验证失败。' }
    [ordered]@{
        stage = 'applied'; updated = $updated; repeatUpdated = $repeatUpdated
        nodes = $neoAfter.nodes; rawRelationships = $neoAfter.relationships; businessLinks = @($after.links).Count
        nonChapterPropertiesPreserved = $true; relationshipsPreserved = $true; chapterSummary = $after.chapterSummary
    } | ConvertTo-Json -Depth 6
}

if ($VerifyHttp) {
    $studentSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $proxyApi = "$frontendUrl/api"
    $studentLogin = Invoke-Api $proxyApi '/user/login' $studentSession (Get-FixtureBody 'login1')
    if ($studentLogin.data.userRole -ne 'user') { throw '已有学生 fixture 角色不符。' }
    $currentStudent = Invoke-Api $proxyApi '/user/get/login' $studentSession
    if ($currentStudent.data.id -ne $studentLogin.data.id) { throw '代理登录会话不一致。' }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $personal = Invoke-Api $apiUrl '/learning/mastery/graph' $studentSession
    $watch.Stop()
    [ordered]@{ stage = 'studentGraph'; proxyLogin = $true; currentRole = $currentStudent.data.userRole
        nodes = @($personal.data.nodes).Count; elapsedMs = [math]::Round($watch.Elapsed.TotalMilliseconds, 2) } | ConvertTo-Json -Compress
    $proxyGraph = Invoke-Api $proxyApi '/learning/mastery/graph' $studentSession
    if (@($proxyGraph.data.nodes).Count -ne @($personal.data.nodes).Count) { throw '代理图谱不一致。' }
    foreach ($node in $personal.data.nodes) {
        $publicNode = @($before.nodes | Where-Object { $_.id -eq $node.id })[0]
        if ($Apply) { $publicNode = @($after.nodes | Where-Object { $_.id -eq $node.id })[0] }
        if ($node.chapter -cne $publicNode.chapter) { throw '个人图谱章节与公开图谱不一致。' }
    }
    foreach ($nodeName in @('LRU 页面置换算法', 'CPU 调度', '进程')) {
        $watch.Restart()
        $workbench = Invoke-Api $apiUrl ('/learning/mastery/node?nodeName=' + [uri]::EscapeDataString($nodeName)) $studentSession
        $watch.Stop()
        if ($workbench.data.mastery.name -cne $nodeName) { throw '工作台返回节点不一致。' }
        [ordered]@{ stage = 'workbenchDirectFirstCall'; nodeName = $nodeName; chapter = $workbench.data.mastery.chapter
            elapsedMs = [math]::Round($watch.Elapsed.TotalMilliseconds, 2)
            questions = @($workbench.data.questions).Count; experiments = @($workbench.data.experiments).Count
        } | ConvertTo-Json -Compress
    }
    $questionCases = @(
        @{ chapter = '硬件管理' }, @{ chapter = '内存管理' }, @{ chapter = '进程管理' },
        @{ chapter = '中断与系统调用' }, @{ chapter = '文件系统' }, @{ chapter = '操作系统概述' },
        @{ nodeName = 'LRU 页面置换算法' }, @{ nodeName = 'CPU 调度' }, @{ nodeName = '进程' },
        @{ type = 'JUDGE' }, @{ type = 'SINGLE' }, @{ type = 'MULTIPLE' }, @{ type = 'FILL_BLANK' },
        @{ studyState = 'ALL' }, @{ studyState = 'UNANSWERED' }, @{ studyState = 'NEEDS_REVIEW' }, @{ studyState = 'CORRECT' }
    )
    $stateTotals = @{}
    foreach ($case in $questionCases) {
        $body = @{ current = 1; pageSize = 20 }
        foreach ($key in $case.Keys) { $body[$key] = $case[$key] }
        $page = Invoke-Api $proxyApi '/learning/questions/list/page' $studentSession $body
        foreach ($question in $page.data.records) {
            if ($case.chapter -and $question.chapter -cne $case.chapter) { throw '章节筛选返回跨章题目。' }
            if ($case.nodeName -and @($question.nodeNames) -cnotcontains $case.nodeName) { throw '节点筛选返回不关联题目。' }
            if ($case.type -and $question.type -cne $case.type) { throw '题型筛选不一致。' }
            if ($case.studyState -eq 'UNANSWERED' -and ($question.hasAttempted -or $question.attemptCount -ne 0)) { throw '未作答筛选不一致。' }
            if ($case.studyState -eq 'NEEDS_REVIEW' -and $question.latestResult -ne 'WRONG') { throw '待巩固筛选不一致。' }
            if ($case.studyState -eq 'CORRECT' -and $question.latestResult -ne 'CORRECT') { throw '已答对筛选不一致。' }
            if ($question.PSObject.Properties.Name -contains 'answer' -or $question.PSObject.Properties.Name -contains 'correctAnswer') { throw '学生题面包含答案字段。' }
        }
        if ($case.studyState) { $stateTotals[$case.studyState] = [long]$page.data.total }
        [ordered]@{ stage = 'questionFilter'; filter = $case; total = $page.data.total; pageRecords = @($page.data.records).Count; checked = $true } | ConvertTo-Json -Compress -Depth 5
    }
    if ($stateTotals.ALL -ne ($stateTotals.UNANSWERED + $stateTotals.NEEDS_REVIEW + $stateTotals.CORRECT)) { throw '学习状态集合总数不闭合。' }
    $timeline = Invoke-Api $proxyApi '/learning/mastery/timeline?current=1&pageSize=5' $studentSession
    [ordered]@{ stage = 'timeline'; total = $timeline.data.total; pageEvents = @($timeline.data.events).Count } | ConvertTo-Json -Compress
    $sets = Invoke-Api $proxyApi '/question/questionSets' $studentSession
    [ordered]@{ stage = 'experimentSets'; total = @($sets.data).Count; names = @($sets.data | ForEach-Object { $_.setName }) } | ConvertTo-Json -Compress
    foreach ($resourcePath in @('/resources/process-scheduling.html', '/resources/syscall-concepts.html')) {
        $resource = Invoke-WebRequest -Uri "$frontendUrl$resourcePath" -TimeoutSec 30
        if ([int]$resource.StatusCode -ne 200 -or $resource.Content -notmatch '<html') { throw 'HTML 资源不可用。' }
        [ordered]@{ stage = 'htmlResource'; path = $resourcePath; status = [int]$resource.StatusCode; bytes = $resource.RawContentLength } | ConvertTo-Json -Compress
    }
    $tutor = Invoke-Api $proxyApi '/learning/tutor/ask' $studentSession @{ nodeName = 'LRU 页面置换算法'; question = '为什么 LRU 命中后仍然需要更新访问顺序？' }
    [ordered]@{ stage = 'tutor'; mode = $tutor.data.mode; sources = @($tutor.data.sources).Count; answerPresent = !!$tutor.data.answer } | ConvertTo-Json -Compress
    $timelineAfter = Invoke-Api $proxyApi '/learning/mastery/timeline?current=1&pageSize=5' $studentSession
    if ($timelineAfter.data.total -ne $timeline.data.total) { throw '只读核验期间学习事件总数变化，请排查并发作答。' }
    [ordered]@{ stage = 'readOnlyEvents'; unchanged = $true; total = $timelineAfter.data.total } | ConvertTo-Json -Compress
}
