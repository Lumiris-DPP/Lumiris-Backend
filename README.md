
# Lumiris Backend API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat&logo=openjdk)](https://openjdk.org)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg?style=flat&logo=docker)](https://www.docker.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791.svg?style=flat&logo=postgresql)](https://www.postgresql.org)

> Backend API of **LUMIRIS** — Digital Product Passport platform for French textile artisans. Built with Java 21, Spring Boot 3, PostgreSQL & DPP/ESPR compliance.

---

## 📖 Table of Contents

- [What is Lumiris?](#-what-is-lumiris)
- [Architecture Overview](#-architecture-overview)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Project Structure](#-project-structure)
- [Development Workflow](#-development-workflow)
- [Contributing](#-contributing)

---

## 🎯 What is Lumiris?

**Lumiris** is a Digital Product Passport (DPP) platform designed for French textile artisans, enabling full traceability and transparency of textile products in compliance with the **European ESPR regulation** (Ecodesign for Sustainable Products Regulation).

### What is a Digital Product Passport?

A **DPP** is a digital record attached to a physical product that contains all information about its lifecycle:

- **Origin**: Where the raw materials come from
- **Manufacturing**: Who made it, where, and how
- **Composition**: Materials, certifications, environmental impact
- **Repairability**: Instructions and spare parts availability
- **End of life**: Recycling and disposal guidelines

### Who uses Lumiris?

- **Textile artisans**: Create and manage their product passports
- **Consumers**: Scan a QR code to access full product traceability
- **Regulators**: Verify ESPR compliance for products sold in the EU

### Key Features:

- **DPP Management**: Create, update and publish product passports
- **ESPR Compliance**: Built to meet EU regulation requirements
- **QR Code Generation**: Each product gets a scannable passport
- **OAuth2 Authentication**: Secure login for artisans
- **AI Document Analysis**: Extract product data from PDF documents via Spring AI + OpenAI
- **Session Management**: Redis-backed persistent sessions
- **API Documentation**: Full Swagger / OpenAPI spec

---

## 🏗️ Architecture Overview

### Request Flow:

```
Client (Artisan Dashboard / Consumer Scan)
                ↓
    Spring Security (OAuth2 / Session)
                ↓
        REST Controller
                ↓
        Service Layer
                ↓
        ├→ PostgreSQL (JPA / Hibernate + Flyway)
        ├→ Redis (Session store)
        └→ OpenAI API (Spring AI — PDF & document analysis)
```

### Key Architectural Decisions:

1. **Spring Security + OAuth2**: Authentication via external providers (Google, etc.)
   - **Why?** No password management, secure by default for artisans

2. **Flyway Migrations**: Versioned database schema
   - **Why?** DPP data models evolve with ESPR regulation — migrations must be auditable

3. **Redis Sessions**: Persistent sessions across restarts
   - **Why?** Stateless app, sessions survive container restarts in production

4. **Spring AI + OpenAI**: PDF document reading and data extraction
   - **Why?** Artisans can upload existing product documents and auto-fill passport fields

5. **ESPR Compliance Layer**: Business rules enforcing regulation requirements
   - **Why?** The EU ESPR regulation mandates specific data fields and formats for DPPs

---

## 🛠️ Tech Stack

### Backend
- **Spring Boot 3.4.5**: Main framework
- **Java 21**: LTS version
- **Spring Security + OAuth2**: Authentication & authorization
- **Spring AI 1.0.0**: OpenAI integration & PDF document reader
- **Spring Validation**: Input validation

### Database
- **PostgreSQL 17**: Primary database
- **Flyway**: Schema migrations
- **Hibernate / JPA**: ORM

### Infrastructure
- **Redis 7**: Session storage
- **Docker + Docker Compose**: Containerization
- **pgAdmin 4**: Database management UI

### Development Tools
- **Spring DevTools**: Hot reload
- **Swagger / SpringDoc 2**: API documentation
- **Testcontainers**: Integration testing with real containers
- **Lombok**: Boilerplate reduction

---

## 🚀 Getting Started

### Prerequisites

- Docker Desktop ([Download](https://www.docker.com/products/docker-desktop))
- Java 21+
- Maven (or use `./mvnw`)

### Step 1: Clone the Repository

```bash
git clone <repo-url>
cd lumiris-backend
```

### Step 2: Configure Environment

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Database
SPRING_DATASOURCE_USERNAME=kader
SPRING_DATASOURCE_PASSWORD=

# Redis
SPRING_DATA_REDIS_HOST=localhost

# OpenAI (for PDF document analysis)
OPENAI_API_KEY=sk-your-key-here

# CORS (your frontend URL)
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

### Step 3: Start Development Environment

```bash
make dev
```

This will:
- Start PostgreSQL, Redis and pgAdmin in Docker
- Launch Spring Boot locally with **hot reload**
- Run Flyway migrations automatically

### Step 4: Verify Everything Works

| URL | Service |
|-----|---------|
| `http://localhost:8081/swagger-ui/index.html` | Swagger UI (dev + hot reload) |
| `http://localhost:8080/swagger-ui/index.html` | Swagger UI (prod Docker) |
| `http://localhost:5050` | pgAdmin (admin@lumiris.com / admin) |
| `http://localhost:8081/actuator/health` | Health check |

---

## 🔐 Seed Accounts

The database is pre-seeded with one account per role for local development:

| Role | Email | Password |
|------|-------|----------|
| `ADMIN` | `admin@lumiris.com` | `admin123` |
| `ARTISAN` | `artisan@lumiris.com` | `artisan123` |
| `CLIENT` | `client@lumiris.com` | `client123` |
| `REPAIRER` | `repairer@lumiris.com` | `repairer123` |

To get a JWT token:

```bash
curl -X POST http://localhost:8081/api/auth/sign-in \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lumiris.com","password":"admin123"}'
```

---

## 📁 Project Structure

```
lumiris-backend/
├── src/
│   ├── main/
│   │   ├── java/com/minoh/lumiris_backend/
│   │   │   ├── config/          # Security, CORS configuration
│   │   │   ├── controller/      # REST endpoints (DPP, artisan, product)
│   │   │   ├── service/         # Business logic & ESPR compliance rules
│   │   │   ├── entity/          # JPA entities (Product, Passport, Artisan...)
│   │   │   ├── repository/      # Spring Data repositories
│   │   │   └── dto/             # Data Transfer Objects
│   │   └── resources/
│   │       ├── application.yaml      # App configuration
│   │       └── db/migration/         # Flyway migrations (V1__*.sql)
│   └── test/                         # Tests (JUnit 5 + Testcontainers)
├── docker-compose.yaml               # Docker services
├── Dockerfile                        # Multi-stage production build
├── Makefile                          # Developer commands
├── .env                              # Local environment variables
└── .env.example                      # Environment template
```

---

## 💻 Development Workflow

### Common Commands

```bash
make help               # Show all available commands

# Docker
make dev                # Start full dev environment (hot reload)
make up                 # Build and start production containers
make down               # Stop all containers
make logs               # Follow app logs
make ps                 # Show running containers

# Database
make db-info            # Show Flyway migration status
make db-migrate         # Run pending migrations
make db-validate        # Validate migrations
make db-reset           # Reset database (clean + migrate)

# Testing
make test               # Run all tests
make test-unit          # Run unit tests only
make test-integration   # Run integration tests (Testcontainers)
make test-coverage      # Run tests with HTML coverage report

# Redis
make redis-cli          # Open Redis CLI
make redis-flush        # Flush all Redis data
make redis-keys         # Show all Redis keys

# Utilities
make ssh                # SSH into app container
make clean              # Remove containers + volumes + build artifacts
```

### Adding a New DPP Field (ESPR Compliance)

1. **Create a Flyway migration** in `src/main/resources/db/migration/`:

```sql
-- V2__add_espr_repairability_score.sql
ALTER TABLE product_passport
ADD COLUMN repairability_score DECIMAL(3,1),
ADD COLUMN repairability_index VARCHAR(10);
```

2. **Update the Entity**:

```java
@Entity
public class ProductPassport {
    @Column
    private BigDecimal repairabilityScore;

    @Column
    private String repairabilityIndex;
}
```

3. **Add validation** (ESPR requires score between 0 and 10):

```java
@DecimalMin("0.0") @DecimalMax("10.0")
private BigDecimal repairabilityScore;
```

---

## 🧪 Testing

```bash
make test               # Run all tests
make test-unit          # Unit tests only
make test-integration   # Integration tests (uses Testcontainers)
make test-coverage      # HTML report → target/site/jacoco/index.html
```

Integration tests use **Testcontainers** — a real PostgreSQL instance spins up automatically, no manual setup needed.

---

## 📚 API Documentation

- **Swagger UI** (dev): `http://localhost:8081/swagger-ui/index.html`
- **Swagger UI** (prod): `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `http://localhost:8081/v3/api-docs`

---

## 🤝 Contributing

1. **Create a feature branch**: `git checkout -b feature/your-feature`
2. **Make your changes**
3. **Run tests**: `make test`
4. **Commit** following [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   feat: add repairability score to product passport
   fix: correct Flyway migration order for ESPR fields
   docs: update DPP compliance requirements
   ```
5. **Push and open a Pull Request**

---

## 🚀 Quick Reference

| Task | Command |
|------|---------|
| Start dev (hot reload) | `make dev` |
| Start production | `make up` |
| Run tests | `make test` |
| View API docs | `http://localhost:8081/swagger-ui/index.html` |
| Open database UI | `http://localhost:5050` |
| SSH into container | `make ssh` |
| View logs | `make logs` |
| Reset database | `make db-reset` |
| Stop everything | `make down` |

---

## 📝 License

This project is proprietary and confidential. All rights reserved © Lumiris.
