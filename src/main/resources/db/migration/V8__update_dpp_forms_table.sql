DROP TABLE IF EXISTS dpp_forms;

CREATE TYPE dpp_status AS ENUM ('VALID', 'INVALID');

-- dpp_forms : only status can be updated
CREATE OR REPLACE FUNCTION fn_dpp_forms_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_new_status dpp_status;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'DPP records cannot be deleted';
    END IF;

    v_new_status := NEW.status;

    -- Restore the entire row, then apply only the allowed change
    NEW            := OLD;
    NEW.status     := v_new_status;
    NEW.updated_at := now();

    RETURN NEW;
END;
$$;

-- Child tables (materials, care instructions, certifications) : fully immutable
CREATE OR REPLACE FUNCTION fn_dpp_children_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'DPP child records are immutable and cannot be modified or deleted';
END;
$$;

CREATE TABLE dpp_forms (
                           id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                           created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           user_id                  UUID         NOT NULL REFERENCES users(id),
                           status                   dpp_status   NOT NULL DEFAULT 'VALID',
                           product_name             TEXT,
                           product_description      TEXT,
                           product_category         TEXT,
                           origin_country           TEXT,
                           main_photo_url           TEXT,
                           manufactured_at          TEXT,
                           batch_number             TEXT,
                           gtin                     TEXT         UNIQUE,
                           sku                      TEXT,
                           reach_compliant          BOOLEAN      NOT NULL DEFAULT FALSE,
                           recycled_pct             SMALLINT     CHECK (recycled_pct BETWEEN 0 AND 100),
                           warranty_description     TEXT,
                           is_repairable            BOOLEAN      NOT NULL DEFAULT FALSE,
                           end_of_life_instructions TEXT,
                           available_sizes          TEXT[],
                           colors                   TEXT[]
);

CREATE TABLE dpp_materials (
                               id              BIGSERIAL   PRIMARY KEY,
                               dpp_form_id     UUID        NOT NULL REFERENCES dpp_forms(id),
                               fiber           TEXT        NOT NULL,
                               percentage      SMALLINT    NOT NULL CHECK (percentage BETWEEN 0 AND 100),
                               origin_country  TEXT
);

CREATE TABLE dpp_care_instructions (
                                       id          BIGSERIAL   PRIMARY KEY,
                                       dpp_form_id UUID        NOT NULL REFERENCES dpp_forms(id),
                                       care_code   TEXT        NOT NULL,
                                       UNIQUE (dpp_form_id, care_code)
);

CREATE TABLE dpp_certifications (
                                    id              BIGSERIAL   PRIMARY KEY,
                                    dpp_form_id     UUID        NOT NULL REFERENCES dpp_forms(id),
                                    name            TEXT        NOT NULL,
                                    custom_name     TEXT,
                                    license_number  TEXT
);

CREATE INDEX ON dpp_forms(user_id);
CREATE INDEX ON dpp_materials(dpp_form_id);
CREATE INDEX ON dpp_care_instructions(dpp_form_id);
CREATE INDEX ON dpp_certifications(dpp_form_id);

CREATE TRIGGER trg_dpp_forms_immutable
    BEFORE UPDATE OR DELETE ON dpp_forms
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_forms_immutable();

CREATE TRIGGER trg_dpp_materials_immutable
    BEFORE UPDATE OR DELETE ON dpp_materials
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();

CREATE TRIGGER trg_dpp_care_immutable
    BEFORE UPDATE OR DELETE ON dpp_care_instructions
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();

CREATE TRIGGER trg_dpp_certifications_immutable
    BEFORE UPDATE OR DELETE ON dpp_certifications
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();
