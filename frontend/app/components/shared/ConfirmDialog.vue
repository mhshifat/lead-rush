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
      <DialogHeader>
        <DialogTitle>{{ state.title }}</DialogTitle>
        <DialogDescription v-if="state.description">
          {{ state.description }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
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
