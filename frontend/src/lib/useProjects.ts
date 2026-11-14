import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage } from '../api';
import type { ProjectSummary } from '../types';

export function useProjects() {
  const [projects, setProjects] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setError(null);
    try {
      setProjects(await api.listProjects());
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { projects, error, reload };
}
