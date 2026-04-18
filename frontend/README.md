# Lead Rush — Frontend

Nuxt 3 web app for Lead Rush. Server-side rendered, talks to the Spring Boot backend at `/api/v1`.

## Stack

- Nuxt 3 (SSR), Vue 3, TypeScript
- Tailwind CSS v4 + shadcn-vue (via Reka UI / Radix Vue)
- Pinia for client state, TanStack Vue Query for server state
- VeeValidate + Zod for forms
- STOMP.js + SockJS for WebSocket (notifications, live chat)

## Prerequisites

- Node 20+
- A running backend on http://localhost:8080 — run `./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"` from `backend/`, or `docker compose up` from the repo root

## Run

```bash
npm install
npm run dev
```

The dev server runs on http://localhost:4000 (port pinned in [`nuxt.config.ts`](nuxt.config.ts)).

## Configuration

Set in `.env` (not committed):

```
NUXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

When running inside Docker, `NUXT_API_BASE_URL_SERVER` is also set so SSR fetches use the compose internal hostname. See [`app/plugins/api.ts`](app/plugins/api.ts).

## Architecture notes

- **Entity pattern** — raw API shapes live in `types/api/*.dto.ts`. Components and composables only use interfaces from `entities/*/entity.ts`, with mappers in `entities/*/mapper.ts` doing the translation. If the backend renames a field, only the mapper changes.
- **SSR + optimistic UI** — data is prefetched server-side, then TanStack Query takes over on the client for cache, mutations, and optimistic updates with rollback on error.
- **Auth** — JWT tokens stored in cookies so SSR can read them on the first paint. See [`stores/auth.ts`](app/stores/auth.ts) and [`middleware/auth.ts`](app/middleware/auth.ts).

## Build for production

```bash
npm run build
node .output/server/index.mjs
```

Or via Docker:

```bash
docker build -t leadrush-frontend .
```
