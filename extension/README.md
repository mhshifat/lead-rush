# Lead Rush — Browser Extension

Chrome/Firefox extension that turns LinkedIn and Sales Navigator into a Lead Rush workstation. Import profiles, see which ones are already contacts, drop notes, enroll into sequences, and knock out outreach tasks — all without leaving the page.

## Stack

- WXT (web-extension toolkit)
- React 18 + TypeScript
- Vitest + happy-dom (scraper tests)

## Features

| Feature | Where |
|---|---|
| **Import LinkedIn profile** (basics + About + experiences + education + skills) | Side panel + `Ctrl/Cmd+Shift+L` hotkey |
| **Bulk-import from search results** — one click imports every visible row on `/search/results/people/…` or `/sales/search/people/…` | Side panel auto-switches to bulk mode on search pages |
| **Sales Navigator support** — `/sales/lead/*` and `/sales/people/*` | Side panel mounts on both layouts |
| **"Already in Lead Rush" card** — name, score, lifecycle, last touch, active sequences | Side panel (rendered when URL matches a contact) |
| **Enroll into a sequence** — picks from active sequences with default mailbox | Side panel (enabled once contact is imported) |
| **Drop a note** — shows on the contact's activity timeline | Side panel (enabled once contact is imported) |
| **Pending tasks** for the viewed contact — complete with one click | Side panel |
| **Browser icon badge** — count of pending LinkedIn tasks across the workspace | Refreshes every 5 min + on every mutation |
| **Auth-expired recovery** — dedicated "Reconnect" flow when the API key is revoked | Both popup + side panel |
| **Scraper telemetry** — pings the backend when selectors miss | Silent, best-effort |

## Prerequisites

- Node 20+
- A Lead Rush workspace API key (generate one at `/settings/api-keys` in the web app — the extension authenticates via `X-API-Key`)

## Run in dev

```bash
npm install
npm run dev           # Chrome
npm run dev:firefox   # Firefox
```

WXT launches a browser with the unpacked extension loaded. Hot reloads on source changes.

## Build

```bash
npm run build           # Chrome — outputs to .output/chrome-mv3/
npm run build:firefox   # Firefox — outputs to .output/firefox-mv2/
npm run zip             # packaged zip for store submission
```

The `prebuild` hook rasterizes [`public/icon.svg`](public/icon.svg) into 16/32/48/128 PNG variants that Chrome requires.

## Tests

```bash
npm test              # one-shot
npm run test:watch    # rerun on change
```

Scraper tests live in [`lib/__tests__/`](lib/__tests__/) with HTML fixtures that stand in for real LinkedIn and Sales Navigator DOM. When LinkedIn ships a selector change, update the scraper + fixture in the same PR so the regression stays green.

## Configure

On first launch, open the popup and paste your API key + workspace URL (e.g. `http://localhost:8080`). Stored in `chrome.storage.local`.

Keyboard shortcut is `Ctrl+Shift+L` (Windows/Linux) or `Cmd+Shift+L` (Mac); rebind at `chrome://extensions/shortcuts`.

## Structure

- [`entrypoints/`](entrypoints/) — manifest entry points (popup, content scripts, background service worker)
- [`components/`](components/) — shared React components
- [`lib/`](lib/) — API client, storage, messaging helpers, LinkedIn scraper
- [`public/`](public/) — static assets (icon SVG + generated PNGs)
- [`scripts/generate-icons.mjs`](scripts/generate-icons.mjs) — rasterizes the brand mark into store-ready sizes

## Architecture

All network traffic goes through the background service worker — content scripts and the popup send typed messages via `chrome.runtime.sendMessage`. This pattern:

- bypasses CORS on LinkedIn content scripts (host_permissions cover the API)
- centralizes the X-API-Key header in one place
- lets the background keep the badge fresh on a periodic `chrome.alarms` tick

The background returns `{ ok, data | error, code }`. UI branches on `code === 'AUTH_REQUIRED'` to surface a Reconnect affordance instead of a raw stack trace.
