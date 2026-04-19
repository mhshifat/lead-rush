<!--
  Auth Layout — login, register, forgot-password, activate-notice, invite/accept.

  Renders the animated hero background behind a centered glass card. Thin
  top nav so visitors can always step back out to the marketing page.
-->
<script setup lang="ts">
// Lock body scroll — main is the only scrollable region if the card overflows on short viewports.
onMounted(() => document.documentElement.classList.add('no-body-scroll'))
onBeforeUnmount(() => document.documentElement.classList.remove('no-body-scroll'))
</script>

<template>
  <div class="relative h-screen flex flex-col overflow-hidden">
    <SharedAnimatedBackground variant="hero" />

    <header class="relative z-10 shrink-0 mx-auto w-full max-w-6xl px-6 py-5 flex items-center justify-between">
      <NuxtLink to="/" class="flex items-center gap-2 group">
        <span class="inline-block w-6 h-6 rounded-md bg-linear-to-br from-indigo-400 to-purple-500 transition-transform group-hover:scale-105" />
        <span class="font-semibold tracking-tight">Lead Rush</span>
      </NuxtLink>
      <NuxtLink
        to="/"
        class="text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        ← Back
      </NuxtLink>
    </header>

    <main class="relative z-10 flex-1 flex items-center justify-center overflow-y-auto px-4 py-8">
      <div class="w-full max-w-md enter-fade-up">
        <slot />
      </div>
    </main>

    <footer class="relative z-10 shrink-0 px-6 py-4 text-center text-xs text-muted-foreground">
      © Lead Rush
    </footer>
  </div>
</template>
