<#
.SYNOPSIS
    校验 sekai-lmc 的数据契约是否仍然成立。

.DESCRIPTION
    本脚本需要**联网**（在你自己机器上运行；交付本工程的沙箱环境无法联网）。

    它会做三件事：
      1. 从 MasterCatalog.kt 里读出全部表名，逐个用 HEAD 请求确认远端是否仍存在，
         并把「实际体积」与代码里记录的 approxBytes 对比，标出偏差。
      2. 读取日服最新 commit，打印当前的 master version / asset version
         （这就是 App 里「检查更新」用的版本号来源）。
      3. 抽查若干素材路径是否仍然可达。

    游戏每次大更新后跑一遍，就能知道：
      - 有没有表被改名/删除（App 会静默失败，必须靠这个发现）
      - 有没有表体积大幅变化（可能官方改了结构，值得看一眼）
      - 素材路径有没有失效

.EXAMPLE
    pwsh -File tools\verify.ps1
    pwsh -File tools\verify.ps1 -Region cn      # 校验简中区服的仓库
#>

[CmdletBinding()]
param(
    [ValidateSet('jp', 'en', 'cn', 'kr', 'tw')]
    [string]$Region = 'jp',

    # 体积偏差超过该百分比才报警
    [int]$SizeTolerancePercent = 25
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Windows PowerShell 5.1 默认可能只启用 TLS 1.0，而 GitHub / storage.sekai.best 要求 TLS 1.2，
# 不显式开启会报「无法创建 SSL/TLS 安全通道」。PowerShell 7 上这行是多余的（无害）。
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

# 区服 -> 仓库名后缀（注意：繁中的仓库是 -tc-diff，不是 -tw-diff）
$repoSuffix = @{ jp = '-diff'; en = '-en-diff'; cn = '-cn-diff'; kr = '-kr-diff'; tw = '-tc-diff' }[$Region]
$pagesBase = "https://sekai-world.github.io/sekai-master-db$repoSuffix"

$catalogPath = Join-Path $PSScriptRoot '..\app\src\main\java\com\pjsk\toolbox\data\remote\MasterCatalog.kt'
if (-not (Test-Path $catalogPath)) {
    throw "找不到 MasterCatalog.kt：$catalogPath"
}

Write-Host "`n=== 1/3 表存在性与体积校验（区服：$Region）===" -ForegroundColor Cyan
Write-Host "基址：$pagesBase`n"

# 从 Kotlin 源码里抽取 TableSpec("xxx.json", DataModule.YYY, 12345L)
$source = Get-Content $catalogPath -Raw
$specList = [regex]::Matches(
    $source,
    'TableSpec\("([^"]+)",\s*DataModule\.(\w+),\s*([0-9_]+)L'
)

if ($specList.Count -eq 0) {
    throw '未能从 MasterCatalog.kt 解析出任何 TableSpec，请检查该文件的写法是否被改动。'
}

Write-Host ("共解析到 {0} 张表`n" -f $specList.Count)

# 关键：显式要求不压缩。否则 GitHub Pages 会返回 gzip 后的 Content-Length，
# 与代码里记录的「真实文件体积」对不上，会产生大量假报警。
$headHeaders = @{ 'Accept-Encoding' = 'identity'; 'User-Agent' = 'sekai-lmc-verify' }

$missing = @()
$changed = @()
$unknownSize = @()
$ok = 0

foreach ($m in $specList) {
    $file = $m.Groups[1].Value
    $module = $m.Groups[2].Value
    $recorded = [int64]($m.Groups[3].Value -replace '_', '')

    try {
        $resp = Invoke-WebRequest -Uri "$pagesBase/$file" -Method Head -TimeoutSec 30 `
            -Headers $headHeaders -UseBasicParsing
        $actual = [int64]$resp.Headers['Content-Length']
    }
    catch {
        $missing += [pscustomobject]@{ 表 = $file; 模块 = $module; 问题 = '远端 404 / 请求失败' }
        continue
    }

    if ($recorded -eq 0) {
        $unknownSize += [pscustomobject]@{ 表 = $file; 模块 = $module; 实际KB = [math]::Round($actual / 1KB, 1) }
        $ok++
        continue
    }

    $diffPercent = [math]::Abs(($actual - $recorded) / [double]$recorded) * 100
    if ($diffPercent -gt $SizeTolerancePercent) {
        $changed += [pscustomobject]@{
            表            = $file
            模块          = $module
            记录KB        = [math]::Round($recorded / 1KB, 1)
            实际KB        = [math]::Round($actual / 1KB, 1)
            偏差百分比    = [math]::Round($diffPercent, 1)
        }
    }
    else {
        $ok++
    }
}

Write-Host ("存在且体积正常：{0} 张" -f $ok) -ForegroundColor Green

if ($missing.Count -gt 0) {
    Write-Host "`n[!] 远端已不存在的表（必须从 MasterCatalog.kt 移除或改名）：" -ForegroundColor Red
    $missing | Format-Table -AutoSize
}

if ($changed.Count -gt 0) {
    Write-Host "`n[!] 体积明显变化的表（建议人工看一眼是否结构改动）：" -ForegroundColor Yellow
    $changed | Format-Table -AutoSize
}

if ($unknownSize.Count -gt 0) {
    Write-Host "`n[i] 代码里标记为「体积未知」(0L) 的表，实测体积如下，建议回填到 MasterCatalog.kt：" -ForegroundColor Yellow
    $unknownSize | Format-Table -AutoSize
}

Write-Host "`n=== 2/3 远端数据版本 ===" -ForegroundColor Cyan
try {
    $headers = @{ 'Accept' = 'application/vnd.github+json'; 'User-Agent' = 'sekai-lmc-verify' }
    $commits = Invoke-RestMethod -Uri "https://api.github.com/repos/Sekai-World/sekai-master-db$repoSuffix/commits?per_page=1" -Headers $headers -TimeoutSec 30
    $msg = $commits[0].commit.message
    $date = $commits[0].commit.committer.date
    Write-Host "最新 commit message : $msg"
    Write-Host "提交时间            : $date"
    Write-Host "（App 里的「检查更新」就是比对这里的版本号；未鉴权时 GitHub API 限速 60 次/小时/IP）"
}
catch {
    Write-Host "版本探测失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "（多半是触发 GitHub 限速，稍后再试即可；这不影响 App 直接下载数据）"
}

Write-Host "`n=== 3/3 素材路径抽查 ===" -ForegroundColor Cyan
$assetBase = "https://storage.sekai.best/sekai-$Region-assets"
$assetChecks = @(
    @{ 名称 = '曲绘';       Url = "$assetBase/music/jacket/jacket_s_001/jacket_s_001.webp";          状态 = '已实测' },
    @{ 名称 = '卡牌缩略图'; Url = "$assetBase/character/member/res001_no001/card_normal.webp";      状态 = '已实测' },
    @{ 名称 = '卡牌立绘';   Url = "$assetBase/character/member_cutout/res001_no001/normal.webp";    状态 = '已实测' },
    @{ 名称 = '贴纸';       Url = "$assetBase/stamp/stamp0001/stamp0001.webp";                      状态 = '已实测' },
    @{ 名称 = '卡池 Logo';  Url = "$assetBase/gacha/ab_gacha_326/logo/logo.webp";                   状态 = '已实测' },
    @{ 名称 = '活动徽章';   Url = "$assetBase/event/event_awakening_2021/icon/icon_eventbadge_1.webp"; 状态 = '已实测' },
    @{ 名称 = '角色立绘';   Url = "$assetBase/character/character_select/chr_1/chr_1.webp";         状态 = '未实测，仅结构参考' }
)

foreach ($c in $assetChecks) {
    try {
        $r = Invoke-WebRequest -Uri $c.Url -Method Head -TimeoutSec 20 `
            -Headers @{ 'Accept-Encoding' = 'identity'; 'User-Agent' = 'sekai-lmc-verify' } `
            -UseBasicParsing
        $sizeKb = [math]::Round([int64]$r.Headers['Content-Length'] / 1KB, 1)
        Write-Host ("  [OK]   {0,-12} {1,8} KB  ({2})" -f $c.名称, $sizeKb, $c.状态) -ForegroundColor Green
    }
    catch {
        Write-Host ("  [FAIL] {0,-12} {1}  ({2})" -f $c.名称, '不可达', $c.状态) -ForegroundColor Red
        Write-Host ("         {0}" -f $c.Url) -ForegroundColor DarkGray
    }
}

Write-Host "`n提示：想自己摸清素材桶的完整结构，可以直接列目录（S3 list-type=2）：" -ForegroundColor DarkGray
Write-Host "  $assetBase/?list-type=2&max-keys=60&delimiter=/" -ForegroundColor DarkGray
Write-Host "  $assetBase/?list-type=2&prefix=stamp/&max-keys=10" -ForegroundColor DarkGray
Write-Host "`n完成。`n"
