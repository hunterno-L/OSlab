# =====================================================================
# OSLab 演示账号准备脚本（执行计划 §6.2.3-5 / task C-1 交付 4 + 5）
# 作用：为全新或已有环境准备三个演示账号并绑定班级名册：
#       teacher_demo（admin，教师功能由 admin 承担）
#       student_demo1 / student_demo2（user，且写入 students 表 userId/className）
# 规则：
#   1) 口令只从参数或环境变量读取；任何输出都不打印口令。
#   2) 幂等：账号已存在则用配置口令登录校验，通过即跳过；已有账号保留
#      既有资料（不覆盖昵称/头像），仅在角色缺失时补齐 admin。
#   3) 用户创建走后端接口：/user/register 设口令（BCrypt），/user/update
#      （管理员）提角色、设新建账号昵称；students 表绑定走 mysql 客户端
#      —— 后端 /student/add 只能"新建账号+名册"，无法把既有账号挂到名册，
#      故该步必须直连数据库（本脚本不依赖 tmp/ 下任何文件）。
#   4) 附加校验（§6.1.7）：后端可用时，通过 GET /knowledge_graph/getNames
#      比对 v3/v4 种子引用的节点名，缺失即失败；不可用时打印清单供人工比对。
# 用法示例：
#   pwsh -File scripts/dev/prepare-demo-accounts.ps1 -DemoPassword '<演示口令>'
#   环境变量：OSLAB_DEMO_PASSWORD / OSLAB_ADMIN_ACCOUNT / OSLAB_ADMIN_PASSWORD
#             OSLAB_DB_HOST / OSLAB_DB_PORT / OSLAB_DB_USER / OSLAB_DB_PASSWORD
#   Docker Compose 环境：-BaseUrl http://localhost/api（经前端代理 /api）
#   本机裸启后端：-BaseUrl http://localhost:8121/api（默认）
# 限制：DbPassword 含空格/引号时无法经命令行传给 mysql 客户端，请改用无空格口令。
# 退出码：0 全部通过；1 存在失败项
# 编码：本脚本必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 兼容）
# =====================================================================
param(
    [string]$BaseUrl = 'http://localhost:8121/api',
    [string]$TeacherAccount = 'teacher_demo',
    [string]$StudentAccountA = 'student_demo1',
    [string]$StudentAccountB = 'student_demo2',
    [string]$ClassName = 'os2026',
    [string]$TeacherNick = '演示教师',
    [string]$StudentNickA = '演示学生A',
    [string]$StudentNickB = '演示学生B',
    [string]$StudentNumberA = 'os2026demo01',
    [string]$StudentNumberB = 'os2026demo02',
    [string]$DemoPassword = $env:OSLAB_DEMO_PASSWORD,
    [string]$AdminAccount = $(if ($env:OSLAB_ADMIN_ACCOUNT) { $env:OSLAB_ADMIN_ACCOUNT } else { 'nana' }),
    [string]$AdminPassword = $env:OSLAB_ADMIN_PASSWORD,
    [string]$DbHost = $(if ($env:OSLAB_DB_HOST) { $env:OSLAB_DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:OSLAB_DB_PORT) { $env:OSLAB_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:OSLAB_DB_USER) { $env:OSLAB_DB_USER } else { 'root' }),
    [string]$DbName = 'oslab_db',
    [string]$DbPassword = $env:OSLAB_DB_PASSWORD
)

$ErrorActionPreference = 'Stop'
$script:failures = 0
$script:sessions = @{}
$script:accountStates = @()
$script:adminSession = $null
$script:adminTried = $false

function Add-Failure([string]$message) {
    Write-Host "[FAIL] $message"
    $script:failures = $script:failures + 1
}
function Add-Pass([string]$message) { Write-Host "[PASS] $message" }
function Add-Skip([string]$message) { Write-Host "[SKIP] $message" }

function Invoke-ApiJson {
    param($Session, [string]$Method, [string]$Path, [object]$Body)
    $uri = $BaseUrl.TrimEnd('/') + $Path
    try {
        $restArgs = @{ Uri = $uri; Method = $Method; TimeoutSec = 30 }
        if ($null -ne $Body) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
            $restArgs['Body'] = $bytes
            $restArgs['ContentType'] = 'application/json; charset=utf-8'
        }
        if ($null -ne $Session) { $restArgs['WebSession'] = $Session }
        return Invoke-RestMethod @restArgs
    } catch {
        return [pscustomobject]@{ code = -1; message = 'HTTP 异常: ' + $_.Exception.Message; data = $null }
    }
}

function New-DemoSession {
    param([string]$Account, [string]$Password)
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $resp = Invoke-ApiJson -Session $session -Method Post -Path '/user/login' `
        -Body @{ userAccount = $Account; userPassword = $Password }
    return [pscustomobject]@{ Response = $resp; Session = $session }
}

function Get-AdminSession {
    if ($script:adminTried) { return $script:adminSession }
    $script:adminTried = $true
    if ([string]::IsNullOrEmpty($AdminPassword)) {
        Write-Host '[WARN] 未提供管理员口令（-AdminPassword 或 OSLAB_ADMIN_PASSWORD），需要管理员的步骤将记为失败'
        return $null
    }
    $r = New-DemoSession -Account $AdminAccount -Password $AdminPassword
    if ($null -eq $r.Response -or $r.Response.code -ne 0) {
        Write-Host "[WARN] 管理员($AdminAccount)登录失败：$($r.Response.message)"
        return $null
    }
    if ([string]$r.Response.data.userRole -ne 'admin') {
        Write-Host "[WARN] 账号($AdminAccount)不是管理员"
        return $null
    }
    $script:adminSession = $r.Session
    return $script:adminSession
}

function Ensure-DemoAccount {
    param([string]$Account, [string]$Label)
    $state = [pscustomobject]@{
        Label = $Label; Account = $Account; UserId = $null; Role = $null
        Status = ''; NewlyCreated = $false; ClassName = '-'; StudentNumber = '-'
    }
    $login = New-DemoSession -Account $Account -Password $DemoPassword
    if ($null -eq $login.Response) {
        $state.Status = '失败：后端不可达或请求异常'
        Add-Failure "$Label($Account) 登录校验无法完成（HTTP 异常）"
    } elseif ($login.Response.code -eq 0) {
        $state.UserId = $login.Response.data.id
        $state.Role = [string]$login.Response.data.userRole
        $state.Status = '已存在（配置口令校验通过，保留既有资料）'
        $script:sessions[$Account] = $login.Session
    } else {
        $loginMessage = [string]$login.Response.message
        if ($loginMessage -like '*次数过多*') {
            $state.Status = '失败：登录失败次数过多（15 分钟窗口锁定），请稍后重试'
            Add-Failure "$Label($Account) 登录失败次数过多，触发锁定窗口"
        } elseif ($loginMessage -like '*已被封*') {
            $state.Status = '失败：该账号被 ban，禁止登录（请先在用户管理解除封禁）'
            Add-Failure "$Label($Account) 账号已被封禁，登录被拒绝"
        } else {
            # 用注册接口做幂等创建：账号已存在时服务端返回"账号重复"
            $reg = Invoke-ApiJson -Method Post -Path '/user/register' -Body @{
                userAccount = $Account; userPassword = $DemoPassword; checkPassword = $DemoPassword
            }
            if ($reg.code -eq 0) {
                $state.UserId = $reg.data
                $state.Role = 'user'
                $state.NewlyCreated = $true
                $state.Status = '本次新建（注册成功，默认角色 user）'
                $relogin = New-DemoSession -Account $Account -Password $DemoPassword
                if ($null -ne $relogin.Response -and $relogin.Response.code -eq 0) {
                    $script:sessions[$Account] = $relogin.Session
                }
            } elseif (([string]$reg.message) -like '*重复*') {
                $state.Status = '失败：账号已存在但配置口令无法登录（已有资料保留，需人工确认口令或重置该演示账号）'
                Add-Failure "$Label($Account) 已存在但口令不一致，register 返回：$($reg.message)"
            } else {
                $state.Status = "失败：注册被拒绝（$($reg.message)）"
                Add-Failure "$Label($Account) 注册失败：$($reg.message)"
            }
        }
    }
    $script:accountStates = $script:accountStates + $state
    return $state
}

function Set-DemoNickname {
    param($State, [string]$Nick, $AdminSession)
    if ($null -eq $AdminSession) {
        Add-Failure "$($State.Label)($($State.Account)) 为新建账号，但无管理员会话，无法设置昵称"
        return
    }
    $upd = Invoke-ApiJson -Session $AdminSession -Method Post -Path '/user/update' `
        -Body @{ id = $State.UserId; userName = $Nick }
    if ($upd.code -eq 0) {
        Add-Pass "$($State.Label)($($State.Account)) 昵称已设为 $Nick"
    } else {
        Add-Failure "$($State.Label)($($State.Account)) 昵称设置失败：$($upd.message)"
    }
}

function Protect-SqlValue([string]$value) {
    return $value -replace "'", "''"
}

function Invoke-DemoSqlFile {
    param([string]$SqlText)
    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $mysqlCmd) {
        throw '未找到 mysql 客户端（请安装 MySQL 客户端或加入 PATH）'
    }
    $tmpSql = [System.IO.Path]::GetTempFileName()
    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        # 以 UTF-8（无 BOM）落盘，避免中文经控制台管道转码
        [System.IO.File]::WriteAllText($tmpSql, $SqlText, (New-Object System.Text.UTF8Encoding($false)))
        $proc = Start-Process -FilePath $mysqlCmd.Source -ArgumentList @(
            ('--host={0}' -f $DbHost),
            ('--port={0}' -f $DbPort),
            ('--user={0}' -f $DbUser),
            ('--password={0}' -f $DbPassword),
            '--default-character-set=utf8mb4',
            '-N', '-B', $DbName
        ) -RedirectStandardInput $tmpSql -RedirectStandardOutput $tmpOut -RedirectStandardError $tmpErr `
            -NoNewWindow -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            $errText = ([System.IO.File]::ReadAllText($tmpErr)).Trim()
            throw ("SQL 执行失败(退出码 {0}): {1}" -f $proc.ExitCode, $errText)
        }
        $outText = [System.IO.File]::ReadAllText($tmpOut)
        return @($outText -split "`r?`n" | Where-Object { $_ -ne '' })
    } finally {
        Remove-Item $tmpSql, $tmpOut, $tmpErr -ErrorAction SilentlyContinue
    }
}

function Bind-DemoStudent {
    param($State, [string]$StudentNumber, [string]$StudentName)
    if ($null -eq $State.UserId) {
        Add-Failure "$($State.Label)($($State.Account)) 无有效 userId，跳过名册绑定"
        return
    }
    $num = Protect-SqlValue $StudentNumber
    $name = Protect-SqlValue $StudentName
    $class = Protect-SqlValue $ClassName
    try {
        # 1) 学号已被占用？
        $byNumber = @(Invoke-DemoSqlFile -SqlText `
            "SELECT s.userId, s.className FROM students s WHERE s.studentNumber = '$num' AND s.isDelete = 0;")
        if ($byNumber.Count -gt 0) {
            $parts = ([string]@($byNumber)[0]) -split "`t"
            $boundUserId = $parts[0]
            $boundClass = if ($parts.Count -gt 1) { $parts[1] } else { '' }
            if ([string]$boundUserId -eq [string]$State.UserId) {
                if ($boundClass -eq $ClassName) {
                    $State.StudentNumber = $StudentNumber
                    $State.ClassName = $boundClass
                    $state.Status = $state.Status + '；名册绑定已存在'
                    Add-Pass "$($State.Label)($($State.Account)) 名册绑定已存在（学号 $StudentNumber / 班级 $boundClass）"
                } else {
                    Add-Failure "$($State.Label)($($State.Account)) 名册班级与配置不一致：现有 $boundClass，配置 $ClassName（请人工确认后调整）"
                }
            } else {
                Add-Failure "$($State.Label)($($State.Account)) 学号 $StudentNumber 已被其他 userId($boundUserId) 占用，未改动"
            }
            return
        }
        # 2) 该用户已有其他名册记录？
        $byUser = @(Invoke-DemoSqlFile -SqlText `
            "SELECT s.studentNumber, s.className FROM students s WHERE s.userId = $($State.UserId) AND s.isDelete = 0;")
        if ($byUser.Count -gt 0) {
            $parts = ([string]@($byUser)[0]) -split "`t"
            $boundNumber = $parts[0]
            $boundClass = if ($parts.Count -gt 1) { $parts[1] } else { '' }
            $State.StudentNumber = [string]$boundNumber
            $State.ClassName = [string]$boundClass
            if ($boundClass -eq $ClassName) {
                Add-Pass "$($State.Label)($($State.Account)) 已有名册记录（学号 $boundNumber / 班级 $boundClass），保留既有绑定"
            } else {
                Add-Failure "$($State.Label)($($State.Account)) 已绑定到班级 $boundClass（学号 $boundNumber），与配置 $ClassName 不一致，请人工确认"
            }
            return
        }
        # 3) 绑定（幂等：NOT EXISTS 防重复挂名册）
        Invoke-DemoSqlFile -SqlText @"
INSERT INTO students (studentNumber, name, className, userId, createTime, updateTime, isDelete)
SELECT '$num', '$name', '$class', u.id, NOW(), NOW(), 0
FROM ``user`` u
WHERE u.userAccount = '$([string]$State.Account)' AND u.isDelete = 0
  AND NOT EXISTS (SELECT 1 FROM students s WHERE s.userId = u.id AND s.isDelete = 0)
LIMIT 1;
"@ | Out-Null
        $verify = @(Invoke-DemoSqlFile -SqlText `
            "SELECT COUNT(*) FROM students s JOIN ``user`` u ON u.id = s.userId WHERE u.userAccount = '$([string]$State.Account)' AND s.isDelete = 0;")
        if ([string]@($verify)[0] -eq '1') {
            $State.StudentNumber = $StudentNumber
            $State.ClassName = $ClassName
            Add-Pass "$($State.Label)($($State.Account)) 名册绑定完成（学号 $StudentNumber / 班级 $ClassName）"
        } else {
            Add-Failure "$($State.Label)($($State.Account)) 名册绑定后校验失败（期望 1 条，实际 $(@($verify)[0])）"
        }
    } catch {
        Add-Failure "$($State.Label)($($State.Account)) 名册绑定失败：$($_.Exception.Message)"
        Add-Skip "可手工执行的绑定 SQL（替换口令后用 mysql 客户端执行）：
INSERT INTO students (studentNumber, name, className, userId, createTime, updateTime, isDelete)
SELECT '$num', '$name', '$class', u.id, NOW(), NOW(), 0
FROM user u WHERE u.userAccount = '$($State.Account)' AND u.isDelete = 0
  AND NOT EXISTS (SELECT 1 FROM students s WHERE s.userId = u.id AND s.isDelete = 0)
LIMIT 1;"
    }
}

# ---------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------
Write-Host '=== OSLab 演示账号准备（口令不打印） ==='
Write-Host "后端：$BaseUrl；数据库：${DbHost}:$DbPort/$DbName；班级：$ClassName"

if ([string]::IsNullOrEmpty($DemoPassword)) {
    Write-Host '[FAIL] 未提供演示口令：请用 -DemoPassword 参数或 OSLAB_DEMO_PASSWORD 环境变量（不少于 8 位）'
    exit 1
}
if ($DemoPassword.Length -lt 8) {
    Write-Host '[FAIL] 演示口令长度不足 8 位（后端注册接口要求至少 8 位）'
    exit 1
}

# 1) 账号存在性校验 / 幂等创建
$teacherState = Ensure-DemoAccount -Account $TeacherAccount -Label '教师演示'
$studentAState = Ensure-DemoAccount -Account $StudentAccountA -Label '学生演示A'
$studentBState = Ensure-DemoAccount -Account $StudentAccountB -Label '学生演示B'

# 2) 角色与新账号资料修正（仅新建账号设昵称；已有账号保留既有资料）
$needsAdmin = (($null -ne $teacherState.UserId) -and ($teacherState.Role -ne 'admin')) -or
    $teacherState.NewlyCreated -or $studentAState.NewlyCreated -or $studentBState.NewlyCreated
$adminSession = $null
if ($needsAdmin) { $adminSession = Get-AdminSession }

if (($null -ne $teacherState.UserId) -and ($teacherState.Role -ne 'admin')) {
    if ($null -eq $adminSession) {
        Add-Failure "$TeacherAccount 角色为 $($teacherState.Role)（需 admin），且无可用管理员会话，教师端演示不可用"
    } else {
        $upd = Invoke-ApiJson -Session $adminSession -Method Post -Path '/user/update' `
            -Body @{ id = $teacherState.UserId; userRole = 'admin' }
        if ($upd.code -eq 0) {
            $relogin = New-DemoSession -Account $TeacherAccount -Password $DemoPassword
            if ($null -ne $relogin.Response -and $relogin.Response.code -eq 0) {
                $teacherState.Role = [string]$relogin.Response.data.userRole
                $script:sessions[$TeacherAccount] = $relogin.Session
            }
            if ($teacherState.Role -eq 'admin') {
                Add-Pass "$TeacherAccount 角色已提升为 admin（userId=$($teacherState.UserId)）"
            } else {
                Add-Failure "$TeacherAccount 角色提升后仍未生效（当前 $($teacherState.Role)）"
            }
        } else {
            Add-Failure "$TeacherAccount 角色提升失败：$($upd.message)"
        }
    }
}
if ($teacherState.NewlyCreated) { Set-DemoNickname -State $teacherState -Nick $TeacherNick -AdminSession $adminSession }
if ($studentAState.NewlyCreated) { Set-DemoNickname -State $studentAState -Nick $StudentNickA -AdminSession $adminSession }
if ($studentBState.NewlyCreated) { Set-DemoNickname -State $studentBState -Nick $StudentNickB -AdminSession $adminSession }

# 3) students 名册绑定（直连数据库）
Bind-DemoStudent -State $studentAState -StudentNumber $StudentNumberA -StudentName $StudentNickA
Bind-DemoStudent -State $studentBState -StudentNumber $StudentNumberB -StudentName $StudentNickB

# 4) 教师端接口验证（admin 角色实际生效）
$teacherSession = $script:sessions[$TeacherAccount]
if (($null -ne $teacherSession) -and ($teacherState.Role -eq 'admin')) {
    $classes = Invoke-ApiJson -Session $teacherSession -Method Get -Path '/learning/teacher/classes'
    if ($classes.code -eq 0) {
        Add-Pass '教师端 /learning/teacher/classes 可访问（admin 角色生效）'
        $classList = @($classes.data | ForEach-Object { [string]$_.className })
        Write-Host ("  班级列表: {0}" -f ($classList -join ', '))
        if ($classList -notcontains $ClassName) {
            Add-Failure "班级 $ClassName 不在教师班级列表中（students 名册绑定可能未生效）"
        }
    } else {
        Add-Failure "教师端 /learning/teacher/classes 失败：code=$($classes.code) $($classes.message)"
    }
}

# 5) 种子节点名一致性校验（§6.1.7，服务可用时执行）
# 清单来源：oslab_demo_seed_v3.sql（learning_question_node 911001-911027，20 个节点）
#          + oslab_seed_v4_content.sql（素材 912001-912012 与映射 911028-911050，新增 3 个节点）
$seedNodeNames = @(
    '中断', '时钟中断', '文件', '系统调用', '页表', '地址映射',
    'TLB', 'TLB 重填', 'TLB 缺失', '两级页表机制',
    '内存管理', '虚拟内存管理', '缺页中断', '页面置换', 'LRU 页面置换算法', 'FIFO',
    '进程', '进程控制块', '进程状态', '进程调度', 'CPU 调度', '时间片轮转算法', '进程切换'
)
Write-Host '=== 种子节点名与图谱一致性（GET /knowledge_graph/getNames） ==='
$graphResponse = $null
try {
    $graphResponse = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/knowledge_graph/getNames') `
        -Method Get -TimeoutSec 15
} catch {
    $graphResponse = $null
}
if (($null -ne $graphResponse) -and ($graphResponse.code -eq 0)) {
    $graphNames = @($graphResponse.data)
    Write-Host "图谱节点总数=$($graphNames.Count)；种子引用节点数=$($seedNodeNames.Count)"
    $missing = @($seedNodeNames | Where-Object { $graphNames -notcontains $_ })
    if ($missing.Count -gt 0) {
        foreach ($m in $missing) {
            Add-Failure "种子节点在当前图谱中不存在（素材/题目会变成空壳，请先补图谱节点）: $m"
        }
    } else {
        Add-Pass '种子引用的全部节点名均存在于图谱'
    }
} else {
    if ($null -ne $graphResponse) {
        Add-Skip "getNames 返回异常 code=$($graphResponse.code)；以下清单供人工比对"
    } else {
        Add-Skip '后端不可用，跳过在线比对；以下清单供人工比对（种子引用的全部节点名）：'
    }
    $seedNodeNames | ForEach-Object { Write-Host "  - $_" }
}

# 6) 汇总
Write-Host '=== 演示账号清单（口令不打印） ==='
foreach ($s in $script:accountStates) {
    Write-Host ('{0}: account={1} userId={2} role={3} class={4} studentNumber={5}' -f `
        $s.Label, $s.Account, $s.UserId, $s.Role, $s.ClassName, $s.StudentNumber)
    Write-Host ('  状态: {0}' -f $s.Status)
}

if ($script:failures -gt 0) {
    Write-Host "FAILURES = $($script:failures)"
    exit 1
}
Write-Host 'ALL PASSED'
exit 0
