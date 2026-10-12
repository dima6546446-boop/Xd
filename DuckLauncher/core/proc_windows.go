//go:build windows

package core

import (
	"os/exec"
	"syscall"
)

// hideWindow makes sure no console window pops up for the game process.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
}
