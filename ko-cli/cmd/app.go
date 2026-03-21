package cmd

import (
	"fmt"
	"os"
	"regexp"
	"strings"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/scaffold"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var appCmd = &cobra.Command{
	Use:   "app --name <app-name>",
	Short: "Create a new Kọ́ application",
	Long: `Scaffold a new Kọ́ project with all the boilerplate:
  - Gradle build with the io.github.omotolani98.ko plugin
  - Spring Boot configuration
  - A sample service with @KoService and @KoAPI annotations
  - A test class using ko-test

Example:
  ko app --name my-cool-app
  ko app --name my-cool-app --package com.mycompany.coolapp`,
	RunE: createApp,
}

var (
	appName    string
	appPackage string
)

func init() {
	appCmd.Flags().StringVar(&appName, "name", "", "Application name (kebab-case, e.g., my-cool-app)")
	appCmd.Flags().StringVar(&appPackage, "package", "", "Java package (default: com.example.<appname>)")
	_ = appCmd.MarkFlagRequired("name")
	rootCmd.AddCommand(appCmd)
}

var kebabRegex = regexp.MustCompile(`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`)

func createApp(cmd *cobra.Command, args []string) error {
	// Validate name
	if !kebabRegex.MatchString(appName) {
		return fmt.Errorf("app name must be kebab-case (e.g., my-cool-app), got: %s", appName)
	}

	// Default package
	pkg := appPackage
	if pkg == "" {
		pkg = "com.example." + scaffold.ToPackage(appName)
	}

	// Validate package
	if !isValidJavaPackage(pkg) {
		return fmt.Errorf("invalid Java package: %s", pkg)
	}

	cwd, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to get working directory: %w", err)
	}

	fmt.Println(style.Banner())
	fmt.Println()
	fmt.Println(style.Info(fmt.Sprintf("Creating new Kọ́ application: %s", style.Service(appName))))
	fmt.Println()

	koVersion := internal.FetchLatestKoVersion()
	fmt.Println(style.Info(fmt.Sprintf("Using Kọ́ framework version: %s", style.Dim.Render(koVersion))))
	fmt.Println()

	opts := scaffold.Options{
		Name:          appName,
		Package:       pkg,
		OutputDir:     cwd,
		KoVersion:     koVersion,
		SpringVersion: "3.4.1",
	}

	projectDir, err := scaffold.Run(opts)
	if err != nil {
		return fmt.Errorf("failed to scaffold project: %w", err)
	}

	// Print summary
	fmt.Println(style.Ok("Project created!"))
	fmt.Println()
	fmt.Printf("  %s  %s\n", style.Tag("dir", style.Secondary), style.Path(projectDir))
	fmt.Printf("  %s  %s\n", style.Tag("pkg", style.Secondary), style.Dim.Render(pkg))
	fmt.Println()
	fmt.Println(style.Info("Next steps:"))
	fmt.Println()
	fmt.Printf("  %s\n", style.Dim.Render(fmt.Sprintf("cd %s", appName)))
	fmt.Printf("  %s\n", style.Dim.Render("ko run"))
	fmt.Println()
	fmt.Printf("  Your app will be available at %s\n", style.URL("http://localhost:8080/hello"))
	fmt.Println()

	return nil
}

func isValidJavaPackage(pkg string) bool {
	parts := strings.Split(pkg, ".")
	if len(parts) < 2 {
		return false
	}
	for _, part := range parts {
		if len(part) == 0 {
			return false
		}
		for i, r := range part {
			if i == 0 && !isJavaIdentStart(r) {
				return false
			}
			if i > 0 && !isJavaIdentPart(r) {
				return false
			}
		}
	}
	return true
}

func isJavaIdentStart(r rune) bool {
	return (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || r == '_'
}

func isJavaIdentPart(r rune) bool {
	return isJavaIdentStart(r) || (r >= '0' && r <= '9')
}
