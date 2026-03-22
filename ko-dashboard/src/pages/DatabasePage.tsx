import { useModel } from '@/hooks/useModel';
import EmptyState from '@/components/EmptyState';
import InfraBadge from '@/components/InfraBadge';

export default function DatabasePage() {
  const { model, loading, error } = useModel();

  if (loading) return <div className="text-gray-500 p-4">Loading...</div>;
  if (error || !model) return <div className="text-red-400 p-4">Failed to load model</div>;

  const databases = model.databases;

  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Databases</h1>

      {databases.length > 0 ? (
        <div className="space-y-3 mb-8">
          {databases.map((db) => {
            const services = model.services.filter((s) => s.databases.some((d) => d.name === db.name));
            return (
              <div key={db.name} className="p-4 rounded-lg border border-gray-800 bg-gray-900">
                <div className="flex items-center gap-3 mb-2">
                  <InfraBadge type="db" label="database" />
                  <span className="text-base font-semibold text-white">{db.name}</span>
                </div>
                <div className="text-xs text-gray-500">
                  Used by: {services.map((s) => s.name).join(', ') || 'none'}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="mb-8">
          <p className="text-sm text-gray-500">No databases declared in the application model.</p>
        </div>
      )}

      <EmptyState
        title="Query Runner Coming Soon"
        description="Execute SQL queries directly against your local development databases from the browser."
      />
    </div>
  );
}
