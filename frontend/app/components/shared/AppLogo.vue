<!--
  Lead Rush brand mark — a stacked upward chevron inside a gradient rounded square.
  Reads as "leads rushing up" and works at every size from 16px favicon to 96px hero.

  Use:
    <SharedAppLogo />              — default 24px square, no wordmark
    <SharedAppLogo :size="32" />   — 32px square
    <SharedAppLogo with-wordmark /> — square + "Lead Rush" text alongside
-->
<script setup lang="ts">
withDefaults(defineProps<{
  size?: number
  withWordmark?: boolean
  wordmarkClass?: string
}>(), {
  size: 24,
  withWordmark: false,
  wordmarkClass: 'font-semibold tracking-tight',
})
</script>

<template>
  <span class="inline-flex items-center gap-2">
    <span
      :style="{ width: `${size}px`, height: `${size}px` }"
      class="relative inline-flex items-center justify-center rounded-md bg-linear-to-br from-indigo-400 to-purple-500 shadow-[0_4px_14px_-4px] shadow-indigo-500/40"
    >
      <!--
        Two stacked chevrons pointing up — the lower one half-opaque so it reads
        as a motion trail. Strokes scale with the square via viewBox.
      -->
      <svg
        viewBox="0 0 24 24"
        fill="none"
        :style="{ width: `${Math.round(size * 0.625)}px`, height: `${Math.round(size * 0.625)}px` }"
        aria-hidden="true"
      >
        <path
          d="M5 17.5 L12 8 L19 17.5"
          stroke="white"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M8 14.5 L12 11.5 L16 14.5"
          stroke="white"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          opacity="0.45"
        />
      </svg>
    </span>
    <span v-if="withWordmark" :class="wordmarkClass">Lead Rush</span>
  </span>
</template>
