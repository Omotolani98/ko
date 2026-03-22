import { useState } from 'react';
import { useModel } from '@/hooks/useModel';
import EndpointCard from '@/components/EndpointCard';
import RequestBuilder from '@/components/RequestBuilder';
import type { APIEndpoint } from '@/types/model';

interface SelectedEndpoint {
  endpoint: APIEndpoint;
  serviceName: string;
}

export default function ApiExplorerPage() {
  const { model, loading, error } = useModel();
  const [selected, setSelected] = useState<SelectedEndpoint | null>(null);

  if (loading) return <div className="text-gray-500 p-4">Loading...</div>;
  if (error || !model) return <div className="text-red-400 p-4">Failed to load model</div>;

  return (
    <div className="flex h-full gap-6">
      {/* Endpoint list */}
      <div className="w-96 shrink-0 overflow-y-auto space-y-4">
        <h1 className="text-xl font-semibold text-white">API Explorer</h1>
        {model.services.map((service) => (
          <div key={service.name}>
            <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
              {service.name}
            </h2>
            <div className="space-y-1.5">
              {service.apis.map((api) => (
                <EndpointCard
                  key={`${api.method}-${api.path}`}
                  endpoint={api}
                  serviceName={service.name}
                  selected={
                    selected?.endpoint.method === api.method &&
                    selected?.endpoint.path === api.path
                  }
                  onClick={() => setSelected({ endpoint: api, serviceName: service.name })}
                />
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Request builder */}
      <div className="flex-1 overflow-y-auto">
        {selected ? (
          <RequestBuilder
            key={`${selected.endpoint.method}-${selected.endpoint.path}`}
            endpoint={selected.endpoint}
            serviceName={selected.serviceName}
          />
        ) : (
          <div className="flex items-center justify-center h-full text-gray-600">
            Select an endpoint to start testing
          </div>
        )}
      </div>
    </div>
  );
}
