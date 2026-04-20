/**
 * Content script for Gmail (mail.google.com).
 *
 * Mounts a small floating panel that shows the Lead Rush contact matching
 * the sender of the currently open thread. If the contact isn't known yet,
 * a deep-link to the web app's "Add contact" form is offered — the
 * extension itself doesn't create email-only contacts (yet), because
 * keeping the API narrow helps us stay out of "accidental contact" bugs.
 *
 * All network calls route through the background service worker, same
 * as the LinkedIn panel.
 */
import { defineContentScript } from 'wxt/utils/define-content-script'
import { createShadowRootUi } from 'wxt/utils/content-script-ui/shadow-root'
import ReactDOM from 'react-dom/client'
import { GmailPanel } from './GmailPanel'
import themeCss from '@/assets/theme.css?inline'

export default defineContentScript({
  matches: ['https://mail.google.com/*'],
  runAt: 'document_idle',
  cssInjectionMode: 'ui',

  async main(ctx) {
    const log = (...args: unknown[]) => console.log('[lead-rush][gmail]', ...args)
    log('content script loaded')

    let ui: Awaited<ReturnType<typeof createShadowRootUi<ReactDOM.Root>>>
    try {
      ui = await createShadowRootUi(ctx, {
        name: 'leadrush-gmail-panel',
        position: 'inline',
        anchor: 'body',
        append: 'last',
        onMount: (container, shadow) => {
          const styleEl = document.createElement('style')
          styleEl.textContent = themeCss
          shadow.prepend(styleEl)
          const root = ReactDOM.createRoot(container)
          root.render(<GmailPanel />)
          return root
        },
        onRemove: (root) => root?.unmount(),
      })
    } catch (err) {
      console.error('[lead-rush][gmail] failed to create shadow root UI', err)
      return
    }

    // Gmail mounts/unmounts the main pane but the URL hash changes on every
    // thread open/close (#inbox/<id>). We just keep the panel mounted
    // continuously — the React component reacts to DOM changes itself.
    ui.mount()
  },
})
