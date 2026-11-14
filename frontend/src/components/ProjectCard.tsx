import { Link } from 'react-router-dom';
import { FileCode2, Trash2 } from 'lucide-react';
import type { ProjectSummary } from '../types';
import { languageLabel, timeAgo } from '../lib/languages';

export default function ProjectCard({ project, onDelete }: { project: ProjectSummary; onDelete?: (p: ProjectSummary) => void }) {
  return (
    <div className="card project-card">
      <Link to={`/projects/${project.id}`} className="project-link">
        <div className="row between">
          <span className="badge">{languageLabel(project.language)}</span>
          <span className="muted small">{timeAgo(project.updated_at)}</span>
        </div>
        <h3>{project.name}</h3>
        <p className="muted clamp">{project.description || 'No description.'}</p>
        <span className="muted small row gap-xs"><FileCode2 size={14} /> {project.file_count} file{project.file_count === 1 ? '' : 's'}</span>
      </Link>
      {onDelete && (
        <button className="icon-btn danger card-action" onClick={() => onDelete(project)} aria-label={`Delete ${project.name}`} title="Delete project">
          <Trash2 size={16} />
        </button>
      )}
    </div>
  );
}
