/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { quasar, transformAssetUrls } from '@quasar/vite-plugin'

export default defineConfig({
  plugins: [
    vue({ template: { transformAssetUrls } }),
    // Inject the studio palette before Quasar's own (all `!default`) variables. An absolute
    // path is required: the import is prepended to Quasar's own stylesheet, so relative paths
    // resolve against node_modules and fail.
    quasar({
      sassVariables: fileURLToPath(new URL('./src/quasar-variables.sass', import.meta.url)),
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // Emit the built SPA into the Spring Boot static dir so the app serves it.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    // The dev server proxies API calls to the Spring app so the SPA needs no CORS handling.
    proxy: {
      '/api': { target: 'http://localhost:18080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
