package core

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

// Prepare installs everything needed and returns a ready-to-start command.
func Prepare(cfg *Config, progress Progress) (*exec.Cmd, string, error) {
	if cfg.Account == nil {
		return nil, "", fmt.Errorf("сначала войди в аккаунт")
	}
	java, err := EnsureJava(progress)
	if err != nil {
		return nil, "", err
	}
	inst, err := Install(progress)
	if err != nil {
		return nil, "", err
	}
	if err := EnsureMods(cfg.Sodium, progress); err != nil {
		return nil, "", fmt.Errorf("mods: %w", err)
	}
	var authlib string
	if cfg.Account.Type == "ely" {
		if err := ElyRefresh(cfg.Account); err != nil {
			return nil, "", fmt.Errorf("вход Ely.by устарел, войди заново: %w", err)
		}
		cfg.Save()
		if authlib, err = EnsureAuthlibInjector(progress); err != nil {
			return nil, "", err
		}
	}
	if progress != nil {
		progress("launch", 1, 1)
	}

	sep := string(os.PathListSeparator)
	vars := map[string]string{
		"auth_player_name":    cfg.Account.Name,
		"version_name":        MinecraftVersion,
		"game_directory":      GameDir(),
		"assets_root":         AssetsDir(),
		"assets_index_name":   inst.Vanilla.AssetIndex.ID,
		"auth_uuid":           cfg.Account.UUID,
		"auth_access_token":   cfg.Account.AccessToken,
		"clientid":            "",
		"auth_xuid":           "",
		"user_type":           map[bool]string{true: "mojang", false: "legacy"}[cfg.Account.Type == "ely"],
		"version_type":        "Duck Visual",
		"natives_directory":   inst.Natives,
		"launcher_name":       LauncherName,
		"launcher_version":    LauncherVersion,
		"classpath":           strings.Join(inst.Classpath, sep),
		"classpath_separator": sep,
		"library_directory":   LibrariesDir(),
		"user_properties":     "{}",
	}
	sub := func(s string) string {
		for k, v := range vars {
			s = strings.ReplaceAll(s, "${"+k+"}", v)
		}
		return s
	}

	var args []string
	args = append(args, fmt.Sprintf("-Xmx%dm", cfg.MemoryMB), "-Xms512m",
		"-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=20",
		"-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M")
	if authlib != "" {
		args = append(args, "-javaagent:"+authlib+"=ely.by")
	}
	if inst.LogConfig != "" {
		args = append(args, sub(strings.ReplaceAll(inst.Vanilla.Logging.Client.Argument, "${path}", inst.LogConfig)))
	}
	if extra := strings.TrimSpace(cfg.JVMArgs); extra != "" {
		args = append(args, strings.Fields(extra)...)
	}
	for _, v := range []*VersionJSON{inst.Vanilla, inst.Fabric} {
		for _, a := range v.Arguments.JVM {
			if RulesAllow(a.Rules) {
				for _, x := range a.Values {
					args = append(args, sub(x))
				}
			}
		}
	}
	args = append(args, inst.Fabric.MainClass)
	for _, v := range []*VersionJSON{inst.Vanilla, inst.Fabric} {
		for _, a := range v.Arguments.Game {
			if RulesAllow(a.Rules) {
				for _, x := range a.Values {
					args = append(args, sub(x))
				}
			}
		}
	}

	os.MkdirAll(GameDir(), 0o755)
	os.MkdirAll(LogsDir(), 0o755)
	// first launch: skip the accessibility onboarding screen and start in Russian
	if opts := filepath.Join(GameDir(), "options.txt"); !fileOK(Task{Path: opts}) {
		os.WriteFile(opts, []byte("onboardAccessibility:false\nlang:ru_ru\nguiScale:2\n"), 0o644)
	}
	cmd := exec.Command(java, args...)
	cmd.Dir = GameDir()
	logPath := filepath.Join(LogsDir(), "game-"+time.Now().Format("2006-01-02_15-04-05")+".log")
	if f, err := os.Create(logPath); err == nil {
		cmd.Stdout = f
		cmd.Stderr = f
	}
	hideWindow(cmd)
	return cmd, logPath, nil
}
