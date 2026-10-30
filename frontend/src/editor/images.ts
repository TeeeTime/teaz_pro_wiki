import { Node, mergeAttributes } from '@tiptap/core'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import ImageBlockView from './ImageBlockView.vue'
import ImageTextView from './ImageTextView.vue'

/*
 * Images come in fixed layouts instead of free floating, so a page never
 * falls apart: one image (width and alignment), two or three side by side,
 * or one image next to text (left or right). Every image keeps its original
 * file; aspect ratio and crop are only settings, applied when rendering
 * (ImageFigure.vue).
 */

export const ASPECTS = ['original', '16:9', '4:3', '1:1', '3:4'] as const
export type Aspect = (typeof ASPECTS)[number]

/** The visible part, as fractions of the original image; `ratio` = width / height of that part in pixels. */
export interface Crop {
  x: number
  y: number
  w: number
  h: number
  ratio: number
}

export interface ImageItem {
  fileId: string
  alt: string
  caption: string
  aspect: Aspect
  crop: Crop | null
}

export type BlockLayout = 'single' | 'two' | 'three'
export type SingleWidth = 25 | 50 | 75 | 100
export type Align = 'left' | 'center' | 'right'

export interface ImageBlockAttrs {
  layout: BlockLayout
  images: ImageItem[]
  width: SingleWidth
  align: Align
}

export interface ImageTextAttrs {
  /** Where the image is; the text takes the other side. */
  side: 'left' | 'right'
  /** The image's share of the width. */
  share: 'third' | 'half'
  images: ImageItem[]
}

/** What the insert dialog offers: an imageBlock layout or an imageText side. */
export type InsertLayout = 'single' | 'image-left' | 'image-right' | 'two' | 'three'

export const LAYOUT_COUNT: Record<BlockLayout, number> = { single: 1, two: 2, three: 3 }

export function newImage(fileId: string, aspect: Aspect = 'original'): ImageItem {
  return { fileId, alt: '', caption: '', aspect, crop: null }
}

export interface ImageOptions {
  /** For uploads from inside a block (adding or replacing an image). */
  wikiId: string
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    images: {
      insertImageBlock: (attrs: Partial<ImageBlockAttrs> & { images: ImageItem[] }) => ReturnType
      insertImageText: (attrs: Partial<ImageTextAttrs> & { images: ImageItem[] }) => ReturnType
    }
  }
}

/** Attributes as one JSON data attribute, so copy and paste keeps them. */
function jsonAttributes(names: string[]) {
  return {
    parseHTML: (element: HTMLElement) => {
      try {
        const all = JSON.parse(element.getAttribute('data-attrs') ?? '{}')
        return Object.fromEntries(names.filter((n) => n in all).map((n) => [n, all[n]]))
      } catch {
        return {}
      }
    },
  }
}

export const ImageBlock = Node.create<ImageOptions>({
  name: 'imageBlock',
  group: 'block',
  atom: true,
  draggable: true,
  selectable: true,

  addOptions() {
    return { wikiId: '' }
  },

  addAttributes() {
    return {
      layout: { default: 'single', rendered: false },
      images: { default: [], rendered: false },
      width: { default: 100, rendered: false },
      align: { default: 'center', rendered: false },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="image-block"]', getAttrs: jsonAttributes(['layout', 'images', 'width', 'align']).parseHTML }]
  },

  renderHTML({ node, HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'image-block', 'data-attrs': JSON.stringify(node.attrs) })]
  },

  addNodeView() {
    return VueNodeViewRenderer(ImageBlockView)
  },

  addCommands() {
    return {
      insertImageBlock:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({ type: this.name, attrs }),
    }
  },
})

export const ImageText = Node.create<ImageOptions>({
  name: 'imageText',
  group: 'block',
  content: 'block+',
  isolating: true,
  defining: true,
  draggable: true,

  addOptions() {
    return { wikiId: '' }
  },

  addAttributes() {
    return {
      side: { default: 'left', rendered: false },
      share: { default: 'third', rendered: false },
      images: { default: [], rendered: false },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="image-text"]', getAttrs: jsonAttributes(['side', 'share', 'images']).parseHTML, contentElement: '.image-text__text' }]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, { 'data-type': 'image-text', 'data-attrs': JSON.stringify(node.attrs) }),
      ['div', { class: 'image-text__text' }, 0],
    ]
  },

  addNodeView() {
    return VueNodeViewRenderer(ImageTextView)
  },

  addCommands() {
    return {
      insertImageText:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs,
            content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Text next to the image.' }] }],
          }),
    }
  },
})
