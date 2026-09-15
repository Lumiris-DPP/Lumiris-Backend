# DPIA légère — Lumiris

> Analyse d'impact simplifiée (art. 35 RGPD). **À valider par le DPO.** Une DPIA complète n'est
> obligatoire que si un traitement est susceptible d'engendrer un risque élevé ; l'analyse ci-dessous
> conclut à ce stade que non, sous réserve des mesures listées.

Date : 2026-09-08 · Version : 0.1 (brouillon) · Auteur : _[à compléter]_

## 1. Description d'ensemble

Lumiris est une plateforme de passeports produits numériques (DPP) avec place de marché,
réseau de retouche et offre d'analytics pour artisans. Quatre types d'utilisateurs : artisan,
retoucheur, consommateur, administrateur.

Traitements couverts : voir `registre-des-traitements.md`.

## 2. Nécessité et proportionnalité

| Traitement | Nécessaire ? | Minimisation |
|---|---|---|
| Comptes | Oui — accès au service | Champs limités (e-mail, nom, rôle) |
| KYB | Oui — fraude / obligations plateforme | Documents non publics, accès admin restreint, durée bornée |
| Commandes / paiement | Oui — contrat | Paiement délégué à Stripe (pas de stockage carte) |
| Analytics passeports | Oui — valeur produit ATELIER+ | **Anonyme dès la collecte** — pas d'IP, pas d'ID utilisateur final |
| Télémétrie / Sentry | Oui — qualité de service | `send-default-pii: false` |

## 3. Risques identifiés

| # | Risque | Impact | Vraisemblance | Mesures |
|---|---|---|---|---|
| R1 | Accès non autorisé aux comptes (credential stuffing, brute force) | Élevé | Moyenne | bcrypt, JWT court + refresh révocable, **rate limiting Redis 100 req/min/IP**, WAF Cloudflare, challenge bot |
| R2 | Fuite de documents KYB (identité, justificatifs) | Élevé | Faible | Stockage objet privé, URLs signées, accès admin restreint, IP allow-list sur `admin.lumiris.fr` |
| R3 | Ré-identification via les analytics d'audience | Faible | Faible | Aucune donnée liante stockée ; agrégation en SQL ; pas d'export par événement |
| R4 | Exposition de données personnelles d'utilisateur final à l'artisan via le dashboard stats | Moyen | Faible | Métriques agrégées uniquement ; test d'acceptation dédié ; revue de code |
| R5 | Non-respect des délais de réponse aux droits (accès / effacement) | Moyen | Moyenne | Endpoints self-service `/me/export` (< 48 h) et `/me/delete` (< 30 j), purge automatisée |
| R6 | Perte de données (incident infra) | Élevé | Faible | PITR Postgres, snapshots quotidiens 30 j, snapshots R2, restore testé en staging, runbook — RPO < 1 h / RTO < 4 h |
| R7 | Transferts hors UE (Stripe, Resend, Sentry) | Moyen | Certaine | Clauses contractuelles types ; DPA signés ; minimisation des données transmises |
| R8 | Sous-traitant compromis | Moyen | Faible | Liste tenue à jour, DPA art. 28, revue périodique |

## 4. Mesures transverses

- Chiffrement en transit (HTTPS partout), secrets hors dépôt (SOPS côté infra).
- CI sécurité : gitleaks, OWASP dependency-check (blocage merge sur CVE critique), SpotBugs,
  Trivy image, SBOM signé à chaque release.
- Journalisation des accès admin _[à confirmer : rétention, revue]_.
- Bandeau cookies conforme CNIL sur les 4 apps (consentement préalable au dépôt non essentiel).
- Registre des traitements tenu à jour.

## 5. Avis et conclusion

**Conclusion provisoire :** pas de risque résiduel élevé sous réserve de la mise en œuvre
effective des mesures R1–R8. Une DPIA complète serait requise si un traitement de profilage
à effet significatif ou de données sensibles à grande échelle était introduit.

- Avis du DPO : _[à recueillir]_
- Position du responsable de traitement : _[à recueillir]_
- Date de revue prévue : _[+ 12 mois ou à tout changement majeur]_
