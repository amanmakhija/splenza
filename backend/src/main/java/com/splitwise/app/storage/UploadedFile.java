package com.splitwise.app.storage;

/**
 * @param url the public URL to store back onto the User/Group record and hand
 * to the client
 * @param key the storage-internal key/path, kept around for logging/diagnostics
 * (deletion re-derives the key from the URL, since that's all callers have on
 * hand later)
 */
public record UploadedFile(String url, String key) {

}
