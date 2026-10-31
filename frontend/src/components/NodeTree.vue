<script setup lang="ts">
import { computed, inject, provide, ref, toRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { WIKI } from '@/keys'
import { confirmAction, notifyError } from '@/feedback'
import { ancestry, children, level, nodePath, slugPath, subCategoryHint, wikiPath } from '@/paths'
import { canEdit as canEditLevel, type NodeDetail, type TreeNode } from '@/types'
import { TREE, type DropPlace, type TreeActions } from '@/components/tree'
import NodeTreeItem from '@/components/NodeTreeItem.vue'
import NameDialog from '@/components/NameDialog.vue'
import MoveDialog from '@/components/MoveDialog.vue'

/*
 * The sidebar: categories, sub-categories and pages. Editors create, rename,
 * reorder (menu or drag and drop), move and delete here; the backend checks
 * the depth rules and names, its messages show as toasts.
 */
const props = defineProps<{ activeId: string | null }>()

const { wiki, tree, reloadTree, setTree } = inject(WIKI)!
const route = useRoute()
const router = useRouter()

const canEdit = computed(() => canEditLevel(wiki.value.level))
const roots = computed(() => children(tree.value, null))

// Expanded categories, per wiki for this browser session.
const storageKey = `wiki:expanded:${wiki.value.id}`
const expanded = ref(new Set<string>(JSON.parse(sessionStorage.getItem(storageKey) ?? '[]')))
watch(expanded, (set) => sessionStorage.setItem(storageKey, JSON.stringify([...set])), { deep: true })
// The open page's categories are always expanded.
watch(
  () => [props.activeId, tree.value] as const,
  ([id]) => {
    if (!id) return
    for (const node of ancestry(tree.value, id)) if (node.kind === 'CATEGORY') expanded.value.add(node.id)
  },
  { immediate: true },
)

const dialog = ref<
  | { type: 'category'; parent: TreeNode | null }
  | { type: 'rename'; node: TreeNode }
  | { type: 'move'; node: TreeNode }
  | null
>(null)

const dragging = ref<TreeNode | null>(null)

/**
 * After a rename or move, the open page may have a new URL. `activeId` comes
 * from the URL, so it has to be taken before the tree changes.
 */
function followActive(activeId: string | null) {
  if (!activeId || route.name !== 'node') return
  const path = slugPath(tree.value, activeId)
  if (path.length) router.replace({ path: nodePath(wiki.value.id, path), query: route.query })
}

async function move(node: TreeNode, parentId: string | null, position: number) {
  try {
    const activeId = props.activeId
    setTree(await api.post<TreeNode[]>(`/api/wikis/${wiki.value.id}/nodes/${node.id}/move`, { parentId, position }))
    if (parentId) expanded.value.add(parentId)
    followActive(activeId)
  } catch (error) {
    notifyError(error)
  }
}

async function createCategory(name: string) {
  const parent = dialog.value?.type === 'category' ? dialog.value.parent : null
  dialog.value = null
  try {
    const node = await api.post<NodeDetail>(`/api/wikis/${wiki.value.id}/nodes`, {
      kind: 'CATEGORY',
      title: name,
      parentId: parent?.id ?? null,
    })
    await reloadTree()
    if (parent) expanded.value.add(parent.id)
    router.push(nodePath(wiki.value.id, node.path))
  } catch (error) {
    notifyError(error)
  }
}

async function rename(name: string) {
  if (dialog.value?.type !== 'rename') return
  const node = dialog.value.node
  dialog.value = null
  try {
    const activeId = props.activeId
    const current = await api.get<NodeDetail>(`/api/wikis/${wiki.value.id}/nodes/${node.id}`)
    await api.put(`/api/wikis/${wiki.value.id}/nodes/${node.id}`, { title: name, version: current.version })
    await reloadTree()
    followActive(activeId)
  } catch (error) {
    notifyError(error)
  }
}

const actions: TreeActions = {
  wikiId: wiki.value.id,
  canEdit,
  activeId: toRef(props, 'activeId'),
  expanded,
  dragging,
  toggle(id) {
    if (expanded.value.has(id)) expanded.value.delete(id)
    else expanded.value.add(id)
  },
  newPage(parent) {
    router.push({ path: `/${wiki.value.id}/-/new`, query: { parent: parent.id } })
  },
  newCategory(parent) {
    dialog.value = { type: 'category', parent }
  },
  rename(node) {
    dialog.value = { type: 'rename', node }
  },
  moveBy(node, offset) {
    const siblings = children(tree.value, node.parentId)
    const index = siblings.findIndex((n) => n.id === node.id)
    const target = index + offset
    if (target < 0 || target >= siblings.length) return
    move(node, node.parentId, target)
  },
  moveTo(node) {
    dialog.value = { type: 'move', node }
  },
  async remove(node) {
    const count = tree.value.filter((n) => ancestry(tree.value, n.id).some((a) => a.id === node.id)).length - 1
    const ok = await confirmAction({
      title: `Delete "${node.title}"?`,
      text:
        count > 0
          ? `This also deletes the ${count} ${count === 1 ? 'entry' : 'entries'} in it, with all comments. This can't be undone.`
          : "Its content and comments are deleted too. This can't be undone.",
      confirmLabel: 'Delete',
      danger: true,
    })
    if (!ok) return
    try {
      const wasOpen = props.activeId && ancestry(tree.value, props.activeId).some((a) => a.id === node.id)
      await api.delete(`/api/wikis/${wiki.value.id}/nodes/${node.id}?recursive=${count > 0}`)
      await reloadTree()
      if (wasOpen) router.push(wikiPath(wiki.value.id))
    } catch (error) {
      notifyError(error)
    }
  },
  drop(target, place: DropPlace) {
    const node = dragging.value
    dragging.value = null
    if (!node || node.id === target.id) return
    if (place === 'inside') {
      move(node, target.id, children(tree.value, target.id).filter((n) => n.id !== node.id).length)
      return
    }
    const siblings = children(tree.value, target.parentId).filter((n) => n.id !== node.id)
    const index = siblings.findIndex((n) => n.id === target.id)
    move(node, target.parentId, place === 'before' ? index : index + 1)
  },
}
provide(TREE, actions)

const renameNode = computed(() => (dialog.value?.type === 'rename' ? dialog.value.node : null))
const moveNode = computed(() => (dialog.value?.type === 'move' ? dialog.value.node : null))
const categoryParent = computed(() => (dialog.value?.type === 'category' ? dialog.value.parent : null))
</script>

<template>
  <nav class="tree">
    <div class="tree__header">
      <RouterLink :to="`/${wiki.id}`" class="tree__overview" :class="{ 'tree__overview--active': route.name === 'wiki' }">
        <Icon name="book" />
        Overview
      </RouterLink>
      <button
        v-if="canEdit"
        type="button"
        class="icon-btn"
        title="New category"
        aria-label="New category"
        @click="actions.newCategory(null)"
      >
        <Icon name="plus" />
      </button>
    </div>

    <ul v-if="roots.length" class="tree__list" role="tree">
      <NodeTreeItem v-for="node in roots" :key="node.id" :node="node" :level="0" />
    </ul>
    <div v-else class="tree__empty">
      <p>No categories yet.</p>
      <button v-if="canEdit" type="button" class="btn btn--small" @click="actions.newCategory(null)">
        <Icon name="plus" />
        New category
      </button>
    </div>
  </nav>

  <NameDialog
    v-if="dialog?.type === 'category'"
    :title="categoryParent ? `New sub-category in ${categoryParent.title}` : 'New category'"
    label="Name"
    submit-label="Create"
    :hint="
      categoryParent
        ? subCategoryHint(level(tree, categoryParent.id) + 1)
        : 'Categories are the top level of the wiki. They hold pages and sub-categories and can have content of their own.'
    "
    @submit="createCategory"
    @close="dialog = null"
  />
  <NameDialog
    v-if="renameNode"
    :title="`Rename ${renameNode.kind === 'CATEGORY' ? 'category' : 'page'}`"
    label="Name"
    :initial="renameNode.title"
    submit-label="Rename"
    hint="The address changes with the name; links to the old address stop working."
    @submit="rename"
    @close="dialog = null"
  />
  <MoveDialog
    v-if="moveNode"
    :node="moveNode"
    :tree="tree"
    @move="(parentId) => { const n = moveNode!; dialog = null; move(n, parentId, children(tree, parentId).length) }"
    @close="dialog = null"
  />
</template>

<style scoped>
.tree {
  padding: 12px 8px 32px;
}

.tree__header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}

.tree__overview {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 8px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text-primary);
  text-decoration: none;
}

.tree__overview:hover {
  background: var(--color-surface-hover);
}

.tree__overview--active {
  background: var(--color-surface-active);
}

.tree__overview .icon {
  width: 16px;
  height: 16px;
  color: var(--color-text-secondary);
}

.tree__list {
  padding: 0;
  list-style: none;
}

.tree__empty {
  padding: 12px 8px;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.tree__empty .btn {
  margin-top: 8px;
}
</style>
