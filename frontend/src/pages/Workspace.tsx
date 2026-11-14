import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import Editor, { type OnMount } from '@monaco-editor/react';
import { AlertTriangle, ArrowLeft, CheckCircle2, Download, FilePlus2, Pencil, RefreshCw, Save, Send, ShieldCheck, Undo2, XCircle } from 'lucide-react';
import { monaco } from '../lib/monaco';
import { api, errorMessage } from '../api';
import { useStatus, useToast } from '../context';
import { languageLabel, monacoLanguage } from '../lib/languages';
import { cleanPath } from '../lib/paths';
import { buildPreview, findEntry } from '../lib/preview';
import FileExplorer from '../components/FileExplorer';
import { ConfirmModal, PromptModal } from '../components/Modal';
import type { Issue, Project, ProjectFile, ValidateResult } from '../types';

type Panel = 'files' | 'code' | 'preview' | 'problems';
type Dialog = { kind: 'new' } | { kind: 'rename'; path: string } | { kind: 'delete'; path: string } | { kind: 'project-name' } | null;
type Busy = 'save' | 'validate' | 'export' | 'ai' | null;

// In-memory storage so previewed pages that use localStorage do not crash in the sandbox.
const STORAGE_SHIM = `<script>(function(){function S(){var d={};return{getItem:function(k){return k in d?d[k]:null},setItem:function(k,v){d[k]=String(v)},removeItem:function(k){delete d[k]},clear:function(){d={}},key:function(i){return Object.keys(d)[i]||null},get length(){return Object.keys(d).length}}}
['localStorage','sessionStorage'].forEach(function(n){try{window[n].length}catch(e){try{Object.defineProperty(window,n,{value:S(),configurable:true})}catch(_){}}})})();</script>`;

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

export default function Workspace() {
  const { id = '' } = useParams();
  const toast = useToast();
  const { status } = useStatus();

  const [project, setProject] = useState<Project | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [files, setFiles] = useState<ProjectFile[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState<Busy>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [panel, setPanel] = useState<Panel>('code');
  const [side, setSide] = useState<'preview' | 'problems'>('preview');
  const [validation, setValidation] = useState<ValidateResult | null>(null);
  const [validationStale, setValidationStale] = useState(false);
  const [instruction, setInstruction] = useState('');
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiSummary, setAiSummary] = useState<string | null>(null);
  const [undoFiles, setUndoFiles] = useState<ProjectFile[] | null>(null);
  const [previewKey, setPreviewKey] = useState(0);

  const editorRef = useRef<Parameters<OnMount>[0] | null>(null);
  const pendingReveal = useRef<{ line: number; column: number } | null>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    setProject(null);
    try {
      const p = await api.getProject(id);
      setProject(p);
      setFiles(p.files);
      setActive(findEntry(p.files) ?? p.files[0]?.path ?? null);
      setDirty(false);
      setValidation(null);
      setUndoFiles(null);
      setAiSummary(null);
    } catch (e) {
      setLoadError(errorMessage(e));
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  // Warn before leaving the page with unsaved work.
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const changeFiles = (next: ProjectFile[]) => {
    setFiles(next);
    setDirty(true);
    setValidationStale(true);
  };

  // Monaco can report a change event without a real difference (for example
  // when a model is first attached); only real edits mark the project unsaved.
  const filesRef = useRef(files);
  filesRef.current = files;
  const editContent = (path: string, content: string) => {
    const current = filesRef.current.find((f) => f.path === path);
    if (!current || current.content === content) return;
    changeFiles(filesRef.current.map((f) => (f.path === path ? { ...f, content } : f)));
  };

  const save = useCallback(async () => {
    if (!project || busy) return;
    setBusy('save');
    try {
      const saved = await api.updateProject(project.id, { files });
      setProject(saved);
      setDirty(false);
      toast('success', 'Project saved.');
    } catch (e) {
      toast('error', errorMessage(e));
    } finally {
      setBusy(null);
    }
  }, [project, files, busy, toast]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        void save();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [save]);

  const validate = async () => {
    if (busy) return;
    setBusy('validate');
    try {
      const result = await api.validate(files);
      setValidation(result);
      setValidationStale(false);
      setSide('problems');
      setPanel('problems');
    } catch (e) {
      toast('error', errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const download = async () => {
    if (!project || busy) return;
    setBusy('export');
    try {
      const blob = await api.exportZip(project.name, files);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${project.name.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase() || 'project'}.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast('error', errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const runAiEdit = async (e: FormEvent) => {
    e.preventDefault();
    if (!project || busy || instruction.trim().length < 3) return;
    setBusy('ai');
    setAiError(null);
    try {
      const result = await api.edit({ instruction: instruction.trim(), language: project.language, files });
      setUndoFiles(files);
      changeFiles(result.files);
      setAiSummary(
        `${result.summary} (${result.changed.length} changed${result.deleted.length ? `, ${result.deleted.length} deleted` : ''}; not saved yet)`,
      );
      setInstruction('');
      if (!active || !result.files.some((f) => f.path === active)) setActive(result.changed[0] ?? result.files[0]?.path ?? null);
      else if (result.changed.length && !result.changed.includes(active)) setActive(result.changed[0]);
    } catch (err) {
      setAiError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  const undoAi = () => {
    if (!undoFiles) return;
    changeFiles(undoFiles);
    if (active && !undoFiles.some((f) => f.path === active)) setActive(undoFiles[0]?.path ?? null);
    setUndoFiles(null);
    setAiSummary('The last AI edit was undone.');
  };

  const openFile = (path: string) => {
    setActive(path);
    setPanel('code');
  };

  const jumpTo = (issue: Issue) => {
    pendingReveal.current = { line: issue.line, column: issue.column };
    openFile(issue.path);
    setTimeout(reveal, 80);
  };

  const reveal = () => {
    const target = pendingReveal.current;
    const editor = editorRef.current;
    if (!target || !editor) return;
    pendingReveal.current = null;
    editor.revealLineInCenter(target.line);
    editor.setPosition({ lineNumber: target.line, column: target.column });
    editor.focus();
  };

  const activeFile = files.find((f) => f.path === active) ?? null;
  const issues = useMemo(() => validation?.issues ?? [], [validation]);

  // Show validation results as squiggles in the editor.
  useEffect(() => {
    const model = editorRef.current?.getModel();
    if (!model) return;
    const markers = validationStale
      ? []
      : issues
          .filter((i) => i.path === active)
          .map((i) => ({
            severity: i.severity === 'error' ? monaco.MarkerSeverity.Error : monaco.MarkerSeverity.Warning,
            message: i.message,
            startLineNumber: i.line,
            startColumn: i.column,
            endLineNumber: i.line,
            endColumn: model.getLineMaxColumn(Math.min(i.line, model.getLineCount())),
          }));
    monaco.editor.setModelMarkers(model, 'ai-code-studio', markers);
  }, [issues, active, validationStale, activeFile?.content]);

  const debouncedFiles = useDebounced(files, 500);
  const preview = useMemo(() => {
    const entry = findEntry(debouncedFiles);
    return entry ? buildPreview(debouncedFiles, entry) : null;
  }, [debouncedFiles]);

  // ---- file dialogs -------------------------------------------------------
  const createFile = (raw: string) => {
    const { path, error } = cleanPath(raw);
    if (!path) return error;
    if (files.some((f) => f.path === path)) return 'A file with that path already exists.';
    changeFiles([...files, { path, content: '' }]);
    openFile(path);
  };

  const renameFile = (from: string, raw: string) => {
    const { path, error } = cleanPath(raw);
    if (!path) return error;
    if (path === from) return;
    if (files.some((f) => f.path === path)) return 'A file with that path already exists.';
    changeFiles(files.map((f) => (f.path === from ? { ...f, path } : f)));
    if (active === from) setActive(path);
  };

  const deleteFile = (path: string) => {
    const next = files.filter((f) => f.path !== path);
    changeFiles(next);
    if (active === path) setActive(next[0]?.path ?? null);
    setDialog(null);
  };

  const renameProject = (raw: string) => {
    const name = raw.trim();
    if (!name) return 'Enter a project name.';
    if (!project || name === project.name) return;
    api
      .updateProject(project.id, { name })
      .then((saved) => {
        setProject((p) => (p ? { ...p, name: saved.name, updated_at: saved.updated_at } : p));
        toast('success', 'Project renamed.');
      })
      .catch((e) => toast('error', errorMessage(e)));
  };

  // ---- render -------------------------------------------------------------
  if (loadError) {
    return (
      <div className="page">
        <div className="empty">
          <XCircle size={28} />
          <h2>Could not open the project</h2>
          <p className="muted">{loadError}</p>
          <div className="row">
            <Link to="/projects" className="btn">Back to projects</Link>
            <button className="btn btn-primary" onClick={() => void load()}><RefreshCw size={14} /> Retry</button>
          </div>
        </div>
      </div>
    );
  }
  if (!project) return <div className="center-fill"><span className="spinner" /> Opening project…</div>;

  const aiReady = !!status?.ai.configured;
  const errorCount = issues.filter((i) => i.severity === 'error').length;

  return (
    <div className="workspace" data-panel={panel}>
      <header className="ws-bar">
        <Link to="/projects" className="icon-btn" aria-label="Back to projects" title="Back to projects"><ArrowLeft size={18} /></Link>
        <button className="ws-title" onClick={() => setDialog({ kind: 'project-name' })} title="Rename project">
          <span className="ws-name">{project.name}</span>
          <Pencil size={13} />
        </button>
        <span className="badge hide-sm">{languageLabel(project.language)}</span>
        <span className={`save-state ${dirty ? 'warn' : ''}`}>{dirty ? 'Unsaved changes' : 'Saved'}</span>
        <div className="ws-actions">
          <button className="btn btn-small" onClick={() => void validate()} disabled={busy !== null || files.length === 0} title="Check the code for problems">
            {busy === 'validate' ? <span className="spinner" /> : <ShieldCheck size={15} />} <span className="hide-sm">Check code</span>
          </button>
          <button className="btn btn-small" onClick={() => void download()} disabled={busy !== null || files.length === 0} title="Download the project as a ZIP file">
            {busy === 'export' ? <span className="spinner" /> : <Download size={15} />} <span className="hide-sm">Download ZIP</span>
          </button>
          <button className="btn btn-small btn-primary" onClick={() => void save()} disabled={busy !== null || !dirty} title="Save (Ctrl+S)">
            {busy === 'save' ? <span className="spinner" /> : <Save size={15} />} <span className="hide-sm">Save</span>
          </button>
        </div>
      </header>

      <nav className="ws-tabs" aria-label="Workspace panels">
        {(['files', 'code', 'preview', 'problems'] as Panel[]).map((p) => (
          <button
            key={p}
            className={panel === p ? 'active' : ''}
            onClick={() => { setPanel(p); if (p === 'preview' || p === 'problems') setSide(p); }}
          >
            {p[0].toUpperCase() + p.slice(1)}{p === 'problems' && issues.length > 0 ? ` (${issues.length})` : ''}
          </button>
        ))}
      </nav>

      <div className="ws-body">
        <section className="ws-pane pane-files">
          <FileExplorer
            files={files}
            active={active}
            issues={validationStale ? [] : issues}
            onOpen={openFile}
            onNew={() => setDialog({ kind: 'new' })}
            onRename={(path) => setDialog({ kind: 'rename', path })}
            onDelete={(path) => setDialog({ kind: 'delete', path })}
          />
        </section>

        <section className="ws-pane pane-code">
          <div className="panel-head"><span className="mono">{active ?? 'No file open'}</span></div>
          <div className="editor-host">
            {activeFile ? (
              <Editor
                theme="studio-dark"
                path={`${project.id}/${activeFile.path}`}
                language={monacoLanguage(activeFile.path)}
                value={activeFile.content}
                onChange={(value) => editContent(activeFile.path, value ?? '')}
                onMount={(editor) => { editorRef.current = editor; reveal(); }}
                loading={<div className="center-fill"><span className="spinner" /> Loading editor…</div>}
                options={{ fontSize: 13, minimap: { enabled: false }, automaticLayout: true, scrollBeyondLastLine: false, tabSize: 2, wordWrap: 'on', padding: { top: 10 } }}
              />
            ) : (
              <div className="panel-empty">
                <p className="muted">{files.length ? 'Choose a file to edit.' : 'Add a file to start writing code, or ask the AI below.'}</p>
                <button className="btn" onClick={() => setDialog({ kind: 'new' })}><FilePlus2 size={15} /> New file</button>
              </div>
            )}
          </div>
        </section>

        <section className={`ws-pane pane-side side-${side}`}>
          <div className="panel-head tabs">
            <button className={side === 'preview' ? 'active' : ''} onClick={() => setSide('preview')}>Preview</button>
            <button className={side === 'problems' ? 'active' : ''} onClick={() => setSide('problems')}>
              Problems{issues.length > 0 ? ` (${issues.length})` : ''}
            </button>
            {side === 'preview' && preview && (
              <button className="icon-btn push" onClick={() => setPreviewKey((k) => k + 1)} aria-label="Reload preview" title="Reload preview"><RefreshCw size={14} /></button>
            )}
          </div>

          <div className="side-preview">
            {preview ? (
              <>
                <iframe key={previewKey} className="preview-frame" title={`Preview of ${preview.entry}`} sandbox="allow-scripts allow-forms allow-modals" srcDoc={STORAGE_SHIM + preview.html} />
                <p className="preview-note muted small">Showing <span className="mono">{preview.entry}</span> in a sandbox. Stored data is not kept, and server calls or build steps do not run here.</p>
              </>
            ) : (
              <div className="panel-empty">
                <p className="muted">Live preview works for projects with an HTML page.</p>
                <p className="muted small">To run this project, download the ZIP and follow its README.</p>
              </div>
            )}
          </div>

          <div className="side-problems">
            {!validation ? (
              <div className="panel-empty">
                <p className="muted">The code has not been checked yet.</p>
                <button className="btn" onClick={() => void validate()} disabled={busy !== null || files.length === 0}>
                  {busy === 'validate' ? <span className="spinner" /> : <ShieldCheck size={15} />} Check code
                </button>
              </div>
            ) : (
              <div className="problems">
                {validationStale && (
                  <div className="notice notice-compact">
                    <div>The code changed since this check.</div>
                    <button className="btn btn-small" onClick={() => void validate()} disabled={busy !== null}>Check again</button>
                  </div>
                )}
                {issues.length === 0 ? (
                  <p className="problem-ok"><CheckCircle2 size={16} /> No problems found in {validation.files_checked} file{validation.files_checked === 1 ? '' : 's'}.</p>
                ) : (
                  <>
                    <p className="muted small">{errorCount} error{errorCount === 1 ? '' : 's'}, {issues.length - errorCount} warning{issues.length - errorCount === 1 ? '' : 's'}</p>
                    <ul>
                      {issues.map((issue, i) => (
                        <li key={i}>
                          <button className="problem" onClick={() => jumpTo(issue)} disabled={!files.some((f) => f.path === issue.path)}>
                            {issue.severity === 'error' ? <XCircle size={15} className="c-bad" /> : <AlertTriangle size={15} className="c-warn" />}
                            <span>
                              <span>{issue.message}</span>
                              <span className="muted small mono"> {issue.path}:{issue.line}</span>
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
                <p className="muted small">{validation.note}</p>
              </div>
            )}
          </div>
        </section>
      </div>

      <footer className="ai-bar">
        {aiError && <div className="ai-msg error" role="alert"><XCircle size={14} /> {aiError}</div>}
        {!aiError && aiSummary && (
          <div className="ai-msg">
            <span>{aiSummary}</span>
            {undoFiles && <button className="btn btn-small" onClick={undoAi}><Undo2 size={13} /> Undo</button>}
          </div>
        )}
        {busy === 'ai' && <div className="ai-msg" role="status"><span className="spinner" /> The AI is editing your project. This can take a minute…</div>}
        <form onSubmit={runAiEdit} className="ai-form">
          <input
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            disabled={!aiReady || busy !== null}
            maxLength={8000}
            aria-label="Instruction for the AI"
            placeholder={aiReady ? 'Tell the AI what to change, for example “add a dark mode toggle”' : 'Connect an AI model in Settings to edit with instructions'}
          />
          {aiReady ? (
            <button className="btn btn-primary" type="submit" disabled={busy !== null || instruction.trim().length < 3}>
              <Send size={15} /> <span className="hide-sm">Apply</span>
            </button>
          ) : (
            <Link to="/settings" className="btn">Connect AI</Link>
          )}
        </form>
      </footer>

      {dialog?.kind === 'new' && (
        <PromptModal title="New file" label="File path" placeholder="src/app.js" submitLabel="Create" onSubmit={createFile} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'rename' && (
        <PromptModal title="Rename or move file" label="File path" initial={dialog.path} submitLabel="Rename" onSubmit={(v) => renameFile(dialog.path, v)} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'project-name' && (
        <PromptModal title="Rename project" label="Project name" initial={project.name} submitLabel="Rename" onSubmit={renameProject} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmModal title="Delete file" message={`"${dialog.path}" will be removed from the project.`} confirmLabel="Delete" onConfirm={() => deleteFile(dialog.path)} onClose={() => setDialog(null)} />
      )}
    </div>
  );
}
