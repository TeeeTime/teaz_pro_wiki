<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { WIKI } from '@/keys'
import { loadMyWikis } from '@/state'
import { confirmAction, notify, notifyError } from '@/feedback'
import { uploadImage } from '@/files'
import type { MemberRole, MemberView, UserView, Visibility, WikiDetail } from '@/types'
import WikiLogo from '@/components/WikiLogo.vue'
import LogoDialog from '@/components/LogoDialog.vue'
import Modal from '@/components/Modal.vue'

/*
 * The owner's settings of a wiki: name, logo, description, who may read and
 * edit (people found by the user search, with a role), public or private,
 * and deleting it.
 */
const { wiki, reloadWiki } = inject(WIKI)!
const router = useRouter()

const name = ref(wiki.value.name)
const description = ref(wiki.value.description)
const savingGeneral = ref(false)
const logoDialog = ref(false)

const members = ref<MemberView[]>([])
const query = ref('')
const results = ref<UserView[]>([])
const searching = ref(false)
const newRole = ref<MemberRole>('EDITOR')
const deleteDialog = ref(false)
const deleteName = ref('')

const isOwner = computed(() => wiki.value.level === 'OWNER')
const link = computed(() => `${window.location.origin}/${wiki.value.id}`)
const generalChanged = computed(() => name.value.trim() !== wiki.value.name || description.value.trim() !== wiki.value.description)
const memberIds = computed(() => new Set(members.value.map((m) => m.user.id)))

async function loadMembers() {
  try {
    members.value = await api.get<MemberView[]>(`/api/wikis/${wiki.value.id}/members`)
  } catch (error) {
    notifyError(error)
  }
}
if (isOwner.value) loadMembers()

async function patch(body: object, message: string) {
  await api.patch<WikiDetail>(`/api/wikis/${wiki.value.id}`, body)
  await Promise.all([reloadWiki(), loadMyWikis()])
  notify(message)
}

async function saveGeneral() {
  savingGeneral.value = true
  try {
    await patch({ name: name.value, description: description.value }, 'Saved.')
  } catch (error) {
    notifyError(error)
  } finally {
    savingGeneral.value = false
  }
}

async function saveLogo(file: File) {
  logoDialog.value = false
  try {
    const uploaded = await uploadImage(wiki.value.id, file)
    await patch({ logoFileId: uploaded.id }, 'Logo changed.')
  } catch (error) {
    notifyError(error)
  }
}

async function removeLogo() {
  try {
    await patch({ removeLogo: true }, 'Logo removed.')
  } catch (error) {
    notifyError(error)
  }
}

async function setVisibility(visibility: Visibility) {
  if (visibility === wiki.value.visibility) return
  if (visibility === 'PUBLIC') {
    const ok = await confirmAction({
      title: 'Make this wiki public?',
      text: 'Everyone with the link can read every page and its comments, also without an account. Only you and the editors you added can change it.',
      confirmLabel: 'Make public',
    })
    if (!ok) return
  }
  try {
    await patch({ visibility }, visibility === 'PUBLIC' ? 'The wiki is public now.' : 'The wiki is private now.')
  } catch (error) {
    notifyError(error)
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(link.value)
    notify('Link copied.')
  } catch {
    notify(link.value)
  }
}

// User search, debounced.
let timer: ReturnType<typeof setTimeout> | undefined
watch(query, (q) => {
  clearTimeout(timer)
  if (q.trim().length < 2) {
    results.value = []
    return
  }
  timer = setTimeout(async () => {
    searching.value = true
    try {
      results.value = await api.get<UserView[]>(`/api/users/search?q=${encodeURIComponent(q.trim())}`)
    } catch (error) {
      notifyError(error)
    } finally {
      searching.value = false
    }
  }, 250)
})
onBeforeUnmount(() => clearTimeout(timer))

async function putMember(userId: string, role: MemberRole) {
  try {
    members.value = await api.put<MemberView[]>(`/api/wikis/${wiki.value.id}/members/${userId}`, { role })
  } catch (error) {
    notifyError(error)
  }
}

async function addMember(user: UserView) {
  await putMember(user.id, newRole.value)
  query.value = ''
  results.value = []
  notify(`${user.name} added.`)
}

async function removeMember(member: MemberView) {
  const ok = await confirmAction({
    title: `Remove ${member.user.name}?`,
    text:
      wiki.value.visibility === 'PRIVATE'
        ? 'They lose access to this wiki.'
        : 'They can still read the public wiki, but no longer edit it.',
    confirmLabel: 'Remove',
    danger: true,
  })
  if (!ok) return
  try {
    members.value = await api.delete<MemberView[]>(`/api/wikis/${wiki.value.id}/members/${member.user.id}`)
  } catch (error) {
    notifyError(error)
  }
}

async function deleteWiki() {
  try {
    await api.delete(`/api/wikis/${wiki.value.id}`)
    await loadMyWikis()
    deleteDialog.value = false
    notify('Wiki deleted.')
    router.push('/')
  } catch (error) {
    notifyError(error)
  }
}

const ROLE_TEXT: Record<MemberRole, string> = {
  EDITOR: 'Editor: reads, writes and organizes pages',
  VIEWER: 'Viewer: reads (only matters while the wiki is private)',
}
</script>

<template>
  <div class="settings">
    <h1 class="settings__title">Settings</h1>
    <p v-if="!isOwner" class="muted">Only the owner can change the settings of this wiki.</p>

    <template v-else>
      <section class="panel">
        <h2 class="panel__title">General</h2>
        <div class="general">
          <div class="general__logo">
            <WikiLogo :name="wiki.name" :file-id="wiki.logoFileId" :size="72" />
            <div class="general__logo-actions">
              <button type="button" class="btn btn--small" @click="logoDialog = true">
                <Icon name="upload" />
                {{ wiki.logoFileId ? 'Change logo' : 'Add logo' }}
              </button>
              <button v-if="wiki.logoFileId" type="button" class="btn btn--small btn--ghost" @click="removeLogo">Remove</button>
            </div>
          </div>
          <form class="general__fields" @submit.prevent="saveGeneral">
            <div class="form-row">
              <label class="label" for="settings-name">Name</label>
              <input id="settings-name" v-model="name" class="field" maxlength="80" required />
            </div>
            <div class="form-row">
              <label class="label" for="settings-description">Description</label>
              <textarea id="settings-description" v-model="description" class="field" maxlength="2000" />
              <p class="hint">Shown on the overview page and in link previews.</p>
            </div>
            <div class="panel__actions">
              <button type="submit" class="btn btn--primary" :disabled="savingGeneral || !generalChanged || !name.trim()">Save</button>
            </div>
          </form>
        </div>
      </section>

      <section class="panel">
        <h2 class="panel__title">Visibility</h2>
        <div class="choices" role="radiogroup" aria-label="Visibility">
          <button
            type="button"
            class="choice"
            :class="{ 'choice--on': wiki.visibility === 'PRIVATE' }"
            role="radio"
            :aria-checked="wiki.visibility === 'PRIVATE'"
            @click="setVisibility('PRIVATE')"
          >
            <Icon name="lock" />
            <span>
              <strong>Private</strong>
              <span class="choice__text">Only you and the people below, as editors or viewers.</span>
            </span>
          </button>
          <button
            type="button"
            class="choice"
            :class="{ 'choice--on': wiki.visibility === 'PUBLIC' }"
            role="radio"
            :aria-checked="wiki.visibility === 'PUBLIC'"
            @click="setVisibility('PUBLIC')"
          >
            <Icon name="globe" />
            <span>
              <strong>Public</strong>
              <span class="choice__text">
                Anyone with the link reads it, without an account. Signed-in people can comment and subscribe. Only
                editors change it.
              </span>
            </span>
          </button>
        </div>
        <div v-if="wiki.visibility === 'PUBLIC'" class="share">
          <input class="field" :value="link" readonly aria-label="Link to this wiki" @focus="($event.target as HTMLInputElement).select()" />
          <button type="button" class="btn" @click="copyLink">
            <Icon name="copy" />
            Copy link
          </button>
        </div>
      </section>

      <section class="panel">
        <h2 class="panel__title">People</h2>
        <p class="muted panel__text">
          {{ wiki.visibility === 'PUBLIC' ? 'Everybody can read this wiki; add editors who may change it.' : 'Add people who may read or edit this wiki.' }}
        </p>
        <div class="add">
          <div class="add__search">
            <Icon name="search" class="add__icon" />
            <input v-model="query" class="field add__input" placeholder="Search by name, username or email" aria-label="Search people" autocomplete="off" />
          </div>
          <select v-model="newRole" class="field add__role" aria-label="Role for new people">
            <option value="EDITOR">Editor</option>
            <option value="VIEWER">Viewer</option>
          </select>
        </div>
        <p class="hint">{{ ROLE_TEXT[newRole] }}</p>
        <ul v-if="results.length" class="people people--results">
          <li v-for="user in results" :key="user.id" class="person">
            <span class="person__name">
              {{ user.name }}
              <span class="person__username">{{ user.username }}</span>
            </span>
            <span v-if="memberIds.has(user.id)" class="muted person__note">Already added</span>
            <button v-else type="button" class="btn btn--small btn--primary" @click="addMember(user)">
              <Icon name="user-plus" />
              Add as {{ newRole === 'EDITOR' ? 'editor' : 'viewer' }}
            </button>
          </li>
        </ul>
        <p v-else-if="query.trim().length >= 2 && !searching" class="hint">Nobody found.</p>

        <ul class="people">
          <li class="person">
            <span class="person__name">
              {{ wiki.owner.name }}
              <span class="person__username">{{ wiki.owner.username }}</span>
            </span>
            <span class="person__role">Owner</span>
          </li>
          <li v-for="member in members" :key="member.user.id" class="person">
            <span class="person__name">
              {{ member.user.name }}
              <span class="person__username">{{ member.user.username }}</span>
            </span>
            <select
              class="field person__select"
              :value="member.role"
              :aria-label="`Role of ${member.user.name}`"
              @change="putMember(member.user.id, ($event.target as HTMLSelectElement).value as MemberRole)"
            >
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
            <button type="button" class="icon-btn" :aria-label="`Remove ${member.user.name}`" title="Remove" @click="removeMember(member)">
              <Icon name="x" />
            </button>
          </li>
        </ul>
      </section>

      <section class="panel panel--danger">
        <h2 class="panel__title">Delete wiki</h2>
        <p class="muted panel__text">All categories, pages, images and comments are deleted for everyone. This can't be undone.</p>
        <button type="button" class="btn btn--danger" @click="deleteDialog = true">
          <Icon name="trash" />
          Delete this wiki
        </button>
      </section>
    </template>

    <LogoDialog v-if="logoDialog" @save="saveLogo" @close="logoDialog = false" />
    <Modal v-if="deleteDialog" :title="`Delete ${wiki.name}?`" narrow @close="deleteDialog = false">
      <p class="muted">Type the wiki's name to confirm.</p>
      <input v-model="deleteName" class="field delete__input" :placeholder="wiki.name" aria-label="Wiki name" />
      <template #actions>
        <button type="button" class="btn" @click="deleteDialog = false">Cancel</button>
        <button type="button" class="btn btn--danger" :disabled="deleteName.trim() !== wiki.name" @click="deleteWiki">Delete</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.settings {
  max-width: 820px;
  padding: 32px 40px 80px;
}

.settings__title {
  margin-bottom: 20px;
  font-size: 1.5rem;
}

.panel {
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.panel + .panel {
  margin-top: 16px;
}

.panel--danger {
  border-left: 3px solid var(--color-status-critical);
}

.panel__title {
  margin-bottom: 14px;
  font-size: 1rem;
}

.panel__text {
  margin: -6px 0 14px;
  font-size: 0.875rem;
}

.panel__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.general {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 24px;
}

.general__logo {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.general__logo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.choices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.choice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  font: inherit;
  text-align: left;
  color: var(--color-text-primary);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.choice:hover {
  border-color: var(--color-text-secondary);
}

.choice--on {
  border-color: var(--color-accent);
  box-shadow: inset 0 0 0 1px var(--color-accent);
}

.choice > .icon {
  width: 20px;
  height: 20px;
  margin-top: 2px;
  color: var(--color-text-secondary);
}

.choice--on > .icon {
  color: var(--color-accent);
}

.choice__text {
  display: block;
  margin-top: 4px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.share {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.add {
  display: flex;
  gap: 8px;
}

.add__search {
  position: relative;
  flex: 1;
}

.add__icon {
  position: absolute;
  top: 50%;
  left: 10px;
  width: 16px;
  height: 16px;
  color: var(--color-text-secondary);
  transform: translateY(-50%);
}

.add__input {
  padding-left: 34px;
}

.add__role {
  width: auto;
}

.people {
  margin-top: 14px;
  padding: 0;
  list-style: none;
  border: 1px solid var(--color-border);
}

.people--results {
  border-color: var(--color-accent);
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
}

.person + .person {
  border-top: 1px solid var(--color-border);
}

.person__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.person__username {
  margin-left: 6px;
  font-size: 0.8125rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.person__role,
.person__note {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.person__select {
  width: auto;
  min-height: 32px;
  padding: 4px 8px;
}

.delete__input {
  margin-top: 10px;
}

@media (max-width: 700px) {
  .settings {
    padding: 24px 16px 64px;
  }

  .general,
  .choices {
    grid-template-columns: minmax(0, 1fr);
  }

  .add {
    flex-direction: column;
  }

  .add__role {
    width: 100%;
  }
}
</style>
