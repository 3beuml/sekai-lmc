<#
.SYNOPSIS
    按远端实际大小重新生成 MasterCatalog.kt 的表清单与体积。

.DESCRIPTION
    游戏每次大更新后，master data 的表可能新增、改名、体积变化。
    本脚本从 MasterCatalog.kt 读出「表名 + 模块 + 是否参与中文名叠加」，
    再取每张表的真实字节数，最后就地重写 ALL 列表块。这样：
      - 表名会被保序、按模块分组、按体积升序排列；
      - 体积变成精确值（而不是靠人估算）；
      - 若某张表已从远端消失，会被明确报告出来（需要你手动从清单中移除或改名）。

    取体积有两种方式：
      1. 默认：对 GitHub Pages 上的每个文件发 HEAD 请求读 Content-Length。
         优点：不受 GitHub API 限速影响；缺点：表多时较慢（68 张表约需十几秒）。
      2. -TreeFile <path>：用本地保存的 `git/trees?recursive=1` 响应解析体积。
         优点：一次拿全；缺点：该响应会被 GitHub 截断，截断区内的表会拿不到体积而保持 0。

.EXAMPLE
    pwsh -File tools\regen-catalog.ps1
    pwsh -File tools\regen-catalog.ps1 -Region cn
    pwsh -File tools\regen-catalog.ps1 -DryRun          # 只看会改成什么，不写文件

.NOTES
    需要联网。如果提示「禁止运行脚本」，用：
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\regen-catalog.ps1

    注意：ALL 块内的**手写注释不会被保留**（模块分组注释是自动生成的）。
    想把某条说明固定下来，请写进 `MasterCatalog.kt` 里 `object TableCatalog` 的 KDoc，
    或者写进 `TableSpec` 字段的注释里 —— 那些位置不在被重写的范围内。
#>

[CmdletBinding()]
param(
    [ValidateSet('jp', 'en', 'cn', 'kr', 'tw')]
    [string]$Region = 'jp',

    # 可选的本地 tree 响应文件（离线/加速用）
    [string]$TreeFile,

    # 只打印结果，不改写 MasterCatalog.kt
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Windows PowerShell 5.1 默认可能没启用 TLS 1.2，会导致 SSL 协商失败
try {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$repoSuffix = @{ jp = '-diff'; en = '-en-diff'; cn = '-cn-diff'; kr = '-kr-diff'; tw = '-tc-diff' }[$Region]
$pagesBase = "https://sekai-world.github.io/sekai-master-db$repoSuffix"

$catalog = Join-Path $PSScriptRoot '..\app\src\main\java\com\pjsk\toolbox\data\remote\MasterCatalog.kt'
$catalog = [IO.Path]::GetFullPath($catalog)
if (-not (Test-Path $catalog)) { throw "找不到 MasterCatalog.kt：$catalog" }

$src = Get-Content $catalog -Raw

# ── 1. 解析现有清单，保留模块、cnOverlay 标记与已有体积 ────────────
# 保留已有体积很重要：若某次取不到远端体积（例如 tree 响应被截断），
# 绝不能把已经知道的精确体积降级成 0。
$entries = New-Object 'System.Collections.Specialized.OrderedDictionary'
foreach ($m in [regex]::Matches($src, 'TableSpec\("([^"]+)",\s*DataModule\.(\w+),\s*([0-9_]+)L(,\s*cnOverlay\s*=\s*true)?')) {
    $existingSize = [int64]($m.Groups[3].Value -replace '_', '')
    $entries[$m.Groups[1].Value] = @($m.Groups[2].Value, $m.Groups[4].Success, $existingSize)
}
if ($entries.Count -eq 0) { throw '未能从 MasterCatalog.kt 解析出任何 TableSpec。' }
Write-Host "从 MasterCatalog.kt 解析到 $($entries.Count) 张表" -ForegroundColor Cyan

# ── 2. 取真实体积 ─────────────────────────────────────────────────
$sizes = @{}

if ($TreeFile) {
    if (-not (Test-Path $TreeFile)) { throw "找不到 -TreeFile 指定的文件：$TreeFile" }
    Write-Host "从本地 tree 文件读取体积：$TreeFile"
    $raw = Get-Content $TreeFile -Raw
    foreach ($m in [regex]::Matches($raw, '"path":"([^"]+\.json)","mode":"100644","type":"blob","sha":"[0-9a-f]+","size":(\d+)')) {
        $sizes[$m.Groups[1].Value] = [int64]$m.Groups[2].Value
    }
    Write-Host "  解析到 $($sizes.Count) 条体积记录`n"
}
else {
    Write-Host "逐表发 HEAD 请求读取真实字节数（$($entries.Count) 张，请稍候）…" -ForegroundColor Cyan
    $headHeaders = @{ 'Accept-Encoding' = 'identity'; 'User-Agent' = 'sekai-lmc-regen' }
    $i = 0
    foreach ($name in $entries.Keys) {
        $i++
        Write-Progress -Activity '读取远端体积' -Status $name -PercentComplete (100 * $i / $entries.Count)
        try {
            $resp = Invoke-WebRequest -Uri "$pagesBase/$name" -Method Head -TimeoutSec 30 `
                -Headers $headHeaders -UseBasicParsing
            $sizes[$name] = [int64]$resp.Headers['Content-Length']
        }
        catch {
            # 不写入 $sizes，后续会被当作「远端不存在」报告
        }
    }
    Write-Progress -Activity '读取远端体积' -Completed
    Write-Host ""
}

# ── 3. 对比，找出取不到体积的表 ────────────────────────────────────
# 这些表会沿用清单里已有的体积（而不是被清零），并在最后被报告出来。
$fellBack = @()
foreach ($name in $entries.Keys) {
    if (-not $sizes.ContainsKey($name)) { $fellBack += $name }
}
if ($fellBack.Count -gt 0) {
    Write-Host "[i] 以下表本次取不到体积，将沿用清单里已有的值：" -ForegroundColor Yellow
    foreach ($name in $fellBack) {
        Write-Host ("    - {0}  → 沿用 {1} 字节" -f $name, $entries[$name][2])
    }
    Write-Host ""
}

# ── 4. 生成新的 ALL 块 ────────────────────────────────────────────
$order = @('CHARACTER', 'MUSIC', 'EVENT', 'STICKER', 'CARD', 'GACHA', 'COSTUME')
$header = @{
    CHARACTER = '// ── 角色 ────────────────────────────────────────────────'
    MUSIC     = '// ── 音乐 ────────────────────────────────────────────────'
    EVENT     = '// ── 活动 ────────────────────────────────────────────────'
    STICKER   = '// ── 贴纸（表情包制作） ──────────────────────────────────'
    CARD      = '// ── 卡牌（体积大） ──────────────────────────────────────'
    GACHA     = '// ── 扭蛋卡池（体积很大） ────────────────────────────────'
    COSTUME   = '// ── 服装（体积最大） ────────────────────────────────────'
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('    val ALL: List<TableSpec> = listOf(')
foreach ($mod in $order) {
    $rows = @()
    foreach ($name in $entries.Keys) {
        if ($entries[$name][0] -ne $mod) { continue }
        # 取不到远端体积时沿用清单里的旧值，避免把已知信息清零
        $size = if ($sizes.ContainsKey($name)) { $sizes[$name] } else { $entries[$name][2] }
        $rows += [pscustomobject]@{ name = $name; size = $size; overlay = $entries[$name][1] }
    }
    if ($rows.Count -eq 0) { continue }
    [void]$sb.AppendLine("        $($header[$mod])")
    foreach ($r in ($rows | Sort-Object size)) {
        $sizeText = '{0}L' -f $r.size.ToString('N0').Replace(',', '_')
        $flag = if ($r.overlay) { ', cnOverlay = true' } else { '' }
        [void]$sb.AppendLine("        TableSpec(`"$($r.name)`", DataModule.$mod, $sizeText$flag),")
    }
    [void]$sb.AppendLine('')
}
# 注意：结尾**不要**带换行。被匹配的原文正是到 `    )` 为止，它自己的换行还留在文件里；
# 这里若再补一个换行，每次运行都会多出一个空行（曾经真的踩过这个坑）。
$newBlock = $sb.ToString().TrimEnd("`r", "`n") + "`r`n    )"

# ── 5. 就地替换 ───────────────────────────────────────────────────
$pattern = '(?s)    val ALL: List<TableSpec> = listOf\(.*?\r?\n    \)'
if ($src -notmatch $pattern) { throw '没有匹配到 ALL 列表块，未做修改（文件结构可能被改过）。' }

if ($DryRun) {
    Write-Host "=== -DryRun：以下是将写入的内容 ===" -ForegroundColor Yellow
    Write-Host $newBlock
    Write-Host "未修改任何文件。"
}
else {
    $updated = [regex]::Replace(
        $src, $pattern,
        [System.Text.RegularExpressions.MatchEvaluator] { param($m) $newBlock }, 1)
    [IO.File]::WriteAllText($catalog, $updated, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "[OK] 已重写 $catalog" -ForegroundColor Green
    Write-Host "     表数：$($entries.Count)　远端取到体积：$($sizes.Count)"
    Write-Host "     建议接着跑 tools\verify.ps1 复核一遍。"
}
