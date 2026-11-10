// do-accounts Service Worker（SD 5.9 PWA）
// 只處理同網域的 GET：頁面網路優先（離線時用快取的首頁），打包檔（檔名帶雜湊，不會變）快取優先。
// API 在另一個網域（Railway），不經過這裡，資料永遠向伺服器取得，不做離線記帳。
const VERSION = 'v1'
const CACHE = `do-accounts-${VERSION}`
const SHELL = ['./', './index.html', './manifest.webmanifest', './favicon.svg', './icon-192.png']
const MAX_ASSETS = 80

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith('do-accounts-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()))
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)
  if (url.origin !== self.location.origin) return

  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).then(res => {
      const copy = res.clone()
      caches.open(CACHE).then(c => c.put('./index.html', copy))
      return res
    }).catch(() => caches.match('./index.html')))
    return
  }

  if (url.pathname.includes('/assets/')) {
    event.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
      if (res.ok) {
        const copy = res.clone()
        caches.open(CACHE).then(async c => {
          await c.put(req, copy)
          const keys = (await c.keys()).filter(k => k.url.includes('/assets/'))
          // 舊版本的打包檔不再使用，超過上限時從最舊的刪起
          for (const k of keys.slice(0, Math.max(0, keys.length - MAX_ASSETS))) await c.delete(k)
        })
      }
      return res
    })))
    return
  }

  // 其他靜態檔：先回快取、背景更新
  event.respondWith(caches.match(req).then(hit => {
    const net = fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)) }
      return res
    }).catch(() => hit)
    return hit || net
  }))
})
