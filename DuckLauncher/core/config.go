package core

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Config struct {
	Account    *Account `json:"account"`
	MemoryMB   int      `json:"memoryMb"`
	Sodium     bool     `json:"sodium"`
	CloseOnRun bool     `json:"closeOnRun"`
	JVMArgs    string   `json:"jvmArgs"`
}

func configPath() string { return filepath.Join(BaseDir(), "launcher.json") }

func LoadConfig() *Config {
	c := &Config{MemoryMB: 3072, Sodium: true, CloseOnRun: false}
	if b, err := os.ReadFile(configPath()); err == nil {
		json.Unmarshal(b, c)
	}
	if c.MemoryMB < 1024 {
		c.MemoryMB = 1024
	}
	return c
}

func (c *Config) Save() error {
	os.MkdirAll(BaseDir(), 0o755)
	b, _ := json.MarshalIndent(c, "", "  ")
	return os.WriteFile(configPath(), b, 0o600)
}
