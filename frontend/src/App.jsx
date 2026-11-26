import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext.jsx';
import ProtectedRoute, { rolePath } from './components/ProtectedRoute.jsx';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage } from './pages/AuthPages.jsx';
import DashboardPage from './pages/DashboardPages.jsx';
import AccountPage from './pages/AccountPage.jsx';
import AdminUsersPage from './pages/AdminUsersPage.jsx';
import AccessDeniedPage from './pages/AccessDeniedPage.jsx';
import ChangePasswordPage from './pages/ChangePasswordPage.jsx';
import {Catalog} from './campus/Catalog.jsx';
import {RecordDetail} from './campus/RecordDetail.jsx';
import {RecordForm} from './campus/RecordForm.jsx';

function HomeRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <div className="loading-screen">Loading CampusConnect…</div>;
  return <Navigate to={user ? rolePath(user.role) : '/login'} replace />;
}

export default function App() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route path="/" element={<HomeRedirect/>}/>
    <Route path="/login" element={<LoginPage/>}/>
    <Route path="/register" element={<RegisterPage/>}/>
    <Route path="/forgot-password" element={<ForgotPasswordPage/>}/>
    <Route path="/reset-password" element={<ResetPasswordPage/>}/>
    <Route path="/unauthorized" element={<AccessDeniedPage/>}/>
    {['events','reports'].map(kind=><Route key={kind} path={kind}>
      <Route index element={<Catalog key={kind} kind={kind}/>}/>
      <Route path=":id" element={<RecordDetail key={kind} kind={kind}/>}/>
      <Route element={<ProtectedRoute roles={kind==='events'?['ADMIN','ORGANIZER']:['MEMBER','ADMIN','ORGANIZER']}/>}>
        <Route path="new" element={<RecordForm key={kind+'new'} kind={kind}/>}/>
        <Route path=":id/edit" element={<RecordForm key={kind+'edit'} kind={kind}/>}/>
      </Route>
    </Route>)}
    <Route element={<ProtectedRoute/>}>
      <Route path="/dashboard" element={<HomeRedirect/>}/>
      <Route path="/account" element={<AccountPage/>}/>
      <Route path="/change-password" element={<ChangePasswordPage/>}/>
    </Route>
    <Route element={<ProtectedRoute roles={['MEMBER']}/>}> <Route path="/dashboard/member" element={<DashboardPage/>}/> </Route>
    <Route element={<ProtectedRoute roles={['ORGANIZER']}/>}> <Route path="/dashboard/organizer" element={<DashboardPage/>}/> </Route>
    <Route element={<ProtectedRoute roles={['ADMIN']}/>}> <Route path="/dashboard/admin" element={<DashboardPage/>}/> <Route path="/admin/users" element={<AdminUsersPage/>}/> </Route>
    <Route path="*" element={<HomeRedirect/>}/>
  </Routes></AuthProvider></BrowserRouter>;
}
