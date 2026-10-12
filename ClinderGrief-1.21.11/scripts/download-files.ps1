# Докачивает недостающие файлы сборки (ядро Paper и плагины) с официальных адресов
# из scripts\files.manifest и сверяет SHA-256. Нужен для облегчённой версии архива.
param([Parameter(Mandatory = $true)][string]$Root)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}

$manifest = Join-Path $Root 'scripts/files.manifest'
$failed = 0
foreach ($line in Get-Content -Path $manifest -Encoding UTF8) {
    if ($line -match '^\s*(#|$)') { continue }
    $path, $url, $sha = $line.Split("`t")
    $target = Join-Path $Root $path
    if ((Test-Path $target) -and ((Get-FileHash -Path $target -Algorithm SHA256).Hash.ToLower() -eq $sha)) { continue }
    $dir = Split-Path -Parent $target
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $part = "$target.part"
    Write-Host "Скачиваю $path ..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $part -UseBasicParsing
        $got = (Get-FileHash -Path $part -Algorithm SHA256).Hash.ToLower()
        if ($got -ne $sha) { throw "контрольная сумма не совпала (ожидалось $sha, получено $got)" }
        Move-Item -Path $part -Destination $target -Force
    } catch {
        Write-Host "  [ОШИБКА] $path : $($_.Exception.Message)"
        if (Test-Path $part) { Remove-Item -Force $part }
        $failed++
    }
}
if ($failed -gt 0) { Write-Host "Не удалось скачать файлов: $failed"; exit 1 }
Write-Host 'Все файлы сборки на месте, контрольные суммы совпали.'
exit 0
