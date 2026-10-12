# Скачивает Eclipse Temurin JRE (Adoptium) в папку сборки и проверяет SHA-256.
# Вызывается из start.bat, когда подходящая Java не найдена. Систему не изменяет.
param(
    [int]$Major = 21,
    [Parameter(Mandatory = $true)][string]$Target
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}

switch ($env:PROCESSOR_ARCHITECTURE) {
    'AMD64' { $arch = 'x64' }
    'ARM64' { $arch = 'aarch64' }
    default { Write-Host "Неподдерживаемая архитектура: $($env:PROCESSOR_ARCHITECTURE). Нужна 64-битная Windows."; exit 1 }
}

$api = "https://api.adoptium.net/v3/assets/latest/$Major/hotspot?architecture=$arch&image_type=jre&os=windows&vendor=eclipse"
Write-Host "Получаю информацию о Temurin JRE $Major ($arch) с api.adoptium.net ..."
$assets = Invoke-RestMethod -Uri $api -UseBasicParsing
$pkg = ($assets | Where-Object { $_.binary.package.name -like '*.zip' } | Select-Object -First 1).binary.package
if (-not $pkg) { Write-Host 'Не найден подходящий архив JRE.'; exit 1 }

# Временная папка рядом с целевой (тот же диск — иначе Move-Item для папок не работает)
$tmp = Join-Path (Split-Path -Parent $Target) 'runtime-download.tmp'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Path $tmp | Out-Null
$zip = Join-Path $tmp $pkg.name
Write-Host "Скачиваю $($pkg.name) ($([math]::Round($pkg.size / 1MB)) МБ) ..."
Invoke-WebRequest -Uri $pkg.link -OutFile $zip -UseBasicParsing

$hash = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower()
if ($hash -ne $pkg.checksum.ToLower()) {
    Write-Host "Контрольная сумма не совпала! Ожидалось $($pkg.checksum), получено $hash. Файл удалён."
    Remove-Item -Recurse -Force $tmp
    exit 1
}
Write-Host 'Контрольная сумма SHA-256 совпала. Распаковываю ...'
Expand-Archive -Path $zip -DestinationPath $tmp -Force
$inner = Get-ChildItem -Path $tmp -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (-not $inner) { Write-Host 'В архиве не найден bin\java.exe.'; exit 1 }

if (Test-Path $Target) { Remove-Item -Recurse -Force $Target }
Move-Item -Path $inner.FullName -Destination $Target
Remove-Item -Recurse -Force $tmp
Write-Host "Java установлена в $Target"
exit 0
