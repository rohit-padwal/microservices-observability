import { useState } from 'react';
import { ArrowRight, LockKeyhole } from 'lucide-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/** Authenticates against Order Service and returns the operator to the route that required login. */
export default function LoginPage() {
  const { isAuthenticated, signIn } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) return <Navigate to="/" replace />;

  // Keep credentials in component state only; AuthContext sends them to the backend and never persists them.
  /** Handles local empty-input feedback and server-side credential errors before navigation. */
  async function handleSubmit(event) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setError('Enter a username and password to continue.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await signIn(username.trim(), password);
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch (loginError) {
      setError(loginError.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-shell">
      <div className="login-aside"><div className="login-brand"><span className="brand-mark">F</span><span>FIELDNOTES <i>/ OPS</i></span></div><div className="login-copy"><p className="eyebrow">Service operations</p><h1>Orders in.<br />Signals out.<br /><em>Stay curious.</em></h1><p>Follow orders through payment and fraud checks while traces, metrics, and service health tell the rest of the story.</p></div><span className="login-coordinate">JAVA 21 &nbsp; · &nbsp; SPRING BOOT &nbsp; · &nbsp; REACT</span></div>
      <section className="login-form-wrap">
        <div className="login-form-heading"><span className="login-icon"><LockKeyhole size={19} /></span><p className="eyebrow">Operator access</p><h2>Sign in.</h2><p>Authenticate with the account provisioned by your administrator.</p></div>
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="field-label" htmlFor="login-username">Username</label><input id="login-username" autoComplete="username" value={username} onChange={(event) => { setUsername(event.target.value); setError(''); }} required />
          <label className="field-label" htmlFor="login-password">Password</label><input id="login-password" type="password" autoComplete="current-password" value={password} onChange={(event) => { setPassword(event.target.value); setError(''); }} required />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary login-submit" type="submit" disabled={submitting}>{submitting ? 'Signing in…' : 'Open Order Desk'} <ArrowRight size={17} /></button>
        </form>
        <p className="login-disclaimer">Access tokens are short-lived and held in memory. Your password is sent only to the authentication endpoint over the configured origin.</p>
      </section>
    </main>
  );
}