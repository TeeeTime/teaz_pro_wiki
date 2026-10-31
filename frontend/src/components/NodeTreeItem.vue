<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { WIKI } from '@/keys'
import { MAX_LEVEL, children, nodePath, slugPath } from '@/paths'
import { usePopover } from '@/popover'
import type { TreeNode } from '@/types'
import { TREE, type DropPlace } from '@/components/tree'

/* One row of the sidebar and, for categories, its children. */
const props = defineProps<{ node: TreeNode; level: number }>()

const { tree } = inject(WIKI)!
const actions = inject(TREE)!

const isCategory = computed(() => props.node.kind === 'CATEGORY')
/** Whether a category may go in here; below that, only pages. */
const takesCategories = computed(() => props.level + 1 < MAX_LEVEL)
const items = computed(() => children(tree.value, props.node.id))
const open = computed(() => actions.expanded.value.has(props.node.id))
const active = computed(() => actions.activeId.value === props.node.id)
const to = computed(() => nodePath(actions.wikiId, slugPath(tree.value, props.node.id)))
const siblings = computed(() => children(tree.value, props.node.parentId))
const index = computed(() => siblings.value.findIndex((n) => n.id === props.node.id))

const menuRoot = ref<HTMLElement | null>(null)
const menu = usePopover(menuRoot)
const addRoot = ref<HTMLElement | null>(null)
const add = usePopover(addRoot)

function run(action: () => void) {
  menu.close()
  add.close()
  action()
}

// Drag and drop: drop on the upper or lower edge to place before/after, in the middle of a category to move into it.
const dropPlace = ref<DropPlace | null>(null)

function onDragStart(event: DragEvent) {
  actions.dragging.value = props.node
  event.dataTransfer?.setData('text/plain', props.node.title)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(event: DragEvent) {
  if (!actions.dragging.value || actions.dragging.value.id === props.node.id) return
  event.preventDefault()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const y = (event.clientY - rect.top) / rect.height
  if (isCategory.value) dropPlace.value = y < 0.25 ? 'before' : y > 0.75 ? 'after' : 'inside'
  else dropPlace.value = y < 0.5 ? 'before' : 'after'
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  if (dropPlace.value) actions.drop(props.node, dropPlace.value)
  dropPlace.value = null
}

function onDragEnd() {
  actions.dragging.value = null
  dropPlace.value = null
}
</script>

<template>
  <li class="item" role="treeitem" :aria-expanded="isCategory ? open : undefined">
    <div
      class="item__row"
      :class="[
        `item__row--level-${level}`,
        { 'item__row--active': active, [`item__row--drop-${dropPlace}`]: dropPlace },
      ]"
      :draggable="actions.canEdit.value"
      @dragstart="onDragStart"
      @dragover="onDragOver"
      @dragleave="dropPlace = null"
      @drop="onDrop"
      @dragend="onDragEnd"
    >
      <button
        v-if="isCategory"
        type="button"
        class="item__toggle"
        :aria-label="open ? 'Collapse' : 'Expand'"
        @click="actions.toggle(node.id)"
      >
        <Icon :name="open ? 'chevron-down' : 'chevron-right'" />
      </button>
      <span v-else class="item__toggle item__toggle--spacer" />

      <RouterLink :to="to" class="item__link" :title="node.title">
        <Icon :name="isCategory ? (open ? 'folder-open' : 'folder') : 'file'" class="item__icon" />
        <span class="item__title">{{ node.title }}</span>
      </RouterLink>

      <template v-if="actions.canEdit.value">
        <div v-if="isCategory" ref="addRoot" class="item__menu">
          <button
            type="button"
            class="icon-btn item__action"
            :aria-label="`Add to ${node.title}`"
            title="Add"
            @click="takesCategories ? add.toggle() : actions.newPage(node)"
          >
            <Icon name="plus" />
          </button>
          <div v-if="add.open.value" class="popover item__popover" role="menu">
            <button type="button" class="menu-item" role="menuitem" @click="run(() => actions.newPage(node))">
              <Icon name="file" />
              New page
            </button>
            <button type="button" class="menu-item" role="menuitem" @click="run(() => actions.newCategory(node))">
              <Icon name="folder" />
              New sub-category
            </button>
          </div>
        </div>

        <div ref="menuRoot" class="item__menu">
          <button
            type="button"
            class="icon-btn item__action"
            :aria-label="`Actions for ${node.title}`"
            title="More"
            @click="menu.toggle()"
          >
            <Icon name="more" />
          </button>
          <div v-if="menu.open.value" class="popover item__popover" role="menu">
            <button type="button" class="menu-item" role="menuitem" @click="run(() => actions.rename(node))">
              <Icon name="pencil" />
              Rename
            </button>
            <button
              type="button"
              class="menu-item"
              role="menuitem"
              :disabled="index <= 0"
              @click="run(() => actions.moveBy(node, -1))"
            >
              <Icon name="arrow-up" />
              Move up
            </button>
            <button
              type="button"
              class="menu-item"
              role="menuitem"
              :disabled="index >= siblings.length - 1"
              @click="run(() => actions.moveBy(node, 1))"
            >
              <Icon name="arrow-down" />
              Move down
            </button>
            <button type="button" class="menu-item" role="menuitem" @click="run(() => actions.moveTo(node))">
              <Icon name="folder-open" />
              Move to…
            </button>
            <div class="menu-divider" />
            <button
              type="button"
              class="menu-item menu-item--danger"
              role="menuitem"
              @click="run(() => actions.remove(node))"
            >
              <Icon name="trash" />
              Delete
            </button>
          </div>
        </div>
      </template>
    </div>

    <ul v-if="isCategory && open && items.length" class="item__children" role="group">
      <NodeTreeItem v-for="child in items" :key="child.id" :node="child" :level="level + 1" />
    </ul>
  </li>
</template>

<style scoped>
.item__row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 32px;
  padding-right: 2px;
}

.item__row:hover {
  background: var(--color-surface-hover);
}

.item__row--active {
  background: var(--color-surface-active);
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.item__row--level-1 {
  padding-left: 16px;
}

.item__row--level-2 {
  padding-left: 32px;
}

.item__row--level-3 {
  padding-left: 48px;
}

.item__row--drop-before {
  box-shadow: inset 0 2px 0 var(--color-accent);
}

.item__row--drop-after {
  box-shadow: inset 0 -2px 0 var(--color-accent);
}

.item__row--drop-inside {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}

.item__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  padding: 0;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.item__toggle .icon {
  width: 16px;
  height: 16px;
}

.item__toggle--spacer {
  cursor: default;
}

.item__link {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 32px;
  padding-right: 4px;
  font-size: 0.875rem;
  color: var(--color-text-primary);
  text-decoration: none;
}

.item__icon {
  width: 16px;
  height: 16px;
  color: var(--color-text-secondary);
}

.item__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__row--active .item__title {
  font-weight: 600;
}

.item__menu {
  position: relative;
}

.item__action {
  width: 26px;
  height: 26px;
  opacity: 0;
}

.item__action .icon {
  width: 16px;
  height: 16px;
}

/* Always visible on touch screens and while in use, otherwise on hover. */
.item__row:hover .item__action,
.item__row:focus-within .item__action,
.item__menu:has(.item__popover) .item__action {
  opacity: 1;
}

@media (hover: none) {
  .item__action {
    opacity: 1;
  }
}

.item__popover {
  top: 100%;
  right: 0;
  width: 200px;
}

.menu-item:disabled {
  opacity: 0.45;
  cursor: default;
}

.item__children {
  padding: 0;
  list-style: none;
}
</style>
