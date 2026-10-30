import { ref } from 'vue'
import { errorMessage } from '@/api'

/*
 * App-wide messages and confirmations, rendered once by Feedback.vue. A
 * confirmation is an in-page dialog, never window.confirm.
 */

export interface Toast {
  id: number
  text: string
  kind: 'info' | 'error'
}

export interface Confirmation {
  title: string
  text: string
  confirmLabel: string
  danger: boolean
  resolve: (ok: boolean) => void
}

export const toasts = ref<Toast[]>([])
export const confirmation = ref<Confirmation | null>(null)

let nextId = 1

export function notify(text: string, kind: Toast['kind'] = 'info'): void {
  const id = nextId++
  toasts.value.push({ id, text, kind })
  setTimeout(() => dismiss(id), kind === 'error' ? 7000 : 3500)
}

export function notifyError(error: unknown): void {
  notify(errorMessage(error), 'error')
}

export function dismiss(id: number): void {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}

export function confirmAction(options: {
  title: string
  text: string
  confirmLabel?: string
  danger?: boolean
}): Promise<boolean> {
  confirmation.value?.resolve(false)
  return new Promise((resolve) => {
    confirmation.value = {
      title: options.title,
      text: options.text,
      confirmLabel: options.confirmLabel ?? 'OK',
      danger: options.danger ?? false,
      resolve: (ok) => {
        confirmation.value = null
        resolve(ok)
      },
    }
  })
}
