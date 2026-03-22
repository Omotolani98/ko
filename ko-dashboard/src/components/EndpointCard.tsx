import type { APIEndpoint } from '@/types/model';

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-green-500/20 text-green-400',
  POST: 'bg-blue-500/20 text-blue-400',
  PUT: 'bg-orange-500/20 text-orange-400',
  DELETE: 'bg-red-500/20 text-red-400',
  PATCH: 'bg-purple-500/20 text-purple-400',
};

interface EndpointCardProps {
  endpoint: APIEndpoint;
  serviceName: string;
  selected: boolean;
  onClick: () => void;
}

export default function EndpointCard({ endpoint, serviceName, selected, onClick }: EndpointCardProps) {
  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-3 py-2.5 rounded-lg border transition-colors ${
        selected
          ? 'bg-ko-violet/10 border-ko-violet/30'
          : 'bg-gray-900 border-gray-800 hover:border-gray-700'
      }`}
    >
      <div className="flex items-center gap-2 mb-1">
        <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded ${METHOD_COLORS[endpoint.method] ?? ''}`}>
          {endpoint.method}
        </span>
        <span className="text-sm text-gray-200 font-mono truncate">{endpoint.path}</span>
        {endpoint.auth && (
          <span className="text-[10px] px-1 rounded bg-amber-500/20 text-amber-400 ml-auto shrink-0">auth</span>
        )}
      </div>
      <div className="text-xs text-gray-500">
        {serviceName} &middot; {endpoint.name}
      </div>
      {endpoint.javadoc && (
        <div className="text-xs text-gray-600 mt-1 truncate">{endpoint.javadoc}</div>
      )}
    </button>
  );
}
