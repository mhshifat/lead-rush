// Rasterize public/icon.svg into the sizes Chrome + Firefox stores expect.
// Run automatically via `npm run prebuild` — also safe to invoke manually.
//
// Output: public/icon/16.png, 32.png, 48.png, 128.png — wxt picks these up from
// the `icons:` block in wxt.config.ts.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const svgPath = resolve(root, 'public/icon.svg')
const outDir = resolve(root, 'public/icon')

if (!existsSync(svgPath)) {
  console.error(`icon source missing: ${svgPath}`)
  process.exit(1)
}
if (!existsSync(outDir)) mkdirSync(outDir, { recursive: true })

const svg = readFileSync(svgPath)
const sizes = [16, 32, 48, 128]

for (const size of sizes) {
  const resvg = new Resvg(svg, { fitTo: { mode: 'width', value: size } })
  const png = resvg.render().asPng()
  writeFileSync(resolve(outDir, `${size}.png`), png)
  console.log(`✓ icon/${size}.png`)
}
