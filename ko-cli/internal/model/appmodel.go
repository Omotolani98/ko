package model

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

// AppModel represents ko-app-model.json.
type AppModel struct {
	Schema      string              `json:"$schema"`
	Version     string              `json:"version"`
	App         string              `json:"app"`
	GeneratedAt string              `json:"generated_at"`
	Services    []ServiceModel      `json:"services"`
	PubSubTopics []PubSubTopicModel `json:"pubsub_topics"`
	Databases   []DatabaseRef       `json:"databases"`
	ServiceDeps []ServiceDependency `json:"service_dependencies"`
}

type ServiceModel struct {
	Name        string             `json:"name"`
	Class       string             `json:"class_name"`
	Package     string             `json:"package_name"`
	APIs        []APIEndpoint      `json:"apis"`
	Databases   []DatabaseModel    `json:"databases"`
	Publishes   []string           `json:"publishes"`
	Subscribes  []string           `json:"subscribes"`
	Caches      []CacheModel       `json:"caches"`
	CronJobs    []CronJobModel     `json:"cron_jobs"`
	Secrets     []SecretModel      `json:"secrets"`
	Buckets     []BucketModel      `json:"buckets"`
}

type APIEndpoint struct {
	Name         string   `json:"name"`
	Method       string   `json:"method"`
	Path         string   `json:"path"`
	Auth         bool     `json:"auth"`
	Permissions  []string `json:"permissions"`
}

type DatabaseModel struct {
	Name       string `json:"name"`
	Migrations string `json:"migrations"`
}

type CacheModel struct {
	Name    string `json:"name"`
	KeyType string `json:"key_type"`
	TTL     int64  `json:"ttl"`
}

type CronJobModel struct {
	Name     string `json:"name"`
	Schedule string `json:"schedule"`
	Method   string `json:"method"`
}

type PubSubTopicModel struct {
	Name        string            `json:"name"`
	Delivery    string            `json:"delivery"`
	Publishers  []string          `json:"publishers"`
	Subscribers []SubscriberModel `json:"subscribers"`
}

type SubscriberModel struct {
	Service      string `json:"service"`
	Subscription string `json:"subscription"`
	Topic        string `json:"topic"`
}

type SecretModel struct {
	Name string `json:"name"`
}

type BucketModel struct {
	Name       string `json:"name"`
	PublicRead bool   `json:"public_read"`
}

type DatabaseRef struct {
	Name     string   `json:"name"`
	Type     string   `json:"type"`
	Services []string `json:"services"`
}

type ServiceDependency struct {
	From  string `json:"from"`
	To    string `json:"to"`
	Type  string `json:"type"`
	Topic string `json:"topic,omitempty"`
}

// LoadAppModel reads and parses ko-app-model.json from the build output.
func LoadAppModel(projectDir string) (*AppModel, error) {
	// Search common locations
	candidates := []string{
		filepath.Join(projectDir, "build", "resources", "main", "ko-app-model.json"),
		filepath.Join(projectDir, "build", "classes", "java", "main", "ko-app-model.json"),
		filepath.Join(projectDir, "ko-app-model.json"),
	}

	for _, path := range candidates {
		data, err := os.ReadFile(path)
		if err == nil {
			var model AppModel
			if err := json.Unmarshal(data, &model); err != nil {
				return nil, fmt.Errorf("failed to parse %s: %w", path, err)
			}
			return &model, nil
		}
	}

	return nil, fmt.Errorf("ko-app-model.json not found in %s (did you run ./gradlew build?)", projectDir)
}

// Summary returns a human-readable summary of the app model.
func (m *AppModel) Summary() string {
	apis := 0
	dbs := 0
	caches := 0
	crons := 0
	buckets := 0
	secrets := 0
	for _, s := range m.Services {
		apis += len(s.APIs)
		dbs += len(s.Databases)
		caches += len(s.Caches)
		crons += len(s.CronJobs)
		buckets += len(s.Buckets)
		secrets += len(s.Secrets)
	}
	return fmt.Sprintf("%d service(s), %d API(s), %d database(s), %d topic(s), %d cache(s), %d cron(s), %d bucket(s), %d secret(s)",
		len(m.Services), apis, dbs, len(m.PubSubTopics), caches, crons, buckets, secrets)
}
