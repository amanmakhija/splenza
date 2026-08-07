package com.splitwise.app.storage;

/**
 * Abstraction over "put a file somewhere and get a public URL back". Every
 * caller in the app (profile photos, group photos, anything added later) talks
 * to this interface only - never to an S3Client, a GCS Storage client, etc.
 * directly.
 *
 * Why this exists: the app currently uses S3, but there's a real chance of
 * moving to GCS or Cloudflare R2 later. Because everything upload-related goes
 * through this interface, that migration becomes: (1) implement this interface
 * against the new provider's SDK, (2) flip `app.storage.provider` in config,
 * (3) done - no controller or service code that uses storage has to change at
 * all.
 *
 * To add a new provider: 1. Add its SDK as a dependency in pom.xml. 2. Create a
 * new class implementing StorageService (see S3StorageService for the shape),
 * annotated `@Service @ConditionalOnProperty(prefix = "app.storage", name =
 * "provider", havingValue = "yourProviderName")`. 3. Set
 * `app.storage.provider=yourProviderName` in config.
 *
 * Note: Cloudflare R2 specifically doesn't need a new implementation at all -
 * it speaks the S3 API, so S3StorageService already works against it. Just
 * point `app.storage.s3.endpoint` at your R2 account endpoint and set
 * `app.storage.s3.path-style-access=true`. See PROGRESS.md.
 */
public interface StorageService {

    /**
     * Uploads the given bytes under the given key and returns the resulting
     * public URL. Overwrites whatever was already at that key, if anything.
     *
     * @param content raw file bytes
     * @param contentType sniffed MIME type (e.g. "image/jpeg") - NOT the
     * client-declared one, see ImageContentSniffer
     * @param key storage key/path, e.g. "users/{userId}/{uuid}.jpg"
     */
    UploadedFile upload(byte[] content, String contentType, String key);

    /**
     * Best-effort delete of a previously-uploaded file, given the public URL
     * that {@link #upload} returned for it. Implementations should log and
     * swallow failures here rather than throw - failing to clean up an old
     * photo should never block the request that's replacing it.
     */
    void delete(String url);

    /**
     * Short identifier for logging/diagnostics, e.g. "s3".
     */
    String getProviderName();
}
