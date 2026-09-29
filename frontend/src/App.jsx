import { Activity, Boxes, LogOut } from 'lucide-react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import LoginPage from './pages/LoginPage.jsx';
import OrderDashboard from './pages/OrderDashboard.jsx';

function AppShell() {
  const { username, signOut } = useAuth();
  const navigate = useNavigate();

  function handleSignOut() {
    signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link className="brand" to="/" aria-label="Fieldnotes home"><span className="brand-mark">F</span><span className="brand-word">FIELDNOTES<i> / OPS</i></span></Link>
        <p className="nav-label">Workspace</p>
        <nav className="primary-nav" aria-label="Main navigation">
          <NavLink to="/" end className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}><Boxes size={17} />Order desk<span className="nav-dot" /></NavLink>
        </nav>
        <div className="sidebar-bottom"><div className="service-mark"><Activity size={15} /><span>4 services connected</span><i /></div><p>JAVA 21 · OBSERVABILITY DEMO</p></div>
      </aside>
      <div className="main-column">
        <header className="topbar"><div className="breadcrumb"><span>Workspace</span><span className="crumb-slash">/</span><span>Fieldnotes</span></div><div className="account"><span className="account-avatar">{username.charAt(0).toUpperCase()}</span><span className="account-name">{username}</span><button type="button" onClick={handleSignOut} className="icon-button signout-button" aria-label="Sign out" title="Sign out"><LogOut size={16} /></button></div></header>
        <main className="content-area"><Outlet /></main>
        <footer className="app-footer"><span>FIELDNOTES / OPERATIONS CONSOLE</span><span>REACT CLIENT <i>·</i> SPRING BOOT API</span></footer>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<OrderDashboard />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}