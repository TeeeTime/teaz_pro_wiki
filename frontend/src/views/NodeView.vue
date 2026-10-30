<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api, ApiError } from '@/api'
import { WIKI } from '@/keys'
import { confirmAction, notify, notifyError } from '@/feedback'
import { ancestry, children, level, nodePath, slugPath, wikiPath } from '@/paths'
import { canEdit as canEditLevel, type NodeDetail, type NodeKind } from '@/types'
import ContentView from '@/editor/ContentView.vue'
import WikiEditor from '@/editor/WikiEditor.vue'
import { EMPTY_DOC } from '@/editor/extensions'
import CommentSection from '@/components/CommentSection.vue'
import NameDialog from '@/components/NameDialog.vue'
import NotFoundView from '@/views/NotFoundView.vue'

/*
 * A category or page: reading (with comments), editing (?edit) and creating
 * (/-/new?parent=…). The editor only opens through the Edit button or for a
 * new page, so reading can't change anything.
 */
const { wiki, tree, reloadTree } = inject(WIKI)!
const route = useRoute()
const router = useRouter()

const node = ref<NodeDetail | null>(null)
const notFound = ref(false)
const title = ref('')
const dirty = ref(false)
const saving = ref(false)
const editor = ref<InstanceType<typeof WikiEditor> | null>(null)
const newCategory = ref(false)

const isNew = computed(() => route.name === 'new-node')
const editing = computed(() => isNew.value || route.query.edit !== undefined)
const canEdit = computed(() => canEditLevel(wiki.value.level))

const newParentId = computed(() => (typeof route.query.parent === 'string' ? route.query.parent : null))
const newKind = computed<NodeKind>(() => (route.query.kind === 'CATEGORY' ? 'CATEGORY' : 'PAGE'))
const newParent = computed(() => tree.value.find((n) => n.id === newParentId.value) ?? null)

const crumbs = computed(() => {
  const id = isNew.value ? newParentId.value : node.value?.id
  if (!id) return []
  const chain = ancestry(tree.value, id)
  return isNew.value ? chain : chain.slice(0, -1)
})

const items = computed(() => (node.value?.kind === 'CATEGORY' ? children(tree.value, node.value.id) : []))
const nodeLevel = computed(() => (node.value ? level(tree.value, node.value.id) : 0))
const isEmpty = computed(() => !node.value || isEmptyDoc(node.value.content))

function isEmptyDoc(content: string): boolean {
  try {
    const doc = JSON.parse(content)
    const blocks = doc.content ?? []
    return blocks.length === 0 || (blocks.length === 1 && blocks[0].type === 'paragraph' && !blocks[0].content?.length)
  } catch {
    return true
  }
}

async function load() {
  if (isNew.value) {
    node.value = null
    title.value = ''
    dirty.value = false
    if (!canEdit.value) notFound.value = true
    return
  }
  const path = route.params.path
  const slugs = Array.isArray(path) ? path : [path]
  if (slugs.length > 3) {
    notFound.value = true
    return
  }
  try {
    node.value = await api.get<NodeDetail>(
      `/api/wikis/${wiki.value.id}/nodes/by-path?path=${encodeURIComponent(slugs.join('/'))}`,
    )
    title.value = node.value.title
    document.title = `${node.value.title} · ${wiki.value.name}`
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound.value = true
    else notifyError(error)
  }
}

load()
watch(editing, (now) => {
  if (now && node.value) title.value = node.value.title
  dirty.value = false
})
onBeforeUnmount(() => {
  document.title = 'Teaz Wiki'
})

async function save() {
  const content = editor.value?.getContent()
  if (!content || !title.value.trim()) return
  saving.value = true
  try {
    let saved: NodeDetail
    if (isNew.value) {
      saved = await api.post<NodeDetail>(`/api/wikis/${wiki.value.id}/nodes`, {
        parentId: newParentId.value,
        kind: newKind.value,
        title: title.value,
        content,
      })
    } else {
      saved = await api.put<NodeDetail>(`/api/wikis/${wiki.value.id}/nodes/${node.value!.id}`, {
        title: title.value,
        content,
        version: node.value!.version,
      })
    }
    dirty.value = false
    node.value = saved
    await reloadTree()
    await router.replace(nodePath(wiki.value.id, saved.path))
    notify('Saved.')
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

function cancel() {
  if (isNew.value) {
    const parent = newParentId.value
    router.push(parent ? nodePath(wiki.value.id, slugPath(tree.value, parent)) : wikiPath(wiki.value.id))
  } else {
    router.push({ path: route.path })
  }
}

async function createSubCategory(name: string) {
  newCategory.value = false
  try {
    const created = await api.post<NodeDetail>(`/api/wikis/${wiki.value.id}/nodes`, {
      kind: 'CATEGORY',
      title: name,
      parentId: node.value!.id,
    })
    await reloadTree()
    router.push(nodePath(wiki.value.id, created.path))
  } catch (error) {
    notifyError(error)
  }
}

// Unsaved changes: ask before leaving the editor, and before closing the tab.
async function mayLeave(): Promise<boolean> {
  if (!editing.value || !dirty.value) return true
  return confirmAction({
    title: 'Discard changes?',
    text: 'Your changes to this page are not saved yet.',
    confirmLabel: 'Discard',
    danger: true,
  })
}
onBeforeRouteLeave(mayLeave)
onBeforeRouteUpdate(mayLeave)

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (editing.value && dirty.value) event.preventDefault()
}
window.addEventListener('beforeunload', onBeforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
</script>

<template>
  <NotFoundView v-if="notFound" />
  <article v-else-if="isNew || node" class="page">
    <nav v-if="crumbs.length" class="crumbs" aria-label="Breadcrumbs">
      <template v-for="(crumb, index) in crumbs" :key="crumb.id">
        <Icon v-if="index > 0" name="chevron-right" class="crumbs__sep" />
        <RouterLink :to="nodePath(wiki.id, slugPath(tree, crumb.id))" class="crumbs__link">{{ crumb.title }}</RouterLink>
      </template>
    </nav>

    <!-- Editing and creating -->
    <template v-if="editing">
      <div class="page__edit-head">
        <input
          v-model="title"
          class="page__title-input"
          :placeholder="newKind === 'CATEGORY' && isNew ? 'Category name' : 'Page title'"
          maxlength="120"
          aria-label="Title"
          @input="dirty = true"
        />
        <div class="page__edit-actions">
          <button type="button" class="btn" :disabled="saving" @click="cancel">Cancel</button>
          <button type="button" class="btn btn--primary" :disabled="saving || !title.trim()" @click="save">
            <Icon name="save" />
            {{ isNew ? 'Create' : 'Save' }}
          </button>
        </div>
      </div>
      <p v-if="isNew && newParent" class="hint page__new-hint">New page in {{ newParent.title }}</p>
      <WikiEditor
        ref="editor"
        :key="node?.id ?? 'new'"
        :content="node?.content ?? JSON.stringify(EMPTY_DOC)"
        :wiki-id="wiki.id"
        @change="dirty = true"
      />
    </template>

    <!-- Reading -->
    <template v-else-if="node">
      <header class="page__head">
        <div class="page__heading">
          <h1 class="page__title">
            <Icon v-if="node.kind === 'CATEGORY'" name="folder" class="page__kind" />
            {{ node.title }}
          </h1>
          <p class="page__meta">
            Updated {{ formatDate(node.updatedAt) }} by {{ node.updatedBy.name }}
          </p>
        </div>
        <RouterLink v-if="canEdit" :to="{ path: route.path, query: { edit: null } }" class="btn">
          <Icon name="pencil" />
          Edit
        </RouterLink>
      </header>

      <ContentView v-if="!isEmpty" :content="node.content" :wiki-id="wiki.id" class="page__content" />
      <p v-else-if="node.kind === 'PAGE'" class="muted page__empty">
        This page is empty.<template v-if="canEdit"> Use Edit to write it.</template>
      </p>

      <section v-if="node.kind === 'CATEGORY'" class="contents" aria-label="In this category">
        <div class="contents__head">
          <h2 class="contents__title">In this category</h2>
          <div v-if="canEdit" class="contents__actions">
            <RouterLink :to="{ path: `/${wiki.id}/-/new`, query: { parent: node.id } }" class="btn btn--small">
              <Icon name="file" />
              New page
            </RouterLink>
            <button v-if="nodeLevel === 0" type="button" class="btn btn--small" @click="newCategory = true">
              <Icon name="folder" />
              New sub-category
            </button>
          </div>
        </div>
        <p v-if="!items.length" class="muted">Nothing here yet.</p>
        <ul v-else class="contents__list">
          <li v-for="item in items" :key="item.id">
            <RouterLink :to="nodePath(wiki.id, slugPath(tree, item.id))" class="contents__item">
              <Icon :name="item.kind === 'CATEGORY' ? 'folder' : 'file'" />
              <span class="contents__name">{{ item.title }}</span>
              <span v-if="item.kind === 'CATEGORY'" class="contents__count">
                {{ children(tree, item.id).length }} {{ children(tree, item.id).length === 1 ? 'page' : 'pages' }}
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <CommentSection :wiki-id="wiki.id" :node-id="node.id" :can-comment="wiki.canComment" />
    </template>

    <NameDialog
      v-if="newCategory && node"
      :title="`New sub-category in ${node.title}`"
      label="Name"
      submit-label="Create"
      hint="Sub-categories hold pages. Like every category, it can have content of its own."
      @submit="createSubCategory"
      @close="newCategory = false"
    />
  </article>
</template>

<style scoped>
.page {
  max-width: 980px;
  padding: 28px 40px 80px;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 0.8125rem;
}

.crumbs__link {
  color: var(--color-text-secondary);
  text-decoration: none;
}

.crumbs__link:hover {
  color: var(--color-text-primary);
  text-decoration: underline;
}

.crumbs__sep {
  width: 14px;
  height: 14px;
  color: var(--color-text-secondary);
}

.page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page__heading {
  min-width: 0;
}

.page__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.875rem;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.page__kind {
  width: 26px;
  height: 26px;
  color: var(--color-text-secondary);
}

.page__meta {
  margin-top: 6px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.page__empty {
  padding: 24px 0;
}

.page__edit-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.page__title-input {
  flex: 1;
  min-width: 220px;
  padding: 6px 0;
  font: inherit;
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--color-text-primary);
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--color-border);
}

.page__title-input:focus {
  border-bottom-color: var(--color-accent);
  outline: none;
}

.page__edit-actions {
  display: flex;
  gap: 8px;
}

.page__new-hint {
  margin: -4px 0 12px;
}

.contents {
  margin-top: 40px;
}

.contents__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.contents__title {
  font-size: 1rem;
}

.contents__actions {
  display: flex;
  gap: 6px;
}

.contents__list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px;
  padding: 0;
  list-style: none;
}

.contents__item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 10px 12px;
  color: var(--color-text-primary);
  text-decoration: none;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.contents__item:hover {
  border-color: var(--color-accent);
}

.contents__item .icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
}

.contents__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.contents__count {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

@media (max-width: 700px) {
  .page {
    padding: 20px 16px 64px;
  }

  .page__title {
    font-size: 1.5rem;
  }

  .page__title-input {
    font-size: 1.375rem;
  }
}
</style>
