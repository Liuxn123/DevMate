import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    proxy: {
      // The browser calls /api; Vite forwards that path to the local Spring server.
      '/api': 'http://127.0.0.1:8080',
    },
  },
})