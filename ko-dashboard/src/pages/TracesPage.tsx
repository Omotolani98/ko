import { useState, useEffect, useCallback } from 'react';
import { fetchTraces, fetchTrace } from '@/api/client';
import type { TraceSummary, Trace } from '@/types/model';
import TraceWaterfall from '@/components/TraceWaterfall';
import EmptyState from '@/components/EmptyState';

export default function TracesPage() {
  const [traces, setTraces] = useState<TraceSummary[]>([]);
  const [selectedTrace, setSelectedTrace] = useState<Trace | null>(null);
  const [loading, setLoading] = useState(true);

  const loadTraces = useCallback(async () => {
    try {
      const data = await fetchTraces(100);
      setTraces(data);
    } catch {
      // Dashboard may not be connected yet
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTraces();
    const interval = setInterval(loadTraces, 2000);
    return () => clearInterval(interval);
  }, [loadTraces]);

  const handleSelectTrace = async (traceId: string) => {
    if (selectedTrace?.trace_id === traceId) {
      setSelectedTrace(null);
      return;
    }
    try {
      const trace = await fetchTrace(traceId);
      setSelectedTrace(trace);
    } catch {
      // ignore
    }
  };

  if (loading) {
    return (
      <div className="text-gray-500 text-sm py-10 text-center">Loading traces...</div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-white">Traces</h1>
        <div className="text-xs text-gray-500">
          {traces.length} trace{traces.length !== 1 ? 's' : ''} &middot; auto-refreshing
        </div>
      </div>

      {traces.length === 0 ? (
        <EmptyState
          title="No Traces Yet"
          description="Make an API request to your app and traces will appear here automatically."
        />
      ) : (
        <div className="space-y-2">
          {traces.map((t) => {
            const isExpanded = selectedTrace?.trace_id === t.trace_id;
            return (
              <div key={t.trace_id} className="border border-gray-800 rounded-lg overflow-hidden">
                <div
                  className={`flex items-center gap-3 px-4 py-3 cursor-pointer transition-colors ${
                    isExpanded ? 'bg-gray-800' : 'hover:bg-gray-800/50'
                  }`}
                  onClick={() => handleSelectTrace(t.trace_id)}
                >
                  {/* Status dot */}
                  <span
                    className={`w-2 h-2 rounded-full shrink-0 ${
                      t.status === 'ERROR' ? 'bg-red-500' : 'bg-emerald-500'
                    }`}
                  />

                  {/* Operation */}
                  <span className="text-sm text-white font-medium truncate flex-1">
                    {t.root_operation}
                  </span>

                  {/* Service */}
                  <span className="text-xs text-gray-500 shrink-0">{t.root_service}</span>

                  {/* Span count */}
                  <span className="text-xs text-gray-600 shrink-0">
                    {t.span_count} span{t.span_count !== 1 ? 's' : ''}
                  </span>

                  {/* Duration */}
                  <span
                    className={`text-xs font-mono shrink-0 ${
                      t.duration_ms > 500
                        ? 'text-red-400'
                        : t.duration_ms > 100
                          ? 'text-amber-400'
                          : 'text-emerald-400'
                    }`}
                  >
                    {t.duration_ms}ms
                  </span>

                  {/* Timestamp */}
                  <span className="text-[10px] text-gray-600 shrink-0 w-20 text-right">
                    {new Date(t.start_time_ms).toLocaleTimeString()}
                  </span>

                  {/* Expand arrow */}
                  <svg
                    className={`w-4 h-4 text-gray-500 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                    fill="none" viewBox="0 0 24 24" stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </div>

                {/* Expanded waterfall */}
                {isExpanded && selectedTrace && (
                  <div className="border-t border-gray-800 px-4 py-3 bg-gray-900/50">
                    <div className="flex items-center gap-4 mb-3 text-[10px] text-gray-500">
                      <span>Trace: <span className="font-mono text-gray-400">{selectedTrace.trace_id}</span></span>
                      <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-violet-500" /> API</span>
                      <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /> Database</span>
                      <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-orange-500" /> PubSub</span>
                      <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-purple-500" /> Service Call</span>
                    </div>
                    <TraceWaterfall
                      spans={selectedTrace.spans}
                      traceStartMs={selectedTrace.start_time_ms}
                      traceDurationMs={selectedTrace.duration_ms}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
