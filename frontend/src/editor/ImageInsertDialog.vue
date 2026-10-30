<script setup lang="ts">
import { ref } from 'vue'
import { ACCEPTED_IMAGES } from '@/files'
import Modal from '@/components/Modal.vue'
import type { InsertLayout } from './images'

/*
 * First the layout, then the files. Pictograms show what each layout looks
 * like: boxes are images, lines are text.
 */

const emit = defineEmits<{ insert: [layout: InsertLayout, files: File[]]; close: [] }>()

const LAYOUTS: { value: InsertLayout; label: string; images: number }[] = [
  { value: 'single', label: 'One image', images: 1 },
  { value: 'image-left', label: 'Image left, text right', images: 1 },
  { value: 'image-right', label: 'Text left, image right', images: 1 },
  { value: 'two', label: 'Two side by side', images: 2 },
  { value: 'three', label: 'Three side by side', images: 3 },
]

const chosen = ref<(typeof LAYOUTS)[number] | null>(null)
const input = ref<HTMLInputElement | null>(null)

function choose(layout: (typeof LAYOUTS)[number]) {
  chosen.value = layout
  input.value!.multiple = layout.images > 1
  input.value!.click()
}

function onFiles(event: Event) {
  const target = event.target as HTMLInputElement
  const files = [...(target.files ?? [])]
  target.value = ''
  if (!chosen.value || !files.length) return
  emit('insert', chosen.value.value, files.slice(0, chosen.value.images))
}
</script>

<template>
  <Modal title="Insert images" @close="emit('close')">
    <p class="muted insert__hint">
      Choose a layout, then the image files. Size, aspect ratio and crop can be changed afterwards.
    </p>
    <div class="insert__grid">
      <button v-for="layout in LAYOUTS" :key="layout.value" type="button" class="insert__option" @click="choose(layout)">
        <span class="picto" :class="`picto--${layout.value}`" aria-hidden="true">
          <template v-if="layout.value === 'single'"><span class="picto__img picto__img--wide" /></template>
          <template v-else-if="layout.value === 'image-left'">
            <span class="picto__img" /><span class="picto__text"><i /><i /><i /><i /></span>
          </template>
          <template v-else-if="layout.value === 'image-right'">
            <span class="picto__text"><i /><i /><i /><i /></span><span class="picto__img" />
          </template>
          <template v-else-if="layout.value === 'two'"><span class="picto__img" /><span class="picto__img" /></template>
          <template v-else><span class="picto__img" /><span class="picto__img" /><span class="picto__img" /></template>
        </span>
        <span class="insert__label">{{ layout.label }}</span>
      </button>
    </div>
    <input ref="input" type="file" :accept="ACCEPTED_IMAGES" hidden @change="onFiles" />
  </Modal>
</template>

<style scoped>
.insert__hint {
  margin-bottom: 16px;
  font-size: 0.875rem;
}

.insert__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.insert__option {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 10px;
  font: inherit;
  color: var(--color-text-primary);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.insert__option:hover,
.insert__option:focus-visible {
  border-color: var(--color-accent);
  outline: none;
}

.insert__label {
  font-size: 0.8125rem;
}

.picto {
  display: flex;
  gap: 6px;
  height: 64px;
  padding: 8px;
  background: var(--color-surface);
}

.picto__img {
  flex: 1;
  background: var(--color-text-secondary);
  opacity: 0.45;
}

.picto--single {
  justify-content: center;
}

.picto__img--wide {
  flex: 0 0 70%;
}

.picto__text {
  display: flex;
  flex: 2;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.picto__text i {
  display: block;
  height: 4px;
  background: var(--color-border);
}

.picto--image-left .picto__img,
.picto--image-right .picto__img {
  flex: 1;
}
</style>
