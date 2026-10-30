<script setup lang="ts">
import { computed } from 'vue'
import { useFileUrl } from '@/files'

/* The wiki's logo, or its first letter on the accent color when it has none. */
const props = defineProps<{ name: string; fileId: string | null; size?: number }>()

const url = useFileUrl(() => props.fileId)
const letter = computed(() => props.name.trim().charAt(0).toUpperCase() || 'W')
const style = computed(() => ({
  width: `${props.size ?? 28}px`,
  height: `${props.size ?? 28}px`,
  fontSize: `${Math.round((props.size ?? 28) * 0.5)}px`,
}))
</script>

<template>
  <img v-if="fileId && url" class="logo" :src="url" alt="" :style="style" />
  <span v-else class="logo logo--letter" :style="style" aria-hidden="true">{{ letter }}</span>
</template>

<style scoped>
.logo {
  display: inline-flex;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.logo--letter {
  align-items: center;
  justify-content: center;
  font-weight: 600;
  line-height: 1;
  color: var(--color-text-on-accent);
  background: var(--color-accent);
}
</style>
