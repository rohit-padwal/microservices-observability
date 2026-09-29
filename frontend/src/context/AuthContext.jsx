import { createContext, useCallback, useContext, useMemo, useState } from 'react';

const AuthContext = createContext(null);
const SESSION_KEY = 'fieldnotes.demo-session';

function encodeBase64Url(value) {
  const bytes = new TextEncoder().encode(value);
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('');
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

function createDemoToken(username) {
  const issuedAt = Math.floor(Date.now() / 1000);
  // This unsigned token is only a local route-demo value; the backend does not trust or validate it.
  const header = encodeBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = encodeBase64Url(JSON.stringify({
    sub: username,
    iat: issuedAt,
    exp: issuedAt + 8 * 60 * 60,
  }));
  return `${header}.${payload}.`;
}

function restoreSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(SESSION_KEY));
    if (!session?.token || !session?.username) return null;
    const encodedPayload = session.token.split('.')[1].replaceAll('-', '+').replaceAll('_', '/');
    const payload = JSON.parse(decodeURIComponent(Array.from(atob(encodedPayload), (character) =>
      `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`,
    ).join('')));
    return payload.exp > Date.now() / 1000 ? session : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(restoreSession);

  const signIn = useCallback((username) => {
    const nextSession = { username, token: createDemoToken(username) };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
  }, []);

  const signOut = useCallback(() => {
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
  }, []);

  const value = useMemo(() => ({
    isAuthenticated: Boolean(session),
    username: session?.username ?? '',
    token: session?.token ?? null,
    signIn,
    signOut,
  }), [session, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}