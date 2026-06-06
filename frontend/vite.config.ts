import { defineConfig } from 'vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import react from '@vitejs/plugin-react'

const useHttps = process.env.npm_lifecycle_event === 'dev:https'

export default defineConfig({
  plugins: [
    react(),
    ...(useHttps
      ? [
          basicSsl({
            name: 'nutritrust-ai',
          }),
        ]
      : []),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
