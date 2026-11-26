package com.campusconnect.campus;

import static com.campusconnect.campus.CampusData.*;
import com.campusconnect.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@Transactional
public class Reports {
    private final CampusData data;
    private final CampusMap map;
    Reports(CampusData data,CampusMap map) { this.data=data; this.map=map; }

    record Form(@NotNull @Pattern(regexp="LOST|FOUND") String type,@NotBlank @Size(max=160) String title,
                @NotBlank @Size(max=4000) String description,@NotBlank @Size(max=80) String category,
                @NotBlank @Size(max=200) String location,@NotNull @PastOrPresent OffsetDateTime incidentAt,
                Double latitude,Double longitude) {}
    private Map<String,Object> fields(Form f) {
        map.validate(f.latitude,f.longitude);
        var p=new HashMap<String,Object>(Map.of("type",f.type,"title",f.title.strip(),"description",f.description.strip(),
            "category",f.category.strip(),"location",f.location.strip(),"incident",f.incidentAt));
        p.put("lat",f.latitude); p.put("lng",f.longitude); return p;
    }
    Map<String,Object> lock(long id) {
        return data.row("select * from lost_found_report where id=:id for update",Map.of("id",id));
    }
    void editable(long id,AppUser actor) {
        var r=lock(id);
        if(!actor.getId().equals(r.get("owner_id"))) throw denied();
        if(!"OPEN".equals(r.get("status")) || Boolean.TRUE.equals(r.get("hidden"))) throw conflict("Only visible open reports can be edited.");
    }
    @GetMapping
    public List<Map<String,Object>> list(Authentication auth,@RequestParam(defaultValue="public") String scope,
        @RequestParam(defaultValue="0") int page,@RequestParam(required=false) String q,@RequestParam(required=false) String type,
        @RequestParam(required=false) String category,@RequestParam(required=false) String status) {
        var f=new Filter(page);
        switch(scope) {
            case "mine" -> f.condition("a.owner_id=:u","u",data.user(auth).getId());
            case "admin" -> requireAdmin(data.user(auth));
            case "public" -> f.sql.append(" and not a.hidden and a.status<>'CLOSED'");
            default -> throw invalid("Invalid report listing.");
        }
        f.search(q); f.condition("a.type=:type","type",type); f.condition("lower(a.category)=lower(:category)","category",category);
        f.condition("a.status=:status","status",status);
        return data.rows("""
            select a.*, (select i.secure_url from report_image i where i.report_id=a.id order by i.id limit 1) as thumbnail
            from lost_found_report a where 1=1
            """+f.sql+" order by a.incident_at desc,a.id desc limit 20 offset :offset",f.args);
    }
    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable long id,Authentication auth) {
        var p=Map.of("id",id);
        var r=data.row("select * from lost_found_report where id=:id",p);
        if(Boolean.TRUE.equals(r.get("hidden")) || "CLOSED".equals(r.get("status"))) {
            if(auth==null) throw missing();
            var u=data.user(auth);
            if(!u.getId().equals(r.get("owner_id")) && u.getRole()!=Role.ADMIN) throw missing();
        }
        r.put("images",data.rows("select id,secure_url from report_image where report_id=:id order by id",p));
        return r;
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Long> create(Authentication auth,@Valid @RequestBody Form form) {
        var p=fields(form); p.put("owner",data.user(auth).getId());
        return Map.of("id",data.insert("""
            insert into lost_found_report(owner_id,type,title,description,category,incident_at,location,latitude,longitude)
            values(:owner,:type,:title,:description,:category,:incident,:location,:lat,:lng)
            """,p));
    }
    @PutMapping("/{id}")
    public void edit(@PathVariable long id, Authentication auth,@Valid @RequestBody Form form) {
        editable(id,data.user(auth));
        var p=fields(form); p.put("id",id);
        data.db.update("""
            update lost_found_report set type=:type,title=:title,description=:description,category=:category,
            incident_at=:incident,location=:location,latitude=:lat,longitude=:lng,updated_at=current_timestamp where id=:id
            """,p);
    }
    @PatchMapping("/{id}/state")
    public void state(@PathVariable long id, Authentication auth,@Valid @RequestBody Events.Decision decision) {
        var u=data.user(auth); var r=lock(id);
        String change=switch(decision.action()) {
            case "resolve" -> {
                ownerOrAdmin(r,u);
                if(!"OPEN".equals(r.get("status"))) throw conflict("Report is already closed or resolved.");
                yield "status='RESOLVED'";
            }
            case "close","hide","show" -> {
                requireAdmin(u);
                yield switch(decision.action()) { case "close" -> "status='CLOSED'"; case "hide" -> "hidden=true"; default -> "hidden=false"; };
            }
            default -> throw invalid("Unknown report action.");
        };
        data.db.update("update lost_found_report set "+change+",updated_at=current_timestamp where id=:id",Map.of("id",id));
        if(u.getRole()==Role.ADMIN) data.db.update("insert into moderation_history(admin_id,report_id,action) values(:u,:id,:act)",
            Map.of("u",u.getId(),"id",id,"act",decision.action()));
    }
}
