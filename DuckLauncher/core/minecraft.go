package core

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

type Rule struct {
	Action   string                       `json:"action"`
	OS       *struct{ Name, Arch string } `json:"os"`
	Features map[string]bool              `json:"features"`
}

type Artifact struct {
	Path string `json:"path"`
	Sha1 string `json:"sha1"`
	Size int64  `json:"size"`
	URL  string `json:"url"`
}

type Library struct {
	Name      string `json:"name"`
	URL       string `json:"url"` // fabric style: maven repo root
	Sha1      string `json:"sha1"`
	Size      int64  `json:"size"`
	Downloads *struct {
		Artifact *Artifact `json:"artifact"`
	} `json:"downloads"`
	Rules []Rule `json:"rules"`
}

// Arg is either a plain string or {rules, value} in version json "arguments".
type Arg struct {
	Values []string
	Rules  []Rule
}

func (a *Arg) UnmarshalJSON(b []byte) error {
	var s string
	if json.Unmarshal(b, &s) == nil {
		a.Values = []string{s}
		return nil
	}
	var obj struct {
		Rules []Rule          `json:"rules"`
		Value json.RawMessage `json:"value"`
	}
	if err := json.Unmarshal(b, &obj); err != nil {
		return err
	}
	a.Rules = obj.Rules
	if json.Unmarshal(obj.Value, &s) == nil {
		a.Values = []string{s}
		return nil
	}
	return json.Unmarshal(obj.Value, &a.Values)
}

type VersionJSON struct {
	ID        string `json:"id"`
	MainClass string `json:"mainClass"`
	Type      string `json:"type"`
	Arguments struct {
		Game []Arg `json:"game"`
		JVM  []Arg `json:"jvm"`
	} `json:"arguments"`
	AssetIndex struct {
		ID   string `json:"id"`
		Sha1 string `json:"sha1"`
		Size int64  `json:"size"`
		URL  string `json:"url"`
	} `json:"assetIndex"`
	Downloads struct {
		Client Artifact `json:"client"`
	} `json:"downloads"`
	Libraries []Library `json:"libraries"`
	Logging   struct {
		Client struct {
			Argument string `json:"argument"`
			File     struct {
				ID   string `json:"id"`
				Sha1 string `json:"sha1"`
				Size int64  `json:"size"`
				URL  string `json:"url"`
			} `json:"file"`
		} `json:"client"`
	} `json:"logging"`
}

func mojangOS() string {
	switch runtime.GOOS {
	case "windows":
		return "windows"
	case "darwin":
		return "osx"
	}
	return "linux"
}

// RulesAllow evaluates Mojang library/argument rules. Rules with feature flags never match (no demo, no quick play...).
func RulesAllow(rules []Rule) bool {
	if len(rules) == 0 {
		return true
	}
	allowed := false
	for _, r := range rules {
		match := true
		if r.OS != nil {
			if r.OS.Name != "" && r.OS.Name != mojangOS() {
				match = false
			}
			if r.OS.Arch == "x86" && runtime.GOARCH != "386" {
				match = false
			}
		}
		if len(r.Features) > 0 {
			match = false
		}
		if match {
			allowed = r.Action == "allow"
		}
	}
	return allowed
}

// nativeForOtherArch filters natives built for another CPU (e.g. natives-windows-arm64 / -x86 on x64).
func nativeForOtherArch(name string) bool {
	parts := strings.Split(name, ":")
	if len(parts) < 4 {
		return false
	}
	c := parts[3]
	isArm := strings.Contains(c, "arm64") || strings.Contains(c, "aarch")
	isX86 := strings.HasSuffix(c, "-x86")
	isNative := strings.Contains(c, "natives") || strings.Contains(c, "linux-")
	switch runtime.GOARCH {
	case "arm64":
		return isNative && !isArm
	case "386":
		return isArm
	}
	return isArm || isX86
}

// MavenPath turns "group:artifact:version[:classifier]" into a repository path.
func MavenPath(name string) string {
	p := strings.Split(name, ":")
	if len(p) < 3 {
		return name
	}
	group := strings.ReplaceAll(p[0], ".", "/")
	file := p[1] + "-" + p[2]
	if len(p) > 3 {
		file += "-" + p[3]
	}
	return group + "/" + p[1] + "/" + p[2] + "/" + file + ".jar"
}

// Install prepares vanilla + Fabric and returns the merged launch description.
type Installed struct {
	Vanilla   *VersionJSON
	Fabric    *VersionJSON
	Classpath []string
	Natives   string
	LogConfig string
}

func Install(progress Progress) (*Installed, error) {
	// --- vanilla version json
	var manifest struct {
		Versions []struct{ ID, URL string } `json:"versions"`
	}
	vdir := filepath.Join(VersionsDir(), MinecraftVersion)
	vjsonPath := filepath.Join(vdir, MinecraftVersion+".json")
	var v VersionJSON
	if b, err := os.ReadFile(vjsonPath); err == nil && json.Unmarshal(b, &v) == nil && v.ID != "" {
		// cached
	} else {
		if progress != nil {
			progress("meta", 0, 1)
		}
		if err := getJSON("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", &manifest); err != nil {
			return nil, fmt.Errorf("version manifest: %w", err)
		}
		url := ""
		for _, m := range manifest.Versions {
			if m.ID == MinecraftVersion {
				url = m.URL
			}
		}
		if url == "" {
			return nil, fmt.Errorf("version %s not found", MinecraftVersion)
		}
		if err := DownloadAll("meta", []Task{{URL: url, Path: vjsonPath}}, 1, nil); err != nil {
			return nil, err
		}
		b, _ := os.ReadFile(vjsonPath)
		if err := json.Unmarshal(b, &v); err != nil {
			return nil, err
		}
	}

	// --- fabric profile
	fabricPath := filepath.Join(vdir, "fabric.json")
	var fab VersionJSON
	if b, err := os.ReadFile(fabricPath); err == nil && json.Unmarshal(b, &fab) == nil && fab.MainClass != "" {
		// cached
	} else {
		var loaders []struct {
			Loader struct {
				Version string `json:"version"`
				Stable  bool   `json:"stable"`
			} `json:"loader"`
		}
		if err := getJSON("https://meta.fabricmc.net/v2/versions/loader/"+MinecraftVersion, &loaders); err != nil || len(loaders) == 0 {
			return nil, fmt.Errorf("fabric loader list: %v", err)
		}
		loader := loaders[0].Loader.Version
		for _, l := range loaders {
			if l.Loader.Stable {
				loader = l.Loader.Version
				break
			}
		}
		url := fmt.Sprintf("https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json", MinecraftVersion, loader)
		if err := DownloadAll("meta", []Task{{URL: url, Path: fabricPath}}, 1, nil); err != nil {
			return nil, err
		}
		b, _ := os.ReadFile(fabricPath)
		if err := json.Unmarshal(b, &fab); err != nil {
			return nil, err
		}
	}

	// --- libraries (fabric first, vanilla second; skip vanilla duplicates of fabric artifacts)
	var tasks []Task
	var classpath []string
	seen := map[string]bool{}
	key := func(name string) string {
		p := strings.Split(name, ":")
		if len(p) >= 4 {
			return p[0] + ":" + p[1] + ":" + p[3]
		}
		if len(p) >= 2 {
			return p[0] + ":" + p[1]
		}
		return name
	}
	var nativeJars []string
	for _, l := range fab.Libraries {
		k := key(l.Name)
		if seen[k] {
			continue
		}
		seen[k] = true
		path := filepath.Join(LibrariesDir(), filepath.FromSlash(MavenPath(l.Name)))
		repo := l.URL
		if repo == "" {
			repo = "https://maven.fabricmc.net/"
		}
		if !strings.HasSuffix(repo, "/") {
			repo += "/"
		}
		tasks = append(tasks, Task{URL: repo + MavenPath(l.Name), Path: path, Sha1: l.Sha1, Size: l.Size})
		classpath = append(classpath, path)
	}
	for _, l := range v.Libraries {
		if !RulesAllow(l.Rules) || nativeForOtherArch(l.Name) {
			continue
		}
		k := key(l.Name)
		if seen[k] {
			continue
		}
		seen[k] = true
		if l.Downloads == nil || l.Downloads.Artifact == nil {
			continue
		}
		a := l.Downloads.Artifact
		path := filepath.Join(LibrariesDir(), filepath.FromSlash(a.Path))
		tasks = append(tasks, Task{URL: a.URL, Path: path, Sha1: a.Sha1, Size: a.Size})
		classpath = append(classpath, path)
		if strings.Contains(l.Name, ":natives-") {
			nativeJars = append(nativeJars, path)
		}
	}
	clientJar := filepath.Join(vdir, MinecraftVersion+".jar")
	c := v.Downloads.Client
	tasks = append(tasks, Task{URL: c.URL, Path: clientJar, Sha1: c.Sha1, Size: c.Size})
	classpath = append(classpath, clientJar)

	logCfg := ""
	if v.Logging.Client.File.URL != "" {
		lf := v.Logging.Client.File
		logCfg = filepath.Join(AssetsDir(), "log_configs", lf.ID)
		tasks = append(tasks, Task{URL: lf.URL, Path: logCfg, Sha1: lf.Sha1, Size: lf.Size})
	}
	if err := DownloadAll("libraries", tasks, 8, progress); err != nil {
		return nil, fmt.Errorf("libraries: %w", err)
	}

	// --- natives
	natives := filepath.Join(vdir, "natives")
	os.MkdirAll(natives, 0o755)
	for _, jar := range nativeJars {
		if err := extractNatives(jar, natives); err != nil {
			return nil, fmt.Errorf("natives: %w", err)
		}
	}

	// --- assets
	idxPath := filepath.Join(AssetsDir(), "indexes", v.AssetIndex.ID+".json")
	if err := DownloadAll("meta", []Task{{URL: v.AssetIndex.URL, Path: idxPath, Sha1: v.AssetIndex.Sha1, Size: v.AssetIndex.Size}}, 1, nil); err != nil {
		return nil, fmt.Errorf("asset index: %w", err)
	}
	var idx struct {
		Objects map[string]struct {
			Hash string `json:"hash"`
			Size int64  `json:"size"`
		} `json:"objects"`
	}
	b, err := os.ReadFile(idxPath)
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(b, &idx); err != nil {
		return nil, err
	}
	var assetTasks []Task
	for _, o := range idx.Objects {
		h := o.Hash
		assetTasks = append(assetTasks, Task{
			URL:  "https://resources.download.minecraft.net/" + h[:2] + "/" + h,
			Path: filepath.Join(AssetsDir(), "objects", h[:2], h),
			Sha1: h, Size: o.Size,
		})
	}
	if err := DownloadAll("assets", assetTasks, 16, progress); err != nil {
		return nil, fmt.Errorf("assets: %w", err)
	}
	return &Installed{Vanilla: &v, Fabric: &fab, Classpath: classpath, Natives: natives, LogConfig: logCfg}, nil
}

func extractNatives(jar, dest string) error {
	r, err := zip.OpenReader(jar)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		name := f.Name
		if f.FileInfo().IsDir() || strings.HasPrefix(name, "META-INF") {
			continue
		}
		lower := strings.ToLower(name)
		if !(strings.HasSuffix(lower, ".dll") || strings.HasSuffix(lower, ".so") || strings.HasSuffix(lower, ".dylib") || strings.HasSuffix(lower, ".jnilib")) {
			continue
		}
		out := filepath.Join(dest, filepath.Base(name))
		if st, err := os.Stat(out); err == nil && st.Size() == int64(f.UncompressedSize64) {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		w, err := os.Create(out)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(w, rc)
		w.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
}
