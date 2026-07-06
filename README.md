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
# retourne { token, user }
curl -s http://localhost:8080/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"artisan@lumiris.com","password":"artisan123"}'

make postman   # raccourci : login admin, réponse formatée
```

---

## 🔌 API principale

- **Auth** (`/api/auth`) — `POST login`, `POST register`, `GET me`
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
