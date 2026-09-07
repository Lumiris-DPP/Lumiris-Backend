CREATE TABLE supplier_invoices (
                                   id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   file_id       UUID NOT NULL REFERENCES files(id),
                                   supplier_name VARCHAR(200) NOT NULL,
                                   issued_at     DATE NOT NULL,
                                   total_amount  NUMERIC(10,2) NOT NULL,
                                   notes         TEXT,
                                   created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_supplier_invoices_user ON supplier_invoices(user_id);

-- Composition fibre saisie manuellement à l'import — une ligne par fibre déclarée.
CREATE TABLE supplier_invoice_fibers (
                                         id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         supplier_invoice_id UUID NOT NULL REFERENCES supplier_invoices(id) ON DELETE CASCADE,
                                         fiber              VARCHAR(50) NOT NULL,
                                         percentage         SMALLINT NOT NULL CHECK (percentage BETWEEN 0 AND 100)
);
CREATE INDEX idx_supplier_invoice_fibers_invoice ON supplier_invoice_fibers(supplier_invoice_id);