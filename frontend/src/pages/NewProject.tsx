import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { FilePlus2, Sparkles } from 'lucide-react';
import { api, errorMessage } from '../api';
import { useStatus, useToast } from '../context';
import { LANGUAGES } from '../lib/languages';
import AiNotice from '../components/AiNotice';

const MIN_PROMPT = 10;

export default function NewProject() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const { status } = useStatus();
  const [prompt, setPrompt] = useState<string>((location.state as { prompt?: string } | null)?.prompt ?? '');
  const [language, setLanguage] = useState<string>('html');
  const [name, setName] = useState('');
  const [busy, setBusy] = useState<'generate' | 'empty' | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (busy !== 'generate') return;
    setElapsed(0);
    const timer = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [busy]);

  const aiReady = !!status?.ai.configured;
  const promptOk = prompt.trim().length >= MIN_PROMPT;

  const generate = async (e: FormEvent) => {
    e.preventDefault();
    if (!aiReady || !promptOk || busy) return;
    setBusy('generate');
    setError(null);
    try {
      const project = await api.generate({ prompt: prompt.trim(), language, name: name.trim() });
      toast('success', `Generated ${project.files.length} file${project.files.length === 1 ? '' : 's'}.`);
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(null);
    }
  };

  const createEmpty = async () => {
    if (busy) return;
    setBusy('empty');
    setError(null);
    try {
      const project = await api.createProject({
        name: name.trim() || 'Untitled project',
        language,
        description: prompt.trim().slice(0, 2000),
        files: [],
      });
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(null);
    }
  };

  return (
    <div className="page narrow">
      <header className="page-head">
        <div>
          <h1>New project</h1>
          <p className="muted">Describe the software in ordinary language. The AI model writes the project files.</p>
        </div>
      </header>

      <AiNotice />

      <form className="card stack" onSubmit={generate}>
        <label className="field">
          <span>What should it do?</span>
          <textarea
            rows={7}
            value={prompt}
            disabled={busy !== null}
            onChange={(e) => setPrompt(e.target.value)}
            maxLength={8000}
            placeholder="Describe the features, the screens or commands, and who will use it. The more detail, the better the result."
          />
          <span className="muted small">{prompt.length} / 8000</span>
        </label>

        <div className="field">
          <span id="lang-label">Main language</span>
          <div className="chips" role="radiogroup" aria-labelledby="lang-label">
            {LANGUAGES.map((l) => (
              <button
                key={l.id}
                type="button"
                role="radio"
                aria-checked={language === l.id}
                disabled={busy !== null}
                className={`chip${language === l.id ? ' active' : ''}`}
                onClick={() => setLanguage(l.id)}
              >
                {l.label}
              </button>
            ))}
          </div>
        </div>

        <label className="field">
          <span>Project name <em className="muted">(optional)</em></span>
          <input value={name} disabled={busy !== null} maxLength={120} onChange={(e) => setName(e.target.value)} placeholder="Leave blank to let the AI name it" />
        </label>

        {error && <div className="notice notice-error" role="alert"><div>{error}</div></div>}

        {busy === 'generate' && (
          <div className="notice" role="status">
            <span className="spinner" />
            <div>Generating your project… {elapsed}s. Larger projects can take a few minutes. Keep this page open.</div>
          </div>
        )}

        <div className="row end wrap">
          <button type="button" className="btn" onClick={() => void createEmpty()} disabled={busy !== null} title="Start with no files and write the code yourself">
            {busy === 'empty' ? <span className="spinner" /> : <FilePlus2 size={16} />} Start empty project
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!aiReady || !promptOk || busy !== null}
            title={!aiReady ? 'Connect an AI model first' : !promptOk ? `Describe the project in at least ${MIN_PROMPT} characters` : ''}
          >
            {busy === 'generate' ? <span className="spinner" /> : <Sparkles size={16} />} Generate project
          </button>
        </div>
        {aiReady && !promptOk && prompt.length > 0 && <p className="muted small right">Add a little more detail to enable generation.</p>}
      </form>
    </div>
  );
}
