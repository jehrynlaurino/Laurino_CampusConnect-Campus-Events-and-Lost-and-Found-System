package com.campusconnect.campus;

import com.campusconnect.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.campusconnect.campus.CampusData.*;

@RestController
@RequestMapping("/api/events")
@Transactional
public class Events {
    private final CampusData data;
    private final CampusMap map;
    private static final String SELECT = """
        select a.*, u.full_name as organizer,
          (select count(*) from event_registration r where r.event_id=a.id and r.status='ACTIVE') as registered
        from campus_event a join app_users u on u.id=a.owner_id
        """;
    Events(CampusData data, CampusMap map) { this.data=data; this.map=map; }

    record Form(@NotBlank @Size(max=160) String title, @NotBlank @Size(max=4000) String description,
                @NotBlank @Size(max=80) String category, @NotBlank @Size(max=200) String location,
                @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,@Positive Integer capacity,
                Double latitude,Double longitude) {}
    record Decision(@NotBlank String action) {}
    Map<String,Object> lock(long id) {
        return data.row("select * from campus_event where id=:id for update",Map.of("id",id));
    }
    private Map<String,Object> fields(Form f) {
        if (!f.endsAt.isAfter(f.startsAt)) throw invalid("End time must follow the start time.");
        map.validate(f.latitude,f.longitude);
        Map<String,Object> p=new HashMap<>(Map.of("title",f.title.strip(),"description",f.description.strip(),
            "category",f.category.strip(),"location",f.location.strip(),"start",f.startsAt,"end",f.endsAt));
        p.put("cap",f.capacity); p.put("lat",f.latitude); p.put("lng",f.longitude); return p;
    }
    @GetMapping
    public List<Map<String,Object>> list(Authentication auth,@RequestParam(defaultValue="public") String scope,
        @RequestParam(defaultValue="0") int page,@RequestParam(required=false) String q,@RequestParam(required=false) String category,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        Filter f=new Filter(page);
        switch(scope) {
            case "public" -> f.sql.append(" and a.status='PUBLISHED' and not a.hidden");
            case "mine" -> f.condition("a.owner_id=:owner","owner",data.user(auth).getId());
            case "admin" -> requireAdmin(data.user(auth));
            default -> throw invalid("Invalid event listing.");
        }
        if (from!=null && to!=null && from.isAfter(to)) throw invalid("Invalid date range.");
        ZoneId zone=ZoneId.of("Asia/Manila");
        f.condition("a.starts_at>=:fromDate","fromDate",from==null?null:from.atStartOfDay(zone).toOffsetDateTime());
        f.condition("a.starts_at<:until","until",to==null?null:to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        f.search(q); f.condition("lower(a.category)=lower(:category)","category",category);
        return data.rows(SELECT+" where 1=1"+f.sql+" order by a.starts_at,a.id limit 20 offset :offset",f.args);
    }
    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable long id,Authentication auth) {
        var event=data.row(SELECT+" where a.id=:id",Map.of("id",id));
        if (!"PUBLISHED".equals(event.get("status")) || Boolean.TRUE.equals(event.get("hidden"))) {
            if (auth==null) throw missing();
            var user=data.user(auth);
            if (!user.getId().equals(event.get("owner_id")) && user.getRole()!=Role.ADMIN) throw missing();
        }
        if(auth!=null) {
            var registration=data.rows("select status from event_registration where event_id=:id and user_id=:uid",
                Map.of("id",id,"uid",data.user(auth).getId()));
            if(!registration.isEmpty()) event.put("my_registration",registration.getFirst().get("status"));
        }
        return event;
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Long> create(Authentication auth,@Valid @RequestBody Form form) {
        var user=data.user(auth);
        if(user.getRole()==Role.MEMBER) throw denied();
        var p=fields(form); p.put("owner",user.getId());
        return Map.of("id",data.insert("""
            insert into campus_event(owner_id,title,description,category,starts_at,ends_at,capacity,location,latitude,longitude)
            values(:owner,:title,:description,:category,:start,:end,:cap,:location,:lat,:lng)
            """,p));
    }
    private void mayManage(Map<String,Object> e,AppUser actor) {
        if(actor.getRole()==Role.MEMBER) throw denied();
        ownerOrAdmin(e,actor);
    }
    private int count(long id) {
        return data.db.queryForObject("select count(*) from event_registration where event_id=:id and status='ACTIVE'",Map.of("id",id),Integer.class);
    }
    @PutMapping("/{id}")
    public void edit(@PathVariable long id,Authentication auth,@Valid @RequestBody Form form) {
        var e=lock(id); mayManage(e,data.user(auth));
        if("CANCELLED".equals(e.get("status"))) throw conflict("This event has been cancelled.");
        if(form.capacity!=null && form.capacity<count(id)) throw conflict("Capacity is below the active registration count.");
        var p=fields(form); p.put("id",id);
        data.db.update("""
            update campus_event set title=:title,description=:description,category=:category,starts_at=:start,ends_at=:end,
            capacity=:cap,location=:location,latitude=:lat,longitude=:lng,updated_at=current_timestamp where id=:id
            """,p);
    }
    @PatchMapping("/{id}/state")
    public void state(@PathVariable long id,Authentication auth,@Valid @RequestBody Decision decision) {
        var actor=data.user(auth); var e=lock(id);
        String change=switch(decision.action) {
            case "publish" -> {
                mayManage(e,actor);
                if(!"DRAFT".equals(e.get("status")) || !Instant.parse((String)e.get("starts_at")).isAfter(Instant.now()))
                    throw conflict("Only an upcoming draft can be published.");
                yield "status='PUBLISHED'";
            }
            case "cancel" -> { mayManage(e,actor); yield "status='CANCELLED'"; }
            case "hide","show" -> { requireAdmin(actor); yield decision.action.equals("hide")?"hidden=true":"hidden=false"; }
            default -> throw invalid("Unknown event action.");
        };
        data.db.update("update campus_event set "+change+", updated_at=current_timestamp where id=:id",Map.of("id",id));
        if ("cancel".equals(decision.action)) {
            data.db.update("""
                update event_registration set status='CANCELLED', cancelled_at=current_timestamp
                where event_id=:id and status='ACTIVE'
                """,Map.of("id",id));
        }
        if(actor.getRole()==Role.ADMIN) data.db.update("insert into moderation_history(admin_id,event_id,action) values(:u,:id,:act)",
            Map.of("u",actor.getId(),"id",id,"act",decision.action));
    }
    @PostMapping("/{id}/registrations")
    public void reserve(@PathVariable long id,Authentication auth) {
        var user=data.user(auth);
        if(user.getRole()!=Role.MEMBER) throw denied();
        var e=lock(id);
        if(!"PUBLISHED".equals(e.get("status")) || Boolean.TRUE.equals(e.get("hidden")) ||
           !Instant.parse((String)e.get("starts_at")).isAfter(Instant.now())) throw conflict("Registration is closed.");
        var args=Map.of("id",id,"u",user.getId());
        var previous=data.rows("select status from event_registration where event_id=:id and user_id=:u",args);
        if(!previous.isEmpty() && "ACTIVE".equals(previous.getFirst().get("status"))) throw conflict("You already have a place.");
        if(e.get("capacity") instanceof Number cap && count(id)>=cap.intValue()) throw conflict("This event is full.");
        if(previous.isEmpty()) data.db.update("insert into event_registration(event_id,user_id,status) values(:id,:u,'ACTIVE')",args);
        else data.db.update("update event_registration set status='ACTIVE',registered_at=current_timestamp,cancelled_at=null where event_id=:id and user_id=:u",args);
    }
    @DeleteMapping("/{id}/registrations/me")
    public void withdraw(@PathVariable long id,Authentication auth) {
        var actor=data.user(auth); var e=lock(id);
        if(!Instant.parse((String)e.get("starts_at")).isAfter(Instant.now())) throw conflict("The event has started.");
        if(data.db.update("update event_registration set status='CANCELLED',cancelled_at=current_timestamp where event_id=:id and user_id=:u and status='ACTIVE'",
            Map.of("id",id,"u",actor.getId()))==0) throw conflict("No active registration.");
    }
    @GetMapping("/registrations/me")
    public List<Map<String,Object>> mine(Authentication auth,@RequestParam(defaultValue="0") int page) {
        var f=new Filter(page); f.args.put("u",data.user(auth).getId());
        return data.rows("""
            select a.id,a.title,a.starts_at,a.status,a.hidden,r.status as registration_status,r.registered_at
            from event_registration r join campus_event a on r.event_id=a.id where r.user_id=:u
            order by a.starts_at,a.id limit 20 offset :offset
            """,f.args);
    }
}
