-- Outbox transactionnel des emails : NotificationService écrit une ligne ici dans la même
-- transaction que l'événement métier (Notification, commande...) ; EmailOutboxDispatcher lit et
-- envoie via Resend en dehors de cette transaction, avec retry (next_attempt_at, backoff
-- exponentiel) et DLQ (status DEAD) une fois max_attempts épuisé. La table sert aussi de log
-- d'envoi consultable côté admin.
CREATE TABLE email_outbox (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_email VARCHAR(320) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    template        VARCHAR(100) NOT NULL,
    variables       JSONB        NOT NULL DEFAULT '{}',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD'))
);

-- Requête du worker : lignes PENDING dues, dans l'ordre.
CREATE INDEX idx_email_outbox_dispatch ON email_outbox (status, next_attempt_at);
