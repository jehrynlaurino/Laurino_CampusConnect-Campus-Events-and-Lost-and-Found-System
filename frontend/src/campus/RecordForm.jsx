import {useEffect,useState} from 'react';
import {useNavigate,useParams} from 'react-router-dom';
import {api} from '../api.js';
import {Shell,Feedback,useRecord,apiTime,localInput,eventCategories,itemCategories} from './shared.jsx';
import CampusMap from './CampusMap.jsx';

export function RecordForm({kind}) {
  const {id}=useParams();
  const events=kind==='events',base='/api/'+kind;
  const {data,error:loadError}=useRecord(id?base+'/'+id:null);
  const [pin,setPin]=useState([null,null]),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const navigate=useNavigate();
  useEffect(()=>{setPin([data?.latitude??null,data?.longitude??null])},[data]);
  async function save(e) {
    e.preventDefault();
    const form=Object.fromEntries(new FormData(e.currentTarget));
    if(events){form.startsAt=apiTime(form.startsAt);form.endsAt=apiTime(form.endsAt);form.capacity=form.capacity?Number(form.capacity):null;
      if(form.endsAt<=form.startsAt){setError('End time must follow the start time.');return}
    }else{form.incidentAt=apiTime(form.incidentAt);if(Date.parse(form.incidentAt)>Date.now()){setError('Incident time cannot be in the future.');return}}
    [form.latitude,form.longitude]=pin.map(value=>value==null?null:Number(value));setBusy(true);setError('');
    try{const result=await api(base+(id?'/'+id:''),{method:id?'PUT':'POST',body:form});
      navigate('/'+kind+'/'+(id||result.id),{replace:true,state:{notice:id?'Changes saved.':events?'Event draft saved.':'Report saved. You can now add photos.'}})}
    catch(err){setError(err.message)} finally{setBusy(false)}
  }
  return <Shell><h1>{id?'Edit': 'Create'} {events?'event':'report'}</h1><Feedback error={error||loadError}/>
    {(!id || data) && <form className="panel record-form" onSubmit={save}>
      {!events && <label>Report type<select name="type" defaultValue={data?.type||'LOST'}><option>LOST</option><option>FOUND</option></select></label>}
      <label>Title<input name="title" required maxLength={160} defaultValue={data?.title}/></label>
      <label>Category<select name="category" required defaultValue={data?.category||''}><option value="">Choose category</option>{(events?eventCategories:itemCategories).map(c=><option key={c}>{c}</option>)}</select></label>
      <label>Description<textarea name="description" required maxLength={4000} rows={4} defaultValue={data?.description}/></label>
      {events ? <><label>Start (Philippine time)<input name="startsAt" type="datetime-local" required defaultValue={localInput(data?.starts_at)}/></label>
        <label>End (Philippine time)<input name="endsAt" type="datetime-local" required defaultValue={localInput(data?.ends_at)}/></label>
        <label>Capacity (optional)<input name="capacity" type="number" min="1" max="2147483647" defaultValue={data?.capacity}/></label></> :
        <label>Incident time (Philippine time)<input name="incidentAt" type="datetime-local" required max={localInput(Date.now())} defaultValue={localInput(data?.incident_at)}/></label>}
      <label>{events?'Venue':'Location'}<input name="location" required maxLength={200} defaultValue={data?.location}/></label>
      <CampusMap lat={pin[0]} lng={pin[1]} label="Campus location" onPick={(a,b)=>setPin([a,b])}/>
      {!events && !id && <p>After saving the report, you can attach up to three photos.</p>}
      <button disabled={busy}>{busy?'Saving…':id?'Save changes':events?'Save draft':'Save report'}</button>
    </form>}{id && !data && !loadError && <p>Loading record…</p>}</Shell>;
}
