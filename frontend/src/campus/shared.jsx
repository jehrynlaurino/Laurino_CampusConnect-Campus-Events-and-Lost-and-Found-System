import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import Layout from '../components/Layout.jsx';
import { api } from '../api.js';
import './campus.css';

export const dateLabel = value => value ? new Intl.DateTimeFormat('en-PH',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Manila'}).format(new Date(value))+' PHT' : '';
export const localInput = value => value ? new Date(new Date(value).getTime()+8*3600000).toISOString().slice(0,16) : '';
export const apiTime = value => value ? value+':00+08:00' : null;
export function useRecord(url,version=0) {
  const [state,set] = useState({url:null});
  useEffect(()=>{
    let active=true;
    if(url) {
      set({url});
      api(url).then(data=>{if(active)set({url,data})}).catch(e=>{if(active)set({url,error:e.message})});
    }
    return ()=>{active=false};
  },[url,version]);
  return state.url===url ? state : {};
}
export function Shell({children}) {
  const {user,loading}=useAuth();
  if(loading) return <main className="loading-screen">Loading CampusConnect…</main>;
  return user ? <Layout><div className="campus">{children}</div></Layout> :
    <main className="content campus"><nav className="catalog-nav"><Link to="/events">Campus events</Link><Link to="/reports">Lost and found</Link><Link to="/login">Sign in</Link></nav>{children}</main>;
}
export function Feedback({error,notice}) {
  return <>{error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status" className="success-message">{notice}</p>}</>;
}
export const itemCategories=['Electronics','Identification Cards','School Supplies','Bags and Wallets','Clothing','Keys','Others'];
export const eventCategories=['Academic','Arts','Community','Sports','Student life','Others'];
export function availability(event) {
  if(event.hidden || event.status!=='PUBLISHED' || Date.parse(event.starts_at)<=Date.now()) return 'Registration closed';
  if(event.capacity==null) return 'Open registration';
  const left=event.capacity-event.registered;
  return left===0 ? 'Full' : left+' of '+event.capacity+' places available';
}
