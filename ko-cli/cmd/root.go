package cmd

import (
	"fmt"
	"os"
	"runtime/debug"

	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

// version is set at build time via ldflags (GoReleaser).
// Falls back to Go module version from debug.BuildInfo (go install).
var version = func() string {
	if info, ok := debug.ReadBuildInfo(); ok && info.Main.Version != "" && info.Main.Version != "(devel)" {
		return info.Main.Version
	}
	return "dev"
}()

var rootCmd = &cobra.Command{
	Use:   "ko",
	Short: "Kọ́ — build type-safe distributed systems in Java",
	Long: fmt.Sprintf(`%s

  Kọ́ (Yoruba: "to build / to learn") is a Java framework for building
  type-safe distributed systems with declarative infrastructure.

  Annotate your code → the framework handles the rest.

  %s
    ko run          Start your app with local infrastructure
    ko dashboard    Open the dev dashboard in your browser
    ko build        Build a production Docker image
    ko gen model    Regenerate the application model
    ko test         Run tests with isolated infrastructure
    ko version      Print version information`,
		style.Banner(),
		style.BoldPri.Render("Commands:")),
	SilenceUsage:  true,
	SilenceErrors: true,
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, style.Err(err.Error()))
		os.Exit(1)
	}
}

func init() {
	rootCmd.CompletionOptions.HiddenDefaultCmd = true
}
