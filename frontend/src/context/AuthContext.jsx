import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getAccessToken, setAccessToken } from '../auth/accessToken.js';

const AuthContext = createContext(null);

// Share session metadata across routes while keeping the bearer token in the separate in-memory module.
export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);

  // Credentials go only to Order Service; neither password nor access token is written to web storage.
  const signIn = useCallback(async (username, password) => {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || 'Invalid username or password.');
    if (!payload.accessToken || !payload.expiresAt) throw new Error('Authentication response was incomplete.');

    setAccessToken(payload.accessToken);
    const authenticatedSession = {
      username: payload.username,
      roles: payload.roles ?? [],
      expiresAt: payload.expiresAt,
    };
    setSession(authenticatedSession);
    return authenticatedSession;
  }, []);

  const signOut = useCallback(() => {
    setAccessToken(null);
    setSession(null);
  }, []);

  useEffect(() => {
    // A protected API's 401 means the server rejected this token, so clear the UI session immediately.
    function handleUnauthorized() {
      signOut();
    }
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, [signOut]);

  useEffect(() => {
    // Sign out at expiry even if an idle user makes no further request to trigger a server 401.
    if (!session?.expiresAt) return undefined;
    const remaining = Date.parse(session.expiresAt) - Date.now();
    if (remaining <= 0) {
      signOut();
      return undefined;
    }
    const timer = window.setTimeout(signOut, remaining);
    return () => window.clearTimeout(timer);
  }, [session, signOut]);

  const value = useMemo(() => ({
    isAuthenticated: Boolean(session && getAccessToken()),
    username: session?.username ?? '',
    roles: session?.roles ?? [],
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