package com.campusconnect.campus;

import com.campusconnect.common.ApiException;
import com.campusconnect.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.time.*;
import java.util.*;

@Component
class CampusData {
    final NamedParameterJdbcTemplate db;
    private final UserRepository users;

    CampusData(NamedParameterJdbcTemplate db, UserRepository users) {
        this.db = db;
        this.users = users;
    }

    AppUser user(Authentication auth) {
        if (auth == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        return users.findByEmail(auth.getName()).filter(AppUser::isActive).orElseThrow(CampusData::denied);
    }
    static ApiException denied() { return new ApiException(HttpStatus.FORBIDDEN, "You cannot change this record."); }
    static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Record not found."); }
    static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }

    Map<String,Object> row(String sql, Map<String,?> args) {
        return rows(sql,args).stream().findFirst().orElseThrow(CampusData::missing);
    }
    List<Map<String,Object>> rows(String sql, Map<String,?> args) {
        return db.query(sql,args,(rs,i)-> {
            Map<String,Object> record = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int col=1; col<=meta.getColumnCount(); col++) {
                String key=meta.getColumnLabel(col).toLowerCase(Locale.ROOT);
                Object value=rs.getObject(col);
                if (value!=null && key.endsWith("_at")) value=rs.getObject(col, OffsetDateTime.class).toInstant().toString();
                record.put(key,value);
            }
            return record;
        });
    }
    long insert(String sql, Map<String,?> params) {
        var key = new GeneratedKeyHolder();
        db.update(sql,new MapSqlParameterSource(params),key,new String[]{"id"});
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    static void ownerOrAdmin(Map<String,Object> record, AppUser actor) {
        if (!actor.getId().equals(record.get("owner_id")) && actor.getRole()!=Role.ADMIN) throw denied();
    }
    static void requireAdmin(AppUser actor) { if (actor.getRole()!=Role.ADMIN) throw denied(); }

    static class Filter {
        final StringBuilder sql = new StringBuilder();
        final Map<String,Object> args = new HashMap<>();
        Filter(int page) {
            if (page<0 || page>100000) throw invalid("Invalid page number.");
            args.put("offset",page*20);
        }
        void condition(String expression,String key,Object value) {
            if (value==null || value instanceof String s && s.isBlank()) return;
            if (value instanceof String s && s.length()>200) throw invalid("Filter is too long.");
            sql.append(" and ").append(expression);
            args.put(key,value);
        }
        void search(String q) {
            if (q==null || q.isBlank()) return;
            String term=q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
            condition("(lower(a.title) like :q escape '!' or lower(a.description) like :q escape '!' or lower(a.location) like :q escape '!')","q","%"+term+"%");
        }
    }
}
