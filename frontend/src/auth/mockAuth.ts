import { readonly, ref } from 'vue'
import type { AuthService, AuthUser } from './types'

/** Same ids as the backend's MockUserDirectory, which MockSecurityConfig accepts. */
const USERS = [
  { id: 'mock-jordan', name: 'Jordan Lee', username: 'jlee' },
  { id: 'mock-sam', name: 'Sam Rivera', username: 'sam' },
  { id: 'mock-alex', name: 'Alex Kim', username: 'alex' },
  { id: 'mock-maria', name: 'Maria Novak', username: 'maria' },
] as const

const STORAGE_KEY = 'wiki:mock-user'
const GUEST = 'guest'

/*
 * Local-dev stand-in for Keycloak, no network calls. Sharing needs several
 * people, so the account menu switches between a few fixed users or a guest
 * (stored in localStorage); requests carry the user's id as X-Mock-User.
 * Used whenever VITE_KEYCLOAK_URL is not set.
 */
export function createMockAuth(): AuthService {
  const user = ref<AuthUser | null>(null)
  let id: string | null = null

  return {
    user: readonly(user),
    async init() {
      const saved = localStorage.getItem(STORAGE_KEY)
      const found = saved === GUEST ? undefined : (USERS.find((u) => u.id === saved) ?? USERS[0])
      id = found?.id ?? null
      user.value = found ? { name: found.name, username: found.username, roles: [] } : null
    },
    async login() {
      this.switchUser!(USERS[0].id)
    },
    async logout() {
      this.switchUser!(null)
    },
    async authHeaders(): Promise<Record<string, string>> {
      return id ? { 'X-Mock-User': id } : {}
    },
    devUsers: USERS.map(({ id, name }) => ({ id, name })),
    switchUser(next) {
      localStorage.setItem(STORAGE_KEY, next ?? GUEST)
      window.location.reload()
    },
  }
}
