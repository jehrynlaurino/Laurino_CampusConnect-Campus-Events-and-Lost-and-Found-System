import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../context/AuthContext.jsx';
import Layout from '../components/Layout.jsx';

const details = {
  MEMBER: {
    kicker: 'MEMBER PORTAL', title: 'Your campus, connected.', subtitle: 'A home for campus events and lost-and-found. Your account is ready.',
    icon: '♧', heading: 'Explore your campus',
    items: [
      { icon: '▣', title: 'Campus Events', text: 'Discover and register for activities.', href: '/events' },
      { icon: '⌕', title: 'Lost & Found', text: 'Report, search and locate missing items.', href: '/reports' },
    ],
  },
  ORGANIZER: {
    kicker: 'ORGANIZER PORTAL', title: 'Bring your campus together.', subtitle: 'You have been approved to organize campus activities.',
    icon: '✺', heading: 'Organizer workspace',
    items: [
      { icon: '▣', title: 'Event Management', text: 'Create and publish campus events.', href: '/events?scope=mine' },
      { icon: '♧', title: 'Event Registrations', text: 'View active registration counts.', href: '/events?scope=mine' },
    ],
  },
  ADMIN: {
    kicker: 'ADMINISTRATOR PORTAL', title: 'Keep campus connected.', subtitle: 'Manage accounts, roles and access for the CampusConnect community.',
    icon: '✧', heading: 'Administration tools',
    items: [
      { icon: '♙', title: 'User Management', text: 'View accounts, assign roles, and change access.', stage: 'AVAILABLE', href: '/admin/users' },
      { icon: '▣', title: 'Content Moderation', text: 'Review campus events and item reports.', href: '/reports?scope=admin' },
    ],
  },
};

export default function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const config = details[user.role];
  useEffect(() => {
    let cancelled = false;
    api(`/api/dashboard/${user.role.toLowerCase()}`).then(d => { if (!cancelled) setData(d); })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [user.role]);
  return <Layout>
    <div className="page-kicker">{config.kicker} <span>● AUTHENTICATED</span></div>
    <section className="welcome-banner"><div className="banner-content"><p>WELCOME BACK, {user.fullName.split(' ')[0].toUpperCase()}</p><h1>{config.title}</h1><span>{config.subtitle}</span></div><div className="banner-illustration" aria-hidden="true">{config.icon}</div></section>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="stats-grid">
      <section className="stat-card"><div className="stat-label">YOUR ROLE</div><strong>{user.role}</strong><small>Verified from your database account</small></section>
      <section className="stat-card"><div className="stat-label">ACCOUNT STATUS</div><strong><span className="status-dot"/> Active</strong><small>Your profile is enabled</small></section>
      <section className="stat-card"><div className="stat-label">AUTHENTICATION</div><strong>{data ? 'Verified' : 'Checking…'}</strong><small>{data ? 'Backend-authorized session' : 'Contacting secured endpoint'}</small></section>
    </div>
    <div className="section-title"><div><div className="section-label">GET STARTED</div><h2>{config.heading}</h2></div><Link to="/account" className="text-link">Manage my profile →</Link></div>
    <div className="feature-grid">{config.items.map(item => <article className="feature-card" key={item.title}><div className="feature-icon">{item.icon}</div><h3>{item.title}</h3><p>{item.text}</p>{<Link to={item.href} className="feature-link">Open tool →</Link>}</article>)}</div>
  </Layout>;
}
