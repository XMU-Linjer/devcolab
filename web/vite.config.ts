import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const port = Number(env.VITE_DEV_PORT || 5173);

  return {
    plugins: [vue()],
    build: {
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('@tiptap') || id.includes('prosemirror')) {
                return 'vendor-editor';
              }
              if (id.includes('element-plus') || id.includes('@element-plus')) {
                return 'vendor-element-plus';
              }
              if (
                id.includes('/vue/')
                || id.includes('vue-router')
                || id.includes('pinia')
              ) {
                return 'vendor-vue';
              }
              if (id.includes('axios')) {
                return 'vendor-http';
              }
              return 'vendor';
            }
            return undefined;
          }
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port,
      proxy: {
        '/agent-api': {
          target: env.VITE_AGENT_API_PROXY_TARGET || 'http://localhost:8092',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/agent-api/, ''),
        },
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws': {
          target: env.VITE_WS_PROXY_TARGET || 'ws://localhost:8090',
          ws: true,
          changeOrigin: true,
        },
      },
    },
  };
});
