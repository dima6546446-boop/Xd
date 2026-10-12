#!/usr/bin/env bash
# ClinderGrief — запуск сервера Paper 1.21.11 (Linux/macOS). Требуется Java 21+.
MIN_RAM="${MIN_RAM:-2G}"
MAX_RAM="${MAX_RAM:-4G}"
SERVER_JAR="paper-1.21.11-132.jar"
JAVA_REQUIRED=21

cd "$(dirname "$0")" || exit 1
# Облегчённая версия архива: докачиваем ядро и плагины с официальных сайтов со сверкой SHA-256
if [ -f scripts/files.manifest ]; then
  grep -vE '^\s*(#|$)' scripts/files.manifest | while IFS=$'\t' read -r path url sha; do
    if [ -f "$path" ] && [ "$(sha256sum "$path" | cut -d' ' -f1)" = "$sha" ]; then continue; fi
    echo "Скачиваю $path ..."
    mkdir -p "$(dirname "$path")"
    curl -fsSL -o "$path.part" "$url" && [ "$(sha256sum "$path.part" | cut -d' ' -f1)" = "$sha" ] \
      && mv "$path.part" "$path" || { rm -f "$path.part"; echo "[ОШИБКА] не удалось скачать $path"; exit 1; }
  done || exit 1
fi
[ -f "$SERVER_JAR" ] || { echo "[ОШИБКА] Не найден $SERVER_JAR"; exit 1; }

if [ -x runtime/bin/java ]; then JAVA=runtime/bin/java
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"
else JAVA=java; fi

JVER=$("$JAVA" -version 2>&1 | grep -i ' version ' | head -1 | awk -F '"' '{print $2}')
JMAJOR=${JVER%%.*}; [ "$JMAJOR" = "1" ] && JMAJOR=$(echo "$JVER" | cut -d. -f2)
if [ -z "$JMAJOR" ] || [ "$JMAJOR" -lt "$JAVA_REQUIRED" ] 2>/dev/null; then
  echo "[ОШИБКА] Нужна Java $JAVA_REQUIRED+, найдена: ${JVER:-нет}. Установите Temurin 21: https://adoptium.net/"
  exit 1
fi
echo "Java: $JVER ($JAVA)"

if ! grep -qi '^eula=true' eula.txt 2>/dev/null; then
  echo "Для запуска нужно принять EULA Minecraft: https://aka.ms/MinecraftEULA"
  read -r -p "Вы прочитали и принимаете EULA? [y/N] " ans
  case "$ans" in
    y|Y|д|Д) printf '# Принято владельцем сервера через start.sh: https://aka.ms/MinecraftEULA\neula=true\n' > eula.txt ;;
    *) echo "Без принятия EULA сервер запустить нельзя."; exit 1 ;;
  esac
fi

exec "$JAVA" -Xms"$MIN_RAM" -Xmx"$MAX_RAM" -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled \
  -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 \
  -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 \
  -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -jar "$SERVER_JAR" --nogui
