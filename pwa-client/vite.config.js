import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({ 
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'masked-icon.svg'],
      manifest: {
        name: 'Prediction App',
        short_name: 'Predict',
        description: 'Policy Event Based Market Prediction',
        theme_color: '#ffffff',
        icons: [] // Ignoring icons for quick dummy test
      }
    })
  ]
})
