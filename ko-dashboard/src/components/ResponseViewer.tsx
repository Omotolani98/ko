import type { ProxyResponse } from '@/types/model';

function statusColor(status: number): string {
  if (status >= 200 && status < 300) return 'text-green-400';
  if (status >= 400 && status < 500) return 'text-amber-400';
  if (status >= 500) return 'text-red-400';
  return 'text-gray-400';
}

function formatJson(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

interface ResponseViewerProps {
  response: ProxyResponse;
}

export default function ResponseViewer({ response }: ResponseViewerProps) {
  return (
    <div className="border border-gray-800 rounded-lg overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-2 bg-gray-900 border-b border-gray-800">
        <span className={`font-bold text-sm ${statusColor(response.status)}`}>
          {response.status || 'Error'}
        </span>
        <span className="text-xs text-gray-500">{response.duration_ms}ms</span>
      </div>
      <pre className="p-4 text-sm text-gray-300 font-mono overflow-x-auto bg-gray-950 max-h-96 overflow-y-auto">
        {formatJson(response.body)}
      </pre>
    </div>
  );
}
