package daemon

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/Omotolani98/ko/ko-cli/internal/model"
)

// Container represents a running Docker container managed by Ko.
type Container struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Type      string `json:"type"`
	Host      string `json:"host"`
	Port      int    `json:"port"`
	Status    string `json:"status"`
	StartedAt time.Time `json:"started_at"`
}

// InfraManager manages Docker containers for local development infrastructure.
type InfraManager struct {
	containers map[string]*Container // keyed by logical name
	projectID  string
	mu         sync.RWMutex
}

// NewInfraManager creates a new infrastructure manager.
func NewInfraManager(projectID string) *InfraManager {
	return &InfraManager{
		containers: make(map[string]*Container),
		projectID:  projectID,
	}
}

// DockerAvailable checks if the docker CLI is available and the daemon is running.
func DockerAvailable() bool {
	cmd := exec.Command("docker", "info")
	cmd.Stdout = nil
	cmd.Stderr = nil
	return cmd.Run() == nil
}

// Provision starts containers for all infrastructure declared in the app model.
func (im *InfraManager) Provision(appModel *model.AppModel) error {
	im.mu.Lock()
	defer im.mu.Unlock()

	// Collect unique databases
	dbNames := map[string]bool{}
	for _, svc := range appModel.Services {
		for _, db := range svc.Databases {
			dbNames[db.Name] = true
		}
	}

	// Start Postgres for databases
	for name := range dbNames {
		if _, exists := im.containers[name]; exists {
			continue
		}
		c, err := im.startPostgres(name)
		if err != nil {
			return fmt.Errorf("failed to start postgres for %s: %w", name, err)
		}
		im.containers[name] = c
	}

	// Collect caches
	for _, svc := range appModel.Services {
		for _, cache := range svc.Caches {
			if _, exists := im.containers["cache-"+cache.Name]; exists {
				continue
			}
			c, err := im.startRedis(cache.Name)
			if err != nil {
				return fmt.Errorf("failed to start redis for cache %s: %w", cache.Name, err)
			}
			im.containers["cache-"+cache.Name] = c
		}
	}

	// Start Kafka if pubsub topics exist
	if len(appModel.PubSubTopics) > 0 {
		if _, exists := im.containers["kafka"]; !exists {
			c, err := im.startKafka()
			if err != nil {
				return fmt.Errorf("failed to start kafka: %w", err)
			}
			im.containers["kafka"] = c
		}
	}

	// Start MinIO if buckets exist
	bucketExists := false
	for _, svc := range appModel.Services {
		if len(svc.Buckets) > 0 {
			bucketExists = true
			break
		}
	}
	if bucketExists {
		if _, exists := im.containers["minio"]; !exists {
			c, err := im.startMinIO()
			if err != nil {
				return fmt.Errorf("failed to start minio: %w", err)
			}
			im.containers["minio"] = c
		}
	}

	return nil
}

// Containers returns a snapshot of all running containers.
func (im *InfraManager) Containers() []Container {
	im.mu.RLock()
	defer im.mu.RUnlock()
	result := make([]Container, 0, len(im.containers))
	for _, c := range im.containers {
		result = append(result, *c)
	}
	return result
}

// Shutdown stops and removes all managed containers.
func (im *InfraManager) Shutdown(ctx context.Context) error {
	im.mu.Lock()
	defer im.mu.Unlock()

	var errs []string
	for name, c := range im.containers {
		cmd := exec.CommandContext(ctx, "docker", "rm", "-f", c.ID)
		if err := cmd.Run(); err != nil {
			errs = append(errs, fmt.Sprintf("%s: %v", name, err))
		}
	}
	im.containers = make(map[string]*Container)

	if len(errs) > 0 {
		return fmt.Errorf("errors stopping containers: %s", strings.Join(errs, "; "))
	}
	return nil
}

// GenerateInfraConfig creates infra-config.json using real container endpoints.
func (im *InfraManager) GenerateInfraConfig(appModel *model.AppModel, appPort int, outputDir string) (string, error) {
	im.mu.RLock()
	defer im.mu.RUnlock()

	config := map[string]interface{}{
		"metadata": map[string]interface{}{
			"app_id":   appModel.App,
			"env_name": "local",
			"env_type": "development",
			"cloud":    "local",
			"base_url": fmt.Sprintf("http://localhost:%d", appPort),
		},
	}

	// SQL servers from real Postgres containers
	dbMap := map[string]bool{}
	for _, svc := range appModel.Services {
		for _, db := range svc.Databases {
			dbMap[db.Name] = true
		}
	}
	if len(dbMap) > 0 {
		dbs := map[string]interface{}{}
		var host string
		for name := range dbMap {
			c, ok := im.containers[name]
			if !ok {
				continue
			}
			host = fmt.Sprintf("localhost:%d", c.Port)
			dbs[name] = map[string]interface{}{
				"username":        "ko",
				"password":        "ko",
				"max_connections": 10,
				"min_connections": 2,
			}
		}
		config["sql_servers"] = []map[string]interface{}{{
			"host":      host,
			"databases": dbs,
		}}
	}

	// PubSub from Kafka container
	if len(appModel.PubSubTopics) > 0 {
		topics := map[string]interface{}{}
		for _, t := range appModel.PubSubTopics {
			subs := map[string]interface{}{}
			for _, sub := range t.Subscribers {
				subs[sub.Subscription] = map[string]interface{}{"name": sub.Subscription}
			}
			topics[t.Name] = map[string]interface{}{
				"name":          t.Name,
				"partitions":    1,
				"subscriptions": subs,
			}
		}
		pubsubType := "in_memory"
		if c, ok := im.containers["kafka"]; ok {
			pubsubType = fmt.Sprintf("kafka://localhost:%d", c.Port)
		}
		config["pubsub"] = []map[string]interface{}{{
			"type":   pubsubType,
			"topics": topics,
		}}
	}

	// Redis from container
	redisConfig := map[string]interface{}{}
	for _, svc := range appModel.Services {
		for _, cache := range svc.Caches {
			if c, ok := im.containers["cache-"+cache.Name]; ok {
				redisConfig[cache.Name] = map[string]interface{}{
					"host": fmt.Sprintf("localhost:%d", c.Port),
				}
			}
		}
	}
	config["redis"] = redisConfig

	// Object storage from MinIO
	buckets := map[string]interface{}{}
	for _, svc := range appModel.Services {
		for _, b := range svc.Buckets {
			buckets[b.Name] = map[string]interface{}{"name": b.Name}
		}
	}
	if len(buckets) > 0 {
		storageType := "local"
		if c, ok := im.containers["minio"]; ok {
			storageType = fmt.Sprintf("s3://localhost:%d", c.Port)
		}
		config["object_storage"] = []map[string]interface{}{{
			"type":    storageType,
			"buckets": buckets,
		}}
	}

	// Service discovery
	config["service_discovery"] = map[string]interface{}{}

	// Secrets
	secrets := map[string]interface{}{}
	for _, svc := range appModel.Services {
		for _, s := range svc.Secrets {
			secrets[s.Name] = "ko-local-secret-" + s.Name
		}
	}
	config["secrets"] = secrets

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

func (im *InfraManager) startPostgres(dbName string) (*Container, error) {
	containerName := fmt.Sprintf("ko-%s-db-%s", im.projectID, dbName)

	// Remove existing container if any
	exec.Command("docker", "rm", "-f", containerName).Run()

	cmd := exec.Command("docker", "run", "-d",
		"--name", containerName,
		"--label", fmt.Sprintf("ko.project=%s", im.projectID),
		"-e", fmt.Sprintf("POSTGRES_DB=%s", dbName),
		"-e", "POSTGRES_USER=ko",
		"-e", "POSTGRES_PASSWORD=ko",
		"-p", "0:5432",
		"postgres:16-alpine",
	)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("docker run failed: %w", err)
	}
	containerID := strings.TrimSpace(string(out))[:12]

	port, err := getMappedPort(containerName, "5432")
	if err != nil {
		return nil, err
	}

	// Wait for Postgres to be ready
	if err := waitForPort("localhost", port, 30*time.Second); err != nil {
		return nil, fmt.Errorf("postgres not ready: %w", err)
	}

	return &Container{
		ID:        containerID,
		Name:      containerName,
		Type:      "postgres",
		Host:      "localhost",
		Port:      port,
		Status:    "running",
		StartedAt: time.Now(),
	}, nil
}

func (im *InfraManager) startRedis(cacheName string) (*Container, error) {
	containerName := fmt.Sprintf("ko-%s-redis-%s", im.projectID, cacheName)

	exec.Command("docker", "rm", "-f", containerName).Run()

	cmd := exec.Command("docker", "run", "-d",
		"--name", containerName,
		"--label", fmt.Sprintf("ko.project=%s", im.projectID),
		"-p", "0:6379",
		"redis:7-alpine",
	)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("docker run failed: %w", err)
	}
	containerID := strings.TrimSpace(string(out))[:12]

	port, err := getMappedPort(containerName, "6379")
	if err != nil {
		return nil, err
	}

	if err := waitForPort("localhost", port, 15*time.Second); err != nil {
		return nil, fmt.Errorf("redis not ready: %w", err)
	}

	return &Container{
		ID:        containerID,
		Name:      containerName,
		Type:      "redis",
		Host:      "localhost",
		Port:      port,
		Status:    "running",
		StartedAt: time.Now(),
	}, nil
}

func (im *InfraManager) startKafka() (*Container, error) {
	containerName := fmt.Sprintf("ko-%s-kafka", im.projectID)

	exec.Command("docker", "rm", "-f", containerName).Run()

	cmd := exec.Command("docker", "run", "-d",
		"--name", containerName,
		"--label", fmt.Sprintf("ko.project=%s", im.projectID),
		"-p", "0:9092",
		"-e", "KAFKA_NODE_ID=1",
		"-e", "KAFKA_PROCESS_ROLES=broker,controller",
		"-e", "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093",
		"-e", "KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER",
		"-e", "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
		"-e", "KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093",
		"-e", "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
		"-e", "CLUSTER_ID=ko-local-cluster",
		"apache/kafka:3.7.0",
	)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("docker run failed: %w", err)
	}
	containerID := strings.TrimSpace(string(out))[:12]

	port, err := getMappedPort(containerName, "9092")
	if err != nil {
		return nil, err
	}

	if err := waitForPort("localhost", port, 30*time.Second); err != nil {
		return nil, fmt.Errorf("kafka not ready: %w", err)
	}

	return &Container{
		ID:        containerID,
		Name:      containerName,
		Type:      "kafka",
		Host:      "localhost",
		Port:      port,
		Status:    "running",
		StartedAt: time.Now(),
	}, nil
}

func (im *InfraManager) startMinIO() (*Container, error) {
	containerName := fmt.Sprintf("ko-%s-minio", im.projectID)

	exec.Command("docker", "rm", "-f", containerName).Run()

	cmd := exec.Command("docker", "run", "-d",
		"--name", containerName,
		"--label", fmt.Sprintf("ko.project=%s", im.projectID),
		"-e", "MINIO_ROOT_USER=ko",
		"-e", "MINIO_ROOT_PASSWORD=ko-secret",
		"-p", "0:9000",
		"minio/minio:latest", "server", "/data",
	)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("docker run failed: %w", err)
	}
	containerID := strings.TrimSpace(string(out))[:12]

	port, err := getMappedPort(containerName, "9000")
	if err != nil {
		return nil, err
	}

	if err := waitForPort("localhost", port, 15*time.Second); err != nil {
		return nil, fmt.Errorf("minio not ready: %w", err)
	}

	return &Container{
		ID:        containerID,
		Name:      containerName,
		Type:      "minio",
		Host:      "localhost",
		Port:      port,
		Status:    "running",
		StartedAt: time.Now(),
	}, nil
}

// getMappedPort retrieves the host port mapped to a container's internal port.
func getMappedPort(containerName, internalPort string) (int, error) {
	cmd := exec.Command("docker", "port", containerName, internalPort)
	out, err := cmd.Output()
	if err != nil {
		return 0, fmt.Errorf("failed to get port mapping: %w", err)
	}

	// Output format: "0.0.0.0:12345\n" or ":::12345\n"
	lines := strings.Split(strings.TrimSpace(string(out)), "\n")
	for _, line := range lines {
		parts := strings.Split(line, ":")
		if len(parts) >= 2 {
			portStr := parts[len(parts)-1]
			var port int
			if _, err := fmt.Sscanf(portStr, "%d", &port); err == nil {
				return port, nil
			}
		}
	}

	return 0, fmt.Errorf("could not parse port from: %s", string(out))
}

// waitForPort polls a TCP port until it accepts connections or the timeout expires.
func waitForPort(host string, port int, timeout time.Duration) error {
	addr := fmt.Sprintf("%s:%d", host, port)
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		conn, err := net.DialTimeout("tcp", addr, 500*time.Millisecond)
		if err == nil {
			conn.Close()
			return nil
		}
		time.Sleep(250 * time.Millisecond)
	}
	return fmt.Errorf("timeout waiting for %s", addr)
}
