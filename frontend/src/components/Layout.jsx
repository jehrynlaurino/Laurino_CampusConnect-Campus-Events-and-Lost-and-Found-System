import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { rolePath } from './ProtectedRoute.jsx';
import { useAuth } from '../context/AuthContext.jsx';

const roleText = { MEMBER: 'Campus member', ORGANIZER: 'Event organizer', ADMIN: 'Campus administrator' };

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const signOut = async () => {
    setBusy(true); setError('');
    try { await logout(); navigate('/login', { replace: true }); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  return <div className="app-shell">
    <aside className="sidebar">
      <Link className="brand" to={rolePath(user.role)}><span className="brand-mark">C<span>✦</span></span><span>Campus<span className="brand-green">Connect</span></span></Link>
      <div className="sidebar-caption">YOUR WORKSPACE</div>
      <nav aria-label="Main navigation" className="side-nav">
        <div className="module-links">
          <Link to="/events">Campus events</Link>
          {user.role==='MEMBER' && <Link to="/events?scope=registrations">My registrations</Link>}
          {user.role!=='MEMBER' && <><Link to="/events/new">Create event</Link><Link to="/events?scope=mine">My events &amp; counts</Link></>}
          <Link to="/reports">Lost and found</Link><Link to="/reports/new">Create report</Link><Link to="/reports?scope=mine">My reports</Link>
          {user.role==='ADMIN' && <><Link to="/events?scope=admin">Moderate events</Link><Link to="/reports?scope=admin">Moderate reports</Link></>}
        </div>
        <NavLink to={rolePath(user.role)} className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>◫ <span>Dashboard</span></NavLink>
        <NavLink to="/account" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>◎ <span>My account</span></NavLink>
        {user.role === 'ADMIN' && <NavLink to="/admin/users" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>♙ <span>User management</span></NavLink>}
        <NavLink to="/change-password" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>Password <span>Change password</span></NavLink>
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-status"><span className="status-dot"/> Campus workspace</div>
        <button className="logout" onClick={signOut} disabled={busy}>↪ {busy ? 'Signing out...' : 'Sign out'}</button>
        {error && <p className="inline-error" role="alert">{error}</p>}
      </div>
    </aside>
    <div className="main-area">
      <header className="topbar"><div className="eyebrow">CAMPUSCONNECT / PORTAL</div><div className="profile-chip"><span className="avatar">{user.fullName.slice(0, 1).toUpperCase()}</span><span><strong>{user.fullName}</strong><small>{roleText[user.role]}</small></span></div></header>
      <main className="content">{children}</main>
    </div>
  </div>;
}
