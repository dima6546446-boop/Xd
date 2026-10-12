#!/bin/sh
# Builds "Duck Launcher.exe" (Windows) and a Linux CLI build into dist/.
# Requires Go 1.24+. Run from the DuckLauncher folder.
set -e
cd "$(dirname "$0")"

# 1. the mod jar that gets embedded into the launcher
if [ -f ../DuckVisual/build/libs/duck-visual-1.0.0.jar ]; then
  cp ../DuckVisual/build/libs/duck-visual-1.0.0.jar embed/duck-visual.jar
elif [ -f ../DuckVisual/release/duck-visual-1.0.0.jar ]; then
  cp ../DuckVisual/release/duck-visual-1.0.0.jar embed/duck-visual.jar
fi

# 2. Windows resources (icon, manifest, version info)
go run github.com/tc-hib/go-winres@v0.3.3 make --in winres/winres.json --out rsrc

# 3. binaries
mkdir -p dist
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-H windowsgui -s -w" -o "dist/Duck Launcher.exe" .
CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o dist/ducklauncher-linux .
echo "built: dist/Duck Launcher.exe"
