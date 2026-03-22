import { useState, useEffect, useCallback, useRef } from 'react';
import type { AppModel } from '@/types/model';
import { fetchModel } from '@/api/client';

export function useModel() {
  const [model, setModel] = useState<AppModel | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const cache = useRef<AppModel | null>(null);

  const load = useCallback(async () => {
    if (cache.current) {
      setModel(cache.current);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await fetchModel();
      cache.current = data;
      setModel(data);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const refetch = useCallback(() => {
    cache.current = null;
    void load();
  }, [load]);

  return { model, loading, error, refetch };
}
