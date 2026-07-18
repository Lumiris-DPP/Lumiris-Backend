package com.minoh.lumiris_backend.exception;

// An artisan tried to publish their public profile before KYB verification → HTTP 403.
public class ArtisanNotVerifiedException extends RuntimeException {
    public ArtisanNotVerifiedException(String message) {
        super(message);
    }
}
