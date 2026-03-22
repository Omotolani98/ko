package cmd

import (
	"fmt"
	"os"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/Omotolani98/ko/ko-cli/internal/upgrade"
	"github.com/spf13/cobra"
)

var upgradeCmd = &cobra.Command{
	Use:   "upgrade [version]",
	Short: "Upgrade Ko framework version in the current project",
	Long: `Upgrade the Ko framework version in build.gradle.kts and version catalog.

If no version is specified, upgrades to the latest version from Maven Central.
Migrates old plugin IDs (dev.ko) and group IDs to the current ones.`,
	Args: cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		projectDir, err := os.Getwd()
		if err != nil {
			return fmt.Errorf("cannot determine working directory: %w", err)
		}

		// Detect current version
		currentVersion, err := upgrade.DetectCurrentVersion(projectDir)
		if err != nil {
			return err
		}

		// Resolve target version
		var targetVersion string
		if len(args) == 1 {
			targetVersion = args[0]
		} else {
			fmt.Println(style.Info("Checking Maven Central for latest version..."))
			latest, err := internal.FetchLatestKoVersionOrError()
			if err != nil {
				return fmt.Errorf("could not resolve latest version: %w", err)
			}
			targetVersion = latest
		}

		if currentVersion == targetVersion {
			fmt.Println(style.Ok(fmt.Sprintf("Already on Ko %s", currentVersion)))
			return nil
		}

		fmt.Println(style.Info(fmt.Sprintf("Upgrading Ko %s → %s", currentVersion, targetVersion)))

		// Run upgrade
		result, err := upgrade.Run(projectDir, targetVersion)
		if err != nil {
			return err
		}

		// Print summary
		diff := upgrade.FormatDiff(result)
		if diff == "Already up to date." {
			fmt.Println(style.Ok(diff))
		} else {
			fmt.Println(style.Ok("Upgrade complete"))
			fmt.Println(diff)
			fmt.Println()
			fmt.Println(style.Dim.Render("  Run ./gradlew build to verify the upgrade."))
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(upgradeCmd)
}
