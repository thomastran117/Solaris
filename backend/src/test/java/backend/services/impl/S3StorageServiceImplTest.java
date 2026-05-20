package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.BadRequestException;
import backend.models.enums.UploadFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3StorageServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private S3Presigner s3Presigner;
    private S3Client s3Client;
    private EnvironmentSetting environmentSetting;
    private EnvironmentSetting.S3 s3Config;
    private S3StorageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        s3Presigner        = mock(S3Presigner.class);
        s3Client           = mock(S3Client.class);
        environmentSetting = mock(EnvironmentSetting.class);
        s3Config           = mock(EnvironmentSetting.S3.class);

        when(environmentSetting.getS3()).thenReturn(s3Config);
        when(s3Config.getBucket()).thenReturn("my-bucket");
        when(s3Config.getRegion()).thenReturn("us-east-1");
        when(s3Config.getPresignExpirySeconds()).thenReturn(300);
        when(s3Config.getPublicUrlBase()).thenReturn(null);

        URL presignPutUrl = URI.create("https://my-bucket.s3.us-east-1.amazonaws.com/key?sig=abc").toURL();
        PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
        when(presignedPut.url()).thenReturn(presignPutUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

        service = new S3StorageServiceImpl(s3Presigner, s3Client, environmentSetting);
    }

    // ─── generatePresignedUrl — content type validation ───────────────────────

    @Test
    void generatePresignedUrl_imageFolder_jpegContentType_succeeds() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/jpeg");

        assertNotNull(resp.getUploadUrl());
        assertNotNull(resp.getKey());
        assertTrue(resp.getKey().startsWith(UploadFolder.PRODUCT_IMAGE.getPath()));
        assertTrue(resp.getKey().endsWith(".jpg"));
    }

    @Test
    void generatePresignedUrl_imageFolder_pngContentType_succeeds() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_THUMBNAIL, USER_ID, "image/png");
        assertTrue(resp.getKey().endsWith(".png"));
    }

    @Test
    void generatePresignedUrl_imageFolder_webpContentType_succeeds() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/webp");
        assertTrue(resp.getKey().endsWith(".webp"));
    }

    @Test
    void generatePresignedUrl_imageFolder_csvContentType_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.generatePresignedUrl(UploadFolder.PRODUCT_IMAGE, USER_ID, "text/csv"));
    }

    @Test
    void generatePresignedUrl_imageFolder_unknownType_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.generatePresignedUrl(UploadFolder.PRODUCT_IMAGE, USER_ID, "application/pdf"));
    }

    @Test
    void generatePresignedUrl_dataFolder_csvContentType_succeeds() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.IMPORT_CSV, USER_ID, "text/csv");
        assertTrue(resp.getKey().endsWith(".csv"));
        assertTrue(resp.getKey().startsWith(UploadFolder.IMPORT_CSV.getPath()));
    }

    @Test
    void generatePresignedUrl_dataFolder_imageContentType_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.generatePresignedUrl(UploadFolder.IMPORT_CSV, USER_ID, "image/jpeg"));
    }

    // ─── URL construction ────────────────────────────────────────────────────

    @Test
    void generatePresignedUrl_noPublicUrlBase_constructsS3DefaultUrl() {
        when(s3Config.getPublicUrlBase()).thenReturn(null);

        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/jpeg");

        assertTrue(resp.getFileUrl().startsWith("https://my-bucket.s3.us-east-1.amazonaws.com/"));
    }

    @Test
    void generatePresignedUrl_withPublicUrlBase_usesBase() {
        when(s3Config.getPublicUrlBase()).thenReturn("https://cdn.example.com");

        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/jpeg");

        assertTrue(resp.getFileUrl().startsWith("https://cdn.example.com/"));
    }

    @Test
    void generatePresignedUrl_keyContainsUserId() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/jpeg");
        assertTrue(resp.getKey().contains(USER_ID.toString()),
                "Key should include userId: " + resp.getKey());
    }

    @Test
    void generatePresignedUrl_expiresInMatchesConfig() {
        PresignUploadResponse resp = service.generatePresignedUrl(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/jpeg");
        assertEquals(300, resp.getExpiresIn());
    }

    // ─── presignDownload ─────────────────────────────────────────────────────

    @Test
    void presignDownload_returnsPresignedUrl() throws Exception {
        URL getUrl = URI.create("https://my-bucket.s3.us-east-1.amazonaws.com/key?sig=xyz").toURL();
        PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
        when(presignedGet.url()).thenReturn(getUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

        String url = service.presignDownload("folder/file.csv", 600);

        assertNotNull(url);
        assertTrue(url.contains("my-bucket.s3.us-east-1.amazonaws.com"));
    }

    // ─── putBytes ────────────────────────────────────────────────────────────

    @Test
    void putBytes_putsObjectAndReturnsResponse() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        PresignUploadResponse resp = service.putBytes(
                UploadFolder.PRODUCT_IMAGE, USER_ID, "image/png", new byte[]{1, 2, 3});

        assertNull(resp.getUploadUrl()); // putBytes has no pre-signed PUT URL
        assertNotNull(resp.getFileUrl());
        assertNotNull(resp.getKey());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
