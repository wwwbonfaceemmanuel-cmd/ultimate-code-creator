import { useState, type FormEvent, type ReactNode } from 'react';
import { Code2 } from 'lucide-react';
import { useAuth } from '../auth';
import { errorMessage } from '../api';

type Mode = 'signin' | 'signup' | 'forgot';
const MIN_PASSWORD = 8;

function Frame({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand auth-brand">
          <span className="brand-mark"><Code2 size={18} /></span>
          <span className="brand-name">AI Code Studio</span>
        </div>
        <div>
          <h1>{title}</h1>
          <p className="muted">{subtitle}</p>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Login() {
  const auth = useAuth();
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
    setNotice(null);
    setPassword('');
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (busy) return;
    setError(null);
    setNotice(null);
    if (mode === 'signup' && password.length < MIN_PASSWORD) {
      setError(`Choose a password with at least ${MIN_PASSWORD} characters.`);
      return;
    }
    setBusy(true);
    try {
      if (mode === 'signin') await auth.signIn(email.trim(), password);
      else if (mode === 'signup') {
        const signedIn = await auth.signUp(email.trim(), password);
        if (!signedIn) {
          setMode('signin');
          setPassword('');
          setNotice(`We sent a confirmation link to ${email.trim()}. Open it, then sign in here.`);
        }
      } else {
        await auth.sendPasswordReset(email.trim());
        setNotice(`If an account exists for ${email.trim()}, a link to set a new password is on its way.`);
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const titles: Record<Mode, [string, string, string]> = {
    signin: ['Sign in', 'Sign in to open your projects.', 'Sign in'],
    signup: ['Create your account', 'Your projects are private to your account.', 'Create account'],
    forgot: ['Reset your password', 'We will email you a link to set a new password.', 'Send reset link'],
  };
  const [title, subtitle, action] = titles[mode];

  return (
    <Frame title={title} subtitle={subtitle}>
      <form className="stack" onSubmit={submit}>
        {auth.error && <div className="notice notice-error" role="alert"><div>{auth.error}</div></div>}
        {notice && <div className="notice notice-ok" role="status"><div>{notice}</div></div>}
        <label className="field">
          <span>Email</span>
          <input type="email" required autoComplete="email" value={email} disabled={busy} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
        </label>
        {mode !== 'forgot' && (
          <label className="field">
            <span>Password</span>
            <input
              type="password"
              required
              autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
              value={password}
              disabled={busy}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'signup' ? `At least ${MIN_PASSWORD} characters` : ''}
            />
          </label>
        )}
        {error && <p className="field-error" role="alert">{error}</p>}
        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          {busy && <span className="spinner" />} {action}
        </button>
      </form>
      <div className="auth-links">
        {mode === 'signin' && (
          <>
            <button className="link-btn" onClick={() => switchMode('forgot')}>Forgot password?</button>
            <button className="link-btn" onClick={() => switchMode('signup')}>Create an account</button>
          </>
        )}
        {mode !== 'signin' && <button className="link-btn" onClick={() => switchMode('signin')}>Back to sign in</button>}
      </div>
    </Frame>
  );
}

export function NewPassword() {
  const auth = useAuth();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (busy) return;
    if (password.length < MIN_PASSWORD) {
      setError(`Choose a password with at least ${MIN_PASSWORD} characters.`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await auth.setNewPassword(password);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Frame title="Set a new password" subtitle={auth.email ? `For ${auth.email}` : 'Choose a new password for your account.'}>
      <form className="stack" onSubmit={submit}>
        <label className="field">
          <span>New password</span>
          <input type="password" required autoFocus autoComplete="new-password" value={password} disabled={busy} onChange={(e) => setPassword(e.target.value)} placeholder={`At least ${MIN_PASSWORD} characters`} />
        </label>
        {error && <p className="field-error" role="alert">{error}</p>}
        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          {busy && <span className="spinner" />} Save password
        </button>
      </form>
    </Frame>
  );
}

export function SetupNeeded({ onRetry, busy }: { onRetry: () => void; busy: boolean }) {
  return (
    <Frame title="Sign-in is not set up yet" subtitle="This server requires sign-in, so it is locked until Supabase is connected.">
      <ol className="steps">
        <li>Create a Supabase project and run <code>supabase/schema.sql</code> in its SQL editor.</li>
        <li>On the server, set <code>SUPABASE_URL</code>, <code>SUPABASE_ANON_KEY</code> and <code>SUPABASE_SERVICE_ROLE_KEY</code>.</li>
        <li>Restart the server, then check again.</li>
      </ol>
      <button className="btn btn-primary btn-block" onClick={onRetry} disabled={busy}>
        {busy && <span className="spinner" />} Check again
      </button>
    </Frame>
  );
}

export function ServerDown({ message, onRetry, busy }: { message: string; onRetry: () => void; busy: boolean }) {
  return (
    <Frame title="Cannot reach the server" subtitle={message}>
      <button className="btn btn-primary btn-block" onClick={onRetry} disabled={busy}>
        {busy && <span className="spinner" />} Try again
      </button>
    </Frame>
  );
}
