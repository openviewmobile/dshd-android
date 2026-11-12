#!/usr/bin/env node
/** Capture stills for review: node stills.mjs zh 1.2 5.6 9.4 ... */
import { chromium } from 'playwright-core';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const [lang = 'zh', ...times] = process.argv.slice(2);
if (!['zh', 'en'].includes(lang)) throw new Error('lang must be zh or en');
const shots = times.length ? times.map(Number) : [1.2, 5.4, 9.3, 13.2, 17.1, 21.4];
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
};
const server = http.createServer((req, res) => {
  let file = path.join(HERE, decodeURIComponent((req.url || '/').split('?')[0]));
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;
const browser = await chromium.launch({
  channel: 'chrome',
  headless: true,
  args: ['--use-angle=metal', '--hide-scrollbars'],
});
const page = await browser.newPage({
  viewport: { width: 1080, height: 1920 },
  deviceScaleFactor: 1,
  locale: 'en-US',
  timezoneId: 'Asia/Shanghai',
});
page.on('pageerror', (e) => console.log('[pageerror]', String(e)));
page.on('console', (m) => m.type() === 'error' && console.log('[console]', m.text()));
await page.goto(`http://127.0.0.1:${port}/src/scene.html?lang=${lang}`, { waitUntil: 'networkidle' });
await page.waitForFunction('window.__ready === true');
fs.mkdirSync(path.join(HERE, 'review'), { recursive: true });
for (const t of shots) {
  await page.evaluate((tt) => window.__setTime(tt), t);
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => r())));
  const out = path.join(HERE, 'review', `${lang}-${t.toFixed(2)}.png`);
  await page.screenshot({ path: out, type: 'png', animations: 'disabled', caret: 'hide' });
  console.log(out);
}
await browser.close();
server.close();
