export interface AppModel {
  schema: string;
  version: string;
  app: string;
  generated_at: string;
  services: ServiceModel[];
  pubsub_topics: PubSubTopicModel[];
  databases: DatabaseRef[];
  service_dependencies: ServiceDependency[];
}

export interface ServiceModel {
  name: string;
  class_name: string;
  package_name: string;
  apis: APIEndpoint[];
  databases: DatabaseModel[];
  publishes: string[];
  subscribes: string[];
  caches: CacheModel[];
  cron_jobs: CronJobModel[];
  secrets: SecretModel[];
  buckets: BucketModel[];
}

export interface APIEndpoint {
  name: string;
  method: string;
  path: string;
  auth: boolean;
  permissions: string[];
  expose: boolean;
  request_type: TypeInfo | null;
  response_type: TypeInfo | null;
  javadoc: string | null;
}

export interface TypeInfo {
  class_name: string;
  fields: FieldInfo[];
}

export interface FieldInfo {
  name: string;
  type: string;
  required: boolean;
}

export interface DatabaseModel {
  name: string;
  migrations: string;
}

export interface CacheModel {
  name: string;
  key_type: string;
  ttl: number;
}

export interface CronJobModel {
  name: string;
  schedule: string;
  method: string;
}

export interface PubSubTopicModel {
  name: string;
  delivery: string;
  message_type: TypeInfo | null;
  publishers: string[];
  subscribers: SubscriberModel[];
}

export interface SubscriberModel {
  service: string;
  subscription: string;
}

export interface SecretModel {
  name: string;
}

export interface BucketModel {
  name: string;
  public_read: boolean;
}

export interface DatabaseRef {
  name: string;
  migrations: string;
}

export interface ServiceDependency {
  from: string;
  to: string;
  type: string;
  topic?: string;
}

export interface HealthResponse {
  status: string;
  app_port: number;
  uptime: string;
}

export interface ProxyResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
  duration_ms: number;
}

export interface TraceSummary {
  trace_id: string;
  root_service: string;
  root_operation: string;
  start_time_ms: number;
  duration_ms: number;
  status: string;
  span_count: number;
}

export interface Trace {
  trace_id: string;
  root_service: string;
  root_operation: string;
  start_time_ms: number;
  duration_ms: number;
  status: string;
  span_count: number;
  spans: Span[];
}

export interface Span {
  trace_id: string;
  span_id: string;
  parent_span_id?: string;
  service: string;
  operation: string;
  kind: 'API' | 'DATABASE' | 'PUBSUB_PUBLISH' | 'PUBSUB_SUBSCRIBE' | 'SERVICE_CALL';
  start_time_ms: number;
  duration_ms: number;
  status: string;
  attributes?: Record<string, string>;
}
