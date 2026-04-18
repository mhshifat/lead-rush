# Lead Rush — Chat Widget

Standalone embeddable chat widget. Builds to a single `widget.js` bundle that a customer can drop into their website to talk to a Lead Rush workspace.

## Stack

- Vite + TypeScript
- Connects to the backend's public chat endpoints and STOMP channel

## Run in dev

```bash
npm install
npm run dev
```

Opens a dev page with the widget mounted, pointing at a local backend.

## Build

```bash
npm run build
```

Outputs `dist/widget.js`. The backend serves this at `/public/widget.js` for customers to embed.

## Embed on a site

```html
<script
  src="https://YOUR_BACKEND/public/widget.js"
  data-workspace="acme"
  async>
</script>
```

The `data-workspace` attribute is the workspace slug. The widget auto-mounts in the bottom-right corner on page load.
