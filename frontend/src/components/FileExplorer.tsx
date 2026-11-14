import { useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, FileCode2, FilePlus2, Folder, Pencil, Trash2 } from 'lucide-react';
import type { Issue, ProjectFile } from '../types';

interface Node {
  name: string;
  path: string;
  children: Map<string, Node>;
  isFile: boolean;
}

function buildTree(files: ProjectFile[]): Node {
  const root: Node = { name: '', path: '', children: new Map(), isFile: false };
  for (const f of files) {
    const parts = f.path.split('/');
    let node = root;
    parts.forEach((part, i) => {
      const path = parts.slice(0, i + 1).join('/');
      if (!node.children.has(part)) node.children.set(part, { name: part, path, children: new Map(), isFile: i === parts.length - 1 });
      node = node.children.get(part)!;
    });
  }
  return root;
}

function sorted(node: Node): Node[] {
  return [...node.children.values()].sort((a, b) => Number(a.isFile) - Number(b.isFile) || a.name.localeCompare(b.name));
}

interface Props {
  files: ProjectFile[];
  active: string | null;
  issues: Issue[];
  onOpen: (path: string) => void;
  onNew: () => void;
  onRename: (path: string) => void;
  onDelete: (path: string) => void;
}

export default function FileExplorer({ files, active, issues, onOpen, onNew, onRename, onDelete }: Props) {
  const tree = useMemo(() => buildTree(files), [files]);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const flagged = useMemo(() => {
    const map = new Map<string, 'error' | 'warning'>();
    for (const i of issues) if (i.severity === 'error' || !map.has(i.path)) map.set(i.path, i.severity);
    return map;
  }, [issues]);

  const toggle = (path: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (!next.delete(path)) next.add(path);
      return next;
    });

  const render = (node: Node, depth: number): JSX.Element[] =>
    sorted(node).flatMap((child) => {
      const pad = { paddingLeft: 10 + depth * 14 };
      if (!child.isFile) {
        const open = !collapsed.has(child.path);
        return [
          <button key={child.path} className="tree-row folder" style={pad} onClick={() => toggle(child.path)} aria-expanded={open}>
            {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <Folder size={14} />
            <span className="tree-name">{child.name}</span>
          </button>,
          ...(open ? render(child, depth + 1) : []),
        ];
      }
      const flag = flagged.get(child.path);
      return [
        <div key={child.path} className={`tree-row file${active === child.path ? ' active' : ''}`} style={pad}>
          <button className="tree-open" onClick={() => onOpen(child.path)} title={child.path}>
            <FileCode2 size={14} />
            <span className="tree-name">{child.name}</span>
            {flag && <span className={`dot ${flag === 'error' ? 'bad' : 'warn'}`} title={flag === 'error' ? 'Has errors' : 'Has warnings'} />}
          </button>
          <span className="tree-actions">
            <button className="icon-btn" onClick={() => onRename(child.path)} aria-label={`Rename ${child.name}`} title="Rename or move"><Pencil size={13} /></button>
            <button className="icon-btn danger" onClick={() => onDelete(child.path)} aria-label={`Delete ${child.name}`} title="Delete"><Trash2 size={13} /></button>
          </span>
        </div>,
      ];
    });

  return (
    <div className="explorer">
      <div className="panel-head">
        <span>Files</span>
        <button className="icon-btn" onClick={onNew} aria-label="New file" title="New file"><FilePlus2 size={16} /></button>
      </div>
      <div className="tree">
        {files.length === 0 ? (
          <div className="panel-empty">
            <p className="muted small">This project has no files yet.</p>
            <button className="btn btn-small" onClick={onNew}><FilePlus2 size={14} /> New file</button>
          </div>
        ) : (
          render(tree, 0)
        )}
      </div>
    </div>
  );
}
