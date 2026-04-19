<template>
  <TooltipProvider>
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <!-- Toaster sits ABOVE dialog overlays (z-50 in shadcn). Without this a toast fired
         while a dialog is open stays hidden behind the backdrop and the user sees nothing. -->
    <Toaster
      position="bottom-right"
      :duration="5000"
      :toast-options="{ style: 'z-index: 9999' }"
    />
    <SharedConfirmDialog />
  </TooltipProvider>
</template>

<script setup lang="ts">
import { Toaster } from '~/components/ui/sonner'
import { TooltipProvider } from '~/components/ui/tooltip'
</script>
