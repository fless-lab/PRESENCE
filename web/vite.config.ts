/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// En développement, /api est relayé vers le serveur FastAPI (préfixe retiré).
// La cible se change avec VITE_API_PROXY (par défaut http://localhost:8000).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const cible = env.VITE_API_PROXY || 'http://localhost:8000';
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: cible,
          changeOrigin: true,
          rewrite: (chemin) => chemin.replace(/^\/api/, ''),
        },
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.ts'],
    },
  };
});
