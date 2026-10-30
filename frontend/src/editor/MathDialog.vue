<script setup lang="ts">
import { computed, ref } from 'vue'
import katex from 'katex'
import Modal from '@/components/Modal.vue'
import type { MathKind } from './extensions'

/* Writes or changes a LaTeX formula, with a live preview. */
const props = defineProps<{ kind: MathKind; latex: string; editing: boolean }>()
const emit = defineEmits<{ save: [kind: MathKind, latex: string]; remove: []; close: [] }>()

const kind = ref<MathKind>(props.kind)
const latex = ref(props.latex)

const preview = computed(() =>
  katex.renderToString(latex.value || '\\text{…}', {
    throwOnError: false,
    displayMode: kind.value === 'block',
  }),
)
</script>

<template>
  <Modal :title="editing ? 'Edit formula' : 'Insert formula'" @close="emit('close')">
    <div v-if="!editing" class="math__kind" role="radiogroup" aria-label="Kind">
      <button
        type="button"
        class="btn btn--small"
        :class="{ 'btn--primary': kind === 'inline' }"
        role="radio"
        :aria-checked="kind === 'inline'"
        @click="kind = 'inline'"
      >
        In the text
      </button>
      <button
        type="button"
        class="btn btn--small"
        :class="{ 'btn--primary': kind === 'block' }"
        role="radio"
        :aria-checked="kind === 'block'"
        @click="kind = 'block'"
      >
        On its own line
      </button>
    </div>
    <label class="label" for="math-latex">LaTeX</label>
    <textarea
      id="math-latex"
      v-model="latex"
      class="field math__input"
      spellcheck="false"
      placeholder="e.g. \frac{a}{b} = \sqrt{x^2 + y^2}"
      @keydown.ctrl.enter="latex.trim() && emit('save', kind, latex.trim())"
    />
    <span class="label math__preview-label">Preview</span>
    <!-- KaTeX output of the user's own formula, rendered with throwOnError: false. -->
    <div class="math__preview" v-html="preview" />
    <template #actions>
      <button v-if="editing" type="button" class="btn btn--danger math__remove" @click="emit('remove')">Remove</button>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn--primary" :disabled="!latex.trim()" @click="emit('save', kind, latex.trim())">
        {{ editing ? 'Apply' : 'Insert' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.math__kind {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
}

.math__input {
  min-height: 90px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.math__preview-label {
  margin-top: 16px;
}

.math__preview {
  min-height: 56px;
  padding: 12px;
  overflow-x: auto;
  color: var(--color-text-primary);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
}

.math__remove {
  margin-right: auto;
}
</style>
