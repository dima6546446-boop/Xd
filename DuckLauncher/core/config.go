package core

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Config struct {
	Account *Account `json:"account"`

	Version string `json:"version"` // Minecraft version id
	Loader  string `json:"loader"`  // "fabric" or "vanilla"

	MemoryMB    int             `json:"memoryMb"` // 0 = automatic
	Mods        map[string]bool `json:"mods"`     // performance mods by Modrinth slug
	WeakPC      bool            `json:"weakPc"`   // light graphics preset for new instances
	Width       int             `json:"width"`
	Height      int             `json:"height"`
	Fullscreen  bool            `json:"fullscreen"`
	Lang        string          `json:"lang"`
	AfterLaunch string          `json:"afterLaunch"` // "keep", "minimize", "close"
	JVMArgs     string          `json:"jvmArgs"`
	CFKey       string          `json:"cfKey"` // CurseForge API key for the mods catalog

	// QuickJoin is a server address to connect to right after start (not saved).
	QuickJoin string `json:"-"`

	// legacy fields from launcher 1.0
	Sodium     *bool `json:"sodium,omitempty"`
	CloseOnRun *bool `json:"closeOnRun,omitempty"`
}

func configPath() string { return filepath.Join(BaseDir(), "launcher.json") }

func LoadConfig() *Config {
	c := &Config{}
	if b, err := os.ReadFile(configPath()); err == nil {
		json.Unmarshal(b, c)
	}
	if c.Version == "" {
		c.Version = DuckVersion
	}
	if c.Loader == "" {
		c.Loader = "fabric"
	}
	if c.Mods == nil {
		c.Mods = map[string]bool{"sodium": true, "lithium": true, "ferrite-core": true, "immediatelyfast": true, "entityculling": true}
		if c.Sodium != nil && !*c.Sodium {
			c.Mods["sodium"] = false
		}
	}
	if c.AfterLaunch == "" {
		c.AfterLaunch = "minimize"
		if c.CloseOnRun != nil && *c.CloseOnRun {
			c.AfterLaunch = "close"
		}
	}
	c.Sodium, c.CloseOnRun = nil, nil
	if c.Lang == "" {
		c.Lang = "ru_ru"
	}
	if c.Width == 0 || c.Height == 0 {
		c.Width, c.Height = 1280, 720
	}
	if c.MemoryMB != 0 && c.MemoryMB < 1024 {
		c.MemoryMB = 1024
	}
	return c
}

// AutoMemoryMB picks a sensible heap for the machine's RAM (weak PCs get less so Windows does not swap).
func AutoMemoryMB(totalMB int) int {
	switch {
	case totalMB <= 4096:
		return 1536
	case totalMB <= 8192:
		return 2560
	case totalMB <= 16384:
		return 4096
	default:
		return 6144
	}
}

func (c *Config) Save() error {
	os.MkdirAll(BaseDir(), 0o755)
	b, _ := json.MarshalIndent(c, "", "  ")
	return os.WriteFile(configPath(), b, 0o600)
}
