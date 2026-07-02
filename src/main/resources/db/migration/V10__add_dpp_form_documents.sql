CREATE TYPE dpp_document_visibility AS ENUM (
    'CIRCULAR_OPERATORS',
    'PUBLIC_USERS',
    'AUTHORITIES'
);

ALTER TABLE dpp_forms ADD COLUMN main_photo_file_id UUID REFERENCES files(id);

CREATE TABLE dpp_form_documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dpp_form_id   UUID NOT NULL REFERENCES dpp_forms(id),
    file_id       UUID NOT NULL REFERENCES files(id),
    document_type VARCHAR(100) NOT NULL,
    visibility    dpp_document_visibility NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON dpp_form_documents(dpp_form_id);

CREATE TRIGGER trg_dpp_documents_immutable
    BEFORE UPDATE OR DELETE ON dpp_form_documents
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();
