#!/usr/bin/env node
/**
 * Deterministic frame renderer.
 *
 *   node render.mjs zh
 *   node render.mjs en
 *
 * Serves the demo folder on a random localhost port, opens Chrome through
 * Playwright at 1080x1920 @ DPR 1, and screenshots every frame after
 * window.__setTime(t). No screen recording, no wall-clock animation.
 */
import { chromium } from 'playwright-core';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const LANGS = args.length ? args.filter((l) => l === 'zh' || l === 'en') : ['zh', 'en'];
if (!LANGS.length) {
  console.error('usage: node render.mjs [zh|en ...]');
  process.exit(1);
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
};

const server = http.createServer((req, res) => {
  try {
    const url = decodeURIComponent((req.url || '/').split('?')[0]);
    let file = path.join(HERE, url);
    if (!file.startsWith(HERE)) {
      res.writeHead(403).end('forbidden');
      return;
    }
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
    if (!fs.existsSync(file)) {
      res.writeHead(404).end('not found');
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    fs.createReadStream(file).pipe(res);
  } catch (err) {
    res.writeHead(500).end(String(err));
  }
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const port = server.address().port;
console.log(`[server] http://127.0.0.1:${port}`);

const browser = await chromium.launch({
  channel: 'chrome',
  headless: true,
  args: ['--use-angle=metal', '--hide-scrollbars'],
});

const context = await browser.newContext({
  viewport: { width: 1080, height: 1920 },
  deviceScaleFactor: 1,
  locale: 'en-US',
  timezoneId: 'Asia/Shanghai',
  colorScheme: 'dark',
});

for (const lang of LANGS) {
  const timeline = JSON.parse(
    fs.readFileSync(path.join(HERE, 'out', `timeline.${lang}.json`), 'utf8')
  );
  const outDir = path.join(HERE, 'frames', lang);
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });

  const page = await context.newPage();
  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log(`[console:${lang}]`, msg.text());
  });
  page.on('pageerror', (err) => console.log(`[pageerror:${lang}]`, String(err)));

  await page.goto(`http://127.0.0.1:${port}/src/scene.html?lang=${lang}`, {
    waitUntil: 'networkidle',
  });
  await page.waitForFunction('window.__ready === true', null, { timeout: 30000 });

  const gl = await page.evaluate(() => {
    const c = document.createElement('canvas');
    const g = c.getContext('webgl2') || c.getContext('webgl');
    return g ? g.getParameter(g.VERSION) : 'none';
  });
  console.log(`[${lang}] webgl: ${gl}`);

  const frames = timeline.frames;
  const fps = timeline.fps;
  const started = Date.now();
  for (let i = 0; i < frames; i++) {
    const t = i / fps;
    await page.evaluate((tt) => window.__setTime(tt), t);
    await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => r())));
    await page.screenshot({
      path: path.join(outDir, `${String(i).padStart(5, '0')}.jpg`),
      type: 'jpeg',
      quality: 95,
      animations: 'disabled',
      caret: 'hide',
    });
    if (i % 60 === 0 || i === frames - 1) {
      const rate = (i + 1) / ((Date.now() - started) / 1000);
      console.log(
        `[${lang}] frame ${i + 1}/${frames} (${rate.toFixed(1)} fps, t=${t.toFixed(2)}s)`
      );
    }
  }
  console.log(`[${lang}] done in ${((Date.now() - started) / 1000).toFixed(1)}s`);
  await page.close();
}

await browser.close();
server.close();
