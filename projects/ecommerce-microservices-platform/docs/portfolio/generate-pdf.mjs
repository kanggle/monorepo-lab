import { spawn } from 'node:child_process';
import { writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const HTML_URL = process.argv[2];
const OUT_PDF  = process.argv[3];
const CHROME   = process.argv[4] || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

if (!HTML_URL || !OUT_PDF) {
  console.error('usage: node generate-pdf.mjs <html-url> <out-pdf> [chrome-path]');
  process.exit(2);
}

const port     = 9222 + Math.floor(Math.random() * 1000);
const userData = mkdtempSync(join(tmpdir(), 'cdp-pdf-'));

const chrome = spawn(CHROME, [
  `--remote-debugging-port=${port}`,
  '--headless=new',
  '--disable-gpu',
  '--hide-scrollbars',
  '--no-first-run',
  '--no-default-browser-check',
  `--user-data-dir=${userData}`,
  'about:blank',
], { stdio: 'ignore' });

const cleanup = () => {
  try { chrome.kill('SIGKILL'); } catch {}
  try { rmSync(userData, { recursive: true, force: true }); } catch {}
};
process.on('exit', cleanup);
process.on('SIGINT', () => { cleanup(); process.exit(130); });

// Wait until Chrome's CDP HTTP endpoint responds with a page target
let target = null;
for (let i = 0; i < 80; i++) {
  await new Promise(r => setTimeout(r, 250));
  try {
    const res = await fetch(`http://127.0.0.1:${port}/json/list`);
    const list = await res.json();
    target = list.find(t => t.type === 'page');
    if (target) break;
  } catch {}
}
if (!target) { console.error('Chrome did not expose a page target'); process.exit(1); }

// Connect to the page's CDP WebSocket
const ws = new WebSocket(target.webSocketDebuggerUrl);
let id = 0;
const pending = new Map();
ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    if (msg.error) reject(new Error(msg.error.message)); else resolve(msg.result);
  }
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const myId = ++id;
  pending.set(myId, { resolve, reject });
  ws.send(JSON.stringify({ id: myId, method, params }));
});
await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));

await send('Page.enable');
await send('Page.navigate', { url: HTML_URL });

// Poll for the Mermaid render-complete marker injected by the HTML
let ready = false;
for (let i = 0; i < 120; i++) {                  // up to 60s
  await new Promise(r => setTimeout(r, 500));
  try {
    const { result } = await send('Runtime.evaluate', {
      expression: 'document.body && document.body.getAttribute("data-mermaid-ready") === "true"',
      returnByValue: true,
    });
    if (result && result.value) { ready = true; break; }
  } catch {}
}
if (!ready) console.warn('warning: mermaid-ready marker never appeared — printing anyway');

// Wait for fonts to fully settle as well
await send('Runtime.evaluate', {
  expression: 'document.fonts && document.fonts.ready ? document.fonts.ready.then(() => true) : true',
  awaitPromise: true,
  returnByValue: true,
});
await new Promise(r => setTimeout(r, 800));

const { data } = await send('Page.printToPDF', {
  printBackground: true,
  preferCSSPageSize: true,
  marginTop: 0, marginBottom: 0, marginLeft: 0, marginRight: 0,
});

writeFileSync(OUT_PDF, Buffer.from(data, 'base64'));
console.log(`wrote ${OUT_PDF} (${(Buffer.from(data, 'base64').length / 1024).toFixed(0)} KB)`);

ws.close();
cleanup();
process.exit(0);
