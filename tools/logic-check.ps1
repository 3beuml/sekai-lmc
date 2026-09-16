# 离线逻辑自检：用真实数据夹具跑一遍卡牌/技能的核心逻辑。
#
# 为什么需要：这些逻辑出错**不会报错**，只会显示错误的数字
# （比如特训后数值算错一个下标、等级序列顺序反了），
# 而本机没有能跑 App 的设备，光靠「编译通过」完全覆盖不到。
#
# 依赖 tools/compile-check.ps1 产出的 build/compile-check/env.txt
# （里面有 java 路径、kotlinc 路径与完整 classpath）。
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\logic-check.ps1

# 注意这里**不能**用 Stop：JDK 25 上 kotlinc 会往 stderr 打
# "sun.misc.Unsafe has been called" 警告，Windows PowerShell 5.1 会把原生命令的 stderr
# 当成错误记录，Stop 策略会因此直接中断整个脚本。退出码由 $LASTEXITCODE 单独判断。
$ErrorActionPreference = 'Continue'

# 每次都先重新编译 app。
#
# 为什么不能省：自检是拿 app 的 class 去跑的，一旦 class 是旧的，自检**会通过**但
# 验的是旧代码 —— 属于「假信心」，比不跑还危险。
# （这个坑真的踩过：改了 TableSchemas 的主键配置后自检仍报旧值，因为它编译的是旧 class。）
# compile-check 有依赖缓存，重复跑大约 20 秒，值得。
Write-Host '先重新编译 app 源码（保证自检跑的是当前代码）…'
& (Join-Path $PSScriptRoot 'compile-check.ps1')
if ($LASTEXITCODE -ne 0) { throw 'app 编译失败，自检无法进行' }

$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path $root 'build\compile-check'
$envFile = Join-Path $work 'env.txt'

if (-not (Test-Path $envFile)) {
    Write-Host '没找到 build/compile-check/env.txt，先跑一次 tools/compile-check.ps1' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'compile-check.ps1')
    if (-not (Test-Path $envFile)) { throw 'compile-check.ps1 没有产出 env.txt' }
}

# 读环境文件（classpath= 那一行可能非常长）
$envMap = @{}
foreach ($line in Get-Content $envFile) {
    $idx = $line.IndexOf('=')
    if ($idx -gt 0) { $envMap[$line.Substring(0, $idx)] = $line.Substring($idx + 1) }
}
$java = $envMap['java']
$klib = $envMap['klib']
$appClasses = $envMap['classes']
$appClasspath = $envMap['classpath']

foreach ($pair in @(@('java', $java), @('klib', $klib), @('classes', $appClasses), @('classpath', $appClasspath))) {
    if ([string]::IsNullOrWhiteSpace($pair[1])) { throw "env.txt 里缺少 $($pair[0])" }
}
if (-not (Test-Path $appClasses)) {
    Write-Host 'app 的 class 目录不存在，先跑 tools/compile-check.ps1' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'compile-check.ps1')
}

# ── 1. 夹具 ────────────────────────────────────────────────────────
$fixtureDir = Join-Path $PSScriptRoot 'logic-check\fixtures'
if (-not (Test-Path (Join-Path $fixtureDir 'card1-raw.json'))) {
    Write-Host '夹具不存在，正在生成（需要联网）…'
    & node (Join-Path $PSScriptRoot 'logic-check\make-fixtures.mjs')
    if ($LASTEXITCODE -ne 0) { throw '生成夹具失败' }
}

# ── 2. 编译自检代码 ────────────────────────────────────────────────
$checkSrc = Join-Path $PSScriptRoot 'logic-check\LogicCheck.kt'
$outDir = Join-Path $root 'build\logic-check'
$checkClasses = Join-Path $outDir 'classes'
New-Item -ItemType Directory -Path $checkClasses -Force | Out-Null

$fullClasspath = "$appClasses;$appClasspath"

# 同样用 @argfile 绕开 32767 字符的命令行上限
function ConvertTo-ArgLine([string]$a) {
    $s = $a.Replace('\', '/')
    if ($s -match '\s') { '"' + $s + '"' } else { $s }
}

$argFile = Join-Path $outDir 'kotlinc-args.txt'
[IO.File]::WriteAllLines($argFile, @(
    '-Xmx1024m'
    '--enable-native-access=ALL-UNNAMED'
    '-cp'
    (ConvertTo-ArgLine (Join-Path $klib '*'))
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler'
    (ConvertTo-ArgLine $checkSrc)
    '-classpath'
    (ConvertTo-ArgLine $fullClasspath)
    '-jvm-target'
    '17'
    '-nowarn'
    '-d'
    (ConvertTo-ArgLine $checkClasses)
), (New-Object Text.UTF8Encoding($false)))

Write-Host '编译自检代码…'
$compileOut = & $java "@$argFile" 2>&1
if ($LASTEXITCODE -ne 0) {
    $compileOut | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
    throw '自检代码编译失败'
}

# ── 3. 运行 ────────────────────────────────────────────────────────
# 运行期的 classpath 比编译期更长（还多了自检自己的 class），同样走 @argfile
$runArgFile = Join-Path $outDir 'java-args.txt'
[IO.File]::WriteAllLines($runArgFile, @(
    '-cp'
    (ConvertTo-ArgLine "$checkClasses;$fullClasspath")
    'LogicCheckKt'
    (ConvertTo-ArgLine $fixtureDir)
), (New-Object Text.UTF8Encoding($false)))

Write-Host ''
$runLog = Join-Path $outDir 'run.log'
& $java "@$runArgFile" 2>&1 | Out-File -FilePath $runLog -Encoding utf8
$code = $LASTEXITCODE
Get-Content $runLog | ForEach-Object { Write-Host $_ }
Write-Host ''
if ($code -eq 0) {
    Write-Host '[结果] 逻辑自检通过' -ForegroundColor Green
} else {
    Write-Host "[结果] 逻辑自检失败（exit=$code）" -ForegroundColor Red
}
exit $code
