import { useRef, useEffect, useCallback } from 'react';
import * as d3 from 'd3';
import type { AppModel } from '@/types/model';

interface GraphNode extends d3.SimulationNodeDatum {
  id: string;
  infraCount: { db: number; cache: number; pubsub: number; cron: number; bucket: number; secret: number };
}

interface GraphLink extends d3.SimulationLinkDatum<GraphNode> {
  type: 'api_call' | 'pubsub';
  label?: string;
}

interface ServiceGraphProps {
  model: AppModel;
  selectedService: string | null;
  onSelectService: (name: string | null) => void;
}

const INFRA_COLORS: Record<string, string> = {
  db: '#3B82F6',
  cache: '#F97316',
  pubsub: '#10B981',
  cron: '#A855F7',
  bucket: '#06B6D4',
  secret: '#EF4444',
};

function deriveGraph(model: AppModel): { nodes: GraphNode[]; links: GraphLink[] } {
  const nodes: GraphNode[] = model.services.map((s) => ({
    id: s.name,
    infraCount: {
      db: s.databases.length,
      cache: s.caches.length,
      pubsub: s.publishes.length + s.subscribes.length,
      cron: s.cron_jobs.length,
      bucket: s.buckets.length,
      secret: s.secrets.length,
    },
  }));

  const links: GraphLink[] = [];

  // Explicit service dependencies
  for (const dep of model.service_dependencies) {
    links.push({ source: dep.from, target: dep.to, type: dep.type === 'pubsub' ? 'pubsub' : 'api_call', label: dep.topic });
  }

  // Implicit pubsub connections
  for (const topic of model.pubsub_topics) {
    for (const pub of topic.publishers) {
      for (const sub of topic.subscribers) {
        if (pub !== sub.service) {
          const exists = links.some(
            (l) => {
              const src = typeof l.source === 'string' ? l.source : (l.source as GraphNode).id;
              const tgt = typeof l.target === 'string' ? l.target : (l.target as GraphNode).id;
              return src === pub && tgt === sub.service && l.type === 'pubsub';
            }
          );
          if (!exists) {
            links.push({ source: pub, target: sub.service, type: 'pubsub', label: topic.name });
          }
        }
      }
    }
  }

  return { nodes, links };
}

export default function ServiceGraph({ model, selectedService, onSelectService }: ServiceGraphProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleClick = useCallback(
    (_event: MouseEvent, d: GraphNode) => {
      onSelectService(d.id === selectedService ? null : d.id);
    },
    [onSelectService, selectedService],
  );

  useEffect(() => {
    if (!svgRef.current) return;
    const svg = d3.select(svgRef.current);
    const container = containerRef.current;
    if (!container) return;

    const width = container.clientWidth;
    const height = container.clientHeight;

    svg.attr('width', width).attr('height', height);
    svg.selectAll('*').remove();

    const { nodes, links } = deriveGraph(model);
    if (nodes.length === 0) return;

    // Arrow marker
    svg.append('defs').append('marker')
      .attr('id', 'arrowhead')
      .attr('viewBox', '0 -5 10 10')
      .attr('refX', 28)
      .attr('refY', 0)
      .attr('markerWidth', 6)
      .attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-5L10,0L0,5')
      .attr('fill', '#4B5563');

    const g = svg.append('g');

    // Zoom
    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.3, 3])
      .on('zoom', (event: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
        g.attr('transform', event.transform.toString());
      });
    svg.call(zoom as unknown as (selection: d3.Selection<SVGSVGElement, unknown, null, undefined>) => void);

    // Links
    const link = g.append('g')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#4B5563')
      .attr('stroke-width', 1.5)
      .attr('stroke-dasharray', (d) => (d.type === 'pubsub' ? '6,3' : 'none'))
      .attr('marker-end', 'url(#arrowhead)');

    // Node groups
    const node = g.append('g')
      .selectAll<SVGGElement, GraphNode>('g')
      .data(nodes)
      .join('g')
      .style('cursor', 'pointer');

    // Main circle
    node.append('circle')
      .attr('r', 22)
      .attr('fill', (d) => d.id === selectedService ? '#8B5CF6' : '#7C3AED')
      .attr('stroke', (d) => d.id === selectedService ? '#C4B5FD' : '#6D28D9')
      .attr('stroke-width', (d) => d.id === selectedService ? 3 : 1.5);

    // Infra dots around node
    node.each(function (d) {
      const el = d3.select(this);
      const badges = Object.entries(d.infraCount).filter(([, count]) => count > 0);
      const angleStep = (2 * Math.PI) / Math.max(badges.length, 1);
      badges.forEach(([type], i) => {
        const angle = angleStep * i - Math.PI / 2;
        el.append('circle')
          .attr('cx', Math.cos(angle) * 32)
          .attr('cy', Math.sin(angle) * 32)
          .attr('r', 5)
          .attr('fill', INFRA_COLORS[type] ?? '#6B7280');
      });
    });

    // Labels
    node.append('text')
      .text((d) => d.id)
      .attr('text-anchor', 'middle')
      .attr('dy', 42)
      .attr('fill', '#D1D5DB')
      .attr('font-size', '12px')
      .attr('font-family', 'system-ui, sans-serif');

    // Click handler
    node.on('click', handleClick as unknown as (this: SVGGElement, event: MouseEvent, d: GraphNode) => void);

    // Drag
    const drag = d3.drag<SVGGElement, GraphNode>()
      .on('start', (event, d) => {
        if (!event.active) simulation.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
      })
      .on('drag', (event, d) => {
        d.fx = event.x;
        d.fy = event.y;
      })
      .on('end', (event, d) => {
        if (!event.active) simulation.alphaTarget(0);
        d.fx = null;
        d.fy = null;
      });
    node.call(drag);

    // Simulation
    const simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink<GraphNode, GraphLink>(links).id((d) => d.id).distance(180))
      .force('charge', d3.forceManyBody().strength(-400))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide(50))
      .on('tick', () => {
        link
          .attr('x1', (d) => (d.source as GraphNode).x ?? 0)
          .attr('y1', (d) => (d.source as GraphNode).y ?? 0)
          .attr('x2', (d) => (d.target as GraphNode).x ?? 0)
          .attr('y2', (d) => (d.target as GraphNode).y ?? 0);
        node.attr('transform', (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);
      });

    return () => {
      simulation.stop();
    };
  }, [model, selectedService, handleClick]);

  return (
    <div ref={containerRef} className="w-full h-full min-h-[500px]">
      <svg ref={svgRef} className="w-full h-full" />
    </div>
  );
}
