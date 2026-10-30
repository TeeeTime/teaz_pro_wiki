<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { NodeSelection } from '@tiptap/pm/state'
import { Icon } from '@teeetime/teaz-pro-ui'
import { uploadImage } from '@/files'
import { notifyError } from '@/feedback'
import { usePopover } from '@/popover'
import { COLOR_LABELS, CONTENT_COLORS, type ContentColor } from './colors'
import { createExtensions, parseContent, type MathKind } from './extensions'
import { newImage, type ImageItem, type InsertLayout } from './images'
import MathDialog from './MathDialog.vue'
import LinkDialog from './LinkDialog.vue'
import ImageInsertDialog from './ImageInsertDialog.vue'

/*
 * The page editor: Tiptap with the toolbar on top. Only opened with the Edit
 * button or for a new page; reading uses ContentView. The parent saves,
 * reading the content through getContent().
 */
const props = defineProps<{ content: string; wikiId: string }>()
const emit = defineEmits<{ change: [] }>()

const mathDialog = ref<{ kind: MathKind; latex: string; pos: number | null } | null>(null)
const linkDialog = ref<{ href: string; needsText: boolean } | null>(null)
const imageDialog = ref(false)

const editor = useEditor({
  content: parseContent(props.content),
  extensions: createExtensions({
    editable: true,
    wikiId: props.wikiId,
    onMathClick: (kind, latex, pos) => {
      mathDialog.value = { kind, latex, pos }
    },
  }),
  editorProps: {
    // Pasted or dropped image files become an image block.
    handlePaste: (_view, event) => insertFiles([...(event.clipboardData?.files ?? [])]),
    handleDrop: (_view, event) => insertFiles([...((event as DragEvent).dataTransfer?.files ?? [])]),
  },
  onUpdate: () => emit('change'),
})

onBeforeUnmount(() => editor.value?.destroy())

defineExpose({
  getContent: () => JSON.stringify(editor.value?.getJSON() ?? {}),
})

const e = computed(() => editor.value!)
const chain = () => e.value.chain().focus()

/** Toolbar clicks must not take the focus (and with it the selection) from the text; the style select needs it. */
function keepFocus(event: MouseEvent) {
  if (!(event.target as Element).closest('select, input')) event.preventDefault()
}

// Block type ------------------------------------------------------------
type BlockType = 'paragraph' | 'h1' | 'h2' | 'h3' | 'quote' | 'code'
const blockType = computed<BlockType>(() => {
  if (!editor.value) return 'paragraph'
  for (const level of [1, 2, 3] as const) if (e.value.isActive('heading', { level })) return `h${level}`
  if (e.value.isActive('blockquote')) return 'quote'
  if (e.value.isActive('codeBlock')) return 'code'
  return 'paragraph'
})

function setBlockType(value: BlockType) {
  const c = chain()
  if (e.value.isActive('blockquote') && value !== 'quote') c.lift('blockquote')
  if (value === 'paragraph') c.setParagraph()
  else if (value === 'quote') c.setParagraph().setBlockquote()
  else if (value === 'code') c.setCodeBlock()
  else c.setHeading({ level: Number(value.slice(1)) as 1 | 2 | 3 })
  c.run()
}

// Colors ------------------------------------------------------------------
const colorRoot = ref<HTMLElement | null>(null)
const colorMenu = usePopover(colorRoot)
const activeTextColor = computed(() => (editor.value ? (e.value.getAttributes('textColor').color as ContentColor | null) : null))
const activeHighlight = computed(() => (editor.value ? (e.value.getAttributes('highlight').color as ContentColor | null) : null))

function setTextColor(color: ContentColor | null) {
  if (color) chain().setTextColor(color).run()
  else chain().unsetTextColor().run()
}

function setHighlight(color: ContentColor | null) {
  if (color) chain().setHighlight(color).run()
  else chain().unsetHighlight().run()
}

// Tables --------------------------------------------------------------------
const tableRoot = ref<HTMLElement | null>(null)
const tableMenu = usePopover(tableRoot)
const GRID = 8
const hover = ref({ rows: 0, cols: 0 })
const inTable = computed(() => editor.value?.isActive('table') ?? false)

function insertTable(rows: number, cols: number) {
  tableMenu.close()
  chain().insertTable({ rows, cols, withHeaderRow: true }).run()
}

// Math and links --------------------------------------------------------------
function openMath() {
  mathDialog.value = { kind: 'inline', latex: '', pos: null }
}

function saveMath(kind: MathKind, latex: string) {
  const dialog = mathDialog.value
  mathDialog.value = null
  if (!dialog) return
  if (dialog.pos === null) {
    if (kind === 'inline') chain().insertInlineMath({ latex }).run()
    else chain().insertBlockMath({ latex }).run()
  } else if (kind === 'inline') {
    chain().updateInlineMath({ latex, pos: dialog.pos }).run()
  } else {
    chain().updateBlockMath({ latex, pos: dialog.pos }).run()
  }
}

function removeMath() {
  const dialog = mathDialog.value
  mathDialog.value = null
  if (dialog?.pos == null) return
  if (dialog.kind === 'inline') chain().deleteInlineMath({ pos: dialog.pos }).run()
  else chain().deleteBlockMath({ pos: dialog.pos }).run()
}

function openLink() {
  const href = (e.value.getAttributes('link').href as string | undefined) ?? ''
  linkDialog.value = { href, needsText: e.value.state.selection.empty && !href }
}

function saveLink(href: string, text: string) {
  const needsText = linkDialog.value?.needsText
  linkDialog.value = null
  if (needsText) {
    chain()
      .insertContent({ type: 'text', text: text || href, marks: [{ type: 'link', attrs: { href } }] })
      .run()
  } else {
    chain().extendMarkRange('link').setLink({ href }).run()
  }
}

function removeLink() {
  linkDialog.value = null
  chain().extendMarkRange('link').unsetLink().run()
}

// Images ------------------------------------------------------------------------
const uploading = ref(false)

async function upload(files: File[]): Promise<ImageItem[]> {
  uploading.value = true
  try {
    const uploaded = await Promise.all(files.map((file) => uploadImage(props.wikiId, file)))
    return uploaded.map((file) => newImage(file.id))
  } finally {
    uploading.value = false
  }
}

async function insertImages(layout: InsertLayout, files: File[]) {
  imageDialog.value = false
  try {
    const images = await upload(files)
    // A selected block (e.g. the image block inserted just before) would be replaced: insert after it instead.
    const { selection } = e.value.state
    const c = selection instanceof NodeSelection ? chain().setTextSelection(selection.to) : chain()
    if (layout === 'image-left' || layout === 'image-right') {
      c.insertImageText({ side: layout === 'image-left' ? 'left' : 'right', images }).run()
    } else {
      const aspect = layout === 'single' ? 'original' : '4:3'
      c.insertImageBlock({ layout, images: images.map((image) => ({ ...image, aspect })) }).run()
    }
  } catch (error) {
    notifyError(error)
  }
}

function insertFiles(files: File[]): boolean {
  const images = files.filter((file) => file.type.startsWith('image/'))
  if (!images.length) return false
  insertImages(images.length >= 3 ? 'three' : images.length === 2 ? 'two' : 'single', images.slice(0, 3))
  return true
}
</script>

<template>
  <div v-if="editor" class="editor">
    <div class="toolbar" role="toolbar" aria-label="Formatting" @mousedown="keepFocus">
      <div class="toolbar__group">
        <button type="button" class="tb" title="Undo (Ctrl+Z)" aria-label="Undo" :disabled="!e.can().undo()" @click="chain().undo().run()">
          <Icon name="undo" />
        </button>
        <button type="button" class="tb" title="Redo (Ctrl+Shift+Z)" aria-label="Redo" :disabled="!e.can().redo()" @click="chain().redo().run()">
          <Icon name="redo" />
        </button>
      </div>

      <div class="toolbar__group">
        <select
          class="tb-select"
          aria-label="Text style"
          :value="blockType"
          @change="setBlockType(($event.target as HTMLSelectElement).value as BlockType)"
        >
          <option value="paragraph">Text</option>
          <option value="h1">Heading 1</option>
          <option value="h2">Heading 2</option>
          <option value="h3">Heading 3</option>
          <option value="quote">Quote</option>
          <option value="code">Code block</option>
        </select>
      </div>

      <div class="toolbar__group">
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('bold') }" title="Bold (Ctrl+B)" aria-label="Bold" :aria-pressed="e.isActive('bold')" @click="chain().toggleBold().run()">
          <Icon name="bold" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('italic') }" title="Italic (Ctrl+I)" aria-label="Italic" :aria-pressed="e.isActive('italic')" @click="chain().toggleItalic().run()">
          <Icon name="italic" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('underline') }" title="Underline (Ctrl+U)" aria-label="Underline" :aria-pressed="e.isActive('underline')" @click="chain().toggleUnderline().run()">
          <Icon name="underline" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('strike') }" title="Strikethrough" aria-label="Strikethrough" :aria-pressed="e.isActive('strike')" @click="chain().toggleStrike().run()">
          <Icon name="strikethrough" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('code') }" title="Code" aria-label="Code" :aria-pressed="e.isActive('code')" @click="chain().toggleCode().run()">
          <Icon name="code" />
        </button>
        <div ref="colorRoot" class="toolbar__popover-root">
          <button
            type="button"
            class="tb"
            :class="{ 'tb--on': activeTextColor || activeHighlight }"
            title="Text and background color"
            aria-label="Text and background color"
            :aria-expanded="colorMenu.open.value"
            @click="colorMenu.toggle()"
          >
            <Icon name="palette" />
          </button>
          <div v-if="colorMenu.open.value" class="popover colors" role="dialog" aria-label="Colors">
            <div class="menu-heading">Text color</div>
            <div class="colors__grid">
              <button type="button" class="swatch" :class="{ 'swatch--on': !activeTextColor }" title="Default" aria-label="Default text color" @click="setTextColor(null)">
                <span class="swatch__letter">A</span>
              </button>
              <button
                v-for="color in CONTENT_COLORS"
                :key="color"
                type="button"
                class="swatch"
                :class="{ 'swatch--on': activeTextColor === color }"
                :title="COLOR_LABELS[color]"
                :aria-label="`${COLOR_LABELS[color]} text`"
                @click="setTextColor(color)"
              >
                <span class="swatch__letter" :data-text-color="color">A</span>
              </button>
            </div>
            <div class="menu-heading">Background</div>
            <div class="colors__grid">
              <button type="button" class="swatch" :class="{ 'swatch--on': !activeHighlight }" title="None" aria-label="No background" @click="setHighlight(null)">
                <span class="swatch__letter swatch__letter--none">A</span>
              </button>
              <button
                v-for="color in CONTENT_COLORS"
                :key="color"
                type="button"
                class="swatch"
                :class="{ 'swatch--on': activeHighlight === color }"
                :title="COLOR_LABELS[color]"
                :aria-label="`${COLOR_LABELS[color]} background`"
                @click="setHighlight(color)"
              >
                <mark class="swatch__letter" :data-highlight="color">A</mark>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="toolbar__group">
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('bulletList') }" title="Bulleted list" aria-label="Bulleted list" @click="chain().toggleBulletList().run()">
          <Icon name="list" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('orderedList') }" title="Numbered list" aria-label="Numbered list" @click="chain().toggleOrderedList().run()">
          <Icon name="list-ordered" />
        </button>
        <button type="button" class="tb" title="Outdent (Shift+Tab)" aria-label="Outdent" :disabled="!e.can().liftListItem('listItem')" @click="chain().liftListItem('listItem').run()">
          <Icon name="outdent" />
        </button>
        <button type="button" class="tb" title="Indent (Tab)" aria-label="Indent" :disabled="!e.can().sinkListItem('listItem')" @click="chain().sinkListItem('listItem').run()">
          <Icon name="indent" />
        </button>
      </div>

      <div class="toolbar__group">
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive({ textAlign: 'left' }) }" title="Align left" aria-label="Align left" @click="chain().setTextAlign('left').run()">
          <Icon name="align-left" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive({ textAlign: 'center' }) }" title="Center" aria-label="Center" @click="chain().setTextAlign('center').run()">
          <Icon name="align-center" />
        </button>
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive({ textAlign: 'right' }) }" title="Align right" aria-label="Align right" @click="chain().setTextAlign('right').run()">
          <Icon name="align-right" />
        </button>
      </div>

      <div class="toolbar__group">
        <button type="button" class="tb" :class="{ 'tb--on': e.isActive('link') }" title="Link" aria-label="Link" @click="openLink">
          <Icon name="link" />
        </button>
        <div ref="tableRoot" class="toolbar__popover-root">
          <button type="button" class="tb" title="Table" aria-label="Insert table" :aria-expanded="tableMenu.open.value" @click="tableMenu.toggle()">
            <Icon name="table" />
          </button>
          <div v-if="tableMenu.open.value" class="popover table-picker" role="dialog" aria-label="Table size">
            <div class="table-picker__grid" @mouseleave="hover = { rows: 0, cols: 0 }">
              <template v-for="row in GRID" :key="row">
                <button
                  v-for="col in GRID"
                  :key="col"
                  type="button"
                  class="table-picker__cell"
                  :class="{ 'table-picker__cell--on': row <= hover.rows && col <= hover.cols }"
                  :aria-label="`${row} × ${col}`"
                  @mouseenter="hover = { rows: row, cols: col }"
                  @focus="hover = { rows: row, cols: col }"
                  @click="insertTable(row, col)"
                />
              </template>
            </div>
            <p class="table-picker__size">{{ hover.rows ? `${hover.rows} × ${hover.cols}` : 'Rows × columns' }}</p>
          </div>
        </div>
        <button type="button" class="tb" title="Images" aria-label="Insert images" :disabled="uploading" @click="imageDialog = true">
          <Icon name="image" />
        </button>
        <button type="button" class="tb" title="Formula (LaTeX)" aria-label="Insert formula" @click="openMath">
          <Icon name="sigma" />
        </button>
        <button type="button" class="tb" title="Divider" aria-label="Insert divider" @click="chain().setHorizontalRule().run()">
          <Icon name="minus" />
        </button>
      </div>
      <span v-if="uploading" class="toolbar__status">Uploading…</span>
    </div>

    <div v-if="inTable" class="toolbar toolbar--table" role="toolbar" aria-label="Table" @mousedown="keepFocus">
      <span class="toolbar__label">Table</span>
      <div class="toolbar__group">
        <button type="button" class="tb tb--text" @click="chain().addRowBefore().run()"><Icon name="row-insert" />Row above</button>
        <button type="button" class="tb tb--text" @click="chain().addRowAfter().run()">Row below</button>
        <button type="button" class="tb tb--text" @click="chain().deleteRow().run()"><Icon name="trash" />Row</button>
      </div>
      <div class="toolbar__group">
        <button type="button" class="tb tb--text" @click="chain().addColumnBefore().run()"><Icon name="column-insert" />Column left</button>
        <button type="button" class="tb tb--text" @click="chain().addColumnAfter().run()">Column right</button>
        <button type="button" class="tb tb--text" @click="chain().deleteColumn().run()"><Icon name="trash" />Column</button>
      </div>
      <div class="toolbar__group">
        <button type="button" class="tb tb--text" :disabled="!e.can().mergeCells()" @click="chain().mergeCells().run()"><Icon name="cells-merge" />Merge</button>
        <button type="button" class="tb tb--text" :disabled="!e.can().splitCell()" @click="chain().splitCell().run()"><Icon name="cells-split" />Split</button>
        <button type="button" class="tb tb--text" @click="chain().toggleHeaderRow().run()">Header row</button>
        <button type="button" class="tb tb--text" @click="chain().toggleHeaderColumn().run()">Header column</button>
      </div>
      <div class="toolbar__group">
        <button type="button" class="tb tb--text" @click="chain().deleteTable().run()"><Icon name="trash" />Table</button>
      </div>
      <span class="toolbar__hint">Drag cell edges to resize columns and rows.</span>
    </div>

    <EditorContent :editor="editor" class="wiki-content wiki-content--editing" />

    <MathDialog
      v-if="mathDialog"
      :kind="mathDialog.kind"
      :latex="mathDialog.latex"
      :editing="mathDialog.pos !== null"
      @save="saveMath"
      @remove="removeMath"
      @close="mathDialog = null"
    />
    <LinkDialog
      v-if="linkDialog"
      :wiki-id="wikiId"
      :href="linkDialog.href"
      :needs-text="linkDialog.needsText"
      @save="saveLink"
      @remove="removeLink"
      @close="linkDialog = null"
    />
    <ImageInsertDialog v-if="imageDialog" @insert="insertImages" @close="imageDialog = false" />
  </div>
</template>

<style scoped>
.editor {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.toolbar {
  position: sticky;
  top: 108px;
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 2px;
  padding: 6px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.toolbar--table {
  top: auto;
  position: static;
  background: var(--color-bg);
}

.toolbar__group {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-right: 6px;
  margin-right: 4px;
  border-right: 1px solid var(--color-border);
}

.toolbar__group:last-of-type {
  border-right: none;
}

.toolbar__label {
  padding: 0 8px 0 4px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.toolbar__hint,
.toolbar__status {
  padding: 0 6px;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.toolbar__popover-root {
  position: relative;
}

.tb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  font: inherit;
  font-size: 0.8125rem;
  color: var(--color-text-primary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.tb:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.tb:disabled {
  opacity: 0.4;
  cursor: default;
}

.tb:focus-visible,
.tb-select:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: -1px;
}

.tb--on {
  color: var(--color-accent);
  background: var(--color-surface-active);
}

.tb .icon {
  width: 17px;
  height: 17px;
}

.tb--text .icon {
  width: 15px;
  height: 15px;
}

.tb-select {
  height: 32px;
  padding: 0 6px;
  font: inherit;
  font-size: 0.8125rem;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.colors {
  top: calc(100% + 4px);
  left: 0;
  width: 236px;
  padding: 4px 8px 8px;
}

.colors__grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 4px;
  padding: 4px 0;
}

.swatch {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0;
  font: inherit;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.swatch:hover {
  border-color: var(--color-text-secondary);
}

.swatch--on {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}

.swatch__letter {
  display: inline-block;
  min-width: 22px;
  padding: 1px 4px;
  font-weight: 600;
  line-height: 1.4;
}

/* No color of its own: the letter shows the color via content.css ([data-text-color], [data-highlight]). */
.swatch__letter:not([data-text-color]) {
  color: inherit;
}

.swatch__letter:not([data-highlight]) {
  background: transparent;
}

.table-picker {
  top: calc(100% + 4px);
  left: 0;
  min-width: 0;
  padding: 8px;
}

.table-picker__grid {
  display: grid;
  grid-template-columns: repeat(8, 18px);
  gap: 3px;
}

.table-picker__cell {
  width: 18px;
  height: 18px;
  padding: 0;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.table-picker__cell--on {
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.table-picker__size {
  margin-top: 6px;
  font-size: 0.8125rem;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
