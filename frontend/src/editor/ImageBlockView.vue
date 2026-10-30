<script setup lang="ts">
import { computed, ref } from 'vue'
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3'
import { Icon } from '@teeetime/teaz-pro-ui'
import { ACCEPTED_IMAGES, uploadImage } from '@/files'
import { notifyError } from '@/feedback'
import ImageFigure from './ImageFigure.vue'
import ImageSettingsDialog from './ImageSettingsDialog.vue'
import {
  LAYOUT_COUNT,
  newImage,
  type Align,
  type BlockLayout,
  type ImageBlockAttrs,
  type ImageItem,
  type ImageOptions,
  type SingleWidth,
} from './images'

/* One image, or two or three side by side. Controls only while editing. */
const props = defineProps(nodeViewProps)

const attrs = computed(() => props.node.attrs as ImageBlockAttrs)
const editable = computed(() => props.editor.isEditable)
const count = computed(() => LAYOUT_COUNT[attrs.value.layout])
const slots = computed(() => Array.from({ length: count.value }, (_, i) => attrs.value.images[i] ?? null))
const wikiId = computed(() => (props.extension.options as ImageOptions).wikiId)

const LAYOUTS: { value: BlockLayout; icon: string; label: string }[] = [
  { value: 'single', icon: 'image', label: 'One image' },
  { value: 'two', icon: 'columns-2', label: 'Two side by side' },
  { value: 'three', icon: 'columns-3', label: 'Three side by side' },
]
const WIDTHS: SingleWidth[] = [25, 50, 75, 100]
const ALIGNS: { value: Align; icon: string }[] = [
  { value: 'left', icon: 'align-left' },
  { value: 'center', icon: 'align-center' },
  { value: 'right', icon: 'align-right' },
]

const editing = ref<number | null>(null)
const uploading = ref<number | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
let uploadSlot = 0

function setImages(images: ImageItem[]) {
  props.updateAttributes({ images })
}

function setImage(index: number, item: ImageItem) {
  const images = [...attrs.value.images]
  images[index] = item
  setImages(images)
}

function setLayout(layout: BlockLayout) {
  // Side by side, images line up better in a common shape.
  const images = attrs.value.images
    .slice(0, LAYOUT_COUNT[layout])
    .map((image) => (layout !== 'single' && image.aspect === 'original' && !image.crop ? { ...image, aspect: '4:3' as const } : image))
  props.updateAttributes({ layout, images })
}

function pick(index: number) {
  uploadSlot = index
  fileInput.value?.click()
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const index = uploadSlot
  uploading.value = index
  try {
    const uploaded = await uploadImage(wikiId.value, file)
    const previous = attrs.value.images[index]
    const aspect = previous?.aspect ?? (attrs.value.layout === 'single' ? 'original' : '4:3')
    setImage(index, { ...newImage(uploaded.id, aspect), caption: previous?.caption ?? '', alt: previous?.alt ?? '' })
  } catch (error) {
    notifyError(error)
  } finally {
    uploading.value = null
  }
}

function remove(index: number) {
  const images = attrs.value.images.filter((_, i) => i !== index)
  if (!images.length) return props.deleteNode()
  const layout: BlockLayout = images.length >= 3 ? 'three' : images.length === 2 ? 'two' : 'single'
  props.updateAttributes({ images, layout: count.value > images.length ? layout : attrs.value.layout })
}
</script>

<template>
  <NodeViewWrapper
    class="image-block"
    :class="[`image-block--${attrs.layout}`, `image-block--align-${attrs.align}`, { 'image-block--editable': editable, 'image-block--selected': selected }]"
    data-drag-handle
  >
    <div v-if="editable" class="image-tools" contenteditable="false">
      <div class="image-tools__group">
        <button
          v-for="layout in LAYOUTS"
          :key="layout.value"
          type="button"
          class="image-tools__btn"
          :class="{ 'image-tools__btn--on': attrs.layout === layout.value }"
          :title="layout.label"
          :aria-label="layout.label"
          @click="setLayout(layout.value)"
        >
          <Icon :name="layout.icon" />
        </button>
      </div>
      <template v-if="attrs.layout === 'single'">
        <div class="image-tools__group">
          <button
            v-for="width in WIDTHS"
            :key="width"
            type="button"
            class="image-tools__btn image-tools__btn--text"
            :class="{ 'image-tools__btn--on': attrs.width === width }"
            :title="`${width} % wide`"
            @click="updateAttributes({ width })"
          >
            {{ width }}%
          </button>
        </div>
        <div v-if="attrs.width < 100" class="image-tools__group">
          <button
            v-for="align in ALIGNS"
            :key="align.value"
            type="button"
            class="image-tools__btn"
            :class="{ 'image-tools__btn--on': attrs.align === align.value }"
            :aria-label="`Align ${align.value}`"
            :title="`Align ${align.value}`"
            @click="updateAttributes({ align: align.value })"
          >
            <Icon :name="align.icon" />
          </button>
        </div>
      </template>
      <div class="image-tools__group">
        <button type="button" class="image-tools__btn" title="Remove images" aria-label="Remove images" @click="deleteNode()">
          <Icon name="trash" />
        </button>
      </div>
    </div>

    <div class="image-block__row" :style="attrs.layout === 'single' ? { width: `${attrs.width}%` } : undefined">
      <figure v-for="(item, index) in slots" :key="index" class="image-block__figure">
        <template v-if="item">
          <div class="image-block__frame">
            <ImageFigure :item="item" />
            <div v-if="editable" class="image-block__overlay" contenteditable="false">
              <button type="button" class="image-tools__btn" title="Aspect ratio, crop, description" aria-label="Edit image" @click="editing = index">
                <Icon name="crop" />
              </button>
              <button type="button" class="image-tools__btn" title="Replace image" aria-label="Replace image" @click="pick(index)">
                <Icon name="upload" />
              </button>
              <button v-if="count > 1" type="button" class="image-tools__btn" title="Remove this image" aria-label="Remove this image" @click="remove(index)">
                <Icon name="x" />
              </button>
            </div>
            <div v-if="uploading === index" class="image-block__busy">Uploading…</div>
          </div>
          <input
            v-if="editable"
            class="image-block__caption-input"
            :value="item.caption"
            placeholder="Caption (optional)"
            maxlength="300"
            @input="setImage(index, { ...item, caption: ($event.target as HTMLInputElement).value })"
          />
          <figcaption v-else-if="item.caption" class="image-block__caption">{{ item.caption }}</figcaption>
        </template>
        <button
          v-else-if="editable"
          type="button"
          class="image-block__empty"
          contenteditable="false"
          :disabled="uploading !== null"
          @click="pick(index)"
        >
          <Icon name="image" />
          {{ uploading === index ? 'Uploading…' : 'Add image' }}
        </button>
      </figure>
    </div>

    <input v-if="editable" ref="fileInput" type="file" :accept="ACCEPTED_IMAGES" hidden @change="onFile" />
    <ImageSettingsDialog
      v-if="editing !== null && attrs.images[editing]"
      :item="attrs.images[editing]!"
      @save="(item) => { setImage(editing!, item); editing = null }"
      @close="editing = null"
    />
  </NodeViewWrapper>
</template>
