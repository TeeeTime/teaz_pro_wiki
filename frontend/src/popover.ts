import { onBeforeUnmount, ref, type Ref } from 'vue'

/** Open/close state for a dropdown that closes on a click outside `root` or on Escape. */
export function usePopover(root: Ref<HTMLElement | null>) {
  const open = ref(false)

  function onClick(event: MouseEvent) {
    if (root.value && !root.value.contains(event.target as Node)) close()
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') close()
  }

  function show() {
    if (open.value) return
    open.value = true
    document.addEventListener('click', onClick, true)
    document.addEventListener('keydown', onKeydown)
  }

  function close() {
    open.value = false
    document.removeEventListener('click', onClick, true)
    document.removeEventListener('keydown', onKeydown)
  }

  function toggle() {
    if (open.value) close()
    else show()
  }

  onBeforeUnmount(close)
  return { open, show, close, toggle }
}
