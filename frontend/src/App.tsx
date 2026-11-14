import { lazy, Suspense, type ReactNode } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { Providers, useStatus } from './context';
import { AuthProvider, useAuth } from './auth';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import NewProject from './pages/NewProject';
import Projects from './pages/Projects';
import Settings from './pages/Settings';
import { Login, NewPassword, ServerDown, SetupNeeded } from './pages/Login';

// The workspace pulls in Monaco, so it is loaded on demand.
const Workspace = lazy(() => import('./pages/Workspace'));

function NotFound() {
  return (
    <div className="page">
      <div className="empty">
        <h2>Page not found</h2>
        <Link className="btn btn-primary" to="/">Back to dashboard</Link>
      </div>
    </div>
  );
}

/** Decides whether to show the app, the sign-in screen or a setup message. */
function Gate({ children }: { children: ReactNode }) {
  const { status, loading, error, refresh } = useStatus();
  const auth = useAuth();
  const spinner = <div className="center-fill full"><span className="spinner" /> Loading…</div>;

  if (!status) return error ? <ServerDown message={error} onRetry={() => void refresh()} busy={loading} /> : spinner;
  if (status.auth.required && !status.auth.enabled) return <SetupNeeded onRetry={() => void refresh()} busy={loading} />;
  if (!auth.ready) return spinner;
  if (auth.enabled) {
    if (auth.recovering && auth.signedIn) return <NewPassword />;
    if (!auth.signedIn) return <Login />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Providers>
      <AuthProvider>
        <Gate>
          <Layout>
            <Suspense fallback={<div className="center-fill"><span className="spinner" /> Loading editor…</div>}>
              <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/new" element={<NewProject />} />
                <Route path="/projects" element={<Projects />} />
                <Route path="/projects/:id" element={<Workspace />} />
                <Route path="/settings" element={<Settings />} />
                <Route path="*" element={<NotFound />} />
              </Routes>
            </Suspense>
          </Layout>
        </Gate>
      </AuthProvider>
    </Providers>
  );
}
