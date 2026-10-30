<script setup lang="ts">
import { inject, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { WIKI } from '@/keys'
import { notifyError } from '@/feedback'
import { nodePath, parentTitles } from '@/paths'
import { highlight } from '@/highlight'
import type { SearchHit } from '@/types'

/* All search results for ?q=, when the dropdown's first few aren't enough. */
const { wiki, tree } = inject(WIKI)!
const route = useRoute()

const hits = ref<SearchHit[] | null>(null)
const query = ref('')

watch(
  () => route.query.q,
  async (q) => {
    query.value = typeof q === 'string' ? q : ''
    hits.value = null
    if (!query.value.trim()) {
      hits.value = []
      return
    }
    try {
      hits.value = await api.get<SearchHit[]>(
        `/api/wikis/${wiki.value.id}/search?q=${encodeURIComponent(query.value)}`,
      )
    } catch (error) {
      notifyError(error)
      hits.value = []
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="results">
    <h1 class="results__title">Search: {{ query }}</h1>
    <p v-if="hits === null" class="muted">Searching…</p>
    <p v-else-if="!hits.length" class="muted">Nothing found. Every word of the search has to occur in a page.</p>
    <ul v-else class="results__list">
      <li v-for="hit in hits" :key="hit.id" class="results__item">
        <RouterLink :to="nodePath(wiki.id, hit.path)" class="results__link">
          <Icon :name="hit.kind === 'CATEGORY' ? 'folder' : 'file'" />
          <template v-for="(part, i) in highlight(hit.title, query)" :key="i">
            <mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
          </template>
        </RouterLink>
        <p v-if="parentTitles(tree, hit.id)" class="results__path">{{ parentTitles(tree, hit.id) }}</p>
        <p v-if="hit.snippet" class="results__snippet">
          <template v-for="(part, i) in highlight(hit.snippet, query)" :key="i">
            <mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
          </template>
        </p>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.results {
  max-width: 860px;
  padding: 32px 32px 64px;
}

.results__title {
  margin-bottom: 20px;
  font-size: 1.375rem;
  overflow-wrap: anywhere;
}

.results__list {
  padding: 0;
  list-style: none;
}

.results__item {
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}

.results__link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  text-decoration: none;
}

.results__link .icon {
  width: 16px;
  height: 16px;
}

.results__path {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.results__snippet {
  margin-top: 4px;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

mark {
  color: inherit;
  background: var(--color-content-yellow-bg);
}

@media (max-width: 600px) {
  .results {
    padding: 24px 16px 48px;
  }
}
</style>
