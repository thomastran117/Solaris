package backend.services.impl;

import org.springframework.stereotype.Service;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.BadRequestException;
import backend.models.enums.UploadFolder;
import backend.services.intf.StorageService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class S3StorageServiceImpl implements StorageService {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> DATA_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel"
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "text/csv", "csv",
            "application/csv", "csv",
            "application/vnd.ms-excel", "csv"
    );

    private static final Set<UploadFolder> DATA_FOLDERS = Set.of(
            UploadFolder.IMPORT_CSV,
            UploadFolder.IMPORT_ERROR_REPORT
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final EnvironmentSetting environmentSetting;

    public S3StorageServiceImpl(S3Presigner s3Presigner, S3Client s3Client, EnvironmentSetting environmentSetting) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.environmentSetting = environmentSetting;
    }

    @Override
    public PresignUploadResponse generatePresignedUrl(UploadFolder folder, UUID userId, String contentType) {
        boolean dataFolder = DATA_FOLDERS.contains(folder);
        Set<String> allowed = dataFolder ? DATA_CONTENT_TYPES : IMAGE_CONTENT_TYPES;
        if (!allowed.contains(contentType)) {
            String allowedDesc = dataFolder
                    ? "text/csv, application/csv, application/vnd.ms-excel"
                    : "image/jpeg, image/png, image/webp, image/gif";
            throw new BadRequestException("Unsupported content type for folder " + folder + ". Allowed: " + allowedDesc);
        }

        EnvironmentSetting.S3 s3Config = environmentSetting.getS3();
        String extension = EXTENSION_MAP.get(contentType);
        String key = folder.getPath() + "/" + userId + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Config.getPresignExpirySeconds()))
                .putObjectRequest(putRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String fileUrl = resolvePublicUrl(s3Config, key);

        return new PresignUploadResponse(
                presigned.url().toString(),
                fileUrl,
                key,
                s3Config.getPresignExpirySeconds()
        );
    }

    @Override
    public InputStream openObject(String key) {
        EnvironmentSetting.S3 s3Config = environmentSetting.getS3();
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(key)
                .build());
    }

    @Override
    public PresignUploadResponse putBytes(UploadFolder folder, UUID userId, String contentType, byte[] data) {
        EnvironmentSetting.S3 s3Config = environmentSetting.getS3();
        String extension = EXTENSION_MAP.getOrDefault(contentType, "bin");
        String key = folder.getPath() + "/" + userId + "/" + UUID.randomUUID() + "." + extension;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));

        return new PresignUploadResponse(
                null,
                resolvePublicUrl(s3Config, key),
                key,
                0);
    }

    @Override
    public String presignDownload(String key, int expirySeconds) {
        EnvironmentSetting.S3 s3Config = environmentSetting.getS3();
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getRequest)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private String resolvePublicUrl(EnvironmentSetting.S3 s3Config, String key) {
        String base = s3Config.getPublicUrlBase();
        if (base != null && !base.isBlank()) {
            return base.stripTrailing() + "/" + key;
        }
        return "https://" + s3Config.getBucket() + ".s3." + s3Config.getRegion() + ".amazonaws.com/" + key;
    }
}
