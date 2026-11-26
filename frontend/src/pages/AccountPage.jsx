import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import Layout from '../components/Layout.jsx';

export default function AccountPage() {
  const { user, updateProfile } = useAuth();
  const [name, setName] = useState(user.fullName);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const saveName = async (e) => {
    e.preventDefault(); setBusy(true); setMessage(''); setError('');
    try { await updateProfile(name); setMessage('Profile saved successfully.'); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  return <Layout>
    <div className="page-kicker">ACCOUNT SETTINGS</div><h1 className="page-title">My account</h1><p className="page-description">Update your personal details and keep your account secure.</p>
    {message && <p className="success-message" role="status">{message}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="settings-grid">
      <section className="panel"><h2>Profile details</h2><p className="subtle">Changes are saved to PostgreSQL, not just your browser.</p><form onSubmit={saveName} className="auth-form">
        <label htmlFor="profileName">Full name</label><input id="profileName" value={name} minLength="2" maxLength="120" required onChange={e=>setName(e.target.value)}/>
        <label htmlFor="profileEmail">Email address</label><input id="profileEmail" value={user.email} disabled/><small className="form-help">Email address is fixed for this increment.</small>
        <label>Account role</label><div className="readonly-field">{user.role}</div>
        <button className="primary-button" disabled={busy} type="submit">Save changes</button>
      </form></section>
      <section className="panel"><h2>Password &amp; security</h2><p className="subtle">Change your password using your current credentials. Updating it signs out every existing CampusConnect session.</p>
        <Link className="primary-button" to="/change-password">Change password <span>→</span></Link>
      </section>
    </div>
  </Layout>;
}
