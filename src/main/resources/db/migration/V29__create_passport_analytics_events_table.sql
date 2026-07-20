-- Aggregate-only analytics events. Deliberately no end-user PII: no IP, no session,
-- no user id — just which passport, what happened, and when. See LUMIRIS-5.
-- Not to be confused with dpp_events (V20), which is the supply-chain custody history log.
CREATE TABLE passport_analytics_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dpp_form_id UUID        NOT NULL REFERENCES dpp_forms(id) ON DELETE CASCADE,
    event_type  VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_passport_analytics_events_dpp_form_id ON passport_analytics_events(dpp_form_id);
CREATE INDEX idx_passport_analytics_events_occurred_at ON passport_analytics_events(occurred_at);
