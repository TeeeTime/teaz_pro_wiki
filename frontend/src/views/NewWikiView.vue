<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { useAuth } from '@/auth'
import { loadMyWikis } from '@/state'
import { notifyError } from '@/feedback'
import { wikiPath } from '@/paths'
import type { WikiDetail } from '@/types'

const auth = useAuth()
const router = useRouter()

const name = ref('')
const description = ref('')
const saving = ref(false)

async function create() {
  saving.value = true
  try {
    const wiki = await api.post<WikiDetail>('/api/wikis', { name: name.value, description: description.value })
    await loadMyWikis()
    router.push(wikiPath(wiki.id))
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="new-wiki">
    <h1 class="new-wiki__title">New wiki</h1>
    <template v-if="auth.user.value">
      <p class="muted">
        A new wiki is private. In its settings you can add a logo, share it with people or make it public.
      </p>
      <form class="new-wiki__form" @submit.prevent="create">
        <div class="form-row">
          <label class="label" for="wiki-name">Name</label>
          <input id="wiki-name" v-model="name" class="field" maxlength="80" required autocomplete="off" />
        </div>
        <div class="form-row">
          <label class="label" for="wiki-description">Description (optional)</label>
          <textarea id="wiki-description" v-model="description" class="field" maxlength="2000" />
          <p class="hint">Shown on the wiki's overview page and in link previews.</p>
        </div>
        <div class="new-wiki__actions">
          <RouterLink to="/" class="btn">Cancel</RouterLink>
          <button type="submit" class="btn btn--primary" :disabled="saving || !name.trim()">Create wiki</button>
        </div>
      </form>
    </template>
    <template v-else>
      <p class="muted">Sign in to create a wiki.</p>
      <button type="button" class="btn btn--primary new-wiki__login" @click="auth.login()">Sign in</button>
    </template>
  </main>
</template>

<style scoped>
.new-wiki {
  max-width: 560px;
  margin: 0 auto;
  padding: 40px 24px 64px;
}

.new-wiki__title {
  font-size: 1.5rem;
  margin-bottom: 8px;
}

.new-wiki__form {
  margin-top: 24px;
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.new-wiki__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.new-wiki__login {
  margin-top: 16px;
}
</style>
