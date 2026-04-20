import tailwindcss from '@tailwindcss/vite'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',

  ssr: true,

  app: {
    head: {
      // Title template: pages set just their page name and the suffix is appended.
      // useHead({ title: 'Contacts' }) → "Contacts · Lead Rush"
      titleTemplate: '%s · Lead Rush',
      title: 'Lead Rush',
      htmlAttrs: { lang: 'en' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'theme-color', content: '#0a0a0f' },
        { name: 'description', content: 'All-in-one lead generation platform — contacts, sequences, landing pages, chat, and enrichment in one workspace.' },
        { name: 'application-name', content: 'Lead Rush' },
        // Open Graph defaults — pages can override per-page via useSeoMeta
        { property: 'og:site_name', content: 'Lead Rush' },
        { property: 'og:type', content: 'website' },
        { property: 'og:title', content: 'Lead Rush' },
        { property: 'og:description', content: 'All-in-one lead generation platform — contacts, sequences, landing pages, chat, and enrichment in one workspace.' },
        // Twitter card defaults
        { name: 'twitter:card', content: 'summary_large_image' },
        { name: 'twitter:title', content: 'Lead Rush' },
        { name: 'twitter:description', content: 'All-in-one lead generation platform.' },
      ],
      link: [
        // SVG favicon — modern browsers prefer this over .ico, scales to any DPR.
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        // Multi-size ICO for legacy browsers + the tab-bar icon some browsers
        // insist on fetching from /favicon.ico regardless of <link> priority.
        // Both files are generated from favicon.svg by scripts/generate-favicons.mjs.
        { rel: 'alternate icon', type: 'image/x-icon', href: '/favicon.ico' },
        // iOS/iPadOS home-screen icon — must be a raster PNG; SVG isn't rendered.
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
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
      // Public site URL — used for canonical links, OG tags, and sitemap.xml.
      // In dev defaults to localhost; in prod set NUXT_PUBLIC_SITE_URL=https://app.leadrush.com.
      siteUrl: process.env.NUXT_PUBLIC_SITE_URL || 'http://localhost:4000',
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
