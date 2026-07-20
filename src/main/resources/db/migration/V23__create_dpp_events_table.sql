-- Aggregate-only analytics events. Deliberately no end-user PII: no IP, no session,
-- no user id — just which passport, what happened, and when. See LUMIRIS-5.
CREATE TABLE dpp_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dpp_form_id UUID        NOT NULL REFERENCES dpp_forms(id) ON DELETE CASCADE,
    event_type  VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dpp_events_dpp_form_id ON dpp_events(dpp_form_id);
CREATE INDEX idx_dpp_events_occurred_at ON dpp_events(occurred_at);
