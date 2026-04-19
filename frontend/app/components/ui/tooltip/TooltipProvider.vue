<script setup lang="ts">
import type { TooltipProviderProps } from "reka-ui"
import { TooltipProvider } from "reka-ui"

// Wraps reka-ui's TooltipProvider. Mount ONCE near the app root (we put it in app.vue).
// The provider coordinates shared hover timing + escape behaviour across every Tooltip.
const props = withDefaults(defineProps<TooltipProviderProps>(), {
  delayDuration: 300,
  disableHoverableContent: false,
  skipDelayDuration: 300,
})
</script>

<template>
  <TooltipProvider v-bind="props">
    <slot />
  </TooltipProvider>
</template>
