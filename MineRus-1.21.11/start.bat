@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion
rem ============================================================
rem  MineRus — запуск сервера Paper 1.21.11 (Windows)
rem  Двойной щелчок по этому файлу. Требуется Java 21 или новее (64-bit).
rem ============================================================

rem ---- Настройки (можно менять) --------------------------------
rem Оперативная память для сервера. 4G подходит для 10-20 игроков.
set "MIN_RAM=2G"
set "MAX_RAM=4G"
set "SERVER_JAR=paper-1.21.11-132.jar"
set "JAVA_REQUIRED=21"
rem --------------------------------------------------------------

cd /d "%~dp0"
title MineRus - Paper 1.21.11

rem Облегчённая версия архива: ядро и плагины скачиваются с официальных сайтов (SHA-256 сверяется)
if not exist "%SERVER_JAR%" if exist "scripts\files.manifest" (
    echo  Скачиваю ядро Paper и плагины с официальных сайтов ^(около 110 МБ^)...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\download-files.ps1" -Root "%CD%"
    if errorlevel 1 (
        echo [ОШИБКА] Не все файлы удалось скачать. Проверьте интернет и запустите start.bat снова.
        goto :fail
    )
)
if not exist "%SERVER_JAR%" (
    echo [ОШИБКА] Не найден файл %SERVER_JAR% в папке:
    echo          %CD%
    echo Распакуйте архив сборки полностью и запускайте start.bat из папки сервера.
    goto :fail
)

rem ---- 1. Поиск подходящей Java ----------------------------------
set "JAVA="
if exist "runtime\bin\java.exe" (
    set "JAVA=%CD%\runtime\bin\java.exe"
    call :checkjava
    if defined JAVA_OK goto :java_found
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
    call :checkjava
    if defined JAVA_OK goto :java_found
)
where java >nul 2>nul
if not errorlevel 1 (
    set "JAVA=java"
    call :checkjava
    if defined JAVA_OK goto :java_found
)

echo.
echo  Не найдена Java %JAVA_REQUIRED%+ (64-bit), необходимая для Paper 1.21.11.
if defined JVER echo  Найдена неподходящая версия: %JVER% %JBITS%
echo.
echo  Скрипт может автоматически скачать Eclipse Temurin JRE %JAVA_REQUIRED% (около 50 МБ)
echo  с официального сайта adoptium.net в папку "runtime" этой сборки.
echo  Система при этом не изменяется.
echo.
choice /c YN /m "Скачать Java сейчас"
if errorlevel 2 (
    echo.
    echo Установите Java %JAVA_REQUIRED%+ вручную: https://adoptium.net/temurin/releases/?version=21
    echo и снова запустите start.bat.
    goto :fail
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\install-java.ps1" -Major %JAVA_REQUIRED% -Target "%CD%\runtime"
if errorlevel 1 (
    echo [ОШИБКА] Не удалось скачать Java. Установите её вручную: https://adoptium.net/temurin/releases/?version=21
    goto :fail
)
set "JAVA=%CD%\runtime\bin\java.exe"
call :checkjava
if not defined JAVA_OK (
    echo [ОШИБКА] Скачанная Java не запускается. Установите Java %JAVA_REQUIRED%+ вручную.
    goto :fail
)

:java_found
echo  Java: %JVER% (%JAVA%)

rem ---- 2. Лицензионное соглашение Minecraft (EULA) --------------
findstr /i /r /c:"^eula=true" eula.txt >nul 2>nul
if errorlevel 1 (
    echo.
    echo  ==============================================================
    echo   Для запуска сервера владелец должен принять EULA Minecraft:
    echo   https://aka.ms/MinecraftEULA
    echo   ^(Лицензионное соглашение Mojang/Microsoft^)
    echo  ==============================================================
    choice /c YN /m "Вы прочитали и принимаете EULA Minecraft"
    if errorlevel 2 (
        echo Без принятия EULA сервер запустить нельзя.
        goto :fail
    )
    > eula.txt echo # Принято владельцем сервера через start.bat: https://aka.ms/MinecraftEULA
    >> eula.txt echo eula=true
)

rem ---- 3. Запуск ------------------------------------------------
:run
echo.
echo  Запуск сервера... Для остановки введите в этом окне: stop
echo.
"%JAVA%" -Xms%MIN_RAM% -Xmx%MAX_RAM% -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%SERVER_JAR%" --nogui
echo.
echo  Сервер остановлен (код выхода %ERRORLEVEL%).
choice /c RQ /m "R - перезапустить сервер, Q - выйти"
if errorlevel 2 goto :eof
goto :run

rem ---- Проверка версии Java ---------------------------------------
:checkjava
set "JAVA_OK="
set "JVER="
set "JBITS="
set "JTMP=%TEMP%\minerus_java_version.txt"
"%JAVA%" -version > "%JTMP%" 2>&1
if errorlevel 1 goto :eof
for /f "usebackq tokens=3" %%v in (`findstr /i /c:" version " "%JTMP%"`) do if not defined JVER set "JVER=%%~v"
findstr /c:"64-Bit" "%JTMP%" >nul 2>nul
if errorlevel 1 (set "JBITS=32-bit") else (set "JBITS=64-bit")
del "%JTMP%" >nul 2>nul
if not defined JVER goto :eof
set "JMAJOR="
for /f "tokens=1,2 delims=.-+_" %%a in ("%JVER%") do (
    if "%%a"=="1" (set "JMAJOR=%%b") else (set "JMAJOR=%%a")
)
if not defined JMAJOR goto :eof
if not "%JBITS%"=="64-bit" goto :eof
if %JMAJOR% GEQ %JAVA_REQUIRED% set "JAVA_OK=1"
goto :eof

:fail
echo.
pause
exit /b 1
