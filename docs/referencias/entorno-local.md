# Entorno local

## Requisitos

- Java 21.
- Docker operativo.
- Make.

El backend usa Docker Compose para PostgreSQL local y Testcontainers para ejecutar `FlywayMigrationTest` sobre PostgreSQL real.

## Comandos habituales

Desde la raiz de `Turnero-api`:

```bash
make db-up
make db-logs
make run
make test
make db-down
```

`make db-up` inicia PostgreSQL local. `make test` ejecuta toda la suite, incluido el test de Flyway con Testcontainers; no requiere tener la base local levantada.

## Verificacion de Docker

Antes de ejecutar tests que usan Testcontainers, verificar que el daemon responde:

```bash
docker info
```

Si este comando falla, iniciar Docker Desktop o Colima antes de continuar.

## macOS con Colima

Colima expone el socket Docker fuera de la ruta estandar que Testcontainers detecta por defecto. El target `make test` detecta el contexto `colima` y configura automaticamente:

```text
DOCKER_HOST=<socket del contexto Colima>
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Para ejecutar Gradle directamente, usar:

```bash
DOCKER_HOST="$(docker context inspect "$(docker context show)" --format '{{ .Endpoints.docker.Host }}')" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test
```

`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` es necesario para que Ryuk, el contenedor auxiliar de Testcontainers, monte el socket interno de la VM de Colima.

## Diagnostico rapido

| Sintoma | Causa probable | Accion |
| --- | --- | --- |
| `docker info` no conecta | Docker/Colima no esta iniciado | Iniciar el runtime y volver a ejecutar el comando. |
| Testcontainers busca `/var/run/docker.sock` | Gradle no recibio el socket de Colima | Ejecutar `make test` o el comando directo de la seccion anterior. |
| Ryuk falla al montar `~/.colima/.../docker.sock` | Falta el override del socket interno | Usar `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. |
