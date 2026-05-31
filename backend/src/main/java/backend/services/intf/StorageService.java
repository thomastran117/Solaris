package backend.services.intf;

import backend.dtos.responses.upload.PresignUploadResponse;
import backend.models.enums.UploadFolder;

public interface StorageService {
    /**
     * Generates a pre-signed S3 PUT URL the client can use to upload a file directly.
     * The returned {@code fileUrl} is the permanent public URL to persist in the database.
     *
     * @param folder      the upload category — determines the S3 key prefix
     * @param userId      ID of the authenticated user — scopes the key to prevent collisions
     * @param contentType MIME type of the file being uploaded (e.g. "image/jpeg")
     */
    PresignUploadResponse generatePresignedUrl(UploadFolder folder, long userId, String contentType);
}
