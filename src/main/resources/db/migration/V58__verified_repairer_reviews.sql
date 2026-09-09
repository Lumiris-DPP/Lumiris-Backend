-- Anti-faux-avis : un avis rattaché à une intervention réellement terminée. Nullable pour les
-- avis existants (seed / imports) ; un avis par demande de réparation au plus.
ALTER TABLE repairer_reviews
    ADD COLUMN repair_request_id UUID REFERENCES repair_requests(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_repairer_reviews_request
    ON repairer_reviews (repair_request_id)
    WHERE repair_request_id IS NOT NULL;
