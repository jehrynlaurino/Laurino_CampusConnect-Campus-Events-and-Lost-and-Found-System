import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { rolePath } from '../components/ProtectedRoute.jsx';

const pageStyle = {
  minHeight: '100vh', display: 'grid', placeItems: 'center', padding: '24px', background: 'linear-gradient(135deg, #f5faf7, #edf4f2)',
};
const cardStyle = {
  width: 'min(100%, 580px)', padding: '42px', border: '1px solid #dce9e3', borderRadius: '18px', background: '#fff', boxShadow: '0 16px 45px rgba(16, 47, 56, .09)',
};

export default function AccessDeniedPage() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="loading-screen">Checking your session…</div>;
  const requestedPath = location.state?.from;
  return <main style={pageStyle}>
    <div style={cardStyle}>
      <div style={{ color: '#b7d9cc', fontSize: '72px', fontWeight: 900, lineHeight: 1, letterSpacing: '-4px' }}>403</div>
      <div className="page-kicker">ACCESS DENIED</div>
      <h1 style={{ fontSize: '30px', letterSpacing: '-1px', margin: '14px 0' }}>This page is not available to your account.</h1>
      <p style={{ color: '#647975', lineHeight: 1.7 }}>{requestedPath ? <>Your <code>{requestedPath}</code> request requires a different role.</> : 'You do not have permission to view this page.'} Server-side permissions are enforced for every private API request.</p>
      <Link className="primary-button" style={{ width: 'fit-content', gap: '24px', marginTop: '25px' }} to={user ? rolePath(user.role) : '/login'}>
        {user ? 'Return to my dashboard' : 'Go to sign in'} <span>→</span>
      </Link>
    </div>
  </main>;
}
