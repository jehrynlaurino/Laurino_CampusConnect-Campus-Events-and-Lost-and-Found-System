import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export function rolePath(role) {
  return role === 'ADMIN' ? '/dashboard/admin'
    : role === 'ORGANIZER' ? '/dashboard/organizer' : '/dashboard/member';
}

export default function ProtectedRoute({ roles }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="loading-screen">Checking your session…</div>;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/unauthorized" state={{ from: location.pathname }} replace />;
  return <Outlet />;
}
