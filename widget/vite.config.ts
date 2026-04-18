import { defineConfig } from 'vite'
import { resolve } from 'node:path'

/**
 * Builds a single self-contained widget.js file that customers can drop onto
 * any site with a <script src=".../widget.js" data-workspace="..."> tag.
 *
 * IIFE output so the bundle executes immediately — no import maps, no modules,
 * no framework runtime. Keeps the footprint small.
 */
export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/widget.ts'),
      name: 'LeadRushWidget',
      formats: ['iife'],
      fileName: () => 'widget.js',
    },
    rollupOptions: {
      // No externals — everything gets inlined for a drop-in bundle
      output: { extend: true },
    },
    outDir: 'dist',
    emptyOutDir: true,
    minify: 'esbuild',
    cssCodeSplit: false,
  },
})
