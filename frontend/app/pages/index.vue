<!--
  Public landing page. Anyone — logged in or not — sees this when visiting /.
  Logged-in users still see a "Go to dashboard" CTA in the nav (swapped in via
  the isLoggedIn computed below).

  Structure (top to bottom):
    1. Thin nav
    2. Hero — headline + dual CTA + floating mockup composition
    3. Trust bar — integrations the platform talks to
    4. Problem framing — why a unified platform matters
    5. Outreach engine section — feature split with mockup
    6. CRM section — feature split with mockup (reversed)
    7. Browser extension section — the biggest recent differentiator
    8. Landing pages section
    9. Outcomes strip — 3 big benefits
    10. Who it's for
    11. Final CTA card
    12. Footer

  Layout is disabled — we render our own nav instead of the app chrome.
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
  description: 'Sequences, CRM, landing pages, enrichment, and a LinkedIn + Gmail browser extension — in one workspace. Replace 5+ tools with Lead Rush.',
  ogTitle: 'Lead Rush — All-in-one lead generation platform',
  ogDescription: 'Sequences, CRM, landing pages, enrichment, and a LinkedIn + Gmail extension — in one workspace.',
  ogUrl: siteUrl,
  ogImage: `${siteUrl}/og-default.png`,
  ogType: 'website',
  twitterCard: 'summary_large_image',
  twitterTitle: 'Lead Rush — All-in-one lead generation platform',
  twitterDescription: 'Sequences, CRM, landing pages, enrichment, and a LinkedIn + Gmail extension — in one workspace.',
  twitterImage: `${siteUrl}/og-default.png`,
})

useHead({
  link: [{ rel: 'canonical', href: siteUrl }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Lead Rush',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url: siteUrl,
        description: 'All-in-one lead generation platform — sequences, CRM, landing pages, enrichment, and a LinkedIn + Gmail browser extension.',
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

// ── Content ──
// Integrations the platform plays well with. Rendered as plain text chips so
// we don't need a logo file per partner — keeps the page dependency-free.
const integrations = [
  'LinkedIn', 'Sales Navigator', 'Gmail', 'Outlook', 'SMTP',
  'Stripe', 'Hunter.io', 'Dropcontact', 'Clearbit', 'Groq', 'Cloudinary',
]

// Problems the platform targets — the "why" before the "what".
const problems = [
  {
    title: 'Tool sprawl',
    body: 'Apollo for prospecting, Instantly for sequences, HubSpot for CRM, Clay for enrichment, Leadpages for forms. Five subscriptions, five logins, five data silos.',
    metric: '5+ tools',
  },
  {
    title: 'Data silos',
    body: 'Contact details live in one tool, the pipeline stage in another, replies in a third. No single source of truth for the person you\'re about to call.',
    metric: '0 source of truth',
  },
  {
    title: 'Stale signals',
    body: 'Your best prospect just moved to a new VP role. Nobody told you. You\'re still chasing the role they left two months ago.',
    metric: 'weeks behind',
  },
]

// Capabilities that live inside the browser extension — rendered as a grid
// with mini-mockup visuals next to each.
const extensionCapabilities = [
  {
    title: 'Import from LinkedIn',
    body: 'Any profile or Sales Nav lead. One click. Deep-scrape grabs the "about" section, experience, and skills into contact.metadata.',
  },
  {
    title: 'Gmail contact card',
    body: 'Open a thread — the panel shows the sender\'s score, stage, active sequences, and open deals. Not in Lead Rush? One-click add.',
  },
  {
    title: 'AI opener',
    body: 'Generate a LinkedIn connection note (≤300 chars) or short cold email, tuned to the profile you\'re viewing. Regenerate until it fits.',
  },
  {
    title: 'Team collision warnings',
    body: '"Sarah sent a LinkedIn message 3d ago." Before you dial, you see every recent teammate touch.',
  },
  {
    title: 'Job-change alerts',
    body: 'Re-scraping a known profile diffs the new title/company against the stored values. "Jane moved to TrendCo." Time to reach out.',
  },
  {
    title: 'Duplicate detection',
    body: 'Fuzzy name match catches leads captured earlier from another source. Merge the new LinkedIn URL into the existing contact — no siblings.',
  },
  {
    title: 'Bulk capture',
    body: 'On a LinkedIn search, reactor modal, or post comments — "Capture N visible profiles" imports them all with one click.',
  },
  {
    title: 'Saved-search alerts',
    body: 'Save a LinkedIn search. Next visit, new profiles are highlighted green. Your own browsing is the check — no polling, no spam.',
  },
  {
    title: 'Right-click capture',
    body: 'See a LinkedIn link on a website, Twitter thread, or Google result? Right-click → "Capture to Lead Rush". Contact created in the background.',
  },
]

// Outcome statements — positioned as the emotional payoff of the full platform.
const outcomes = [
  {
    metric: '5+ tools → 1',
    title: 'One subscription',
    body: 'Replace the Apollo + Instantly + HubSpot + Clay + Leadpages stack. One login, one bill, one contact graph.',
  },
  {
    metric: 'Zero',
    title: 'Double-work across teammates',
    body: 'Every action is visible. Collision warnings catch you before you dial a lead a teammate touched this morning.',
  },
  {
    metric: 'Always fresh',
    title: 'Contact data',
    body: 'Every re-visit to a LinkedIn profile triggers a diff. Job changes, company moves — flagged the moment you look.',
  },
]

// Audience cards — who gets the most value from the platform.
const audiences = [
  {
    role: 'Solo founder',
    pitch: 'Don\'t pay for five tools to do one job. Run outreach, CRM, and landing pages from a single workspace while you build.',
    features: ['Full platform on free tier', 'AI writes your drafts', 'No credit card for sign-up'],
  },
  {
    role: 'Sales team',
    pitch: 'Stop stepping on each other. Shared pipeline, visible activity, collision warnings across the whole team.',
    features: ['Team workspaces + roles', 'Collision warnings', 'Deal ownership + rotation'],
  },
  {
    role: 'Agency',
    pitch: 'Run unlimited client workspaces from one login. Full tenant isolation. White-label the landing pages.',
    features: ['Multi-workspace', 'Tenant-scoped API keys', 'Custom domains on pages'],
  },
]
</script>

<template>
  <div class="relative min-h-screen text-foreground overflow-x-hidden">
    <SharedAnimatedBackground variant="hero" />

    <!-- ─── Nav ─── -->
    <header class="relative z-10 mx-auto max-w-6xl px-6 py-5 flex items-center justify-between">
      <SharedAppLogo with-wordmark />
      <nav class="flex items-center gap-2">
        <a href="#features" class="hidden md:inline-block px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">Features</a>
        <a href="#extension" class="hidden md:inline-block px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">Extension</a>
        <a href="#audiences" class="hidden md:inline-block px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">Who it's for</a>
        <template v-if="isLoggedIn">
          <NuxtLink to="/dashboard" class="px-3 py-1.5 text-sm rounded-md bg-primary text-primary-foreground hover:opacity-90 transition-opacity">
            Go to dashboard
          </NuxtLink>
        </template>
        <template v-else>
          <NuxtLink to="/auth/login" class="px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">Sign in</NuxtLink>
          <NuxtLink to="/auth/register" class="px-3 py-1.5 text-sm rounded-md bg-primary text-primary-foreground hover:opacity-90 transition-opacity">
            Start free
          </NuxtLink>
        </template>
      </nav>
    </header>

    <!-- ─── Hero ─── -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 pt-20 pb-24">
      <div class="text-center">
        <p class="enter-fade-up inline-flex items-center gap-2 rounded-full hairline px-3 py-1 text-xs text-muted-foreground">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          Now shipping — LinkedIn + Gmail browser extension
        </p>
        <h1
          class="enter-fade-up text-gradient mt-7 text-5xl md:text-6xl lg:text-7xl font-semibold tracking-tight leading-[1.05] max-w-4xl mx-auto"
          style="animation-delay: 60ms"
        >
          The whole outreach stack.<br>
          In one window.
        </h1>
        <p
          class="enter-fade-up mt-7 max-w-2xl mx-auto text-lg md:text-xl text-muted-foreground leading-relaxed"
          style="animation-delay: 120ms"
        >
          Sequences, CRM, landing pages, and enrichment — with a browser sidekick that
          rides along on LinkedIn and Gmail. Replace five subscriptions with one workspace.
        </p>
        <div
          class="enter-fade-up mt-10 flex flex-col sm:flex-row items-center justify-center gap-3"
          style="animation-delay: 180ms"
        >
          <NuxtLink
            :to="isLoggedIn ? '/dashboard' : '/auth/register'"
            class="px-6 py-3 rounded-md bg-primary text-primary-foreground text-sm font-medium glow-primary hover:opacity-90 transition-all"
          >
            {{ isLoggedIn ? 'Open dashboard' : 'Start free — no card' }}
          </NuxtLink>
          <a
            href="#features"
            class="px-6 py-3 rounded-md hairline text-sm font-medium hover:bg-white/5 transition-colors inline-flex items-center gap-2"
          >
            See what's inside
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14M13 5l7 7-7 7" />
            </svg>
          </a>
        </div>
      </div>

      <!-- Hero mockup — composite of LinkedIn panel + contact card + pipeline chip. Pure CSS. -->
      <div
        class="enter-fade-up mt-20 relative max-w-5xl mx-auto"
        style="animation-delay: 260ms"
      >
        <div class="relative aspect-video rounded-2xl overflow-hidden hairline bg-[linear-gradient(135deg,hsl(240_8%_8%),hsl(240_10%_6%))] shadow-[0_30px_80px_-20px_rgb(0_0_0/0.5)]">
          <!-- Ambient glow -->
          <div class="absolute -top-20 -left-20 w-96 h-96 rounded-full bg-primary/20 blur-3xl" />
          <div class="absolute -bottom-20 -right-20 w-96 h-96 rounded-full bg-primary/15 blur-3xl" />

          <!-- Floating LinkedIn side panel mockup -->
          <div class="absolute top-6 right-6 w-72 rounded-xl glass p-4 shadow-2xl ring-1 ring-white/10">
            <div class="flex items-center gap-2 pb-3 border-b border-white/5">
              <div class="w-5 h-5 rounded bg-linear-to-br from-primary to-primary/40" />
              <span class="text-xs font-semibold">Lead Rush</span>
              <span class="ml-auto text-xs text-muted-foreground">linkedin.com</span>
            </div>
            <div class="mt-3 flex items-center gap-3">
              <div class="w-10 h-10 rounded-full bg-linear-to-br from-fuchsia-500/40 to-violet-500/40" />
              <div>
                <div class="text-sm font-semibold">Jane Doe</div>
                <div class="text-xs text-muted-foreground">VP Marketing · TrendCo</div>
              </div>
            </div>
            <div class="mt-3 grid grid-cols-3 gap-2 text-center">
              <div><div class="text-xs text-muted-foreground">Score</div><div class="text-sm font-semibold">84</div></div>
              <div><div class="text-xs text-muted-foreground">Stage</div><div class="text-sm font-semibold">Lead</div></div>
              <div><div class="text-xs text-muted-foreground">Touch</div><div class="text-sm font-semibold">2h ago</div></div>
            </div>
            <!-- Job-change banner inside the panel -->
            <div class="mt-3 rounded-md px-2.5 py-2 bg-sky-500/10 border border-sky-500/25">
              <div class="text-[10px] uppercase tracking-wider text-sky-300">✨ Recent change</div>
              <div class="text-xs mt-0.5">Moved to <strong>TrendCo</strong> <span class="text-muted-foreground">(was Acme) · 3d ago</span></div>
            </div>
            <div class="mt-3 grid grid-cols-2 gap-2">
              <button class="text-xs rounded-md py-1.5 hairline hover:bg-white/5">Add note</button>
              <button class="text-xs rounded-md py-1.5 bg-primary text-primary-foreground">Enroll</button>
            </div>
          </div>

          <!-- Pipeline kanban mockup -->
          <div class="absolute bottom-6 left-6 w-xl rounded-xl glass p-4 shadow-2xl ring-1 ring-white/10">
            <div class="text-xs font-semibold pb-2 mb-2 border-b border-white/5">Pipeline · Q2 Outbound</div>
            <div class="grid grid-cols-4 gap-2">
              <div v-for="stage in [
                { name: 'Discovery', count: 12, color: 'sky' },
                { name: 'Demo', count: 7, color: 'violet' },
                { name: 'Proposal', count: 4, color: 'fuchsia' },
                { name: 'Close', count: 2, color: 'emerald' },
              ]" :key="stage.name" class="space-y-1.5">
                <div class="flex items-center justify-between text-[10px]">
                  <span class="uppercase tracking-wider text-muted-foreground">{{ stage.name }}</span>
                  <span class="text-muted-foreground">{{ stage.count }}</span>
                </div>
                <div v-for="n in Math.min(stage.count, 2)" :key="n" class="rounded-md hairline p-2 bg-white/2">
                  <div class="h-1.5 w-16 rounded-full bg-white/10" />
                  <div class="h-1.5 w-10 rounded-full bg-white/5 mt-1.5" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ─── Trust / integrations ─── -->
    <section class="relative z-10 mx-auto max-w-5xl px-6 pb-24">
      <p class="text-center text-xs uppercase tracking-[0.2em] text-muted-foreground mb-6">
        Plays nice with your existing stack
      </p>
      <div class="flex flex-wrap items-center justify-center gap-x-8 gap-y-3">
        <span
          v-for="name in integrations"
          :key="name"
          class="text-sm text-muted-foreground/80 hover:text-foreground transition-colors"
        >{{ name }}</span>
      </div>
    </section>

    <!-- ─── Problem framing ─── -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="max-w-3xl">
        <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-4">The problem</p>
        <h2 class="text-4xl md:text-5xl font-semibold tracking-tight leading-tight">
          Your lead gen stack<br>is leaking money.
        </h2>
        <p class="mt-5 text-lg text-muted-foreground leading-relaxed">
          Each tool in the chain was built for one thing. None of them share a contact graph.
          You pay for the overlap, and your team pays for the context switches.
        </p>
      </div>

      <div class="mt-14 grid grid-cols-1 md:grid-cols-3 gap-4">
        <div
          v-for="(p, idx) in problems"
          :key="p.title"
          class="enter-fade-up glass rounded-2xl p-7 hover:bg-white/5 transition-colors"
          :style="{ animationDelay: (100 * idx) + 'ms' }"
        >
          <div class="text-3xl md:text-4xl font-semibold text-gradient leading-none">{{ p.metric }}</div>
          <h3 class="mt-5 text-lg font-semibold tracking-tight">{{ p.title }}</h3>
          <p class="mt-2 text-sm text-muted-foreground leading-relaxed">{{ p.body }}</p>
        </div>
      </div>
    </section>

    <!-- ─── Feature: Outreach engine ─── -->
    <section id="features" class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center">
        <div>
          <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-4">Outreach engine</p>
          <h2 class="text-4xl md:text-5xl font-semibold tracking-tight leading-tight">
            Sequences that<br>actually convert.
          </h2>
          <p class="mt-5 text-lg text-muted-foreground leading-relaxed">
            Email, LinkedIn, calls, and tasks in one flow. Conditional branches skip follow-ups
            when a lead already engaged. Per-mailbox rotation, warmup, and SPF/DKIM/DMARC
            monitoring — so your domain never ends up on a blocklist.
          </p>
          <ul class="mt-7 space-y-3">
            <li v-for="item in [
              'Multi-channel: email + LinkedIn + calls + manual tasks',
              'A/B variants on subject + body with traffic splits',
              'Per-mailbox warmup + daily send limits + rotation',
              'Bounce + complaint webhook processing',
              'Unsubscribe handling (RFC 8058)',
            ]" :key="item" class="flex items-start gap-3 text-sm">
              <svg class="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 13l4 4L19 7" />
              </svg>
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>

        <!-- Sequence builder mockup -->
        <div class="relative">
          <div class="absolute -inset-8 bg-linear-to-br from-primary/20 to-fuchsia-500/10 blur-3xl rounded-full" />
          <div class="relative rounded-2xl glass p-6 shadow-2xl">
            <div class="flex items-center justify-between pb-3 border-b border-white/5">
              <div class="text-sm font-semibold">Cold outbound · Q2</div>
              <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/25">Active</span>
            </div>
            <div class="mt-4 space-y-2.5">
              <div v-for="(step, idx) in [
                { type: 'Email', label: 'Intro — personalised opener', meta: 'Day 0 · AI draft' },
                { type: 'LinkedIn', label: 'Connect note (≤300 chars)', meta: 'Day 3' },
                { type: 'Email', label: 'Follow-up — value prop', meta: 'Day 7 · A/B' },
                { type: 'Call', label: 'Quick dial if replied', meta: 'Conditional' },
                { type: 'Email', label: 'Breakup — last ask', meta: 'Day 14' },
              ]" :key="idx" class="flex items-center gap-3 p-3 rounded-lg hairline bg-white/2">
                <div class="w-8 h-8 flex items-center justify-center rounded-md text-xs font-semibold"
                     :class="{
                       'bg-sky-500/15 text-sky-300': step.type === 'Email',
                       'bg-violet-500/15 text-violet-300': step.type === 'LinkedIn',
                       'bg-emerald-500/15 text-emerald-300': step.type === 'Call',
                     }">
                  {{ idx + 1 }}
                </div>
                <div class="flex-1">
                  <div class="text-sm font-medium">{{ step.label }}</div>
                  <div class="text-xs text-muted-foreground">{{ step.type }} · {{ step.meta }}</div>
                </div>
                <svg class="w-4 h-4 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ─── Feature: CRM (reversed layout) ─── -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center">
        <!-- Pipeline mockup (on the left this time) -->
        <div class="relative order-2 lg:order-1">
          <div class="absolute -inset-8 bg-linear-to-br from-violet-500/15 to-sky-500/15 blur-3xl rounded-full" />
          <div class="relative rounded-2xl glass p-6 shadow-2xl">
            <div class="flex items-center justify-between pb-3 border-b border-white/5">
              <div class="text-sm font-semibold">Enterprise pipeline</div>
              <div class="text-xs text-muted-foreground">$284K · 25 deals</div>
            </div>
            <div class="mt-4 grid grid-cols-4 gap-2">
              <div v-for="col in [
                { name: 'Discovery', count: 12, value: '$42K' },
                { name: 'Demo', count: 7, value: '$89K' },
                { name: 'Proposal', count: 4, value: '$113K' },
                { name: 'Close', count: 2, value: '$40K' },
              ]" :key="col.name" class="space-y-1.5">
                <div class="text-[10px] uppercase tracking-wider text-muted-foreground">{{ col.name }}</div>
                <div class="text-xs text-muted-foreground">{{ col.value }}</div>
                <div v-for="n in col.count > 2 ? 2 : col.count" :key="n" class="rounded-md p-2.5 hairline bg-white/3">
                  <div class="h-1.5 w-20 rounded-full bg-white/15" />
                  <div class="flex items-center gap-1 mt-2">
                    <div class="w-4 h-4 rounded-full bg-linear-to-br from-fuchsia-500/40 to-violet-500/40" />
                    <div class="h-1.5 w-12 rounded-full bg-white/5" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="order-1 lg:order-2">
          <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-4">CRM + scoring</p>
          <h2 class="text-4xl md:text-5xl font-semibold tracking-tight leading-tight">
            A real CRM,<br>not a feature.
          </h2>
          <p class="mt-5 text-lg text-muted-foreground leading-relaxed">
            Kanban pipelines with custom stages, deal ownership, and forecasting.
            Rule-based lead scoring with an audit log. Custom fields for the shape your business
            actually takes — not what a SaaS vendor decided you need.
          </p>
          <ul class="mt-7 space-y-3">
            <li v-for="item in [
              'Unlimited pipelines + drag-and-drop Kanban',
              'Custom lifecycle stages and win probabilities',
              'Lead scoring rules with an audit trail',
              'Activity timeline across every touch',
              'Custom fields on contacts, companies, deals',
            ]" :key="item" class="flex items-start gap-3 text-sm">
              <svg class="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7" /></svg>
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ─── Feature: Browser extension (big, full-width) ─── -->
    <section id="extension" class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="text-center max-w-3xl mx-auto">
        <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-4">Browser extension</p>
        <h2 class="text-4xl md:text-5xl font-semibold tracking-tight leading-tight">
          Works where<br>you already work.
        </h2>
        <p class="mt-5 text-lg text-muted-foreground leading-relaxed">
          A Chromium extension that rides along on LinkedIn, Sales Navigator, and Gmail.
          The contact graph travels with you — no tab-switching, no copy-paste, no stale data.
        </p>
      </div>

      <div class="mt-16 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="(cap, idx) in extensionCapabilities"
          :key="cap.title"
          class="enter-fade-up glass rounded-2xl p-6 hover:bg-white/5 transition-all hover:-translate-y-0.5"
          :style="{ animationDelay: (60 * idx) + 'ms' }"
        >
          <div class="flex items-center gap-2">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-primary" />
            <h3 class="text-base font-semibold tracking-tight">{{ cap.title }}</h3>
          </div>
          <p class="mt-3 text-sm text-muted-foreground leading-relaxed">{{ cap.body }}</p>
        </div>
      </div>
    </section>

    <!-- ─── Feature: Landing pages & enrichment (compact split) ─── -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Landing pages -->
        <div class="glass rounded-2xl p-8">
          <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-3">Landing pages</p>
          <h3 class="text-2xl md:text-3xl font-semibold tracking-tight leading-tight">
            Build, publish, convert —<br>without another tool.
          </h3>
          <p class="mt-4 text-muted-foreground leading-relaxed">
            Block editor with 19 block types. Custom domains + SSL. Forms auto-create or
            match contacts and can enroll them straight into a sequence.
          </p>
          <!-- Mini page mockup -->
          <div class="mt-6 rounded-lg hairline p-4 space-y-2 bg-linear-to-br from-white/3 to-transparent">
            <div class="h-2 w-32 rounded-full bg-white/20" />
            <div class="h-1.5 w-48 rounded-full bg-white/8" />
            <div class="h-1.5 w-40 rounded-full bg-white/8" />
            <div class="pt-2 grid grid-cols-3 gap-1.5">
              <div class="aspect-video rounded bg-white/5" />
              <div class="aspect-video rounded bg-white/5" />
              <div class="aspect-video rounded bg-white/5" />
            </div>
            <div class="pt-1 h-6 w-24 rounded bg-primary/30" />
          </div>
        </div>

        <!-- Enrichment -->
        <div class="glass rounded-2xl p-8">
          <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-3">Enrichment waterfall</p>
          <h3 class="text-2xl md:text-3xl font-semibold tracking-tight leading-tight">
            Every provider,<br>in one flow.
          </h3>
          <p class="mt-4 text-muted-foreground leading-relaxed">
            Hunter, Dropcontact, Clearbit, GitHub, People Data Labs, sitemap crawl — tried in
            priority order with confidence scoring. Paid providers skipped when a free tier
            already returned a VERIFIED email.
          </p>
          <!-- Mini waterfall mockup -->
          <div class="mt-6 space-y-1.5">
            <div v-for="(adapter, i) in [
              { name: 'GitHub', status: 'LIKELY', tone: 'sky' },
              { name: 'Hunter.io', status: 'VERIFIED', tone: 'emerald' },
              { name: 'Dropcontact', status: 'skipped', tone: 'muted' },
              { name: 'Clearbit', status: 'skipped', tone: 'muted' },
            ]" :key="i" class="flex items-center gap-3 text-xs">
              <div class="w-5 text-muted-foreground text-right">{{ i + 1 }}</div>
              <div class="flex-1 h-7 flex items-center px-3 rounded hairline bg-white/2">
                <span class="font-medium">{{ adapter.name }}</span>
                <span class="ml-auto"
                  :class="{
                    'text-sky-300': adapter.tone === 'sky',
                    'text-emerald-300': adapter.tone === 'emerald',
                    'text-muted-foreground line-through': adapter.tone === 'muted',
                  }"
                >{{ adapter.status }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ─── Outcomes strip ─── -->
    <section class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div
          v-for="(o, idx) in outcomes"
          :key="o.title"
          class="enter-fade-up text-center md:text-left"
          :style="{ animationDelay: (100 * idx) + 'ms' }"
        >
          <div class="text-4xl md:text-5xl font-semibold text-gradient leading-none">{{ o.metric }}</div>
          <h3 class="mt-4 text-xl font-semibold tracking-tight">{{ o.title }}</h3>
          <p class="mt-2 text-muted-foreground leading-relaxed">{{ o.body }}</p>
        </div>
      </div>
    </section>

    <!-- ─── Who it's for ─── -->
    <section id="audiences" class="relative z-10 mx-auto max-w-6xl px-6 py-24">
      <div class="text-center max-w-3xl mx-auto mb-14">
        <p class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-4">Built for</p>
        <h2 class="text-4xl md:text-5xl font-semibold tracking-tight leading-tight">
          Every team that<br>hates tool sprawl.
        </h2>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div
          v-for="(a, idx) in audiences"
          :key="a.role"
          class="enter-fade-up glass rounded-2xl p-7 hover:bg-white/5 transition-all"
          :style="{ animationDelay: (100 * idx) + 'ms' }"
        >
          <div class="text-sm uppercase tracking-[0.2em] text-primary/80 mb-3">{{ a.role }}</div>
          <p class="text-base leading-relaxed">{{ a.pitch }}</p>
          <ul class="mt-5 space-y-2.5 pt-5 border-t border-white/5">
            <li v-for="f in a.features" :key="f" class="flex items-start gap-2 text-sm text-muted-foreground">
              <svg class="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7" /></svg>
              <span>{{ f }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ─── Final CTA ─── -->
    <section class="relative z-10 mx-auto max-w-4xl px-6 pb-32">
      <div class="glass rounded-3xl p-10 md:p-16 text-center relative overflow-hidden">
        <div class="absolute -top-32 left-1/2 -translate-x-1/2 w-160 h-160 rounded-full bg-primary/10 blur-3xl" />
        <div class="relative">
          <h2 class="text-3xl md:text-5xl font-semibold tracking-tight leading-tight">
            {{ isLoggedIn ? 'Pick up where you left off.' : 'Start in under a minute.' }}
          </h2>
          <p class="mt-5 text-lg text-muted-foreground max-w-xl mx-auto">
            {{ isLoggedIn
              ? 'Your workspace is waiting. Jump back into the dashboard.'
              : 'Spin up a workspace. Connect a mailbox. Install the extension. Invite your team. All on the free tier.' }}
          </p>
          <div class="mt-9 flex flex-col sm:flex-row items-center justify-center gap-3">
            <NuxtLink
              :to="isLoggedIn ? '/dashboard' : '/auth/register'"
              class="px-6 py-3 rounded-md bg-primary text-primary-foreground text-sm font-medium glow-primary hover:opacity-90 transition-all"
            >
              {{ isLoggedIn ? 'Open dashboard' : 'Create your workspace' }}
            </NuxtLink>
            <NuxtLink
              v-if="!isLoggedIn"
              to="/auth/login"
              class="px-6 py-3 rounded-md hairline text-sm font-medium hover:bg-white/5 transition-colors"
            >
              Sign in
            </NuxtLink>
          </div>
          <p class="mt-6 text-xs text-muted-foreground">
            No credit card. No trial countdown. Upgrade when you outgrow the free tier.
          </p>
        </div>
      </div>
    </section>

    <!-- ─── Footer ─── -->
    <footer class="relative z-10 mx-auto max-w-6xl px-6 py-10 border-t border-white/5">
      <div class="flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-muted-foreground">
        <div class="flex items-center gap-2">
          <SharedAppLogo />
          <span>© {{ new Date().getFullYear() }} Lead Rush</span>
        </div>
        <div class="flex items-center gap-5">
          <a href="#features" class="hover:text-foreground transition-colors">Features</a>
          <a href="#extension" class="hover:text-foreground transition-colors">Extension</a>
          <NuxtLink to="/auth/login" class="hover:text-foreground transition-colors">Sign in</NuxtLink>
        </div>
      </div>
    </footer>
  </div>
</template>
