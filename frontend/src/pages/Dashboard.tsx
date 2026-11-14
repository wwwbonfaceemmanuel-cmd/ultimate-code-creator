import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Bot, Database, FolderKanban, RefreshCw, Sparkles } from 'lucide-react';
import { useStatus } from '../context';
import { useProjects } from '../lib/useProjects';
import AiNotice from '../components/AiNotice';
import ProjectCard from '../components/ProjectCard';

export default function Dashboard() {
  const navigate = useNavigate();
  const { status } = useStatus();
  const { projects, error, reload } = useProjects();
  const [prompt, setPrompt] = useState('');

  const start = (e: FormEvent) => {
    e.preventDefault();
    navigate('/new', { state: { prompt } });
  };

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Describe the software you want and work on it in the studio.</p>
        </div>
        <Link to="/new" className="btn btn-primary"><Sparkles size={16} /> New project</Link>
      </header>

      <AiNotice />

      <form className="card hero" onSubmit={start}>
        <label htmlFor="quick-prompt"><h2>What do you want to build?</h2></label>
        <textarea
          id="quick-prompt"
          rows={3}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="For example: a stock-keeping app for a small shop, with products, sales and a daily report"
        />
        <div className="row end">
          <button className="btn btn-primary" type="submit">Continue <ArrowRight size={16} /></button>
        </div>
      </form>

      <section className="stats">
        <div className="card stat">
          <FolderKanban size={18} />
          <div>
            <span className="muted small">Saved projects</span>
            <strong>{projects ? projects.length : '–'}</strong>
          </div>
        </div>
        <div className="card stat">
          <Bot size={18} />
          <div>
            <span className="muted small">AI model</span>
            <strong>{!status ? '–' : status.ai.configured ? status.ai.model : 'Not connected'}</strong>
          </div>
        </div>
        <div className="card stat">
          <Database size={18} />
          <div>
            <span className="muted small">Project storage</span>
            <strong>{!status ? '–' : !status.storage.ok ? 'Unavailable' : status.storage.kind === 'supabase' ? 'Supabase' : 'Local files'}</strong>
          </div>
        </div>
      </section>

      <section>
        <div className="row between section-head">
          <h2>Recent projects</h2>
          {projects && projects.length > 0 && <Link to="/projects" className="link">View all</Link>}
        </div>
        {error ? (
          <div className="notice notice-error">
            <div>{error}</div>
            <button className="btn" onClick={() => void reload()}><RefreshCw size={14} /> Retry</button>
          </div>
        ) : !projects ? (
          <div className="grid">{[0, 1, 2].map((i) => <div key={i} className="card skeleton" />)}</div>
        ) : projects.length === 0 ? (
          <div className="empty">
            <p className="muted">No projects yet. Your saved projects will appear here.</p>
            <Link to="/new" className="btn">Create your first project</Link>
          </div>
        ) : (
          <div className="grid">{projects.slice(0, 6).map((p) => <ProjectCard key={p.id} project={p} />)}</div>
        )}
      </section>
    </div>
  );
}
