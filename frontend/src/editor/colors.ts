import { Mark, mergeAttributes } from '@tiptap/core'

/*
 * Text and background colors in page content. Content stores the color's
 * name, never a value; content.css maps the name to the theme's
 * --color-content-<name> / --color-content-<name>-bg tokens, which keep text
 * readable in every theme (teaz_pro/docs/COLOR_SCHEMES.md).
 */
export const CONTENT_COLORS = ['gray', 'red', 'orange', 'yellow', 'green', 'blue', 'purple', 'pink'] as const
export type ContentColor = (typeof CONTENT_COLORS)[number]

export const COLOR_LABELS: Record<ContentColor, string> = {
  gray: 'Gray',
  red: 'Red',
  orange: 'Orange',
  yellow: 'Yellow',
  green: 'Green',
  blue: 'Blue',
  purple: 'Purple',
  pink: 'Pink',
}

function isColor(value: unknown): value is ContentColor {
  return CONTENT_COLORS.includes(value as ContentColor)
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    contentColors: {
      setTextColor: (color: ContentColor) => ReturnType
      unsetTextColor: () => ReturnType
      setHighlight: (color: ContentColor) => ReturnType
      unsetHighlight: () => ReturnType
    }
  }
}

export const TextColor = Mark.create({
  name: 'textColor',

  addAttributes() {
    return {
      color: {
        default: null,
        parseHTML: (element) => {
          const color = element.getAttribute('data-text-color')
          return isColor(color) ? color : null
        },
        renderHTML: (attributes) => ({ 'data-text-color': attributes.color }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-text-color]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes), 0]
  },

  addCommands() {
    return {
      setTextColor:
        (color) =>
        ({ commands }) =>
          commands.setMark(this.name, { color }),
      unsetTextColor:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
    }
  },
})

export const Highlight = Mark.create({
  name: 'highlight',

  addAttributes() {
    return {
      color: {
        default: null,
        parseHTML: (element) => {
          const color = element.getAttribute('data-highlight')
          return isColor(color) ? color : null
        },
        renderHTML: (attributes) => ({ 'data-highlight': attributes.color }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'mark[data-highlight]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['mark', mergeAttributes(HTMLAttributes), 0]
  },

  addCommands() {
    return {
      setHighlight:
        (color) =>
        ({ commands }) =>
          commands.setMark(this.name, { color }),
      unsetHighlight:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
    }
  },
})
