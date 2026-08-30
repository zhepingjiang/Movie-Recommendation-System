import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Docker Desktop's bind mount of a Windows host path into the Linux container doesn't
    // propagate inotify events, so Vite's default watcher never sees file changes made on the
    // Windows side -- polling is the only way HMR picks them up.
    watch: {
      usePolling: true,
    },
  },
})
