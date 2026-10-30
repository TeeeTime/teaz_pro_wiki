<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { useFileUrl } from '@/files'
import Modal from '@/components/Modal.vue'
import { ASPECTS, type Aspect, type Crop, type ImageItem } from './images'

/*
 * Aspect ratio, visible part and alt text of one image. "Original" allows any
 * crop; the other ratios lock the crop box to them. Nothing is cut from the
 * file: the crop is stored as fractions and applied when shown.
 */
const props = defineProps<{ item: ImageItem }>()
const emit = defineEmits<{ save: [item: ImageItem]; close: [] }>()

const LABELS: Record<Aspect, string> = {
  original: 'Original',
  '16:9': '16:9',
  '4:3': '4:3',
  '1:1': 'Square',
  '3:4': '3:4',
}

const url = useFileUrl(() => props.item.fileId)
const image = ref<HTMLImageElement | null>(null)
const aspect = ref<Aspect>(props.item.aspect)
const alt = ref(props.item.alt)
let cropper: Cropper | null = null

function ratioOf(value: Aspect): number {
  if (value === 'original') return NaN // free
  const [w, h] = value.split(':').map(Number)
  return w! / h!
}

function start() {
  if (!image.value) return
  cropper?.destroy()
  const crop = props.item.crop
  cropper = new Cropper(image.value, {
    viewMode: 1,
    autoCropArea: 1,
    aspectRatio: ratioOf(aspect.value),
    zoomable: false,
    background: false,
    ready() {
      if (!crop || !cropper) return
      const { naturalWidth, naturalHeight } = cropper.getImageData()
      cropper.setData({
        x: crop.x * naturalWidth,
        y: crop.y * naturalHeight,
        width: crop.w * naturalWidth,
        height: crop.h * naturalHeight,
      })
    },
  })
}

watch(url, async (value) => {
  if (!value) return
  await nextTick()
  if (image.value?.complete) start()
})

function setAspect(value: Aspect) {
  aspect.value = value
  cropper?.setAspectRatio(ratioOf(value))
}

function reset() {
  cropper?.reset()
}

function save() {
  let crop: Crop | null = null
  if (cropper) {
    const data = cropper.getData(true)
    const { naturalWidth, naturalHeight } = cropper.getImageData()
    const whole = data.x <= 1 && data.y <= 1 && data.width >= naturalWidth - 2 && data.height >= naturalHeight - 2
    if (!(whole && aspect.value === 'original')) {
      crop = {
        x: data.x / naturalWidth,
        y: data.y / naturalHeight,
        w: data.width / naturalWidth,
        h: data.height / naturalHeight,
        ratio: data.width / data.height,
      }
    }
  }
  emit('save', { ...props.item, aspect: aspect.value, crop, alt: alt.value.trim() })
}

onBeforeUnmount(() => cropper?.destroy())
</script>

<template>
  <Modal title="Image" wide @close="emit('close')">
    <div class="settings">
      <div class="settings__crop">
        <img v-if="url" ref="image" :src="url" alt="" class="settings__image" @load="start" />
      </div>
      <div class="settings__side">
        <div>
          <span class="label">Aspect ratio</span>
          <div class="settings__aspects" role="radiogroup" aria-label="Aspect ratio">
            <button
              v-for="value in ASPECTS"
              :key="value"
              type="button"
              class="btn btn--small"
              :class="{ 'btn--primary': aspect === value }"
              role="radio"
              :aria-checked="aspect === value"
              @click="setAspect(value)"
            >
              {{ LABELS[value] }}
            </button>
          </div>
          <p class="hint">Drag the frame to choose the visible part.</p>
          <button type="button" class="btn btn--small settings__reset" @click="reset">Show whole image</button>
        </div>
        <div>
          <label class="label" for="image-alt">Description for screen readers</label>
          <input id="image-alt" v-model="alt" class="field" maxlength="300" placeholder="What the image shows" />
        </div>
      </div>
    </div>
    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn--primary" @click="save">Apply</button>
    </template>
  </Modal>
</template>

<style scoped>
.settings {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 20px;
}

.settings__crop {
  min-height: 240px;
  max-height: 60vh;
  background: var(--color-bg);
}

.settings__image {
  display: block;
  max-width: 100%;
  max-height: 60vh;
}

.settings__side {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings__aspects {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.settings__reset {
  margin-top: 8px;
}

/* Cropper.js draws in its own blue; use the theme's accent instead. */
.settings__crop :deep(.cropper-view-box) {
  outline-color: var(--color-accent);
}

.settings__crop :deep(.cropper-line),
.settings__crop :deep(.cropper-point) {
  background-color: var(--color-accent);
}

@media (max-width: 640px) {
  .settings {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
