import { useEffect, useState } from 'react';
import Layout from '../components/Layout.jsx';
import { api } from '../api.js';
import { useAuth } from '../context/AuthContext.jsx';

export default function AdminUsersPage() {
  const { user } = useAuth();
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(null);
  const load = () => api('/api/admin/users').then(setUsers).catch(err=>setError(err.message));
  useEffect(()=>{load();}, []);

  const update = async (id, kind, value) => {
    setBusy(id); setError(''); setMessage('');
    try {
      const result = await api(`/api/admin/users/${id}/${kind}`, { method: 'PATCH', body: { [kind==='role' ? 'role' : 'active']: value } });
      setUsers(list=>list.map(u=>u.id===id ? result : u));
      setMessage('Account updated in the database. Role changes take effect on the user’s next request or page refresh.');
    } catch (err) { setError(err.message); }
    finally { setBusy(null); }
  };

  return <Layout>
    <div className="page-kicker">ADMINISTRATOR / USER MANAGEMENT</div>
    <h1 className="page-title">Manage campus accounts</h1>
    <p className="page-description">Only administrators can access this page or its protected API endpoints.</p>
    {message && <p className="success-message" role="status">{message}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="panel"><div className="table-heading"><div><h2>Registered users</h2><p className="subtle">{users.length} accounts • live PostgreSQL records</p></div><button className="secondary-button" onClick={load}>Refresh list ↻</button></div>
      <div className="table-scroll"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Account status</th></tr></thead><tbody>{users.map(u=><tr key={u.id}>
        <td><strong>{u.fullName}</strong>{u.id===user.id && <small className="you-label"> (You)</small>}</td>
        <td>{u.email}</td>
        <td><label className="sr-only" htmlFor={`role-${u.id}`}>Role for {u.fullName}</label><select id={`role-${u.id}`} value={u.role} disabled={busy===u.id || u.id===user.id} onChange={e=>update(u.id,'role',e.target.value)}><option value="MEMBER">MEMBER</option><option value="ORGANIZER">ORGANIZER</option><option value="ADMIN">ADMIN</option></select></td>
        <td><label className="sr-only" htmlFor={`status-${u.id}`}>Account status for {u.fullName}</label><select id={`status-${u.id}`} value={String(u.active)} disabled={busy===u.id || u.id===user.id} onChange={e=>update(u.id,'status',e.target.value==='true')}><option value="true">Active</option><option value="false">Deactivated</option></select></td>
      </tr>)}</tbody></table></div>
      <p className="table-note">For safety, you cannot change your own admin role or deactivate your own account.</p>
    </div>
  </Layout>;
}
