-include .env
export

.PHONY: db-up db-down db-logs run test clean

db-up:
	docker compose up -d

db-down:
	docker compose down

db-logs:
	docker compose logs -f postgres

run:
	./gradlew bootRun --args='--spring.profiles.active=$${SPRING_PROFILES_ACTIVE:-dev}'

test:
	@docker_context="$$(docker context show 2>/dev/null || true)"; \
	if [ "$$docker_context" = "colima" ] || [ "$${docker_context#colima-}" != "$$docker_context" ]; then \
		docker_host="$$(docker context inspect "$$docker_context" --format '{{ .Endpoints.docker.Host }}')"; \
		DOCKER_HOST="$$docker_host" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew test; \
	else \
		./gradlew test; \
	fi

clean:
	./gradlew clean
