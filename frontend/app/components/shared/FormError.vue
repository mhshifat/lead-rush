<script setup lang="ts">
// Small helper: renders nothing when the message is empty, so you can sprinkle
//   <SharedFormError :message="errors.get('name')" />
// next to every field without having to v-if each one.
defineProps<{
  message?: string | null
  // Pass class="mt-2" or similar to tweak spacing at call sites.
  class?: string
}>()
</script>

<template>
  <p
    v-if="message"
    :class="['text-xs text-destructive', $attrs.class]"
  >
    {{ message }}
  </p>
</template>
