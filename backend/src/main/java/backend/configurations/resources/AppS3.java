package backend.configurations.resources;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import backend.configurations.environment.EnvironmentSetting;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AppS3 {

    private final EnvironmentSetting environmentSetting;

    public AppS3(EnvironmentSetting environmentSetting) {
        this.environmentSetting = environmentSetting;
    }

    @Bean
    public S3Presigner s3Presigner() {
        EnvironmentSetting.S3 s3 = environmentSetting.getS3();
        return S3Presigner.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
                ))
                .build();
    }
}
