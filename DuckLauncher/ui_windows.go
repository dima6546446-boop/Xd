//go:build windows

package main

import (
	"encoding/base64"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"unsafe"

	"ducklauncher/core"

	webview2 "github.com/jchv/go-webview2"
)

type app struct {
	w       webview2.WebView
	cfg     *core.Config
	mu      sync.Mutex
	running bool
	busy    bool
}

func messageBox(title, text string) {
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(text)
	syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x40)
}

// totalMemoryMB uses GlobalMemoryStatusEx to size the RAM slider.
func totalMemoryMB() int {
	type memStatus struct {
		Length               uint32
		MemoryLoad           uint32
		TotalPhys            uint64
		AvailPhys            uint64
		TotalPageFile        uint64
		AvailPageFile        uint64
		TotalVirtual         uint64
		AvailVirtual         uint64
		AvailExtendedVirtual uint64
	}
	var m memStatus
	m.Length = uint32(unsafe.Sizeof(m))
	r, _, _ := syscall.NewLazyDLL("kernel32.dll").NewProc("GlobalMemoryStatusEx").Call(uintptr(unsafe.Pointer(&m)))
	if r == 0 {
		return 8192
	}
	return int(m.TotalPhys / 1024 / 1024)
}

func runUI() {
	a := &app{cfg: core.LoadConfig()}
	os.MkdirAll(core.BaseDir(), 0o755)
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		DataPath:  filepath.Join(core.BaseDir(), "webview"),
		WindowOptions: webview2.WindowOptions{
			Title:  "Duck Launcher",
			Width:  1000,
			Height: 640,
			IconId: 1,
			Center: true,
		},
	})
	if w == nil {
		messageBox("Duck Launcher", "Для окна лаунчера нужен Microsoft Edge WebView2 Runtime.\n\n"+
			"Он уже есть в Windows 11. Если у тебя Windows 10 — установи его с сайта Microsoft "+
			"(«WebView2 Runtime»), откроется страница загрузки.")
		exec.Command("rundll32", "url.dll,FileProtocolHandler", "https://developer.microsoft.com/microsoft-edge/webview2/").Start()
		return
	}
	a.w = w
	defer w.Destroy()
	w.SetSize(1000, 640, webview2.HintFixed)

	w.Bind("dlState", a.state)
	w.Bind("dlLoginOffline", a.loginOffline)
	w.Bind("dlLoginEly", a.loginEly)
	w.Bind("dlLogout", a.logout)
	w.Bind("dlSaveSettings", a.saveSettings)
	w.Bind("dlPlay", a.play)
	w.Bind("dlOpen", a.open)
	w.SetHtml(strings.Replace(indexHTML, "{{HERO}}", base64.StdEncoding.EncodeToString(heroJPG), 1))
	w.Run()
}

func (a *app) emit(fn string, args ...any) {
	b, _ := json.Marshal(args)
	js := "window." + fn + " && window." + fn + ".apply(null, " + string(b) + ")"
	a.w.Dispatch(func() { a.w.Eval(js) })
}

type state struct {
	Account     *core.Account `json:"account"`
	MemoryMB    int           `json:"memoryMb"`
	MaxMemoryMB int           `json:"maxMemoryMb"`
	Sodium      bool          `json:"sodium"`
	CloseOnRun  bool          `json:"closeOnRun"`
	JVMArgs     string        `json:"jvmArgs"`
	Running     bool          `json:"running"`
	Busy        bool          `json:"busy"`
	Version     string        `json:"version"`
	Dir         string        `json:"dir"`
}

func (a *app) state() state {
	a.mu.Lock()
	defer a.mu.Unlock()
	acc := a.cfg.Account
	var safe *core.Account
	if acc != nil {
		safe = &core.Account{Type: acc.Type, Name: acc.Name}
	}
	return state{Account: safe, MemoryMB: a.cfg.MemoryMB, MaxMemoryMB: totalMemoryMB(), Sodium: a.cfg.Sodium,
		CloseOnRun: a.cfg.CloseOnRun, JVMArgs: a.cfg.JVMArgs, Running: a.running, Busy: a.busy,
		Version: core.LauncherVersion, Dir: core.BaseDir()}
}

func (a *app) loginOffline(name string) string {
	acc, err := core.OfflineAccount(name)
	if err != nil {
		return err.Error()
	}
	a.mu.Lock()
	a.cfg.Account = acc
	a.cfg.Save()
	a.mu.Unlock()
	return ""
}

func (a *app) loginEly(login, password string) string {
	acc, err := core.ElyLogin(login, password)
	if err != nil {
		return err.Error()
	}
	a.mu.Lock()
	a.cfg.Account = acc
	a.cfg.Save()
	a.mu.Unlock()
	return ""
}

func (a *app) logout() {
	a.mu.Lock()
	a.cfg.Account = nil
	a.cfg.Save()
	a.mu.Unlock()
}

func (a *app) saveSettings(memoryMb int, sodium, closeOnRun bool, jvmArgs string) {
	a.mu.Lock()
	if memoryMb >= 1024 {
		a.cfg.MemoryMB = memoryMb
	}
	a.cfg.Sodium = sodium
	a.cfg.CloseOnRun = closeOnRun
	a.cfg.JVMArgs = jvmArgs
	a.cfg.Save()
	a.mu.Unlock()
}

func (a *app) open(kind string) {
	dir := core.GameDir()
	switch kind {
	case "mods":
		dir = core.ModsDir()
	case "logs":
		dir = core.LogsDir()
	case "screenshots":
		dir = filepath.Join(core.GameDir(), "screenshots")
	}
	os.MkdirAll(dir, 0o755)
	exec.Command("explorer", dir).Start()
}

func (a *app) play() string {
	a.mu.Lock()
	if a.busy || a.running {
		a.mu.Unlock()
		return "Игра уже запускается"
	}
	if a.cfg.Account == nil {
		a.mu.Unlock()
		return "Сначала войди в аккаунт"
	}
	a.busy = true
	a.mu.Unlock()

	go func() {
		cmd, logPath, err := core.Prepare(a.cfg, func(stage string, done, total int64) {
			a.emit("onProgress", stage, done, total)
		})
		if err == nil {
			err = cmd.Start()
		}
		a.mu.Lock()
		a.busy = false
		a.mu.Unlock()
		if err != nil {
			a.emit("onError", err.Error())
			return
		}
		a.mu.Lock()
		a.running = true
		closeOnRun := a.cfg.CloseOnRun
		a.mu.Unlock()
		a.emit("onLaunched", logPath)
		if closeOnRun {
			a.w.Dispatch(func() { a.w.Terminate() })
			return
		}
		werr := cmd.Wait()
		a.mu.Lock()
		a.running = false
		a.mu.Unlock()
		msg := ""
		if werr != nil {
			msg = werr.Error()
		}
		a.emit("onGameExit", msg, logPath)
	}()
	return ""
}
