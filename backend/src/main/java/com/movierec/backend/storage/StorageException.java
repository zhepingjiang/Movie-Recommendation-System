package com.movierec.backend.storage;

/** Wraps checked failures from the underlying object-storage client. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
