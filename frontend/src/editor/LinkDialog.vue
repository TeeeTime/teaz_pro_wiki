<script setup lang="ts">
import { ref, watch } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { nodePath } from '@/paths'
import type { SearchHit } from '@/types'
import Modal from '@/components/Modal.vue'

/* A link to a web address or to a page of this wiki (found by search). */
const props = defineProps<{ wikiId: string; href: string; needsText: boolean }>()
const emit = defineEmits<{ save: [href: string, text: string]; remove: []; close: [] }>()

const href = ref(props.href)
const text = ref('')
const query = ref('')
const hits = ref<SearchHit[]>([])
let timer: ReturnType<typeof setTimeout> | undefined

watch(query, (q) => {
  clearTimeout(timer)
  if (!q.trim()) {
    hits.value = []
    return
  }
  timer = setTimeout(async () => {
    try {
      hits.value = (await api.get<SearchHit[]>(`/api/wikis/${props.wikiId}/search?q=${encodeURIComponent(q.trim())}`)).slice(0, 6)
    } catch {
      hits.value = []
    }
  }, 200)
})

function choose(hit: SearchHit) {
  href.value = nodePath(props.wikiId, hit.path)
  if (!text.value) text.value = hit.title
  query.value = ''
}

/** Adds https:// to bare addresses like "example.com". */
function normalized(): string {
  const value = href.value.trim()
  if (/^(https?:\/\/|mailto:|\/|#)/i.test(value)) return value
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return `mailto:${value}`
  return `https://${value}`
}
</script>

<template>
  <Modal title="Link" @close="emit('close')">
    <form id="link-form" @submit.prevent="href.trim() && emit('save', normalized(), text.trim())">
      <div class="form-row">
        <label class="label" for="link-href">Address</label>
        <input id="link-href" v-model="href" class="field" placeholder="https://… or a page below" autocomplete="off" />
      </div>
      <div v-if="needsText" class="form-row">
        <label class="label" for="link-text">Text</label>
        <input id="link-text" v-model="text" class="field" placeholder="Shown instead of the address" autocomplete="off" />
      </div>
      <div class="form-row">
        <label class="label" for="link-search">Or link a page of this wiki</label>
        <input id="link-search" v-model="query" class="field" placeholder="Search pages" autocomplete="off" />
        <ul v-if="hits.length" class="link__hits">
          <li v-for="hit in hits" :key="hit.id">
            <button type="button" class="menu-item" @click="choose(hit)">
              <Icon :name="hit.kind === 'CATEGORY' ? 'folder' : 'file'" />
              <span>{{ hit.title }}</span>
              <span class="muted link__path">{{ hit.path.slice(0, -1).join(' / ') }}</span>
            </button>
          </li>
        </ul>
      </div>
    </form>
    <template #actions>
      <button v-if="props.href" type="button" class="btn btn--danger link__remove" @click="emit('remove')">Remove link</button>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="submit" form="link-form" class="btn btn--primary" :disabled="!href.trim()">Apply</button>
    </template>
  </Modal>
</template>

<style scoped>
.link__hits {
  margin-top: 6px;
  padding: 4px;
  list-style: none;
  border: 1px solid var(--color-border);
}

.link__path {
  margin-left: auto;
  font-size: 0.8125rem;
}

.link__remove {
  margin-right: auto;
}
</style>
