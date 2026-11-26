import {useEffect,useRef,useState} from 'react';
import {useRecord} from './shared.jsx';

let loader;
function loadMaps(key) {
  if(window.google?.maps?.importLibrary) return Promise.resolve();
  if(!loader) loader=new Promise((resolve,reject)=>{
    const script=document.createElement('script');
    const fail=()=>reject(new Error('Map unavailable'));
    const timer=setTimeout(fail,12000);
    window.campusMapReady=()=>{clearTimeout(timer);resolve()};
    window.gm_authFailure=()=>{fail();window.dispatchEvent(new Event('campus-map-error'))};
    script.onerror=fail; script.async=true;
    script.src='https://maps.googleapis.com/maps/api/js?'+new URLSearchParams({key,loading:'async',callback:'campusMapReady',v:'weekly'});
    document.head.appendChild(script);
  });
  return loader;
}
export default function CampusMap({lat,lng,label,onPick}) {
  const {data:config,error}=useRecord('/api/campus/map');
  const [failed,setFailed]=useState(false);
  const host=useRef(null), callback=useRef(onPick), markerRef=useRef(null);
  callback.current=onPick;
  const editable=!!onPick;
  const hasPoint=lat!=null && lng!=null;
  const point=hasPoint?{lat:Number(lat),lng:Number(lng)}:null;
  const current=useRef(null);
  current.current={point,label};
  const showMap=editable || hasPoint;
  useEffect(()=>{
    if(!config?.key || !showMap) return;
    let active=true, listener;
    const onFailure=()=>{if(active)setFailed(true)};
    window.addEventListener('campus-map-error',onFailure);
    loadMaps(config.key).then(async()=>{
      const [{Map},{AdvancedMarkerElement}]=await Promise.all([google.maps.importLibrary('maps'),google.maps.importLibrary('marker')]);
      if(!active || !host.current)return;
      const {point,label}=current.current;
      const map=new Map(host.current,{center:point||config.center,zoom:17,mapId:config.mapId,restriction:{latLngBounds:config.bounds,strictBounds:true}});
      const marker=new AdvancedMarkerElement({map,position:point,title:label});
      markerRef.current=marker;
      if(editable) listener=map.addListener('click',e=>{if(e.latLng)callback.current(e.latLng.lat(),e.latLng.lng())});
    }).catch(onFailure);
    return ()=>{active=false;listener?.remove();if(markerRef.current)markerRef.current.map=null;markerRef.current=null;window.removeEventListener('campus-map-error',onFailure)};
  },[config,editable,showMap]);
  useEffect(()=>{if(markerRef.current){markerRef.current.position=current.current.point;markerRef.current.title=label}},[lat,lng,label]);
  if(!editable && !hasPoint) return null;
  return <section aria-label="Campus map">
    {config?.key && !failed ? <div className="map-canvas" ref={host} aria-label={label || 'Choose a campus location'}/> :
      <p className="form-help">{failed || error || config ? 'Map unavailable. The written location is available.' : 'Loading map settings…'}</p>}
    {editable && <fieldset><legend>Campus pin (optional)</legend>
      <label>Latitude<input type="number" step="any" min={config?.bounds.south} max={config?.bounds.north}
        value={lat??''} required={lng!=null} onChange={e=>onPick(e.target.value||null,lng)}/></label>
      <label>Longitude<input type="number" step="any" min={config?.bounds.west} max={config?.bounds.east}
        value={lng??''} required={lat!=null} onChange={e=>onPick(lat,e.target.value||null)}/></label>
      {(lat!=null || lng!=null) && <button type="button" onClick={()=>onPick(null,null)}>Remove pin</button>}</fieldset>}
    {!editable && hasPoint && <p>Saved pin: {point.lat.toFixed(5)}, {point.lng.toFixed(5)}</p>}
  </section>;
}
