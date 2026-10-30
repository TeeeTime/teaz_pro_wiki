import Keycloak from 'keycloak-js'
import { readonly, ref } from 'vue'
import type { AuthService, AuthUser } from './types'

export interface KeycloakSettings {
  url: string
  realm: string
  clientId: string
}

// Refresh the access token when it has less than this many seconds left.
const MIN_TOKEN_VALIDITY = 30

/*
 * Real login via Keycloak (OIDC authorization code flow + PKCE, public
 * client). Login is optional (public wikis are readable without), so init()
 * only checks for an existing session (check-sso, silently in an iframe via
 * public/silent-check-sso.html) and the app mounts either way.
 */
export function createKeycloakAuth(settings: KeycloakSettings): AuthService {
  const keycloak = new Keycloak(settings)
  const user = ref<AuthUser | null>(null)

  function readUser(): AuthUser | null {
    const token = keycloak.tokenParsed
    if (!token) return null
    const username = (token.preferred_username as string | undefined) ?? token.sub ?? ''
    return {
      name: (token.name as string | undefined) ?? username,
      username,
      email: token.email as string | undefined,
      roles: token.realm_access?.roles ?? [],
    }
  }

  keycloak.onTokenExpired = () => {
    keycloak.updateToken(MIN_TOKEN_VALIDITY).catch(() => {
      user.value = null
    })
  }
  keycloak.onAuthRefreshSuccess = () => {
    user.value = readUser()
  }

  return {
    user: readonly(user),
    async init() {
      await keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        checkLoginIframe: false,
        pkceMethod: 'S256',
      })
      user.value = readUser()
    },
    async login() {
      await keycloak.login({ redirectUri: window.location.href })
    },
    async logout() {
      await keycloak.logout({ redirectUri: window.location.href })
    },
    async authHeaders(): Promise<Record<string, string>> {
      if (!keycloak.authenticated) return {}
      try {
        await keycloak.updateToken(MIN_TOKEN_VALIDITY)
      } catch {
        // Session ended elsewhere: continue as a guest rather than forcing a login.
        user.value = null
        return {}
      }
      return keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {}
    },
  }
}
