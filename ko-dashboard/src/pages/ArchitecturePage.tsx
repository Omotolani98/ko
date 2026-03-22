import { useState } from 'react';
import { useModel } from '@/hooks/useModel';
import ServiceGraph from '@/components/ServiceGraph';
import ServiceDetail from '@/components/ServiceDetail';

export default function ArchitecturePage() {
  const { model, loading, error } = useModel();
  const [selectedService, setSelectedService] = useState<string | null>(null);

  if (loading) return <LoadingState />;
  if (error) return <ErrorState error={error} />;
  if (!model) return null;

  return (
    <div className="flex h-full">
      <div className="flex-1 relative">
        <div className="absolute top-0 left-0 right-0 z-10 p-4">
          <h1 className="text-xl font-semibold text-white">Architecture</h1>
          <p className="text-sm text-gray-500">
            {model.services.length} service(s) &middot; {model.pubsub_topics.length} topic(s)
          </p>
        </div>
        <ServiceGraph
          model={model}
          selectedService={selectedService}
          onSelectService={setSelectedService}
        />
      </div>
      {selectedService && (
        <ServiceDetail
          serviceName={selectedService}
          model={model}
          onClose={() => setSelectedService(null)}
        />
      )}
    </div>
  );
}

function LoadingState() {
  return (
    <div className="flex items-center justify-center h-full">
      <div className="text-gray-500">Loading model...</div>
    </div>
  );
}

function ErrorState({ error }: { error: Error }) {
  return (
    <div className="flex items-center justify-center h-full">
      <div className="text-center">
        <p className="text-red-400 mb-2">Failed to load model</p>
        <p className="text-sm text-gray-500">{error.message}</p>
      </div>
    </div>
  );
}
