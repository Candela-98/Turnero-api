-include .env
export

COMPOSE := $(shell docker compose version >/dev/null 2>&1 && echo "docker compose" || command -v docker-compose 2>/dev/null || true)

.PHONY: check-compose db-up db-down db-logs run test clean provision-local-owner

check-compose:
	@if [ -z "$(COMPOSE)" ]; then \
		echo "Docker Compose no está instalado. Instalá Docker Desktop o docker-compose."; \
		exit 1; \
	fi

db-up: check-compose
	$(COMPOSE) up -d

db-down: check-compose
	$(COMPOSE) down

db-logs: check-compose
	$(COMPOSE) logs -f postgres

run:
	./gradlew bootRun --args="--spring.profiles.active=$${SPRING_PROFILES_ACTIVE:-dev}"

test:
	@docker_context="$$(docker context show 2>/dev/null || true)"; \
	if [ "$$docker_context" = "colima" ] || [ "$${docker_context#colima-}" != "$$docker_context" ]; then \
		docker_host="$$(docker context inspect "$$docker_context" --format '{{ .Endpoints.docker.Host }}')"; \
		DOCKER_HOST="$$docker_host" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew test; \
	else \
		./gradlew test; \
	fi

provision-local-owner:
	bash ./scripts/provision-local-owner.sh

clean:
	./gradlew clean

# Complemento opcional del aprovisionamiento; simula salvo AGENDA_APPLY=1.
.PHONY: seed-local-appointments test-local-appointments
AGENDA_COUNT ?= 106
AGENDA_DAYS ?= 3
AGENDA_BASE_URL ?= http://localhost:8080
seed-local-appointments:
	python3 scripts/seed-local-appointments.py --business-id "$(BUSINESS_ID)" --count "$(AGENDA_COUNT)" --days "$(AGENDA_DAYS)" --base-url "$(AGENDA_BASE_URL)" $(if $(filter 1,$(AGENDA_CALENDAR)),--calendar-days,) $(if $(filter 1,$(AGENDA_APPLY)),--apply,)

test-local-appointments:
	PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_seed_local_appointments.py' -v
