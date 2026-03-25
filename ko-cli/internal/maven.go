package internal

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

const (
	mavenSearchURL  = "https://central.sonatype.com/solrsearch/select?q=g:io.github.omotolani98+AND+a:ko-annotations&core=gav&rows=1&wt=json&sort=version+desc"
	fallbackVersion = "0.5.1"
)

type mavenResponse struct {
	Response struct {
		Docs []struct {
			Version string `json:"v"`
		} `json:"docs"`
	} `json:"response"`
}

// FetchLatestKoVersion queries Maven Central for the latest published version
// of the Ko framework. Returns the fallback version on any error.
func FetchLatestKoVersion() string {
	client := &http.Client{Timeout: 5 * time.Second}

	resp, err := client.Get(mavenSearchURL)
	if err != nil {
		return fallbackVersion
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fallbackVersion
	}

	var result mavenResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return fallbackVersion
	}

	if len(result.Response.Docs) == 0 || result.Response.Docs[0].Version == "" {
		return fallbackVersion
	}

	return result.Response.Docs[0].Version
}

// FallbackKoVersion returns the hardcoded fallback version.
func FallbackKoVersion() string {
	return fallbackVersion
}

// FetchLatestKoVersionOrError is like FetchLatestKoVersion but returns an error
// instead of silently falling back.
func FetchLatestKoVersionOrError() (string, error) {
	client := &http.Client{Timeout: 5 * time.Second}

	resp, err := client.Get(mavenSearchURL)
	if err != nil {
		return "", fmt.Errorf("failed to reach Maven Central: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("Maven Central returned HTTP %d", resp.StatusCode)
	}

	var result mavenResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("failed to parse Maven Central response: %w", err)
	}

	if len(result.Response.Docs) == 0 || result.Response.Docs[0].Version == "" {
		return "", fmt.Errorf("no published version found on Maven Central")
	}

	return result.Response.Docs[0].Version, nil
}
