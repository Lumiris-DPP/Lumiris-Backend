package com.minoh.lumiris_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum DocumentType {

    PRODUCT_PHOTO        ("productPhoto",       DppDocumentVisibility.PUBLIC_USERS),
    CARE_GUIDE           ("careGuide",          DppDocumentVisibility.PUBLIC_USERS),
    ORIGIN_CERTIFICATES  ("originCerts",        DppDocumentVisibility.PUBLIC_USERS),

    REPAIR_MANUAL        ("repairManual",        DppDocumentVisibility.CIRCULAR_OPERATORS),
    END_OF_LIFE_GUIDE    ("endOfLifeGuide",      DppDocumentVisibility.CIRCULAR_OPERATORS),
    TEST_REPORTS         ("testReports",         DppDocumentVisibility.CIRCULAR_OPERATORS),
    TRANSACTION_CERTIFICATES("transactionCerts", DppDocumentVisibility.CIRCULAR_OPERATORS),
    CREATION_PASSPORT    ("creationPassport",    DppDocumentVisibility.CIRCULAR_OPERATORS),

    EU_DOC_OF_CONFORMITY ("euDeclaration",       DppDocumentVisibility.AUTHORITIES),
    REACH_COMPLIANCE     ("reachCompliance",     DppDocumentVisibility.AUTHORITIES),
    SALE_INVOICE         ("saleInvoice",         DppDocumentVisibility.AUTHORITIES);

    public final String partName;
    private final DppDocumentVisibility defaultVisibility;

    DocumentType(String partName, DppDocumentVisibility defaultVisibility) {
        this.partName = partName;
        this.defaultVisibility = defaultVisibility;
    }

    public DppDocumentVisibility defaultVisibility() {
        return defaultVisibility;
    }

    private static final Map<String, DocumentType> BY_PART_NAME;
    static {
        Map<String, DocumentType> m = new HashMap<>();
        for (DocumentType t : values()) m.put(t.partName, t);
        BY_PART_NAME = Collections.unmodifiableMap(m);
    }

    public static Optional<DocumentType> fromPartName(String partName) {
        return Optional.ofNullable(BY_PART_NAME.get(partName));
    }

    @JsonCreator
    public static DocumentType fromJson(String value) {
        return fromPartName(value).orElseGet(() -> DocumentType.valueOf(value));
    }
}
