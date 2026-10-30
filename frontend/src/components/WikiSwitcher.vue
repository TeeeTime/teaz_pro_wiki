<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { useAuth } from '@/auth'
import { myWikis } from '@/state'
import { usePopover } from '@/popover'
import { wikiPath } from '@/paths'
import type { Relation, WikiDetail, WikiSummary } from '@/types'
import WikiLogo from '@/components/WikiLogo.vue'

/*
 * Top left of the TopBar: the open wiki's logo and name; opens the list of
 * the user's wikis to switch between them.
 */
const props = defineProps<{ current: WikiDetail | null }>()

const auth = useAuth()
const root = ref<HTMLElement | null>(null)
const { open, toggle, close } = usePopover(root)

const GROUPS: { label: string; relations: Relation[] }[] = [
  { label: 'My wikis', relations: ['OWNER'] },
  { label: 'Shared with me', relations: ['EDITOR', 'VIEWER'] },
  { label: 'Subscribed', relations: ['SUBSCRIBER'] },
]

const groups = computed(() =>
  GROUPS.map((group) => ({
    label: group.label,
    wikis: myWikis.value.filter((w: WikiSummary) => group.relations.includes(w.relation)),
  })).filter((group) => group.wikis.length > 0),
)
</script>

<template>
  <div ref="root" class="switcher">
    <button type="button" class="switcher__button" :aria-expanded="open" aria-haspopup="menu" @click="toggle">
      <template v-if="props.current">
        <WikiLogo :name="props.current.name" :file-id="props.current.logoFileId" :size="26" />
        <span class="switcher__name">{{ props.current.name }}</span>
      </template>
      <span v-else class="switcher__name">Wiki</span>
      <Icon name="chevron-down" class="switcher__chevron" />
    </button>

    <div v-if="open" class="popover switcher__menu" role="menu">
      <template v-for="(group, index) in groups" :key="group.label">
        <div v-if="index > 0" class="menu-divider" />
        <div class="menu-heading">{{ group.label }}</div>
        <RouterLink
          v-for="wiki in group.wikis"
          :key="wiki.id"
          :to="wikiPath(wiki.id)"
          class="menu-item"
          :class="{ 'menu-item--active': wiki.id === props.current?.id }"
          role="menuitem"
          @click="close"
        >
          <WikiLogo :name="wiki.name" :file-id="wiki.logoFileId" :size="22" />
          <span class="switcher__item-name">{{ wiki.name }}</span>
          <Icon v-if="wiki.visibility === 'PRIVATE'" name="lock" class="switcher__lock" />
        </RouterLink>
      </template>

      <template v-if="auth.user.value">
        <div v-if="groups.length" class="menu-divider" />
        <RouterLink to="/-/new" class="menu-item" role="menuitem" @click="close">
          <Icon name="plus" />
          New wiki
        </RouterLink>
      </template>
      <template v-else>
        <p class="switcher__guest">Sign in to see your own wikis and the ones you subscribed to.</p>
        <button type="button" class="menu-item" role="menuitem" @click="auth.login()">
          <Icon name="login" />
          Sign in
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.switcher {
  position: relative;
  min-width: 0;
}

.switcher__button {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
  height: 40px;
  padding: 0 8px 0 6px;
  font: inherit;
  color: var(--color-text-primary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.switcher__button:hover {
  background: var(--color-surface-hover);
}

.switcher__button:focus-visible {
  outline: 2px solid var(--color-accent);
}

.switcher__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.95rem;
  font-weight: 600;
}

.switcher__chevron {
  width: 16px;
  height: 16px;
  color: var(--color-text-secondary);
}

.switcher__menu {
  top: calc(100% + 8px);
  left: 0;
  width: 300px;
  max-width: calc(100vw - 24px);
  max-height: 70vh;
  overflow: auto;
}

.switcher__item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item .switcher__lock {
  width: 14px;
  height: 14px;
}

.switcher__guest {
  padding: 8px 10px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}
</style>
