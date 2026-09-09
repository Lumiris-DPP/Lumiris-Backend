# Réseau retoucheurs — amorçage par annuaire + réclamation de fiche

**But :** peupler l'offre avant le lancement. On importe des retoucheurs depuis des données
ouvertes, on les liste en fiches « non réclamées », et chacun peut réclamer sa fiche via un
e-mail de prospection → onboarding → la fiche existante est rattachée à son compte (jamais un
doublon).

**Principe directeur :** tout dans le monolithe (`@Scheduled` + services), **pas de
microservice** — c'est un batch périodique qui écrit dans `repairer_profiles`, rien à scaler à
part. MVP d'abord, le reste en différé.

---

## Ce qui existe déjà (à réutiliser)

| Brique | Où |
|---|---|
| `repairer_profiles.user_id` **nullable** (fiche sans compte) | `V51` |
| `RepairerOnboardingService` — search géo, findAll, verify/reject/KYB | backend |
| `SireneService` (lookup SIRENE, cache Redis) | backend |
| `GeocodingService` (Nominatim, User-Agent configuré, politique 1 req/s) | backend |
| `email_outbox` + `EmailOutboxDispatcher` (Resend, retry, DLQ) + templates Thymeleaf | backend |
| Pattern `@Scheduled` (`OrderScheduler`, `AccountPurgeScheduler`, …) | backend |
| Seed « échantillon annuaire CMA Île-de-France » (5 fiches sans compte) | `R__seed_demo_data.sql` |

---

## Phase 0 — Schéma & états · `V55` **[FAIT]**

- `RepairerStatus` : ajout de `UNCLAIMED` (importé, non vérifié, masqué de la recherche) et
  `SUSPENDED`. Les fiches annuaire curées (seed CMA) restent `VERIFIED` et visibles.
- `RepairerSource` : `SELF | SIRENE | CMA | OSM | MANUAL`.
- `repairer_profiles` gagne : `source`, `external_ref`, `imported_at`, `claim_token`,
  `claimed_at`.
- Index unique partiel `(source, external_ref)` — dédup d'import **par source** (un retoucheur
  peut être dans SIRENE *et* CMA avec des données différentes ; on dédup dans une source, le
  merge inter-sources est un problème distinct). Pas de contrainte unique brute sur `siret` :
  le seed en réutilise un pour les profils démo, et un artisan multi-boutiques peut légitimement
  répéter un SIRET.
- Index unique partiel sur `claim_token`.

## Phase 1 — Import annuaire **[FAIT : SIRENE ; OSM/CMA différés]**

```
service/directory/
  SireneDirectorySource      NAF 95.29A/B + départements ; filtre anti-bruit (mots-clés
                             retouche/couture/cordonnerie sur code large) ; etat_administratif
  RepairerDirectoryImportService.importFrom(source, criteria)
      - upsert par (source, external_ref) → jamais de doublon
      - coords de l'API, sinon secours GeocodingService
      - établissement fermé → ignoré (nouveau) / SUSPENDED (existant)
      - status = UNCLAIMED, source, external_ref, imported_at
  RepairerDirectoryImportScheduler   @Scheduled hebdo                   [différé]
  RepairerDirectoryPurgeScheduler    @Scheduled hebdo : purge des UNCLAIMED > 18 mois jamais
                                     réclamés ni recontactés depuis 6 mois (RGPD)
  OsmDirectorySource / CmaDirectorySource / RefashionDirectorySource   [différé]
```

Endpoint : `POST /api/admin/repairers/import` `{source, departments, nafCodes, maxPages}` (ADMIN,
manuel ; l'action est audit-loguée).

## Phase 2 — Réclamation de fiche **[FAIT]**

```
service/RepairerClaimService   (jeton lié à l'e-mail invité + expiration 30 j — V61)
  issueClaimToken(profileId[, email])  → UUID
  resolveToken(token)                  → RepairerClaimPreview
  claim(userEmail, token)              :
      - refuse si le compte a déjà un profil retoucheur
      - refuse si jeton expiré, ou si lié à une autre adresse que userEmail
      - profile.user = compte courant, claimed_at = now, status = PENDING, jeton effacé
      - PAS de nouvelle ligne
```

Endpoints :
- `GET  /v1/repairers/claim/{token}` — public, renvoie le preview prérempli
- `POST /api/repairers/claim` `{token}` — rôle REPAIRER, rattache le compte courant
- `POST /api/admin/repairers/{id}/claim-token` — ADMIN, (re)génère un token

Front : l'onboarding retoucheur accepte `?claim=<token>` → préremplit → à la soumission appelle
`/api/repairers/claim` au lieu de créer un profil vierge.

**Tests :** token à usage unique ; fiche déjà réclamée → 404 ; compte ayant déjà un profil →
409 ; `issueClaimToken` sur fiche avec compte → 409 ; le rattachement ne duplique pas.

## Phase 3 — Prospection e-mail + conformité **[FAIT — incl. tunnel : pixel d'ouverture, clic tracé, conversion, relances J+7/J+21, webhook bounces Resend]**

```
V56  →  repairer_prospect_outreach (profile, email, sent/opened/clicked/claimed/unsubscribed_at)
        email_suppression (email PK, reason UNSUBSCRIBE|BOUNCE|COMPLAINT|MANUAL)
V57  →  + token, contact_count, last_contacted_at (suivi de tunnel + cadence de relance)

service/RepairerProspectingService
  invite(profileId, email)   refuse si email ∈ email_suppression ; issueClaimToken ; trace
                             l'outreach (token) ; envoie via liens tracés
  followUp(outreachId)       relance (réutilise le token, contact_count++)
  recordOpen / recordClick   pose opened_at / clicked_at
  suppress(email, reason)    ajoute à email_suppression (désinscription, bounce, plainte)
RepairerClaimService.claim() pose outreach.claimed_at si le token correspond (conversion)
RepairerProspectingReminderScheduler  @Scheduled quotidien : 2e contact J+7, 3e J+21, stop
```

Endpoints (tous publics, GET-safe) :
- `POST /api/admin/repairers/{id}/invite {email}` (ADMIN)
- `GET /v1/prospecting/open/{id}` → GIF 1×1
- `GET /v1/prospecting/click/{id}` → 302 vers la landing de réclamation
- `GET /v1/prospecting/unsubscribe/{id}` → page HTML de confirmation
- `POST /api/resend/webhook` → bounces/plaintes Resend → suppression (pas de vérif de
  signature : un appel falsifié ne fait que sur-supprimer, sans risque)

Template e-mail : identité Lumiris, **source de la donnée**, finalité, lien de réclamation
(tracé), **lien de désinscription**, contact `privacy@lumiris.fr`, pixel d'ouverture.

Registre des traitements : §9 « Prospection retoucheurs » ajouté (intérêt légitime, B2B).

## Phase 4 — Console admin **[différé — MVP : 3 onglets + bouton inviter]**

- Section « Réseau retoucheurs », 3 onglets : **Fiches annuaire** (UNCLAIMED + VERIFIED sans
  compte) / **En vérification** (PENDING) / **Vérifiés**.
- `GET /api/admin/repairers?state=unclaimed|pending|verified&page=` (remplace `/all`).
- Bouton « Inviter » par fiche.
- Différé : import CSV, invitation en masse, entonnoir (envoyés → ouverts → réclamés →
  vérifiés).

## Phase 5 — Côté consommateur **[différé]**

- `searchNearby` inclut aussi les `UNCLAIMED` avec un flag `claimed: false`.
- Fiche non réclamée : CTA doux (« Pas encore sur Lumiris — on le contacte pour vous »), pas de
  flux devis/RDV.
- Tri : distance, note, réactivité ; pagination.

## Phase 6 — Ops **[partiel]**

- **[FAIT]** Anti-faux-avis : `repair_request_id` sur `repairer_reviews` (V58), endpoint
  `POST /api/repair-requests/{id}/review` réservé au client d'une intervention `COMPLETED`
  (un avis/demande), flag `verified`. L'ancien `POST /v1/repairers/{id}/reviews` anonyme reste
  (`@Deprecated`) jusqu'à migration du front mobile.
- **[FAIT]** Métrique de réactivité : `medianResponseHours` + `completedJobs` sur
  `RepairerPublicProfileResponse` (médiane `quote_submitted_at − created_at` en SQL).
- **[différé]** Taux d'acceptation : le modèle d'état actuel (refus de devis → `COMPLETED`
  direct, pas `REFUSED`) ne permet pas de distinguer « refusé » de « fait » — nécessite un
  statut dédié.
- **[différé]** Carte de couverture : zones avec demandes consommateur mais 0 retoucheur.

---

## Améliorer le réseau — liste priorisée

1. **États de fiche** (`UNCLAIMED / PENDING / VERIFIED / SUSPENDED`) + `source`, et un onglet
   admin par état. → **Phase 0 (fait) + Phase 4**
2. **Flux de réclamation** — c'est ça qui débloque la croissance. → **Phase 2 (fait)**
3. **Anti-faux-avis** → **FAIT** (V58, `POST /api/repair-requests/{id}/review`, flag `verified`).
4. **Dédup import** : unicité. → **Phase 0 (fait)** via `(source, external_ref)` plutôt que
   `siret` brut (voir la note Phase 0).
5. **Métriques Doctolib-like** → **délai de réponse FAIT** (`medianResponseHours`,
   `completedJobs`) ; taux d'acceptation différé (modèle d'état à revoir).
6. **Tri & pagination** sur `/v1/repairers/search` → **FAIT** : `sort`
   (`distance`|`rating`|`responsiveness`) + `page`/`size` ; résultats avec `averageRating`,
   `reviewCount`, `medianResponseHours` (LEFT JOIN agrégats avis + `repair_requests`).
7. **Carte de couverture** pour les ops. → **Phase 6** (agrégat des `repair_requests` sans
   retoucheur à proximité, ou des recherches consommateur infructueuses).
8. **Cycle devis → paiement** → **FAIT (MVP)** : `V59` (`stripe_payment_intent_id`, `paid_at`
   sur `repair_requests`), `POST /api/repair-requests/{id}/pay` → `PaymentIntent` du montant du
   devis encaissé sur le compte plateforme, webhook `payment_intent.succeeded` (order_type=repair)
   → `ACCEPTED`, remboursement automatique si le client annule avant démarrage. `acceptQuote`
   reste la voie sans paiement (Stripe non configuré). **Différé** : escrow + reversement au
   retoucheur (Transfer) — nécessite un compte Connect par retoucheur ; l'acompte partiel
   plutôt que le montant total.

---

## Escrow + reversement au retoucheur (Stripe Connect) — **différé, ~1 j**

Aujourd'hui `pay` encaisse sur le compte **plateforme** ; le reversement au retoucheur se fait
hors bande. Pour automatiser (comme la marketplace, `V35`) :

1. **`V61`** : `repairer_stripe_account` (user_id, stripe_account_id, charges_enabled,
   payouts_enabled) ; `repair_requests` gagne `stripe_transfer_id`, `net_cents`, `released_at`.
2. **`RepairerConnectService`** : `createOnboardingLink(repairerEmail)` → Stripe `AccountLink`
   (compte Express) ; `syncFromStripe(accountId)` branché sur le webhook `account.updated`
   existant (`StripeWebhookService`). Endpoint `POST /api/repairers/me/connect/link` (REPAIRER).
3. **`RepairRequestPaymentService`** : ajouter `transfer_group` au PaymentIntent (modèle
   separate charges & transfers, fonds retenus).
4. **`RepairRequestPayoutService.release(request)`** appelé à `complete` (clôture retoucheur) :
   `Transfer` vers le compte connecté de `quote_amount_cents − commission` (config
   `repairer.commission-rate`), pose `released_at` / `net_cents`.
5. **Annulation après reversement** : `TransferReversal` + `Refund` (voir `OrderRefundService`).
6. **Garde** : un retoucheur ne reçoit de demandes / de payout que `VERIFIED` **et**
   `charges_enabled`.

Alternative plus légère d'ici là : **acompte partiel** (ex. 30 %) au lieu du montant total.

## Risques & dépendances

| Risque | Mitigation |
|---|---|
| Clé API INSEE/SIRENE + quotas | secret `INSEE_API_KEY`, cache Redis en place |
| Nominatim : 1 req/s | throttle dans l'import |
| NAF larges → bruit | file de revue admin obligatoire avant `VERIFIED` |
| Délivrabilité cold email | SPF/DKIM/DMARC, warm-up, volume progressif |
| Légal | strictement B2B, adresses pro, opt-out honoré < 48 h, source citée dans chaque mail |
| Google Maps | **interdit** de scraper (CGU) — ne pas utiliser |

## Séquencement MVP (~1 à 1,5 semaine, 1 dev)

1. ~~`V55` + entités + `UNCLAIMED`~~ **fait**
2. ~~`SireneDirectorySource` + `RepairerDirectoryImportService` + `POST /api/admin/repairers/import`~~ **fait**
3. ~~`RepairerClaimService` + endpoints~~ **fait**
4. ~~`RepairerProspectingService` + `V56` + template + suppression + `/v1/prospecting/unsubscribe`~~ **fait**
5. ~~Registre des traitements à jour~~ **fait** (§9)
6. ~~Admin : onglets par état + boutons « Importer SIRENE » / « Inviter »~~ **fait**
   (`RealRepairerAccounts`) ; ~~avis vérifiés côté mobile~~ **fait** (`my-repairs`)
7. Front : préremplir l'onboarding retoucheur depuis `?token=` → `GET /v1/repairers/claim/{token}` puis `POST /api/repairers/claim` (`Lumiris-Front` — non fait, l'emplacement de l'onboarding retoucheur est encore en chantier côté front)
