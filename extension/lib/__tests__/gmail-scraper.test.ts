/**
 * Gmail scraper tests.
 *
 * We build a minimal synthetic DOM rather than snapshotting real Gmail —
 * their live markup carries a ton of unrelated noise and changes often.
 * The selectors we rely on (`span[email]`, `h2[data-thread-perm-id]`)
 * have been stable for years, so these tests stay focused on the logic.
 */
import { beforeEach, describe, expect, it } from 'vitest'
import { extractGmailThreadContext, isGmailThreadOpen } from '../gmail-scraper'

function mountOpenThread(html: string) {
  document.body.innerHTML = `
    <div role="main">
      <h2 data-thread-perm-id="thread-1">Subject line</h2>
      ${html}
    </div>
  `
}

function mountInboxOnly() {
  document.body.innerHTML = `
    <div role="main">
      <ul><li>Inbox row 1</li></ul>
    </div>
  `
}

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('isGmailThreadOpen', () => {
  it('is true when a thread heading is present', () => {
    mountOpenThread('')
    expect(isGmailThreadOpen()).toBe(true)
  })

  it('is false on the inbox list', () => {
    mountInboxOnly()
    expect(isGmailThreadOpen()).toBe(false)
  })
})

describe('extractGmailThreadContext', () => {
  it('returns null on the inbox list (no open thread)', () => {
    mountInboxOnly()
    expect(extractGmailThreadContext()).toBeNull()
  })

  it('picks the first non-self participant as primary', () => {
    mountOpenThread(`
      <div data-hovercard-id="me">
        <span email="me@example.com" name="Me Myself"></span>
      </div>
      <span email="jane@acme.io" name="Jane Doe"></span>
      <span email="bob@acme.io" name="Bob Builder"></span>
    `)
    const ctx = extractGmailThreadContext()
    expect(ctx).not.toBeNull()
    expect(ctx!.primaryEmail).toBe('jane@acme.io')
    expect(ctx!.primaryName).toBe('Jane Doe')
    expect(ctx!.allParticipants).toContain('me@example.com')
    expect(ctx!.allParticipants).toContain('jane@acme.io')
    expect(ctx!.allParticipants).toContain('bob@acme.io')
  })

  it('falls back to first participant if everyone is self', () => {
    mountOpenThread(`
      <div data-hovercard-id="me">
        <span email="me@example.com" name="Me Myself"></span>
      </div>
    `)
    const ctx = extractGmailThreadContext()
    expect(ctx).not.toBeNull()
    expect(ctx!.primaryEmail).toBe('me@example.com')
  })

  it('dedupes participants across multiple messages', () => {
    mountOpenThread(`
      <span email="jane@acme.io" name="Jane Doe"></span>
      <span email="jane@acme.io"></span>
      <span email="jane@acme.io" name="J. Doe"></span>
    `)
    const ctx = extractGmailThreadContext()
    expect(ctx).not.toBeNull()
    expect(ctx!.allParticipants).toEqual(['jane@acme.io'])
  })

  it('ignores chips without a valid email attribute', () => {
    mountOpenThread(`
      <span email=""></span>
      <span email="not-an-email"></span>
      <span email="jane@acme.io" name="Jane Doe"></span>
    `)
    const ctx = extractGmailThreadContext()
    expect(ctx!.primaryEmail).toBe('jane@acme.io')
    expect(ctx!.allParticipants).toEqual(['jane@acme.io'])
  })

  it('returns null when an open thread has no participants at all', () => {
    mountOpenThread('<div>No chips here</div>')
    expect(extractGmailThreadContext()).toBeNull()
  })
})
