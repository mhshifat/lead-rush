import { defineConfig } from 'wxt'

// WXT handles manifest generation from our entrypoints + the `manifest` block here.
// Host permissions include LinkedIn (for the content script) and the Lead Rush API
// (so background fetch() bypasses CORS preflights on chrome.runtime).
export default defineConfig({
  modules: ['@wxt-dev/module-react'],

  manifest: {
    name: 'Lead Rush',
    description: 'Import LinkedIn profiles and complete outreach tasks directly from LinkedIn.',
    version: '0.1.0',

    permissions: [
      'storage',          // persist API key + API base URL
      'activeTab',        // inject side panel only when user is on a profile
    ],

    host_permissions: [
      'https://*.linkedin.com/*',
      'http://localhost:8080/*',
      'https://*/api/v1/*',
    ],

    action: {
      default_title: 'Lead Rush',
    },
  },
})
