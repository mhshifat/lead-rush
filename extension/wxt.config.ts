import { defineConfig } from 'wxt'

// WXT handles manifest generation from our entrypoints + the `manifest` block here.
// Host permissions include LinkedIn (for the content script) and the Lead Rush API
// (so background fetch() bypasses CORS preflights on chrome.runtime).
export default defineConfig({
  modules: ['@wxt-dev/module-react'],

  manifest: {
    name: 'Lead Rush — LinkedIn & Gmail sidekick',
    short_name: 'Lead Rush',
    description:
      'Import LinkedIn and Sales Navigator profiles into your Lead Rush workspace, '
      + 'see a contact card for any open Gmail thread, drop notes, and complete '
      + 'outreach tasks without leaving the page.',
    version: '0.1.0',
    author: { email: 'hello@leadrush.com' },
    homepage_url: 'https://leadrush.com',

    // Icons rasterized from public/icon.svg by scripts/generate-icons.mjs.
    // Chrome requires PNG; SVG is not accepted in MV3 `icons` blocks.
    icons: {
      16: 'icon/16.png',
      32: 'icon/32.png',
      48: 'icon/48.png',
      128: 'icon/128.png',
    },

    permissions: [
      'storage',          // persist API key + API base URL
      'activeTab',        // inject side panel only when user is on a profile
      'alarms',           // periodic task-count badge refresh in the background worker
      'contextMenus',     // right-click "Capture to Lead Rush" on LinkedIn links anywhere
      'notifications',    // toast confirmation after a background capture
    ],

    // Keyboard shortcuts — user can rebind in chrome://extensions/shortcuts.
    commands: {
      'import-current-profile': {
        suggested_key: {
          default: 'Ctrl+Shift+L',
          mac: 'Command+Shift+L',
        },
        description: 'Import the current LinkedIn profile to Lead Rush',
      },
    },

    host_permissions: [
      'https://*.linkedin.com/*',
      'https://mail.google.com/*',
      'http://localhost:8080/*',
      'https://*/api/v1/*',
    ],

    action: {
      default_title: 'Lead Rush',
      default_icon: {
        16: 'icon/16.png',
        32: 'icon/32.png',
        48: 'icon/48.png',
        128: 'icon/128.png',
      },
    },
  },
})
