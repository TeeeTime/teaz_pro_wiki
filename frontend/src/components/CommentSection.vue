<script setup lang="ts">
import { ref, watch } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { useAuth } from '@/auth'
import { confirmAction, notifyError } from '@/feedback'
import type { CommentView } from '@/types'

/*
 * Comments under a page: everyone signed in who can read the wiki writes
 * here. Replies show one level indented; answering a reply adds to the same
 * thread.
 */
const props = defineProps<{ wikiId: string; nodeId: string; canComment: boolean }>()

const auth = useAuth()
const comments = ref<CommentView[]>([])
const body = ref('')
const replyTo = ref<string | null>(null)
const replyBody = ref('')
const editingId = ref<string | null>(null)
const editBody = ref('')
const busy = ref(false)

const base = () => `/api/wikis/${props.wikiId}`

watch(
  () => props.nodeId,
  async () => {
    try {
      comments.value = await api.get<CommentView[]>(`${base()}/nodes/${props.nodeId}/comments`)
    } catch (error) {
      notifyError(error)
    }
  },
  { immediate: true },
)

async function run(action: () => Promise<CommentView[]>) {
  busy.value = true
  try {
    comments.value = await action()
    return true
  } catch (error) {
    notifyError(error)
    return false
  } finally {
    busy.value = false
  }
}

async function post() {
  if (await run(() => api.post(`${base()}/nodes/${props.nodeId}/comments`, { body: body.value }))) body.value = ''
}

function startReply(comment: CommentView, to?: CommentView) {
  replyTo.value = comment.id
  replyBody.value = to?.author ? `@${to.author.name} ` : ''
}

async function reply() {
  const parentId = replyTo.value
  const ok = await run(() => api.post(`${base()}/nodes/${props.nodeId}/comments`, { body: replyBody.value, parentId }))
  if (ok) {
    replyTo.value = null
    replyBody.value = ''
  }
}

function startEdit(comment: CommentView) {
  editingId.value = comment.id
  editBody.value = comment.body
}

async function saveEdit() {
  if (await run(() => api.patch(`${base()}/comments/${editingId.value}`, { body: editBody.value }))) editingId.value = null
}

async function remove(comment: CommentView) {
  const ok = await confirmAction({
    title: 'Delete comment?',
    text: "This can't be undone.",
    confirmLabel: 'Delete',
    danger: true,
  })
  if (ok) await run(() => api.delete<CommentView[]>(`${base()}/comments/${comment.id}`))
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}
</script>

<template>
  <section class="comments" aria-label="Comments">
    <h2 class="comments__title">
      <Icon name="chat" />
      Comments
      <span v-if="comments.length" class="comments__count">{{ comments.reduce((n, c) => n + 1 + c.replies.length, 0) }}</span>
    </h2>

    <ul v-if="comments.length" class="comments__list">
      <li v-for="comment in comments" :key="comment.id" class="thread">
        <template v-for="entry in [comment, ...comment.replies]" :key="entry.id">
          <div class="comment" :class="{ 'comment--reply': entry.parentId }">
            <span class="comment__avatar" aria-hidden="true">{{ entry.author ? initials(entry.author.name) : '–' }}</span>
            <div class="comment__main">
              <div class="comment__head">
                <span class="comment__author">{{ entry.author?.name ?? 'Deleted comment' }}</span>
                <span class="comment__time">
                  {{ formatTime(entry.createdAt) }}<template v-if="entry.editedAt"> · edited</template>
                </span>
              </div>
              <template v-if="editingId === entry.id">
                <textarea v-model="editBody" class="field" maxlength="5000" aria-label="Edit comment" />
                <div class="comment__form-actions">
                  <button type="button" class="btn btn--small" @click="editingId = null">Cancel</button>
                  <button type="button" class="btn btn--small btn--primary" :disabled="busy || !editBody.trim()" @click="saveEdit">Save</button>
                </div>
              </template>
              <p v-else-if="!entry.deleted" class="comment__body">{{ entry.body }}</p>
              <p v-else class="comment__body comment__body--deleted">This comment was deleted.</p>
              <div v-if="editingId !== entry.id && !entry.deleted" class="comment__actions">
                <button v-if="canComment" type="button" class="comment__action" @click="startReply(comment, entry.parentId ? entry : undefined)">
                  <Icon name="reply" />
                  Reply
                </button>
                <button v-if="entry.canEdit" type="button" class="comment__action" @click="startEdit(entry)">Edit</button>
                <button v-if="entry.canDelete" type="button" class="comment__action" @click="remove(entry)">Delete</button>
              </div>
            </div>
          </div>
        </template>
        <form v-if="replyTo === comment.id" class="comment comment--reply comment__reply-form" @submit.prevent="reply">
          <textarea v-model="replyBody" class="field" maxlength="5000" placeholder="Write a reply" aria-label="Reply" />
          <div class="comment__form-actions">
            <button type="button" class="btn btn--small" @click="replyTo = null">Cancel</button>
            <button type="submit" class="btn btn--small btn--primary" :disabled="busy || !replyBody.trim()">Reply</button>
          </div>
        </form>
      </li>
    </ul>
    <p v-else class="muted comments__none">No comments yet.</p>

    <form v-if="canComment" class="comments__new" @submit.prevent="post">
      <textarea v-model="body" class="field" maxlength="5000" placeholder="Write a comment" aria-label="New comment" />
      <div class="comment__form-actions">
        <button type="submit" class="btn btn--primary" :disabled="busy || !body.trim()">Comment</button>
      </div>
    </form>
    <p v-else-if="!auth.user.value" class="comments__login">
      <button type="button" class="btn btn--small" @click="auth.login()">
        <Icon name="login" />
        Sign in
      </button>
      to comment.
    </p>
  </section>
</template>

<style scoped>
.comments {
  margin-top: 56px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}

.comments__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 1.0625rem;
}

.comments__title .icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
}

.comments__count {
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.comments__list {
  padding: 0;
  list-style: none;
}

.thread + .thread {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.comment {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

/* Replies: one level in, with a line that ties them to the thread. */
.comment--reply {
  margin-left: 44px;
  padding-left: 14px;
  border-left: 2px solid var(--color-border);
}

.comment__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-on-accent);
  background: var(--color-accent);
  /* Square: the TopBar avatar is the only round exception (STYLE_GUIDE.md). */
  border-radius: var(--radius-sm);
}

.comment__main {
  flex: 1;
  min-width: 0;
}

.comment__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
}

.comment__author {
  font-size: 0.875rem;
  font-weight: 600;
}

.comment__time {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.comment__body {
  margin-top: 4px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.comment__body--deleted {
  font-style: italic;
  color: var(--color-text-secondary);
}

.comment__actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.comment__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 0;
  font: inherit;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.comment__action:hover {
  color: var(--color-text-primary);
}

.comment__action .icon {
  width: 14px;
  height: 14px;
}

.comment__reply-form {
  flex-direction: column;
  gap: 0;
}

.comment__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.comments__none {
  margin-bottom: 8px;
}

.comments__new {
  margin-top: 16px;
}

.comments__login {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  color: var(--color-text-secondary);
}

@media (max-width: 480px) {
  .comment--reply {
    margin-left: 16px;
  }
}
</style>
