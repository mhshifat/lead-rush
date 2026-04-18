<!--
  Minimal SVG sparkline — no chart library required.
  Renders an area + line over the provided numeric series.
-->
<script setup lang="ts">
const props = defineProps<{
  points: number[]
  height?: number
  color?: string
}>()

const height = computed(() => props.height ?? 80)
const color = computed(() => props.color ?? 'hsl(var(--primary))')
const width = 600  // viewBox width — SVG scales to container

const max = computed(() => Math.max(1, ...props.points))

const linePath = computed(() => {
  if (props.points.length === 0) return ''
  const step = props.points.length === 1 ? 0 : width / (props.points.length - 1)
  return props.points.map((v, i) => {
    const x = i * step
    const y = height.value - (v / max.value) * (height.value - 4) - 2
    return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
  }).join(' ')
})

const areaPath = computed(() => {
  if (props.points.length === 0) return ''
  return `${linePath.value} L ${width} ${height.value} L 0 ${height.value} Z`
})
</script>

<template>
  <svg
    :viewBox="`0 0 ${width} ${height}`"
    preserveAspectRatio="none"
    class="w-full"
    :style="{ height: height + 'px' }"
  >
    <path :d="areaPath" fill="currentColor" class="text-primary/20" />
    <path :d="linePath" fill="none" stroke="currentColor" stroke-width="2" class="text-primary" />
  </svg>
</template>
