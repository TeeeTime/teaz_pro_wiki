<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import { Icon } from '@teeetime/teaz-pro-ui'
import { api } from '@/api'
import { WIKI } from '@/keys'
import { notifyError } from '@/feedback'
import { children, nodePath, slugPath } from '@/paths'
import { canEdit, type Overview } from '@/types'
import WikiLogo from '@/components/WikiLogo.vue'

/*
 * What a link to the wiki itself shows: built from the structure, not
 * editable. Name, logo and description come from the settings.
 */
const { wiki, tree } = inject(WIKI)!

const overview = ref<Overview | null>(null)
const categories = computed(() => children(tree.value, null))

watch(
  tree,
  async () => {
    try {
      overview.value = await api.get<Overview>(`/api/wikis/${wiki.value.id}/overview`)
    } catch (error) {
      notifyError(error)
    }
  },
  { immediate: true },
)

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
</script>

<template>
  <div class="landing">
    <header class="landing__head">
      <WikiLogo :name="wiki.name" :file-id="wiki.logoFileId" :size="64" />
      <div class="landing__intro">
        <h1 class="landing__name">{{ wiki.name }}</h1>
        <p class="landing__facts">
          <span>
            <Icon :name="wiki.visibility === 'PUBLIC' ? 'globe' : 'lock'" />
            {{ wiki.visibility === 'PUBLIC' ? 'Public' : 'Private' }}
          </span>
          <span>by {{ wiki.owner.name }}</span>
          <span v-if="overview">
            {{ overview.categoryCount }} {{ overview.categoryCount === 1 ? 'category' : 'categories' }},
            {{ overview.pageCount }} {{ overview.pageCount === 1 ? 'page' : 'pages' }}
          </span>
        </p>
      </div>
    </header>
    <p v-if="wiki.description" class="landing__description">{{ wiki.description }}</p>

    <div v-if="!categories.length" class="landing__empty">
      <p class="muted">This wiki is still empty.</p>
      <p v-if="canEdit(wiki.level)" class="muted">Start with a category: the plus next to "Overview" in the sidebar.</p>
    </div>

    <div v-else class="landing__grid">
      <section v-for="category in categories" :key="category.id" class="card">
        <RouterLink :to="nodePath(wiki.id, [category.slug])" class="card__title">
          <Icon name="folder" />
          {{ category.title }}
        </RouterLink>
        <ul v-if="children(tree, category.id).length" class="card__list">
          <li v-for="item in children(tree, category.id)" :key="item.id">
            <RouterLink :to="nodePath(wiki.id, slugPath(tree, item.id))" class="card__item">
              <Icon :name="item.kind === 'CATEGORY' ? 'folder' : 'file'" />
              <span>{{ item.title }}</span>
              <span v-if="item.kind === 'CATEGORY'" class="card__count">{{ children(tree, item.id).length }}</span>
            </RouterLink>
          </li>
        </ul>
        <p v-else class="card__none">Empty</p>
      </section>
    </div>

    <section v-if="overview?.recent.length" class="recent">
      <h2 class="recent__title">Recently updated</h2>
      <ul class="recent__list">
        <li v-for="entry in overview.recent" :key="entry.id" class="recent__item">
          <RouterLink :to="nodePath(wiki.id, entry.path)" class="recent__link">
            <Icon :name="entry.kind === 'CATEGORY' ? 'folder' : 'file'" />
            {{ entry.title }}
          </RouterLink>
          <span class="recent__meta">{{ formatDate(entry.updatedAt) }} · {{ entry.updatedBy.name }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
/* Narrow layout via the container query of WikiLayout's .wiki. */
.landing {
  max-width: 1100px;
  padding: 32px 40px 80px;
}

.landing__head {
  display: flex;
  align-items: center;
  gap: 18px;
}

.landing__intro {
  min-width: 0;
}

.landing__name {
  font-size: 1.875rem;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.landing__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 6px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.landing__facts span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.landing__facts .icon {
  width: 14px;
  height: 14px;
}

.landing__description {
  max-width: 720px;
  margin-top: 20px;
  white-space: pre-line;
}

.landing__empty {
  margin-top: 40px;
}

.landing__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  margin-top: 32px;
}

.card {
  padding: 14px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--color-text-primary);
  text-decoration: none;
}

.card__title:hover {
  color: var(--color-accent);
}

.card__title .icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
}

.card__list {
  margin-top: 10px;
  padding: 0;
  list-style: none;
}

.card__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 0.875rem;
  color: var(--color-text-primary);
  text-decoration: none;
}

.card__item:hover span:first-of-type {
  text-decoration: underline;
}

.card__item .icon {
  width: 15px;
  height: 15px;
  color: var(--color-text-secondary);
}

.card__count {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.card__none {
  margin-top: 8px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.recent {
  margin-top: 40px;
}

.recent__title {
  margin-bottom: 10px;
  font-size: 1rem;
}

.recent__list {
  padding: 0;
  list-style: none;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.recent__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 16px;
  padding: 10px 14px;
}

.recent__item + .recent__item {
  border-top: 1px solid var(--color-border);
}

.recent__link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  text-decoration: none;
}

.recent__link .icon {
  width: 15px;
  height: 15px;
}

.recent__meta {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

@container (max-width: 600px) {
  .landing {
    padding: 24px 16px 64px;
  }

  .landing__name {
    font-size: 1.5rem;
  }
}
</style>
