# Lead Rush — Browser Extension

Chrome/Firefox extension that imports LinkedIn profiles into Lead Rush and surfaces pending tasks from the web app.

## Stack

- WXT (web-extension toolkit)
- React 18 + TypeScript

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

## Configure

On first launch, open the popup and paste your API key + workspace URL (e.g. `http://localhost:4000`). Stored in `chrome.storage.local`.

## Structure

- [`entrypoints/`](entrypoints/) — manifest entry points (popup, content scripts, background)
- [`components/`](components/) — shared React components
- [`lib/`](lib/) — API client, storage helpers
