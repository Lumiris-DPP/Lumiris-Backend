-- Bibliothèque de certificats réutilisables : un artisan pré-uploade un certificat une fois,
-- puis le rattache à plusieurs DPP. file_id référence toujours `files(id)` directement (jamais
-- cette table) — dpp_form_documents garde sa forme actuelle, donc supprimer une ligne ici ne
-- peut jamais invalider un DPP qui référence déjà ce fichier.
CREATE TYPE certificate_type AS ENUM ('ORIGIN', 'TRANSACTION');

CREATE TABLE certificate_library (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_id    UUID NOT NULL REFERENCES files(id),
    type       certificate_type NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_certificate_library_user ON certificate_library(user_id);
