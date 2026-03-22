package upgrade

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const (
	pluginID = "io.github.omotolani98.ko"
	groupID  = "io.github.omotolani98"
)

// Result holds the outcome of an upgrade operation.
type Result struct {
	BuildFile      string
	PreviousVersion string
	NewVersion      string
	PluginUpdated   bool
	DepsUpdated     int
	CatalogUpdated  bool
}

// Run upgrades the Ko framework version in a project directory.
func Run(projectDir, targetVersion string) (*Result, error) {
	buildFile := filepath.Join(projectDir, "build.gradle.kts")
	content, err := os.ReadFile(buildFile)
	if err != nil {
		return nil, fmt.Errorf("cannot read build.gradle.kts: %w", err)
	}

	original := string(content)
	updated := original
	result := &Result{
		BuildFile:  buildFile,
		NewVersion: targetVersion,
	}

	// 1. Update plugin version: id("io.github.omotolani98.ko") version "X.Y.Z"
	pluginRe := regexp.MustCompile(`(id\("` + regexp.QuoteMeta(pluginID) + `"\)\s+version\s+")([^"]+)(")`)
	if m := pluginRe.FindStringSubmatch(updated); len(m) == 4 {
		result.PreviousVersion = m[2]
		if m[2] != targetVersion {
			updated = pluginRe.ReplaceAllString(updated, "${1}"+targetVersion+"${3}")
			result.PluginUpdated = true
		}
	}

	// Also handle: id("io.github.omotolani98.ko") version "X.Y.Z" with alias form
	// alias(libs.plugins.ko) — skip, handled via catalog

	// 2. Update dependency versions: "io.github.omotolani98:ko-*:X.Y.Z"
	depRe := regexp.MustCompile(`("` + regexp.QuoteMeta(groupID) + `:ko-[^:]+:)([^"]+)(")`)
	matches := depRe.FindAllStringSubmatch(updated, -1)
	for _, m := range matches {
		if m[2] != targetVersion {
			result.DepsUpdated++
		}
	}
	if result.DepsUpdated > 0 {
		updated = depRe.ReplaceAllString(updated, "${1}"+targetVersion+"${3}")
	}

	// Also try the old "dev.ko" group for migration
	oldGroupID := "dev.ko"
	oldDepRe := regexp.MustCompile(`("` + regexp.QuoteMeta(oldGroupID) + `:ko-[^:]+:)([^"]+)(")`)
	oldMatches := oldDepRe.FindAllStringSubmatch(updated, -1)
	for _, m := range oldMatches {
		if result.PreviousVersion == "" {
			result.PreviousVersion = m[2]
		}
		result.DepsUpdated++
	}
	if len(oldMatches) > 0 {
		// Replace old group with new group and update version
		oldDepReReplace := regexp.MustCompile(`"` + regexp.QuoteMeta(oldGroupID) + `:(ko-[^:]+):[^"]+"`)
		updated = oldDepReReplace.ReplaceAllString(updated, `"`+groupID+`:${1}:`+targetVersion+`"`)
	}

	// Also handle old plugin ID "dev.ko"
	oldPluginRe := regexp.MustCompile(`(id\("dev\.ko"\)\s+version\s+")([^"]+)(")`)
	if m := oldPluginRe.FindStringSubmatch(updated); len(m) == 4 {
		if result.PreviousVersion == "" {
			result.PreviousVersion = m[2]
		}
		updated = oldPluginRe.ReplaceAllString(updated, `id("`+pluginID+`") version "`+targetVersion+`"`)
		result.PluginUpdated = true
	}

	// Write back if changed
	if updated != original {
		if err := os.WriteFile(buildFile, []byte(updated), 0644); err != nil {
			return nil, fmt.Errorf("failed to write build.gradle.kts: %w", err)
		}
	}

	// 3. Update version catalog if it exists
	catalogFile := filepath.Join(projectDir, "gradle", "libs.versions.toml")
	if catalogContent, err := os.ReadFile(catalogFile); err == nil {
		catalogStr := string(catalogContent)
		catalogUpdated := catalogStr

		// Update ko version in [versions] section: ko = "X.Y.Z"
		koVersionRe := regexp.MustCompile(`(ko\s*=\s*")([^"]+)(")`)
		if m := koVersionRe.FindStringSubmatch(catalogUpdated); len(m) == 4 {
			if result.PreviousVersion == "" {
				result.PreviousVersion = m[2]
			}
			if m[2] != targetVersion {
				catalogUpdated = koVersionRe.ReplaceAllString(catalogUpdated, "${1}"+targetVersion+"${3}")
				result.CatalogUpdated = true
			}
		}

		if catalogUpdated != catalogStr {
			if err := os.WriteFile(catalogFile, []byte(catalogUpdated), 0644); err != nil {
				return nil, fmt.Errorf("failed to write libs.versions.toml: %w", err)
			}
		}
	}

	if result.PreviousVersion == "" {
		result.PreviousVersion = "unknown"
	}

	return result, nil
}

// DetectCurrentVersion reads the current Ko version from build.gradle.kts.
func DetectCurrentVersion(projectDir string) (string, error) {
	buildFile := filepath.Join(projectDir, "build.gradle.kts")
	content, err := os.ReadFile(buildFile)
	if err != nil {
		return "", fmt.Errorf("cannot read build.gradle.kts: %w", err)
	}

	text := string(content)

	// Try current plugin ID
	pluginRe := regexp.MustCompile(`id\("` + regexp.QuoteMeta(pluginID) + `"\)\s+version\s+"([^"]+)"`)
	if m := pluginRe.FindStringSubmatch(text); len(m) == 2 {
		return m[1], nil
	}

	// Try old plugin ID
	oldPluginRe := regexp.MustCompile(`id\("dev\.ko"\)\s+version\s+"([^"]+)"`)
	if m := oldPluginRe.FindStringSubmatch(text); len(m) == 2 {
		return m[1], nil
	}

	// Try version catalog
	catalogFile := filepath.Join(projectDir, "gradle", "libs.versions.toml")
	if catalogContent, err := os.ReadFile(catalogFile); err == nil {
		koVersionRe := regexp.MustCompile(`ko\s*=\s*"([^"]+)"`)
		if m := koVersionRe.FindStringSubmatch(string(catalogContent)); len(m) == 2 {
			return m[1], nil
		}
	}

	// Try dependency version
	depRe := regexp.MustCompile(`"(?:` + regexp.QuoteMeta(groupID) + `|dev\.ko):ko-[^:]+:([^"]+)"`)
	if m := depRe.FindStringSubmatch(text); len(m) == 2 {
		return m[1], nil
	}

	return "", fmt.Errorf("could not detect Ko version in %s", buildFile)
}

// FormatDiff returns a human-readable summary of what changed.
func FormatDiff(r *Result) string {
	if !r.PluginUpdated && r.DepsUpdated == 0 && !r.CatalogUpdated {
		return "Already up to date."
	}

	var lines []string
	if r.PluginUpdated {
		lines = append(lines, fmt.Sprintf("  Plugin: %s → %s", r.PreviousVersion, r.NewVersion))
	}
	if r.DepsUpdated > 0 {
		lines = append(lines, fmt.Sprintf("  Dependencies: %d updated to %s", r.DepsUpdated, r.NewVersion))
	}
	if r.CatalogUpdated {
		lines = append(lines, fmt.Sprintf("  Version catalog: ko → %s", r.NewVersion))
	}
	return strings.Join(lines, "\n")
}
