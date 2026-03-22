import { useState } from 'react';
import type { Span } from '@/types/model';
import SpanDetail from './SpanDetail';

interface TraceWaterfallProps {
  spans: Span[];
  traceStartMs: number;
  traceDurationMs: number;
}

const KIND_COLORS: Record<string, string> = {
  API: 'bg-violet-500',
  DATABASE: 'bg-emerald-500',
  PUBSUB_PUBLISH: 'bg-orange-500',
  PUBSUB_SUBSCRIBE: 'bg-amber-500',
  SERVICE_CALL: 'bg-purple-500',
};

const KIND_LABELS: Record<string, string> = {
  API: 'API',
  DATABASE: 'DB',
  PUBSUB_PUBLISH: 'PUB',
  PUBSUB_SUBSCRIBE: 'SUB',
  SERVICE_CALL: 'CALL',
};

interface SpanNode {
  span: Span;
  children: SpanNode[];
  depth: number;
}

function buildTree(spans: Span[]): SpanNode[] {
  const byId = new Map<string, SpanNode>();
  const roots: SpanNode[] = [];

  for (const span of spans) {
    byId.set(span.span_id, { span, children: [], depth: 0 });
  }

  for (const node of byId.values()) {
    if (node.span.parent_span_id && byId.has(node.span.parent_span_id)) {
      const parent = byId.get(node.span.parent_span_id)!;
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  function setDepth(nodes: SpanNode[], d: number) {
    for (const n of nodes) {
      n.depth = d;
      setDepth(n.children, d + 1);
    }
  }
  setDepth(roots, 0);

  function flatten(nodes: SpanNode[]): SpanNode[] {
    const result: SpanNode[] = [];
    for (const n of nodes) {
      result.push(n);
      result.push(...flatten(n.children));
    }
    return result;
  }

  return flatten(roots);
}

export default function TraceWaterfall({ spans, traceStartMs, traceDurationMs }: TraceWaterfallProps) {
  const [selectedSpanId, setSelectedSpanId] = useState<string | null>(null);
  const flatSpans = buildTree(spans);
  const totalMs = Math.max(traceDurationMs, 1);

  return (
    <div className="space-y-0.5">
      {flatSpans.map((node) => {
        const { span } = node;
        const offsetPct = ((span.start_time_ms - traceStartMs) / totalMs) * 100;
        const widthPct = Math.max((span.duration_ms / totalMs) * 100, 0.5);
        const color = KIND_COLORS[span.kind] || 'bg-gray-500';
        const label = KIND_LABELS[span.kind] || span.kind;
        const isSelected = selectedSpanId === span.span_id;

        return (
          <div key={span.span_id}>
            <div
              className={`flex items-center gap-2 py-1.5 px-2 rounded cursor-pointer transition-colors ${
                isSelected ? 'bg-gray-800' : 'hover:bg-gray-800/50'
              }`}
              onClick={() => setSelectedSpanId(isSelected ? null : span.span_id)}
            >
              {/* Operation name with indent */}
              <div
                className="w-64 shrink-0 text-xs truncate text-gray-300"
                style={{ paddingLeft: node.depth * 16 }}
              >
                <span className={`inline-block w-1.5 h-1.5 rounded-full ${color} mr-1.5`} />
                <span className="text-gray-500 mr-1">{label}</span>
                {span.operation}
              </div>

              {/* Waterfall bar */}
              <div className="flex-1 relative h-5">
                <div className="absolute inset-0 bg-gray-800/30 rounded" />
                <div
                  className={`absolute h-full rounded ${color} opacity-80`}
                  style={{ left: `${offsetPct}%`, width: `${widthPct}%`, minWidth: '2px' }}
                />
              </div>

              {/* Duration */}
              <div className="w-16 shrink-0 text-right text-xs text-gray-400">
                {span.duration_ms}ms
              </div>

              {/* Status */}
              <div className="w-12 shrink-0">
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded ${
                    span.status === 'ERROR'
                      ? 'bg-red-900/50 text-red-400'
                      : 'bg-emerald-900/50 text-emerald-400'
                  }`}
                >
                  {span.status}
                </span>
              </div>
            </div>

            {/* Expanded detail */}
            {isSelected && <SpanDetail span={span} />}
          </div>
        );
      })}
    </div>
  );
}
