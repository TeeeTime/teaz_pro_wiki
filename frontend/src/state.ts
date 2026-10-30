import { ref } from 'vue'
import { api } from '@/api'
import { useAuth } from '@/auth'
import type { WikiSummary } from '@/types'

/** The signed-in user's wikis (switcher, home page). Empty for guests. */
export const myWikis = ref<WikiSummary[]>([])
export const myWikisLoaded = ref(false)

export async function loadMyWikis(): Promise<void> {
  myWikis.value = useAuth().user.value ? await api.get<WikiSummary[]>('/api/my/wikis') : []
  myWikisLoaded.value = true
}

const LAST_WIKI_KEY = 'wiki:last'

/** The home page opens the wiki the user had open last. */
export function rememberWiki(id: string): void {
  localStorage.setItem(LAST_WIKI_KEY, id)
}

export function lastWiki(): string | null {
  return localStorage.getItem(LAST_WIKI_KEY)
}
