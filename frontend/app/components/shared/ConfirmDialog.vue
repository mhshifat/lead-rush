<script setup lang="ts">
import { Button } from '~/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '~/components/ui/dialog'

const confirm = useConfirm()
const state = confirm._state

// Treat ESC / backdrop click as "cancel"
function onUpdate(open: boolean) {
  if (!open && state.resolver) confirm._cancel()
  else state.open = open
}
</script>

<template>
  <Dialog :open="state.open" @update:open="onUpdate">
    <DialogContent class="max-w-md">
      <!--
        Suppress header border + footer border when there is no description,
        otherwise the two dividers collide and render as a pair of stacked lines.
      -->
      <DialogHeader
        :class="state.description ? '' : 'mb-3! pb-0! border-b-0!'"
        :style="state.description ? '' : 'border-bottom: 0 !important;'"
      >
        <DialogTitle>{{ state.title }}</DialogTitle>
        <DialogDescription v-if="state.description">
          {{ state.description }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter
        :class="state.description ? '' : 'mt-0! pt-0! border-t-0!'"
        :style="state.description ? '' : 'border-top: 0 !important;'"
      >
        <Button variant="outline" @click="confirm._cancel">
          {{ state.cancelLabel }}
        </Button>
        <Button
          :variant="state.variant === 'destructive' ? 'destructive' : 'default'"
          @click="confirm._confirm"
        >
          {{ state.confirmLabel }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
