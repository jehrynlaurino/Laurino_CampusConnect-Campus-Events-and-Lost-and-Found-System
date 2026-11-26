"""Run disposable PostgreSQL and browser checks on macOS or Windows. See TESTING.md."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import ExitStack
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import mkdtemp
import json
import os
import secrets
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
from xml.etree import ElementTree
from playwright.sync_api import sync_playwright, expect
from email import message_from_bytes, policy
from queue import Queue
import re

ROOT = Path(__file__).resolve().parents[1]
SUFFIX = ".exe" if os.name == "nt" else ""
FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0)
pg_bin = os.environ.get("PREVIEW_PG_BIN")
if not pg_bin and shutil.which("initdb"):
    pg_bin = Path(shutil.which("initdb")).parent
if not pg_bin and shutil.which("brew"):
    prefix = subprocess.check_output(["brew", "--prefix", "postgresql@16"], text=True).strip()
    pg_bin = Path(prefix) / "bin"
if not pg_bin:
    raise SystemExit("Set PREVIEW_PG_BIN to the PostgreSQL 16 bin directory.")
PG = Path(pg_bin)
java_home = os.environ.get("JAVA_HOME")
JAVA = Path(java_home) / "bin" / ("java" + SUFFIX) if java_home else "java"
CHROME = os.environ.get("PREVIEW_CHROME")
mac_chrome = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
if not CHROME and mac_chrome.is_file():
    CHROME = str(mac_chrome)
work = Path(mkdtemp(prefix="campus-inc3-"))
evidence = Path(os.environ.get("PREVIEW_EVIDENCE", work / "evidence"))
evidence.mkdir(parents=True, exist_ok=True)
password = "Cc!8" + secrets.token_hex(16)
admin_email = "admin@example.test"
pht = timezone(timedelta(hours=8))
when = datetime.now(pht) + timedelta(days=4)
future = when.strftime("%Y-%m-%dT10:00")
end = when.strftime("%Y-%m-%dT12:00")
incident = (when - timedelta(days=5)).strftime("%Y-%m-%dT09:30")
inbox = Queue()
class Mailbox:
    async def handle_DATA(self, server, session, envelope):
        inbox.put(message_from_bytes(envelope.original_content, policy=policy.default))
        return "250 Accepted"

def free_ports(count):
    with ExitStack() as stack:
        sockets = [stack.enter_context(socket.socket()) for _ in range(count)]
        for sock in sockets:
            sock.bind(("127.0.0.1", 0))
        return [sock.getsockname()[1] for sock in sockets]

pgport, apiport, uiport, mailport, inboxport = free_ports(5)
origin = f"http://127.0.0.1:{uiport}"
def run(args, **kwargs):
    return subprocess.run(args, check=True, creationflags=FLAGS, **kwargs)

def launch(stack, args, cwd, logname, env=None):
    log = stack.enter_context((work / logname).open("ab"))
    proc = subprocess.Popen(args,cwd=cwd,env=env,stdout=log,stderr=log,creationflags=FLAGS)
    def stop():
        if proc.poll() is None:
            proc.terminate()
            proc.wait(timeout=15)
    stack.callback(stop)
    return proc

def wait_ready(proc,url):
    for _ in range(120):
        if proc.poll() is not None:
            raise RuntimeError("Process exited. Inspect " + str(work))
        try:
            with urllib.request.urlopen(url,timeout=1) as response:
                if response.status==200: return
        except OSError:
            time.sleep(.3)
    raise RuntimeError("Startup timed out. Inspect " + str(work))

def photo(page, name):
    page.screenshot(path=evidence / (name+".png"), animations="disabled")

def api(page,path,method="GET",body=None,status=200):
    result=page.evaluate("""async ([path,method,body])=>{
       const token=await (await fetch('/api/auth/csrf')).json();
       const r=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json','X-CSRF-TOKEN':token.token},
         body:body===null?undefined:JSON.stringify(body)});
       return [r.status,await r.text()];
    }""",[path,method,body])
    assert result[0]==status, (path,method,result[0],result[1][:200])
    return json.loads(result[1]) if result[1] else None

def click(page, name):
    page.get_by_role("button",name=name,exact=True).click()

def register(page, alias):
    page.goto(origin+"/register")
    for field,val in {"fullName":alias.title()+" Demo","regEmail":alias+"@example.test","regPassword":password,"confirm":password}.items():
        page.locator("#"+field).fill(val)
    page.locator("form button[type=submit]").click()
    page.wait_for_url("**/login")
def login(page, email, pwd=password):
    page.goto(origin+"/login")
    page.locator("#email").fill(email)
    page.locator("#password").fill(pwd)
    page.locator("form button[type=submit]").click()
    page.wait_for_url("**/dashboard/*")

def concurrent_registration(pages,eid):
    inputs=[]
    for p in pages:
        token=api(p,"/auth/csrf")["token"]
        cookies="; ".join(c["name"]+"="+c["value"] for c in p.context.cookies())
        inputs.append((cookies,token))
    def reserve(credentials):
        req=urllib.request.Request(origin+f"/api/events/{eid}/registrations",method="POST",
            headers={"Cookie":credentials[0],"X-CSRF-TOKEN":credentials[1]})
        try:
            with urllib.request.urlopen(req) as result: return result.status
        except urllib.error.HTTPError as ex: return ex.code
    with ThreadPoolExecutor(max_workers=2) as pool:
        statuses=list(pool.map(reserve,inputs))
    assert sorted(statuses)==[200,409],statuses
    return pages[statuses.index(200)]

def start_mail(stack):
    mailpit = shutil.which("mailpit")
    if not mailpit:
        from aiosmtpd.controller import Controller
        mail = Controller(Mailbox(), hostname="127.0.0.1", server_hostname="localhost", port=mailport)
        mail.start()
        stack.callback(mail.stop)
        print("SMTP receiver: aiosmtpd", flush=True)
        return lambda: inbox.get(timeout=30)

    base = f"http://127.0.0.1:{inboxport}"
    proc = launch(stack, [mailpit, "--listen", f"127.0.0.1:{inboxport}", "--smtp",
                          f"127.0.0.1:{mailport}"], work, "mailpit.log")
    wait_ready(proc, base + "/api/v1/messages")
    print("SMTP receiver: Mailpit", flush=True)

    def receive():
        for _ in range(100):
            with urllib.request.urlopen(base + "/api/v1/messages", timeout=2) as response:
                messages = json.load(response)["messages"]
            if messages:
                url = base + "/api/v1/message/" + messages[0]["ID"] + "/raw"
                with urllib.request.urlopen(url, timeout=2) as response:
                    return message_from_bytes(response.read(), policy=policy.default)
            time.sleep(.2)
        raise RuntimeError("No reset email arrived in the disposable Mailpit inbox.")
    return receive

def main():
    print("Isolated run:",work,flush=True)
    with ExitStack() as stack:
        receive = start_mail(stack)
        dbdir=work/"database"
        stack.callback(shutil.rmtree, dbdir, ignore_errors=True)
        pwfile=work/"init.pw"
        try:
            pwfile.write_text(password+"\n",encoding="ascii")
            run([PG/("initdb"+SUFFIX),"-D",dbdir,"-U","campus_demo","-A","scram-sha-256","--pwfile",pwfile,"-E","UTF8","--no-locale"],
                stdout=subprocess.DEVNULL)
        finally:
            pwfile.unlink(missing_ok=True)
        run([PG/("pg_ctl"+SUFFIX),"-D",dbdir,"-l",work/"pg.log","-o",f"-h 127.0.0.1 -p {pgport} -c unix_socket_directories=''","-w","start"])
        stack.callback(run,[PG/("pg_ctl"+SUFFIX),"-D",dbdir,"-m","fast","-w","stop"])
        props=ElementTree.parse(next((ROOT/"backend/target/surefire-reports").glob("TEST-*.xml")))
        cp=props.find(".//property[@name='surefire.test.class.path']").get("value")
        env={**os.environ,"APP_ADMIN_EMAIL":admin_email,"APP_ADMIN_PASSWORD":password,"APP_ADMIN_NAME":"Campus Review Admin",
             "DB_URL":f"jdbc:postgresql://127.0.0.1:{pgport}/postgres","DB_USERNAME":"campus_demo","DB_PASSWORD":password,
             "PORT":str(apiport),"FRONTEND_URL":origin,"MAIL_HOST":"127.0.0.1","MAIL_PORT":str(mailport),
             "CLOUDINARY_API_SECRET":"","CLOUDINARY_API_KEY":"","CLOUDINARY_CLOUD_NAME":"","GOOGLE_MAPS_API_KEY":"",
             "CAMPUS_SOUTH":"10.292","CAMPUS_NORTH":"10.299","CAMPUS_WEST":"123.877","CAMPUS_EAST":"123.885",
             "MAIL_SMTP_AUTH":"false","MAIL_SMTP_STARTTLS":"false","MAIL_USERNAME":"","MAIL_PASSWORD":"",
             "APP_ALLOWED_EMAIL_DOMAIN":"","SPRING_PROFILES_ACTIVE":"preview","SESSION_COOKIE_SECURE":"false"}
        def start_backend():
            proc=launch(stack,[JAVA,"-cp",cp,"com.campusconnect.CampusConnectApplication","--server.address=127.0.0.1",
                "--spring.mail.properties.mail.smtp.localhost=localhost"],ROOT/"backend","backend.log",env)
            wait_ready(proc,f"http://127.0.0.1:{apiport}/api/health")
            return proc
        backend=start_backend()
        js="import {preview} from 'vite';await preview({configFile:false,preview:{host:'127.0.0.1',port:%d,strictPort:true,proxy:{'/api':'http://127.0.0.1:%d'}}});"%(uiport,apiport)
        ui=launch(stack,[shutil.which("node"),"--input-type=module","-e",js],ROOT/"frontend","ui.log")
        wait_ready(ui,origin+"/api/health")
        pw=stack.enter_context(sync_playwright())
        browser=pw.chromium.launch(executable_path=CHROME)
        stack.callback(browser.close)
        def page():
            p=browser.new_context(viewport={"width":1440,"height":1000}).new_page()
            p.set_default_timeout(10000)
            p.on("dialog",lambda d:d.accept())
            return p
        admin,org,member,other,visitor=[page() for _ in range(5)]
        login(admin,admin_email)
        for p,alias in [(org,"organizer"),(member,"member"),(other,"second")]:
            register(p,alias)
        uid=next(u["id"] for u in api(admin,"/admin/users") if u["email"]=="organizer@example.test")
        api(admin,f"/admin/users/{uid}/role","PATCH",{"role":"ORGANIZER"})
        login(org,"organizer@example.test"); login(member,"member@example.test"); login(other,"second@example.test")
        visitor.goto(origin+"/events")
        org.goto(origin+"/events/new")
        for name,value in {"title":"Student Research Forum","description":"Meet the campus research teams and discuss their projects.",
            "startsAt":future,"endsAt":end,"location":"Engineering Auditorium","capacity":"1"}.items():
            org.locator('[name="'+name+'"]').fill(value)
        org.get_by_label("Category").select_option("Academic")
        org.get_by_label("Latitude").fill("10.295")
        org.get_by_label("Longitude").fill("123.881")
        click(org,"Save draft")
        org.wait_for_url(re.compile(r"/events/\d+$"))
        eid=int(org.url.split("/")[-1])
        expect(org.get_by_text("Event draft saved.")).to_be_visible()
        org.reload()
        expect(org.get_by_text("Saved pin: 10.29500, 123.88100")).to_be_visible()
        expect(org.get_by_text("Map unavailable.", exact=False)).to_be_visible()
        photo(org,"08-saved-event-pin")
        assert not api(visitor,"/events")
        api(visitor,f"/events/{eid}",status=404)
        photo(org,"01-event-draft")
        valid={"title":"Student Research Forum","description":"Meet the campus research teams and discuss their projects.","category":"Academic",
            "location":"Engineering Auditorium","startsAt":future+":00+08:00","endsAt":end+":00+08:00","capacity":1,
            "latitude":10.295,"longitude":123.881}
        api(member,"/events","POST",valid,403)
        api(org,"/events","POST",{**valid,"title":""},400)
        api(org,"/events","POST",{**valid,"endsAt":valid["startsAt"]},400)
        api(org,"/events","POST",{**valid,"endsAt":incident+":00+08:00"},400)
        api(org,"/events","POST",{**valid,"location":""},400)
        api(org,"/events","POST",{**valid,"latitude":0,"longitude":0},400)
        api(org,"/events","POST",{**valid,"capacity":0},400)
        api(org,f"/events/{eid}","PUT",valid)
        admin_event=api(admin,"/events","POST",valid,201)["id"]
        api(org,f"/events/{admin_event}","PUT",valid,403)
        api(org,f"/events/{admin_event}/state","PATCH",{"action":"cancel"},403)
        click(org,"Publish event")
        expect(org.get_by_text("Publish event saved.")).to_be_visible()
        member.goto(origin+"/events")
        member.get_by_label("Keywords").fill("Research")
        member.get_by_label("Category").select_option("Academic")
        click(member,"Search")
        expect(member.get_by_role("link",name=valid["title"])).to_be_visible()
        photo(member,"02-event-discovery")
        assert len(api(visitor,"/events?category=Academic&q=Research&from="+future[:10]+"&to="+future[:10]))==1
        assert not api(visitor,"/events?q=unrelated")
        earlier=api(org,"/events","POST",{**valid,"title":"Earlier campus session","capacity":None},201)["id"]
        api(org,f"/events/{earlier}/state","PATCH",{"action":"publish"})
        api(member,f"/events/{earlier}/registrations","POST")
        # Advance this published event into the past without a wall-clock wait.
        past={**valid,"title":"Earlier campus session","startsAt":incident+":00+08:00","endsAt":incident[:10]+"T11:30:00+08:00","capacity":None}
        api(admin,f"/events/{earlier}","PUT",past)
        assert [e["id"] for e in api(visitor,"/events")]==[earlier,eid]
        assert [e["id"] for e in api(visitor,"/events?from="+future[:10])]==[eid]
        api(other,f"/events/{earlier}/registrations","POST",status=409)
        api(member,f"/events/{earlier}/registrations/me","DELETE",status=409)
        api(admin,f"/events/{earlier}/state","PATCH",{"action":"cancel"})
        winner=concurrent_registration([member,other],eid)
        api(winner,f"/events/{eid}/registrations","POST",status=409)
        winner.goto(origin+f"/events/{eid}")
        expect(winner.get_by_text("Your registration is active.")).to_be_visible()
        expect(winner.get_by_text("Organizer Demo",exact=True)).to_be_visible()
        expect(winner.get_by_text(valid["description"],exact=True)).to_be_visible()
        expect(winner.get_by_text(valid["location"],exact=True)).to_be_visible()
        expect(winner.locator("dd").filter(has_text="PHT")).to_be_visible()
        org.goto(origin+"/events?scope=mine")
        expect(org.locator("article").filter(has_text=valid["title"])).to_contain_text("Full · 1 registered")
        photo(org,"09-organizer-count")
        photo(winner,"03-event-registration")
        click(winner,"Cancel my registration")
        expect(winner.get_by_text("Registration cancelled.")).to_be_visible()
        assert api(org,f"/events/{eid}")["registered"]==0
        click(winner,"Register for event")
        expect(winner.get_by_text("Registration confirmed.")).to_be_visible()
        def registration_status():
            return next(r["registration_status"] for r in api(winner,"/events/registrations/me") if r["id"]==eid)
        assert registration_status()=="ACTIVE"
        api(member,f"/events/{eid}/state","PATCH",{"action":"hide"},403)
        api(admin,f"/events/{eid}/state","PATCH",{"action":"hide"})
        assert not api(visitor,"/events"); api(member,f"/events/{eid}/registrations","POST",status=409)
        api(admin,f"/events/{eid}/state","PATCH",{"action":"show"})
        api(org,f"/events/{eid}/state","PATCH",{"action":"cancel"})
        assert registration_status()=="CANCELLED"
        winner.goto(origin+"/events?scope=registrations")
        expect(winner.locator("article").filter(has_text=valid["title"])).to_contain_text("Registration: CANCELLED")
        photo(winner,"10-cancelled-registration")
        assert not api(visitor,"/events"); api(member,f"/events/{eid}/registrations","POST",status=409)
        print("Event validation, ownership, discovery, capacity race, cancellation, moderation passed",flush=True)
        member.goto(origin+"/reports/new")
        for name,value in {"title":"Library desk keys","description":"Small keyring found near the library entrance.",
            "incidentAt":incident,"location":"Main Library"}.items():
            member.locator('[name="'+name+'"]').fill(value)
        member.get_by_label("Report type").select_option("FOUND"); member.get_by_label("Category").select_option("Keys")
        member.get_by_label("Latitude").fill("10.296")
        member.get_by_label("Longitude").fill("123.882")
        click(member,"Save report")
        member.wait_for_url(re.compile(r"/reports/\d+$"))
        rid=int(member.url.split("/")[-1])
        expect(member.get_by_text("Report saved.",exact=False)).to_be_visible()
        member.reload()
        expect(member.get_by_text("Saved pin: 10.29600, 123.88200")).to_be_visible()
        expect(member.get_by_text("Main Library",exact=True)).to_be_visible()
        expect(member.get_by_text("FOUND · Keys · OPEN")).to_be_visible()
        expect(member.get_by_text("Small keyring found near the library entrance.")).to_be_visible()
        expect(member.locator("dd").filter(has_text="PHT")).to_be_visible()
        photo(member,"04-found-report")
        # Exercise a browser script-load failure without using a real key or provider quota.
        config=api(visitor,"/campus/map")
        visitor.route("**/api/campus/map",lambda route:route.fulfill(json={**config,"key":"offline-test"}))
        visitor.route("https://maps.googleapis.com/**",lambda route:route.abort())
        visitor.goto(origin+f"/reports/{rid}")
        expect(visitor.get_by_text("Map unavailable.",exact=False)).to_be_visible()
        expect(visitor.get_by_text("Main Library",exact=True)).to_be_visible()
        visitor.unroute("**/api/campus/map")
        visitor.unroute("https://maps.googleapis.com/**")
        item={"type":"LOST","title":"Library access card","description":"Blue card sleeve","location":"West entrance","category":"Identification Cards","incidentAt":incident+":00+08:00"}
        lost=api(other,"/reports","POST",item,201)["id"]
        api(member,f"/reports/{lost}","PUT",item,403)
        api(other,f"/reports/{lost}","PUT",{**item,"location":"Library entrance"})
        for field in ("title","type","location","description","category","incidentAt"):
            api(other,"/reports","POST",{k:v for k,v in item.items() if k!=field},400)
        api(other,"/reports","POST",{**item,"latitude":0,"longitude":0},400)
        api(other,"/reports","POST",{**item,"incidentAt":valid["startsAt"]},400)
        assert len(api(visitor,"/reports?type=FOUND&category=Keys&status=OPEN&q=library"))==1
        assert len(api(visitor,"/reports?type=LOST&q=access"))==1
        member.locator('input[type=file]').set_input_files({"name":"note.txt","mimeType":"text/plain","buffer":b"notes"})
        expect(member.get_by_role("alert")).to_contain_text("5 MB")
        photo(member,"05-photo-validation")
        def upload(p,signal,expected):
            token=api(p,"/auth/csrf")["token"]
            res=p.request.post(origin+f"/api/reports/{rid}/images",headers={"X-CSRF-TOKEN":token},multipart={"file":signal})
            assert res.status==expected,(res.status,expected)
        upload(other,{"name":"bad.jpg","mimeType":"image/jpeg","buffer":b"plain text"},403)
        upload(member,{"name":"bad.jpg","mimeType":"image/jpeg","buffer":b"plain text"},400)
        upload(member,{"name":"large.png","mimeType":"image/png","buffer":bytes(5*1024*1024+1)},413)
        upload(member,{"name":"example.png","mimeType":"image/png","buffer":(evidence/"04-found-report.png").read_bytes()},503)
        assert api(member,f"/reports/{rid}")["images"]==[]
        member.locator('input[type=file]').set_input_files(evidence/"04-found-report.png")
        click(member,"Upload photo")
        expect(member.get_by_role("alert")).to_contain_text("Retry")
        click(member,"Retry upload")
        expect(member.get_by_role("button",name="Retry upload")).to_be_enabled()
        click(member,"Discard selection")
        expect(member.get_by_text("0 of 3 photos")).to_be_visible()
        api(other,f"/reports/{rid}/state","PATCH",{"action":"resolve"},403)
        api(member,f"/reports/{rid}/state","PATCH",{"action":"resolve"})
        assert [r["id"] for r in api(visitor,"/reports?status=RESOLVED")]==[rid]
        api(member,f"/reports/{rid}","PUT",item,409)
        api(member,"/reports?scope=admin",status=403)
        api(admin,f"/reports/{rid}/state","PATCH",{"action":"hide"})
        api(visitor,f"/reports/{rid}",status=404)
        assert not api(visitor,"/reports?type=FOUND"); assert api(member,"/reports?scope=mine")[0]["id"]==rid
        admin.goto(origin+"/reports?scope=admin")
        expect(admin.get_by_role("heading",name="Library desk keys")).to_be_visible()
        photo(admin,"06-moderation")
        api(admin,f"/reports/{rid}/state","PATCH",{"action":"close"})
        api(admin,f"/reports/{rid}/state","PATCH",{"action":"show"})
        api(visitor,f"/reports/{rid}",status=404)
        assert not api(visitor,"/reports?type=FOUND")
        api(admin,f"/reports/{lost}/state","PATCH",{"action":"resolve"})
        visitor.set_viewport_size({"width":360,"height":800}); visitor.goto(origin+"/reports")
        expect(visitor.get_by_role("link",name=item["title"])).to_be_visible()
        assert visitor.evaluate("document.documentElement.scrollWidth<=innerWidth")
        photo(visitor,"07-mobile-list")
        for width in (360,768,1366):
            for p,route in ((visitor,"/events"),(visitor,"/reports"),(member,"/reports/new"),(org,"/events/new")):
                p.set_viewport_size({"width":width,"height":900})
                p.goto(origin+route)
                expect(p.locator("main h1")).to_be_visible()
                assert p.evaluate("document.documentElement.scrollWidth<=innerWidth"), (route,width)
        org.get_by_label("Title",exact=True).focus()
        org.keyboard.press("Tab")
        assert org.get_by_label("Category").evaluate("e=>e===document.activeElement")
        print("Lost/found, photo validation, provider failure, moderation and mobile list passed",flush=True)
        api(member,"/auth/profile","PUT",{"fullName":"Campus Member"})
        admin.goto(origin+"/forgot-password")
        admin.locator("#forgotEmail").fill("member@example.test");admin.locator("form button[type=submit]").click()
        msg=receive(); assert msg["To"]=="member@example.test"
        reset=re.search(re.escape(origin)+r"/reset-password\?token=[A-Za-z0-9_-]+",msg.get_body(preferencelist=("plain",)).get_content()).group()
        visitor.goto(reset); visitor.locator("#resetPassword").fill(password+"b");visitor.locator("#resetConfirm").fill(password+"b")
        visitor.locator("form button[type=submit]").click(); visitor.wait_for_url("**/login")
        login(visitor,"member@example.test",password+"b")
        api(visitor,"/auth/change-password","PUT",{"currentPassword":password+"b","newPassword":password+"c","confirmNewPassword":password+"c"})
        backend.terminate(); backend.wait(timeout=15)
        # Model an earlier release that kept ACTIVE rows when the event was cancelled.
        run([PG/("psql"+SUFFIX),"-X","-h","127.0.0.1","-p",str(pgport),"-U","campus_demo",
             "-d","postgres","-v","ON_ERROR_STOP=1","-c",
             f"update event_registration set status='ACTIVE',cancelled_at=null where event_id={eid}"],
            env={**os.environ,"PGPASSWORD":password},stdout=subprocess.DEVNULL)
        start_backend()
        login(member,"member@example.test",password+"c")
        assert api(member,"/auth/me")["fullName"]=="Campus Member"
        assert api(member,"/reports?scope=mine")[0]["id"]==rid
        assert api(member,f"/reports/{rid}")["latitude"]==10.296
        login(org,"organizer@example.test")
        saved=api(org,f"/events/{eid}")
        assert (saved["latitude"],saved["longitude"],saved["status"],saved["registered"])==(10.295,123.881,"CANCELLED",0)
        assert api(member,"/events?scope=public")==[]
        api(member,"/auth/logout","POST")
        api(member,"/auth/me",status=401)
        print("SMTP reset, changed credentials, restart migration and persisted records passed",flush=True)
if __name__=="__main__": main()
