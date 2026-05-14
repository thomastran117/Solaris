package backend.services.intf;

import backend.dtos.responses.upload.PresignUploadResponse;
import backend.models.enums.UploadFolder;

import java.io.InputStream;

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

    /**
     * Opens an InputStream over the object at the given key. Caller closes.
     * Used by the import worker to stream large CSV uploads without buffering the whole file.
     */
    InputStream openObject(String key);

    /**
     * Writes bytes to the given folder under a generated key and returns the permanent public URL.
     * Used by the import worker to write the per-job error report CSV.
     */
    PresignUploadResponse putBytes(UploadFolder folder, long userId, String contentType, byte[] data);

    /**
     * Issues a presigned GET URL the client can use to download an object directly.
     */
    String presignDownload(String key, int expirySeconds);
}
