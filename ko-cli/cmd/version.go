package cmd

import (
	"fmt"

	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print version information",
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Printf("%s %s\n", style.KoPrefix, style.BoldPri.Render("v"+version))
	},
}

func init() {
	rootCmd.AddCommand(versionCmd)
}
