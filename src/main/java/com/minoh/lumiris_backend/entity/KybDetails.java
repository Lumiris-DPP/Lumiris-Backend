package com.minoh.lumiris_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared by ArtisanProfile and RepairerProfile: the deeper KYB dossier (legal entity, legal
 * representative, documents, terms consent) collected after the fast SIRET-only registration,
 * plus a read-only snapshot of what SIRENE reports for the same SIRET so an admin can compare
 * the two before verifying the account.
 */
@Embeddable
@Getter
@Setter
public class KybDetails {

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "kyb_legal_category")
    private LegalCategory legalCategory;

    @Column(name = "kyb_business_entity")
    private String businessEntity;

    @Column(name = "kyb_vat_number")
    private String vatNumber;

    @Column(name = "kyb_address_line1")
    private String addressLine1;

    @Column(name = "kyb_address_city")
    private String addressCity;

    @Column(name = "kyb_address_postal_code")
    private String addressPostalCode;

    @Column(name = "kyb_address_country")
    private String addressCountry;

    @Column(name = "kyb_terms_accepted_at")
    private Instant termsAcceptedAt;

    // Legal representative (natural person)
    @Column(name = "kyb_rep_first_name")
    private String repFirstName;

    @Column(name = "kyb_rep_last_name")
    private String repLastName;

    @Column(name = "kyb_rep_birth_date")
    private LocalDate repBirthDate;

    @Column(name = "kyb_rep_nationality")
    private String repNationality;

    @Column(name = "kyb_rep_address_line1")
    private String repAddressLine1;

    @Column(name = "kyb_rep_address_city")
    private String repAddressCity;

    @Column(name = "kyb_rep_address_postal_code")
    private String repAddressPostalCode;

    @Column(name = "kyb_rep_address_country")
    private String repAddressCountry;

    // Ultimate Beneficial Owner: true when the representative also owns >25% of the company
    // (most KYC/AML regimes require this to be declared separately from "is the signatory").
    @Column(name = "kyb_rep_is_ubo")
    private Boolean repIsUbo;

    @Column(name = "kyb_rep_ownership_percentage")
    private Integer repOwnershipPercentage;

    // Documents — UUID references to `files` (StorageService/MinIO), uploaded one at a time.
    @Column(name = "kyb_doc_id_file_id")
    private UUID idDocFileId;

    @Column(name = "kyb_doc_kbis_file_id")
    private UUID kbisFileId;

    @Column(name = "kyb_doc_proof_of_address_file_id")
    private UUID proofOfAddressFileId;

    @Column(name = "kyb_doc_rib_file_id")
    private UUID ribFileId;

    // SIRENE snapshot captured at registration time (read-only) — the "ground truth" an admin
    // compares the declared fields above against. Not user-editable.
    @Column(name = "kyb_sirene_siren")
    private String sireneSiren;

    @Column(name = "kyb_sirene_siege_address")
    private String sireneSiegeAddress;

    @Column(name = "kyb_sirene_nature_juridique")
    private String sireneNatureJuridique;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kyb_sirene_dirigeants", columnDefinition = "jsonb")
    private String sireneDirigeantsJson;
}
