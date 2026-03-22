package cmd

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/Omotolani98/ko/ko-cli/internal/daemon"
	"github.com/Omotolani98/ko/ko-cli/internal/model"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var runCmd = &cobra.Command{
	Use:   "run",
	Short: "Build and start your app with local infrastructure",
	Long: `Build the application model, provision infrastructure containers,
generate infra-config.json, and start the Spring Boot app via Gradle bootRun.

Features:
  --watch        Watch source files and rebuild on changes (default: true)
  --containers   Use Docker containers for real Postgres, Kafka, Redis (default: true)

This is the main development command — run it and start coding.`,
	RunE: runApp,
}

var (
	runPort       int
	runModule     string
	dashboardPort int
	runWatch      bool
	runContainers bool
)

func init() {
	runCmd.Flags().IntVarP(&runPort, "port", "p", 8080, "HTTP port for the app")
	runCmd.Flags().StringVarP(&runModule, "module", "m", "", "Gradle module to run (e.g., :examples:hello-world)")
	runCmd.Flags().IntVar(&dashboardPort, "dashboard-port", 9400, "Port for the dev dashboard")
	runCmd.Flags().BoolVar(&runWatch, "watch", true, "Watch source files and rebuild on changes")
	runCmd.Flags().BoolVar(&runContainers, "containers", true, "Use Docker containers for infrastructure")
	rootCmd.AddCommand(runCmd)
}

func runApp(cmd *cobra.Command, args []string) error {
	projectDir, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to get working directory: %w", err)
	}

	moduleDir := daemon.ResolveModuleDir(projectDir, runModule)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle signals
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigCh
		fmt.Println()
		fmt.Println(style.Info(fmt.Sprintf("Received %s, shutting down...", sig)))
		cancel()
	}()

	d := daemon.New(daemon.Opts{
		ProjectDir:    projectDir,
		ModuleDir:     moduleDir,
		Module:        runModule,
		AppPort:       runPort,
		DashboardPort: dashboardPort,
		Watch:         runWatch,
		Containers:    runContainers,
	})

	return d.Run(ctx)
}

// printInfrastructure displays discovered services and infrastructure from the model.
func printInfrastructure(m *model.AppModel) {
	for _, svc := range m.Services {
		fmt.Printf("  %s %s\n", style.Tag("service", style.Secondary), style.Service(svc.Name))

		for _, api := range svc.APIs {
			method := style.Bold.Render(api.Method)
			authTag := ""
			if api.Auth {
				authTag = " " + style.Tag("auth", style.Warning)
			}
			fmt.Printf("    %s %s%s\n", method, api.Path, authTag)
		}

		for _, db := range svc.Databases {
			fmt.Printf("    %s %s\n", style.Tag("db", style.Primary), style.Infra(db.Name))
		}
		for _, c := range svc.Caches {
			fmt.Printf("    %s %s\n", style.Tag("cache", style.Primary), style.Infra(c.Name))
		}
		for _, b := range svc.Buckets {
			fmt.Printf("    %s %s\n", style.Tag("bucket", style.Primary), style.Infra(b.Name))
		}
		for _, cj := range svc.CronJobs {
			fmt.Printf("    %s %s (%s)\n", style.Tag("cron", style.Primary), style.Infra(cj.Name), cj.Schedule)
		}
		fmt.Println()
	}

	if len(m.PubSubTopics) > 0 {
		for _, t := range m.PubSubTopics {
			fmt.Printf("  %s %s → %d subscriber(s)\n",
				style.Tag("topic", style.Primary), style.Infra(t.Name), len(t.Subscribers))
		}
		fmt.Println()
	}
}
