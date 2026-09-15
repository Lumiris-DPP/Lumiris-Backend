# Registre des activités de traitement — Lumiris

> Art. 30 RGPD. Pré-rempli à partir du code au 2026-09-08. **À faire valider par le DPO / responsable de traitement** puis tenir à jour à chaque nouveau traitement.

- **Responsable de traitement :** Lumiris SAS — _[adresse, SIREN]_
- **Représentant / point de contact RGPD :** privacy@lumiris.fr — _[nom]_
- **DPO :** _[nommé ? oui/non — coordonnées]_

---

## 1. Comptes & authentification

| Champ | Valeur |
|---|---|
| Finalité | Créer et sécuriser l'accès au service (artisan, retoucheur, consommateur, admin) |
| Base légale | Exécution du contrat (art. 6.1.b) |
| Catégories de personnes | Utilisateurs inscrits |
| Catégories de données | E-mail, nom, mot de passe (haché bcrypt), rôle, avatar, `stripe_customer_id`, dates de création / dernière connexion, IP (logs) |
| Source | La personne concernée |
| Destinataires | Interne ; Stripe (rattachement client) |
| Sous-traitants | Hébergeur _[à préciser]_, Stripe |
| Transferts hors UE | Stripe (USA) — clauses contractuelles types |
| Durée de conservation | Compte actif + **suppression douce 30 j** (`users.deleted_at`) puis **anonymisation définitive** (`AccountPurgeScheduler` → `users.anonymized_at`) |
| Sécurité | JWT courte durée (15 min) + refresh révocables, HTTPS, rate limiting, mots de passe bcrypt |

## 2. Dossier KYB artisan / retoucheur

| Champ | Valeur |
|---|---|
| Finalité | Vérifier l'identité et la légitimité professionnelle (lutte anti-fraude, obligations plateforme) |
| Base légale | Obligation légale / intérêt légitime (art. 6.1.c / 6.1.f) |
| Catégories de données | Entité légale (SIRET, raison sociale, code NAF), représentant légal (nom, fonction), pièces justificatives téléversées, date d'expiration, résultat du contrôle OCR du nom, statut de revue (5 états) |
| Destinataires | Équipe conformité Lumiris |
| Sous-traitants | Stockage objet (MinIO / S3 compatible) ; API SIRENE (INSEE) pour la vérification SIRET |
| Durée de conservation | Durée de la relation + **5 ans** après clôture (obligation anti-blanchiment / preuve) — _à confirmer_ |
| Mesures | Accès restreint admin, documents non publics, URLs signées |

## 3. Place de marché — commandes & paiements

| Champ | Valeur |
|---|---|
| Finalité | Traiter les commandes, paiements, expéditions, litiges et rétractations |
| Base légale | Exécution du contrat |
| Catégories de données | Lignes de commande, montants, adresse de livraison, suivi transporteur, historique d'événements de commande, `stripe_subscription_id` |
| Sous-traitants | Stripe (paiement, Klarna paiement fractionné), Sendcloud (étiquettes & suivi) |
| Transferts hors UE | Stripe (USA) — CCT |
| Durée de conservation | **10 ans** (obligation comptable / factures) — dissociées de l'identité après anonymisation du compte |

## 4. Réseau de retouche — demandes d'intervention

| Champ | Valeur |
|---|---|
| Finalité | Mettre en relation un consommateur et un retoucheur, gérer devis et rendez-vous |
| Base légale | Exécution du contrat |
| Catégories de données | Message libre, passeport concerné, devis (montant, description), date de rendez-vous, statut |
| Destinataires | Le retoucheur destinataire de la demande |
| Durée de conservation | Durée de la relation + _[X]_ mois |

## 5. Notifications transactionnelles

| Champ | Valeur |
|---|---|
| Finalité | Informer des événements (commande, scan de passeport, garantie, etc.) |
| Base légale | Exécution du contrat / intérêt légitime |
| Catégories de données | E-mail, contenu de la notification, abonnements Web Push (endpoint navigateur) |
| Sous-traitants | Resend (e-mail) ; navigateur / push service (VAPID) |
| Transferts hors UE | Resend (USA) — CCT |
| Durée de conservation | Notifications in-app : _[X]_ mois ; abonnements push : jusqu'à désinscription |

## 6. Analytics d'audience des passeports

| Champ | Valeur |
|---|---|
| Finalité | Statistiques agrégées d'audience et de conversion pour l'artisan (offre ATELIER / ATELIER+) |
| Base légale | Intérêt légitime |
| Catégories de données | **Aucune donnée personnelle d'utilisateur final** : uniquement `{passeport, type d'événement, horodatage}` (`passport_analytics_events`) — pas d'IP, pas de session, pas d'identifiant |
| Durée de conservation | _[X]_ mois glissants |
| Remarque | Traitement volontairement anonyme dès la collecte — hors périmètre « données personnelles » côté utilisateur final |

## 7. Télémétrie technique (Web Vitals) & supervision

| Champ | Valeur |
|---|---|
| Finalité | Mesure de performance front, détection d'erreurs |
| Base légale | Intérêt légitime |
| Catégories de données | Métriques Web Vitals (anonymes) ; Sentry avec `send-default-pii: false` ; traces OpenTelemetry |
| Sous-traitants | Sentry _[région ?]_ |
| Durée de conservation | Selon rétention Sentry / backend d'observabilité |

## 8. Géocodage

| Champ | Valeur |
|---|---|
| Finalité | Positionner les retoucheurs sur la carte, recherche géolocalisée |
| Base légale | Exécution du contrat |
| Données transmises | Adresse professionnelle du retoucheur → Nominatim (OpenStreetMap) |
| Durée | Coordonnées stockées tant que le profil existe |

## 9. Prospection retoucheurs (annuaire → réclamation de fiche)

| Champ | Valeur |
|---|---|
| Finalité | Constituer le réseau de retoucheurs : importer des fiches depuis le registre public des entreprises, inviter les professionnels à réclamer leur fiche |
| Base légale | Intérêt légitime (art. 6.1.f) — prospection B2B : destinataires professionnels, message lié à leur activité, opt-out simple |
| Catégories de personnes | Retoucheurs / cordonniers / couturiers (souvent entrepreneurs individuels → données personnelles) |
| Catégories de données | Raison sociale, SIRET, adresse professionnelle, coordonnées de contact ; jeton de réclamation ; suivi d'envoi (`repairer_prospect_outreach`) |
| Source | API « Recherche d'entreprises » (recherche-entreprises.api.gouv.fr), données publiques du registre |
| Information des personnes | Mentionnée dans chaque e-mail : identité de Lumiris, source des données, finalité, lien de désinscription, contact `privacy@lumiris.fr` |
| Destinataires | Interne ; Resend (envoi e-mail) |
| Durée de conservation | Fiche non réclamée : jusqu'à réclamation ou purge annuelle des fiches inactives ; `email_suppression` (désinscrits) : conservée sans limite pour ne plus recontacter |
| Droits | Désinscription en un clic (`/v1/prospecting/unsubscribe/{id}`) ; opposition / effacement via `privacy@lumiris.fr` |

## 10. Ancrage blockchain des passeports

| Champ | Valeur |
|---|---|
| Finalité | Preuve d'intégrité (hash) du passeport produit |
| Données | **Hash uniquement** — aucune donnée personnelle inscrite on-chain |
| Base légale | Exécution du contrat / intérêt légitime |

---

## Droits des personnes — mise en œuvre

| Droit | Mécanisme | SLA |
|---|---|---|
| Accès / portabilité | `GET /api/auth/me/export` → archive ZIP JSON | **< 48 h** (synchrone) |
| Effacement | `DELETE /api/auth/me` → suppression douce, puis anonymisation auto | **< 30 j** |
| Rectification | Édition du profil dans l'app / support | — |
| Opposition / limitation | Support privacy@lumiris.fr | 1 mois |

## Sous-traitants — récapitulatif

Stripe · Sendcloud · Resend · Sentry · MinIO/S3 _[hébergeur]_ · INSEE SIRENE · Nominatim/OSM · _[hébergeur infra]_ · _[fournisseur RPC Ethereum]_

> Vérifier qu'un accord de sous-traitance (art. 28) est signé avec chacun.
