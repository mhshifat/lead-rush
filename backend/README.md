# Lead Rush — Backend

Spring Boot REST API and WebSocket server for the Lead Rush platform.

## Stack

- Java 21 (virtual threads enabled)
- Spring Boot 3.5 (Web MVC, Security, Data JPA, WebSocket, Mail, OAuth2 Client)
- PostgreSQL 16 + pgvector
- Redis 7 (cache, rate limiting, STOMP broker relay)
- Flyway for migrations, Lombok, MapStruct, jjwt, SpringDoc OpenAPI

## Prerequisites

- Java 21
- A running Postgres + Redis + SMTP server — the simplest way is `docker compose up -d` from the repo root

## Run

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

The `local` profile points at:
- Postgres on `localhost:5433`
- Redis on `localhost:6379`
- MailDev on `localhost:1025`

All configured in [`src/main/resources/application-local.yml`](src/main/resources/application-local.yml) (gitignored — copy from the example block in the main repo README).

Once running:
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html

## Profiles

| Profile | When | File |
|---|---|---|
| `local` | Local dev on the host | `application-local.yml` (gitignored) |
| `docker` | Running inside the `docker compose` stack | `application-docker.yml` |
| default | Prod — reads env vars | `application.yml` |

## External services

Services the backend connects to at runtime:

- **PostgreSQL** — primary DB; all tenant data
- **Redis** — cache, rate limits, STOMP message relay
- **SMTP** — outbound email (transactional + sequence sends)
- **IMAP** — polling connected mailboxes for replies
- **Groq** — LLM calls (AI email writer, chatbot, subject-line variants). Optional; unset = AI features disabled.
- **Hunter.io** — email enrichment. Optional.

## Database migrations

Flyway runs on boot. Migrations live in [`src/main/resources/db/migration/`](src/main/resources/db/migration/) and follow the `V<N>__description.sql` convention. Add new ones with the next sequential number — never edit an applied migration.

## Tests

```bash
./mvnw test
```

## Build a JAR

```bash
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Or build the Docker image used by `docker compose`:

```bash
docker build -t leadrush-backend .
```
