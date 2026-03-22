import type { Span } from '@/types/model';

interface SpanDetailProps {
  span: Span;
}

export default function SpanDetail({ span }: SpanDetailProps) {
  const attrs = span.attributes || {};

  return (
    <div className="ml-8 mr-4 mb-2 p-3 bg-gray-800/50 border border-gray-700 rounded-lg text-xs">
      <div className="grid grid-cols-2 gap-x-6 gap-y-1.5">
        <div>
          <span className="text-gray-500">Span ID:</span>{' '}
          <span className="text-gray-300 font-mono">{span.span_id}</span>
        </div>
        <div>
          <span className="text-gray-500">Kind:</span>{' '}
          <span className="text-gray-300">{span.kind}</span>
        </div>
        <div>
          <span className="text-gray-500">Service:</span>{' '}
          <span className="text-gray-300">{span.service}</span>
        </div>
        <div>
          <span className="text-gray-500">Duration:</span>{' '}
          <span className="text-gray-300">{span.duration_ms}ms</span>
        </div>
        {span.parent_span_id && (
          <div>
            <span className="text-gray-500">Parent:</span>{' '}
            <span className="text-gray-300 font-mono">{span.parent_span_id}</span>
          </div>
        )}
      </div>

      {Object.keys(attrs).length > 0 && (
        <div className="mt-3 border-t border-gray-700 pt-2">
          <div className="text-gray-500 mb-1.5">Attributes</div>
          <table className="w-full">
            <tbody>
              {Object.entries(attrs).map(([key, value]) => (
                <tr key={key}>
                  <td className="text-gray-500 pr-4 py-0.5 align-top whitespace-nowrap">{key}</td>
                  <td className="text-gray-300 py-0.5">
                    {key === 'db.sql' ? (
                      <pre className="whitespace-pre-wrap font-mono text-emerald-400 bg-gray-900 p-1.5 rounded">
                        {value}
                      </pre>
                    ) : (
                      value
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
