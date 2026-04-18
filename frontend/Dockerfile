# ── Build stage ──
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
# --legacy-peer-deps: @nuxt/vite-builder is pinned to 4.3.1 (via overrides) to avoid
# the vite-node IPC crash in 4.4.2, which introduces a peer-dep mismatch with nuxt@4.4.2.
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build

# ── Runtime stage ──
FROM node:20-alpine
WORKDIR /app

COPY --from=build /app/.output ./.output

ENV HOST=0.0.0.0
ENV PORT=3000
ENV NODE_ENV=production

EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
