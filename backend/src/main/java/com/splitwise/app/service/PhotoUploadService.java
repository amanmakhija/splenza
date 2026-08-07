package com.splitwise.app.service;

import com.splitwise.app.exception.ApiException;
import com.splitwise.app.storage.ImageContentSniffer;
import com.splitwise.app.storage.StorageProperties;
import com.splitwise.app.storage.StorageService;
import com.splitwise.app.storage.UploadedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Shared logic behind both POST /api/v1/users/me/photo and POST
 * /api/v1/groups/{groupId}/photo: validate the upload, sniff its real content
 * type, store it, and clean up whatever was there before. Kept as one place so
 * both endpoints enforce identical rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private final StorageService storageService;
    private final StorageProperties storageProperties;

    /**
     * Validates and uploads {@code file} under a fresh key below
     * {@code keyPrefix}, then best-effort deletes {@code previousUrl} if one
     * was passed in. Returns the new public URL - the caller is responsible for
     * persisting it onto the User/Group record.
     */
    public String uploadAndReplace(MultipartFile file, String keyPrefix, String previousUrl) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file was provided");
        }
        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            throw ApiException.badRequest(
                    "File exceeds the maximum allowed size of " + (storageProperties.getMaxFileSizeBytes() / (1024 * 1024)) + "MB");
        }

        byte[] content = readBytes(file);

        // Never trust the client's declared Content-Type (it defaults to
        // image/jpeg for anything it doesn't recognize) - sniff the real
        // format from the file's magic bytes instead.
        String contentType = ImageContentSniffer.detect(content);
        if (contentType == null) {
            throw ApiException.badRequest(
                    "File must be a valid JPEG, PNG, WEBP, or HEIC image");
        }

        String key = keyPrefix + "/" + UUID.randomUUID() + "." + ImageContentSniffer.extensionFor(contentType);
        UploadedFile uploaded = storageService.upload(content, contentType, key);

        if (previousUrl != null && !previousUrl.isBlank()) {
            // Best-effort - StorageService implementations already swallow
            // their own delete failures, so this never blocks the response.
            storageService.delete(previousUrl);
        }

        return uploaded.url();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read uploaded file bytes.", e);
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }
}
