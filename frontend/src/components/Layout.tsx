import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { Code2, FolderKanban, LayoutDashboard, Settings as SettingsIcon, Sparkles } from 'lucide-react';
import { useStatus } from '../context';

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/new', label: 'New project', icon: Sparkles, end: false },
  { to: '/projects', label: 'Projects', icon: FolderKanban, end: false },
  { to: '/settings', label: 'Settings', icon: SettingsIcon, end: false },
];

export default function Layout({ children }: { children: ReactNode }) {
  const { status, loading, error } = useStatus();
  const aiLabel = loading && !status ? 'Checking…' : error ? 'Server offline' : status?.ai.configured ? 'AI connected' : 'AI not connected';
  const aiClass = error ? 'bad' : status?.ai.configured ? 'ok' : 'warn';

  return (
    <div className="shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand">
          <span className="brand-mark"><Code2 size={18} /></span>
          <span className="brand-name">AI Code Studio</span>
        </NavLink>
        <nav className="nav">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <NavLink to="/settings" className="status-pill" title="Open connection settings">
          <span className={`dot ${aiClass}`} />
          <span>{aiLabel}</span>
        </NavLink>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
