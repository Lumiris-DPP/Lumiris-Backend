-- Un scan (QR ouvert plusieurs fois de suite par le même visiteur, ou un scanner de sécurité de
-- client mail qui pré-visite les liens sortants) ne doit pas spammer le propriétaire d'un email par
-- scan. last_scan_notified_at porte le throttle : une notification par fenêtre, l'event analytics
-- (passport_analytics_events), lui, continue d'enregistrer chaque scan sans exception.
ALTER TABLE dpp_forms ADD COLUMN last_scan_notified_at TIMESTAMPTZ;
