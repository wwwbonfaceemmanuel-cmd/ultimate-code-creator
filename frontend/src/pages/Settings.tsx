import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react';
import { useStatus } from '../context';

function State({ ok, children }: { ok: boolean; children: string }) {
  return (
    <span className={`state ${ok ? 'ok' : 'bad'}`}>
      {ok ? <CheckCircle2 size={16} /> : <XCircle size={16} />} {children}
    </span>
  );
}

export default function Settings() {
  const { status, loading, error, refresh } = useStatus();

  return (
    <div className="page narrow">
      <header className="page-head">
        <div>
          <h1>Settings</h1>
          <p className="muted">Connections are configured on the server so secret keys never reach the browser.</p>
        </div>
        <button className="btn" onClick={() => void refresh()} disabled={loading}>
          {loading ? <span className="spinner" /> : <RefreshCw size={16} />} Check again
        </button>
      </header>

      {error && <div className="notice notice-error" role="alert"><div><strong>The backend is not reachable.</strong> {error}</div></div>}

      {status && (
        <>
          <section className="card stack">
            <div className="row between wrap">
              <h2>AI model</h2>
              <State ok={status.ai.configured}>{status.ai.configured ? 'Connected' : 'Not connected'}</State>
            </div>
            {status.ai.configured ? (
              <p className="muted">Requests are sent from the server to model <code>{status.ai.model}</code>.</p>
            ) : (
              <>
                <p className="muted">
                  Generation and AI edits are turned off until the server has a model to call. Missing: {status.ai.missing.map((m) => <code key={m}>{m}</code>)}
                </p>
                <ol className="steps">
                  <li>Get an API key from an OpenAI-compatible provider (OpenAI, OpenRouter, Groq and others).</li>
                  <li>On the server, copy <code>backend/.env.example</code> to <code>backend/.env</code>.</li>
                  <li>Set <code>AI_API_KEY</code>, <code>AI_MODEL</code> and, if not OpenAI, <code>AI_BASE_URL</code>.</li>
                  <li>Restart the backend, then press “Check again”.</li>
                </ol>
              </>
            )}
          </section>

          <section className="card stack">
            <div className="row between wrap">
              <h2>Project storage</h2>
              <State ok={status.storage.ok}>
                {!status.storage.ok ? 'Unavailable' : status.storage.kind === 'supabase' ? 'Supabase' : 'Local files'}
              </State>
            </div>
            {status.storage.error && <p className="field-error">{status.storage.error}</p>}
            {status.storage.kind === 'local' ? (
              <>
                <p className="muted">
                  Projects are saved as files on the server’s disk. That suits local development; many hosting services wipe the disk on each deploy.
                </p>
                <ol className="steps">
                  <li>Create a Supabase project and run <code>supabase/schema.sql</code> in its SQL editor.</li>
                  <li>Set <code>SUPABASE_URL</code> and <code>SUPABASE_SERVICE_ROLE_KEY</code> in <code>backend/.env</code>.</li>
                  <li>Restart the backend, then press “Check again”.</li>
                </ol>
              </>
            ) : (
              <p className="muted">Projects are saved in the <code>projects</code> table of your Supabase database.</p>
            )}
          </section>

          <section className="card stack">
            <h2>Languages</h2>
            <div className="chips">{status.languages.map((l) => <span key={l.id} className="chip static">{l.label}</span>)}</div>
          </section>
        </>
      )}
    </div>
  );
}
