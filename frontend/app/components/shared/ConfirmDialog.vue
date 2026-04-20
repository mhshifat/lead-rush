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
        Confirm dialogs have no body content between header and footer, so the
        default DialogHeader/DialogFooter borders bracket an empty gap and read
        as two stacked lines. Strip both borders — the title hierarchy + button
        affordances are enough visual structure for this dialog.
      -->
      <DialogHeader
        class="mb-3! pb-0! border-b-0!"
        style="border-bottom: 0 !important;"
      >
        <DialogTitle>{{ state.title }}</DialogTitle>
        <DialogDescription v-if="state.description">
          {{ state.description }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter
        class="mt-5! pt-0! border-t-0!"
        style="border-top: 0 !important;"
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
