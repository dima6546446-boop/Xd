#!/usr/bin/env bash
# Сборка ClinderCore без Gradle: компилирует против библиотек Paper из папки сервера
# (появляются после первого запуска сервера в <сервер>/libraries).
# Использование: ./build.sh <папка сервера>   → build/ClinderCore-<версия>.jar
set -e
cd "$(dirname "$0")"
S="${1:?папка сервера}"
VERSION=$(grep "^version:" src/main/resources/plugin.yml | tr -d "' " | cut -d: -f2)
CP=$(find "$S/libraries" -name "*.jar" | tr '\n' ':')$(ls "$S"/plugins/Vault*.jar):$(ls "$S"/plugins/PlaceholderAPI-*.jar)
rm -rf build && mkdir -p build/classes
javac -encoding UTF-8 --release 21 -proc:none -Xlint:deprecation -cp "$CP" -d build/classes $(find src/main/java -name "*.java")
cp src/main/resources/* build/classes/
(cd build/classes && jar --create --file "../ClinderCore-$VERSION.jar" .)
echo "build/ClinderCore-$VERSION.jar"
