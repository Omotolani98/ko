package cmd

import (
	"fmt"

	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var buildCmd = &cobra.Command{
	Use:   "build",
	Short: "Build artifacts for deployment",
}

var buildDockerCmd = &cobra.Command{
	Use:   "docker [image:tag]",
	Short: "Build a production Docker image",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		// TODO: implement Docker build
		fmt.Println(style.Warn("Docker build is not yet implemented"))
		fmt.Println(style.Info(fmt.Sprintf("Target image: %s", args[0])))
		return nil
	},
}

func init() {
	buildCmd.AddCommand(buildDockerCmd)
	rootCmd.AddCommand(buildCmd)
}
