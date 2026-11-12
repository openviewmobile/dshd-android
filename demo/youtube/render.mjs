#!/usr/bin/env node
/**
 * Render the 1920x1080 YouTube cut from the deterministic vertical demo.
 *
 *   node youtube/render.mjs en zh
 *
 * The vertical demo is mounted inside a phone mockup on the right; the left
 * side is HTML/CSS typography and captions. Every frame is seeked explicitly.
 */
import { chromium } from 'playwright-core';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEMO = path.resolve(HERE, '..');
const LANGS = process.argv.slice(2).filter((l) => l === 'en' || l === 'zh');
if (!LANGS.length) LANGS.push('en', 'zh');
const FPS = 30;
const TOTAL = 31.2;
const FRAMES = Math.round(FPS * TOTAL);
const LIMIT = Math.min(FRAMES, Number(process.env.YT_LIMIT || FRAMES));

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.mp4': 'video/mp4',
};

const server = http.createServer((req, res) => {
  try {
    const url = decodeURIComponent((req.url || '/').split('?')[0]);
    const file = path.join(DEMO, url);
    if (!file.startsWith(DEMO + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404).end('not found');
      return;
    }
    const stat = fs.statSync(file);
    const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
    const range = req.headers.range;
    if (range && type === 'video/mp4') {
      const match = /^bytes=(\d*)-(\d*)$/.exec(range);
      if (match) {
        const start = match[1] ? Number(match[1]) : 0;
        const end = match[2] ? Number(match[2]) : stat.size - 1;
        if (start >= stat.size || end < start) {
          res.writeHead(416, { 'Content-Range': `bytes */${stat.size}` }).end();
          return;
        }
        res.writeHead(206, {
          'Content-Type': type,
          'Accept-Ranges': 'bytes',
          'Content-Range': `bytes ${start}-${end}/${stat.size}`,
          'Content-Length': end - start + 1,
          'Cache-Control': 'no-store',
        });
        if (req.method === 'HEAD') return res.end();
        fs.createReadStream(file, { start, end }).pipe(res);
        return;
      }
    }
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': stat.size,
      'Accept-Ranges': type === 'video/mp4' ? 'bytes' : 'none',
      'Cache-Control': 'no-store',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(file).pipe(res);
  } catch (error) {
    res.writeHead(500).end(String(error));
  }
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const port = server.address().port;
console.log(`[server] http://127.0.0.1:${port}`);

const browser = await chromium.launch({
  channel: 'chrome',
  headless: true,
  args: ['--use-angle=metal', '--hide-scrollbars', '--mute-audio'],
});
const context = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  colorScheme: 'dark',
});

for (const lang of LANGS) {
  const outDir = path.join(HERE, 'frames', lang);
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });
  const page = await context.newPage();
  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log(`[console:${lang}]`, msg.text());
  });
  page.on('pageerror', (error) => console.log(`[pageerror:${lang}]`, String(error)));
  await page.goto(`http://127.0.0.1:${port}/youtube/src/scene.html?lang=${lang}`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForFunction('window.__ready === true || window.__error', null, { timeout: 30000 });
  const error = await page.evaluate(() => window.__error || '');
  if (error) throw new Error(`[${lang}] ${error}`);

  const started = Date.now();
  for (let i = 0; i < LIMIT; i++) {
    await page.evaluate((t) => window.__setTime(t), i / FPS);
    await page.screenshot({
      path: path.join(outDir, `${String(i).padStart(5, '0')}.jpg`),
      type: 'jpeg',
      quality: 94,
      caret: 'hide',
    });
    if (i % 90 === 0 || i === LIMIT - 1) {
      const rate = (i + 1) / ((Date.now() - started) / 1000);
      console.log(`[${lang}] ${i + 1}/${LIMIT} frames (${rate.toFixed(1)} fps)`);
    }
  }
  // The thumbnail frame is the first feature slide; keep a full-size source.
  await page.evaluate((t) => window.__setTime(t), 2.0);
  await page.screenshot({
    path: path.join(HERE, 'out', `thumb-${lang}.jpg`),
    type: 'jpeg',
    quality: 94,
    caret: 'hide',
  });
  await page.close();
}

await browser.close();
server.close();
console.log('done');
