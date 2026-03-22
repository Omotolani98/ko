package cmd

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net"

	"github.com/Omotolani98/ko/ko-cli/internal/daemon"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show the status of the running Ko daemon",
	Long:  `Connects to the Ko daemon socket and displays the current state, running containers, and uptime.`,
	RunE:  runStatus,
}

func init() {
	rootCmd.AddCommand(statusCmd)
}

func runStatus(cmd *cobra.Command, args []string) error {
	sockPath := daemon.SocketPath()

	conn, err := net.Dial("unix", sockPath)
	if err != nil {
		return fmt.Errorf("no running daemon (could not connect to %s)\nStart one with: ko run", sockPath)
	}
	defer conn.Close()

	// Send status request
	req := daemon.SocketRequest{Method: "status", ID: "1"}
	data, _ := json.Marshal(req)
	data = append(data, '\n')
	if _, err := conn.Write(data); err != nil {
		return fmt.Errorf("failed to send request: %w", err)
	}

	// Read response
	scanner := bufio.NewScanner(conn)
	if !scanner.Scan() {
		return fmt.Errorf("no response from daemon")
	}

	var resp daemon.SocketResponse
	if err := json.Unmarshal(scanner.Bytes(), &resp); err != nil {
		return fmt.Errorf("invalid response: %w", err)
	}
	if resp.Error != "" {
		return fmt.Errorf("daemon error: %s", resp.Error)
	}

	// Parse the result as StatusResult
	resultBytes, _ := json.Marshal(resp.Result)
	var status daemon.StatusResult
	if err := json.Unmarshal(resultBytes, &status); err != nil {
		return fmt.Errorf("failed to parse status: %w", err)
	}

	// Display
	fmt.Println(style.Banner())
	fmt.Println()
	fmt.Printf("  %s %s\n", style.Bold.Render("State:"), stateStyle(status.State))
	fmt.Printf("  %s %s\n", style.Bold.Render("Uptime:"), status.Uptime)
	fmt.Printf("  %s %s\n", style.Bold.Render("App:"), style.URL(fmt.Sprintf("http://localhost:%d", status.AppPort)))
	fmt.Printf("  %s %s\n", style.Bold.Render("Dashboard:"), style.URL(fmt.Sprintf("http://localhost:%d", status.DashboardPort)))
	fmt.Printf("  %s %v\n", style.Bold.Render("Watch:"), status.WatchEnabled)
	fmt.Println()

	if len(status.Containers) > 0 {
		fmt.Println(style.Bold.Render("  Containers:"))
		for _, c := range status.Containers {
			fmt.Printf("    %s %s on :%d (%s)\n",
				style.Tag(c.Type, style.Primary),
				style.Infra(c.Name),
				c.Port,
				c.Status,
			)
		}
	} else {
		fmt.Println(style.Dim.Render("  No containers (using in-memory providers)"))
	}
	fmt.Println()

	return nil
}

func stateStyle(state string) string {
	switch state {
	case "running":
		return style.BoldOk.Render(state)
	case "rebuilding":
		return style.BoldWarn.Render(state)
	case "starting", "provisioning":
		return style.BoldSec.Render(state)
	case "shutdown":
		return style.BoldErr.Render(state)
	default:
		return state
	}
}
