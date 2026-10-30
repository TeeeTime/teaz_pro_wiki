/** Shapes of the backend's JSON (WikiDtos, NodeDtos, CommentService, Users). */

export interface UserView {
  id: string
  username: string
  name: string
}

export type Visibility = 'PRIVATE' | 'PUBLIC'
export type AccessLevel = 'NONE' | 'READER' | 'VIEWER' | 'EDITOR' | 'OWNER'
export type Relation = 'OWNER' | 'EDITOR' | 'VIEWER' | 'SUBSCRIBER'
export type MemberRole = 'EDITOR' | 'VIEWER'
export type NodeKind = 'CATEGORY' | 'PAGE'

export interface WikiSummary {
  id: string
  name: string
  logoFileId: string | null
  visibility: Visibility
  relation: Relation
}

export interface WikiDetail {
  id: string
  name: string
  description: string
  logoFileId: string | null
  visibility: Visibility
  owner: UserView
  level: AccessLevel
  subscribed: boolean
  canComment: boolean
}

export interface MemberView {
  user: UserView
  role: MemberRole
}

export interface TreeNode {
  id: string
  parentId: string | null
  kind: NodeKind
  title: string
  slug: string
  position: number
}

export interface NodeDetail {
  id: string
  parentId: string | null
  kind: NodeKind
  title: string
  slug: string
  path: string[]
  /** Tiptap JSON */
  content: string
  version: number
  createdBy: UserView
  createdAt: string
  updatedBy: UserView
  updatedAt: string
}

export interface NodeRef {
  id: string
  kind: NodeKind
  title: string
  path: string[]
  updatedBy: UserView
  updatedAt: string
}

export interface Overview {
  categoryCount: number
  pageCount: number
  recent: NodeRef[]
}

export interface SearchHit {
  id: string
  kind: NodeKind
  title: string
  path: string[]
  snippet: string
}

export interface CommentView {
  id: string
  parentId: string | null
  /** Null once deleted. */
  author: UserView | null
  body: string
  createdAt: string
  editedAt: string | null
  deleted: boolean
  canEdit: boolean
  canDelete: boolean
  replies: CommentView[]
}

export interface FileView {
  id: string
  mediaType: string
  width: number | null
  height: number | null
}

export function canEdit(level: AccessLevel): boolean {
  return level === 'EDITOR' || level === 'OWNER'
}
