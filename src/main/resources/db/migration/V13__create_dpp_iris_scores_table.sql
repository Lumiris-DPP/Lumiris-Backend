CREATE TABLE dpp_iris_scores (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dpp_form_id   UUID         NOT NULL UNIQUE REFERENCES dpp_forms(id),
    transparency  NUMERIC(5,2) NOT NULL DEFAULT 0,
    craftsmanship NUMERIC(5,2) NOT NULL DEFAULT 0,
    repairability NUMERIC(5,2) NOT NULL DEFAULT 0,
    impact        NUMERIC(5,2) NOT NULL DEFAULT 0,
    total         NUMERIC(5,2) NOT NULL DEFAULT 0,
    grade         TEXT         NOT NULL DEFAULT 'E',
    computed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ON dpp_iris_scores(dpp_form_id);

CREATE TRIGGER trg_dpp_iris_scores_immutable
    BEFORE UPDATE OR DELETE ON dpp_iris_scores
    FOR EACH ROW EXECUTE FUNCTION fn_dpp_children_immutable();
