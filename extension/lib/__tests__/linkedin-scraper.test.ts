/**
 * Scraper snapshot tests — fastest signal we've got when LinkedIn ships a DOM
 * change. Each fixture is a minimal HTML sample; tests assert the scraper
 * extracts what we expect.
 *
 * When a selector breaks, update the scraper AND the fixture in the same PR
 * so the regression stays green.
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'

import {
  deriveNameFromProfileUrl,
  isMountablePage,
  isPostPage,
  isProfileUrl,
  isRegularProfileUrl,
  isSalesNavProfileUrl,
  isSearchResultsUrl,
  normalizeProfileUrl,
  scrapeProfile,
  scrapeProfileWithTelemetry,
  scrapeSearchResults,
  scrapeVisibleProfileLinks,
  SCRAPER_VERSION,
} from '../linkedin-scraper'

const __dirname = dirname(fileURLToPath(import.meta.url))
const regularFixture = readFileSync(join(__dirname, 'fixtures/regular-profile.html'), 'utf8')
const salesNavFixture = readFileSync(join(__dirname, 'fixtures/sales-nav.html'), 'utf8')
const searchFixture = readFileSync(join(__dirname, 'fixtures/search-results.html'), 'utf8')

// happy-dom exposes window.location, but the setter is read-only in default
// mode. This helper stubs it to whatever URL the test needs.
function setUrl(url: string) {
  const u = new URL(url)
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: Object.assign(u, {
      toString: () => url,
      href: url,
    }),
  })
}

function loadFixture(html: string) {
  document.body.innerHTML = html
}

describe('URL classification', () => {
  it('recognises regular profile URLs', () => {
    expect(isRegularProfileUrl('https://www.linkedin.com/in/janedoe')).toBe(true)
    expect(isRegularProfileUrl('https://linkedin.com/in/jane-doe/details/experience')).toBe(true)
    expect(isRegularProfileUrl('https://www.linkedin.com/feed')).toBe(false)
  })

  it('recognises Sales Navigator URLs', () => {
    expect(isSalesNavProfileUrl('https://www.linkedin.com/sales/lead/ACoAAB1,NAME_SEARCH')).toBe(true)
    expect(isSalesNavProfileUrl('https://www.linkedin.com/sales/people/ACoAAB2')).toBe(true)
    expect(isSalesNavProfileUrl('https://www.linkedin.com/in/foo')).toBe(false)
  })

  it('combines both in isProfileUrl', () => {
    expect(isProfileUrl('https://www.linkedin.com/in/foo')).toBe(true)
    expect(isProfileUrl('https://www.linkedin.com/sales/lead/ACoA,NAME_SEARCH')).toBe(true)
    expect(isProfileUrl('https://google.com')).toBe(false)
  })

  it('recognises search-results URLs (regular + Sales Nav)', () => {
    expect(isSearchResultsUrl('https://www.linkedin.com/search/results/people/?keywords=cto')).toBe(true)
    expect(isSearchResultsUrl('https://www.linkedin.com/sales/search/people?companyIndustry=5')).toBe(true)
    expect(isSearchResultsUrl('https://www.linkedin.com/in/janedoe')).toBe(false)
    expect(isSearchResultsUrl('https://www.linkedin.com/feed')).toBe(false)
  })

  it('isMountablePage covers profiles AND search pages', () => {
    expect(isMountablePage('https://www.linkedin.com/in/janedoe')).toBe(true)
    expect(isMountablePage('https://www.linkedin.com/search/results/people/?keywords=foo')).toBe(true)
    expect(isMountablePage('https://www.linkedin.com/feed')).toBe(false)
  })
})

describe('normalizeProfileUrl', () => {
  it('strips details sub-tabs + tracking params on regular profiles', () => {
    expect(
      normalizeProfileUrl('https://www.linkedin.com/in/janedoe/details/experience?utm_source=foo')
    ).toBe('https://www.linkedin.com/in/janedoe')
  })

  it('strips search-origin commas on Sales Nav', () => {
    expect(
      normalizeProfileUrl('https://www.linkedin.com/sales/lead/ACoAAB1,NAME_SEARCH,XYZ?foo=bar')
    ).toBe('https://www.linkedin.com/sales/lead/ACoAAB1')
  })

  it('handles trailing slashes + query strings gracefully', () => {
    expect(
      normalizeProfileUrl('https://www.linkedin.com/in/janedoe/?miniProfileUrn=abc')
    ).toBe('https://www.linkedin.com/in/janedoe')
  })
})

describe('scrapeProfile — regular layout', () => {
  beforeEach(() => {
    setUrl('https://www.linkedin.com/in/janedoe')
    loadFixture(regularFixture)
  })

  it('extracts name, title, company, location, avatar', () => {
    const payload = scrapeProfile()
    expect(payload).not.toBeNull()
    expect(payload?.linkedinUrl).toBe('https://www.linkedin.com/in/janedoe')
    expect(payload?.firstName).toBe('Jane')
    expect(payload?.lastName).toBe('Doe')
    expect(payload?.title).toBe('Senior Engineer')
    expect(payload?.companyName).toBe('Acme Inc')
    expect(payload?.location).toBe('San Francisco Bay Area')
    expect(payload?.avatarUrl).toBe('https://media.licdn.com/avatar.jpg')
  })

  it('extracts the About section', () => {
    const payload = scrapeProfile()
    expect(payload?.about).toContain('Software engineer')
  })

  it('extracts experiences in display order', () => {
    const payload = scrapeProfile()
    expect(payload?.experiences).toHaveLength(2)
    expect(payload?.experiences?.[0]).toMatchObject({
      title: 'Senior Engineer',
      companyName: 'Acme Inc',
      dateRange: '2021 · Present',
    })
  })

  it('extracts education with degree + field', () => {
    const payload = scrapeProfile()
    expect(payload?.education).toHaveLength(1)
    expect(payload?.education?.[0]).toMatchObject({
      school: 'Stanford University',
      degree: 'Bachelor of Science',
      fieldOfStudy: 'Computer Science',
    })
  })

  it('extracts skills', () => {
    const payload = scrapeProfile()
    expect(payload?.skills).toEqual(['TypeScript', 'Java', 'PostgreSQL'])
  })
})

describe('scrapeProfile — Sales Navigator layout', () => {
  beforeEach(() => {
    setUrl('https://www.linkedin.com/sales/lead/ACoAAB1,NAME_SEARCH,XYZ')
    loadFixture(salesNavFixture)
  })

  it('extracts name/title/company/location with Sales Nav selectors', () => {
    const payload = scrapeProfile()
    expect(payload).not.toBeNull()
    expect(payload?.firstName).toBe('John')
    expect(payload?.lastName).toBe('Smith')
    expect(payload?.title).toBe('VP of Sales')
    expect(payload?.companyName).toBe('Globex Corp')
    expect(payload?.location).toBe('New York, NY')
  })

  it('prefers the canonical /in/<vanity> URL when available — dedupes with regular imports', () => {
    const payload = scrapeProfile()
    expect(payload?.linkedinUrl).toBe('https://www.linkedin.com/in/johnsmith')
  })
})

describe('scrapeSearchResults', () => {
  beforeEach(() => {
    setUrl('https://www.linkedin.com/search/results/people/?keywords=cto')
    loadFixture(searchFixture)
  })

  it('extracts every unique profile row', () => {
    const rows = scrapeSearchResults()
    // 3 rows in the fixture but Jane Doe appears twice (the details/experience
    // link dedupes to the canonical /in/janedoe). Should return 2 unique.
    expect(rows).toHaveLength(2)
    expect(rows.map(r => r.linkedinUrl)).toEqual([
      'https://www.linkedin.com/in/janedoe',
      'https://www.linkedin.com/in/johnsmith',
    ])
  })

  it('populates name + headline + avatar per row', () => {
    const [first, second] = scrapeSearchResults()
    expect(first).toMatchObject({
      firstName: 'Jane',
      lastName: 'Doe',
      title: 'Senior Engineer',
      companyName: 'Acme Inc',
      avatarUrl: 'https://media.licdn.com/jane.jpg',
      location: 'San Francisco Bay Area',
    })
    expect(second).toMatchObject({
      firstName: 'John',
      lastName: 'Smith',
      title: 'VP of Sales',
      companyName: 'Globex',
    })
  })

  it('returns an empty array on non-search pages', () => {
    setUrl('https://www.linkedin.com/in/janedoe')
    loadFixture(regularFixture)
    expect(scrapeSearchResults()).toEqual([])
  })
})

describe('scrapeProfileWithTelemetry', () => {
  it('reports missed fields when the DOM is empty', () => {
    setUrl('https://www.linkedin.com/in/ghost')
    loadFixture('<div></div>')
    const result = scrapeProfileWithTelemetry()
    // payload is still non-null (URL is valid), but essential fields are missing
    expect(result.layout).toBe('profile')
    expect(result.missedFields).toEqual(
      expect.arrayContaining(['firstName', 'title', 'companyName', 'avatarUrl'])
    )
  })

  it('returns zero misses on a complete regular fixture', () => {
    setUrl('https://www.linkedin.com/in/janedoe')
    loadFixture(regularFixture)
    const result = scrapeProfileWithTelemetry()
    expect(result.missedFields).toEqual([])
    expect(result.layout).toBe('profile')
  })

  it('publishes SCRAPER_VERSION so telemetry can correlate with releases', () => {
    expect(SCRAPER_VERSION).toMatch(/^\d+$/)
  })
})

describe('isPostPage', () => {
  it('recognises /feed/update/ URLs', () => {
    expect(isPostPage('https://www.linkedin.com/feed/update/urn:li:activity:7188880000000000000/')).toBe(true)
  })
  it('recognises /posts/ URLs', () => {
    expect(isPostPage('https://www.linkedin.com/posts/jane-doe_activity-7188880000000000000')).toBe(true)
  })
  it('rejects profile URLs', () => {
    expect(isPostPage('https://www.linkedin.com/in/jane-doe')).toBe(false)
  })
  it('is included in isMountablePage', () => {
    expect(isMountablePage('https://www.linkedin.com/feed/update/urn:li:activity:123')).toBe(true)
  })
})

describe('scrapeVisibleProfileLinks', () => {
  it('extracts distinct profile anchors within a given root', () => {
    loadFixture(`
      <div class="comments-comments-list">
        <article>
          <a href="/in/alice-wonderland/"><span aria-hidden="true">Alice Wonderland</span></a>
          <div class="artdeco-entity-lockup__subtitle">Product Manager at Acme</div>
        </article>
        <article>
          <a href="/in/bob-builder/"><span aria-hidden="true">Bob Builder</span></a>
          <div class="artdeco-entity-lockup__subtitle">CEO at Constructo</div>
        </article>
        <!-- duplicate anchor — dedupe by canonical URL -->
        <a href="/in/alice-wonderland"><span aria-hidden="true">Alice W</span></a>
      </div>
    `)
    setUrl('https://www.linkedin.com/feed/update/urn:li:activity:123')
    const root = document.querySelector<HTMLElement>('.comments-comments-list')!
    const rows = scrapeVisibleProfileLinks(root)

    expect(rows).toHaveLength(2)
    const urls = rows.map(r => r.linkedinUrl)
    expect(urls).toContain('https://www.linkedin.com/in/alice-wonderland')
    expect(urls).toContain('https://www.linkedin.com/in/bob-builder')
  })
})

describe('deriveNameFromProfileUrl', () => {
  it('title-cases a simple first-last slug', () => {
    expect(deriveNameFromProfileUrl('https://www.linkedin.com/in/jane-doe'))
      .toEqual({ firstName: 'Jane', lastName: 'Doe' })
  })

  it('drops the trailing random disambiguation token', () => {
    expect(deriveNameFromProfileUrl('https://www.linkedin.com/in/jane-doe-1a2b3'))
      .toEqual({ firstName: 'Jane', lastName: 'Doe' })
  })

  it('joins multi-part last names', () => {
    expect(deriveNameFromProfileUrl('https://www.linkedin.com/in/jane-van-der-berg'))
      .toEqual({ firstName: 'Jane', lastName: 'Van Der Berg' })
  })

  it('returns null for Sales Navigator hashed slugs', () => {
    expect(deriveNameFromProfileUrl('https://www.linkedin.com/sales/lead/ACoAABcXyz'))
      .toBeNull()
  })

  it('returns null for non-LinkedIn URLs', () => {
    expect(deriveNameFromProfileUrl('https://example.com/profile/foo'))
      .toBeNull()
  })
})
