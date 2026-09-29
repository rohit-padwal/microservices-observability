import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getAccessToken, setAccessToken } from '../auth/accessToken.js';

const AuthContext = createContext(null);

/**
 * Shares session metadata and auth actions across routes while the bearer token stays in memory.
 * @param {{children: import('react').ReactNode}} props Provider subtree that needs the authenticated session.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);

  /**
   * Exchanges credentials for a backend-issued session; neither credential nor token is persisted to web storage.
   * @param {string} username Account name
   * @param {string} password Plaintext input sent only to the same-origin login endpoint
   * @returns {Promise<{username: string, roles: string[], expiresAt: string}>} Safe session metadata
   */
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

  /** Clears the in-memory token and session metadata after logout, expiry, or a protected API 401. */
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

/** @returns {{isAuthenticated: boolean, username: string, roles: string[], signIn: Function, signOut: Function}} Session API; throws outside AuthProvider. */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}