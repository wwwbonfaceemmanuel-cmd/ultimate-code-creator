import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, Search, Sparkles } from 'lucide-react';
import { api, errorMessage } from '../api';
import { useToast } from '../context';
import { useProjects } from '../lib/useProjects';
import { LANGUAGES } from '../lib/languages';
import ProjectCard from '../components/ProjectCard';
import { ConfirmModal } from '../components/Modal';
import type { ProjectSummary } from '../types';

export default function Projects() {
  const toast = useToast();
  const { projects, error, reload } = useProjects();
  const [query, setQuery] = useState('');
  const [language, setLanguage] = useState('');
  const [toDelete, setToDelete] = useState<ProjectSummary | null>(null);
  const [deleting, setDeleting] = useState(false);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (projects ?? []).filter(
      (p) => (!language || p.language === language) && (!q || p.name.toLowerCase().includes(q) || p.description.toLowerCase().includes(q)),
    );
  }, [projects, query, language]);

  const confirmDelete = async () => {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await api.deleteProject(toDelete.id);
      toast('success', `Deleted "${toDelete.name}".`);
      setToDelete(null);
      await reload();
    } catch (e) {
      toast('error', errorMessage(e));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Projects</h1>
          <p className="muted">Open a saved project to keep working on it.</p>
        </div>
        <Link to="/new" className="btn btn-primary"><Sparkles size={16} /> New project</Link>
      </header>

      <div className="row wrap filters">
        <label className="search">
          <Search size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search projects" aria-label="Search projects" />
        </label>
        <select value={language} onChange={(e) => setLanguage(e.target.value)} aria-label="Filter by language">
          <option value="">All languages</option>
          {LANGUAGES.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
        </select>
      </div>

      {error ? (
        <div className="notice notice-error">
          <div>{error}</div>
          <button className="btn" onClick={() => void reload()}><RefreshCw size={14} /> Retry</button>
        </div>
      ) : !projects ? (
        <div className="grid">{[0, 1, 2, 3].map((i) => <div key={i} className="card skeleton" />)}</div>
      ) : projects.length === 0 ? (
        <div className="empty">
          <p className="muted">You have no saved projects yet.</p>
          <Link to="/new" className="btn">Create a project</Link>
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty">
          <p className="muted">No projects match your search.</p>
          <button className="btn" onClick={() => { setQuery(''); setLanguage(''); }}>Clear filters</button>
        </div>
      ) : (
        <div className="grid">{filtered.map((p) => <ProjectCard key={p.id} project={p} onDelete={setToDelete} />)}</div>
      )}

      {toDelete && (
        <ConfirmModal
          title="Delete project"
          message={`"${toDelete.name}" and all its files will be permanently deleted.`}
          confirmLabel="Delete"
          busy={deleting}
          onConfirm={() => void confirmDelete()}
          onClose={() => setToDelete(null)}
        />
      )}
    </div>
  );
}
