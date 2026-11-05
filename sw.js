// Shelfie service worker: handles the reading-timer notification buttons.
// If you already have a sw.js (e.g. for offline caching), keep your code and
// paste only the "notificationclick" listener below into it.
self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));

self.addEventListener('notificationclick',e=>{
  const act=e.action||'open';
  e.notification.close();
  e.waitUntil((async()=>{
    const cs=await self.clients.matchAll({type:'window',includeUncontrolled:true});
    if(cs.length){
      const c=cs[0];
      // Pause/Resume can happen quietly; Stop and tap need the app in front.
      if(act!=='pause'){try{await c.focus()}catch(_){}}
      c.postMessage({shelfie:act});
    }else{
      await self.clients.openWindow('./?act='+act);
    }
  })());
});
