/* Service worker za potisna obvestila (Web Push, paket Navijač). Samo toliko: prikaže
   obvestilo, ki ga pošlje strežnik (PotisnaObvestilaStoritev), in ob kliku odpre stran.
   Ničesar ne predpomni: aplikacija ostane na spletu, delovanje brez povezave ni zahteva
   (PRODUCT.md). Vsebina obvestila je JSON {title, body, url, tag}; ista oznaka (tag)
   nadomesti prejšnje obvestilo o isti tekmi. */
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (dogodek) => dogodek.waitUntil(self.clients.claim()))

self.addEventListener('push', (dogodek) => {
  let podatki = {}
  try {
    podatki = dogodek.data ? dogodek.data.json() : {}
  } catch {
    podatki = { title: 'Turnirko', body: dogodek.data ? dogodek.data.text() : '' }
  }
  dogodek.waitUntil(
    self.registration.showNotification(podatki.title || 'Turnirko', {
      body: podatki.body || '',
      tag: podatki.tag || undefined,
      icon: '/ikona-192.png',
      data: { url: podatki.url || '/' },
    }),
  )
})

self.addEventListener('notificationclick', (dogodek) => {
  dogodek.notification.close()
  const url = (dogodek.notification.data && dogodek.notification.data.url) || '/'
  dogodek.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((okna) => {
      for (const okno of okna) {
        if ('focus' in okno) {
          if ('navigate' in okno) okno.navigate(url)
          return okno.focus()
        }
      }
      return self.clients.openWindow(url)
    }),
  )
})
