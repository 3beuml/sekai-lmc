# 构建「内置数据快照」：把 master data 裁好、连同中文名一起写进 app/src/main/assets/datapack/
#
# 产物会让 App 首次启动就能直接导入，不需要任何网络。
# 数据更新后重新跑本脚本 + 重新打包 APK 即可。
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\datapack\build.ps1 [--skip-fetch]

$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$skipFetch = $args -contains '--skip-fetch'

# ── 1. 源数据 ────────────────────────────────────────────────────
$rawDir = Join-Path $PSScriptRoot 'raw'
$cnDir = Join-Path $PSScriptRoot 'cn'

if (-not $skipFetch) {
    # ⚠️ 这里原来是「raw\cards.json 存在就跳过下载」，那是个**会让快照悄悄过期的陷阱**：
    # 数据在 09-13 的版本提交里更新过，而本地缓存是更早抓的（当时还抓到了 CDN 的旧副本），
    # 于是这份旧数据被打进了快照 —— 用户看到的现象是「卡牌少了 5 张、歌曲少了 2 首」。
    # 现在改成**每次都跑抓取脚本**：它内部按仓库的 blob sha 逐个核对，
    # 已经是最新的会跳过，只有对不上的才重下（实测 12 秒左右，比事后排查便宜得多）。
    Write-Host '核对源数据是否与上游仓库一致（只重下对不上的表）…'
    & node (Join-Path $PSScriptRoot 'fetch-raw.mjs')
    if ($LASTEXITCODE -ne 0) { throw '下载日服源数据失败' }

    & node (Join-Path $PSScriptRoot 'fetch-cn-overlay.mjs')
    if ($LASTEXITCODE -ne 0) {
        Write-Host '⚠️ 简中服数据有不完整之处（见上面的 404 列表），快照的中文名会少那几张表' -ForegroundColor Yellow
    }
}

# ── 2. 重新编译 app（保证打包用的是当前那份裁剪器）────────────────
# 与逻辑自检同理：拿旧的 class 去打包，产物会和源码不一致，而且不报错。
Write-Host ''
Write-Host '先重新编译 app 源码…'
& (Join-Path $root 'tools\compile-check.ps1')
if ($LASTEXITCODE -ne 0) { throw 'app 编译失败，无法打包' }

$work = Join-Path $root 'build\compile-check'
$envFile = Join-Path $work 'env.txt'
$envMap = @{}
foreach ($line in Get-Content $envFile) {
    $idx = $line.IndexOf('=')
    if ($idx -gt 0) { $envMap[$line.Substring(0, $idx)] = $line.Substring($idx + 1) }
}
$java = $envMap['java']
$klib = $envMap['klib']
$appClasses = $envMap['classes']
$appClasspath = $envMap['classpath']
foreach ($k in @('java','klib','classes','classpath')) {
    if ([string]::IsNullOrWhiteSpace($envMap[$k])) { throw "env.txt 里缺少 $k" }
}
$fullClasspath = "$appClasses;$appClasspath"

# ── 3. 编译打包工具 ──────────────────────────────────────────────
$outDir = Join-Path $root 'build\datapack'
$toolClasses = Join-Path $outDir 'classes'
New-Item -ItemType Directory -Path $toolClasses -Force | Out-Null

function ConvertTo-ArgLine([string]$a) {
    $s = $a.Replace('\', '/')
    if ($s -match '\s') { '"' + $s + '"' } else { $s }
}

$src = Join-Path $PSScriptRoot 'BuildDataPack.kt'
$argFile = Join-Path $outDir 'kotlinc-args.txt'
[IO.File]::WriteAllLines($argFile, @(
    '-Xmx3072m'
    '--enable-native-access=ALL-UNNAMED'
    '-cp'
    (ConvertTo-ArgLine (Join-Path $klib '*'))
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler'
    (ConvertTo-ArgLine $src)
    '-classpath'
    (ConvertTo-ArgLine $fullClasspath)
    '-jvm-target'
    '17'
    '-nowarn'
    '-d'
    (ConvertTo-ArgLine $toolClasses)
), (New-Object Text.UTF8Encoding($false)))

Write-Host '编译打包工具…'
$compileOut = & $java "@$argFile" 2>&1
if ($LASTEXITCODE -ne 0) {
    $compileOut | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
    throw '打包工具编译失败'
}

# ── 4. 运行 ──────────────────────────────────────────────────────
# 打包工具不需要联网（读本地 raw/ 与 cn/），但需要写 assets 目录。
$runArgFile = Join-Path $outDir 'java-args.txt'
[IO.File]::WriteAllLines($runArgFile, @(
    '-cp'
    (ConvertTo-ArgLine "$toolClasses;$fullClasspath")
    'BuildDataPackKt'
    (ConvertTo-ArgLine $root)
), (New-Object Text.UTF8Encoding($false)))

Write-Host ''
& $java "@$runArgFile" 2>&1 | ForEach-Object { Write-Host $_ }
$code = $LASTEXITCODE
Write-Host ''
if ($code -eq 0) {
    Write-Host '[结果] 内置快照已生成 → app\src\main\assets\datapack\' -ForegroundColor Green
} else {
    Write-Host "[结果] 打包失败（exit=$code）" -ForegroundColor Red
}
exit $code
