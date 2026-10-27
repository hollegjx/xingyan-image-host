import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import { designPreviewPlugin } from './dev/design-preview'

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  const design = mode === 'design'
  if (design && command !== 'serve')
    throw new Error('Design fixtures are for local development only; use build:tc for production.')
  return {
    // Also pin direct Blob/WebSocket helpers to the local preview origin.
    define: design ? { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('') } : undefined,
    plugins: [vue(), vueJsx(), ...(design ? [designPreviewPlugin()] : [])],
    server: {
      host: 'localhost',
      proxy: design
        ? undefined
        : {
            '/api': {
              target: 'http://localhost:8123',
              changeOrigin: true,
              ws: true,
            },
          },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
