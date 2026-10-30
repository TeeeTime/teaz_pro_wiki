<script setup lang="ts">
import { inject, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { usePopover } from '@/popover'
import { nodePath, parentTitles } from '@/paths'
import { highlight } from '@/highlight'
import { WIKI } from '@/keys'
import type { SearchHit } from '@/types'

/*
 * Keyword search over the open wiki's titles and texts. Results drop down
 * while typing; arrow keys pick one, Enter opens it (or all results).
 */
const props = defineProps<{ wikiId: string }>()

const SHOWN = 8
const { tree } = inject(WIKI)!
const router = useRouter()
const root = ref<HTMLElement | null>(null)
const { open, show, close } = usePopover(root)

const query = ref('')
const hits = ref<SearchHit[]>([])
const loading = ref(false)
const selected = ref(-1)
let timer: ReturnType<typeof setTimeout> | undefined
let latest = 0

watch(query, (q) => {
  clearTimeout(timer)
  selected.value = -1
  if (!q.trim()) {
    hits.value = []
    close()
    return
  }
  timer = setTimeout(() => search(q), 200)
})
onBeforeUnmount(() => clearTimeout(timer))

async function search(q: string) {
  const request = ++latest
  loading.value = true
  try {
    const result = await api.get<SearchHit[]>(`/api/wikis/${props.wikiId}/search?q=${encodeURIComponent(q.trim())}`)
    if (request !== latest) return
    hits.value = result
    show()
  } catch {
    if (request === latest) hits.value = []
  } finally {
    if (request === latest) loading.value = false
  }
}

function openHit(hit: SearchHit) {
  close()
  query.value = ''
  router.push(nodePath(props.wikiId, hit.path))
}

function openAll() {
  if (!query.value.trim()) return
  close()
  router.push({ path: `/${props.wikiId}/-/search`, query: { q: query.value.trim() } })
}

function onKeydown(event: KeyboardEvent) {
  const shown = Math.min(hits.value.length, SHOWN)
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!open.value && hits.value.length) show()
    selected.value = Math.min(selected.value + 1, shown - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    selected.value = Math.max(selected.value - 1, -1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const hit = hits.value[selected.value]
    if (hit) openHit(hit)
    else openAll()
  }
}
</script>

<template>
  <div ref="root" class="search" role="search">
    <Icon name="search" class="search__icon" />
    <input
      v-model="query"
      type="search"
      class="field search__input"
      placeholder="Search this wiki"
      aria-label="Search this wiki"
      autocomplete="off"
      @focus="hits.length && show()"
      @keydown="onKeydown"
    />
    <div v-if="open" class="popover search__results">
      <p v-if="!hits.length" class="search__none">{{ loading ? 'Searching…' : 'Nothing found.' }}</p>
      <button
        v-for="(hit, index) in hits.slice(0, SHOWN)"
        :key="hit.id"
        type="button"
        class="search__hit"
        :class="{ 'search__hit--selected': index === selected }"
        @mouseenter="selected = index"
        @click="openHit(hit)"
      >
        <span class="search__title">
          <Icon :name="hit.kind === 'CATEGORY' ? 'folder' : 'file'" />
          <span>
            <template v-for="(part, i) in highlight(hit.title, query)" :key="i">
              <mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
            </template>
          </span>
        </span>
        <span v-if="parentTitles(tree, hit.id)" class="search__path">{{ parentTitles(tree, hit.id) }}</span>
        <span v-if="hit.snippet" class="search__snippet">
          <template v-for="(part, i) in highlight(hit.snippet, query)" :key="i">
            <mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
          </template>
        </span>
      </button>
      <button v-if="hits.length > SHOWN" type="button" class="menu-item search__all" @click="openAll">
        Show all {{ hits.length }} results
      </button>
    </div>
  </div>
</template>

<style scoped>
.search {
  position: relative;
}

.search__icon {
  position: absolute;
  top: 50%;
  left: 10px;
  width: 16px;
  height: 16px;
  color: var(--color-text-secondary);
  transform: translateY(-50%);
  pointer-events: none;
}

.search__input {
  padding-left: 34px;
}

.search__results {
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 70vh;
  overflow: auto;
}

.search__none {
  padding: 10px;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.search__hit {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  font: inherit;
  text-align: left;
  color: var(--color-text-primary);
  background: transparent;
  border: none;
  cursor: pointer;
}

.search__hit--selected {
  background: var(--color-surface-hover);
}

.search__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.875rem;
  font-weight: 500;
}

.search__title .icon {
  width: 15px;
  height: 15px;
  color: var(--color-text-secondary);
}

.search__path,
.search__snippet {
  padding-left: 23px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.search__snippet {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

mark {
  color: inherit;
  background: var(--color-content-yellow-bg);
}

.search__all {
  justify-content: center;
  color: var(--color-accent);
}
</style>
