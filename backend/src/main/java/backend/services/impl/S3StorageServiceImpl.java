package backend.services.impl;

import org.springframework.stereotype.Service;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.BadRequestException;
import backend.models.enums.UploadFolder;
import backend.services.intf.StorageService;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class S3StorageServiceImpl implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final S3Presigner s3Presigner;
    private final EnvironmentSetting environmentSetting;

    public S3StorageServiceImpl(S3Presigner s3Presigner, EnvironmentSetting environmentSetting) {
        this.s3Presigner = s3Presigner;
        this.environmentSetting = environmentSetting;
    }

    @Override
    public PresignUploadResponse generatePresignedUrl(UploadFolder folder, long userId, String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Unsupported content type. Allowed: image/jpeg, image/png, image/webp, image/gif");
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

    private String resolvePublicUrl(EnvironmentSetting.S3 s3Config, String key) {
        String base = s3Config.getPublicUrlBase();
        if (base != null && !base.isBlank()) {
            return base.stripTrailing() + "/" + key;
        }
        return "https://" + s3Config.getBucket() + ".s3." + s3Config.getRegion() + ".amazonaws.com/" + key;
    }
}
