package internal

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

// FindGradleWrapper locates the Gradle wrapper script relative to a project dir.
func FindGradleWrapper(projectDir string) (string, error) {
	var wrapper string
	if runtime.GOOS == "windows" {
		wrapper = "gradlew.bat"
	} else {
		wrapper = "gradlew"
	}

	// Walk up from projectDir looking for gradlew
	dir := projectDir
	for {
		path := filepath.Join(dir, wrapper)
		if _, err := os.Stat(path); err == nil {
			return path, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}

	return "", fmt.Errorf("gradle wrapper (%s) not found in %s or parent directories", wrapper, projectDir)
}

// RunGradle runs a Gradle command and streams output.
// Returns the exit code and any error.
func RunGradle(projectDir string, args []string, env []string, stdout io.Writer, stderr io.Writer) error {
	wrapper, err := FindGradleWrapper(projectDir)
	if err != nil {
		return err
	}

	cmd := exec.Command(wrapper, args...)
	cmd.Dir = projectDir
	cmd.Env = append(os.Environ(), env...)
	cmd.Stdout = stdout
	cmd.Stderr = stderr

	return cmd.Run()
}

// RunGradleStreaming runs Gradle and streams output line-by-line through a callback.
func RunGradleStreaming(projectDir string, args []string, env []string, onLine func(string)) error {
	wrapper, err := FindGradleWrapper(projectDir)
	if err != nil {
		return err
	}

	cmd := exec.Command(wrapper, args...)
	cmd.Dir = projectDir
	cmd.Env = append(os.Environ(), env...)

	pipe, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("failed to create stdout pipe: %w", err)
	}
	cmd.Stderr = cmd.Stdout // merge stderr into stdout

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("failed to start gradle: %w", err)
	}

	scanner := bufio.NewScanner(pipe)
	for scanner.Scan() {
		onLine(scanner.Text())
	}

	return cmd.Wait()
}
