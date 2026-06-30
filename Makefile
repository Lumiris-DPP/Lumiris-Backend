include .env
ifneq ("$(wildcard .env.local)", "")
	include .env.local
endif

DC  := docker compose
MVN := ./mvnw

export SPRING_DATASOURCE_URL
export SPRING_DATASOURCE_USERNAME
export SPRING_DATASOURCE_PASSWORD
export CORS_ALLOWED_ORIGINS
export JWT_SECRET
export MINIO_ENDPOINT
export MINIO_ACCESS_KEY
export MINIO_SECRET_KEY
export MINIO_BUCKET
export SPRING_PROFILES_ACTIVE

.DEFAULT_GOAL := help
.PHONY: help start stop down fresh logs run mvn maven flyway test postman

help:
	@echo "Usage: make <command>"
	@echo ""
	@echo "  Docker"
	@echo "    start     Start containers in background"
	@echo "    stop      Stop containers without removing them"
	@echo "    down      Stop and remove containers"
	@echo "    fresh     Full reset: remove containers + volumes, then restart"
	@echo "    logs      Follow PostgreSQL logs"
	@echo ""
	@echo "  App"
	@echo "    run           Start the Spring Boot application"
	@echo "    mvn <args>    Run any Maven command with .env loaded"
	@echo "                  ex: make mvn spring-boot:run"
	@echo "                  ex: make mvn clean install"
	@echo "                  ex: make mvn flyway:info"
	@echo ""
	@echo "  Reference (displays commands to run manually)"
	@echo "    maven     Maven build and run commands"
	@echo "    flyway    Database migration commands"
	@echo "    test      Test commands"

## —— App —————————————————————————————————————————————————————

run:
	@$(MVN) spring-boot:run

mvn:
	@$(MVN) $(filter-out mvn, $(MAKECMDGOALS))

%:
	@:

## —— Docker ——————————————————————————————————————————————————

start:
	@$(DC) up -d

stop:
	@$(DC) stop

down:
	@$(DC) down

fresh:
	@$(DC) down -v
	@$(DC) up -d

logs:
	@$(DC) logs -f postgres

## —— Reference ———————————————————————————————————————————————

maven:
	@echo "Maven commands (mvn):"
	@echo ""
	@echo "  mvn spring-boot:run                Run the application locally"
	@echo "  mvn compile                        Compile source code"
	@echo "  mvn clean package -DskipTests      Build the JAR without running tests"
	@echo "  mvn clean                          Delete build artifacts (target/)"
	@echo "  mvn dependency:resolve             Download all declared dependencies"
	@echo "  mvn dependency:tree                Print the full dependency tree"

flyway:
	@echo "Flyway commands (mvn flyway:<command>):"
	@echo ""
	@echo "  mvn flyway:info                    Show migration status (applied, pending)"
	@echo "  mvn flyway:migrate                 Apply all pending migrations"
	@echo "  mvn flyway:validate                Check that applied migrations match scripts on disk"
	@echo "  mvn flyway:repair                  Repair the schema history after a failed migration"
	@echo "  mvn flyway:clean                   Drop all database objects — destroys all data"

test:
	@echo "Test commands (./mvnw):"
	@echo ""
	@echo "  mvn test                           Run all tests"
	@echo "  mvn test -Dgroups=unit             Run only tests tagged @Tag(\"unit\")"
	@echo "  mvn test -Dgroups=integration      Run only integration tests (requires Docker)"
	@echo "  mvn test -Dtest=MyClassTest        Run a single test class"
	@echo "  mvn test jacoco:report             Run tests and generate HTML coverage report"
	@echo "                                        Output: target/site/jacoco/index.html"

postman:
	@echo "Quick login as admin:"
	@curl -s -X POST http://localhost:8080/api/auth/login \
		-H "Content-Type: application/json" \
		-d '{"email":"admin@lumiris.com","password":"admin123"}' | python3 -m json.tool

## —— ℹ️  Information ————————————————————————————————————————
info: ## Show Java and Maven versions
	@echo "📋 System Information:"
	@java --version
	@$(MVN) --version