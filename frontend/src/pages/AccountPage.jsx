import { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../api.js';
import Layout from '../components/Layout.jsx';

export default function AccountPage() {
  const { user, updateProfile } = useAuth();
  const [name, setName] = useState(user.fullName);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const saveName = async (e) => {
    e.preventDefault(); setBusy(true); setMessage(''); setError('');
    try { await updateProfile(name); setMessage('Profile saved successfully.'); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  const savePassword = async (e) => {
    e.preventDefault(); setBusy(true); setMessage(''); setError('');
    if (newPassword !== confirmNewPassword) {
      setError('Your new password confirmation does not match.');
      setBusy(false);
      return;
    }
    try {
      await api('/api/auth/change-password', { method: 'PUT', body: { currentPassword, newPassword, confirmNewPassword } });
      setCurrentPassword(''); setNewPassword(''); setConfirmNewPassword(''); setMessage('Password changed successfully.');
    } catch (err) { setError(err.message); }
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
      <section className="panel"><h2>Change password</h2><p className="subtle">Your new password is securely hashed on the server.</p><form onSubmit={savePassword} className="auth-form">
        <label htmlFor="oldPassword">Current password</label><input id="oldPassword" type="password" autoComplete="current-password" required value={currentPassword} onChange={e=>setCurrentPassword(e.target.value)}/>
        <label htmlFor="newPassword">New password</label><input id="newPassword" type="password" autoComplete="new-password" required minLength="10" maxLength="72" value={newPassword} onChange={e=>setNewPassword(e.target.value)}/>
        <label htmlFor="confirmNewPassword">Confirm new password</label><input id="confirmNewPassword" type="password" autoComplete="new-password" required value={confirmNewPassword} onChange={e=>setConfirmNewPassword(e.target.value)}/>
        <p className="form-help">Use 10+ characters with uppercase, lowercase, a number, and a symbol. Never reuse your old password.</p>
        <button className="primary-button" disabled={busy} type="submit">Update password</button>
      </form></section>
    </div>
  </Layout>;
}
