import {useState} from 'react';
import {Link,useSearchParams} from 'react-router-dom';
import {useAuth} from '../context/AuthContext.jsx';
import {api} from '../api.js';
import {Shell,Feedback,useRecord,availability,dateLabel,itemCategories,eventCategories} from './shared.jsx';

export function Catalog({kind}) {
  const events=kind==='events';
  const {user}=useAuth();
  const [params,setParams]=useSearchParams();
  const scope=params.get('scope')||'public',page=Number(params.get('page'))||0;
  const registrations=events && scope==='registrations';
  const [version,setVersion]=useState(0),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const url=registrations ? '/api/events/registrations/me?page='+page : '/api/'+kind+'?'+params;
  const {data,error:loadError}=useRecord(url,version);
  const changePage=n=>{const p=new URLSearchParams(params);p.set('page',n);setParams(p)};
  const search=e=>{e.preventDefault();const p=new URLSearchParams(new FormData(e.currentTarget));p.set('scope',scope);setParams(p)};
  const title=registrations?'My registrations':scope==='admin'?(events?'Event moderation':'Report moderation'):scope==='mine'?(events?'My events':'My reports'):(events?'Campus events':'Lost and found');
  const cancel=async id=>{
    if(!confirm('Cancel your registration and release this place?'))return;
    try{await api('/api/events/'+id+'/registrations/me',{method:'DELETE'});setNotice('Registration cancelled.');setVersion(v=>v+1)}
    catch(e){setError(e.message)}
  };
  return <Shell><header className="catalog-header"><h1>{title}</h1>
    {user && (!events || user.role!=='MEMBER') && !registrations && <Link className="secondary-button" to={'/'+kind+'/new'}>{events?'Create event':'Create report'}</Link>}</header>
    {!registrations && <form className="filters" key={scope+params.toString()} onSubmit={search}>
      <label>Keywords<input name="q" defaultValue={params.get('q')||''} maxLength="120"/></label>
      <label>Category<select name="category" defaultValue={params.get('category')||''}><option value="">All categories</option>{(events?eventCategories:itemCategories).map(c=><option key={c}>{c}</option>)}</select></label>
      {events ? <>{['from','to'].map(k=><label key={k}>{k==='from'?'From date':'Through date'}<input name={k} type="date" defaultValue={params.get(k)||''}/></label>)}</> :
        <>{[['type',['LOST','FOUND']],['status',['OPEN','RESOLVED',...(scope==='public'?[]:['CLOSED'])]]].map(([k,options])=><label key={k}>{k==='type'?'Report type':'Status'}<select name={k} defaultValue={params.get(k)||''}><option value="">All</option>{options.map(o=><option key={o}>{o}</option>)}</select></label>)}</>}
      <button>Search</button></form>}
    <Feedback error={error||loadError} notice={notice}/>
    {!data && !loadError && <p role="status">Loading records…</p>}
    {data?.length===0 && <p>No matching records.</p>}
    <div className="record-grid">{data?.map(row=><article key={row.id} className="panel">
      {row.thumbnail && <img className="item-photo" src={row.thumbnail} alt={row.title}/>}
      <small>{[row.type,row.category,row.status,row.hidden?'HIDDEN':null].filter(Boolean).join(' · ')}</small>
      <h2>{registrations && (row.hidden || row.status==='CANCELLED') ? row.title : <Link to={'/'+kind+'/'+row.id}>{row.title}</Link>}</h2>
      <p>{dateLabel(row.starts_at||row.incident_at)} {row.location && ' · '+row.location}</p>
      {events && !registrations && <p>{availability(row)}{scope!=='public' && ' · '+row.registered+' registered'}</p>}
      {registrations && <><p>Registration: {row.registration_status}</p>{row.registration_status==='ACTIVE' && Date.parse(row.starts_at)>Date.now() && <button onClick={()=>cancel(row.id)}>Cancel registration</button>}</>}
    </article>)}</div>
    {(page>0 || data?.length===20) && <nav className="catalog-nav"><button disabled={!page} onClick={()=>changePage(page-1)}>Previous</button><span>Page {page+1}</span><button disabled={data?.length!==20} onClick={()=>changePage(page+1)}>Next</button></nav>}
  </Shell>;
}
