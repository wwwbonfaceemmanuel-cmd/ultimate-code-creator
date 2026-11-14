import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, errorMessage } from './api';
import type { SystemStatus } from './types';

// ---- System status (AI + storage connection) ------------------------------
interface StatusState {
  status: SystemStatus | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
}
const StatusContext = createContext<StatusState | null>(null);

// ---- Toasts ---------------------------------------------------------------
type ToastKind = 'success' | 'error' | 'info';
interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}
const ToastContext = createContext<(kind: ToastKind, text: string) => void>(() => {});

export function Providers({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await api.status());
      setError(null);
    } catch (e) {
      setStatus(null);
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const toast = useCallback((kind: ToastKind, text: string) => {
    const id = nextId.current++;
    setToasts((t) => [...t, { id, kind, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), kind === 'error' ? 8000 : 4000);
  }, []);

  return (
    <StatusContext.Provider value={{ status, loading, error, refresh }}>
      <ToastContext.Provider value={toast}>
        {children}
        <div className="toasts" role="status" aria-live="polite">
          {toasts.map((t) => (
            <button key={t.id} className={`toast toast-${t.kind}`} onClick={() => setToasts((all) => all.filter((x) => x.id !== t.id))}>
              {t.text}
            </button>
          ))}
        </div>
      </ToastContext.Provider>
    </StatusContext.Provider>
  );
}

export function useStatus(): StatusState {
  const ctx = useContext(StatusContext);
  if (!ctx) throw new Error('useStatus must be used inside Providers');
  return ctx;
}

export function useToast() {
  return useContext(ToastContext);
}
