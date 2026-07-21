package com.minoh.lumiris_backend.entity;

// Fine-grained review status for the KYB dossier itself, distinct from the coarser account-level
// status (PENDING/VERIFIED/REJECTED) that gates ATELIER login access. VALIDATED/REJECTED here
// drive that coarser status too; ONGOING/INCOMPLETE are review-workflow states that don't.
public enum KybStatus {
    PENDING, ONGOING, VALIDATED, REJECTED, INCOMPLETE
}
