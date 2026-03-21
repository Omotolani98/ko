package cmd

import (
	"fmt"
	"os"
	"time"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var testCmd = &cobra.Command{
	Use:   "test",
	Short: "Run tests with isolated Ko infrastructure",
	Long: `Run tests using the ko-test framework with isolated infrastructure.
Each test class gets its own in-memory providers, automatically reset between tests.`,
	RunE: runTests,
}

var (
	testModule string
	testVerbose bool
)

func init() {
	testCmd.Flags().StringVarP(&testModule, "module", "m", "", "Gradle module to test (e.g., :examples:hello-world)")
	testCmd.Flags().BoolVarP(&testVerbose, "verbose", "v", false, "Show verbose test output")
	rootCmd.AddCommand(testCmd)
}

func runTests(cmd *cobra.Command, args []string) error {
	startTime := time.Now()
	projectDir, _ := os.Getwd()

	fmt.Println(style.Info("Running Ko tests..."))
	fmt.Println()

	testTask := "test"
	if testModule != "" {
		testTask = testModule + ":test"
	}

	gradleArgs := []string{testTask}
	if testVerbose {
		gradleArgs = append(gradleArgs, "--info")
	}

	err := internal.RunGradle(projectDir, gradleArgs, nil, os.Stdout, os.Stderr)
	elapsed := time.Since(startTime).Round(time.Millisecond)

	fmt.Println()
	if err != nil {
		fmt.Println(style.Err(fmt.Sprintf("Tests failed (%s)", elapsed)))
		return err
	}

	fmt.Println(style.Ok(fmt.Sprintf("All tests passed (%s)", elapsed)))
	return nil
}
