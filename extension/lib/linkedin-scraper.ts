/**
 * Extract a structured payload from a LinkedIn profile page DOM.
 *
 * LinkedIn's markup changes regularly — these selectors target both the current
 * layout and a few older variants we've observed. If any field can't be found
 * we return null/undefined rather than throwing; the backend tolerates sparse data.
 *
 * IMPORTANT: we never click or mutate anything. This is pure DOM read.
 */
import type { LinkedInImportPayload } from './types'

export function isProfileUrl(url: string): boolean {
  // Matches /in/<vanity>/ and /in/<vanity>/details/<sub>
  return /linkedin\.com\/in\/[^/?#]+/.test(url)
}

/** Strip tracking params + trailing slash so we dedupe consistently. */
export function normalizeProfileUrl(url: string): string {
  try {
    const u = new URL(url)
    const parts = u.pathname.split('/').filter(Boolean)
    // Keep just "in/<vanity>" — drop /details/... tabs
    const inIdx = parts.indexOf('in')
    if (inIdx < 0) return url.split('?')[0] ?? url
    const vanity = parts[inIdx + 1]
    if (!vanity) return url.split('?')[0] ?? url
    return `${u.origin}/in/${vanity}`
  } catch {
    return url.split('?')[0] ?? url
  }
}

export function scrapeProfile(): LinkedInImportPayload | null {
  const url = normalizeProfileUrl(window.location.href)
  if (!isProfileUrl(url)) return null

  const fullName = textOf(
    'h1.text-heading-xlarge',
    'h1.top-card-layout__title',
    'h1',
  )

  const headline = textOf(
    '.text-body-medium.break-words',
    '.top-card-layout__headline',
    '[data-generated-suggestion-target]',
  )

  const location = textOf(
    '.text-body-small.inline.t-black--light.break-words',
    '.top-card__subline-item',
  )

  const avatarUrl = attrOf('img.pv-top-card-profile-picture__image, img.profile-photo-edit__preview', 'src')

  // Company — current position is usually in the "experience" card or the top card
  const companyName = textOf(
    'button[aria-label*="Current company"]',
    '.pv-text-details__right-panel-item-text',
    '[data-section="currentPositionsDetails"] a',
  ) ?? splitHeadlineCompany(headline)

  const { firstName, lastName } = splitName(fullName)

  return {
    linkedinUrl: url,
    firstName,
    lastName,
    title: splitHeadlineTitle(headline),
    companyName: companyName ?? undefined,
    avatarUrl: avatarUrl ?? undefined,
    location: location ?? undefined,
  }
}

// ── Helpers ──

function textOf(...selectors: string[]): string | undefined {
  for (const sel of selectors) {
    const el = document.querySelector(sel)
    const txt = el?.textContent?.trim()
    if (txt) return txt
  }
  return undefined
}

function attrOf(selector: string, attr: string): string | undefined {
  const el = document.querySelector(selector)
  const value = el?.getAttribute(attr) ?? undefined
  return value || undefined
}

function splitName(full: string | undefined): { firstName?: string; lastName?: string } {
  if (!full) return {}
  const parts = full.replace(/\s+/g, ' ').trim().split(' ')
  if (parts.length === 1) return { firstName: parts[0] }
  return {
    firstName: parts[0],
    lastName: parts.slice(1).join(' '),
  }
}

/** Headlines often look like "Title at Company · other stuff". */
function splitHeadlineTitle(headline: string | undefined): string | undefined {
  if (!headline) return undefined
  const [left] = headline.split(/\s+at\s+/i)
  return (left ?? headline).split('·')[0]?.trim() || undefined
}

function splitHeadlineCompany(headline: string | undefined): string | undefined {
  if (!headline) return undefined
  const match = headline.match(/\s+at\s+(.+)/i)
  if (!match) return undefined
  return match[1]?.split('·')[0]?.trim() || undefined
}
