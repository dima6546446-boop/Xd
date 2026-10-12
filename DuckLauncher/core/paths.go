package core

import (
	"os"
	"path/filepath"
	"runtime"
)

// Fixed versions this launcher installs.
const (
	MinecraftVersion = "1.21.4"
	FabricAPIVersion = "0.119.4+1.21.4"
	SodiumVersion    = "mc1.21.4-0.6.13-fabric"
	LauncherName     = "DuckLauncher"
	LauncherVersion  = "1.0.0"
)

// BaseDir is where everything lives: %APPDATA%\.ducklauncher on Windows, ~/.ducklauncher elsewhere.
func BaseDir() string {
	if runtime.GOOS == "windows" {
		if appData := os.Getenv("APPDATA"); appData != "" {
			return filepath.Join(appData, ".ducklauncher")
		}
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".ducklauncher")
}

func GameDir() string      { return filepath.Join(BaseDir(), "minecraft") }
func LibrariesDir() string { return filepath.Join(BaseDir(), "libraries") }
func AssetsDir() string    { return filepath.Join(BaseDir(), "assets") }
func VersionsDir() string  { return filepath.Join(BaseDir(), "versions") }
func RuntimeDir() string   { return filepath.Join(BaseDir(), "runtime") }
func ModsDir() string      { return filepath.Join(GameDir(), "mods") }
func LogsDir() string      { return filepath.Join(BaseDir(), "logs") }
