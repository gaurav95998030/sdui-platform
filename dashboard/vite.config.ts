import { defineConfig } from 'vite';
import process from 'node:process';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxying keeps the dashboard same-origin, so no CORS or credential handling
    // is needed in the browser and the backend URL is configured in exactly one place.
    proxy: {
      '/api': {
        target: process.env.SDUI_BACKEND ?? 'http://localhost:8787',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
