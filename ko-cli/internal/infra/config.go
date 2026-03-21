package infra

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/Omotolani98/ko/ko-cli/internal/model"
)

// InfraConfig represents infra-config.json for local development.
type InfraConfig struct {
	Metadata         Metadata                       `json:"metadata"`
	SQLServers       []SQLServer                    `json:"sql_servers"`
	PubSub           []PubSubConfig                 `json:"pubsub"`
	Redis            map[string]interface{}          `json:"redis"`
	ObjectStorage    []ObjectStorageConfig           `json:"object_storage"`
	ServiceDiscovery map[string]ServiceDiscoveryEntry `json:"service_discovery"`
	Secrets          map[string]interface{}          `json:"secrets"`
}

type Metadata struct {
	AppID   string `json:"app_id"`
	EnvName string `json:"env_name"`
	EnvType string `json:"env_type"`
	Cloud   string `json:"cloud"`
	BaseURL string `json:"base_url"`
}

type SQLServer struct {
	Host      string                    `json:"host"`
	Databases map[string]DatabaseConfig `json:"databases"`
}

type DatabaseConfig struct {
	Username       string `json:"username"`
	Password       string `json:"password"`
	MaxConnections int    `json:"max_connections"`
	MinConnections int    `json:"min_connections"`
}

type PubSubConfig struct {
	Type   string                  `json:"type"`
	Topics map[string]TopicConfig  `json:"topics"`
}

type TopicConfig struct {
	Name          string                        `json:"name"`
	Partitions    int                           `json:"partitions"`
	Subscriptions map[string]SubscriptionConfig `json:"subscriptions"`
}

type SubscriptionConfig struct {
	Name string `json:"name"`
}

type ObjectStorageConfig struct {
	Type    string                   `json:"type"`
	Buckets map[string]BucketConfig  `json:"buckets"`
}

type BucketConfig struct {
	Name string `json:"name"`
}

type ServiceDiscoveryEntry struct {
	BaseURL string `json:"base_url"`
}

// GenerateLocalConfig creates an infra-config.json for local development
// based on the application model. Uses in-memory providers and H2 databases.
func GenerateLocalConfig(appModel *model.AppModel, outputDir string) (string, error) {
	config := InfraConfig{
		Metadata: Metadata{
			AppID:   appModel.App,
			EnvName: "local",
			EnvType: "development",
			Cloud:   "local",
			BaseURL: "http://localhost:8080",
		},
		Redis:            map[string]interface{}{},
		ServiceDiscovery: map[string]ServiceDiscoveryEntry{},
		Secrets:          map[string]interface{}{},
	}

	// Collect databases
	dbMap := map[string]bool{}
	for _, svc := range appModel.Services {
		for _, db := range svc.Databases {
			dbMap[db.Name] = true
		}
	}
	if len(dbMap) > 0 {
		dbs := map[string]DatabaseConfig{}
		for name := range dbMap {
			dbs[name] = DatabaseConfig{
				Username:       "sa",
				Password:       "",
				MaxConnections: 10,
				MinConnections: 2,
			}
		}
		config.SQLServers = []SQLServer{{
			Host:      "localhost:mem",
			Databases: dbs,
		}}
	}

	// Collect pub/sub topics
	if len(appModel.PubSubTopics) > 0 {
		topics := map[string]TopicConfig{}
		for _, t := range appModel.PubSubTopics {
			subs := map[string]SubscriptionConfig{}
			for _, sub := range t.Subscribers {
				subs[sub.Subscription] = SubscriptionConfig{Name: sub.Subscription}
			}
			topics[t.Name] = TopicConfig{
				Name:          t.Name,
				Partitions:    1,
				Subscriptions: subs,
			}
		}
		config.PubSub = []PubSubConfig{{
			Type:   "in_memory",
			Topics: topics,
		}}
	}

	// Collect buckets
	bucketMap := map[string]bool{}
	for _, svc := range appModel.Services {
		for _, b := range svc.Buckets {
			bucketMap[b.Name] = true
		}
	}
	if len(bucketMap) > 0 {
		buckets := map[string]BucketConfig{}
		for name := range bucketMap {
			buckets[name] = BucketConfig{Name: name}
		}
		config.ObjectStorage = []ObjectStorageConfig{{
			Type:    "local",
			Buckets: buckets,
		}}
	}

	// Collect secrets (use empty placeholder for local dev)
	for _, svc := range appModel.Services {
		for _, s := range svc.Secrets {
			config.Secrets[s.Name] = "ko-local-secret-" + s.Name
		}
	}

	// Write to file
	data, err := json.MarshalIndent(config, "", "  ")
	if err != nil {
		return "", fmt.Errorf("failed to marshal infra config: %w", err)
	}

	outPath := filepath.Join(outputDir, "infra-config.json")
	if err := os.WriteFile(outPath, data, 0644); err != nil {
		return "", fmt.Errorf("failed to write infra config: %w", err)
	}

	return outPath, nil
}

func toEnvVar(name string) string {
	result := make([]byte, 0, len(name))
	for _, c := range name {
		if c == '-' {
			result = append(result, '_')
		} else if c >= 'a' && c <= 'z' {
			result = append(result, byte(c-32))
		} else {
			result = append(result, byte(c))
		}
	}
	return string(result)
}
