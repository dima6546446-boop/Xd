package main

import (
	_ "embed"
	"flag"
	"fmt"
	"os"
	"strings"

	"ducklauncher/core"
)

//go:embed embed/duck-visual.jar
var duckVisualJar []byte

//go:embed ui/index.html
var indexHTML string

//go:embed embed/hero.jpg
var heroJPG []byte

func main() {
	core.DuckVisualJar = duckVisualJar
	cli := flag.Bool("cli", false, "install and launch from the console")
	name := flag.String("name", "", "offline nickname (with -cli)")
	noSodium := flag.Bool("no-sodium", false, "do not install Sodium (with -cli)")
	flag.Parse()
	if *cli {
		os.Exit(runCLI(*name, !*noSodium))
	}
	runUI()
}

func runCLI(name string, sodium bool) int {
	cfg := core.LoadConfig()
	if name != "" {
		acc, err := core.OfflineAccount(name)
		if err != nil {
			fmt.Println("error:", err)
			return 1
		}
		cfg.Account = acc
	}
	cfg.Sodium = sodium
	cfg.Save()
	last, lastPct := "", int64(-1)
	cmd, logPath, err := core.Prepare(cfg, func(stage string, done, total int64) {
		pct := int64(100)
		if total > 0 {
			pct = done * 100 / total
		}
		// one line per stage change and per 10%
		if stage != last || pct/10 != lastPct/10 {
			fmt.Printf("%-12s %3d%%  (%d/%d)\n", stage, pct, done, total)
			last, lastPct = stage, pct
		}
	})
	if err != nil {
		fmt.Println("error:", err)
		return 1
	}
	fmt.Println("launching:", strings.Join(cmd.Args[:4], " "), "...")
	fmt.Println("log:", logPath)
	if err := cmd.Start(); err != nil {
		fmt.Println("error:", err)
		return 1
	}
	if err := cmd.Wait(); err != nil {
		fmt.Println("game exited:", err)
		return 1
	}
	return 0
}
