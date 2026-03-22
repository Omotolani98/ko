package cmd

import (
	"fmt"
	"os/exec"
	"runtime"

	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var dashboardOpenPort int

var dashboardCmd = &cobra.Command{
	Use:   "dashboard",
	Short: "Open the Kọ́ dev dashboard in your browser",
	Long:  `Opens the development dashboard in your default browser. The dashboard must be running (started by ko run).`,
	RunE: func(cmd *cobra.Command, args []string) error {
		url := fmt.Sprintf("http://localhost:%d", dashboardOpenPort)
		fmt.Println(style.Info(fmt.Sprintf("Opening dashboard at %s", style.URL(url))))
		return openBrowser(url)
	},
}

func init() {
	dashboardCmd.Flags().IntVar(&dashboardOpenPort, "port", 9400, "Dashboard port")
	rootCmd.AddCommand(dashboardCmd)
}

func openBrowser(url string) error {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "linux":
		cmd = exec.Command("xdg-open", url)
	case "windows":
		cmd = exec.Command("cmd", "/c", "start", url)
	default:
		return fmt.Errorf("unsupported platform: %s", runtime.GOOS)
	}
	return cmd.Start()
}
