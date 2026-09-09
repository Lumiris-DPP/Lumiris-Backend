# Lumiris Backend API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat&logo=openjdk)](https://openjdk.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791.svg?style=flat&logo=postgresql)](https://www.postgresql.org)
[![Stripe](https://img.shields.io/badge/Stripe-billing-635BFF.svg?style=flat&logo=stripe)](https://stripe.com)

> API backend de **LUMIRIS** — plateforme de Passeport Numérique Produit (DPP)
> pour les artisans textiles français. Java 21 · Spring Boot 4 · PostgreSQL 17 ·
> auth JWT · facturation Stripe · stockage MinIO · ancrage blockchain (Sepolia) ·
> conformité DPP/ESPR.

---

## 🚀 Démarrer

### Option A — toute la stack (recommandé)

Le backend fait partie de l'écosystème Lumiris (front + infra). Le plus simple
est de tout lancer depuis **Lumiris-Infra** :

```bash
cd ../Lumiris-Infra && make dev      # infra (Postgres…) + backend + front + stripe
```

Puis, **au premier lancement uniquement**, appliquer migrations + seeds (voir la
section **Base de données** ci-dessous). Détails : [`../Lumiris-Infra/README.md`](../Lumiris-Infra/README.md).

### Option B — backend seul

**Pré-requis :** Java 21, Docker (Postgres), Maven (`./mvnw` fourni).

```bash
cp .env.example .env          # puis renseigner les valeurs (DB, JWT_SECRET, Stripe…)

make start                    # Postgres via docker compose
# première fois : migrer + seed (cf. section Base de données)
make run                      # = ./mvnw spring-boot:run  → http://localhost:8080
```

Le backend écoute sur **`http://localhost:8080`**. Il attend Postgres sur
`localhost:5433` (cf. `SPRING_DATASOURCE_URL` dans `.env`).

---

## 🗄️ Base de données

Migrations **Flyway** dans `src/main/resources/db/`. ⚠️ Elles **ne s'exécutent
pas automatiquement** au démarrage (Spring Boot 4) : il faut les appliquer via
le plugin Maven. Les **seeds** (`V2__seed_users`, `V6__seed_artisan_profiles`)
vivent dans `db/seed`, **séparés** de `db/migration`.

```bash
set -a && . ./.env && set +a
# migrations + seeds (indispensable pour avoir des comptes de connexion) :
./mvnw flyway:migrate \
  -Dflyway.locations=filesystem:src/main/resources/db/migration,filesystem:src/main/resources/db/seed

./mvnw flyway:info            # statut des migrations
```

> Sans les deux `flyway.locations`, seul le schéma est créé (aucun utilisateur →
> connexion impossible). La commande est idempotente.

---

## 🔐 Authentification & comptes de démo

Auth **JWT** (email/mot de passe → jeton Bearer). Endpoints publics :
`/api/auth/**`, `/api/stripe/webhook`, `/swagger-ui/**`, `/v3/api-docs/**`,
`/actuator/**`. Tout le reste exige `Authorization: Bearer <token>`.

Un compte par rôle est seedé (mot de passe = `<rôle>123`) :

| Rôle       | Email                  | Mot de passe  |
| ---------- | ---------------------- | ------------- |
| `ADMIN`    | `admin@lumiris.com`    | `admin123`    |
| `ARTISAN`  | `artisan@lumiris.com`  | `artisan123`  |
| `CONSUMER` | `client@lumiris.com`   | `client123`   |
| `REPAIRER` | `repairer@lumiris.com` | `repairer123` |

```bash
# retourne { token, refreshToken, user }
curl -s http://localhost:8081/api/auth/sign-in \
  -H 'content-type: application/json' \
  -d '{"email":"artisan@lumiris.com","password":"artisan123"}'

make postman   # raccourci : login admin, réponse formatée
```

---

## 🔌 API principale

- **Auth** (`/api/auth`) — `POST sign-in`, `POST sign-up`, `POST refresh`, `GET me`
- **DPP** (`/api/dpp-forms`) — `POST` (**multipart** : part `data` JSON + fichiers `productPhoto`/documents), `GET` (liste), `GET /{id}`, `GET /{id}/iris_score`, `GET /{id}/verify` (ancrage blockchain)
- **Abonnement** (`/api/subscription`) — `GET` (état), `GET plans`, `POST setup-intent`, `POST confirm`, `POST change` (changement de plan), `POST portal`
- **Stripe** — `POST /api/stripe/webhook` (signé HMAC, non authentifié)
- **Public** (`/public/**`) — endpoints consommateur (scan / vérification DPP), non authentifiés

Docs & observabilité :

- **Swagger UI** : `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON** : `http://localhost:8080/v3/api-docs`
- **Health** : `http://localhost:8080/actuator/health` · **Métriques** : `/actuator/prometheus`

---

## 💳 Facturation Stripe

Paliers ATELIER en **mode test** : SetupIntent (carte uniquement) → confirmation →
abonnement, quotas de passeports, changement de plan in-app (`POST /api/subscription/change`,
proration), et portail client. Les webhooks (`/api/stripe/webhook`) synchronisent
l'état ; en local, le CLI Stripe les forwarde (lancé par `make dev`, ou manuellement —
voir [`../Lumiris-Infra/README.md#stripe-webhooks-locaux`](../Lumiris-Infra/README.md)).

Clés attendues dans `.env` : `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`,
`STRIPE_WEBHOOK_SECRET`, `STRIPE_PRODUCT_*` (ids produits).

---

## 🔔 Notifications (email + push)

Notifications transactionnelles envoyées via une file d'attente (`email_outbox`,
retry exponentiel + DLQ, `EmailOutboxDispatcher` toutes les 30s) — log d'envoi
consultable côté admin (`GET /api/admin/emails`).

**Email (Resend)** — `RESEND_API_KEY` : vide en local, `MailService` s'efface
silencieusement sans clé (aucun email ne part, mais l'outbox et les notifications
in-app fonctionnent normalement).

**Webhook Resend (bounces / plaintes)** — `RESEND_WEBHOOK_SECRET`. À configurer
en prod pour alimenter la liste de suppression e-mail (`email_suppression`) :

1. Resend → **Webhooks** → *Add Endpoint* : URL `https://<api>/api/resend/webhook`,
   événements `email.bounced` et `email.complained`.
2. Copier le *Signing Secret* (`whsec_…`) dans `RESEND_WEBHOOK_SECRET`.

Sans le secret, `POST /api/resend/webhook` accepte sans vérifier la signature (OK
en dev — un appel falsifié ne fait que sur-supprimer une adresse). Avec le secret,
la signature Svix est exigée.

**Push (Web Push / VAPID)** — identifie le serveur auprès des navigateurs pour
autoriser l'envoi de notifications push (standard [RFC 8292](https://datatracker.ietf.org/doc/html/rfc8292)).
Vide en local par défaut, même comportement : `PushNotificationService` s'efface
sans clé.

Génère une paire **une seule fois par environnement**, jamais à chaque déploiement :

```bash
npx web-push generate-vapid-keys
```

```env
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:contact@lumiris.app
```

> ⚠️ Ne jamais régénérer la paire une fois en prod : ça invalide instantanément tous
> les abonnements push existants des utilisateurs (ils devraient tous se réabonner).
> La clé publique est servie dynamiquement au front via `GET /api/push/vapid-public-key`
> — rien à synchroniser manuellement côté front.

---

## ⛓️ Blockchain Setup (Ethereum Sepolia)

Each DPP created is automatically anchored on the **Ethereum Sepolia testnet** via an Alchemy RPC node. The SHA-256 hash of the DPP data is stored in the transaction's calldata, making it externally verifiable and tamper-proof.

### Prerequisites

1. **Create an Alchemy account** at [alchemy.com](https://www.alchemy.com) and create a new app on **Ethereum Sepolia**. Copy the HTTPS RPC URL (looks like `https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY`).

2. **Create a MetaMask wallet** and switch to the **Sepolia testnet**. Export your private key (Account > Settings > Export private key) — MetaMask exports it with a `0x` prefix, **remove the `0x`** before using it.

3. **Get Sepolia ETH** (testnet tokens, free) at [cloud.google.com/application/web3/faucet/ethereum/sepolia](https://cloud.google.com/application/web3/faucet/ethereum/sepolia). You need a small amount to pay gas fees for each anchor transaction.

### Configuration

Add the two variables to your `.env` file:

```env
BLOCKCHAIN_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_ALCHEMY_KEY
BLOCKCHAIN_WALLET_PRIVATE_KEY=your_private_key_without_0x_prefix
```

> These variables are never committed — `.env` and `application-local.yaml` are gitignored.

### How it works

| Step | What happens |
|------|-------------|
| DPP created | SHA-256 hash computed from product fields, stored in DB with status `PENDING` |
| After DB commit | Async job sends a transaction to Sepolia with the hash as calldata |
| Receipt confirmed | Status updated to `ANCHORED`, transaction hash stored in DB |
| `GET /api/dpp-forms/{id}/verify` | Retrieves hash from blockchain and compares to recomputed hash |

### Verification response

```json
{
  "id": "...",
  "verified": true,
  "blockchainHash": "6b3ae768...",
  "recomputedHash": "6b3ae768...",
  "blockchainTxHash": "0xdeadbeef...",
  "anchorStatus": "ANCHORED",
  "message": null
}
```

- `verified: true` → the DPP data has not been tampered with since anchoring
- `anchorStatus` can be `PENDING`, `ANCHORED`, or `FAILED`

---

## 📦 Stockage (MinIO) & ⛓️ Blockchain (Ethereum Sepolia)

- **Stockage fichiers** : les photos produit et documents DPP sont uploadés sur
  **MinIO** (S3). En local, MinIO tourne dans la stack infra (`localhost:9000`) et
  le bucket est créé au démarrage. Défauts locaux dans `application.yaml` → aucune
  config requise pour `make dev`.
- **Ancrage blockchain** : à chaque création de DPP, le hash SHA-256 des données est
  ancré de façon **asynchrone** sur **Ethereum Sepolia** (calldata), vérifiable via
  `GET /api/dpp-forms/{id}/verify` (`PENDING` → `ANCHORED`/`FAILED`).

> **En local, aucune config blockchain n'est nécessaire** : `application.yaml` fournit
> une RPC Sepolia publique + une clé de test jetable, donc l'app démarre out-of-the-box.
> L'ancrage échoue silencieusement (clé non financée) — c'est attendu. Pour un ancrage
> réel, renseigne `BLOCKCHAIN_RPC_URL` (Alchemy) + `BLOCKCHAIN_WALLET_PRIVATE_KEY`
> (wallet Sepolia financé, sans le préfixe `0x`) dans `.env`.

Variables `.env` : `MINIO_*` (stockage) · `BLOCKCHAIN_RPC_URL` / `BLOCKCHAIN_WALLET_PRIVATE_KEY`.

---

## 🛠️ Tech stack

- **Spring Boot 4.0.6** / **Java 21** — web, validation, security, data-jpa, actuator, devtools
- **PostgreSQL 17** + **Flyway** (migrations versionnées)
- **JWT** via `jjwt` (`JwtService`, `JwtAuthFilter`) — sessions stateless, mots de passe BCrypt
- **Stripe** (`stripe-java`) — abonnements, quotas, webhooks
- **MinIO** (S3) — stockage des photos & documents DPP
- **web3j** — ancrage du hash DPP sur Ethereum Sepolia
- **springdoc-openapi** — Swagger UI / OpenAPI
- **Micrometer + Prometheus** — `/actuator/prometheus`
- **Testcontainers** + JUnit 5 — tests d'intégration sur un vrai Postgres

---

## 📁 Structure

```text
src/main/java/com/minoh/lumiris_backend/
├── config/          # SecurityConfig (JWT), CorsConfig, config Stripe
│   └── security/    # JwtAuthFilter, JwtService, @CurrentUserEmail
├── controller/      # AuthController, DppFormController, SubscriptionController, StripeWebhookController
├── service/         # logique métier
│   └── stripe/      # SubscriptionService, catalogue, sync, webhooks
├── entity/          # User, DppForm, DppMaterial, ArtisanProfile, UserSubscription…
├── repository/      # Spring Data JPA
├── domain/          # PlanTier, BillingCycle, statuts…
├── dto/{in,out}/    # DTOs requêtes / réponses
└── exception/       # GlobalExceptionHandler + exceptions métier
src/main/resources/
├── application.yaml
└── db/{migration,seed}/   # Flyway
```

---

## 💻 Commandes (Makefile)

```bash
make help        # liste des commandes

# Docker (Postgres)
make start       # up -d
make stop        # stop (garde les containers)
make down        # down
make fresh       # down -v + up -d  (⚠️ efface les volumes → base vide, re-seed nécessaire)
make logs        # logs Postgres

# App
make run                 # ./mvnw spring-boot:run  (hot reload via devtools)
make mvn <args>          # n'importe quelle commande Maven avec .env chargé
                         #   ex: make mvn flyway:info · make mvn clean package -DskipTests

# Aide-mémoire (affichent les commandes à lancer)
make maven · make flyway · make test · make info
```

---

## 🧪 Tests

```bash
./mvnw test                          # tous les tests (Testcontainers démarre un Postgres)
./mvnw test -Dtest=DppFormServiceTest
./mvnw test jacoco:report            # couverture → target/site/jacoco/index.html
```

---

## 🤝 Contribution

Branche de feature → tests verts → [Conventional Commits](https://www.conventionalcommits.org/)
(`feat(billing): …`, `fix(dpp): …`) → Pull Request.

---

© Lumiris — propriétaire et confidentiel.
