import { createRouter, createWebHistory } from 'vue-router'

// History mode: nginx.conf falls back to index.html for unknown paths.
// URL scheme and the reserved "-" segment: see paths.ts.
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/-/new', name: 'new-wiki', component: () => import('@/views/NewWikiView.vue') },
    {
      path: '/:wikiId([a-z0-9]{8})',
      component: () => import('@/views/WikiLayout.vue'),
      props: true,
      children: [
        { path: '', name: 'wiki', component: () => import('@/views/WikiLanding.vue') },
        { path: '-/settings', name: 'settings', component: () => import('@/views/WikiSettingsView.vue') },
        { path: '-/search', name: 'search', component: () => import('@/views/SearchView.vue') },
        // ?parent=<id>&kind=PAGE|CATEGORY: the editor for a new page.
        { path: '-/new', name: 'new-node', component: () => import('@/views/NodeView.vue') },
        // ?edit: the editor for this page.
        { path: ':path([a-z0-9-]+)+', name: 'node', component: () => import('@/views/NodeView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash }
    if (to.path !== from.path) return { top: 0 }
  },
})
