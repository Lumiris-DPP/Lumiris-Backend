-- Drafts have no QR identity nor content hash until publication.
ALTER TABLE dpp_forms ALTER COLUMN public_code DROP NOT NULL;
ALTER TABLE dpp_forms ALTER COLUMN data_hash DROP NOT NULL;

-- Immutability is a guarantee about *published* passports. A DRAFT form is fully
-- editable and deletable; publication (DRAFT -> VALID) is the only way out of
-- DRAFT, and a published form can never go back.
CREATE OR REPLACE FUNCTION fn_dpp_forms_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_new_status                   dpp_status;
    v_new_blockchain_tx_hash       TEXT;
    v_new_blockchain_anchor_status VARCHAR(20);
BEGIN
    IF OLD.status = 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        IF NEW.status NOT IN ('DRAFT', 'VALID') THEN
            RAISE EXCEPTION 'A draft DPP can only stay DRAFT or be published to VALID';
        END IF;
        NEW.updated_at := now();
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'DPP records cannot be deleted';
    END IF;
    IF NEW.status = 'DRAFT' THEN
        RAISE EXCEPTION 'A published DPP cannot go back to DRAFT';
    END IF;

    v_new_status                   := NEW.status;
    v_new_blockchain_tx_hash       := NEW.blockchain_tx_hash;
    v_new_blockchain_anchor_status := NEW.blockchain_anchor_status;

    -- Restore the entire row, then apply only the allowed changes
    NEW                            := OLD;
    NEW.status                     := v_new_status;
    NEW.blockchain_tx_hash         := v_new_blockchain_tx_hash;
    NEW.blockchain_anchor_status   := v_new_blockchain_anchor_status;
    NEW.updated_at                 := now();

    RETURN NEW;
END;
$$;

-- Child rows (materials, care instructions, documents, events, scores) follow
-- their parent: editable while the parent form is a DRAFT.
CREATE OR REPLACE FUNCTION fn_dpp_children_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_parent_status dpp_status;
BEGIN
    SELECT status INTO v_parent_status FROM dpp_forms WHERE id = OLD.dpp_form_id;
    IF v_parent_status = 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'DPP child records are immutable and cannot be modified or deleted';
END;
$$;
