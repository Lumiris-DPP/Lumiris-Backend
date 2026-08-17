-include .env
ifneq ("$(wildcard .env.local)", "")
	-include .env.local
endif

DC  := docker compose
MVN := ./mvnw

# Tout ce que .env définit part dans l'environnement des sous-processus. Une allowlist
# manuelle laissait silencieusement tomber chaque nouvelle variable (MAIL_*, MARKETPLACE_*,
# SHIPPING_*…), et l'application repartait sur ses valeurs par défaut sans rien signaler.
# Une variable absente de .env reste indéfinie, donc non exportée : FLYWAY_LOCATIONS vide
# n'écrase pas le défaut du plugin flyway-maven.
export

.DEFAULT_GOAL := help
.PHONY: help start stop down fresh fresh-seed logs run mvn maven flyway test postman setup-stripe

help:
	@echo "Usage: make <command>"
	@echo ""
	@echo "  Docker"
	@echo "    start     Start containers in background"
	@echo "    stop      Stop containers without removing them"
	@echo "    down      Stop and remove containers"
	@echo "    fresh     DB reset: flyway clean + migrate (keeps containers)"
	@echo "    fresh-seed  DB clean, then run the app with demo seeds (db/seed)"
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

fresh: start
	@$(MVN) flyway:clean
	@$(MVN) flyway:migrate

fresh-seed: start
	@$(MVN) flyway:clean
	@FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed $(MAKE) run

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