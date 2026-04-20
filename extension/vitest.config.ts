import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'

// Extension tests run against a synthetic DOM (happy-dom) so the scraper can
// query real selectors without us spinning up a browser. Tests live next to
// the code they exercise, under `lib/__tests__/` — keeps fixtures close.
export default defineConfig({
  test: {
    environment: 'happy-dom',
    include: ['lib/**/*.test.ts'],
    globals: true,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./', import.meta.url)),
    },
  },
})
