CREATE OR REPLACE FUNCTION fn_dpp_forms_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_new_status                   dpp_status;
    v_new_blockchain_tx_hash       TEXT;
    v_new_blockchain_anchor_status VARCHAR(20);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'DPP records cannot be deleted';
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
