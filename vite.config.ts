import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The edge server binds 0.0.0.0 so the ESP32 and phones can reach it over any
// exhibit hotspot/router. The Vite dev/preview server also binds 0.0.0.0 and
// proxies API calls to the edge server.
//
// By default the edge server is assumed to run on the SAME machine as Vite
// (127.0.0.1:5000). If the edge server runs elsewhere (e.g. a Raspberry Pi),
// set EDGE_SERVER_URL, e.g.:
//   EDGE_SERVER_URL=http://192.168.1.42:5000 npm run dev
// The browser always uses relative /api, /health, /analyse paths, so it never
// hardcodes a host and works from any device on the LAN.
const EDGE_SERVER_URL = process.env.EDGE_SERVER_URL || 'http://127.0.0.1:5000';

const proxy = {
  '/api': { target: EDGE_SERVER_URL, changeOrigin: true },
  '/health': { target: EDGE_SERVER_URL, changeOrigin: true },
  '/analyse': { target: EDGE_SERVER_URL, changeOrigin: true },
  '/audio': { target: EDGE_SERVER_URL, changeOrigin: true }
};

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy
  },
  preview: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy
  }
});
