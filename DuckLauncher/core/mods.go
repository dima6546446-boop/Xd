package core

import (
	"os"
	"path/filepath"
	"strings"
)

// DuckVisualJar is the mod embedded into the launcher binary (set by main via go:embed).
var DuckVisualJar []byte

// EnsureMods installs Fabric API, Duck Visual (embedded) and optionally Sodium into the mods folder.
// Old versions of these managed mods are removed; any other mods the player added are left alone.
func EnsureMods(sodium bool, progress Progress) error {
	if err := os.MkdirAll(ModsDir(), 0o755); err != nil {
		return err
	}
	fabricAPI := "fabric-api-" + FabricAPIVersion + ".jar"
	sodiumJar := "sodium-" + SodiumVersion + ".jar"
	duck := "duck-visual.jar"

	entries, _ := os.ReadDir(ModsDir())
	for _, e := range entries {
		n := e.Name()
		managed := (strings.HasPrefix(n, "fabric-api-") && n != fabricAPI) ||
			(strings.HasPrefix(n, "sodium-") && (n != sodiumJar || !sodium)) ||
			(strings.HasPrefix(n, "duck-visual") && n != duck)
		if managed {
			os.Remove(filepath.Join(ModsDir(), n))
		}
	}

	tasks := []Task{{
		URL:  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/" + FabricAPIVersion + "/fabric-api-" + FabricAPIVersion + ".jar",
		Path: filepath.Join(ModsDir(), fabricAPI),
	}}
	if sodium {
		tasks = append(tasks, Task{
			URL:  "https://api.modrinth.com/maven/maven/modrinth/sodium/" + SodiumVersion + "/sodium-" + SodiumVersion + ".jar",
			Path: filepath.Join(ModsDir(), sodiumJar),
		})
	}
	if err := DownloadAll("mods", tasks, 2, progress); err != nil {
		return err
	}
	// always refresh the embedded mod so launcher updates ship new visuals
	if len(DuckVisualJar) > 0 {
		path := filepath.Join(ModsDir(), duck)
		if old, err := os.ReadFile(path); err != nil || string(old) != string(DuckVisualJar) {
			if err := os.WriteFile(path, DuckVisualJar, 0o644); err != nil {
				return err
			}
		}
	}
	return nil
}
