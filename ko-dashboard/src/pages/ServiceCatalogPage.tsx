import { useParams, Link } from 'react-router-dom';
import { useModel } from '@/hooks/useModel';
import InfraBadge from '@/components/InfraBadge';
import type { ServiceModel } from '@/types/model';

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-green-500/20 text-green-400',
  POST: 'bg-blue-500/20 text-blue-400',
  PUT: 'bg-orange-500/20 text-orange-400',
  DELETE: 'bg-red-500/20 text-red-400',
  PATCH: 'bg-purple-500/20 text-purple-400',
};

export default function ServiceCatalogPage() {
  const { name } = useParams<{ name: string }>();
  const { model, loading, error } = useModel();

  if (loading) return <div className="text-gray-500 p-4">Loading...</div>;
  if (error || !model) return <div className="text-red-400 p-4">Failed to load model</div>;

  if (name) {
    const service = model.services.find((s) => s.name === name);
    if (!service) return <div className="text-gray-500 p-4">Service not found: {name}</div>;
    return <ServiceDetailView service={service} />;
  }

  return (
    <div>
      <h1 className="text-xl font-semibold text-white mb-6">Services</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {model.services.map((service) => (
          <Link
            key={service.name}
            to={`/services/${service.name}`}
            className="block p-4 rounded-lg border border-gray-800 bg-gray-900 hover:border-gray-700 transition-colors"
          >
            <h2 className="text-base font-semibold text-white mb-1">{service.name}</h2>
            <p className="text-xs text-gray-500 font-mono mb-3">{service.class_name}</p>
            <div className="flex items-center gap-3 text-xs text-gray-400 mb-3">
              <span>{service.apis.length} endpoints</span>
              <span>&middot;</span>
              <span>{infraCount(service)} resources</span>
            </div>
            <div className="flex flex-wrap gap-1">
              {service.databases.map((d) => <InfraBadge key={d.name} type="db" label={d.name} />)}
              {service.caches.map((c) => <InfraBadge key={c.name} type="cache" label={c.name} />)}
              {service.buckets.map((b) => <InfraBadge key={b.name} type="bucket" label={b.name} />)}
              {service.cron_jobs.map((j) => <InfraBadge key={j.name} type="cron" label={j.name} />)}
              {service.secrets.map((s) => <InfraBadge key={s.name} type="secret" label={s.name} />)}
              {service.publishes.map((t) => <InfraBadge key={t} type="pubsub" label={t} />)}
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function infraCount(s: ServiceModel): number {
  return s.databases.length + s.caches.length + s.buckets.length + s.cron_jobs.length + s.secrets.length;
}

function ServiceDetailView({ service }: { service: ServiceModel }) {
  return (
    <div>
      <div className="mb-6">
        <Link to="/services" className="text-sm text-ko-violet hover:underline">&larr; All Services</Link>
      </div>
      <h1 className="text-xl font-semibold text-white mb-1">{service.name}</h1>
      <p className="text-sm text-gray-500 font-mono mb-6">{service.class_name}</p>

      {/* Endpoints */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
          Endpoints ({service.apis.length})
        </h2>
        <div className="space-y-2">
          {service.apis.map((api) => (
            <div key={`${api.method}-${api.path}`} className="flex items-start gap-3 p-3 rounded-lg bg-gray-900 border border-gray-800">
              <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded shrink-0 mt-0.5 ${METHOD_COLORS[api.method] ?? ''}`}>
                {api.method}
              </span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-gray-200 font-mono">{api.path}</span>
                  {api.auth && <span className="text-[10px] px-1 rounded bg-amber-500/20 text-amber-400">auth</span>}
                </div>
                {api.javadoc && <p className="text-xs text-gray-500 mt-1">{api.javadoc}</p>}
                {api.request_type && api.request_type.fields.length > 0 && (
                  <div className="mt-2">
                    <span className="text-[10px] text-gray-600 uppercase">Request:</span>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {api.request_type.fields.map((f) => (
                        <span key={f.name} className="text-xs px-1.5 py-0.5 rounded bg-gray-800 text-gray-400 font-mono">
                          {f.name}: {f.type.split('.').pop()}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Infrastructure */}
      {infraCount(service) > 0 && (
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">Infrastructure</h2>
          <div className="flex flex-wrap gap-2">
            {service.databases.map((d) => <InfraBadge key={d.name} type="db" label={d.name} />)}
            {service.caches.map((c) => <InfraBadge key={c.name} type="cache" label={`${c.name} (TTL: ${c.ttl}s)`} />)}
            {service.buckets.map((b) => <InfraBadge key={b.name} type="bucket" label={b.name} />)}
            {service.cron_jobs.map((j) => <InfraBadge key={j.name} type="cron" label={`${j.name} (${j.schedule})`} />)}
            {service.secrets.map((s) => <InfraBadge key={s.name} type="secret" label={s.name} />)}
          </div>
        </section>
      )}

      {/* Pub/Sub */}
      {(service.publishes.length > 0 || service.subscribes.length > 0) && (
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">Pub/Sub</h2>
          <div className="space-y-1">
            {service.publishes.map((t) => (
              <div key={`pub-${t}`} className="flex items-center gap-2">
                <InfraBadge type="pubsub" label="publish" />
                <span className="text-sm text-gray-300">{t}</span>
              </div>
            ))}
            {service.subscribes.map((t) => (
              <div key={`sub-${t}`} className="flex items-center gap-2">
                <InfraBadge type="pubsub" label="subscribe" />
                <span className="text-sm text-gray-300">{t}</span>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
