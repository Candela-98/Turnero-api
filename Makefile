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
	./gradlew test

clean:
	./gradlew clean