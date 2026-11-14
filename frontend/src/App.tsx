import { lazy, Suspense } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { Providers } from './context';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import NewProject from './pages/NewProject';
import Projects from './pages/Projects';
import Settings from './pages/Settings';

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

export default function App() {
  return (
    <Providers>
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
    </Providers>
  );
}
