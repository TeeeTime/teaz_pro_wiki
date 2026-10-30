<script setup lang="ts">
import { Icon } from '@teeetime/teaz-pro-ui'
import { useAuth } from '@/auth'

defineProps<{ what?: string }>()
const auth = useAuth()
</script>

<template>
  <main class="empty-state">
    <Icon name="search" class="empty-state__icon" />
    <h1 class="empty-state__title">{{ what ?? 'Page' }} not found</h1>
    <p class="empty-state__text">
      It doesn't exist, was moved, or you don't have access to it.
      <template v-if="!auth.user.value">Signing in may help if it was shared with you.</template>
    </p>
    <div class="empty-state__actions">
      <RouterLink to="/" class="btn">Go to my wikis</RouterLink>
      <button v-if="!auth.user.value" type="button" class="btn btn--primary" @click="auth.login()">
        <Icon name="login" />
        Sign in
      </button>
    </div>
  </main>
</template>
