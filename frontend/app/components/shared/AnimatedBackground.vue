<!--
  Animated SVG background — Minimalist Maximalism.

  Three slow-drifting radial orbs + a faint vector grid + a grain overlay.
  All motion is CSS-only so it costs ~nothing. Respects `prefers-reduced-motion`.

  Variants:
    "hero"   — brighter, more saturated. For the landing + auth pages.
    "subtle" — muted. For app chrome where content is primary.
-->
<script setup lang="ts">
const props = withDefaults(defineProps<{
  variant?: 'hero' | 'subtle'
}>(), { variant: 'subtle' })

const orbOpacity = computed(() => props.variant === 'hero' ? 0.55 : 0.25)
const gridOpacity = computed(() => props.variant === 'hero' ? 0.08 : 0.05)
const grainOpacity = computed(() => props.variant === 'hero' ? 0.15 : 0.08)
</script>

<template>
  <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
    <!-- Gradient orbs (three blurred blobs drifting on independent cycles) -->
    <svg
      class="absolute inset-0 w-full h-full"
      preserveAspectRatio="xMidYMid slice"
      viewBox="0 0 1440 900"
      aria-hidden="true"
    >
      <defs>
        <radialGradient id="orb-a" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stop-color="hsl(243 80% 65%)" :stop-opacity="orbOpacity" />
          <stop offset="70%"  stop-color="hsl(243 80% 65%)" stop-opacity="0" />
        </radialGradient>
        <radialGradient id="orb-b" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stop-color="hsl(280 75% 60%)" :stop-opacity="orbOpacity" />
          <stop offset="70%"  stop-color="hsl(280 75% 60%)" stop-opacity="0" />
        </radialGradient>
        <radialGradient id="orb-c" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stop-color="hsl(210 80% 55%)" :stop-opacity="orbOpacity" />
          <stop offset="70%"  stop-color="hsl(210 80% 55%)" stop-opacity="0" />
        </radialGradient>

        <pattern id="grid" width="48" height="48" patternUnits="userSpaceOnUse">
          <path
            d="M 48 0 L 0 0 0 48"
            fill="none"
            :stroke="`hsl(0 0% 100% / ${gridOpacity})`"
            stroke-width="1"
          />
        </pattern>

        <filter id="soft-blur">
          <feGaussianBlur stdDeviation="60" />
        </filter>
      </defs>

      <!-- Grid (behind orbs, very faint) -->
      <rect width="100%" height="100%" fill="url(#grid)" />

      <!-- Mask so the grid fades toward the edges -->
      <rect
        width="100%" height="100%"
        fill="hsl(240 6% 6%)"
        style="mix-blend-mode: multiply; mask: radial-gradient(ellipse at center, transparent 20%, black 80%);"
      />

      <!-- Orbs — each animates on its own cycle via CSS classes -->
      <g filter="url(#soft-blur)">
        <circle class="orb orb-a" cx="260"  cy="180" r="260" fill="url(#orb-a)" />
        <circle class="orb orb-b" cx="1200" cy="260" r="320" fill="url(#orb-b)" />
        <circle class="orb orb-c" cx="760"  cy="760" r="280" fill="url(#orb-c)" />
      </g>
    </svg>

    <!-- Grain overlay — SVG feTurbulence baked into a data URI so it tiles cheaply -->
    <div
      class="absolute inset-0 mix-blend-overlay"
      :style="{ opacity: grainOpacity }"
      style='background-image: url("data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 200 200%22><filter id=%22n%22><feTurbulence type=%22fractalNoise%22 baseFrequency=%220.9%22 numOctaves=%222%22 stitchTiles=%22stitch%22/></filter><rect width=%22100%25%22 height=%22100%25%22 filter=%22url(%23n)%22 opacity=%220.7%22/></svg>"); background-size: 200px 200px;'
    />

    <!-- Top fade so foreground cards always read crisply -->
    <div
      class="absolute inset-0"
      style="background: linear-gradient(180deg, hsl(240 6% 6% / 0.0) 0%, hsl(240 6% 6% / 0.35) 45%, hsl(240 6% 6% / 0.6) 100%);"
    />
  </div>
</template>

<style scoped>
.orb {
  transform-origin: center;
  transform-box: fill-box;
  will-change: transform;
}
.orb-a { animation: drift-a 22s ease-in-out infinite; }
.orb-b { animation: drift-b 28s ease-in-out infinite; }
.orb-c { animation: drift-c 25s ease-in-out infinite; }

@media (prefers-reduced-motion: reduce) {
  .orb-a, .orb-b, .orb-c { animation: none; }
}
</style>
