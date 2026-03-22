import type { AppModel, HealthResponse, ProxyResponse } from '@/types/model';

const BASE = '';

export async function fetchModel(): Promise<AppModel> {
  const res = await fetch(`${BASE}/api/model`);
  if (!res.ok) throw new Error(`Failed to fetch model: ${res.status}`);
  return res.json();
}

export async function fetchHealth(): Promise<HealthResponse> {
  const res = await fetch(`${BASE}/api/health`);
  if (!res.ok) throw new Error(`Failed to fetch health: ${res.status}`);
  return res.json();
}

export async function proxyRequest(
  method: string,
  path: string,
  body?: string,
): Promise<ProxyResponse> {
  const start = performance.now();
  const res = await fetch(`${BASE}/api/proxy${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Ko-Method': method,
    },
    body: body || undefined,
  });

  const duration_ms = Math.round(performance.now() - start);
  const responseBody = await res.text();
  const headers: Record<string, string> = {};
  res.headers.forEach((v, k) => {
    headers[k] = v;
  });

  return {
    status: res.status,
    headers,
    body: responseBody,
    duration_ms,
  };
}
