﻿﻿﻿# =====================================================================
# UI 术语检查（Phase 10 §14.2）
# 检查词通过码点组合配置：0x666E,0x901A,0x9898（历史显示用语「普通题」）
#   注：执行计划原文写作 0x901D，经核对系笔误（0x901D=逝，0x901A=通），
#       本脚本按实际历史用语取 0x901A，交付文档中有说明。
# 检查范围：Vue template / 用户可见字符串 / 接口 message / AI 来源标题 /
#           当前演示文档 / 交付文档 / 执行计划
# 口径：
#   1) .java 仅检查字符串字面量内命中（注释不属用户可见文案）；
#   2) 历史来源字段只检查显示映射：数据值经前端映射为合规显示词的，
#      在 $allowRules 登记豁免（显示值已合规，不要求改写旧事件/不可变数据）；
#   3) docs 下历史审计文档为不可变记录，按文件名豁免；新交付文档必须干净。
# 退出码：0 = 无未豁免命中；1 = 存在未豁免命中（附文件与位置）
# 编码：本脚本必须保存为 UTF-8 with BOM（单个 BOM，Windows PowerShell 5.1 兼容）
# =====================================================================
$ErrorActionPreference = 'Stop'

# 检查词：码点组合（动态拼接，避免脚本自身成为命中源时无法自检）
$term = [string][char]0x666E + [string][char]0x901A + [string][char]0x9898

# 检查目标（相对仓库根）
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targets = @(
    (Join-Path $root '前端代码/oslab-frontend/src'),
    (Join-Path $root '后端代码/osLab-backend/src/main/java'),
    (Join-Path $root '演示脚本.md'),
    (Join-Path $root 'docs'),
    (Join-Path $root 'OSLab第三阶段执行计划-学习流程与UI重构.md')
)

# 不可变历史审计文档（记录当时事实，不随术语演进修订）
$historicalDocs = @(
    'docs/learning-phase2-audit\.md$',
    'docs/frontend-ui-audit-phase2\.md$',
    'docs/learning-contract-audit\.md$',
    'docs/phase3-baseline\.md$'
)

# 豁免规则：文件相对路径正则 + 行内容正则（同时命中才豁免），须写明理由
$allowRules = @(
    @{
        Path  = 'views/knowledgeGraph/KnowledgeGraphView\.vue$'
        Line  = '普通题作答: "题目"'
        Reason = 'evidence.source 历史数据值的显示映射键，显示值已改为合规术语「题目」'
    },
    @{
        Path  = 'LearningEventServiceImpl\.java$'
        Line  = '"普通题作答"'
        Reason = 'evidence.source 数据值（写入事件证据 JSON），前端经显示映射呈现为「题目」'
    },
    @{
        Path  = 'LearningAttributionService\.java$'
        Line  = '"普通题作答"'
        Reason = 'evidence.source 数据值（归因证据 JSON），前端经显示映射呈现为「题目」'
    },
    @{
        Path  = 'model/enums/learning/LearningEventTypeEnum\.java$'
        Line  = '"普通题作答"'
        Reason = '枚举数据文本，getText() 未用于任何用户可见输出（仅 name() 参与存储）'
    },
    @{
        Path  = 'model/enums/learning/LearningResourceTypeEnum\.java$'
        Line  = '"普通题"'
        Reason = '枚举数据文本，getText() 未用于任何用户可见输出（仅 name() 参与存储）'
    }
)

# 参与扫描的扩展名
$includeExt = @('.vue', '.ts', '.js', '.java', '.md', '.json', '.xml', '.yml', '.properties')

$hits = New-Object System.Collections.Generic.List[string]
$allowed = New-Object System.Collections.Generic.List[string]
$scanned = 0

foreach ($target in $targets) {
    if (-not (Test-Path $target)) { continue }
    $files = if (Test-Path $target -PathType Container) {
        Get-ChildItem $target -Recurse -File | Where-Object { $includeExt -contains $_.Extension }
    } else {
        @(Get-Item $target) | Where-Object { $includeExt -contains $_.Extension }
    }
    foreach ($file in $files) {
        $relPath = $file.FullName.Substring($root.Length).TrimStart('\', '/') -replace '\\', '/'
        $scanned++
        # 不可变历史文档：整体跳过
        $isHistorical = $false
        foreach ($h in $historicalDocs) {
            if ($relPath -match $h) { $isHistorical = $true; break }
        }
        if ($isHistorical) { continue }

        $isJava = $file.Extension -eq '.java'
        $lines = Get-Content $file.FullName -Encoding UTF8
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -notmatch [regex]::Escape($term)) { continue }
            $lineText = $lines[$i].Trim()
            # Java：仅字符串字面量内命中才算用户可见文案（注释命中跳过）
            if ($isJava -and $lineText -notmatch ('"[^"]*' + [regex]::Escape($term) + '[^"]*"')) {
                continue
            }
            $entry = "${relPath}:$($i + 1)  $lineText"
            $isAllowed = $false
            foreach ($rule in $allowRules) {
                if ($relPath -match $rule.Path -and $lineText -match $rule.Line) {
                    $isAllowed = $true
                    $allowed.Add("[豁免] $entry  （理由：$($rule.Reason)）")
                    break
                }
            }
            if (-not $isAllowed) { $hits.Add($entry) }
        }
    }
}

Write-Host "=== UI 术语检查（码点 0x666E,0x901A,0x9898） ==="
Write-Host "检查目标："
$targets | ForEach-Object { Write-Host "  - $($_.Substring($root.Length).TrimStart('\', '/'))" }
Write-Host "扫描文件数：$scanned"

if ($allowed.Count -gt 0) {
    Write-Host ""
    Write-Host "已豁免命中（显示映射 / 不可变数据，$($allowed.Count) 处）："
    $allowed | ForEach-Object { Write-Host "  $_" }
}

if ($hits.Count -gt 0) {
    Write-Host ""
    Write-Host "未豁免命中（$($hits.Count) 处，需人工确认是否为用户可见文案）："
    $hits | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
    Write-Host "RESULT = FAILED（存在未豁免命中）"
    exit 1
}

Write-Host ""
Write-Host "RESULT = PASSED（无未豁免命中）"
exit 0
