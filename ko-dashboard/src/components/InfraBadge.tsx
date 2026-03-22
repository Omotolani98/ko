const BADGE_COLORS: Record<string, string> = {
  db: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
  cache: 'bg-orange-500/20 text-orange-400 border-orange-500/30',
  pubsub: 'bg-green-500/20 text-green-400 border-green-500/30',
  cron: 'bg-purple-500/20 text-purple-400 border-purple-500/30',
  bucket: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30',
  secret: 'bg-red-500/20 text-red-400 border-red-500/30',
};

interface InfraBadgeProps {
  type: string;
  label: string;
}

export default function InfraBadge({ type, label }: InfraBadgeProps) {
  const colors = BADGE_COLORS[type] ?? 'bg-gray-500/20 text-gray-400 border-gray-500/30';
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded border ${colors}`}>
      {label}
    </span>
  );
}
