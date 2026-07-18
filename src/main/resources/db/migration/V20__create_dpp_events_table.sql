CREATE TABLE dpp_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dpp_form_id UUID        NOT NULL REFERENCES dpp_forms(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    description TEXT        NOT NULL,
    actor_type  VARCHAR(20) NOT NULL CHECK (actor_type IN ('MANUFACTURER','DISTRIBUTOR','RETAILER','CONSUMER','REPAIRER','RECYCLER')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ON dpp_events(dpp_form_id);

CREATE TRIGGER trg_dpp_events_immutable
    BEFORE UPDATE OR DELETE ON dpp_events
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();
