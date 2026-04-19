<script setup lang="ts">
import type { TooltipTriggerProps } from "reka-ui"
import { TooltipTrigger, useForwardProps } from "reka-ui"

const props = withDefaults(defineProps<TooltipTriggerProps>(), { asChild: true })
const forwarded = useForwardProps(props)
</script>

<template>
  <TooltipTrigger v-bind="forwarded">
    <slot />
  </TooltipTrigger>
</template>
