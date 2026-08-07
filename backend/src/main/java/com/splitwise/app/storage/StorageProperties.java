package com.splitwise.app.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `app.storage.*` config block. `provider` selects which
 * StorageService bean is active (see StorageService javadoc for how to add a
 * new one) - everything else is provider-specific settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * "s3" today; add more as new StorageService implementations are added.
     */
    private String provider = "s3";

    private long maxFileSizeBytes = 10L * 1024 * 1024;

    private S3 s3 = new S3();

    @Data
    public static class S3 {

        private String bucket;

        private String region = "us-east-1";

        /**
         * Leave blank for real AWS S3. Set this to point the SAME client at an
         * S3-compatible provider instead, e.g. a Cloudflare R2 account endpoint
         * (https://<account-id>.r2.cloudflarestorage.com) or a GCS
         * XML-API-compatible endpoint - no code change needed for those, just
         * config.
         */
        private String endpoint;

        /**
         * If set, generated URLs use this as the base (e.g. a CDN domain or R2
         * public bucket URL) instead of the default
         * https://{bucket}.s3.{region}.amazonaws.com form.
         */
        private String publicBaseUrl;

        /**
         * Most S3-compatible providers other than AWS (including R2) need
         * path-style requests (https://host/bucket/key) rather than
         * virtual-hosted-style (https://bucket.host/key). Leave false for real
         * AWS S3.
         */
        private boolean pathStyleAccess = false;

        /**
         * Optional explicit static credentials. Leave blank to fall back to the
         * SDK's default credential chain (env vars, instance profile on EC2,
         * etc.) - the recommended approach in production.
         */
        private String accessKeyId;
        private String secretAccessKey;
    }
}
