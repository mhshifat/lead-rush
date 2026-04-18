<!-- Fallback page; verification normally happens server-side with a redirect to login. -->
<script setup lang="ts">
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '~/components/ui/card'
import { Button } from '~/components/ui/button'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle class="text-2xl text-center">Email Verified</CardTitle>
      <CardDescription class="text-center">
        Your account has been activated. You can now sign in.
      </CardDescription>
    </CardHeader>
    <CardContent>
      <Button as-child class="w-full">
        <NuxtLink to="/auth/login?activated=true">
          Go to Sign In
        </NuxtLink>
      </Button>
    </CardContent>
  </Card>
</template>
