-- Livraison stockée explicitement sur la commande (portée par la 1re ligne du panier, 0 sur les
-- autres). Permet à l'écran de confirmation d'afficher le montant EXACTEMENT débité par Stripe
-- (articles + livraison), au lieu du seul total d'articles. Historiquement le port n'était que
-- replié dans net_cents ; on le matérialise pour un récapitulatif fidèle.
ALTER TABLE marketplace_orders
    ADD COLUMN shipping_cents INTEGER NOT NULL DEFAULT 0 CHECK (shipping_cents >= 0);
