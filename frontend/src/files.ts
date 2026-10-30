import { ref, watchEffect, type Ref } from 'vue'
import { api } from '@/api'
import { useAuth } from '@/auth'
import type { FileView } from '@/types'

/*
 * Images of private wikis need the user's token, which an <img src> can't
 * send, so signed-in users load them through fetch into object URLs (cached
 * per file: files never change). Guests only ever see public wikis and use
 * the plain URL, which the browser caches itself.
 */
const cache = new Map<string, Promise<string>>()

export function fileUrl(id: string): Promise<string> {
  if (!useAuth().user.value) return Promise.resolve(`/api/files/${id}`)
  let url = cache.get(id)
  if (!url) {
    url = api.blob(`/api/files/${id}`).then((blob) => URL.createObjectURL(blob))
    url.catch(() => cache.delete(id))
    cache.set(id, url)
  }
  return url
}

/** A reactive URL for a file id (undefined while loading or without id). */
export function useFileUrl(id: () => string | null | undefined): Ref<string | undefined> {
  const url = ref<string>()
  watchEffect(() => {
    const current = id()
    url.value = undefined
    if (!current) return
    fileUrl(current)
      .then((value) => {
        if (id() === current) url.value = value
      })
      .catch(() => {
        url.value = undefined
      })
  })
  return url
}

export const ACCEPTED_IMAGES = 'image/png,image/jpeg,image/gif,image/webp'

export function uploadImage(wikiId: string, file: File): Promise<FileView> {
  const form = new FormData()
  form.append('file', file)
  return api.post<FileView>(`/api/wikis/${wikiId}/files`, form)
}
