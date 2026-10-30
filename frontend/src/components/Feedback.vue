<script setup lang="ts">
import { Icon } from '@teeetime/teaz-pro-ui'
import { confirmation, dismiss, toasts } from '@/feedback'
import Modal from '@/components/Modal.vue'
</script>

<template>
  <div class="toasts" aria-live="polite">
    <div v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast--${toast.kind}`" role="status">
      <span class="toast__mark" aria-hidden="true" />
      <span class="toast__text">{{ toast.text }}</span>
      <button type="button" class="toast__close" aria-label="Dismiss" @click="dismiss(toast.id)">
        <Icon name="x" />
      </button>
    </div>
  </div>

  <Modal v-if="confirmation" :title="confirmation.title" narrow @close="confirmation.resolve(false)">
    <p class="confirm__text">{{ confirmation.text }}</p>
    <template #actions>
      <button type="button" class="btn" @click="confirmation.resolve(false)">Cancel</button>
      <button
        type="button"
        class="btn"
        :class="confirmation.danger ? 'btn--danger' : 'btn--primary'"
        @click="confirmation.resolve(true)"
      >
        {{ confirmation.confirmLabel }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.toasts {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(420px, calc(100vw - 32px));
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 10px 10px 12px;
  font-size: 0.9rem;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: 0 4px 12px var(--color-shadow-strong);
}

.toast__mark {
  flex-shrink: 0;
  width: 4px;
  align-self: stretch;
  background: var(--color-accent);
}

.toast--error .toast__mark {
  background: var(--color-status-critical);
}

.toast__text {
  flex: 1;
  padding-top: 2px;
}

.toast__close {
  display: flex;
  padding: 2px;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.toast__close .icon {
  width: 16px;
  height: 16px;
}

.confirm__text {
  color: var(--color-text-secondary);
  line-height: 1.5;
}
</style>
