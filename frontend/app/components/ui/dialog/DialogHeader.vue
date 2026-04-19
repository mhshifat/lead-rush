<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { cn } from "@/lib/utils"

const props = defineProps<{ class?: HTMLAttributes["class"] }>()
</script>

<template>
  <div
    :class="
      cn(
        'flex flex-col gap-y-1 mb-5 pb-4',
        props.class,
      )
    "
    style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);"
  >
    <slot />
  </div>
</template>
