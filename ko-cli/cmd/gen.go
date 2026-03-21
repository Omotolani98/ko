package cmd

import (
	"fmt"
	"os"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/model"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var genCmd = &cobra.Command{
	Use:   "gen",
	Short: "Code generation commands",
}

var genModelCmd = &cobra.Command{
	Use:   "model",
	Short: "Regenerate the application model (ko-app-model.json)",
	RunE: func(cmd *cobra.Command, args []string) error {
		projectDir, _ := os.Getwd()

		fmt.Println(style.Info("Generating application model..."))

		err := internal.RunGradle(projectDir, []string{"build", "-x", "test"}, nil, os.Stdout, os.Stderr)
		if err != nil {
			return fmt.Errorf("build failed: %w", err)
		}

		appModel, err := model.LoadAppModel(projectDir)
		if err != nil {
			return fmt.Errorf("failed to load generated model: %w", err)
		}

		fmt.Println()
		fmt.Println(style.Ok(fmt.Sprintf("Generated app model: %s", appModel.Summary())))
		return nil
	},
}

func init() {
	genCmd.AddCommand(genModelCmd)
	rootCmd.AddCommand(genCmd)
}
