/**
 * Content script for LinkedIn profile pages + Sales Navigator leads.
 *
 * Mounts a floating React side panel (bottom-right) that:
 *   - Scrapes the visible profile and offers an "Import to Lead Rush" button
 *   - Lists any pending LINKEDIN_* tasks Lead Rush has for this contact
 *   - Sends "task completed" to the background when the user confirms
 *
 * We do NOT automate any LinkedIn actions — the user still clicks LinkedIn's
 * own Connect / Message button themselves. This extension stays assistive.
 *
 * All network calls go through the background service worker (via chrome.runtime
 * messages) so we bypass CORS from the LinkedIn origin.
 */
import { defineContentScript } from 'wxt/utils/define-content-script'
import { createShadowRootUi } from 'wxt/utils/content-script-ui/shadow-root'
import ReactDOM from 'react-dom/client'
import { SidePanel } from './SidePanel'
import themeCss from '@/assets/theme.css?inline'
import { isMountablePage } from '@/lib/linkedin-scraper'

export default defineContentScript({
  matches: ['https://*.linkedin.com/*'],
  runAt: 'document_idle',
  cssInjectionMode: 'ui',

  async main(ctx) {
    // Leave a breadcrumb so users can see in DevTools whether the content
    // script even loaded. Prefixed for easy filtering.
    const log = (...args: unknown[]) => console.log('[lead-rush]', ...args)
    log('content script loaded on', window.location.href)

    // Mount React inside an open Shadow DOM so LinkedIn's CSS can't bleed into us
    // and our CSS doesn't leak the other way.
    let ui: Awaited<ReturnType<typeof createShadowRootUi<ReactDOM.Root>>>
    try {
      ui = await createShadowRootUi(ctx, {
        name: 'leadrush-sidepanel',
        position: 'inline',
        anchor: 'body',
        append: 'last',

        onMount: (container, shadow) => {
          // Inject the theme stylesheet into the shadow root
          const styleEl = document.createElement('style')
          styleEl.textContent = themeCss
          shadow.prepend(styleEl)

          const root = ReactDOM.createRoot(container)
          root.render(<SidePanel />)
          return root
        },

        onRemove: (root) => root?.unmount(),
      })
    } catch (err) {
      console.error('[lead-rush] failed to create shadow root UI', err)
      return
    }

    let mounted = false

    function syncForCurrentUrl() {
      const mountable = isMountablePage(window.location.href)
      log('syncForCurrentUrl', { href: window.location.href, mountable, mounted })
      if (mountable && !mounted) {
        ui.mount()
        mounted = true
        log('panel mounted')
      } else if (!mountable && mounted) {
        ui.remove()
        mounted = false
        log('panel removed — not a supported page')
      }
    }

    // LinkedIn is a SPA — URL changes without page reloads.
    // Poll URL changes; on each change, remount the panel (fresh state + re-scrape).
    let lastUrl = window.location.href
    const interval = setInterval(() => {
      if (window.location.href !== lastUrl) {
        lastUrl = window.location.href
        if (mounted) {
          ui.remove()
          mounted = false
        }
        // Brief delay for the new profile's DOM to settle
        setTimeout(syncForCurrentUrl, 400)
      }
    }, 500)

    syncForCurrentUrl()

    ctx.onInvalidated(() => clearInterval(interval))
  },
})
