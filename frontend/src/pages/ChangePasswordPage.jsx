import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../context/AuthContext.jsx';
import Layout from '../components/Layout.jsx';

function PasswordField({ id, label, value, onChange, autoComplete }) {
  const [shown, setShown] = useState(false);
  return <>
    <label htmlFor={id}>{label}</label>
    <div className="password-field"><input id={id} type={shown ? 'text' : 'password'} autoComplete={autoComplete} required minLength="10" maxLength="72" value={value} onChange={e => onChange(e.target.value)} />
      <button className="password-toggle" type="button" onClick={() => setShown(v => !v)} aria-label={`${shown ? 'Hide' : 'Show'} ${label.toLowerCase()}`}>{shown ? 'Hide' : 'Show'}</button>
    </div>
  </>;
}

export default function ChangePasswordPage() {
  const { changePassword } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault(); setError('');
    if (newPassword !== confirmNewPassword) return setError('Your new password confirmation does not match.');
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/.test(newPassword)) {
      return setError('Password must be 10-72 characters and include uppercase, lowercase, number, and symbol.');
    }
    setBusy(true);
    try {
      await changePassword({ currentPassword, newPassword, confirmNewPassword });
      navigate('/login', { state: { passwordChanged: true }, replace: true });
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  return <Layout><div className="page-kicker">ACCOUNT SETTINGS / SECURITY</div><h1 className="page-title">Change password</h1><p className="page-description">For your protection, changing your password signs out all active sessions.</p>
    <section className="panel password-panel"><form onSubmit={submit} className="auth-form">
      <PasswordField id="currentPassword" label="Current password" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" />
      <PasswordField id="newPassword" label="New password" value={newPassword} onChange={setNewPassword} autoComplete="new-password" />
      <PasswordField id="confirmNewPassword" label="Confirm new password" value={confirmNewPassword} onChange={setConfirmNewPassword} autoComplete="new-password" />
      <p className="form-help">Use 10+ characters with uppercase, lowercase, a number, and a symbol. Your new password must be different from the current one.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Updating…' : 'Update password'} <span>→</span></button>
    </form></section>
  </Layout>;
}
