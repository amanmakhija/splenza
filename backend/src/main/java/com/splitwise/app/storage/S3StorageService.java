package com.splitwise.app.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * StorageService backed by AWS S3 (or anything else that speaks the S3 API,
 * e.g. Cloudflare R2 - see StorageProperties.S3#endpoint). This is the only
 * class in the app that touches the S3 SDK directly; everything else goes
 * through the StorageService interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @Override
    public UploadedFile upload(byte[] content, String contentType, String key) {
        StorageProperties.S3 s3Props = storageProperties.getS3();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Props.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));

        String url = buildPublicUrl(key);
        log.info("Uploaded object to S3: key={}, bucket={}", key, s3Props.getBucket());
        return new UploadedFile(url, key);
    }

    @Override
    public void delete(String url) {
        String key = extractKey(url);
        if (key == null) {
            log.warn("Could not derive S3 key from URL '{}' - skipping delete of old file.", url);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucket())
                    .key(key)
                    .build());
            log.info("Deleted old S3 object: key={}", key);
        } catch (Exception e) {
            // Never let cleanup of an orphaned old file fail the request
            // that's replacing it - log and move on.
            log.warn("Failed to delete old S3 object (key={}): {}", key, e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "s3";
    }

    private String buildPublicUrl(String key) {
        StorageProperties.S3 s3Props = storageProperties.getS3();

        if (s3Props.getPublicBaseUrl() != null && !s3Props.getPublicBaseUrl().isBlank()) {
            return stripTrailingSlash(s3Props.getPublicBaseUrl()) + "/" + key;
        }
        if (s3Props.getEndpoint() != null && !s3Props.getEndpoint().isBlank()) {
            // Custom/S3-compatible endpoint (e.g. R2) without a separate
            // public CDN domain configured - fall back to path-style under
            // the endpoint itself.
            return stripTrailingSlash(s3Props.getEndpoint()) + "/" + s3Props.getBucket() + "/" + key;
        }
        return "https://" + s3Props.getBucket() + ".s3." + s3Props.getRegion() + ".amazonaws.com/" + key;
    }

    /**
     * Reverses buildPublicUrl() to recover the key for a delete call. We only
     * ever need to parse URLs this same class generated, so matching against
     * the same three prefixes it can produce is sufficient.
     */
    private String extractKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        StorageProperties.S3 s3Props = storageProperties.getS3();

        String[] candidatePrefixes = {
            s3Props.getPublicBaseUrl() != null ? stripTrailingSlash(s3Props.getPublicBaseUrl()) + "/" : null,
            s3Props.getEndpoint() != null
            ? stripTrailingSlash(s3Props.getEndpoint()) + "/" + s3Props.getBucket() + "/" : null,
            "https://" + s3Props.getBucket() + ".s3." + s3Props.getRegion() + ".amazonaws.com/"
        };

        for (String prefix : candidatePrefixes) {
            if (prefix != null && !prefix.isBlank() && url.startsWith(prefix)) {
                return url.substring(prefix.length());
            }
        }
        return null;
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
