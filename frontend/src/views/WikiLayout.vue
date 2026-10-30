<script setup lang="ts">
import { computed, inject, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api, ApiError } from '@/api'
import { useAuth } from '@/auth'
import { CURRENT_WIKI, WIKI, type WikiContext } from '@/keys'
import { loadMyWikis, rememberWiki } from '@/state'
import { notifyError } from '@/feedback'
import type { TreeNode, WikiDetail } from '@/types'
import NodeTree from '@/components/NodeTree.vue'
import SearchBox from '@/components/SearchBox.vue'
import NotFoundView from '@/views/NotFoundView.vue'

/*
 * Frame of an open wiki: the bar with search and wiki actions, the sidebar
 * with the structure, and the page (child route). Loads the wiki and its
 * tree and hands them to the pages (WIKI).
 */
const props = defineProps<{ wikiId: string }>()

const auth = useAuth()
const route = useRoute()
const currentWiki = inject(CURRENT_WIKI)!

const wiki = ref<WikiDetail | null>(null)
const tree = ref<TreeNode[]>([])
const notFound = ref(false)
const sidebarOpen = ref(false)
const subscribing = ref(false)

async function reloadWiki() {
  wiki.value = await api.get<WikiDetail>(`/api/wikis/${props.wikiId}`)
  currentWiki.value = wiki.value
}

async function reloadTree() {
  tree.value = await api.get<TreeNode[]>(`/api/wikis/${props.wikiId}/tree`)
}

async function load() {
  notFound.value = false
  wiki.value = null
  try {
    await Promise.all([reloadWiki(), reloadTree()])
    rememberWiki(props.wikiId)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound.value = true
    else notifyError(error)
  }
}

watch(() => props.wikiId, load, { immediate: true })
onBeforeUnmount(() => {
  currentWiki.value = null
})

// Close the drawer (narrow screens) whenever a page opens.
watch(
  () => route.fullPath,
  () => {
    sidebarOpen.value = false
  },
)

const context: WikiContext = {
  wiki: wiki as WikiContext['wiki'],
  tree,
  reloadTree,
  reloadWiki,
  setTree: (next) => {
    tree.value = next
  },
}
provide(WIKI, context)

/** The node the URL points at, for highlighting it in the sidebar. */
const activeId = computed(() => {
  const path = route.params.path
  if (!Array.isArray(path)) return null
  let parentId: string | null = null
  let found: TreeNode | undefined
  for (const slug of path) {
    found = tree.value.find((n) => n.parentId === parentId && n.slug === slug)
    if (!found) return null
    parentId = found.id
  }
  return found?.id ?? null
})

async function toggleSubscription() {
  if (!wiki.value) return
  subscribing.value = true
  try {
    const path = `/api/wikis/${props.wikiId}/subscription`
    wiki.value = wiki.value.subscribed ? await api.delete<WikiDetail>(path) : await api.post<WikiDetail>(path)
    currentWiki.value = wiki.value
    await loadMyWikis()
  } catch (error) {
    notifyError(error)
  } finally {
    subscribing.value = false
  }
}

const isMember = computed(() => wiki.value && wiki.value.level !== 'READER')
</script>

<template>
  <NotFoundView v-if="notFound" what="Wiki" />
  <div v-else-if="wiki" class="wiki" :class="{ 'wiki--drawer-open': sidebarOpen }">
    <div class="wiki-bar">
      <button
        type="button"
        class="icon-btn wiki-bar__menu"
        aria-label="Show pages"
        :aria-expanded="sidebarOpen"
        @click="sidebarOpen = !sidebarOpen"
      >
        <Icon name="menu" />
      </button>
      <SearchBox :wiki-id="wiki.id" class="wiki-bar__search" />
      <div class="wiki-bar__actions">
        <span v-if="wiki.visibility === 'PRIVATE'" class="wiki-bar__badge" title="Only you and the people you added">
          <Icon name="lock" />
          <span class="wiki-bar__badge-text">Private</span>
        </span>
        <button
          v-if="wiki.visibility === 'PUBLIC' && !isMember && auth.user.value"
          type="button"
          class="btn btn--small"
          :class="{ 'btn--primary': !wiki.subscribed }"
          :disabled="subscribing"
          @click="toggleSubscription"
        >
          <Icon :name="wiki.subscribed ? 'check' : 'star'" />
          {{ wiki.subscribed ? 'Subscribed' : 'Subscribe' }}
        </button>
        <RouterLink
          v-if="wiki.level === 'OWNER'"
          :to="`/${wiki.id}/-/settings`"
          class="btn btn--small"
          aria-label="Wiki settings"
        >
          <Icon name="gear" />
          <span class="wiki-bar__label">Settings</span>
        </RouterLink>
      </div>
    </div>

    <div class="wiki__body">
      <div class="wiki__backdrop" @click="sidebarOpen = false" />
      <aside class="wiki__sidebar" aria-label="Pages">
        <NodeTree :active-id="activeId" />
      </aside>
      <main class="wiki__main">
        <RouterView :key="route.path" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.wiki {
  container-type: inline-size;
}

.wiki-bar {
  position: sticky;
  top: 56px;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding: 0 16px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.wiki-bar__menu {
  display: none;
}

.wiki-bar__search {
  flex: 1;
  max-width: 520px;
}

.wiki-bar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.wiki-bar__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.wiki-bar__badge .icon {
  width: 15px;
  height: 15px;
}

.wiki__body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: calc(100vh - 108px);
}

.wiki__sidebar {
  position: sticky;
  top: 108px;
  align-self: start;
  height: calc(100vh - 108px);
  overflow: auto;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.wiki__main {
  min-width: 0;
}

.wiki__backdrop {
  display: none;
}

/* Below ~900px the sidebar becomes a drawer behind the menu button. */
@container (max-width: 900px) {
  .wiki-bar__menu {
    display: inline-flex;
  }

  .wiki__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .wiki__sidebar {
    position: fixed;
    top: 108px;
    left: 0;
    z-index: 25;
    width: min(320px, 85vw);
    transform: translateX(-100%);
    transition: transform var(--transition-medium);
    box-shadow: 0 8px 24px var(--color-shadow-strong);
  }

  .wiki--drawer-open .wiki__sidebar {
    transform: none;
  }

  .wiki--drawer-open .wiki__backdrop {
    position: fixed;
    inset: 108px 0 0;
    z-index: 24;
    display: block;
    background: var(--color-shadow);
  }
}

@container (max-width: 600px) {
  .wiki-bar {
    gap: 6px;
    padding: 0 8px;
  }

  .wiki-bar__badge-text,
  .wiki-bar__label {
    display: none;
  }
}
</style>
