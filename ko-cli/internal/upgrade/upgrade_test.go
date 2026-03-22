package upgrade

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeBuildFile(t *testing.T, dir, content string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(dir, "build.gradle.kts"), []byte(content), 0644); err != nil {
		t.Fatal(err)
	}
}

func readBuildFile(t *testing.T, dir string) string {
	t.Helper()
	b, err := os.ReadFile(filepath.Join(dir, "build.gradle.kts"))
	if err != nil {
		t.Fatal(err)
	}
	return string(b)
}

func TestRun_UpdatesPluginVersion(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("io.github.omotolani98.ko") version "0.3.0"
    id("org.springframework.boot") version "3.4.1"
}
`)

	result, err := Run(dir, "0.4.0")
	if err != nil {
		t.Fatal(err)
	}
	if result.PreviousVersion != "0.3.0" {
		t.Errorf("expected previous 0.3.0, got %s", result.PreviousVersion)
	}
	if !result.PluginUpdated {
		t.Error("expected plugin to be updated")
	}

	content := readBuildFile(t, dir)
	if !strings.Contains(content, `version "0.4.0"`) {
		t.Errorf("expected version 0.4.0 in build file, got:\n%s", content)
	}
	// Spring Boot version should be untouched
	if !strings.Contains(content, `version "3.4.1"`) {
		t.Error("Spring Boot version was incorrectly modified")
	}
}

func TestRun_UpdatesDependencyVersions(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("io.github.omotolani98.ko") version "0.3.0"
}

dependencies {
    testImplementation("io.github.omotolani98:ko-test:0.3.0")
}
`)

	result, err := Run(dir, "0.4.0")
	if err != nil {
		t.Fatal(err)
	}
	if result.DepsUpdated != 1 {
		t.Errorf("expected 1 dep updated, got %d", result.DepsUpdated)
	}

	content := readBuildFile(t, dir)
	if !strings.Contains(content, `ko-test:0.4.0"`) {
		t.Errorf("expected ko-test:0.4.0 in build file, got:\n%s", content)
	}
}

func TestRun_MigratesOldGroupID(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("dev.ko") version "0.3.0"
}

dependencies {
    testImplementation("dev.ko:ko-test:0.3.0")
}
`)

	result, err := Run(dir, "0.4.0")
	if err != nil {
		t.Fatal(err)
	}
	if !result.PluginUpdated {
		t.Error("expected plugin to be updated")
	}
	if result.DepsUpdated != 1 {
		t.Errorf("expected 1 dep updated, got %d", result.DepsUpdated)
	}

	content := readBuildFile(t, dir)
	if !strings.Contains(content, `id("io.github.omotolani98.ko") version "0.4.0"`) {
		t.Errorf("expected migrated plugin ID, got:\n%s", content)
	}
	if !strings.Contains(content, `io.github.omotolani98:ko-test:0.4.0`) {
		t.Errorf("expected migrated group ID, got:\n%s", content)
	}
}

func TestRun_UpdatesVersionCatalog(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("io.github.omotolani98.ko") version "0.3.0"
}
`)

	gradleDir := filepath.Join(dir, "gradle")
	os.MkdirAll(gradleDir, 0755)
	catalog := `[versions]
ko = "0.3.0"
spring-boot = "3.4.1"
`
	os.WriteFile(filepath.Join(gradleDir, "libs.versions.toml"), []byte(catalog), 0644)

	result, err := Run(dir, "0.4.0")
	if err != nil {
		t.Fatal(err)
	}
	if !result.CatalogUpdated {
		t.Error("expected catalog to be updated")
	}

	b, _ := os.ReadFile(filepath.Join(gradleDir, "libs.versions.toml"))
	content := string(b)
	if !strings.Contains(content, `ko = "0.4.0"`) {
		t.Errorf("expected ko version updated in catalog, got:\n%s", content)
	}
	if !strings.Contains(content, `spring-boot = "3.4.1"`) {
		t.Error("spring-boot version was incorrectly modified")
	}
}

func TestRun_AlreadyUpToDate(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("io.github.omotolani98.ko") version "0.4.0"
}

dependencies {
    testImplementation("io.github.omotolani98:ko-test:0.4.0")
}
`)

	result, err := Run(dir, "0.4.0")
	if err != nil {
		t.Fatal(err)
	}
	if result.PluginUpdated || result.DepsUpdated > 0 || result.CatalogUpdated {
		t.Error("expected no changes for same version")
	}
	if FormatDiff(result) != "Already up to date." {
		t.Errorf("unexpected diff: %s", FormatDiff(result))
	}
}

func TestDetectCurrentVersion(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("io.github.omotolani98.ko") version "0.3.0"
}
`)

	v, err := DetectCurrentVersion(dir)
	if err != nil {
		t.Fatal(err)
	}
	if v != "0.3.0" {
		t.Errorf("expected 0.3.0, got %s", v)
	}
}

func TestDetectCurrentVersion_OldPluginID(t *testing.T) {
	dir := t.TempDir()
	writeBuildFile(t, dir, `plugins {
    id("dev.ko") version "0.2.0"
}
`)

	v, err := DetectCurrentVersion(dir)
	if err != nil {
		t.Fatal(err)
	}
	if v != "0.2.0" {
		t.Errorf("expected 0.2.0, got %s", v)
	}
}
