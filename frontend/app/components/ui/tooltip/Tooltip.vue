<script setup lang="ts">
import type { TooltipRootEmits, TooltipRootProps } from "reka-ui"
import { reactiveOmit } from "@vueuse/core"
import { TooltipRoot, useForwardPropsEmits } from "reka-ui"

const props = defineProps<TooltipRootProps>()
const emits = defineEmits<TooltipRootEmits>()

const delegated = reactiveOmit(props)
const forwarded = useForwardPropsEmits(delegated, emits)
</script>

<template>
  <TooltipRoot v-bind="forwarded">
    <slot />
  </TooltipRoot>
</template>
