<script setup lang="ts">
import type { CheckboxRootEmits, CheckboxRootProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { CheckboxIndicator, CheckboxRoot, useForwardPropsEmits } from "reka-ui"
import { Check, Minus } from "lucide-vue-next"
import { cn } from "@/lib/utils"

// Follows the reka-ui v2 API — modelValue / update:modelValue (like Switch).
// Supports the indeterminate state via modelValue="indeterminate".
const props = defineProps<CheckboxRootProps & { class?: HTMLAttributes["class"] }>()
const emits = defineEmits<CheckboxRootEmits>()

const delegatedProps = reactiveOmit(props, "class")
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <CheckboxRoot
    v-bind="forwarded"
    :class="cn(
      'peer h-4 w-4 shrink-0 rounded-[4px] border border-input bg-background shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=checked]:border-primary data-[state=checked]:text-primary-foreground data-[state=indeterminate]:bg-primary data-[state=indeterminate]:border-primary data-[state=indeterminate]:text-primary-foreground',
      props.class,
    )"
  >
    <CheckboxIndicator class="flex h-full w-full items-center justify-center">
      <Minus v-if="(forwarded as any).modelValue === 'indeterminate'" class="h-3 w-3" />
      <Check v-else class="h-3 w-3" />
    </CheckboxIndicator>
  </CheckboxRoot>
</template>
