import type { AnyExtension } from '@tiptap/core'
import type { Node as PmNode } from '@tiptap/pm/model'
import StarterKit from '@tiptap/starter-kit'
import TextAlign from '@tiptap/extension-text-align'
import { Table, TableCell, TableHeader } from '@tiptap/extension-table'
import { Mathematics } from '@tiptap/extension-mathematics'
import { Placeholder } from '@tiptap/extensions'
import { Highlight, TextColor } from './colors'
import { ResizableTableRow } from './tableRowHeight'
import { ImageBlock, ImageText } from './images'

/*
 * The one list of extensions for editing and for reading, so a page looks
 * the same in both. Whatever is added here has to be known to the backend's
 * ContentText too if it carries text (for search).
 */

export type MathKind = 'inline' | 'block'

export interface ExtensionOptions {
  editable: boolean
  wikiId: string
  /** Editing only: a click on a formula, to change it. */
  onMathClick?: (kind: MathKind, latex: string, pos: number) => void
}

export const EMPTY_DOC = { type: 'doc', content: [{ type: 'paragraph' }] }

export function createExtensions(options: ExtensionOptions): AnyExtension[] {
  const mathClick = (kind: MathKind) => (node: PmNode, pos: number) =>
    options.onMathClick?.(kind, node.attrs.latex as string, pos)

  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      link: {
        openOnClick: false,
        autolink: true,
        defaultProtocol: 'https',
        protocols: ['http', 'https', 'mailto'],
        HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: null },
      },
    }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    TextColor,
    Highlight,
    Table.configure({ resizable: true, cellMinWidth: 48, lastColumnResizable: true }),
    ResizableTableRow,
    TableHeader,
    TableCell,
    Mathematics.configure({
      katexOptions: { throwOnError: false },
      inlineOptions: options.editable ? { onClick: mathClick('inline') } : {},
      blockOptions: options.editable ? { onClick: mathClick('block') } : {},
    }),
    ImageBlock.configure({ wikiId: options.wikiId }),
    ImageText.configure({ wikiId: options.wikiId }),
    ...(options.editable ? [Placeholder.configure({ placeholder: 'Write something…' })] : []),
  ]
}

/** Content from the backend; anything unreadable shows as an empty page instead of breaking it. */
export function parseContent(content: string): object {
  try {
    const doc = JSON.parse(content)
    return doc && doc.type === 'doc' ? doc : EMPTY_DOC
  } catch {
    return EMPTY_DOC
  }
}
