package scaffold

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// Options holds the configuration for scaffolding a new Ko project.
type Options struct {
	Name       string // project/app name (kebab-case)
	Package    string // Java package (e.g., com.example.myapp)
	OutputDir  string // parent directory to create project in
	KoVersion  string // Ko framework version
	SpringVersion string // Spring Boot version
}

// Run creates a new Ko project with the given options.
func Run(opts Options) (string, error) {
	projectDir := filepath.Join(opts.OutputDir, opts.Name)

	if _, err := os.Stat(projectDir); err == nil {
		return "", fmt.Errorf("directory %s already exists", projectDir)
	}

	// Create directory structure
	packagePath := strings.ReplaceAll(opts.Package, ".", "/")
	dirs := []string{
		filepath.Join("src", "main", "java", packagePath),
		filepath.Join("src", "main", "resources"),
		filepath.Join("src", "test", "java", packagePath),
		filepath.Join("gradle"),
	}

	for _, dir := range dirs {
		if err := os.MkdirAll(filepath.Join(projectDir, dir), 0755); err != nil {
			return "", fmt.Errorf("failed to create directory %s: %w", dir, err)
		}
	}

	// Generate files
	files := map[string]string{
		"settings.gradle.kts":                        settingsGradle(opts),
		"build.gradle.kts":                           buildGradle(opts),
		"gradle.properties":                          gradleProperties(opts),
		"gradle/libs.versions.toml":                  versionCatalog(opts),
		".gitignore":                                 gitignore(),
		".sdkmanrc":                                  sdkmanrc(),
		srcMain(packagePath, "Application.java"):     applicationJava(opts),
		srcMain(packagePath, "HelloService.java"):    helloServiceJava(opts),
		srcMain(packagePath, "HelloResponse.java"):   helloResponseJava(opts),
		"src/main/resources/application.properties":  applicationProperties(opts),
		srcTest(packagePath, "HelloServiceTest.java"): helloServiceTestJava(opts),
	}

	for path, content := range files {
		fullPath := filepath.Join(projectDir, path)
		if err := os.WriteFile(fullPath, []byte(content), 0644); err != nil {
			return "", fmt.Errorf("failed to write %s: %w", path, err)
		}
	}

	// Generate Gradle wrapper
	if err := generateGradleWrapper(projectDir); err != nil {
		return "", fmt.Errorf("failed to generate Gradle wrapper: %w", err)
	}

	return projectDir, nil
}

func srcMain(packagePath, file string) string {
	return filepath.Join("src", "main", "java", packagePath, file)
}

func srcTest(packagePath, file string) string {
	return filepath.Join("src", "test", "java", packagePath, file)
}

// ToPackage converts a kebab-case app name to a Java package segment.
// e.g., "my-cool-app" → "mycoolapp"
func ToPackage(name string) string {
	return strings.ReplaceAll(strings.ToLower(name), "-", "")
}

// ToPascalCase converts kebab-case to PascalCase.
// e.g., "my-cool-app" → "MyCoolApp"
func ToPascalCase(name string) string {
	parts := strings.Split(name, "-")
	var result strings.Builder
	for _, part := range parts {
		if len(part) > 0 {
			result.WriteString(strings.ToUpper(part[:1]) + part[1:])
		}
	}
	return result.String()
}

const gradleVersion = "8.14.1"

func generateGradleWrapper(projectDir string) error {
	// Try using system gradle to generate the wrapper with the correct version
	if gradlePath, err := exec.LookPath("gradle"); err == nil {
		cmd := exec.Command(gradlePath, "wrapper", "--gradle-version", gradleVersion)
		cmd.Dir = projectDir
		if err := cmd.Run(); err == nil {
			return nil
		}
	}

	// Fallback: download wrapper jar and write config manually
	wrapperDir := filepath.Join(projectDir, "gradle", "wrapper")
	if err := os.MkdirAll(wrapperDir, 0755); err != nil {
		return err
	}

	// Download gradle-wrapper.jar
	jarPath := filepath.Join(wrapperDir, "gradle-wrapper.jar")
	jarURL := fmt.Sprintf("https://raw.githubusercontent.com/gradle/gradle/v%s/gradle/wrapper/gradle-wrapper.jar", gradleVersion)
	if err := downloadFile(jarPath, jarURL); err != nil {
		// Try alternative: services.gradle.org wrapper endpoint
		altURL := fmt.Sprintf("https://services.gradle.org/distributions/gradle-%s-wrapper.jar", gradleVersion)
		if err2 := downloadFile(jarPath, altURL); err2 != nil {
			return fmt.Errorf("could not download gradle-wrapper.jar: %w (also tried: %v)", err, err2)
		}
	}

	// Write wrapper properties
	props := fmt.Sprintf(`distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-%s-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`, gradleVersion)

	if err := os.WriteFile(filepath.Join(wrapperDir, "gradle-wrapper.properties"), []byte(props), 0644); err != nil {
		return err
	}

	// Write gradlew shell script
	if err := os.WriteFile(filepath.Join(projectDir, "gradlew"), []byte(gradlewScript()), 0755); err != nil {
		return err
	}

	return nil
}

func downloadFile(path, url string) error {
	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %d from %s", resp.StatusCode, url)
	}

	out, err := os.Create(path)
	if err != nil {
		return err
	}
	defer out.Close()

	_, err = io.Copy(out, resp.Body)
	return err
}

// --- Template functions ---

func settingsGradle(opts Options) string {
	return fmt.Sprintf(`pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "%s"
`, opts.Name)
}

func buildGradle(opts Options) string {
	return fmt.Sprintf(`plugins {
    id("dev.ko") version "%s"
    id("org.springframework.boot") version "%s"
}

group = "%s"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

ko {
    appName = "%s"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation("dev.ko:ko-test:%s")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

springBoot {
    mainClass.set("%s.Application")
}
`, opts.KoVersion, opts.SpringVersion, opts.Package, opts.Name, opts.KoVersion, opts.Package)
}

func gradleProperties(_ Options) string {
	return `org.gradle.parallel=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx2g
`
}

func versionCatalog(opts Options) string {
	return fmt.Sprintf(`[versions]
ko = "%s"
spring-boot = "%s"

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
ko = { id = "dev.ko", version.ref = "ko" }
`, opts.KoVersion, opts.SpringVersion)
}

func applicationJava(opts Options) string {
	return fmt.Sprintf(`package %s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
`, opts.Package)
}

func helloServiceJava(opts Options) string {
	return fmt.Sprintf(`package %s;

import dev.ko.annotations.KoAPI;
import dev.ko.annotations.KoService;

@KoService("%s")
public class HelloService {

    @KoAPI(method = "GET", path = "/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello from Kọ́!");
    }

    @KoAPI(method = "GET", path = "/hello/:name")
    public HelloResponse helloName(@dev.ko.annotations.PathParam("name") String name) {
        return new HelloResponse("Hello, " + name + "!");
    }
}
`, opts.Package, opts.Name+"-service")
}

func helloResponseJava(opts Options) string {
	return fmt.Sprintf(`package %s;

public record HelloResponse(String message) {}
`, opts.Package)
}

func applicationProperties(_ Options) string {
	return `spring.application.name=${ko.app.name:ko-app}
server.port=${SERVER_PORT:8080}

# Ko framework will auto-configure infrastructure based on infra-config.json
`
}

func helloServiceTestJava(opts Options) string {
	pascal := ToPascalCase(opts.Name)
	_ = pascal
	return fmt.Sprintf(`package %s;

import dev.ko.test.KoTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@KoTestApp
class HelloServiceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void hello_returnsGreeting() {
        var response = restTemplate.getForObject("/hello", HelloResponse.class);
        assertThat(response.message()).isEqualTo("Hello from Kọ́!");
    }

    @Test
    void helloName_returnsPersonalizedGreeting() {
        var response = restTemplate.getForObject("/hello/World", HelloResponse.class);
        assertThat(response.message()).isEqualTo("Hello, World!");
    }
}
`, opts.Package)
}

func sdkmanrc() string {
	return `# https://sdkman.io/usage/#env
java=21.0.6-tem
gradle=8.14.1
`
}

func gitignore() string {
	return `.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
.idea/
*.iml
*.ipr
*.iws
.settings/
.classpath
.project
.DS_Store
infra-config.json
`
}

func gradlewScript() string {
	return `#!/bin/sh

#
# Gradle start up script for POSIX generated by Ko CLI
#

# Attempt to set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ]; then
    if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    fi
fi

# Resolve the script directory
APP_DIR=$(cd "$(dirname "$0")" && pwd)

# Download wrapper jar if missing
WRAPPER_JAR="$APP_DIR/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_DIR/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper..."
    DIST_URL=$(grep 'distributionUrl' "$WRAPPER_PROPS" | sed 's/.*=//;s/\\//g')
    GRADLE_VERSION=$(echo "$DIST_URL" | sed 's/.*gradle-//;s/-bin.zip//')
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    mkdir -p "$(dirname "$WRAPPER_JAR")"
    if command -v curl > /dev/null 2>&1; then
        curl -sL -o "$WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "ERROR: Cannot download gradle-wrapper.jar. Install curl or wget." >&2
        exit 1
    fi
fi

exec java $JAVA_OPTS \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
`
}
