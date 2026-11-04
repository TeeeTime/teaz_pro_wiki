import type { Ref } from 'vue'

export interface AuthUser {
  /** Keycloak user id (sub). Also the key of the user's profile picture: avatarUrl(id). */
  id: string
  name: string
  username: string
  email?: string
  /** Keycloak realm roles (realm_access.roles). For showing/hiding UI only; backends enforce them. */
  roles: readonly string[]
}

/*
 * The contract every auth implementation (mock or Keycloak) fulfils, so
 * components only ever talk to `useAuth()` and never to a concrete client.
 * Wiki works without login too (public wikis), so `user` may stay null.
 */
export interface AuthService {
  /** Null for guests. */
  user: Readonly<Ref<AuthUser | null>>
  /** Resolves once it's known whether someone is logged in; never forces a login. */
  init(): Promise<void>
  /** Sends the user to the login page and back to where they were. */
  login(): Promise<void>
  logout(): Promise<void>
  /** Headers that identify the user to the backend (none for guests), the token refreshed if needed. */
  authHeaders(): Promise<Record<string, string>>
  /** Local dev only: users to switch between, see mockAuth.ts. */
  devUsers?: readonly { id: string; name: string }[]
  switchUser?(id: string | null): void
}
