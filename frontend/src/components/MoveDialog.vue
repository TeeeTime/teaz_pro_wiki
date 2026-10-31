<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { MAX_LEVEL, ancestry, children, level } from '@/paths'
import type { TreeNode } from '@/types'
import Modal from '@/components/Modal.vue'

/*
 * Picks a new place for a node. Offers only places the depth rules allow
 * (see NodeService): the top level takes categories, the deepest level
 * (MAX_LEVEL) takes pages only, nothing goes deeper.
 */
const props = defineProps<{ node: TreeNode; tree: TreeNode[] }>()
const emit = defineEmits<{ move: [parentId: string | null]; close: [] }>()

/** How many levels the node occupies: 1 (no children) up to MAX_LEVEL + 1. */
const height = computed(() => {
  const below = (id: string): number =>
    Math.max(0, ...children(props.tree, id).map((child) => 1 + below(child.id)))
  return 1 + below(props.node.id)
})

/** How far below the node its deepest category is (0 = the node itself), -1 without categories. */
const categoryDepth = computed(() => {
  const below = (node: TreeNode): number =>
    node.kind !== 'CATEGORY'
      ? -1
      : Math.max(0, ...children(props.tree, node.id).map((child) => below(child) + 1).filter((d) => d > 0))
  return below(props.node)
})

function allowedAt(targetLevel: number): boolean {
  if (targetLevel + height.value - 1 > MAX_LEVEL) return false
  if (targetLevel === 0 && props.node.kind !== 'CATEGORY') return false
  if (categoryDepth.value >= 0 && targetLevel + categoryDepth.value >= MAX_LEVEL) return false
  return true
}

const targets = computed(() => {
  const result: { id: string | null; label: string; depth: number }[] = []
  if (allowedAt(0) && props.node.parentId !== null) result.push({ id: null, label: 'Top level', depth: 0 })
  for (const category of props.tree.filter((n) => n.kind === 'CATEGORY')) {
    const inside = ancestry(props.tree, category.id).some((a) => a.id === props.node.id)
    if (inside || category.id === props.node.parentId) continue
    const depth = level(props.tree, category.id)
    if (allowedAt(depth + 1)) result.push({ id: category.id, label: category.title, depth: depth + 1 })
  }
  return result.sort((a, b) => order(a.id).localeCompare(order(b.id)))
})

/** Sort key that keeps sub-categories below their category. */
function order(id: string | null): string {
  if (!id) return ''
  return ancestry(props.tree, id)
    .map((n) => String(n.position).padStart(5, '0'))
    .join('/')
}

const selected = ref<string | null | undefined>(undefined)
</script>

<template>
  <Modal :title="`Move ${node.title}`" narrow @close="emit('close')">
    <p v-if="targets.length === 0" class="muted">There is no other place this can go.</p>
    <ul v-else class="targets">
      <li v-for="target in targets" :key="target.id ?? 'root'">
        <button
          type="button"
          class="menu-item"
          :class="{ 'menu-item--active': selected === target.id }"
          :style="{ paddingLeft: `${10 + Math.max(0, target.depth - 1) * 16}px` }"
          @click="selected = target.id"
        >
          <Icon :name="target.id ? 'folder' : 'book'" />
          {{ target.label }}
        </button>
      </li>
    </ul>
    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button
        type="button"
        class="btn btn--primary"
        :disabled="selected === undefined"
        @click="emit('move', selected ?? null)"
      >
        Move here
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.targets {
  padding: 0;
  list-style: none;
  max-height: 50vh;
  overflow: auto;
}
</style>
