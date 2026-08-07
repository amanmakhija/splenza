package com.splitwise.app.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final StorageProperties storageProperties;

    /**
     * Only created when app.storage.provider=s3 (the default). If/when a
     * different provider is added, its config class gates its own client bean
     * the same way, so an unused SDK client is never built.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
    public S3Client s3Client() {
        StorageProperties.S3 s3Props = storageProperties.getS3();

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3Props.isPathStyleAccess())
                        .build());

        if (s3Props.getEndpoint() != null && !s3Props.getEndpoint().isBlank()) {
            // Set for Cloudflare R2 or any other S3-API-compatible provider.
            log.info("Configuring S3 client with custom endpoint: {}", s3Props.getEndpoint());
            builder.endpointOverride(URI.create(s3Props.getEndpoint()));
        }

        if (s3Props.getAccessKeyId() != null && !s3Props.getAccessKeyId().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.getAccessKeyId(), s3Props.getSecretAccessKey())));
        }
        // else: falls back to the SDK default credential chain (EC2 instance
        // profile in production, env vars locally).

        return builder.build();
    }
}
