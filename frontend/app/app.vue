<template>
  <div>
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <Toaster position="bottom-right" :duration="5000" />
    <SharedConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { Toaster } from '~/components/ui/sonner'
</script>
