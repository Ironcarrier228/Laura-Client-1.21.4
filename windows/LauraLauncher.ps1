#Requires -Version 5.1
<#
Laura Client - запускатель для Windows.

Что делает скрипт (все шаги идемпотентны - повторный запуск докачивает только недостающее):
  1. Находит Java 21 (PATH, JAVA_HOME, типовые папки установки; при отсутствии - предлагает поставить через winget).
  2. Создаёт чистый инстанс Minecraft 1.21.4 + Fabric в %LOCALAPPDATA%\Laura Client\instance.
  3. Ставит Fabric Loader (через официальный fabric-installer.jar).
  4. Ставит клиент и Fabric API в папку mods.
  5. Скачивает jar игры и ассеты (первый раз, один единственный).
  6. Запускает игру headless (офлайн-режим) без официального лаунчера Mojang.

Параметры:
  -Username <ник>   - никнейм (по умолчанию - имя Windows-пользователя)
  -Reset            - удалить инстанс и настроить заново
  -InstanceRoot <путь> - куда ставить инстанс (по умолчанию %LOCALAPPDATA%\Laura Client)
#>
param(
    [string]$Username = [string]$env:USERNAME,
    [string]$InstanceRoot = (Join-Path $env:LOCALAPPDATA 'Laura Client'),
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # без этого Invoke-WebRequest в PS 5.1 тормозит в разы
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
Add-Type -AssemblyName System.IO.Compression.FileSystem

$GameVersion   = '1.21.4'
$DefaultLoader = '0.18.4'
# Always launch in a window. This prevents Minecraft/LWJGL from changing the
# desktop display mode and restarting Explorer on Windows.
$WindowedWidth  = 854
$WindowedHeight = 480

function Write-Step([string]$msg) { Write-Host "" ; Write-Host "[Laura] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "    $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg)  { Write-Host "    ОШИБКА: $msg" -ForegroundColor Red }

function Get-Json([string]$url) {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60
    return ($r.Content | ConvertFrom-Json)
}

function Has-Prop($obj, [string]$name) {
    return (($obj.PSObject.Properties | Where-Object { $_.Name -eq $name } | Select-Object -First 1) -ne $null)
}

function Force-WindowedOptions([string]$optionsPath) {
    # fullscreen is persisted by Minecraft. Remove stale fullscreen:true
    # before Java starts, otherwise GLFW can change the Windows display mode.
    $original = ''
    if (Test-Path $optionsPath) {
        $original = [System.IO.File]::ReadAllText($optionsPath)
    }
    $newline = if ($original.Contains("`r`n")) { "`r`n" } else { "`n" }
    $hasTrailingNewline = $original -match "`r?`n$"
    if ([string]::IsNullOrEmpty($original)) {
        $lines = @()
    } else {
        $lines = @($original -split "`r?`n")
        if ($hasTrailingNewline -and $lines.Count -gt 0) {
            $lines = @($lines | Select-Object -First ($lines.Count - 1))
        }
    }

    $result = New-Object 'System.Collections.Generic.List[string]'
    $found = $false
    foreach ($line in $lines) {
        if ([string]$line -match '^\s*fullscreen\s*:') {
            if (-not $found) {
                [void]$result.Add('fullscreen:false')
                $found = $true
            }
            continue
        }
        [void]$result.Add([string]$line)
    }
    if (-not $found) { [void]$result.Add('fullscreen:false') }

    $updated = ($result -join $newline) + $newline
    [System.IO.File]::WriteAllText($optionsPath, $updated, (New-Object System.Text.UTF8Encoding($false)))
}

function Save-Url([string]$url, [string]$dest) {
    if (Test-Path $dest) { return }
    $dir = Split-Path -Parent $dest
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $tmp = "$dest.part"
    if (Test-Path $tmp) { Remove-Item $tmp -Force }
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing -TimeoutSec 300
    Move-Item -Force $tmp $dest
}

function Expand-Into([string]$zip, [string]$dest) {
    # Распаковка jar в папку: Expand-Archive в PS 5.1 ругается на расширение .jar,
    # поэтому работаем через .NET напрямую (с перезаписью существующих файлов).
    if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest -Force | Out-Null }
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($zip)
        $entries = $z.Entries
        $z.Dispose()
        foreach ($e in $entries) {
            if ($e.FullName -match '/$') { continue }
            $target = Join-Path $dest $e.FullName
            $tdir = Split-Path -Parent $target
            if (-not (Test-Path $tdir)) { New-Item -ItemType Directory -Path $tdir -Force | Out-Null }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        }
    } catch {
        Write-Warn "Не удалось распаковать $zip : $($_.Exception.Message)"
        throw
    }
}

# ----------------------------------------------------------------------------
# Дедупликация classpath (страховка от «duplicate ASM classes»)
# ----------------------------------------------------------------------------
# Профиль Fabric-лоадера штатно перекрывает org.ow2.asm:9.6 из профиля
# Minecraft 1.21.4 на org.ow2.asm:9.9, поэтому в свежем инстансе конфликта нет.
# Но если профиль остался от старого лаунчера или собран вручную, в classpath
# могут попасть две версии одного артефакта — и игра падает ещё до окна:
#   IllegalStateException: duplicate ASM classes found on classpath
# Ниже оставляем по одной (самой свежей) версии каждого артефакта.
function Get-LibKey([string]$libPath) {
    # 'C:\...\org\ow2\asm\asm\9.6\asm-9.6.jar' -> 'C:/.../org/ow2/asm/asm'
    $seg = ([string]$libPath).Replace('\', '/').Split('/')
    if ($seg.Count -lt 3) { return $null }
    return ($seg[0..($seg.Count - 3)] -join '/')
}

function Get-LibVersion([string]$libPath) {
    $seg = ([string]$libPath).Replace('\', '/').Split('/')
    if ($seg.Count -lt 2) { return '' }
    return $seg[$seg.Count - 2]
}

function Compare-Version([string]$left, [string]$right) {
    # 1 если left > right, -1 если left < right, 0 если равны (как compareVersions в JS-лаунчере)
    $a = @([regex]::Matches(([string]$left), '[0-9A-Za-z]+') | ForEach-Object { $_.Value })
    $b = @([regex]::Matches(([string]$right), '[0-9A-Za-z]+') | ForEach-Object { $_.Value })
    $n = [Math]::Max($a.Count, $b.Count)
    for ($i = 0; $i -lt $n; $i++) {
        $x = $null; $y = $null
        if ($i -lt $a.Count) { $x = $a[$i] }
        if ($i -lt $b.Count) { $y = $b[$i] }
        if ($null -eq $x) { return -1 }
        if ($null -eq $y) { return 1 }
        if ($x -eq $y) { continue }
        $nx = 0; $ny = 0
        if ([int]::TryParse([string]$x, [ref]$nx) -and [int]::TryParse([string]$y, [ref]$ny)) {
            if ($nx -lt $ny) { return -1 }
            if ($nx -gt $ny) { return 1 }
        } else {
            $c = [string]::CompareOrdinal([string]$x, [string]$y)
            if ($c -lt 0) { return -1 }
            if ($c -gt 0) { return 1 }
        }
    }
    return 0
}

# ----------------------------------------------------------------------------
# 0. Инстанс
# ----------------------------------------------------------------------------
$Instance    = Join-Path $InstanceRoot 'instance'
$ModsDir     = Join-Path $Instance 'mods'
$LibsDir     = Join-Path $Instance 'libraries'
$AssetsDir   = Join-Path $Instance 'assets'
$VersionsDir = Join-Path $Instance 'versions'
$ScriptsDir  = Join-Path $InstanceRoot 'scripts'
$NativesDir  = Join-Path $Instance 'natives-windows'

Write-Host "==================================================" -ForegroundColor Magenta
Write-Host "  Laura Client - лаунчер для Windows (MC $GameVersion)" -ForegroundColor Magenta
Write-Host "==================================================" -ForegroundColor Magenta

if ($Reset) {
    Write-Step "Сброс инстанса (-Reset)"
    if (Test-Path $Instance) { Remove-Item $Instance -Recurse -Force }
    Write-Ok "Инстанс удалён"
}

foreach ($d in @($Instance, $ModsDir, $LibsDir, $AssetsDir, $VersionsDir, $ScriptsDir, $NativesDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# Force windowed mode before any possible Minecraft start. This also repairs
# an old options.txt left by a previous fullscreen launch.
Force-WindowedOptions (Join-Path $Instance 'options.txt')

# ----------------------------------------------------------------------------
# 1. Java 21
# ----------------------------------------------------------------------------
Write-Step "Проверяем Java 21"

function Find-Java21 {
    $candidates = New-Object System.Collections.Generic.List[string]
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $candidates.Add($cmd.Source) }
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    $roots = @(
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Microsoft')
    )
    foreach ($r in $roots) {
        if (Test-Path $r) {
            $found = Get-ChildItem $r -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk-2[01]' }
            foreach ($f in $found) { $candidates.Add((Join-Path $f.FullName 'bin\java.exe')) }
        }
    }
    foreach ($c in $candidates) {
        if (-not $c -or -not (Test-Path $c)) { continue }
        try {
            $ver = (& $c -version 2>&1 | Out-String)
            if ($ver -match 'version "21') { return $c }
        } catch {}
    }
    return $null
}

$java = Find-Java21
if (-not $java) {
    Write-Warn "Java 21 не найдена."
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        $ans = Read-Host "    Установить Temurin JDK 21 через winget? [Y/n]"
        if ($ans -ne 'n' -and $ans -ne 'Н') {
            & winget install -e --id EclipseAdoptium.Temurin.21.JDK --accept-source-agreements --accept-package-agreements
            if ($LASTEXITCODE -eq 0) {
                $java = Find-Java21
            }
        }
    }
}
if (-not $java) {
    Write-Err "Java 21 не найдена, поставить её автоматически не удалось."
    Write-Err "Скачайте Temurin JDK 21: https://adoptium.net/temurin/releases/?version=21"
    Write-Err "После установки запустите лаунчер снова."
    exit 1
}
Write-Ok "Java: $java"

# ----------------------------------------------------------------------------
# 2. Fabric Loader
# ----------------------------------------------------------------------------
Write-Step "Устанавливаем Fabric Loader"
$loader = $DefaultLoader
try {
    $list = Get-Json 'https://meta.fabricmc.net/v2/versions/loader'
    if ($list -and $list.Count -gt 0) { $loader = [string]$list[0].loader }
} catch {
    Write-Warn "Не удалось узнать свежую версию лоадера, используем $DefaultLoader"
}

$installerJar = Join-Path $ScriptsDir 'fabric-installer.jar'
$installed = $false
try {
    if (-not (Test-Path $installerJar)) {
        $meta = Get-Json "https://meta.fabricmc.net/v2/versions/installer"
        $installerUrl = [string]$meta[0].version
        Write-Ok "Скачиваем fabric-installer.jar ($installerUrl)"
        Save-Url $installerUrl $installerJar
    }
    & $java -jar $installerJar --installClient $loader $GameVersion $Instance
    if ($LASTEXITCODE -ne 0) { throw "fabric installer завершился с кодом $LASTEXITCODE" }
    $installed = $true
} catch {
    Write-Warn "Автоматическая установка Fabric не удалась: $($_.Exception.Message)"
}

# имя версии fabric-loader-<loader>-1.21.4
$fabricVersionId = "fabric-loader-$loader-$GameVersion"
$fabricVersionDir = Join-Path $VersionsDir $fabricVersionId
$fabricVersionJson = Join-Path $fabricVersionDir "$fabricVersionId.json"
if (-not $installed -and -not (Test-Path $fabricVersionJson)) {
    Write-Err "Версия Fabric не установлена и профиль не найден."
    Write-Err "Проверьте доступ в интернет и запустите лаунчер снова."
    exit 1
}
Write-Ok "Fabric Loader $loader -> $fabricVersionId"

# ----------------------------------------------------------------------------
# 3. Моды: Laura Client + Fabric API
# ----------------------------------------------------------------------------
Write-Step "Ставим моды"
$jarCandidates = @()
$jarCandidates += Get-ChildItem (Join-Path $PSScriptRoot '*.jar') -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'laura-client-*.jar' }
$devLibs = Join-Path $PSScriptRoot '..\build\libs'
$jarCandidates += Get-ChildItem $devLibs -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'laura-client-*.jar' }
$clientJar = $jarCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $clientJar) {
    Write-Err "JAR клиента (laura-client-*.jar) не найден рядом со скриптом и в ..\build\libs."
    Write-Err "Соберите проект: gradlew build  - и положите build\libs\laura-client-<версия>.jar в папку windows\"
    exit 1
}
Copy-Item $clientJar.FullName $ModsDir -Force
Write-Ok "Клиент: $($clientJar.Name)"

if (-not (Get-ChildItem $ModsDir -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue)) {
    try {
        Write-Ok "Скачиваем Fabric API (Modrinth)..."
        # URL собираем через конкатенацию, чтобы PowerShell 5.1 не спотыкался об '&' в одиночных кавычках
        $apiUrl = 'https://api.modrinth.com/v2/project/fabric-api/version' + '?game_versions=%5B%221.21.4%22%5D' + '&' + 'loaders=%5B%22fabric%22%5D'
        $api = Get-Json $apiUrl
        $best = $null
        foreach ($v in $api) {
            if ($v.version_number -match '^0\.119\.4') { $best = $v; break }
        }
        if (-not $best) { $best = $api[0] }
        $file = $best.files | Where-Object { $_.filename -like "fabric-api-*.jar" } | Select-Object -First 1
        if (-not $file) { throw "в ответе Modrinth нет файла" }
        $dest = Join-Path $ModsDir $file.filename
        Save-Url $file.url $dest
        Write-Ok "Fabric API: $($file.filename)"
    } catch {
        Write-Warn "Не удалось скачать Fabric API: $($_.Exception.Message)"
        Write-Warn "Скачайте fabric-api-0.119.4+1.21.4.jar вручную и положите в: $ModsDir"
    }
} else {
    Write-Ok "Fabric API уже на месте"
}

# ----------------------------------------------------------------------------
# 4. Профиль версии, jar игры, ассеты
# ----------------------------------------------------------------------------
Write-Step "Загружаем профиль версии"
if (-not (Test-Path $fabricVersionJson)) {
    Write-Err "Файл профиля $fabricVersionJson не найден."
    exit 1
}
$profile = Get-Content $fabricVersionJson -Raw | ConvertFrom-Json

Write-Step "Скачиваем jar Minecraft $GameVersion (первый раз)"
$clientJarPath = Join-Path $VersionsDir "$GameVersion\$GameVersion.jar"
if (-not (Test-Path $clientJarPath)) {
    $manifest = Get-Json 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
    $entry = $manifest.versions | Where-Object { $_.id -eq $GameVersion } | Select-Object -First 1
    if (-not $entry) { throw "версия $GameVersion не найдена в манифесте Mojang" }
    $vjson = Get-Json ([string]$entry.url)
    $clientUrl = [string]$vjson.downloads.client.url
    Save-Url $clientUrl $clientJarPath
}
Write-Ok "jar игры готов"

Write-Step "Скачиваем ассеты (первый раз, может занять несколько минут)"
$assetIndexUrl = [string]$profile.assetIndex.url
$assetIndexId  = [string]$profile.assetIndex.id
$assetIndex = Get-Json $assetIndexUrl
$objects = $assetIndex.objects.PSObject.Properties
$assetsDirObjects = Join-Path $AssetsDir 'objects'
$total = @($objects).Count
$done = 0
$failed = 0
foreach ($obj in $objects) {
    $hash = [string]$obj.Value.hash
    $dest = Join-Path $assetsDirObjects "$($hash.Substring(0,2))\$hash"
    if (-not (Test-Path $dest)) {
        try {
            $u = "https://resources.download.minecraft.net/$($hash.Substring(0,2))/$hash"
            Save-Url $u $dest
        } catch {
            $failed++
            Write-Warn "Не скачался ассет $hash : $($_.Exception.Message)"
        }
    }
    $done++
    if (($done % 200) -eq 0) { Write-Ok "ассеты: $done / $total" }
}
Write-Ok "ассеты: $done / $total (ошибок: $failed)"
if ($failed -gt 20) {
    Write-Err "Слишком много ассетов не скачалось. Запустите лаунчер ещё раз - недостающее докачается."
    exit 1
}

# ----------------------------------------------------------------------------
# 5. Библиотеки
# ----------------------------------------------------------------------------
Write-Step "Скачиваем библиотеки"
$libraryFiles = New-Object System.Collections.Generic.List[string]
$nativesNeeded = $false
$libCount = @($profile.libraries).Count
$libDone = 0
foreach ($lib in $profile.libraries) {
    $allowed = $true
    if ((Has-Prop $lib 'rules') -and @($lib.rules).Count -gt 0) {
        $allowed = $false
        foreach ($rule in $lib.rules) {
            $osOk = $true
            if (Has-Prop $rule 'os') {
                $osName = [string]$rule.os.name
                if ($osName -ne 'windows') { $osOk = $false }
            }
            $featuresOk = $true
            if (Has-Prop $rule 'features') {
                foreach ($feat in $rule.features.PSObject.Properties) { $featuresOk = $false }
            }
            if ($osOk -and $featuresOk) {
                if ([string]$rule.action -eq 'allow') { $allowed = $true }
                else { $allowed = $false }
            }
        }
    }
    if (-not $allowed) { $libDone++; continue }

    $dl = $null
    if (Has-Prop $lib 'downloads') { $dl = $lib.downloads }
    if (-not $dl) { $libDone++; continue }

    # нативы
    $classifier = $null
    if (Has-Prop $lib 'natives') {
        $n = $lib.natives.PSObject.Properties
        foreach ($prop in $n) {
            if ($prop.Name -eq 'windows') { $classifier = [string]$prop.Value; break }
        }
    }
    if (-not $classifier) { $classifier = 'natives-windows' }
    $ncl = $null
    if (Has-Prop $dl 'classifiers') {
        foreach ($prop in $dl.classifiers.PSObject.Properties) {
            if ($prop.Name -eq $classifier) { $ncl = $prop.Value; break }
        }
    }
    if ($ncl) {
        $npath = [string]$ncl.path
        $nurl  = [string]$ncl.url
        $ndest = Join-Path $LibsDir $npath
        if (-not (Test-Path $ndest)) {
            Save-Url $nurl $ndest
        }
        Expand-Into $ndest $NativesDir
        $nativesNeeded = $true
    }

    # сам артефакт
    if (Has-Prop $dl 'artifact') {
        $art = $dl.artifact
        $path = [string]$art.path
        $url  = [string]$art.url
        $dest = Join-Path $LibsDir $path
        if (-not (Test-Path $dest)) {
            Save-Url $url $dest
        }
        $libraryFiles.Add($dest)
    }
    $libDone++
    if (($libDone % 10) -eq 0) { Write-Ok "библиотеки: $libDone / $libCount" }
}
Write-Ok "библиотеки: $libDone / $libCount"

# Дедупликация classpath: оставляем по одной (самой свежей) версии артефакта.
# Порядок сохраняется; артефакты без распознанного ключа (нетиповой путь) не трогаем.
$classpathMap = @{}
$dedupOrder = New-Object System.Collections.Generic.List[string]
$droppedDupes = 0
foreach ($libFile in $libraryFiles) {
    $libKey = Get-LibKey $libFile
    if (-not $libKey) { $dedupOrder.Add($libFile); continue }
    $libVer = Get-LibVersion $libFile
    if ($classpathMap.ContainsKey($libKey)) {
        $prev = $classpathMap[$libKey]
        if ((Compare-Version $libVer $prev.version) -le 0) { $droppedDupes++; continue }
        [void]$dedupOrder.Remove($prev.dest)  # новая версия вытесняет старую
        $droppedDupes++
    }
    $classpathMap[$libKey] = @{ version = $libVer; dest = $libFile }
    $dedupOrder.Add($libFile)
}
if ($droppedDupes -gt 0) {
    Write-Warn "Убрал дубли библиотек из classpath: $droppedDupes"
}
$libraryFiles = $dedupOrder

# ----------------------------------------------------------------------------
# 6. Собираем аргументы запуска
# ----------------------------------------------------------------------------
Write-Step "Готовим запуск"

function Resolve-Arg([object]$arg) {
    # аргумент - строка либо объект с rules/value
    if ($arg -is [string]) { return ,@($arg) }
    $allowed = $true
    if ((Has-Prop $arg 'rules') -and @($arg.rules).Count -gt 0) {
        $allowed = $false
        foreach ($rule in $arg.rules) {
            $osOk = $true
            if (Has-Prop $rule 'os') {
                if ([string]$rule.os.name -ne 'windows') { $osOk = $false }
            }
            if ($osOk) {
                if ([string]$rule.action -eq 'allow') { $allowed = $true } else { $allowed = $false }
            }
        }
    }
    if (-not $allowed) { return ,@() }
    $val = $arg.value
    if ($val -is [string]) { return ,@($val) }
    return @($val)
}

$jvmRaw = @()
$gameRaw = @()
if (Has-Prop $profile 'arguments') {
    $jvmRaw  = @($profile.arguments.jvm)
    $gameRaw = @($profile.arguments.game)
} elseif (Has-Prop $profile 'minecraftArguments') {
    $gameRaw = @(([string]$profile.minecraftArguments).Split(' '))
}

$jvmArgs = @()
foreach ($a in $jvmRaw) { $jvmArgs += Resolve-Arg $a }
$gameArgs = @()
foreach ($a in $gameRaw) { $gameArgs += Resolve-Arg $a }

if ($gameArgs.Count -eq 0) {
    # резервный набор аргументов
    $gameArgs = @(
        '--username', 'Player', '--version', $fabricVersionId,
        '--gameDir', $Instance, '--assetsDir', $AssetsDir,
        '--assetIndex', $assetIndexId, '--userProperties', '{}',
        '--clientId', '0', '--versionType', 'release', '--userType', 'mojang'
    )
}

# Strip all profile window flags. A stale --fullscreen or duplicate size
# can otherwise override the safe options.txt value on startup.
$windowedGameArgs = New-Object 'System.Collections.Generic.List[object]'
for ($i = 0; $i -lt $gameArgs.Count; $i++) {
    $arg = [string]$gameArgs[$i]
    if ($arg -eq '--fullscreen') { continue }
    if ($arg -eq '--width' -or $arg -eq '--height') {
        if (($i + 1) -lt $gameArgs.Count) { $i++ }
        continue
    }
    [void]$windowedGameArgs.Add($gameArgs[$i])
}
$gameArgs = @($windowedGameArgs.ToArray())
$gameArgs += @('--width', [string]$WindowedWidth, '--height', [string]$WindowedHeight)

# класспуть: библиотеки + jar игры
$libraryFiles.Add($clientJarPath)
$classpath = ($libraryFiles -join ';')

$uuid = [System.Guid]::NewGuid().ToString()
$accessToken = -join ((1..32) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
$deviceToken = -join ((1..16) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })

$name = ([string]$Username) -replace '[^A-Za-z0-9_]', ''
if ([string]::IsNullOrWhiteSpace($name)) { $name = 'Player' }
if ($name.Length -gt 16) { $name = $name.Substring(0, 16) }

$replacements = @{
    '${auth_player_name}'   = $name
    '${version_name}'       = $fabricVersionId
    '${game_directory}'     = $Instance
    '${assets_root}'        = $AssetsDir
    '${assets_index_name}'  = $assetIndexId
    '${auth_uuid}'          = $uuid
    '${auth_access_token}'  = $accessToken
    '${auth_session}'       = 'token:' + $accessToken + ':' + $uuid
    '${user_type}'          = 'mojang'
    '${version_type}'       = 'release'
    '${user_properties}'    = '{}'
    '${clientid}'           = '0'
    '${auth_xuid}'          = ''
    '${auth_device_id}'     = $deviceToken
    '${natives_directory}'  = $NativesDir
    '${launcher_name}'      = 'LauraLauncher'
    '${launcher_version}'   = '1.0'
    '${classpath}'          = $classpath
    '${classpath_separator}' = ';'
}

foreach ($k in @($replacements.Keys)) {
    $val = [string]$replacements[$k]
    $jvmArgs  = @($jvmArgs  | ForEach-Object { ([string]$_).Replace($k, $val) })
    $gameArgs = @($gameArgs | ForEach-Object { ([string]$_).Replace($k, $val) })
}

$mainClass = [string]$profile.mainClass
if ([string]::IsNullOrWhiteSpace($mainClass)) { $mainClass = 'net.fabricmc.loader.impl.launch.knot.KnotClient' }

# ----------------------------------------------------------------------------
# 7. Запуск
# ----------------------------------------------------------------------------
Write-Step "Запускаем Minecraft ($name)"
Write-Host "    Инстанс: $Instance" -ForegroundColor DarkGray
Write-Host ""

$all = @('-cp', $classpath) + $jvmArgs + @($mainClass) + $gameArgs
Push-Location $Instance
try {
    & $java @all
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host ""
if ($code -eq 0) {
    Write-Host "Игра завершена. Папку инстанса можно найти здесь: $Instance" -ForegroundColor Green
} else {
    Write-Warn "Игра завершилась с кодом $code."
    if ($code -eq 1) {
        Write-Warn "Частая причина: нужен официальный аккаунт Minecraft (офлайн-режим работает в одиночной игре)."
        Write-Warn "Для онлайн-серверов установите игру через официальный лаунчер, Fabric - через installer, а потом просто скопируйте содержимое папки mods из инстанса."
    }
}
Read-Host "Нажмите Enter для выхода" | Out-Null
