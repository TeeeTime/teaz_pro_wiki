import { TableRow } from '@tiptap/extension-table'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import type { EditorView } from '@tiptap/pm/view'

/*
 * Table rows with an adjustable height: drag a row's lower edge (like the
 * column edges, which Tiptap's Table already handles). The height is a
 * minimum; rows still grow with their content.
 */

const MIN_HEIGHT = 28
/** How close (px) to a row's lower edge the pointer has to be to grab it. */
const HANDLE = 5

const key = new PluginKey('tableRowHeight')

function rowAt(view: EditorView, target: EventTarget | null, clientY: number): HTMLTableRowElement | null {
  if (!(target instanceof Element)) return null
  const row = target.closest('tr')
  if (!row || !view.dom.contains(row)) return null
  const rect = row.getBoundingClientRect()
  return clientY >= rect.bottom - HANDLE && clientY <= rect.bottom + 1 ? row : null
}

/** Document position of the tableRow node rendered as `row`. */
function rowPos(view: EditorView, row: HTMLTableRowElement): number | null {
  const cell = row.firstElementChild
  if (!cell) return null
  const $pos = view.state.doc.resolve(view.posAtDOM(cell, 0))
  for (let depth = $pos.depth; depth > 0; depth--) {
    if ($pos.node(depth).type.name === 'tableRow') return $pos.before(depth)
  }
  return null
}

function rowResizePlugin() {
  return new Plugin({
    key,
    props: {
      handleDOMEvents: {
        mousemove(view, event) {
          if (!view.editable) return false
          view.dom.classList.toggle('row-resize-cursor', rowAt(view, event.target, event.clientY) !== null)
          return false
        },
        mouseleave(view) {
          view.dom.classList.remove('row-resize-cursor')
          return false
        },
        mousedown(view, event) {
          if (!view.editable || event.button !== 0) return false
          const row = rowAt(view, event.target, event.clientY)
          if (!row) return false
          const pos = rowPos(view, row)
          if (pos === null) return false
          event.preventDefault()

          const startY = event.clientY
          const startHeight = row.getBoundingClientRect().height
          let height = startHeight
          const onMove = (move: MouseEvent) => {
            height = Math.max(MIN_HEIGHT, Math.round(startHeight + move.clientY - startY))
            row.style.height = `${height}px`
          }
          const onUp = () => {
            window.removeEventListener('mousemove', onMove)
            window.removeEventListener('mouseup', onUp)
            const node = view.state.doc.nodeAt(pos)
            if (node?.type.name !== 'tableRow') return
            view.dispatch(view.state.tr.setNodeMarkup(pos, undefined, { ...node.attrs, height }))
          }
          window.addEventListener('mousemove', onMove)
          window.addEventListener('mouseup', onUp)
          return true
        },
      },
    },
  })
}

export const ResizableTableRow = TableRow.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      height: {
        default: null,
        parseHTML: (element) => {
          const value = parseInt((element as HTMLElement).style.height, 10)
          return Number.isFinite(value) ? value : null
        },
        renderHTML: (attributes) => (attributes.height ? { style: `height: ${attributes.height}px` } : {}),
      },
    }
  },

  addProseMirrorPlugins() {
    return [rowResizePlugin()]
  },
})
