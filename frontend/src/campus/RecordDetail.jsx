import {useEffect,useState} from 'react';
import {Link,useLocation,useParams} from 'react-router-dom';
import {api} from '../api.js';
import {useAuth} from '../context/AuthContext.jsx';
import {Shell,Feedback,availability,dateLabel,useRecord} from './shared.jsx';
import CampusMap from './CampusMap.jsx';

function PhotoEditor({record,refresh}) {
  const [file,setFile]=useState(null),[preview,setPreview]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  useEffect(()=>{
    if(!file){setPreview('');return}
    const url=URL.createObjectURL(file);setPreview(url);return ()=>URL.revokeObjectURL(url);
  },[file]);
  async function upload() {
    if(!file)return;
    setBusy(true);setError('');
    const form=new FormData();form.set('file',file);
    try{await api('/api/reports/'+record.id+'/images',{method:'POST',body:form});setFile(null);refresh()}
    catch(e){setError(e.message)}finally{setBusy(false)}
  }
  const select=e=>{
    const item=e.target.files?.[0];e.target.value='';setError('');setFile(null);
    if(!item)return;
    if(!['image/jpeg','image/png','image/webp'].includes(item.type)||item.size>5*1024*1024){setError('Select a JPG, PNG or WebP photo up to 5 MB.');return}
    setFile(item);
  };
  return <section><h2>Report photos</h2><Feedback error={error}/>
    {record.images.length<3 && <label>Choose a photo<input type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={select}/></label>}
    <p>{record.images.length} of 3 photos</p>
    {file && <><img className="item-photo" src={preview} alt="Selected item photo"/><button disabled={busy} onClick={upload}>{busy?'Uploading…':error?'Retry upload':'Upload photo'}</button><button disabled={busy} onClick={()=>setFile(null)}>Discard selection</button></>}
  </section>;
}
export function RecordDetail({kind}) {
  const {id}=useParams(),{user}=useAuth(),{state}=useLocation();
  const [version,setVersion]=useState(0),[message,setMessage]=useState(state?.notice||''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const base='/api/'+kind+'/'+id,{data:r,error:loadError}=useRecord(base,version);
  const events=kind==='events',admin=user?.role==='ADMIN',owner=!!user && user.id===r?.owner_id;
  const manageEvent=events && (admin || (owner && user.role==='ORGANIZER'));
  const editReport=!events && owner && r?.status==='OPEN' && !r.hidden;
  async function act(path,method,body,notice,confirmText) {
    if(confirmText && !confirm(confirmText))return;
    setBusy(true);setError('');setMessage('');
    try{await api(base+path,{method,body});setMessage(notice);setVersion(x=>x+1)}
    catch(e){setError(e.message)}finally{setBusy(false)}
  }
  const decision=(action,label,ask)=> <button disabled={busy} onClick={()=>act('/state','PATCH',{action},label+' saved.',ask)}>{label}</button>;
  return <Shell><Link to={'/'+kind}>← {events?'Campus events':'Lost and found'}</Link><Feedback error={error||loadError} notice={message}/>
    {r ? <article className="panel record-detail"><small>{[r.type,r.category,r.status,r.hidden?'HIDDEN':null].filter(Boolean).join(' · ')}</small><h1>{r.title}</h1>
      <p className="description">{r.description}</p><dl><dt>{events?'Venue':'Location'}</dt><dd>{r.location}</dd>
      {events?<><dt>Organizer</dt><dd>{r.organizer}</dd><dt>When</dt><dd>{dateLabel(r.starts_at)} – {dateLabel(r.ends_at)}</dd><dt>Registration</dt><dd>{availability(r)} · {r.registered} registered</dd></>:
       <><dt>Incident date</dt><dd>{dateLabel(r.incident_at)}</dd></>}</dl>
      <CampusMap lat={r.latitude} lng={r.longitude} label={r.title+' · '+r.location}/>
      <div className="actions">
        {((manageEvent && r.status!=='CANCELLED') || editReport) && <Link className="secondary-button" to={'/'+kind+'/'+id+'/edit'}>Edit {events?'event':'report'}</Link>}
        {manageEvent && r.status==='DRAFT' && decision('publish','Publish event','Publish this event to the campus listing?')}
        {manageEvent && r.status!=='CANCELLED' && decision('cancel','Cancel event','Cancel this event and close registration?')}
        {events && user?.role==='MEMBER' && (r.my_registration==='ACTIVE'?
          <><strong>Your registration is active.</strong>{Date.parse(r.starts_at)>Date.now() && <button disabled={busy} onClick={()=>act('/registrations/me','DELETE',undefined,'Registration cancelled.','Release your place?')}>Cancel my registration</button>}</>:
          <button disabled={busy || r.hidden || r.status!=='PUBLISHED' || Date.parse(r.starts_at)<=Date.now() || (r.capacity!=null && r.registered>=r.capacity)} onClick={()=>act('/registrations','POST',undefined,'Registration confirmed.')}>Register for event</button>)}
        {!user && <Link to="/login">Sign in to participate</Link>}
        {!events && (owner || admin) && r.status==='OPEN' && decision('resolve','Mark resolved','Mark this item as located or returned?')}
        {admin && decision(r.hidden?'show':'hide',r.hidden?'Restore visibility':'Hide from listings','Change campus listing visibility?')}
        {!events && admin && r.status!=='CLOSED' && decision('close','Close report','Close this report and remove it from listings?')}
      </div>
      {!events && <div className="actions">{r.images.map(image=><figure key={image.id}><img src={image.secure_url} className="item-photo" alt={r.title}/>{editReport && <button disabled={busy} onClick={()=>act('/images/'+image.id,'DELETE',undefined,'Photo removed.','Remove this photo?')}>Remove photo</button>}</figure>)}</div>}
      {editReport && <PhotoEditor record={r} refresh={()=>setVersion(v=>v+1)}/>}
    </article>:!loadError&&<p>Loading details…</p>}</Shell>;
}
