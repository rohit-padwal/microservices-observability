import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/** Redirects anonymous users and blocks users without OPERATOR/ADMIN; API filters enforce the same rules server-side. */
export default function ProtectedRoute() {
  const { isAuthenticated, roles, signOut } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  if (!roles.some((role) => role === 'OPERATOR' || role === 'ADMIN')) {
    return <main className="access-denied"><h1>Access denied</h1><p>Your account cannot access the operations console.</p><button className="button button-primary" onClick={signOut}>Sign out</button></main>;
  }
  return <Outlet />;
}