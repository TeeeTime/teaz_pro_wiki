<script setup lang="ts">
import { computed, ref } from 'vue'
import { NodeViewContent, NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3'
import { Icon } from '@teeetime/teaz-pro-ui'
import { ACCEPTED_IMAGES, uploadImage } from '@/files'
import { notifyError } from '@/feedback'
import ImageFigure from './ImageFigure.vue'
import ImageSettingsDialog from './ImageSettingsDialog.vue'
import { newImage, type ImageItem, type ImageOptions, type ImageTextAttrs } from './images'

/* An image on one side, editable text on the other. Stacks on narrow screens. */
const props = defineProps(nodeViewProps)

const attrs = computed(() => props.node.attrs as ImageTextAttrs)
const editable = computed(() => props.editor.isEditable)
const item = computed(() => attrs.value.images[0] ?? null)

const editing = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function setItem(next: ImageItem) {
  props.updateAttributes({ images: [next] })
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const uploaded = await uploadImage((props.extension.options as ImageOptions).wikiId, file)
    setItem({ ...newImage(uploaded.id, item.value?.aspect ?? 'original'), caption: item.value?.caption ?? '' })
  } catch (error) {
    notifyError(error)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <NodeViewWrapper
    class="image-text"
    :class="[`image-text--${attrs.side}`, `image-text--${attrs.share}`, { 'image-text--editable': editable }]"
  >
    <div v-if="editable" class="image-tools" contenteditable="false">
      <div class="image-tools__group">
        <button
          type="button"
          class="image-tools__btn"
          :class="{ 'image-tools__btn--on': attrs.side === 'left' }"
          title="Image left, text right"
          aria-label="Image left, text right"
          @click="updateAttributes({ side: 'left' })"
        >
          <Icon name="panel-left" />
        </button>
        <button
          type="button"
          class="image-tools__btn"
          :class="{ 'image-tools__btn--on': attrs.side === 'right' }"
          title="Text left, image right"
          aria-label="Text left, image right"
          @click="updateAttributes({ side: 'right' })"
        >
          <Icon name="panel-right" />
        </button>
      </div>
      <div class="image-tools__group">
        <button
          type="button"
          class="image-tools__btn image-tools__btn--text"
          :class="{ 'image-tools__btn--on': attrs.share === 'third' }"
          title="Image takes a third"
          @click="updateAttributes({ share: 'third' })"
        >
          1/3
        </button>
        <button
          type="button"
          class="image-tools__btn image-tools__btn--text"
          :class="{ 'image-tools__btn--on': attrs.share === 'half' }"
          title="Image takes half"
          @click="updateAttributes({ share: 'half' })"
        >
          1/2
        </button>
      </div>
      <div class="image-tools__group">
        <button type="button" class="image-tools__btn" title="Remove image and text" aria-label="Remove image and text" @click="deleteNode()">
          <Icon name="trash" />
        </button>
      </div>
    </div>

    <div class="image-text__grid">
      <figure class="image-text__figure" contenteditable="false">
        <template v-if="item">
          <div class="image-block__frame">
            <ImageFigure :item="item" />
            <div v-if="editable" class="image-block__overlay">
              <button type="button" class="image-tools__btn" title="Aspect ratio, crop, description" aria-label="Edit image" @click="editing = true">
                <Icon name="crop" />
              </button>
              <button type="button" class="image-tools__btn" title="Replace image" aria-label="Replace image" @click="fileInput?.click()">
                <Icon name="upload" />
              </button>
            </div>
            <div v-if="uploading" class="image-block__busy">Uploading…</div>
          </div>
          <input
            v-if="editable"
            class="image-block__caption-input"
            :value="item.caption"
            placeholder="Caption (optional)"
            maxlength="300"
            @input="setItem({ ...item, caption: ($event.target as HTMLInputElement).value })"
          />
          <figcaption v-else-if="item.caption" class="image-block__caption">{{ item.caption }}</figcaption>
        </template>
        <button v-else-if="editable" type="button" class="image-block__empty" @click="fileInput?.click()">
          <Icon name="image" />
          Add image
        </button>
      </figure>
      <NodeViewContent class="image-text__text" />
    </div>

    <input v-if="editable" ref="fileInput" type="file" :accept="ACCEPTED_IMAGES" hidden @change="onFile" />
    <ImageSettingsDialog
      v-if="editing && item"
      :item="item"
      @save="(next) => { setItem(next); editing = false }"
      @close="editing = false"
    />
  </NodeViewWrapper>
</template>
