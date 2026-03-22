import EmptyState from '@/components/EmptyState';

export default function TracesPage() {
  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Traces</h1>
      <EmptyState
        title="Coming Soon"
        description="OpenTelemetry trace visualization will display request waterfall diagrams with timing breakdowns across services."
      />
    </div>
  );
}
