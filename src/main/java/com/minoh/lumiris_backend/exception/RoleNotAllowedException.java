package com.minoh.lumiris_backend.exception;

// A client tried to self-assign a privileged role (ADMIN/REPAIRER) at registration → HTTP 403.
public class RoleNotAllowedException extends RuntimeException {
    public RoleNotAllowedException(String message) {
        super(message);
    }
}
