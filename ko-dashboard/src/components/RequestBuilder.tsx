import { useState, useMemo } from 'react';
import type { APIEndpoint, ProxyResponse } from '@/types/model';
import { proxyRequest } from '@/api/client';
import ResponseViewer from './ResponseViewer';

interface RequestBuilderProps {
  endpoint: APIEndpoint;
  serviceName: string;
}

function extractPathParams(path: string): string[] {
  const matches = path.match(/:([a-zA-Z_]+)/g);
  return matches ? matches.map((m) => m.slice(1)) : [];
}

export default function RequestBuilder({ endpoint }: RequestBuilderProps) {
  const pathParams = useMemo(() => extractPathParams(endpoint.path), [endpoint.path]);
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [body, setBody] = useState(() => {
    if (endpoint.request_type && endpoint.request_type.fields.length > 0) {
      const template: Record<string, string> = {};
      for (const f of endpoint.request_type.fields) {
        template[f.name] = '';
      }
      return JSON.stringify(template, null, 2);
    }
    return '';
  });
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<ProxyResponse | null>(null);

  const hasBody = ['POST', 'PUT', 'PATCH'].includes(endpoint.method);

  const resolvedPath = useMemo(() => {
    let p = endpoint.path;
    for (const param of pathParams) {
      p = p.replace(`:${param}`, paramValues[param] || `:${param}`);
    }
    return p;
  }, [endpoint.path, pathParams, paramValues]);

  async function handleSend() {
    setLoading(true);
    setResponse(null);
    try {
      const res = await proxyRequest(endpoint.method, resolvedPath, hasBody ? body : undefined);
      setResponse(res);
    } catch (e) {
      setResponse({
        status: 0,
        headers: {},
        body: e instanceof Error ? e.message : 'Request failed',
        duration_ms: 0,
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm">
        <span className="px-2 py-1 rounded bg-gray-800 text-gray-300 font-mono">{endpoint.method}</span>
        <span className="text-gray-300 font-mono flex-1 truncate">{resolvedPath}</span>
      </div>

      {endpoint.javadoc && (
        <p className="text-sm text-gray-500">{endpoint.javadoc}</p>
      )}

      {pathParams.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-semibold text-gray-500 uppercase">Path Parameters</h4>
          {pathParams.map((param) => (
            <div key={param} className="flex items-center gap-2">
              <label className="text-sm text-gray-400 w-24 font-mono">{param}</label>
              <input
                type="text"
                value={paramValues[param] || ''}
                onChange={(e) => setParamValues((prev) => ({ ...prev, [param]: e.target.value }))}
                placeholder={`Enter ${param}`}
                className="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200 focus:outline-none focus:border-ko-violet"
              />
            </div>
          ))}
        </div>
      )}

      {hasBody && (
        <div className="space-y-2">
          <h4 className="text-xs font-semibold text-gray-500 uppercase">Request Body</h4>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={6}
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-sm text-gray-200 font-mono focus:outline-none focus:border-ko-violet resize-y"
            placeholder="JSON body..."
          />
        </div>
      )}

      <button
        onClick={() => void handleSend()}
        disabled={loading}
        className="px-4 py-2 rounded-lg bg-ko-violet text-white text-sm font-medium hover:bg-ko-violet/90 disabled:opacity-50 transition-colors"
      >
        {loading ? 'Sending...' : 'Send Request'}
      </button>

      {response && <ResponseViewer response={response} />}
    </div>
  );
}
