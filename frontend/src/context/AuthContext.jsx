import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, refreshCsrf, setUnauthorizedHandler } from '../api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const checkSession = useCallback(async () => {
    try {
      await refreshCsrf();
      const me = await api('/api/auth/me');
      setUser(me);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { checkSession(); }, [checkSession]);
  useEffect(() => setUnauthorizedHandler(() => {
    setUser(null);
    setLoading(false);
  }), []);

  const login = async (email, password) => {
    const data = await api('/api/auth/login', {
      method: 'POST',
      body: new URLSearchParams({ email: email.trim().toLowerCase(), password }),
    });
    setUser(data);
    await refreshCsrf(); // Spring Security rotates the token on login.
    return data;
  };

  const register = async ({ fullName, email, password, confirmPassword }) =>
    api('/api/auth/register', { method: 'POST', body: { fullName, email, password, confirmPassword } });

  const logout = async () => {
    try {
      await api('/api/auth/logout', { method: 'POST' });
    } finally {
      // Clear the UI even when an expired session causes logout to return 401.
      setUser(null);
      try { await refreshCsrf(); } catch { /* The next API write will retry. */ }
    }
  };

  const updateProfile = async (fullName) => {
    const updated = await api('/api/auth/profile', { method: 'PUT', body: { fullName } });
    setUser(updated);
    return updated;
  };

  const changePassword = async ({ currentPassword, newPassword, confirmNewPassword }) => {
    const response = await api('/api/auth/change-password', {
      method: 'PUT', body: { currentPassword, newPassword, confirmNewPassword },
    });
    setUser(null);
    try { await refreshCsrf(); } catch { /* A new token will be requested before the next write. */ }
    return response;
  };

  return <AuthContext.Provider value={{ user, loading, login, register, logout, updateProfile, changePassword, checkSession }}>
    {children}
  </AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be inside AuthProvider');
  return value;
}
