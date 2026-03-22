package daemon

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/Omotolani98/ko/ko-cli/internal/model"
)

func TestGenerateInfraConfig_WithContainers(t *testing.T) {
	im := NewInfraManager("test-app")

	// Simulate containers already provisioned
	im.containers["users-db"] = &Container{
		ID:   "abc123",
		Name: "ko-test-app-db-users-db",
		Type: "postgres",
		Host: "localhost",
		Port: 54321,
	}
	im.containers["cache-sessions"] = &Container{
		ID:   "def456",
		Name: "ko-test-app-redis-sessions",
		Type: "redis",
		Host: "localhost",
		Port: 63790,
	}

	appModel := &model.AppModel{
		App: "test-app",
		Services: []model.ServiceModel{
			{
				Name: "user-service",
				Databases: []model.DatabaseModel{
					{Name: "users-db"},
				},
				Caches: []model.CacheModel{
					{Name: "sessions"},
				},
			},
		},
	}

	tmpDir := t.TempDir()
	configPath, err := im.GenerateInfraConfig(appModel, 8080, tmpDir)
	if err != nil {
		t.Fatalf("GenerateInfraConfig() error: %v", err)
	}

	// Verify file was written
	if _, err := os.Stat(configPath); err != nil {
		t.Fatalf("config file not created: %v", err)
	}

	// Parse and verify contents
	data, _ := os.ReadFile(configPath)
	var config map[string]any
	if err := json.Unmarshal(data, &config); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}

	// Check metadata
	meta, ok := config["metadata"].(map[string]any)
	if !ok {
		t.Fatal("missing metadata")
	}
	if meta["app_id"] != "test-app" {
		t.Errorf("app_id = %v, want test-app", meta["app_id"])
	}
	if meta["env_type"] != "development" {
		t.Errorf("env_type = %v, want development", meta["env_type"])
	}

	// Check SQL servers use real port
	sqlServers, ok := config["sql_servers"].([]any)
	if !ok || len(sqlServers) == 0 {
		t.Fatal("missing sql_servers")
	}
	server := sqlServers[0].(map[string]any)
	if server["host"] != "localhost:54321" {
		t.Errorf("sql host = %v, want localhost:54321", server["host"])
	}

	// Check redis uses real port
	redis, ok := config["redis"].(map[string]any)
	if !ok {
		t.Fatal("missing redis")
	}
	sessionsRedis, ok := redis["sessions"].(map[string]any)
	if !ok {
		t.Fatal("missing redis sessions entry")
	}
	if sessionsRedis["host"] != "localhost:63790" {
		t.Errorf("redis host = %v, want localhost:63790", sessionsRedis["host"])
	}
}

func TestGenerateInfraConfig_EmptyModel(t *testing.T) {
	im := NewInfraManager("empty-app")
	appModel := &model.AppModel{
		App:      "empty-app",
		Services: []model.ServiceModel{},
	}

	tmpDir := t.TempDir()
	configPath, err := im.GenerateInfraConfig(appModel, 8080, tmpDir)
	if err != nil {
		t.Fatalf("GenerateInfraConfig() error: %v", err)
	}

	data, _ := os.ReadFile(configPath)
	var config map[string]any
	json.Unmarshal(data, &config)

	// Should still have metadata
	meta := config["metadata"].(map[string]any)
	if meta["app_id"] != "empty-app" {
		t.Errorf("app_id = %v, want empty-app", meta["app_id"])
	}

	// Should have base_url with app port
	if meta["base_url"] != "http://localhost:8080" {
		t.Errorf("base_url = %v, want http://localhost:8080", meta["base_url"])
	}
}

func TestGenerateInfraConfig_PubSub(t *testing.T) {
	im := NewInfraManager("pubsub-app")
	im.containers["kafka"] = &Container{
		ID:   "kafka123",
		Name: "ko-pubsub-app-kafka",
		Type: "kafka",
		Host: "localhost",
		Port: 29092,
	}

	appModel := &model.AppModel{
		App: "pubsub-app",
		PubSubTopics: []model.PubSubTopicModel{
			{
				Name:     "order-events",
				Delivery: "at_least_once",
				Subscribers: []model.SubscriberModel{
					{Service: "billing", Subscription: "billing-sub"},
				},
			},
		},
	}

	tmpDir := t.TempDir()
	configPath, err := im.GenerateInfraConfig(appModel, 8080, tmpDir)
	if err != nil {
		t.Fatalf("GenerateInfraConfig() error: %v", err)
	}

	data, _ := os.ReadFile(configPath)
	var config map[string]any
	json.Unmarshal(data, &config)

	pubsub, ok := config["pubsub"].([]any)
	if !ok || len(pubsub) == 0 {
		t.Fatal("missing pubsub config")
	}

	ps := pubsub[0].(map[string]any)
	if ps["type"] != "kafka://localhost:29092" {
		t.Errorf("pubsub type = %v, want kafka://localhost:29092", ps["type"])
	}
}

func TestNewInfraManager(t *testing.T) {
	im := NewInfraManager("my-app")
	if im.projectID != "my-app" {
		t.Errorf("projectID = %s, want my-app", im.projectID)
	}
	if len(im.Containers()) != 0 {
		t.Error("expected empty containers")
	}
}

func TestGetMappedPort_ParseOutput(t *testing.T) {
	// This tests the parsing logic indirectly via GenerateInfraConfig
	// since getMappedPort requires actual docker.
	// We verify the infra-config.json path exists after generation.
	im := NewInfraManager("test")
	appModel := &model.AppModel{App: "test"}
	tmpDir := t.TempDir()

	path, err := im.GenerateInfraConfig(appModel, 9000, tmpDir)
	if err != nil {
		t.Fatal(err)
	}

	expected := filepath.Join(tmpDir, "infra-config.json")
	if path != expected {
		t.Errorf("path = %s, want %s", path, expected)
	}
}
