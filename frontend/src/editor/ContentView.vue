<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { createExtensions, parseContent } from './extensions'

/*
 * A page for reading: the editor's own rendering, not editable, so it looks
 * exactly as written and nothing can be changed by accident. Links within
 * the app open without reloading the page.
 */
const props = defineProps<{ content: string; wikiId: string }>()
const router = useRouter()

const editor = useEditor({
  editable: false,
  content: parseContent(props.content),
  extensions: createExtensions({ editable: false, wikiId: props.wikiId }),
})

watch(
  () => props.content,
  (content) => editor.value?.commands.setContent(parseContent(content), { emitUpdate: false }),
)

function onClick(event: MouseEvent) {
  const link = (event.target as Element).closest('a')
  const href = link?.getAttribute('href')
  if (!href || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return
  event.preventDefault()
  if (href.startsWith('/') && !href.startsWith('//')) router.push(href)
  else if (href.startsWith('#')) document.getElementById(href.slice(1))?.scrollIntoView()
  else window.open(href, '_blank', 'noopener,noreferrer')
}

onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <EditorContent :editor="editor" class="wiki-content" @click="onClick" />
</template>
