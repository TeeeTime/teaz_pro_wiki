<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { ACCEPTED_IMAGES } from '@/files'
import Modal from '@/components/Modal.vue'

/* Picks an image file and the square part of it that becomes the logo (256 px PNG). */
const emit = defineEmits<{ save: [file: File]; close: [] }>()

const SIZE = 256
const url = ref<string | null>(null)
const image = ref<HTMLImageElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
let cropper: Cropper | null = null

async function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = URL.createObjectURL(file)
  await nextTick()
}

function start() {
  cropper?.destroy()
  if (image.value) cropper = new Cropper(image.value, { aspectRatio: 1, viewMode: 1, autoCropArea: 1, zoomable: false, background: false })
}

function save() {
  cropper?.getCroppedCanvas({ width: SIZE, height: SIZE }).toBlob((blob) => {
    if (blob) emit('save', new File([blob], 'logo.png', { type: 'image/png' }))
  }, 'image/png')
}

onBeforeUnmount(() => {
  cropper?.destroy()
  if (url.value) URL.revokeObjectURL(url.value)
})
</script>

<template>
  <Modal title="Logo" @close="emit('close')">
    <div v-if="url" class="logo-crop">
      <img ref="image" :src="url" alt="" class="logo-crop__image" @load="start" />
    </div>
    <p class="hint">{{ url ? 'Drag the square to choose the part that shows.' : 'Choose an image; you can pick a square part of it next.' }}</p>
    <input ref="input" type="file" :accept="ACCEPTED_IMAGES" hidden @change="onFile" />
    <template #actions>
      <button type="button" class="btn" @click="input?.click()">{{ url ? 'Other image' : 'Choose image' }}</button>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn--primary" :disabled="!url" @click="save">Use as logo</button>
    </template>
  </Modal>
</template>

<style scoped>
.logo-crop {
  max-height: 55vh;
  background: var(--color-bg);
}

.logo-crop__image {
  display: block;
  max-width: 100%;
  max-height: 55vh;
}

.logo-crop :deep(.cropper-view-box) {
  outline-color: var(--color-accent);
}

.logo-crop :deep(.cropper-line),
.logo-crop :deep(.cropper-point) {
  background-color: var(--color-accent);
}
</style>
