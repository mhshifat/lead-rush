# Lead Rush

All-in-one lead generation platform: contacts & companies, email sequences, landing pages, live chat, CRM pipeline, enrichment, and AI-assisted outreach — delivered as a multi-tenant SaaS.

## Monorepo layout

| Path | Stack | What it is |
|---|---|---|
| [`backend/`](backend/) | Java 21, Spring Boot 3.5, PostgreSQL 16 + pgvector, Redis | REST API, WebSocket (STOMP), scheduled jobs, auth |
| [`frontend/`](frontend/) | Nuxt 3, Vue 3, Tailwind CSS v4, Pinia, TanStack Query | Web app (SSR) |
| [`extension/`](extension/) | WXT, React, TypeScript | Browser extension — LinkedIn profile import |
| [`widget/`](widget/) | Vite, TypeScript | Embeddable chat widget (`widget.js`) |

## Running the stack

One command brings up everything — Postgres, Redis, MailDev, backend, and frontend:

```bash
docker compose up --build
```

Services:

| What | URL |
|---|---|
| Frontend | http://localhost:3001 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MailDev inbox | http://localhost:1080 |
| Postgres | `localhost:5433` (with `pgvector`) |
| Redis | `localhost:6379` (30MB cap) |

### Native dev (faster iteration loop)

If you want hot-reload on backend/frontend changes, run the infra in Docker and the apps natively. Comment out the `backend:` and `frontend:` services in [docker-compose.yml](docker-compose.yml), then:

```bash
docker compose up -d              # postgres + redis + maildev

# Terminal 1 — backend
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"

# Terminal 2 — frontend
cd frontend
npm install
npm run dev                       # http://localhost:4000
```

## Configuration

Backend reads from `application.yml` + a per-profile override (`application-local.yml`, `application-docker.yml`). Secrets are never committed — `application-local.yml` is gitignored; production picks up env vars.

Key env vars: `DATABASE_URL`, `REDIS_URL`, `SMTP_HOST`, `JWT_SECRET`, `ENCRYPTION_KEY`, `GROQ_API_KEY` (optional).

## Further reading

- [`backend/README.md`](backend/README.md) — Spring Boot specifics, migrations, testing
- [`frontend/README.md`](frontend/README.md) — Nuxt setup, API wiring, component library
- [`extension/README.md`](extension/README.md) — installing the dev build in Chrome/Firefox
- [`widget/README.md`](widget/README.md) — embedding on a customer site
