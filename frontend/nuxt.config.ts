import tailwindcss from '@tailwindcss/vite'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',

  ssr: true,

  app: {
    head: {
      title: 'Lead Rush',
      link: [
        // SVG favicon — modern browsers prefer this over .ico, scales to any DPR.
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'alternate icon', type: 'image/x-icon', href: '/favicon.ico' },
      ],
    },
  },

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
      // Cookie lifetimes in seconds. Must match (or exceed) the backend JWT
      // expiries or the user gets logged out before the token itself expires.
      // Defaults: 24h access / 30d refresh.
      accessTokenTtl: Number(process.env.NUXT_PUBLIC_ACCESS_TOKEN_TTL ?? 60 * 60 * 24),
      refreshTokenTtl: Number(process.env.NUXT_PUBLIC_REFRESH_TOKEN_TTL ?? 60 * 60 * 24 * 30),
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
