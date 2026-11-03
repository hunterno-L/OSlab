# =====================================================================
# ZhiCore AI 升级 —— 演示环境准备脚本（交付计划 §7 演示 / 任务书 §5 完成定义）
#
# 作用（按顺序，全部幂等）：
#   1) 检查演示所需环境变量是否已设置（只报"已设置/未设置"，绝不打印取值）；
#   2) 应用 v11/v12 迁移（仅 CREATE TABLE IF NOT EXISTS，应用前先扫描文件，
#      出现 DROP/TRUNCATE/DELETE/UPDATE/ALTER 立即中止，避免误伤既有数据）；
#   3) 校验 7 张新表存在；
#   4) 管理员重建课程段落索引，学生重建个人资料索引；
#   5) 读取模型配置列表，确认平台默认项存在且密钥已脱敏。
#
# 安全约束：
#   - 口令/密钥只从参数或环境变量读取，经 MYSQL_PWD 传给 mysql 客户端，
#     不落日志、不出现在进程命令行、不写进报告；
#   - 报告只写 artifacts/ai-platform-demo/，不覆盖任何既有业务数据；
#   - 本脚本不做任何删除操作，也不修改非演示账号。
#
# 用法：
#   pwsh -File scripts/dev/prepare-ai-demo.ps1
#   pwsh -File scripts/dev/prepare-ai-demo.ps1 -AdminAccount nana -AdminPassword '<口令>'
#
# 环境变量（后端进程需要，脚本只检查是否已设置）：
#   AI_ARK_API_KEY            平台默认模型密钥（缺失则整轮 mode=FALLBACK）
#   AI_ARK_BASE_URL           OpenAI 兼容基址，默认 https://ark.cn-beijing.volces.com/api/v3
#   AI_ARK_MODEL              对话模型名
#   AI_ARK_EMBEDDING_MODEL    向量模型名（缺失则语义检索停用，只有关键词+图谱）
#   OSLAB_AI_MASTER_KEY       个人模型配置密钥的主密钥（缺失则无法保存个人配置）
#   AI_ALLOW_PRIVATE_BASE_URL 仅本机联调时置 true，生产必须留空
#
# 退出码：0 全部通过；1 存在失败项
# 编码：必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 兼容）
# =====================================================================
param(
    [string]$BaseUrl = $(if ($env:OSLAB_AI_BASE_URL) { $env:OSLAB_AI_BASE_URL } else { 'http://localhost:8121/api' }),
    [string]$AdminAccount = $(if ($env:OSLAB_ADMIN_ACCOUNT) { $env:OSLAB_ADMIN_ACCOUNT } else { 'nana' }),
    [string]$AdminPassword = $env:OSLAB_ADMIN_PASSWORD,
    [string]$StudentAccount = $(if ($env:OSLAB_STUDENT_A_ACCOUNT) { $env:OSLAB_STUDENT_A_ACCOUNT } else { 'student_demo1' }),
    [string]$StudentPassword = $(if ($env:OSLAB_STUDENT_A_PASSWORD) { $env:OSLAB_STUDENT_A_PASSWORD } else { $env:OSLAB_DEMO_PASSWORD }),
    [string]$TeacherAccount = 'teacher_demo',
    [string]$OtherStudentAccount = 'student_demo2',
    [string]$DemoPassword = $(if ($env:OSLAB_DEMO_PASSWORD) { $env:OSLAB_DEMO_PASSWORD } else { '123456' }),
    [string]$ClassName = 'os2026',
    [string]$CourseCode = $(if ($env:OSLAB_AI_COURSE) { $env:OSLAB_AI_COURSE } else { 'course.operating-systems' }),
    [string]$DatabaseCourseCode = 'course.database-systems',
    [long]$DatabaseCourseId = 2115000000000000001,
    [string]$DbHost = $(if ($env:OSLAB_DB_HOST) { $env:OSLAB_DB_HOST } else { 'localhost' }),
    [int]$DbPort = $(if ($env:OSLAB_DB_PORT) { [int]$env:OSLAB_DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:OSLAB_DB_USER) { $env:OSLAB_DB_USER } else { 'root' }),
    [string]$DbName = $(if ($env:OSLAB_DB_NAME) { $env:OSLAB_DB_NAME } else { 'oslab_db' }),
    [string]$DbPassword = $env:OSLAB_DB_PASSWORD,
    [switch]$SkipMigration,
    [switch]$SkipIndex,
    # 仅在本地演示环境使用：把已存在的演示账号口令重置为 DemoPassword 的初始 MD5。
    # 默认关闭——既有账号默认保留原口令，不会被脚本悄悄改写。
    [switch]$ResetDemoPassword,
    [string]$RunKey = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$ReportDir = 'artifacts/ai-platform-demo'
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $repoRoot '后端代码'))) { $repoRoot = Split-Path -Parent $PSScriptRoot }
$reportDirFull = Join-Path $repoRoot $ReportDir
if (-not (Test-Path $reportDirFull)) { New-Item -ItemType Directory -Path $reportDirFull -Force | Out-Null }
$reportPath = Join-Path $reportDirFull "prepare-$RunKey.json"

$script:failures = 0
$script:steps = @()

function Add-Step([string]$id, [string]$name, [string]$status, [string]$detail) {
    $script:steps += [pscustomobject]@{ id = $id; name = $name; status = $status; detail = $detail }
    $mark = switch ($status) {
        'PASS' { '[ OK ]' }
        'FAIL' { '[FAIL]' }
        'WARN' { '[WARN]' }
        default { '[SKIP]' }
    }
    Write-Host "$mark $id $name$(if ($detail) { " —— $detail" })"
    if ($status -eq 'FAIL') { $script:failures++ }
}

function Invoke-Api([object]$WebSession, [string]$Method, [string]$Path, [object]$Body) {
    $uri = "$BaseUrl$Path"
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 8
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        return Invoke-RestMethod -Uri $uri -Method Post -Body $bytes `
            -ContentType 'application/json; charset=utf-8' -WebSession $WebSession -TimeoutSec 120
    }
    if ($Method -eq 'Get') {
        return Invoke-RestMethod -Uri $uri -Method Get -WebSession $WebSession -TimeoutSec 120
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -WebSession $WebSession -TimeoutSec 120
}

function Invoke-Login([string]$account, [string]$password) {
    $body = @{ userAccount = $account; userPassword = $password } | ConvertTo-Json
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    # -SessionVariable 只在函数作用域内创建变量，必须和响应一起返回给调用方
    $resp = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body $bytes `
        -ContentType 'application/json; charset=utf-8' -SessionVariable loginSession -TimeoutSec 30
    return [pscustomobject]@{ resp = $resp; session = $loginSession }
}

function Invoke-MySql([string]$sql) {
    $env:MYSQL_PWD = $DbPassword
    try {
        return (& mysql -h $DbHost -P $DbPort -u $DbUser -D $DbName -N -B -e $sql) 2>&1
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

Write-Host "=== ZhiCore AI 演示准备 RunKey=$RunKey ==="
Write-Host "后端：$BaseUrl    数据库：$DbHost`:$DbPort/$DbName    课程：$CourseCode"

# ---------------------------------------------------------------------
# S0 环境变量检查：只报是否设置，不打印取值
# ---------------------------------------------------------------------
$required = @(
    @{ name = 'AI_ARK_API_KEY'; hint = '平台默认模型密钥；缺失时所有轮次 mode=FALLBACK（规则回答）' },
    @{ name = 'AI_ARK_MODEL'; hint = '对话模型名' },
    @{ name = 'AI_ARK_EMBEDDING_MODEL'; hint = '向量模型名；缺失时语义检索停用，只有关键词+图谱' },
    @{ name = 'OSLAB_AI_MASTER_KEY'; hint = '个人模型配置密钥的主密钥；缺失时无法保存个人配置' }
)
$optional = @(
    @{ name = 'AI_ARK_BASE_URL'; hint = 'OpenAI 兼容基址（有默认值）' },
    @{ name = 'AI_ALLOW_PRIVATE_BASE_URL'; hint = '仅本机联调置 true；生产必须留空' }
)
$missing = @()
foreach ($item in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($item.name))) { $missing += $item.name }
}
foreach ($item in ($required + $optional)) {
    $set = -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($item.name))
    Write-Host ("  {0,-26} {1}" -f $item.name, $(if ($set) { '已设置' } else { '未设置' }))
}
if ($missing.Count -eq 0) {
    Add-Step 'S0' '演示环境变量齐备' 'PASS' '模型与向量链路可用'
} else {
    # 缺 key 不是准备失败：链路仍可跑通（规则回答 + 关键词检索），但不能当作模型验收。
    Add-Step 'S0' '演示环境变量齐备' 'WARN' ("缺少：" + ($missing -join ', ') + "。缺 key 时链路仍可跑通，但回答为规则降级（mode=FALLBACK），不得当作模型验收")
}

# ---------------------------------------------------------------------
# S1 迁移文件安全扫描 + 应用
# ---------------------------------------------------------------------
$migrations = @(
    (Join-Path $repoRoot '数据库文件/oslab_upgrade_v11_ai_session.sql'),
    (Join-Path $repoRoot '数据库文件/oslab_upgrade_v12_ai_evidence.sql')
)
$unsafe = @()
foreach ($file in $migrations) {
    if (-not (Test-Path $file)) { $unsafe += "缺失文件：$file"; continue }
    $content = Get-Content -Raw -Encoding UTF8 $file
    # 只检查"语句开头"的破坏性关键字：ON UPDATE CURRENT_TIMESTAMP 这类列定义不算。
    # 行注释先剔除，避免注释里的示例语句误报。
    $withoutComments = ($content -split "`n" | ForEach-Object { $_ -replace '--.*$', '' }) -join "`n"
    foreach ($match in [regex]::Matches($withoutComments, '(?im)^\s*(DROP|TRUNCATE|DELETE|UPDATE|ALTER)\b')) {
        $unsafe += "$($match.Groups[1].Value.ToUpper()) in $(Split-Path -Leaf $file)"
    }
}
if ($unsafe.Count -gt 0) {
    Add-Step 'S1' '迁移文件安全扫描' 'FAIL' ("迁移必须只做加法，发现：" + ($unsafe -join '; '))
} elseif ($SkipMigration) {
    Add-Step 'S1' '应用 v11/v12 迁移' 'SKIP' '指定 -SkipMigration'
} else {
    $applied = $true
    foreach ($file in $migrations) {
        $env:MYSQL_PWD = $DbPassword
        try {
            & cmd /c "mysql -h $DbHost -P $DbPort -u $DbUser -D $DbName --default-character-set=utf8mb4 < `"$file`"" 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { $applied = $false }
        } finally {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
    if ($applied) { Add-Step 'S1' '应用 v11/v12 迁移' 'PASS' 'CREATE TABLE IF NOT EXISTS，可重复执行' }
    else { Add-Step 'S1' '应用 v11/v12 迁移' 'FAIL' 'mysql 客户端返回非 0；请检查数据库连通性与账号权限' }
}

# ---------------------------------------------------------------------
# S2 表存在性校验（7 张）
# ---------------------------------------------------------------------
$expectedTables = @(
    'ai_learning_session', 'ai_tutor_turn', 'ai_material', 'ai_material_chunk',
    'ai_model_profile', 'learning_evidence_context', 'learning_review_item'
)
try {
    $rows = Invoke-MySql ("SELECT table_name FROM information_schema.tables WHERE table_schema='$DbName' " +
        "AND table_name IN ('" + ($expectedTables -join "','") + "')")
    $found = @($rows | Where-Object { $_ })
    $absent = $expectedTables | Where-Object { $found -notcontains $_ }
    if ($absent.Count -eq 0) {
        Add-Step 'S2' '7 张 AI 表已建立' 'PASS' ($found -join ', ')
    } else {
        Add-Step 'S2' '7 张 AI 表已建立' 'FAIL' ("缺少：" + ($absent -join ', '))
    }
} catch {
    Add-Step 'S2' '7 张 AI 表已建立' 'FAIL' "数据库不可达：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S2b 演示账号（幂等）
#
# 只在账号不存在时插入，且写入的是后端仍兼容的初始 MD5 口令
# MD5('yupi' + 明文)（SALT 见 UserServiceImpl.SALT）；首次成功登录时后端会把它
# 自动迁移成 BCrypt，因此这里不保存明文、也不覆盖既有账号的口令。
# 角色与名册字段对照本地库现有行：教师 admin、学生 user，students.className=os2026，
# studentNumber 沿用 "S<userId>" 约定。
# ---------------------------------------------------------------------
function Get-LegacyPasswordHash([string]$raw) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes('yupi' + $raw)
        return ([System.BitConverter]::ToString($md5.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $md5.Dispose()
    }
}

$demoAccounts = @(
    @{ account = $TeacherAccount;      role = 'admin'; student = $false; displayName = '演示教师' },
    @{ account = $StudentAccount;      role = 'user';  student = $true;  displayName = '演示学生A' },
    @{ account = $OtherStudentAccount; role = 'user';  student = $true;  displayName = '演示学生B' }
)

if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    Add-Step 'S2b' '演示账号准备' 'SKIP' '未提供 DemoPassword / OSLAB_DEMO_PASSWORD'
} else {
    $legacyHash = Get-LegacyPasswordHash $DemoPassword
    $createdAccounts = @()
    $keptAccounts = @()
    $resetAccounts = @()
    $accountErrors = @()
    foreach ($item in $demoAccounts) {
        $account = $item.account
        try {
            $count = (Invoke-MySql "SELECT COUNT(*) FROM user WHERE userAccount='$account'") | Select-Object -First 1
            if ($LASTEXITCODE -ne 0) {
                $accountErrors += "$account 查询失败"
                continue
            }
            if ([int]$count -eq 0) {
                Invoke-MySql ("INSERT INTO user (userAccount,userPassword,userName,userRole,isDelete) " +
                    "SELECT '$account','$legacyHash',NULL,'$($item.role)',0 FROM DUAL " +
                    "WHERE NOT EXISTS (SELECT 1 FROM user WHERE userAccount='$account')") | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    $accountErrors += "$account 插入失败"
                    continue
                }
                $createdAccounts += $account
            } else {
                # 既有账号默认保留原口令与资料；只有显式 -ResetDemoPassword 才重置演示口令
                $keptAccounts += $account
                if ($ResetDemoPassword) {
                    Invoke-MySql ("UPDATE user SET userPassword='$legacyHash' " +
                        "WHERE userAccount='$account' AND isDelete=0") | Out-Null
                    if ($LASTEXITCODE -ne 0) {
                        $accountErrors += "$account 口令重置失败"
                    } else {
                        $resetAccounts += $account
                    }
                }
            }
            if ($item.student) {
                $userId = (Invoke-MySql "SELECT id FROM user WHERE userAccount='$account' AND isDelete=0") |
                    Select-Object -First 1
                if (-not $userId) {
                    $accountErrors += "$account 未取到 userId"
                    continue
                }
                $studentNumber = "S$userId"
                Invoke-MySql ("INSERT INTO students (studentNumber,name,className,userId,isDelete) " +
                    "SELECT '$studentNumber','$($item.displayName)','$ClassName',$userId,0 FROM DUAL " +
                    "WHERE NOT EXISTS (SELECT 1 FROM students WHERE userId=$userId) " +
                    "AND NOT EXISTS (SELECT 1 FROM students WHERE studentNumber='$studentNumber')") | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    $accountErrors += "$account 名册写入失败"
                }
            }
        } catch {
            $accountErrors += "$account：$($_.Exception.Message)"
        }
    }
    # 复核：三个账号都必须存在且未删除
    $stillMissing = @()
    foreach ($item in $demoAccounts) {
        $c = (Invoke-MySql "SELECT COUNT(*) FROM user WHERE userAccount='$($item.account)' AND isDelete=0") |
            Select-Object -First 1
        if ([int]$c -eq 0) { $stillMissing += $item.account }
    }
    if ($accountErrors.Count -gt 0 -or $stillMissing.Count -gt 0) {
        Add-Step 'S2b' '演示账号准备' 'FAIL' (($accountErrors + ($stillMissing | ForEach-Object { "$_ 缺失" })) -join '; ')
    } else {
        $detail = "已存在 " + ($keptAccounts -join ',')
        if ($createdAccounts.Count -gt 0) { $detail += "；本次新建 " + ($createdAccounts -join ',') + "（初始 MD5 口令，首次登录自动升级）" }
        if ($resetAccounts.Count -gt 0) { $detail += "；已按 -ResetDemoPassword 重置 " + ($resetAccounts -join ',') + " 的口令（首次登录自动升级）" }
        Add-Step 'S2b' '演示账号准备' 'PASS' $detail
    }
}

# ---------------------------------------------------------------------
# S2c 数据库课程注册（幂等）
#
# 只在缺少 course.database-systems 时插入，取本地已注册行的同值
# （id/title/description/status）；已有行不改写，避免覆盖内容侧发布状态。
# ---------------------------------------------------------------------
try {
    $courseCount = (Invoke-MySql "SELECT COUNT(*) FROM course_catalog WHERE courseCode='$DatabaseCourseCode' AND isDelete=0") |
        Select-Object -First 1
    if ([int]$courseCount -gt 0) {
        Add-Step 'S2c' '数据库课程注册' 'PASS' "$DatabaseCourseCode 已存在，保留原行"
    } else {
        Invoke-MySql ("INSERT INTO course_catalog (id,courseCode,title,description,status,isDelete) " +
            "SELECT $DatabaseCourseId,'$DatabaseCourseCode','数据库系统'," +
            "'函数依赖、属性闭包与候选键；复用统一检索、问答、练习、诊断与复习服务。','DRAFT',0 FROM DUAL " +
            "WHERE NOT EXISTS (SELECT 1 FROM course_catalog WHERE courseCode='$DatabaseCourseCode')") | Out-Null
        $after = (Invoke-MySql "SELECT COUNT(*) FROM course_catalog WHERE courseCode='$DatabaseCourseCode' AND isDelete=0") |
            Select-Object -First 1
        if ([int]$after -gt 0) {
            Add-Step 'S2c' '数据库课程注册' 'PASS' "$DatabaseCourseCode 已按本地同值插入"
        } else {
            Add-Step 'S2c' '数据库课程注册' 'FAIL' '插入后仍未查到该课程'
        }
    }
} catch {
    Add-Step 'S2c' '数据库课程注册' 'FAIL' "数据库不可达：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# S3 管理员登录 + 课程索引重建
# ---------------------------------------------------------------------
$adminSession = $null
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    Add-Step 'S3' '管理员登录' 'SKIP' '未提供 AdminPassword / OSLAB_ADMIN_PASSWORD'
} else {
    try {
        $login = Invoke-Login $AdminAccount $AdminPassword
        if ($login.resp.code -eq 0) {
            $adminSession = $login.session
            Add-Step 'S3' '管理员登录' 'PASS' "账号 $AdminAccount"
        } else {
            Add-Step 'S3' '管理员登录' 'FAIL' "业务码 $($login.resp.code)：$($login.resp.message)"
        }
    } catch {
        Add-Step 'S3' '管理员登录' 'FAIL' "后端不可达：$($_.Exception.Message)"
    }
}

if ($adminSession -and -not $SkipIndex) {
    try {
        $rebuild = Invoke-Api -WebSession $adminSession -Method Post -Path '/learning/ai/index/rebuild' `
            -Body @{ courseCode = $CourseCode }
        if ($rebuild.code -eq 0) {
            $data = $rebuild.data
            $detail = "段落 $($data.blocks) 条，写入 $($data.written)，跳过 $($data.skipped)，向量 $($data.embedded)（模型 $($data.embeddingModel)）"
            if ($data.degraded) {
                Add-Step 'S4' '课程段落索引重建' 'FAIL' "$detail；降级原因：$($data.message)"
            } else {
                Add-Step 'S4' '课程段落索引重建' 'PASS' $detail
            }
        } else {
            Add-Step 'S4' '课程段落索引重建' 'FAIL' "业务码 $($rebuild.code)：$($rebuild.message)"
        }
    } catch {
        Add-Step 'S4' '课程段落索引重建' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
} else {
    Add-Step 'S4' '课程段落索引重建' 'SKIP' $(if ($SkipIndex) { '指定 -SkipIndex' } else { '管理员会话不可用' })
}

# ---------------------------------------------------------------------
# S5 学生登录 + 个人资料索引
# ---------------------------------------------------------------------
$studentSession = $null
if ([string]::IsNullOrWhiteSpace($StudentPassword)) {
    Add-Step 'S5' '学生登录' 'SKIP' '未提供 StudentPassword / OSLAB_STUDENT_A_PASSWORD / OSLAB_DEMO_PASSWORD'
} else {
    try {
        $login = Invoke-Login $StudentAccount $StudentPassword
        if ($login.resp.code -eq 0) {
            $studentSession = $login.session
            Add-Step 'S5' '学生登录' 'PASS' "账号 $StudentAccount"
            if (-not $SkipIndex) {
                $personal = Invoke-Api -WebSession $studentSession -Method Post `
                    -Path '/learning/ai/index/rebuild-personal' -Body @{ courseCode = $CourseCode }
                if ($personal.code -eq 0) {
                    Add-Step 'S6' '个人资料索引重建' 'PASS' "本人资料 $($personal.data.materials) 份"
                } else {
                    Add-Step 'S6' '个人资料索引重建' 'FAIL' "业务码 $($personal.code)：$($personal.message)"
                }
            } else {
                Add-Step 'S6' '个人资料索引重建' 'SKIP' '指定 -SkipIndex'
            }
        } else {
            Add-Step 'S5' '学生登录' 'FAIL' "业务码 $($login.resp.code)：$($login.resp.message)"
        }
    } catch {
        Add-Step 'S5' '学生登录' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
}

# ---------------------------------------------------------------------
# S7 模型配置列表：平台默认项存在且密钥脱敏
# ---------------------------------------------------------------------
$profileSession = if ($studentSession) { $studentSession } else { $adminSession }
if ($profileSession) {
    try {
        $profiles = Invoke-Api -WebSession $profileSession -Method Get -Path '/ai/model-profiles'
        if ($profiles.code -ne 0) {
            Add-Step 'S7' '模型配置列表' 'FAIL' "业务码 $($profiles.code)：$($profiles.message)"
        } else {
            $items = @($profiles.data)
            $platform = $items | Where-Object { $_.platformDefault } | Select-Object -First 1
            $leaked = $items | Where-Object { $_.apiKeyMasked -and $_.apiKeyMasked -notmatch '\*' }
            if (-not $platform) {
                Add-Step 'S7' '模型配置列表' 'FAIL' "响应中没有平台默认项（platformDefault=true）"
            } elseif ($leaked) {
                Add-Step 'S7' '模型配置列表' 'FAIL' "apiKeyMasked 未脱敏，疑似明文密钥外泄"
            } else {
                $raw = $items | Where-Object { $_.PSObject.Properties.Name -contains 'apiKey' }
                if ($raw) {
                    Add-Step 'S7' '模型配置列表' 'FAIL' '响应包含 apiKey 字段（只允许 apiKeyMasked）'
                } else {
                    Add-Step 'S7' '模型配置列表' 'PASS' ("共 $($items.Count) 项；平台默认模型 $($platform.model)（$($platform.status)），脱敏样例 $($platform.apiKeyMasked)")
                }
            }
        }
    } catch {
        Add-Step 'S7' '模型配置列表' 'FAIL' "请求异常：$($_.Exception.Message)"
    }
} else {
    Add-Step 'S7' '模型配置列表' 'SKIP' '无可用会话'
}

# ---------------------------------------------------------------------
# 报告
# ---------------------------------------------------------------------
$report = [pscustomobject]@{
    runKey     = $RunKey
    baseUrl    = $BaseUrl
    courseCode = $CourseCode
    database   = "$DbHost`:$DbPort/$DbName"
    generatedAt = (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz')
    missingEnv = $missing
    failures   = $script:failures
    steps      = $script:steps
}
$report | ConvertTo-Json -Depth 6 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host ""
Write-Host "报告：$reportPath"
if ($script:failures -gt 0) {
    Write-Host "PREPARE FAILED（$($script:failures) 项）" -ForegroundColor Red
    exit 1
}
Write-Host 'PREPARE PASSED' -ForegroundColor Green
exit 0
