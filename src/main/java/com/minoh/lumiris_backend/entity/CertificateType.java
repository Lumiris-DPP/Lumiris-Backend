package com.minoh.lumiris_backend.entity;

// Sous-ensemble volontairement restreint de DocumentType : la bibliothèque ne couvre que les
// deux types de certificats réutilisables, chacun mappé sur son DocumentType pour résoudre le
// partName au moment de l'attachement à un DPP (voir DppFormService.resolveCertificateLibraryRefs).
public enum CertificateType {
    ORIGIN(DocumentType.ORIGIN_CERTIFICATES),
    TRANSACTION(DocumentType.TRANSACTION_CERTIFICATES);

    public final DocumentType documentType;

    CertificateType(DocumentType documentType) {
        this.documentType = documentType;
    }
}
