package com.campusconnect.campus;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@EnableScheduling
public class PhotoCleanup {
    private final CampusData data;
    private final CloudImages cloud;
    PhotoCleanup(CampusData data,CloudImages cloud) { this.data=data; this.cloud=cloud; }

    // Commit the compensation before calling the external service, even if the caller later rolls back.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reserve(String id) { enqueue(id,OffsetDateTime.now().plusHours(1)); }
    void enqueue(String id,OffsetDateTime after) {
        data.db.update("insert into image_cleanup(public_id,retry_after) values(:id,:due)",Map.of("id",id,"due",after));
    }
    void release(String id) { data.db.update("delete from image_cleanup where public_id=:id",Map.of("id",id)); }

    @Scheduled(fixedDelay=60000)
    public void retry() {
        for(var entry:data.rows("select public_id from image_cleanup where retry_after<=current_timestamp order by retry_after limit 20",Map.of())) {
            String id=(String)entry.get("public_id");
            try { cloud.destroy(id); release(id); }
            catch(com.campusconnect.common.ApiException ex) {
                data.db.update("update image_cleanup set retry_after=:due where public_id=:id",Map.of("due",OffsetDateTime.now().plusMinutes(10),"id",id));
            }
        }
    }
}
