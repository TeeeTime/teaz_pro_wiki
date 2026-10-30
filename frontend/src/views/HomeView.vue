<script setup lang="ts">
import { watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { useAuth } from '@/auth'
import { lastWiki, myWikis, myWikisLoaded } from '@/state'
import { wikiPath } from '@/paths'

/*
 * There's no start page of its own: with wikis, open the last one (or the
 * first); without any, this explains what to do.
 */
const auth = useAuth()
const router = useRouter()

watchEffect(() => {
  if (!myWikisLoaded.value || myWikis.value.length === 0) return
  const last = lastWiki()
  const target = myWikis.value.find((w) => w.id === last) ?? myWikis.value[0]!
  router.replace(wikiPath(target.id))
})
</script>

<template>
  <main v-if="myWikisLoaded && myWikis.length === 0" class="empty-state">
    <Icon name="book" class="empty-state__icon" />
    <template v-if="auth.user.value">
      <h1 class="empty-state__title">No wikis yet</h1>
      <p class="empty-state__text">
        Create a wiki of your own, or open a link to a public wiki and subscribe to it. Wikis others share with you
        show up here too.
      </p>
      <div class="empty-state__actions">
        <RouterLink to="/-/new" class="btn btn--primary">
          <Icon name="plus" />
          Create a wiki
        </RouterLink>
      </div>
    </template>
    <template v-else>
      <h1 class="empty-state__title">Teaz Wiki</h1>
      <p class="empty-state__text">
        Sign in to see your wikis. Public wikis can be read without an account: open the link someone shared with
        you.
      </p>
      <div class="empty-state__actions">
        <button type="button" class="btn btn--primary" @click="auth.login()">
          <Icon name="login" />
          Sign in
        </button>
      </div>
    </template>
  </main>
</template>
