import type { InjectionKey, Ref } from 'vue'
import type { TreeNode, WikiDetail } from '@/types'

/** The wiki that is open, provided by App.vue, set by WikiLayout.vue. */
export const CURRENT_WIKI: InjectionKey<Ref<WikiDetail | null>> = Symbol('current-wiki')

/** What WikiLayout gives its pages. */
export interface WikiContext {
  wiki: Ref<WikiDetail>
  tree: Ref<TreeNode[]>
  reloadTree(): Promise<void>
  reloadWiki(): Promise<void>
  setTree(tree: TreeNode[]): void
}

export const WIKI: InjectionKey<WikiContext> = Symbol('wiki')
