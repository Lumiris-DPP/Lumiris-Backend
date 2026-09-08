-- Per-document expiry dates, best-effort OCR text for the ID document, and a fine-grained KYB
-- review status (distinct from the coarser account-level PENDING/VERIFIED/REJECTED status).

ALTER TABLE artisan_profiles
    ADD COLUMN kyb_doc_id_expires_at              DATE,
    ADD COLUMN kyb_doc_id_ocr_text                TEXT,
    ADD COLUMN kyb_doc_kbis_expires_at            DATE,
    ADD COLUMN kyb_doc_proof_of_address_expires_at DATE,
    ADD COLUMN kyb_doc_rib_expires_at             DATE,
    ADD COLUMN kyb_status                         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN kyb_review_note                    TEXT;

ALTER TABLE repairer_profiles
    ADD COLUMN kyb_doc_id_expires_at              DATE,
    ADD COLUMN kyb_doc_id_ocr_text                TEXT,
    ADD COLUMN kyb_doc_kbis_expires_at            DATE,
    ADD COLUMN kyb_doc_proof_of_address_expires_at DATE,
    ADD COLUMN kyb_doc_rib_expires_at             DATE,
    ADD COLUMN kyb_status                         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN kyb_review_note                    TEXT;
