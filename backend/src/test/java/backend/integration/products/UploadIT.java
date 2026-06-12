package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UploadFolder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadIT extends AbstractIntegrationIT {

    // ── Request body builder ──────────────────────────────────────────────────

    private String presignBody(UploadFolder folder, String contentType) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (folder != null) {
            body.put("folder", folder.name());
        }
        body.put("contentType", contentType);
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /upload/presign ──────────────────────────────────────────────────

    @Test
    void presign_imageFolderWithAllowedContentType_returnsPresignedUrl() throws Exception {
        User user = createActiveUser("upload-presign-image@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.PRODUCT_IMAGE, "image/jpeg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.fileUrl",
                        startsWith("http://localhost/s3/product-images/" + user.getId() + "/")))
                .andExpect(jsonPath("$.data.fileUrl", endsWith(".jpg")))
                .andExpect(jsonPath("$.data.key", startsWith("product-images/" + user.getId() + "/")))
                .andExpect(jsonPath("$.data.key", endsWith(".jpg")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void presign_dataFolderWithAllowedContentType_returnsPresignedUrl() throws Exception {
        User user = createActiveUser("upload-presign-csv@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.IMPORT_CSV, "text/csv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl",
                        startsWith("http://localhost/s3/imports/csv/" + user.getId() + "/")))
                .andExpect(jsonPath("$.data.fileUrl", endsWith(".csv")))
                .andExpect(jsonPath("$.data.key", startsWith("imports/csv/" + user.getId() + "/")))
                .andExpect(jsonPath("$.data.key", endsWith(".csv")))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void presign_imageFolderWithCsvContentType_returns400() throws Exception {
        User user = createActiveUser("upload-presign-mismatch-image@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.PRODUCT_IMAGE, "text/csv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.detail", is(
                        "Unsupported content type for folder PRODUCT_IMAGE. "
                                + "Allowed: image/jpeg, image/png, image/webp, image/gif")));
    }

    @Test
    void presign_dataFolderWithImageContentType_returns400() throws Exception {
        User user = createActiveUser("upload-presign-mismatch-data@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.IMPORT_CSV, "image/jpeg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.detail", is(
                        "Unsupported content type for folder IMPORT_CSV. "
                                + "Allowed: text/csv, application/csv, application/vnd.ms-excel")));
    }

    @Test
    void presign_missingFolder_returns400() throws Exception {
        User user = createActiveUser("upload-presign-missing-folder@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(null, "image/jpeg")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presign_blankContentType_returns400() throws Exception {
        User user = createActiveUser("upload-presign-blank-content-type@example.com", "Password1!");

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.PRODUCT_IMAGE, "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presign_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/upload/presign")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.PRODUCT_IMAGE, "image/jpeg")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void presign_rateLimitExceeded_returns429() throws Exception {
        User user = createActiveUser("upload-presign-ratelimit@example.com", "Password1!");

        // Pre-load the rate-limit counter to the limit (20 per 60s) so the next call exceeds it.
        stringRedisTemplate.opsForValue().set(
                "app:ratelimit:upload:presign:" + user.getId(), "20", Duration.ofSeconds(60));

        mockMvc.perform(post("/upload/presign")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignBody(UploadFolder.PRODUCT_IMAGE, "image/jpeg")))
                .andExpect(status().isTooManyRequests());
    }
}
