/** Splits `text` into parts, marking where any of the query's words occur (case-insensitive). */
export function highlight(text: string, query: string): { text: string; match: boolean }[] {
  const terms = query
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  if (!terms.length) return [{ text, match: false }]
  const pattern = new RegExp(`(${terms.join('|')})`, 'gi')
  return text
    .split(pattern)
    .filter(Boolean)
    .map((part) => ({ text: part, match: terms.some((t) => new RegExp(`^${t}$`, 'i').test(part)) }))
}
