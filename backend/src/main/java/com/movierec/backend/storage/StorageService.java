package com.movierec.backend.storage;

import java.io.InputStream;

/**
 * Generic object-storage abstraction. Avatars are the first caller, but this is intentionally
 * not avatar-specific — any future file type (movie posters, admin-uploaded assets, etc.) can
 * reuse it by picking its own key prefix.
 */
public interface StorageService {

    /**
     * Uploads {@code data} under {@code key}, replacing any existing object at that key, and
     * returns a publicly-accessible URL for it.
     */
    String upload(String key, InputStream data, long size, String contentType);

    /** Deletes the object at {@code key}. A no-op if it doesn't exist. */
    void delete(String key);
}
