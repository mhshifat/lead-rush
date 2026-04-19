<!--
  Public landing page. Anyone — logged in or not — sees this when visiting /.
  Logged-in users still see a "Go to dashboard" CTA in the nav (swapped in via
  the isLoggedIn computed below).

  Layout is disabled — we render our own thin nav.
-->
<script setup lang="ts">
definePageMeta({
  layout: false,
})

// Used to swap nav CTAs based on auth state without redirecting away.
const isLoggedIn = computed(() => !!useCookie('accessToken').value)

// ── SEO ──
const config = useRuntimeConfig()
const siteUrl = config.public.siteUrl || 'https://leadrush.com'

useSeoMeta({
  title: 'Lead Rush — All-in-one lead generation platform',
  description: 'Multi-channel sequences, built-in deliverability, CRM, landing pages, chat, and data enrichment in one workspace. Replace 5+ tools with Lead Rush.',
  ogTitle: 'Lead Rush — All-in-one lead generation platform',
  ogDescription: 'Multi-channel sequences, built-in deliverability, CRM, landing pages, chat, and data enrichment in one workspace.',
  ogUrl: siteUrl,
  ogImage: `${siteUrl}/og-default.png`,
  ogType: 'website',
  twitterCard: 'summary_large_image',
  twitterTitle: 'Lead Rush — All-in-one lead generation platform',
  twitterDescription: 'Multi-channel sequences, built-in deliverability, CRM, landing pages, chat, and data enrichment in one workspace.',
  twitterImage: `${siteUrl}/og-default.png`,
})

useHead({
  link: [{ rel: 'canonical', href: siteUrl }],
  script: [
    {
      type: 'application/ld+json',
      // SoftwareApplication schema gives Google a structured signal that this is a SaaS
      // product, not a blog or store. Helps with knowledge-panel + sitelinks eligibility.
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Lead Rush',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url: siteUrl,
        description: 'All-in-one lead generation platform — sequences, CRM, landing pages, chat, enrichment.',
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
        publisher: {
          '@type': 'Organization',
          name: 'Lead Rush',
          url: siteUrl,
          logo: `${siteUrl}/favicon.svg`,
        },
      }),
    },
  ],
})

const features = [
  {
    title: 'Multi-channel sequences',
    body: 'Email, LinkedIn, calls, and tasks in one flow. Conditional branches skip follow-ups when a lead already engaged.',
    icon: 'M4 6h16M4 12h16M4 18h10',
  },
  {
    title: 'Built-in deliverability',
    body: 'Per-mailbox rotation, warmup schedules, SPF/DKIM/DMARC checks, and bounce-rate alerts — before your domain gets burned.',
    icon: 'M3 10l1.5-6h15L21 10M3 10v10h18V10M3 10h18M9 14h6',
  },
  {
    title: 'Pipeline + contacts + scoring',
    body: 'A real CRM underneath the outreach engine. Kanban deals, custom lifecycle stages, rule-based lead scoring with an audit log.',
    icon: 'M4 4h6v6H4V4m10 0h6v6h-6V4M4 14h6v6H4v-6m10 0h6v6h-6v-6',
  },
  {
    title: 'Landing pages & forms',
    body: 'Build hosted pages with a block editor. Forms auto-create or match contacts and can auto-enroll them in a sequence.',
    icon: 'M3 4h18v4H3V4m0 8h12v8H3v-8m14 0h4v8h-4v-8',
  },
  {
    title: 'Data enrichment waterfall',
    body: 'Hunter, Dropcontact, Clearbit — tried in priority order until a field is resolved. Swap providers without touching business logic.',
    icon: 'M12 3v18M5 10l7-7 7 7M5 14l7 7 7-7',
  },
  {
    title: 'AI that writes your first draft',
    body: 'Personalize cold emails from a contact profile. Generate subject-line variants tuned to avoid spam filters. Uses your own Groq key.',
    icon: 'M12 2l3 7h7l-5.5 4 2 7-6.5-4.5L5.5 20l2-7L2 9h7l3-7z',
  },
]

const stats = [
  { label: 'Adapters', value: '8' },
  { label: 'Spring modules', value: '18' },
  { label: 'DB migrations', value: '14' },
  { label: 'Real-time', value: 'STOMP' },
]
</script>

<template>
  <div class="relative min-h-screen text-foreground overflow-x-hidden">
    <SharedAnimatedBackground variant="hero" />

    <!-- Nav -->
    <header class="relative z-10 mx-auto max-w-6xl px-6 py-5 flex items-center justify-between">
      <SharedAppLogo with-wordmark />
      <nav class="flex items-center gap-2">
        <template v-if="isLoggedIn">
          <NuxtLink
            to="/dashboard"
            class="px-3 py-1.5 text-sm rounded-md bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
          >Go to dashboard</NuxtLink>
        </template>
        <template v-else>
          <NuxtLink
            to="/auth/login"
            class="px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >Sign in</NuxtLink>
          <NuxtLink
            to="/auth/register"
            class="px-3 py-1.5 text-sm rounded-md bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
          >Get started</NuxtLink>
        </template>
      </nav>
    </header>

    <!-- Hero -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 pt-20 pb-28 text-center">
      <p class="enter-fade-up inline-flex items-center gap-2 rounded-full hairline px-3 py-1 text-xs text-muted-foreground">
        <span class="w-1.5 h-1.5 rounded-full bg-emerald-400" />
        Phase 2 complete — analytics, scoring, AI, team workspaces
      </p>
      <h1
        class="enter-fade-up text-gradient mt-6 text-5xl md:text-7xl font-semibold tracking-tight leading-[1.05]"
        style="animation-delay: 60ms"
      >
        Outreach engine.<br>CRM. Landing pages.<br>One platform.
      </h1>
      <p
        class="enter-fade-up mt-6 max-w-2xl mx-auto text-lg text-muted-foreground"
        style="animation-delay: 120ms"
      >
        Lead Rush replaces the stack of tools sales teams duct-tape together — sequences,
        enrichment, pipelines, forms, scoring, and deliverability, all sharing one contact graph.
      </p>
      <div
        class="enter-fade-up mt-9 flex items-center justify-center gap-3"
        style="animation-delay: 180ms"
      >
        <NuxtLink
          v-if="isLoggedIn"
          to="/dashboard"
          class="px-5 py-2.5 rounded-md bg-primary text-primary-foreground text-sm font-medium glow-primary hover:opacity-90 transition-all"
        >
          Open dashboard
        </NuxtLink>
        <NuxtLink
          v-else
          to="/auth/register"
          class="px-5 py-2.5 rounded-md bg-primary text-primary-foreground text-sm font-medium glow-primary hover:opacity-90 transition-all"
        >
          Start free
        </NuxtLink>
        <NuxtLink
          v-if="!isLoggedIn"
          to="/auth/login"
          class="px-5 py-2.5 rounded-md hairline text-sm font-medium hover:bg-white/5 transition-colors"
        >
          Sign in
        </NuxtLink>
      </div>

      <!-- Subtle stat strip -->
      <div
        class="enter-fade-up mt-16 grid grid-cols-2 md:grid-cols-4 gap-6 max-w-3xl mx-auto"
        style="animation-delay: 240ms"
      >
        <div v-for="s in stats" :key="s.label" class="text-center">
          <div class="text-2xl font-semibold tracking-tight">{{ s.value }}</div>
          <div class="text-xs text-muted-foreground uppercase tracking-wider mt-1">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- Feature grid -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 pb-28">
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="(f, idx) in features"
          :key="f.title"
          class="enter-fade-up glass rounded-xl p-6 hover:bg-white/5 transition-colors"
          :style="{ animationDelay: (60 * idx + 300) + 'ms' }"
        >
          <div class="flex items-center justify-center w-10 h-10 rounded-lg bg-primary/10 text-primary mb-4">
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path :d="f.icon" />
            </svg>
          </div>
          <h3 class="text-base font-semibold tracking-tight">{{ f.title }}</h3>
          <p class="mt-2 text-sm text-muted-foreground leading-relaxed">{{ f.body }}</p>
        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="relative z-10 mx-auto max-w-4xl px-6 pb-28 text-center">
      <div class="glass rounded-2xl p-10 md:p-14">
        <h2 class="text-3xl md:text-4xl font-semibold tracking-tight">
          {{ isLoggedIn ? 'Pick up where you left off.' : 'Ready when you are.' }}
        </h2>
        <p class="mt-3 text-muted-foreground">
          {{ isLoggedIn
            ? 'Your workspace is waiting. Jump back into the dashboard.'
            : 'Spin up a workspace in under a minute. Invite your team. Connect a mailbox.' }}
        </p>
        <div class="mt-8 flex items-center justify-center gap-3">
          <NuxtLink
            :to="isLoggedIn ? '/dashboard' : '/auth/register'"
            class="px-5 py-2.5 rounded-md bg-primary text-primary-foreground text-sm font-medium glow-primary hover:opacity-90 transition-all"
          >{{ isLoggedIn ? 'Open dashboard' : 'Create your workspace' }}</NuxtLink>
        </div>
      </div>
    </section>

    <footer class="relative z-10 mx-auto max-w-6xl px-6 py-8 text-xs text-muted-foreground flex items-center justify-between">
      <span>© Lead Rush</span>
      <span class="tracking-wide">Built with Spring Boot + Nuxt</span>
    </footer>
  </div>
</template>
