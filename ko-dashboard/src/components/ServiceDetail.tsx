import type { AppModel, ServiceModel } from '@/types/model';
import InfraBadge from './InfraBadge';

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-green-500/20 text-green-400',
  POST: 'bg-blue-500/20 text-blue-400',
  PUT: 'bg-orange-500/20 text-orange-400',
  DELETE: 'bg-red-500/20 text-red-400',
  PATCH: 'bg-purple-500/20 text-purple-400',
};

interface ServiceDetailProps {
  serviceName: string;
  model: AppModel;
  onClose: () => void;
}

export default function ServiceDetail({ serviceName, model, onClose }: ServiceDetailProps) {
  const service = model.services.find((s) => s.name === serviceName);
  if (!service) return null;

  return (
    <div className="w-96 bg-gray-900 border-l border-gray-800 h-full overflow-y-auto p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-white">{service.name}</h2>
        <button onClick={onClose} className="text-gray-500 hover:text-gray-300">
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <p className="text-xs text-gray-500 font-mono mb-5">{service.class_name}</p>

      <Section title="API Endpoints" count={service.apis.length}>
        {service.apis.map((api) => (
          <div key={`${api.method}-${api.path}`} className="flex items-center gap-2 py-1.5">
            <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded ${METHOD_COLORS[api.method] ?? ''}`}>
              {api.method}
            </span>
            <span className="text-sm text-gray-300 font-mono">{api.path}</span>
            {api.auth && <span className="text-[10px] px-1 rounded bg-amber-500/20 text-amber-400">auth</span>}
          </div>
        ))}
      </Section>

      <InfraSection service={service} />

      {(service.publishes.length > 0 || service.subscribes.length > 0) && (
        <Section title="Pub/Sub">
          {service.publishes.map((t) => (
            <div key={`pub-${t}`} className="flex items-center gap-2 py-1">
              <span className="text-[10px] px-1.5 rounded bg-green-500/20 text-green-400">publish</span>
              <span className="text-sm text-gray-300">{t}</span>
            </div>
          ))}
          {service.subscribes.map((t) => (
            <div key={`sub-${t}`} className="flex items-center gap-2 py-1">
              <span className="text-[10px] px-1.5 rounded bg-cyan-500/20 text-cyan-400">subscribe</span>
              <span className="text-sm text-gray-300">{t}</span>
            </div>
          ))}
        </Section>
      )}
    </div>
  );
}

function Section({ title, count, children }: { title: string; count?: number; children: React.ReactNode }) {
  return (
    <div className="mb-5">
      <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
        {title} {count !== undefined && <span className="text-gray-600">({count})</span>}
      </h3>
      {children}
    </div>
  );
}

function InfraSection({ service }: { service: ServiceModel }) {
  const items: { type: string; label: string }[] = [];
  service.databases.forEach((d) => items.push({ type: 'db', label: d.name }));
  service.caches.forEach((c) => items.push({ type: 'cache', label: `${c.name} (${c.ttl}s)` }));
  service.buckets.forEach((b) => items.push({ type: 'bucket', label: b.name }));
  service.cron_jobs.forEach((j) => items.push({ type: 'cron', label: `${j.name} (${j.schedule})` }));
  service.secrets.forEach((s) => items.push({ type: 'secret', label: s.name }));

  if (items.length === 0) return null;

  return (
    <Section title="Infrastructure" count={items.length}>
      <div className="flex flex-wrap gap-1.5">
        {items.map((item, i) => (
          <InfraBadge key={i} type={item.type} label={item.label} />
        ))}
      </div>
    </Section>
  );
}
