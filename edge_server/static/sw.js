// BlindSight PWA Service Worker
//
// Registered with an explicit scope of '/' and served by the gateway with a
// `Service-Worker-Allowed: /` header. Without that, a worker living at
// /static/sw.js is scoped to /static/ only and never controls the page at /,
// which made the precache list and the offline navigation fallback inert.
const CACHE_NAME = 'blindsight-v2';
const ASSETS_TO_CACHE = [
  '/',
  '/static/manifest.json',
  '/static/icon.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS_TO_CACHE))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;

  // Only handle GET; never intercept the dynamic API, upload or audio routes.
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') ||
      url.pathname.startsWith('/analyse') ||
      url.pathname.startsWith('/audio/')) {
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        // Cache successful same-origin GETs for offline use.
        if (response && response.ok && request.mode !== 'only-if-cached') {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        }
        return response;
      }).catch(() => {
        if (request.mode === 'navigate') {
          // Always resolves to a Response (or rejects) — respondWith must never
          // be given a promise that resolves to undefined, which is a spec
          // violation that turns the miss into a hard error for the page.
          return caches.match('/').then((fallback) => {
            if (fallback) return fallback;
            return new Response(
              '<h1>BlindSight is offline</h1><p>The gateway cannot be reached.</p>',
              { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } }
            );
          });
        }
        return new Response('', { status: 504, statusText: 'Offline' });
      });
    })
  );
});
