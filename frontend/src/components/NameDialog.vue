<script setup lang="ts">
import { ref } from 'vue'
import Modal from '@/components/Modal.vue'

/* Asks for a name (new category, rename). Emits the trimmed name. */
const props = defineProps<{ title: string; label: string; initial?: string; submitLabel: string; hint?: string }>()
const emit = defineEmits<{ submit: [name: string]; close: [] }>()

const name = ref(props.initial ?? '')
</script>

<template>
  <Modal :title="title" narrow @close="emit('close')">
    <form id="name-dialog" @submit.prevent="name.trim() && emit('submit', name.trim())">
      <label class="label" for="name-dialog-input">{{ label }}</label>
      <input id="name-dialog-input" v-model="name" class="field" maxlength="120" autocomplete="off" />
      <p v-if="hint" class="hint">{{ hint }}</p>
    </form>
    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="submit" form="name-dialog" class="btn btn--primary" :disabled="!name.trim()">
        {{ submitLabel }}
      </button>
    </template>
  </Modal>
</template>
