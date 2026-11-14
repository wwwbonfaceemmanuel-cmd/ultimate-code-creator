import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { SupabaseClient } from '@supabase/supabase-js';
import { setApiAuth } from './api';
import { useStatus } from './context';

interface AuthState {
  /** False when the server runs without sign-in (single-user local mode). */
  enabled: boolean;
  ready: boolean;
  email: string | null;
  signedIn: boolean;
  /** True after the user opens a password-reset link. */
  recovering: boolean;
  error: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  /** Resolves to true if the account is signed in, false if the email must be confirmed first. */
  signUp: (email: string, password: string) => Promise<boolean>;
  sendPasswordReset: (email: string) => Promise<void>;
  setNewPassword: (password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

function friendly(message: string): string {
  const m = message.toLowerCase();
  if (m.includes('invalid login credentials')) return 'The email or password is not correct.';
  if (m.includes('email not confirmed')) return 'Confirm your email first: open the link we sent you.';
  if (m.includes('already registered')) return 'An account with this email already exists. Sign in instead.';
  if (m.includes('failed to fetch') || m.includes('networkerror')) return 'Could not reach the sign-in service. Check your connection.';
  return message;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const { status } = useStatus();
  const url = status?.auth.enabled ? status.auth.supabase_url : null;
  const anonKey = status?.auth.enabled ? status.auth.supabase_anon_key : null;
  const client = useRef<SupabaseClient | null>(null);
  const [ready, setReady] = useState(false);
  const [email, setEmail] = useState<string | null>(null);
  const [signedIn, setSignedIn] = useState(false);
  const [recovering, setRecovering] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!status) return;
    if (!url || !anonKey) {
      client.current = null;
      setApiAuth(null);
      setSignedIn(false);
      setReady(true);
      return;
    }
    let cancelled = false;
    let unsubscribe = () => {};
    setReady(false);
    // Loaded on demand so local mode does not download the Supabase library.
    import('@supabase/supabase-js')
      .then(async ({ createClient }) => {
        if (cancelled) return;
        const supabase = createClient(url, anonKey);
        client.current = supabase;
        setApiAuth({
          getToken: async () => (await supabase.auth.getSession()).data.session?.access_token ?? null,
          onUnauthorized: () => void supabase.auth.signOut({ scope: 'local' }),
        });
        const { data } = supabase.auth.onAuthStateChange((event, session) => {
          setSignedIn(!!session);
          setEmail(session?.user.email ?? null);
          if (event === 'PASSWORD_RECOVERY') setRecovering(true);
          if (event === 'SIGNED_OUT') setRecovering(false);
        });
        unsubscribe = () => data.subscription.unsubscribe();
        const { data: initial } = await supabase.auth.getSession();
        if (cancelled) return;
        setSignedIn(!!initial.session);
        setEmail(initial.session?.user.email ?? null);
        setReady(true);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load the sign-in service. Reload the page to try again.');
        setReady(true);
      });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [status, url, anonKey]);

  const value = useMemo<AuthState>(() => {
    const need = () => {
      if (!client.current) throw new Error('Sign-in is not available right now.');
      return client.current.auth;
    };
    const check = (err: { message: string } | null) => {
      if (err) throw new Error(friendly(err.message));
    };
    return {
      enabled: !!url,
      ready,
      email,
      signedIn,
      recovering,
      error,
      signIn: async (mail, password) => {
        check((await need().signInWithPassword({ email: mail, password })).error);
      },
      signUp: async (mail, password) => {
        const { data, error: err } = await need().signUp({ email: mail, password, options: { emailRedirectTo: window.location.origin } });
        check(err);
        // Supabase hides whether an email is taken; an empty identity list means it is.
        if (data.user && data.user.identities && data.user.identities.length === 0) {
          throw new Error('An account with this email already exists. Sign in instead.');
        }
        return !!data.session;
      },
      sendPasswordReset: async (mail) => {
        check((await need().resetPasswordForEmail(mail, { redirectTo: window.location.origin })).error);
      },
      setNewPassword: async (password) => {
        check((await need().updateUser({ password })).error);
        setRecovering(false);
      },
      signOut: async () => {
        await need().signOut({ scope: 'local' });
      },
    };
  }, [url, ready, email, signedIn, recovering, error]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
