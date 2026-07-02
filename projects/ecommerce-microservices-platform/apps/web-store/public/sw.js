/* web-store Web Push service worker (TASK-FE-083).
 * Displays a desktop/browser notification banner for pushes delivered by
 * notification-service (WebPushSender, TASK-BE-464) and focuses the notifications
 * page when the banner is clicked. Plain JS — served from /public, not bundled. */

self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (e) {
    payload = {};
  }

  const title = payload.title || '알림';
  const options = {
    body: payload.body || '',
    data: { url: '/my/notifications' },
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/my/notifications';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
