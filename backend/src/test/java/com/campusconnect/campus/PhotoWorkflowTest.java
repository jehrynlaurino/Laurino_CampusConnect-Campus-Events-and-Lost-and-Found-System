package com.campusconnect.campus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.campusconnect.common.ApiException;
import com.campusconnect.user.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:photos;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class PhotoWorkflowTest {
    @Autowired Photos photos;
    @Autowired Reports reports;
    @Autowired CampusData data;
    @Autowired UserRepository users;
    @Autowired PhotoCleanup cleanup;
    @MockitoBean CloudImages provider;
    TestingAuthenticationToken actor;
    long report;
    MockMultipartFile png;

    @BeforeEach void setUp() throws Exception {
        String email=java.util.UUID.randomUUID()+"@example.test";
        users.save(new AppUser("Photo Test",email,"unused",Role.MEMBER));
        actor=new TestingAuthenticationToken(email,null,"ROLE_MEMBER");
        report=reports.create(actor,new Reports.Form("FOUND","Bag","Canvas bag","Bags","Library",OffsetDateTime.now().minusHours(1),null,null)).get("id");
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(5,5,BufferedImage.TYPE_INT_RGB),"png",output);
        png=new MockMultipartFile("file","item.png","image/png",output.toByteArray());
    }
    @Test void successfulAdapterContractEnforcesThreeAndQueuesRemoval() throws Exception {
        when(provider.upload(anyString(),any(),eq("png"))).thenReturn("https://example.test/test-photo");
        var first=photos.upload(report,actor,png);
        photos.upload(report,actor,png);photos.upload(report,actor,png);
        assertThrows(ApiException.class,()->photos.upload(report,actor,png));
        verify(provider,times(3)).upload(anyString(),any(),eq("png"));
        var args=Map.of("id",first.get("id"));
        String publicId=(String)data.row("select public_id from report_image where id=:id",args).get("public_id");
        photos.remove(report,((Number)first.get("id")).longValue(),actor);
        assertTrue(data.rows("select id from report_image where id=:id",args).isEmpty());
        cleanup.retry();
        verify(provider).destroy(publicId);
        assertEquals(2,((java.util.List<?>)reports.detail(report,actor).get("images")).size());
    }
    @Test void remoteFailureRollsBackPhotoAndRetainsCompensation() {
        when(provider.upload(anyString(),any(),anyString())).thenThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Retry"));
        assertThrows(ApiException.class,()->photos.upload(report,actor,png));
        assertTrue(((java.util.List<?>)reports.detail(report,actor).get("images")).isEmpty());
        assertFalse(data.rows("select public_id from image_cleanup where public_id like :pattern",Map.of("pattern","campusconnect/reports/"+report+"/%")).isEmpty());
    }
    @Test void truncatedAndDisguisedImagesRejected() {
        assertThrows(ApiException.class,()->Photos.inspect(new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertThrows(ApiException.class,()->Photos.inspect("plain text".getBytes()));
    }
}
