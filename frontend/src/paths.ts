import type { TreeNode } from '@/types'

/*
 * URLs: /{wikiId}/{category}/{sub-category or page}/{sub-category or page}/{page}.
 * App pages of a
 * wiki live under the segment "-", which is never a slug:
 * /{wikiId}/-/settings, /{wikiId}/-/new, /{wikiId}/-/search.
 */

export function wikiPath(wikiId: string): string {
  return `/${wikiId}`
}

export function nodePath(wikiId: string, path: readonly string[]): string {
  return `/${wikiId}/${path.join('/')}`
}

/** Slugs from the top level down to `id`, from the wiki's tree. */
export function slugPath(tree: readonly TreeNode[], id: string): string[] {
  const byId = new Map(tree.map((n) => [n.id, n]))
  const path: string[] = []
  for (let node = byId.get(id); node; node = node.parentId ? byId.get(node.parentId) : undefined) {
    path.unshift(node.slug)
  }
  return path
}

/** Nodes from the top level down to `id`, for breadcrumbs. */
export function ancestry(tree: readonly TreeNode[], id: string): TreeNode[] {
  const byId = new Map(tree.map((n) => [n.id, n]))
  const chain: TreeNode[] = []
  for (let node = byId.get(id); node; node = node.parentId ? byId.get(node.parentId) : undefined) {
    chain.unshift(node)
  }
  return chain
}

/**
 * Deepest level: 0 = top level (categories only), MAX_LEVEL holds pages only
 * (see NodeService).
 */
export const MAX_LEVEL = 3

/** 0 = top level; the depth rules allow 0..MAX_LEVEL. */
export function level(tree: readonly TreeNode[], id: string): number {
  return ancestry(tree, id).length - 1
}

/** Hint for the dialog that creates a sub-category on `atLevel`. */
export function subCategoryHint(atLevel: number): string {
  const holds = atLevel + 1 < MAX_LEVEL ? 'pages and further sub-categories' : 'pages'
  return `Sub-categories here hold ${holds}. Like every category, it can have content of its own.`
}

export function children(tree: readonly TreeNode[], parentId: string | null): TreeNode[] {
  return tree.filter((n) => n.parentId === parentId).sort((a, b) => a.position - b.position)
}

/** Titles of the categories above `id`, e.g. "Guides / Setup" (empty on the top level). */
export function parentTitles(tree: readonly TreeNode[], id: string): string {
  return ancestry(tree, id)
    .slice(0, -1)
    .map((n) => n.title)
    .join(' / ')
}
