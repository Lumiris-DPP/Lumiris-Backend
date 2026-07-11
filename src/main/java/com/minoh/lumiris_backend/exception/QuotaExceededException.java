package com.minoh.lumiris_backend.exception;

// Passport creation would exceed the tier quota → HTTP 403, code QUOTA_EXCEEDED.
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
