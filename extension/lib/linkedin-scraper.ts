/**
 * Extract a structured payload from a LinkedIn profile page DOM.
 *
 * Supports three page layouts:
 *   1. Regular LinkedIn profile  — linkedin.com/in/<vanity>
 *   2. Sales Navigator lead      — linkedin.com/sales/lead/<hashed-id>,NAME_SEARCH[,<extra>]
 *   3. Sales Navigator people    — linkedin.com/sales/people/<hashed-id>
 *
 * LinkedIn's markup changes regularly — each selector list targets the current
 * layout plus a few older variants. If any field can't be found we return
 * null/undefined rather than throwing; the backend tolerates sparse data.
 *
 * IMPORTANT: we never click or mutate anything. This is pure DOM read.
 */
import type { LinkedInEducation, LinkedInExperience, LinkedInImportPayload } from './types'

// ── URL classification ──

export function isProfileUrl(url: string): boolean {
  return isRegularProfileUrl(url) || isSalesNavProfileUrl(url)
}

export function isRegularProfileUrl(url: string): boolean {
  return /linkedin\.com\/in\/[^/?#]+/.test(url)
}

export function isSalesNavProfileUrl(url: string): boolean {
  // /sales/lead/<hashed-id>,<search-origin>  OR  /sales/people/<hashed-id>
  return /linkedin\.com\/sales\/(lead|people)\/[^/?#]+/.test(url)
}

/** True on regular search results OR Sales Nav people search. */
export function isSearchResultsUrl(url: string): boolean {
  return /linkedin\.com\/search\/results\/people/.test(url)
      || /linkedin\.com\/sales\/search\/people/.test(url)
}

/**
 * True on a LinkedIn post detail page — where reactors/commenters are visible
 * and the "capture visible profiles" button makes sense.
 *   /feed/update/urn:li:activity:...
 *   /posts/<vanity>_activity-...
 */
export function isPostPage(url: string): boolean {
  return /linkedin\.com\/feed\/update\//.test(url)
      || /linkedin\.com\/posts\//.test(url)
}

/** Any page where the extension should mount — profile OR search OR post. */
export function isMountablePage(url: string): boolean {
  return isProfileUrl(url) || isSearchResultsUrl(url) || isPostPage(url)
}

/** Strip tracking params + trailing slash so we dedupe consistently across visits. */
export function normalizeProfileUrl(url: string): string {
  try {
    const u = new URL(url)
    const parts = u.pathname.split('/').filter(Boolean)

    // Regular profile: keep "in/<vanity>", drop /details/... sub-tabs
    const inIdx = parts.indexOf('in')
    if (inIdx >= 0) {
      const vanity = parts[inIdx + 1]
      if (vanity) return `${u.origin}/in/${vanity}`
    }

    // Sales Navigator: keep "sales/lead/<id>" or "sales/people/<id>", drop comma-
    // separated search-origin tokens because they change between visits.
    if (parts[0] === 'sales' && (parts[1] === 'lead' || parts[1] === 'people')) {
      const tokenPart = parts[2]
      if (tokenPart) {
        // "ACoAAB...,NAME_SEARCH,ABC_" → keep only the leading hash id
        const id = tokenPart.split(',')[0]!
        return `${u.origin}/sales/${parts[1]}/${id}`
      }
    }

    return url.split('?')[0] ?? url
  } catch {
    return url.split('?')[0] ?? url
  }
}

/**
 * Best-effort name derivation from a profile URL's slug — used by the
 * background context-menu flow when we don't have a DOM to scrape.
 *
 * Examples:
 *   /in/jane-doe           → { firstName: 'Jane',   lastName: 'Doe' }
 *   /in/jane-doe-1a2b3     → { firstName: 'Jane',   lastName: 'Doe' }   (trailing random tokens dropped)
 *   /in/j-doe-phd          → { firstName: 'J',      lastName: 'Doe' }
 *   /sales/lead/ACoAAB...  → null   (Sales Nav slugs are hashes, no name in URL)
 */
export function deriveNameFromProfileUrl(url: string): { firstName: string; lastName?: string } | null {
  try {
    const u = new URL(url)
    const parts = u.pathname.split('/').filter(Boolean)
    const inIdx = parts.indexOf('in')
    if (inIdx < 0) return null
    const vanity = parts[inIdx + 1]
    if (!vanity) return null

    // LinkedIn commonly appends a trailing random token (5-8 alphanumerics) to
    // disambiguate identical vanities — drop it if present.
    const tokens = decodeURIComponent(vanity).split('-').filter(Boolean)
    if (tokens.length > 1 && /^[0-9a-z]{5,10}$/i.test(tokens[tokens.length - 1]!)) {
      tokens.pop()
    }
    if (tokens.length === 0) return null

    const titleCase = (s: string) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
    const firstName = titleCase(tokens[0]!)
    const lastName = tokens.length >= 2 ? tokens.slice(1).map(titleCase).join(' ') : undefined
    return { firstName, lastName }
  } catch {
    return null
  }
}

// ── Scraping ──

/** Bump whenever selectors change, so telemetry correlates with releases. */
export const SCRAPER_VERSION = '2'

export interface ScrapeResult {
  payload: LinkedInImportPayload | null
  /** Fields that couldn't be extracted — used by telemetry. */
  missedFields: string[]
  /** "profile" or "salesNav", for routing misses back to the right selector set. */
  layout: 'profile' | 'salesNav' | 'unknown'
}

export function scrapeProfile(): LinkedInImportPayload | null {
  return scrapeProfileWithTelemetry().payload
}

export function scrapeProfileWithTelemetry(): ScrapeResult {
  const rawUrl = window.location.href
  const url = normalizeProfileUrl(rawUrl)
  if (!isProfileUrl(url)) return { payload: null, missedFields: [], layout: 'unknown' }

  if (isSalesNavProfileUrl(url)) {
    const payload = scrapeSalesNavigator(url)
    return { payload, missedFields: payload ? missesFor(payload) : [], layout: 'salesNav' }
  }
  const payload = scrapeRegularProfile(url)
  return { payload, missedFields: payload ? missesFor(payload) : [], layout: 'profile' }
}

/** Essentials we EXPECT on any profile. If any are missing we ping telemetry. */
function missesFor(p: LinkedInImportPayload): string[] {
  const misses: string[] = []
  if (!p.firstName) misses.push('firstName')
  if (!p.title) misses.push('title')
  if (!p.companyName) misses.push('companyName')
  if (!p.avatarUrl) misses.push('avatarUrl')
  return misses
}

// ── Search results scraper ──

/**
 * Walks visible search-result rows on either:
 *   linkedin.com/search/results/people/…  (regular)
 *   linkedin.com/sales/search/people/…    (Sales Navigator)
 *
 * We only read what's rendered — LinkedIn lazy-loads as the user scrolls, so
 * the caller gets whatever the page has painted right now. No scrolling, no
 * clicking. Duplicates (same canonical /in/<vanity>) are collapsed.
 */
export function scrapeSearchResults(): LinkedInImportPayload[] {
  const url = window.location.href
  if (!isSearchResultsUrl(url)) return []

  // Find every <a href="…/in/…"> on the page. Modern LinkedIn search results
  // put two anchors per row (avatar + name link); dedupe by canonical URL.
  const seen = new Set<string>()
  const rows: LinkedInImportPayload[] = []

  const anchors = document.querySelectorAll<HTMLAnchorElement>('a[href*="/in/"]')
  // One-time diagnostic log so users can grep DevTools to see selector health.
  console.log('[lead-rush] search scan:', anchors.length, 'profile-like anchors')

  anchors.forEach(anchor => {
    const href = anchor.getAttribute('href')
    if (!href) return
    const canonical = normalizeProfileUrl(new URL(href, window.location.origin).href)
    if (!isRegularProfileUrl(canonical) || seen.has(canonical)) return

    const row = findSearchRow(anchor)
    const payload = extractRowPayload(canonical, row, anchor)
    if (!payload) return
    seen.add(canonical)
    rows.push(payload)
  })

  console.log('[lead-rush] search scan:', rows.length, 'unique profiles extracted')
  return rows
}

/**
 * Generic DOM scan for profile links — used on post detail pages to capture
 * reactors + commenters, and inside reactor-detail modals. Reuses the row
 * payload extractor so the data shape matches search results.
 *
 * Scope to a specific subtree (e.g. the comments section) when possible to
 * avoid scraping sidebar "People you may know" recommendations as leads.
 */
export function scrapeVisibleProfileLinks(root: ParentNode = document.body): LinkedInImportPayload[] {
  const seen = new Set<string>()
  const rows: LinkedInImportPayload[] = []

  const anchors = root.querySelectorAll<HTMLAnchorElement>('a[href*="/in/"]')
  anchors.forEach(anchor => {
    const href = anchor.getAttribute('href')
    if (!href) return
    let canonical: string
    try {
      canonical = normalizeProfileUrl(new URL(href, window.location.origin).href)
    } catch {
      return
    }
    if (!isRegularProfileUrl(canonical) || seen.has(canonical)) return

    const row = findSearchRow(anchor) ?? anchor.parentElement
    const payload = extractRowPayload(canonical, row, anchor)
    if (!payload) return
    seen.add(canonical)
    rows.push(payload)
  })

  return rows
}

/**
 * On post pages, prefer scraping ONLY the reactor modal (if open) or the
 * comments section — everything else on the page is sidebar noise. Falls
 * back to the whole document if neither region is detectable.
 */
export function scrapePostReactorsAndCommenters(): LinkedInImportPayload[] {
  const modal = document.querySelector<HTMLElement>(
    '.artdeco-modal [data-test-reactions-detail], '
    + '.artdeco-modal [class*="reactors-"], '
    + '.artdeco-modal[role="dialog"]'
  )
  if (modal) return scrapeVisibleProfileLinks(modal)

  const comments = document.querySelector<HTMLElement>(
    '.comments-comments-list, [class*="comments-comment-list"], section[aria-label*="omment"]'
  )
  if (comments) return scrapeVisibleProfileLinks(comments)

  return scrapeVisibleProfileLinks(document.body)
}

/**
 * Walks up from a profile anchor to the search-result row container.
 * Tries several generations of LinkedIn DOM; falls back to the nearest common
 * ancestor that contains both the anchor and a name-looking child.
 */
function findSearchRow(anchor: HTMLElement): HTMLElement | null {
  // Current LinkedIn (2024+) and older layouts.
  const known = anchor.closest<HTMLElement>(
    '[data-chameleon-result-urn], '
    + 'li.reusable-search__result-container, '
    + 'div.entity-result, '
    + 'div.search-results-container > div > div, '
    + 'li.artdeco-list__item, '
    + 'li.search-result'
  )
  if (known) return known

  // Sales Navigator.
  const salesNav = anchor.closest<HTMLElement>(
    'li.artdeco-list__item, '
    + '.search-results__result-item, '
    + '[data-x-search-result]'
  )
  if (salesNav) return salesNav

  // Generic fallback: walk up until we find an ancestor <li> or a div with a
  // headline/subtitle sibling — that's the row in any layout.
  let el: HTMLElement | null = anchor
  for (let i = 0; i < 8 && el; i++) {
    if (el.tagName === 'LI') return el
    if (el.querySelector('[class*="subtitle"]')) return el
    el = el.parentElement
  }
  return anchor.parentElement
}

/**
 * Pull name/title/company from a search-row subtree. Selectors tolerate the
 * classic and refreshed LinkedIn layouts — fall back to text scraping if
 * nothing else matches so we still get a usable contact.
 */
function extractRowPayload(
  profileUrl: string,
  row: HTMLElement | null,
  anchor: HTMLElement,
): LinkedInImportPayload | null {
  const scope = row ?? document.body

  // Name — try inside the anchor first (most reliable: the name link always
  // wraps the name text); fall back to scope-scoped selectors; last resort,
  // use the anchor's own visible text content.
  const fullName =
    pickText(anchor, [
      'span[aria-hidden="true"]',
    ]) ?? pickText(scope, [
      '.entity-result__title-text a[href*="/in/"] span[aria-hidden="true"]',
      'a[href*="/in/"] span[aria-hidden="true"]',
      '.artdeco-entity-lockup__title',
      '[data-anonymize="person-name"]',
      '.t-roman.t-sans',
    ]) ?? cleanText(anchor.textContent)

  const headline = pickText(scope, [
    '.entity-result__primary-subtitle',
    '.artdeco-entity-lockup__subtitle',
    '[data-anonymize="job-title"]',
    // 2024+ LinkedIn: primary subtitle class-name changed; match by class-contains.
    '[class*="primary-subtitle"]',
    'div.t-14.t-black.t-normal',
  ])

  const location = pickText(scope, [
    '.entity-result__secondary-subtitle',
    '.artdeco-entity-lockup__caption',
    '[data-anonymize="location"]',
    '[class*="secondary-subtitle"]',
    'div.t-14.t-normal.t-black--light',
  ])

  const avatarUrl = pickAttr(scope, [
    'img.ivm-view-attr__img--centered',
    'img.presence-entity__image',
    'img[data-anonymize="headshot-photo"]',
    // 2024+ LinkedIn: generic selector that matches the large profile image in a row.
    'img.EntityPhoto-circle-3',
    'img[class*="EntityPhoto"]',
  ], 'src')

  // Skip "View Name's profile" screen-reader text the selector occasionally
  // grabs from the visually-hidden sibling. And require some name.
  const name = isScreenReaderNoise(fullName) ? undefined : fullName
  if (!name) return null

  const { firstName, lastName } = splitName(name)

  return {
    linkedinUrl: profileUrl,
    firstName,
    lastName,
    title: splitHeadlineTitle(headline) ?? headline ?? undefined,
    companyName: splitHeadlineCompany(headline) ?? undefined,
    avatarUrl: avatarUrl ?? undefined,
    location: location ?? undefined,
  }
}

function cleanText(raw: string | null | undefined): string | undefined {
  if (!raw) return undefined
  const t = raw.replace(/\s+/g, ' ').trim()
  return t.length > 0 && t.length < 200 ? t : undefined
}

function isScreenReaderNoise(s: string | undefined): boolean {
  if (!s) return false
  return /^(View|Status is).*profile\b/i.test(s)
}

function pickText(scope: ParentNode, selectors: string[]): string | undefined {
  for (const sel of selectors) {
    const el = scope.querySelector(sel)
    const txt = el?.textContent?.replace(/\s+/g, ' ').trim()
    if (txt) return txt
  }
  return undefined
}

function pickAttr(scope: ParentNode, selectors: string[], attr: string): string | undefined {
  for (const sel of selectors) {
    const v = scope.querySelector(sel)?.getAttribute(attr)
    if (v) return v
  }
  return undefined
}

function scrapeRegularProfile(url: string): LinkedInImportPayload | null {
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

  const avatarUrl = attrOf(
    'img.pv-top-card-profile-picture__image, img.profile-photo-edit__preview',
    'src',
  )

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
    about: scrapeAboutSection(),
    experiences: scrapeExperiences(),
    education: scrapeEducation(),
    skills: scrapeSkills(),
  }
}

// ── Deep-scrape: About / Experience / Education / Skills ──
//
// LinkedIn sections are identified by their header <div id="..."> anchors:
//   #about, #experience, #education, #skills
// Data rows live under the ancestor <section> containing that anchor.
// We walk the DOM carefully because selectors deeper than that change often.

function scrapeAboutSection(): string | undefined {
  const section = sectionFor('about')
  if (!section) return undefined
  // The About text usually lives in a `.display-flex` block with an inline expand button.
  const block = section.querySelector('.display-flex.full-width > span[aria-hidden="true"]')
    ?? section.querySelector('span[aria-hidden="true"]')
    ?? section.querySelector('.pvs-list__outer-container')
  const txt = block?.textContent?.replace(/\s+/g, ' ').trim()
  return txt && txt.length > 0 ? txt : undefined
}

function scrapeExperiences(): LinkedInExperience[] | undefined {
  const section = sectionFor('experience')
  if (!section) return undefined
  const items = Array.from(section.querySelectorAll('li.artdeco-list__item'))
    .slice(0, 5) // cap — prevents dragging in multi-role expansions
    .map((li): LinkedInExperience | null => {
      const title = li.querySelector('div[class*="mr1"] > span[aria-hidden="true"]')?.textContent?.trim()
      const companyLine = li.querySelector('.t-14.t-normal > span[aria-hidden="true"]')?.textContent?.trim()
      const dateRange = li.querySelector('.pvs-entity__caption-wrapper')?.textContent?.trim()
        ?? li.querySelector('.t-14.t-normal.t-black--light > span[aria-hidden="true"]')?.textContent?.trim()
      if (!title && !companyLine) return null
      return {
        title: title ?? undefined,
        companyName: companyLine?.split('·')[0]?.trim() || undefined,
        dateRange: dateRange ?? undefined,
      }
    })
    .filter((x): x is LinkedInExperience => x !== null)
  return items.length > 0 ? items : undefined
}

function scrapeEducation(): LinkedInEducation[] | undefined {
  const section = sectionFor('education')
  if (!section) return undefined
  const items = Array.from(section.querySelectorAll('li.artdeco-list__item'))
    .slice(0, 3)
    .map((li): LinkedInEducation | null => {
      const school = li.querySelector('div[class*="mr1"] > span[aria-hidden="true"]')?.textContent?.trim()
      const line = li.querySelector('.t-14.t-normal > span[aria-hidden="true"]')?.textContent?.trim()
      if (!school && !line) return null
      const [degree, field] = (line ?? '').split(',').map(s => s.trim())
      return {
        school: school ?? undefined,
        degree: degree || undefined,
        fieldOfStudy: field || undefined,
      }
    })
    .filter((x): x is LinkedInEducation => x !== null)
  return items.length > 0 ? items : undefined
}

function scrapeSkills(): string[] | undefined {
  const section = sectionFor('skills')
  if (!section) return undefined
  const items = Array.from(section.querySelectorAll('li.artdeco-list__item div[class*="mr1"] > span[aria-hidden="true"]'))
    .map(el => el.textContent?.trim() ?? '')
    .filter(Boolean)
    .slice(0, 15)
  return items.length > 0 ? items : undefined
}

/** Returns the <section> containing the anchor div with id="<name>". */
function sectionFor(id: string): Element | null {
  const anchor = document.getElementById(id)
  return anchor?.closest('section') ?? null
}

function scrapeSalesNavigator(fallbackUrl: string): LinkedInImportPayload | null {
  // Prefer the canonical /in/<vanity> URL if the Sales Nav page exposes one —
  // deduplicates correctly with profiles imported from regular LinkedIn.
  const canonicalUrl = attrOf(
    'a[href*="/in/"][data-control-name="view_linkedin_profile"]',
    'href',
  ) ?? attrOf('a[href*="/in/"]', 'href')

  const linkedinUrl = canonicalUrl && isRegularProfileUrl(canonicalUrl)
    ? normalizeProfileUrl(canonicalUrl)
    : fallbackUrl

  const fullName = textOf(
    '[data-anonymize="person-name"]',
    '.profile-topcard-person-entity__name',
    '.artdeco-entity-lockup__title',
    'h1',
  )

  const title = textOf(
    '[data-anonymize="job-title"]',
    '.profile-topcard-person-entity__description .profile-topcard__current-positions-details',
    '.profile-topcard__summary-position-title',
  )

  const companyName = textOf(
    '[data-anonymize="company-name"]',
    '.profile-topcard-company-profile a',
    '.profile-topcard__summary-position-company',
  )

  const location = textOf(
    '[data-anonymize="location"]',
    '.profile-topcard__location-data',
  )

  const avatarUrl = attrOf(
    'img.profile-topcard-person-entity__image',
    'src',
  ) ?? attrOf('img[data-anonymize="headshot-photo"]', 'src')

  const { firstName, lastName } = splitName(fullName)

  return {
    linkedinUrl,
    firstName,
    lastName,
    title: title ?? undefined,
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
