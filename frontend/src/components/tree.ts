import type { InjectionKey, Ref } from 'vue'
import type { TreeNode } from '@/types'

/** Where a dragged node would land relative to the row under the pointer. */
export type DropPlace = 'before' | 'inside' | 'after'

/** What NodeTree gives its rows (NodeTreeItem). */
export interface TreeActions {
  wikiId: string
  canEdit: Ref<boolean>
  activeId: Ref<string | null>
  expanded: Ref<Set<string>>
  dragging: Ref<TreeNode | null>
  toggle(id: string): void
  newPage(parent: TreeNode): void
  newCategory(parent: TreeNode | null): void
  rename(node: TreeNode): void
  moveBy(node: TreeNode, offset: number): void
  moveTo(node: TreeNode): void
  remove(node: TreeNode): void
  drop(target: TreeNode, place: DropPlace): void
}

export const TREE: InjectionKey<TreeActions> = Symbol('tree')
