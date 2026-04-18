<!--
  Horizontal "fill bar" for a funnel step. Width is calculated from value/max
  so bars across steps line up visually for easy drop-off scanning.
-->
<script setup lang="ts">
const props = defineProps<{
  value: number
  max: number
  label: string
  /** Tailwind bg class for the fill (e.g., "bg-primary"). */
  color?: string
  /** Optional secondary text shown after the value. */
  suffix?: string
}>()

const pct = computed(() => (props.max === 0 ? 0 : Math.max(0, Math.min(100, (props.value / props.max) * 100))))
const bgClass = computed(() => props.color ?? 'bg-primary')
</script>

<template>
  <div class="space-y-1">
    <div class="flex justify-between text-xs">
      <span class="text-muted-foreground">{{ label }}</span>
      <span class="font-mono">
        {{ value.toLocaleString() }}<template v-if="suffix"> {{ suffix }}</template>
      </span>
    </div>
    <div class="h-2 rounded-full bg-muted overflow-hidden">
      <div :class="bgClass" class="h-full transition-all" :style="{ width: pct + '%' }" />
    </div>
  </div>
</template>
