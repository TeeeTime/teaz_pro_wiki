<script setup lang="ts">
import { computed } from 'vue'
import { useFileUrl } from '@/files'
import type { ImageItem } from './images'

/*
 * One image with its aspect ratio and crop applied (the file itself is never
 * changed): with a crop, the frame has the crop's ratio and the image is
 * scaled and shifted so exactly the cropped part fills it; with only an
 * aspect ratio, the image covers the frame, centered.
 */
const props = defineProps<{ item: ImageItem }>()

const url = useFileUrl(() => props.item.fileId)

const frameStyle = computed(() => {
  const { crop, aspect } = props.item
  if (crop) return { aspectRatio: String(crop.ratio) }
  if (aspect !== 'original') return { aspectRatio: aspect.replace(':', ' / ') }
  return {}
})

const imageStyle = computed(() => {
  const { crop, aspect } = props.item
  if (crop) {
    return {
      position: 'absolute' as const,
      width: `${100 / crop.w}%`,
      height: `${100 / crop.h}%`,
      left: `${(-crop.x / crop.w) * 100}%`,
      top: `${(-crop.y / crop.h) * 100}%`,
      maxWidth: 'none',
    }
  }
  if (aspect !== 'original') return { width: '100%', height: '100%', objectFit: 'cover' as const }
  return { width: '100%', height: 'auto' }
})

const framed = computed(() => props.item.crop !== null || props.item.aspect !== 'original')
</script>

<template>
  <div class="frame" :class="{ 'frame--framed': framed, 'frame--loading': !url }" :style="frameStyle">
    <img v-if="url" :src="url" :alt="item.alt" :style="imageStyle" draggable="false" />
  </div>
</template>

<style scoped>
.frame {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--color-surface-hover);
}

.frame img {
  display: block;
}

.frame--loading:not(.frame--framed) {
  aspect-ratio: 4 / 3;
}
</style>
