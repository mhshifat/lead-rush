// Rasterise public/favicon.svg into the PNG + ICO variants browsers expect.
//
// Why: Nuxt's default `nuxt prepare` ships a generic favicon.ico in public/
// (from the starter template). Browsers prefer the /favicon.ico at the root
// over the <link rel="icon" type="image/svg+xml"> we declare in nuxt.config.ts,
// so a stale .ico wins in the tab bar regardless of the SVG. This script
// rebuilds both from the real brand mark.
//
// Outputs:
//   public/favicon.ico                — multi-size (16, 32, 48) ICO with PNGs
//   public/apple-touch-icon.png       — 180×180 for iOS/iPadOS home screens
//   public/icon-192.png, icon-512.png — PWA-friendly sizes (harmless if unused)
//
// Run manually: `node scripts/generate-favicons.mjs`
// Or hook into build: add `"prebuild": "node scripts/generate-favicons.mjs"` to package.json scripts.

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const svgPath = resolve(root, 'public/favicon.svg')
const publicDir = resolve(root, 'public')

if (!existsSync(svgPath)) {
  console.error(`source missing: ${svgPath}`)
  process.exit(1)
}

const svg = readFileSync(svgPath)

function rasterise(size) {
  const resvg = new Resvg(svg, { fitTo: { mode: 'width', value: size } })
  return resvg.render().asPng()
}

// ── Build multi-size ICO ──
//
// ICO file format (modern, PNG-embedded):
//
//   Header (6 bytes):
//     uint16  reserved = 0
//     uint16  type     = 1 (ICO)
//     uint16  count    = number of images
//
//   Per-image directory entry (16 bytes, repeated `count` times):
//     uint8   width            (0 means 256)
//     uint8   height           (0 means 256)
//     uint8   color_count      (0 for truecolor)
//     uint8   reserved         = 0
//     uint16  planes           = 1
//     uint16  bits_per_pixel   = 32
//     uint32  bytes_in_resource = length of the PNG data
//     uint32  image_offset      = byte offset from start of file to PNG data
//
//   Then each PNG blob in sequence.
function buildIco(pngs /* Array<{ size: number, data: Buffer }> */) {
  const headerSize = 6
  const entrySize = 16
  const directorySize = headerSize + entrySize * pngs.length

  const header = Buffer.alloc(headerSize)
  header.writeUInt16LE(0, 0)         // reserved
  header.writeUInt16LE(1, 2)         // type = ICO
  header.writeUInt16LE(pngs.length, 4) // image count

  let offset = directorySize
  const entries = []
  for (const { size, data } of pngs) {
    const entry = Buffer.alloc(entrySize)
    entry.writeUInt8(size === 256 ? 0 : size, 0)        // width
    entry.writeUInt8(size === 256 ? 0 : size, 1)        // height
    entry.writeUInt8(0, 2)                               // color count
    entry.writeUInt8(0, 3)                               // reserved
    entry.writeUInt16LE(1, 4)                            // planes
    entry.writeUInt16LE(32, 6)                           // bits per pixel
    entry.writeUInt32LE(data.length, 8)                  // bytes in image
    entry.writeUInt32LE(offset, 12)                      // offset to image
    entries.push(entry)
    offset += data.length
  }

  return Buffer.concat([header, ...entries, ...pngs.map(p => p.data)])
}

// 16 / 32 / 48 cover every real-world favicon request (tab bar, bookmarks,
// address bar, Windows shortcut). 48 is also the size Chrome uses in the
// "frequent sites" tile. We skip 64 and 256 to keep the ICO small.
const sizes = [16, 32, 48]
const pngs = sizes.map(size => ({ size, data: rasterise(size) }))
const ico = buildIco(pngs)
writeFileSync(resolve(publicDir, 'favicon.ico'), ico)
console.log(`✓ favicon.ico (${ico.length} bytes, ${sizes.length} embedded PNGs)`)

// ── Stand-alone PNGs for platforms that ignore ICO ──
writeFileSync(resolve(publicDir, 'apple-touch-icon.png'), rasterise(180))
console.log('✓ apple-touch-icon.png (180×180)')

writeFileSync(resolve(publicDir, 'icon-192.png'), rasterise(192))
console.log('✓ icon-192.png (192×192)')

writeFileSync(resolve(publicDir, 'icon-512.png'), rasterise(512))
console.log('✓ icon-512.png (512×512)')
