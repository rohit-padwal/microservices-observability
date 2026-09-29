import { useState } from 'react';
import { ArrowRight, LockKeyhole } from 'lucide-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function LoginPage() {
  const { isAuthenticated, signIn } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [username, setUsername] = useState('demo-operator');
  const [password, setPassword] = useState('fieldnotes-demo');
  const [error, setError] = useState('');

  if (isAuthenticated) return <Navigate to="/" replace />;

  function handleSubmit(event) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setError('Enter a username and password to continue.');
      return;
    }
    signIn(username.trim());
    navigate(location.state?.from?.pathname || '/', { replace: true });
  }

  return (
    <main className="login-shell">
      <div className="login-aside"><div className="login-brand"><span className="brand-mark">F</span><span>FIELDNOTES <i>/ OPS</i></span></div><div className="login-copy"><p className="eyebrow">Service operations</p><h1>Orders in.<br />Signals out.<br /><em>Stay curious.</em></h1><p>Follow orders through payment and fraud checks while traces, metrics, and service health tell the rest of the story.</p></div><span className="login-coordinate">JAVA 21 &nbsp; · &nbsp; SPRING BOOT &nbsp; · &nbsp; REACT</span></div>
      <section className="login-form-wrap">
        <div className="login-form-heading"><span className="login-icon"><LockKeyhole size={19} /></span><p className="eyebrow">Demo session</p><h2>Step inside.</h2><p>Any non-empty username and password opens this client-side learning demo.</p></div>
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="field-label" htmlFor="login-username">Username</label><input id="login-username" autoComplete="username" value={username} onChange={(event) => { setUsername(event.target.value); setError(''); }} required />
          <label className="field-label" htmlFor="login-password">Password</label><input id="login-password" type="password" autoComplete="current-password" value={password} onChange={(event) => { setPassword(event.target.value); setError(''); }} required />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary login-submit" type="submit">Open Order Desk <ArrowRight size={17} /></button>
        </form>
        <p className="login-disclaimer">Client-only JWT exercise. No credential is sent to Spring Boot; its APIs are not authenticated.</p>
      </section>
    </main>
  );
}