@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem Резервная копия миров и настроек в папку backups\ (ZIP).
rem Лучше запускать, когда сервер остановлен (или после команды save-all в консоли сервера).
cd /d "%~dp0"
if not exist backups mkdir backups
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm"') do set "TS=%%t"
if not defined TS set "TS=backup"
set "OUT=backups\MineRus_%TS%.zip"
echo Создаю резервную копию %OUT% ...
tar -a -c -f "%OUT%" world world_nether world_the_end plugins server.properties bukkit.yml spigot.yml commands.yml config
if errorlevel 1 (
    echo [ОШИБКА] Не удалось создать архив. Нужна Windows 10 1803 или новее ^(встроенная утилита tar^).
) else (
    echo Готово: %OUT%
)
pause
