import tailwindcss from '@tailwindcss/vite'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',

  ssr: true,

  // Pinned to 4000 to avoid port-sharing with a local Next.js app — cached browser
  // tabs can POST /__nextjs_original-stack-frames and crash vite-node otherwise.
  devServer: {
    port: 4000,
    host: 'localhost',
  },

  devtools: { enabled: true },

  modules: [
    '@pinia/nuxt',
    '@vueuse/nuxt',
    '@vee-validate/nuxt',
  ],

  // shadcn-vue's `ui/**/index.ts` exports would collide with the sibling `*.vue`
  // files if auto-scanned — we ignore the index.ts files and import explicitly.
  components: {
    dirs: [
      { path: '~/components', ignore: ['**/ui/**/index.ts'] },
    ],
  },

  vite: {
    plugins: [
      tailwindcss(),
    ],
  },

  css: [
    '@/assets/css/main.css',
  ],

  runtimeConfig: {
    // Server-only: used during SSR. In Docker this points at the internal backend
    // service name (e.g. http://backend:8080/api/v1). Falls back to the public URL.
    apiBaseUrlServer: process.env.NUXT_API_BASE_URL_SERVER || '',
    public: {
      apiBaseUrl: process.env.NUXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1',
    },
  },

  imports: {
    dirs: [
      'composables/**',
      'stores/**',
      'entities/**',
      'types/**',
    ],
  },

  typescript: {
    strict: true,
  },
})
