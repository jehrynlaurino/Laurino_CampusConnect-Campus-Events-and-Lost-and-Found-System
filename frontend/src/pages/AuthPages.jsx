import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { rolePath } from '../components/ProtectedRoute.jsx';

function AuthShell({ title, subtitle, children }) {
  return <div className="auth-shell">
    <section className="auth-aside">
      <div className="landing-brand">C<span>✦</span> <strong>CampusConnect</strong></div>
      <div className="aside-content">
        <p className="tag">ONE CAMPUS. ONE CONNECTION.</p>
        <h1>Everything happening on campus, all in one place.</h1>
        <p>Discover campus events, connect with your community, and make lost-and-found easier for everyone.</p>
        <div className="aside-feature"><span>◎</span><span>Campus Events</span><span className="separator"/><span>⌑</span><span>Lost &amp; Found</span></div>
      </div>
      <small>CampusConnect • Systems Integration &amp; Architecture 1</small>
    </section>
    <main className="auth-main"><div className="auth-card"><div className="mobile-brand">✦ CampusConnect</div><div className="auth-topmark">◉ STUDENT PORTAL</div><h2>{title}</h2><p className="subtle">{subtitle}</p>{children}</div></main>
  </div>;
}

export function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to={rolePath(user.role)} replace />;
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('');
    try {
      const account = await login(email, password);
      const desired = location.state?.from;
      navigate(desired && desired.startsWith('/account') ? desired : rolePath(account.role), { replace: true });
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  return <AuthShell title="Welcome back." subtitle="Sign in to your campus account to continue.">
    <form onSubmit={submit} className="auth-form">
      <label htmlFor="email">Email address</label><input id="email" type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} placeholder="student@campus.edu" maxLength="254" />
      <label htmlFor="password">Password</label><input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" />
      {location.state?.registered && <p className="success-message" role="status">Account created! Sign in using your new credentials.</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'} <span>→</span></button>
    </form>
    <p className="auth-footer">New to CampusConnect? <Link to="/register">Create an account</Link></p>
  </AuthShell>;
}

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to={rolePath(user.role)} replace />;
  const set = (key, value) => setForm(f => ({ ...f, [key]: value }));
  const submit = async (e) => {
    e.preventDefault(); setError('');
    if (form.password !== form.confirmPassword) return setError('Your passwords do not match.');
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/.test(form.password)) {
      return setError('Password must be 10-72 characters and include uppercase, lowercase, number, and symbol.');
    }
    setBusy(true);
    try {
      await register(form);
      navigate('/login', { state: { registered: true }, replace: true });
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };
  return <AuthShell title="Join CampusConnect." subtitle="Create your account using your campus email.">
    <form onSubmit={submit} className="auth-form">
      <label htmlFor="fullName">Full name</label><input id="fullName" autoComplete="name" required minLength="2" maxLength="120" placeholder="Your full name" value={form.fullName} onChange={e => set('fullName', e.target.value)} />
      <label htmlFor="regEmail">Email address</label><input id="regEmail" type="email" autoComplete="username" required placeholder="student@campus.edu" value={form.email} onChange={e => set('email', e.target.value)} />
      <label htmlFor="regPassword">Password</label><input id="regPassword" type="password" autoComplete="new-password" required minLength="10" maxLength="72" placeholder="At least 10 characters" value={form.password} onChange={e => set('password', e.target.value)} />
      <label htmlFor="confirm">Confirm password</label><input id="confirm" type="password" autoComplete="new-password" required placeholder="Repeat your password" value={form.confirmPassword} onChange={e => set('confirmPassword', e.target.value)} />
      <p className="form-help">Use 10+ characters with uppercase, lowercase, a number, and a symbol.</p>
      <p className="form-help">New accounts are Members. Organizer access is assigned by a campus administrator.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Creating account...' : 'Create account'} <span>→</span></button>
    </form>
    <p className="auth-footer">Already have an account? <Link to="/login">Sign in</Link></p>
  </AuthShell>;
}
