<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'

/* A dialog over the page: Escape, the close button and a click beside it emit "close". */
defineProps<{ title: string; narrow?: boolean; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  // Focus the first field, so typing works right away.
  const field = panel.value?.querySelector<HTMLElement>('input, textarea, select, button.btn--primary')
  field?.focus()
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal" @mousedown.self="emit('close')">
      <div
        ref="panel"
        class="modal__panel"
        :class="{ 'modal__panel--narrow': narrow, 'modal__panel--wide': wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="modal__header">
          <h2 class="modal__title">{{ title }}</h2>
          <button type="button" class="icon-btn" aria-label="Close" @click="emit('close')">
            <Icon name="x" />
          </button>
        </header>
        <div class="modal__body">
          <slot />
        </div>
        <footer v-if="$slots.actions" class="modal__actions">
          <slot name="actions" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-shadow-strong);
}

.modal__panel {
  display: flex;
  flex-direction: column;
  width: min(560px, 100%);
  max-height: calc(100vh - 32px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: 0 8px 32px var(--color-shadow-strong);
}

.modal__panel--narrow {
  width: min(420px, 100%);
}

.modal__panel--wide {
  width: min(900px, 100%);
}

.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 12px 12px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal__title {
  font-size: 1rem;
  font-weight: 600;
}

.modal__body {
  padding: 20px;
  overflow: auto;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}
</style>
