# Генерирует уникальный секрет пересылки Velocity -> Paper при первом запуске сборки.
# Пишет его в velocity\forwarding.secret и config\paper-global.yml (proxies.velocity.secret).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$secret = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $root 'velocity\forwarding.secret'), $secret, $utf8)
$cfg = Join-Path $root 'config\paper-global.yml'
$text = [System.IO.File]::ReadAllText($cfg, $utf8)
$text = [regex]::Replace($text, "(?m)^(    secret: ).*$", "`${1}'$secret'")
[System.IO.File]::WriteAllText($cfg, $text, $utf8)
[System.IO.File]::WriteAllText((Join-Path $root 'velocity\.secret-generated'), 'ok', $utf8)
Write-Host ' Создан уникальный секрет пересылки Velocity.'
