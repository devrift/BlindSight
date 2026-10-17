// BlindSight PWA Service Worker
const CACHE_NAME = 'blindsight-v1';
const ASSETS_TO_CACHE = [
  '/',
  '/static/manifest.json',
  '/static/icon.svg',
  '/static/icon-192.png',
  '/static/icon-512.png',
  '/static/apple-touch-icon.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  // Pass dynamic API and camera stream calls directly to network
  if (
    event.request.url.includes('/api/') ||
    event.request.url.includes('/analyse') ||
    event.request.url.includes('/audio/') ||
    event.request.url.includes('/health')
  ) {
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) {
        return cached;
      }
      return fetch(event.request).catch(async () => {
        if (event.request.mode === 'navigate') {
          const fallback = await caches.match('/');
          if (fallback) {
            return fallback;
          }
        }
        return new Response('Network offline', {
          status: 408,
          statusText: 'Request Offline',
          headers: { 'Content-Type': 'text/plain' },
        });
      });
    })
  );
});
