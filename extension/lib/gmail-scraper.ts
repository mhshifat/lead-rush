/**
 * Pull the "other party" email out of the currently open Gmail thread.
 *
 * Gmail is an SPA with a volatile DOM, so we rely on stable-ish data
 * attributes rather than class names:
 *   - `span[email]`         wraps every sender/recipient chip
 *   - `h2[data-thread-perm-id]` exists on an open thread (not the inbox list)
 *
 * Strategy: find the first non-self `span[email]` inside the open thread.
 * "Self" is filtered out via Gmail's own `data-hovercard-id="me"` or by
 * matching the logged-in user's email where we can detect it.
 *
 * We read only — never click or mutate. If nothing is open / matches, we
 * return null and the panel stays idle.
 */

/** Very loose RFC-5322-ish email regex — good enough for the "pull from text" path. */
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export interface GmailThreadContext {
  /** The primary other party we'll look up. */
  primaryEmail: string
  /** Sender's display name if Gmail exposes it — helps the "Add to Lead Rush" prefill. */
  primaryName: string | null
  /** All distinct participants in the thread (including self). Useful later for cc bulk-capture. */
  allParticipants: string[]
}

/**
 * True when a thread is fully open in the main viewport (not just hovered in
 * the inbox). Used to gate the panel mount — no point showing a lookup card
 * while the user is browsing the inbox list.
 */
export function isGmailThreadOpen(): boolean {
  // Gmail sets data-thread-perm-id on the heading of the open conversation.
  if (document.querySelector('h2[data-thread-perm-id]')) return true
  // Fallback: the thread toolbar area only renders once a conversation is open.
  return !!document.querySelector('div[role="main"] div[gh="tm"]')
}

/**
 * Pull the thread context. Null when nothing's open or we couldn't detect any
 * non-self participant.
 */
export function extractGmailThreadContext(): GmailThreadContext | null {
  if (!isGmailThreadOpen()) return null
  const mainPane = document.querySelector<HTMLElement>('div[role="main"]') ?? document.body

  // Every sender/recipient chip exposes the raw address as a `email` attribute.
  // `name` is optional but Gmail sets it when known.
  const chips = Array.from(mainPane.querySelectorAll<HTMLElement>('span[email]'))

  // Dedupe preserving order — primary sender of the first message tends to
  // appear first in DOM order, which matches what the user is looking at.
  const seen = new Set<string>()
  const participants: { email: string; name: string | null; isSelf: boolean }[] = []
  for (const chip of chips) {
    const email = chip.getAttribute('email')?.trim()?.toLowerCase()
    if (!email || !EMAIL_REGEX.test(email)) continue
    if (seen.has(email)) continue
    seen.add(email)
    const name = chip.getAttribute('name')?.trim() || null
    // Gmail tags the logged-in user on self-authored messages.
    const isSelf = chip.getAttribute('data-hovercard-id') === 'me'
                   || chip.closest('[data-hovercard-id="me"]') !== null
    participants.push({ email, name, isSelf })
  }

  // Prefer the first non-self participant as the "primary". If everyone looks
  // like self (e.g. user sent a thread to themselves), fall back to the first
  // entry so the panel still has something to look up.
  const primary = participants.find(p => !p.isSelf) ?? participants[0]
  if (!primary) return null

  return {
    primaryEmail: primary.email,
    primaryName: primary.name,
    allParticipants: participants.map(p => p.email),
  }
}
