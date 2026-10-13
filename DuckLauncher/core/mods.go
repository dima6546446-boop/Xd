package core

import (
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

// DuckVisualJar is the mod embedded into the launcher binary (set by main via go:embed).
var DuckVisualJar []byte

// OptionalMod is a performance mod the player can toggle in the settings.
type OptionalMod struct {
	Slug  string `json:"slug"`
	Name  string `json:"name"`
	About string `json:"about"`
}

// PerfMods are Modrinth projects that make the game run better on weak PCs.
var PerfMods = []OptionalMod{
	{"sodium", "Sodium", "Новый движок рендера: обычно в 2–3 раза больше FPS"},
	{"lithium", "Lithium", "Оптимизирует физику, ИИ мобов и тики мира"},
	{"ferrite-core", "FerriteCore", "Сильно уменьшает потребление оперативной памяти"},
	{"immediatelyfast", "ImmediatelyFast", "Ускоряет отрисовку HUD, текста и предметов"},
	{"entityculling", "EntityCulling", "Не рисует мобов и сундуки, которые не видно"},
}

type modrinthVersion struct {
	VersionNumber string `json:"version_number"`
	Files         []struct {
		URL      string `json:"url"`
		Filename string `json:"filename"`
		Primary  bool   `json:"primary"`
		Size     int64  `json:"size"`
		Hashes   struct {
			Sha1 string `json:"sha1"`
		} `json:"hashes"`
	} `json:"files"`
}

// modrinthFile finds the newest Fabric build of a project for a game version.
func modrinthFile(slug, gameVersion string) (*Task, error) {
	q := url.Values{}
	q.Set("game_versions", `["`+gameVersion+`"]`)
	q.Set("loaders", `["fabric"]`)
	var versions []modrinthVersion
	if err := getJSON("https://api.modrinth.com/v2/project/"+slug+"/version?"+q.Encode(), &versions); err != nil {
		return nil, err
	}
	if len(versions) == 0 || len(versions[0].Files) == 0 {
		return nil, nil // not available for this version
	}
	f := versions[0].Files[0]
	for _, x := range versions[0].Files {
		if x.Primary {
			f = x
		}
	}
	return &Task{URL: f.URL, Sha1: f.Hashes.Sha1, Size: f.Size, Path: f.Filename}, nil
}

// managed remembers which jars the launcher put into a mods folder, so it can update or
// remove them without touching mods the player added by hand.
type managed struct {
	Files map[string]string `json:"files"` // slug -> file name
}

// EnsureMods installs Fabric API, the chosen performance mods and (on 1.21.4) Duck Visual.
func EnsureMods(version, gameDir string, enabled map[string]bool, progress Progress) error {
	mods := filepath.Join(gameDir, "mods")
	if err := os.MkdirAll(mods, 0o755); err != nil {
		return err
	}
	manifestPath := filepath.Join(mods, ".duck-managed.json")
	var m managed
	readJSON(manifestPath, &m)
	if m.Files == nil {
		m.Files = map[string]string{}
	}

	cachePath := filepath.Join(VersionsDir(), version, "mods-resolved.json")
	var cache map[string]*Task
	readJSON(cachePath, &cache)
	if cache == nil {
		cache = map[string]*Task{}
	}

	want := map[string]bool{"fabric-api": true}
	for _, pm := range PerfMods {
		if enabled[pm.Slug] {
			want[pm.Slug] = true
		}
	}
	var tasks []Task
	next := map[string]string{}
	for slug := range want {
		t, ok := cache[slug]
		if !ok {
			if progress != nil {
				progress("mods-resolve", 0, 1)
			}
			var err error
			t, err = modrinthFile(slug, version)
			if err != nil {
				if slug == "fabric-api" {
					return fmt.Errorf("Fabric API: %w", err)
				}
				continue // optional mod: skip on network errors
			}
			cache[slug] = t
		}
		if t == nil {
			continue
		}
		next[slug] = t.Path
		tasks = append(tasks, Task{URL: t.URL, Sha1: t.Sha1, Size: t.Size, Path: filepath.Join(mods, t.Path)})
	}
	os.MkdirAll(filepath.Dir(cachePath), 0o755)
	if b, err := json.Marshal(cache); err == nil {
		os.WriteFile(cachePath, b, 0o644)
	}

	// remove managed jars that are disabled or replaced by a newer file
	for slug, file := range m.Files {
		if next[slug] != file && !strings.Contains(file, "/") && !strings.Contains(file, "\\") {
			os.Remove(filepath.Join(mods, file))
		}
	}
	if err := DownloadAll("mods", tasks, 4, progress); err != nil {
		return err
	}

	// Duck Visual only exists for 1.21.4; refresh it every launch so launcher updates ship it
	duck := filepath.Join(mods, "duck-visual.jar")
	if version == DuckVersion && len(DuckVisualJar) > 0 {
		if old, err := os.ReadFile(duck); err != nil || string(old) != string(DuckVisualJar) {
			if err := os.WriteFile(duck, DuckVisualJar, 0o644); err != nil {
				return err
			}
		}
		next["duck-visual"] = "duck-visual.jar"
	}
	// clean up the jars of launcher 1.0 (fixed names)
	entries, _ := os.ReadDir(mods)
	for _, e := range entries {
		n := e.Name()
		if (strings.HasPrefix(n, "fabric-api-0.119.4") || strings.HasPrefix(n, "sodium-mc1.21.4-0.6.13")) && !isValue(next, n) {
			os.Remove(filepath.Join(mods, n))
		}
	}
	m.Files = next
	b, _ := json.MarshalIndent(m, "", "  ")
	return os.WriteFile(manifestPath, b, 0o644)
}

func isValue(m map[string]string, v string) bool {
	for _, x := range m {
		if x == v {
			return true
		}
	}
	return false
}
