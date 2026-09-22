CREATE TABLE supplier_invoices (
    id                UUID PRIMARY KEY,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id),
    file_id           UUID NOT NULL REFERENCES files(id),
    ocr_raw_text      TEXT,
    supplier_name     TEXT,
    invoice_date      DATE,
    total_ht          NUMERIC(12, 2),
    currency          TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_supplier_invoices_uploaded_by ON supplier_invoices (uploaded_by_user_id);

CREATE TABLE supplier_invoice_line_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_invoice_id UUID NOT NULL REFERENCES supplier_invoices(id) ON DELETE CASCADE,
    line_order          INTEGER NOT NULL,
    fiber               TEXT,
    label               TEXT NOT NULL,
    qty                 NUMERIC(12, 3) NOT NULL,
    unit                TEXT NOT NULL
);

CREATE INDEX idx_supplier_invoice_line_items_invoice ON supplier_invoice_line_items (supplier_invoice_id);
