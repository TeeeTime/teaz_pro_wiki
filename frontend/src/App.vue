<script setup lang="ts">
import { computed, onMounted, provide, ref } from 'vue'
import { ACCOUNT_URL, TopBar, avatarUrl, useTheme } from '@teeetime/teaz-pro-ui'
import type { TopBarMenuItem } from '@teeetime/teaz-pro-ui'
import { useAuth } from '@/auth'
import { loadMyWikis } from '@/state'
import { notifyError } from '@/feedback'
import Feedback from '@/components/Feedback.vue'
import WikiSwitcher from '@/components/WikiSwitcher.vue'
import type { WikiDetail } from '@/types'
import { CURRENT_WIKI } from '@/keys'

// Applies the persisted/system theme to <html data-theme> on first render.
useTheme()

const auth = useAuth()

// The open wiki (set by WikiLayout), shown in the switcher.
const currentWiki = ref<WikiDetail | null>(null)
provide(CURRENT_WIKI, currentWiki)

const menuItems = computed<TopBarMenuItem[]>(() => {
  const items: TopBarMenuItem[] = auth.user.value
    ? [
        { label: 'Account', icon: 'user' },
        { label: 'Sign out', icon: 'logout' },
      ]
    : [{ label: 'Sign in', icon: 'login' }]
  // Local dev: switch between the mock users (mockAuth.ts).
  for (const user of auth.devUsers ?? []) {
    if (user.name !== auth.user.value?.name) items.push({ label: `Switch to ${user.name}`, icon: 'user' })
  }
  return items
})

function onMenuSelect(item: TopBarMenuItem) {
  if (item.label === 'Sign out') auth.logout()
  else if (item.label === 'Account') window.location.href = ACCOUNT_URL
  else if (item.label === 'Sign in') auth.login()
  else {
    const user = auth.devUsers?.find((u) => item.label === `Switch to ${u.name}`)
    if (user) auth.switchUser?.(user.id)
  }
}

onMounted(() => loadMyWikis().catch(notifyError))
</script>

<template>
  <TopBar
    home-url="https://portal.teaz.pro"
    :user-name="auth.user.value?.name ?? 'Guest'"
    :avatar-url="avatarUrl(auth.user.value?.id, 64)"
    :menu-items="menuItems"
    @select="onMenuSelect"
  >
    <WikiSwitcher :current="currentWiki" />
  </TopBar>

  <RouterView />
  <Feedback />
</template>
