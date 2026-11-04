<script setup lang="ts">
import { ref, watch } from 'vue'
import { Avatar, Icon, avatarUrl } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { notifyError } from '@/feedback'
import type { McpAccessEntry, McpAccessPage } from '@/types'

/* The owner's view of what AI assistants read in this wiki through the MCP server. */
const props = defineProps<{ wikiId: string }>()

const page = ref(0)
const entries = ref<McpAccessEntry[]>([])
const hasMore = ref(false)
const loading = ref(true)

watch(
  [() => props.wikiId, page],
  async () => {
    loading.value = true
    try {
      const result = await api.get<McpAccessPage>(`/api/wikis/${props.wikiId}/mcp-access-log?page=${page.value}`)
      entries.value = result.entries
      hasMore.value = result.hasMore
    } catch (error) {
      notifyError(error)
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function when(value: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <section class="panel">
    <h2 class="panel__title">AI access</h2>
    <p class="muted panel__text">What AI assistants read here through the MCP server, on whose behalf. Kept for 30 days.</p>

    <p v-if="!loading && entries.length === 0 && page === 0" class="muted access__empty">
      No AI assistant has read this wiki in the last 30 days.
    </p>

    <ul v-else class="access">
      <li v-for="(entry, index) in entries" :key="index" class="access__row">
        <Avatar :src="avatarUrl(entry.user?.id, 64)" :name="entry.user?.name ?? 'Guest'" :size="32" />
        <div class="access__main">
          <p class="access__who">{{ entry.user?.name ?? 'Guest' }}</p>
          <p class="access__what">
            <Icon :name="entry.tool === 'search_wiki' ? 'search' : 'file'" />
            <span v-if="entry.tool === 'search_wiki'">Searched for <q>{{ entry.query }}</q></span>
            <span v-else-if="entry.tool === 'get_page'">Read <code class="access__path">{{ entry.query }}</code></span>
            <span v-else>{{ entry.tool }}</span>
          </p>
        </div>
        <time class="access__time muted" :datetime="entry.at">{{ when(entry.at) }}</time>
      </li>
    </ul>

    <div v-if="page > 0 || hasMore" class="access__paging">
      <button type="button" class="btn btn--small" :disabled="page === 0 || loading" @click="page--">
        <Icon name="chevron-left" />
        Newer
      </button>
      <button type="button" class="btn btn--small" :disabled="!hasMore || loading" @click="page++">
        Older
        <Icon name="chevron-right" />
      </button>
    </div>
  </section>
</template>

<style scoped>
/* WikiSettingsView's panel styles are scoped and stop at this component's root. */
.panel__title {
  margin-bottom: 14px;
  font-size: 1rem;
}

.panel__text {
  margin: -6px 0 14px;
  font-size: 0.875rem;
}

.access {
  margin: 0;
  padding: 0;
  list-style: none;
}

.access__empty {
  font-size: 0.875rem;
}

.access__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}

.access__main {
  flex: 1 1 200px;
  min-width: 0;
}

.access__who {
  font-size: 0.875rem;
  font-weight: 600;
}

.access__what {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 2px;
  font-size: 0.875rem;
  overflow-wrap: anywhere;
  color: var(--color-text-secondary);
}

.access__what .icon {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  margin-top: 3px;
}

.access__path {
  font-size: 0.8125rem;
}

.access__time {
  flex-shrink: 0;
  margin-left: 44px;
  font-size: 0.8125rem;
}

@media (min-width: 560px) {
  .access__time {
    margin-left: 0;
  }
}

.access__paging {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
