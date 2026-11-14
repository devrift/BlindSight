import { defineConfig, Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'fs';
import path from 'path';

function apkMimePlugin(): Plugin {
  return {
    name: 'apk-mime-type',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url && (req.url === '/BlindSight-testing.apk' || req.url.startsWith('/BlindSight-testing.apk?') || req.url.endsWith('.apk'))) {
          const apkPath = path.resolve(process.cwd(), 'public', 'BlindSight-testing.apk');
          if (fs.existsSync(apkPath)) {
            const stat = fs.statSync(apkPath);
            res.writeHead(200, {
              'Content-Type': 'application/vnd.android.package-archive',
              'Content-Disposition': 'attachment; filename="BlindSight-testing.apk"',
              'Content-Length': stat.size,
              'Cache-Control': 'no-cache'
            });
            if (req.method === 'HEAD') {
              res.end();
              return;
            }
            fs.createReadStream(apkPath).pipe(res);
            return;
          }
        }
        next();
      });
    }
  };
}

export default defineConfig({
  plugins: [react(), apkMimePlugin()],
  envPrefix: ['VITE_', 'GEMINI_'],
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    watch: {
      ignored: ['**/build/**', '**/.gradle/**', '**/app/build/**', '**/*.apk', '**/edge_server/**', '**/*.log']
    },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      },
      '/health': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      },
      '/analyse': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      },
      '/health': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      },
      '/analyse': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      }
    }
  }
});
