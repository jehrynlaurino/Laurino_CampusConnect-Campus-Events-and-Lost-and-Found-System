package com.campusconnect.campus;

import static com.campusconnect.campus.CampusData.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reports/{reportId}/images")
@Transactional
public class Photos {
    private final CampusData data; private final Reports reports; private final CloudImages cloud; private final PhotoCleanup cleanup;
    Photos(CampusData data,Reports reports,CloudImages cloud,PhotoCleanup cleanup) {
        this.data=data; this.reports=reports; this.cloud=cloud; this.cleanup=cleanup;
    }
    static String inspect(byte[] bytes) {
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw invalid("Select a JPG, PNG or WebP photograph.");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png","webp").contains(format)) throw invalid("Unsupported image type.");
                reader.setInput(stream,true,true);
                if((long)reader.getWidth(0)*reader.getHeight(0)>24_000_000) throw invalid("Resize the photo to 24 megapixels or less.");
                reader.read(0);
                return format;
            } finally { reader.dispose(); }
        } catch(IOException | IllegalArgumentException ex) { throw invalid("The photo cannot be decoded."); }
    }
    @PostMapping
    public Map<String,Object> upload(@PathVariable long reportId,Authentication auth,@RequestParam MultipartFile file) throws IOException {
        reports.editable(reportId,data.user(auth));
        Map<String,Long> args=Map.of("r",reportId);
        if(data.db.queryForObject("select count(*) from report_image where report_id=:r",args,Integer.class)>=3)
            throw conflict("A report can hold three photos.");
        if(file.isEmpty() || file.getSize()>5L*1024*1024) throw invalid("Choose a photo up to 5 MB.");
        byte[] bytes=file.getBytes(); String format=inspect(bytes);
        cloud.configured();
        String id="campusconnect/reports/"+reportId+"/"+UUID.randomUUID();
        cleanup.reserve(id);
        String url=cloud.upload(id,bytes,format);
        long image=data.insert("insert into report_image(report_id,public_id,secure_url) values(:r,:id,:url)",Map.of("r",reportId,"id",id,"url",url));
        cleanup.release(id);
        return Map.of("id",image,"secure_url",url);
    }
    @DeleteMapping("/{imageId}")
    public void remove(@PathVariable long reportId,@PathVariable long imageId,Authentication auth) {
        reports.editable(reportId,data.user(auth));
        var args=Map.of("r",reportId,"id",imageId);
        var photo=data.row("select public_id from report_image where report_id=:r and id=:id",args);
        data.db.update("delete from report_image where report_id=:r and id=:id",args);
        cleanup.enqueue((String)photo.get("public_id"),OffsetDateTime.now());
    }
}
