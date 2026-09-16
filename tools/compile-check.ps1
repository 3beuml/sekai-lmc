<#
.SYNOPSIS
    用 Android Studio 自带的 Kotlin 编译器 + 已下载的依赖缓存，对全部 Kotlin 源码做编译检查。

.DESCRIPTION
    为什么需要它：完整的 Gradle 构建要跑 KSP(Room)、AAPT2、D8，一次要几分钟；
    而「我改了一行 Kotlin，语法/类型对不对」这个问题，用这个脚本十几秒就能回答。

    它做三件事：
      1. 从 Gradle 依赖缓存（~/.gradle/caches/modules-2/files-2.1）里收集所有 jar，
         并把 aar 里的 classes.jar 解出来；
      2. 直接用 java 调用 Kotlin 编译器主类（**故意不用 kotlinc.bat** ——
         批处理要经 cmd.exe，单行上限 8191 字符，271 项 classpath 必然超限报
         「The command line is too long」）；
      3. 带上 Compose 与 kotlinx.serialization 两个编译器插件，编译 app/src/main/java 下全部 .kt。

    前置条件：Android Studio 已经成功同步过一次工程（这样依赖才会在缓存里）。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\compile-check.ps1

.NOTES
    ⚠️ 它**不能**替代完整构建。这里不跑 KSP 注解处理（Room 的代码生成与 @Query 校验）、
    不跑 AAPT2 资源编译、不跑 D8/打包。它只回答「Kotlin 源码本身编不编得过」。
    另：用的是 Android Studio 自带的 Kotlin 编译器版本，可能与工程声明的版本略有差异。
#>

[CmdletBinding()]
param(
    # Android Studio 安装目录（自动探测）
    [string]$AndroidStudioHome,
    # android.jar（自动从 SDK 取最新的）
    [string]$AndroidJar,
    # 只编译单个文件时用（调试方便）；留空则编译全部
    [string]$Only
)

$ErrorActionPreference = 'Continue'
$proj = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$src = Join-Path $proj 'app\src\main\java'
$work = Join-Path $proj 'build\compile-check'
$cpDir = Join-Path $work 'cp'

# ── 1. 找 Android Studio（里面有 kotlinc 和 JDK）────────────────────
#
# 查找顺序：显式参数 → 环境变量 ANDROID_STUDIO_HOME → local.properties 里的
# androidStudioHome（这两个都是**本机专属、不进仓库**的写法）→ 常见安装位置。
if (-not $AndroidStudioHome -and $env:ANDROID_STUDIO_HOME) {
    $AndroidStudioHome = $env:ANDROID_STUDIO_HOME
}
if (-not $AndroidStudioHome) {
    $localProps = Join-Path $proj 'local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^\s*androidStudioHome\s*=' } | Select-Object -First 1
        if ($line) { $AndroidStudioHome = ($line -split '=', 2)[1].Trim() -replace '\\\\', '\' }
    }
}
if (-not $AndroidStudioHome) {
    $cands = @(
        'C:\Program Files\Android\Android Studio',
        "$env:LOCALAPPDATA\Programs\Android Studio",
        "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\AndroidStudio"
    )
    $AndroidStudioHome = $cands | Where-Object { Test-Path (Join-Path $_ 'plugins\Kotlin\kotlinc\lib\kotlin-compiler.jar') } | Select-Object -First 1
}
if (-not $AndroidStudioHome) {
    throw '找不到 Android Studio（需要它自带的 kotlinc）。可用 -AndroidStudioHome 指定，或设环境变量 ANDROID_STUDIO_HOME，或在 local.properties 里写 androidStudioHome=...'
}
$java = Join-Path $AndroidStudioHome 'jbr\bin\java.exe'
$klib = Join-Path $AndroidStudioHome 'plugins\Kotlin\kotlinc\lib'
Write-Host "Android Studio : $AndroidStudioHome"

# ── 2. 找 android.jar ──────────────────────────────────────────────
if (-not $AndroidJar) {
    $sdkCands = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT
    ) | Where-Object { $_ -and (Test-Path $_) }
    foreach ($sdk in $sdkCands) {
        $p = Get-ChildItem (Join-Path $sdk 'platforms') -Filter 'android.jar' -Recurse -ErrorAction SilentlyContinue |
             Sort-Object FullName -Descending | Select-Object -First 1
        if ($p) { $AndroidJar = $p.FullName; break }
    }
}
if (-not $AndroidJar -or -not (Test-Path $AndroidJar)) { throw '找不到 android.jar，请用 -AndroidJar 指定。' }
Write-Host "android.jar    : $AndroidJar"

# ── 3. 组装 classpath ──────────────────────────────────────────────
#
# 首选：**Gradle 导出的真实 classpath**（app/build/compile-classpath.txt，
# 由 `gradle :app:writeCompileClasspath` 生成）。
#
# 为什么不再默认去扫 Gradle 缓存：缓存目录是**全机器共用**的，同一个构件可能存着多个版本
# （本机其它工程留下的）。全塞进 classpath 后旧版本可能排在前面，于是出现
# 「旧版 Compose 遮蔽新版 Compose」的假失败 —— 甚至可能假通过，那更危险。
# 实测差距：扫缓存得到 480 项 / 49,353 字符（其中 400 多项与本工程无关），
# 而 Gradle 导出的只有 64 项 / 7,371 字符。
$gradleCpFile = Join-Path $proj 'app\build\compile-classpath.txt'
$classpath = $null

if (Test-Path $gradleCpFile) {
    $raw = (Get-Content $gradleCpFile -Raw).Trim()
    if ($raw.Length -gt 0) {
        $items = @($raw -split ';' | Where-Object { $_ })
        # android.jar 不一定在 Gradle 的编译 classpath 里，缺了就补上
        if (-not ($items | Where-Object { $_ -match 'platforms.*android\.jar$' })) {
            $items = @($AndroidJar) + $items
        }
        $classpath = $items -join ';'
        Write-Host "classpath      : Gradle 导出，$($items.Count) 项 / $($classpath.Length) 字符"
    }
}

if (-not $classpath) {
Write-Host '⚠️ 没找到 Gradle 导出的 classpath，退回「扫描 Gradle 缓存」的老办法。' -ForegroundColor Yellow
Write-Host '   老办法可能因缓存里存在多版本而产生假失败/假通过。建议先执行：' -ForegroundColor Yellow
Write-Host '     gradle :app:writeCompileClasspath' -ForegroundColor Yellow

$cacheBase = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
if (-not (Test-Path $cacheBase)) {
    throw "依赖缓存不存在：$cacheBase`n请先用 Android Studio 成功同步一次工程。"
}

New-Item -ItemType Directory -Force -Path $cpDir | Out-Null
$stamp = Join-Path $work '.stamp'
$needRebuild = $true
if (Test-Path $stamp) {
    $newest = (Get-ChildItem $cacheBase -Recurse -File -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    if ($newest -and (Get-Item $stamp).LastWriteTime -gt $newest) { $needRebuild = $false }
}

if ($needRebuild) {
    Write-Host '重新收集依赖（首次或缓存有更新）…'
    Remove-Item $cpDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $cpDir | Out-Null

    $i = 0
    Get-ChildItem $cacheBase -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -eq '.jar' -and $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        ForEach-Object { $i++; Copy-Item $_.FullName (Join-Path $cpDir ('{0:d3}-{1}' -f $i, $_.Name)) -Force -ErrorAction SilentlyContinue }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $a = 0
    Get-ChildItem $cacheBase -Recurse -File -Filter '*.aar' -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $zip = [IO.Compression.ZipFile]::OpenRead($_.FullName)
            $e = $zip.Entries | Where-Object { $_.FullName -eq 'classes.jar' }
            if ($e) {
                $a++
                [IO.Compression.ZipFileExtensions]::ExtractToFile(
                    $e, (Join-Path $cpDir ('aar{0:d3}-{1}-classes.jar' -f $a, $_.BaseName)), $true)
            }
            $zip.Dispose()
        } catch { }
    }
    Write-Host "  jar $i 个，aar 解出的 classes.jar $a 个"
    New-Item -ItemType File -Path $stamp -Force | Out-Null
} else {
    Write-Host '复用已收集的依赖（缓存无变化）'
}

$cpJars = (Get-ChildItem $cpDir -Filter *.jar -ErrorAction SilentlyContinue).FullName
if (-not $cpJars) { throw '没收集到任何依赖 jar。' }
$classpath = (@($AndroidJar) + $cpJars) -join ';'
}

# ── 4. 编译 ────────────────────────────────────────────────────────
if ($Only) {
    $files = @((Resolve-Path $Only).Path)
} else {
    $files = (Get-ChildItem $src -Recurse -Filter *.kt -File).FullName
}
Write-Host "源文件 $($files.Count) 个，classpath $(($classpath -split ';').Count) 项 / $($classpath.Length) 字符"

$classesDir = Join-Path $work 'classes'
Remove-Item $classesDir -Recurse -Force -ErrorAction SilentlyContinue

# 把编译环境落盘，给 tools/logic-check.ps1 复用。
# 为什么要复用而不是各算一遍：收集依赖要扫描整个 Gradle 缓存（几秒），
# 而且两边的 classpath 一旦不一致，就会出现「编译过了但自检跑不起来」这种假问题。
Set-Content -Path (Join-Path $work 'env.txt') -Encoding ASCII -Value @(
    "java=$java"
    "klib=$klib"
    "work=$work"
    "classes=$classesDir"
    "classpath=$classpath"
)

$sw = [Diagnostics.Stopwatch]::StartNew()

# Windows 的 CreateProcess 命令行上限是 32767 字符，依赖变多后 classpath 会顶破它
# （实测 35639 字符 → 报 "The filename or extension is too long"）。
# Java 9+ 支持 @argfile：把参数写进文件，命令行上只留一个 @文件路径。
# 注意：argfile 里反斜杠是转义字符，所以统一换成正斜杠；只有含空白的参数才加引号。
function ConvertTo-ArgLine([string]$a) {
    $s = $a.Replace('\', '/')
    if ($s -match '\s') { '"' + $s + '"' } else { $s }
}

$argFile = Join-Path $work 'kotlinc-args.txt'
$argLines = @(
    '-Xmx1536m'
    '--enable-native-access=ALL-UNNAMED'
    '-cp'
    (ConvertTo-ArgLine (Join-Path $klib '*'))
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler'
)
$argLines += $files | ForEach-Object { ConvertTo-ArgLine $_ }
$argLines += @(
    '-classpath'
    (ConvertTo-ArgLine $classpath)
    '-Xplugin=' + (ConvertTo-ArgLine (Join-Path $klib 'compose-compiler-plugin.jar'))
    '-Xplugin=' + (ConvertTo-ArgLine (Join-Path $klib 'kotlinx-serialization-compiler-plugin.jar'))
    '-jvm-target'
    '17'
    '-nowarn'
    '-d'
    (ConvertTo-ArgLine $classesDir)
)
# argfile 必须是 ASCII/UTF-8 无 BOM，否则 Java 会把 BOM 当成参数的一部分
[IO.File]::WriteAllLines($argFile, $argLines, (New-Object Text.UTF8Encoding($false)))

$raw = & $java "@$argFile" 2>&1
$sw.Stop()
$code = $LASTEXITCODE
$raw | Set-Content (Join-Path $work 'compile.log') -Encoding UTF8

$errs = @($raw | Where-Object { $_ -match ': error: ' })
Write-Host ''
Write-Host ("耗时 {0}s   exit={1}   输出 {2} 行" -f [math]::Round($sw.Elapsed.TotalSeconds, 1), $code, $raw.Count)

if ($raw.Count -lt 3) {
    # exit=1 且几乎没有输出，通常是参数问题（例如命令行过长）
    Write-Host '[结果] 编译器没有真正执行，不能判定通过' -ForegroundColor Yellow
    $raw | Select-Object -First 5 | ForEach-Object { "  |$_|" }
    exit 2
}

if ($errs.Count -eq 0 -and $code -eq 0) {
    $n = (Get-ChildItem $classesDir -Recurse -Filter *.class -ErrorAction SilentlyContinue).Count
    Write-Host "[结果] PASS —— 全部 $($files.Count) 个文件编译通过，生成 $n 个 class" -ForegroundColor Green
    exit 0
}

Write-Host "[结果] FAIL —— $($errs.Count) 个错误" -ForegroundColor Red
Write-Host ''
Write-Host '按文件分组：'
$errs | ForEach-Object { Split-Path (($_ -split ': error: ')[0]) -Leaf } |
    Group-Object | Sort-Object Count -Descending |
    ForEach-Object { '  {0,-34} {1}' -f $_.Name, $_.Count }
Write-Host ''
Write-Host '错误明细（前 30 条）：'
$errs | Select-Object -First 30 | ForEach-Object {
    $p = $_ -split ': error: '
    '  {0}' -f $_
}
Write-Host ''
Write-Host "完整日志：$(Join-Path $work 'compile.log')"
exit 1
