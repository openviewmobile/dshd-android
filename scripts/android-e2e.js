#!/usr/bin/env node
// Repeatable UI smoke test over the WebView DevTools socket.
// Requires: adb forward tcp:$CDP_PORT localabstract:webview_devtools_remote_<pid>
// and a running dshd Android app with a configured provider.
const port = process.env.CDP_PORT || '9224';
const prompt = process.env.E2E_PROMPT || '只回复 E2E-OK';
let seq = 0;

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.onopen = () => resolve(ws);
    ws.onerror = (event) => reject(new Error('cdp connect failed: ' + event.message));
  });
}

async function main() {
  const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const page = pages.find((p) => p.type === 'page' && !/devtools/.test(p.url));
  if (!page) throw new Error('no WebView page');
  const ws = await connect(page.webSocketDebuggerUrl);
  const pending = new Map();
  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
    }
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++seq;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', {
      expression, returnByValue: true, awaitPromise: true, userGesture: true
    });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'evaluate failed');
    return result.result?.value;
  };
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const waitFor = async (expression, timeoutMs, label) => {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (await evaluate(expression)) return;
      await sleep(1000);
    }
    throw new Error('timeout: ' + label);
  };

  const checks = [];
  const check = (name, ok, detail = '') => {
    checks.push({ name, ok, detail });
    console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
  };

  await waitFor("!!document.querySelector('[data-composer-input]')", 30000, 'composer');
  check('webview shell and composer', true);

  // Fresh session keeps the smoke test independent of previous history.
  await evaluate(`(() => { const labels=['新建会话','New session']; const b=[...document.querySelectorAll('button')].find(e=>labels.includes(e.getAttribute('aria-label'))); if (b) b.click(); return !!b; })()`);
  await sleep(1500);
  await waitFor("!!document.querySelector('[data-composer-input]')", 15000, 'new session composer');

  await evaluate(`document.querySelector('[data-composer-input]').focus()`);
  await send('Input.insertText', { text: prompt });
  await evaluate(`(() => { const labels=['发送消息','Send message']; const b=[...document.querySelectorAll('button')].find(e=>labels.includes(e.getAttribute('aria-label'))); if (b) b.click(); return !!b; })()`);
  await waitFor(`document.body.innerText.includes('已完成') || document.body.innerText.includes('Completed') || document.body.innerText.includes('处理失败') || document.body.innerText.includes('Failed')`, 90000, 'agent response');
  const text = await evaluate('document.body.innerText');
  check('agent round trip', text.includes('已完成') || text.includes('Completed'), text.includes('E2E-OK') ? 'E2E-OK' : 'response rendered');

  const sessionId = await evaluate(`JSON.parse(localStorage.getItem('dsh.sessions.current')).sessionId`);
  const upload = await evaluate(`new Promise((resolve) => {
    const x = new XMLHttpRequest();
    x.open('POST', '/api/session/uploadFileBinary?sessionId=${sessionId}&name=e2e.txt', true);
    x.setRequestHeader('content-type', 'application/octet-stream');
    x.onload = () => resolve({ status: x.status, body: x.responseText });
    x.onerror = () => resolve({ status: 0, body: 'network' });
    x.send(new Blob(['android e2e']));
  })`);
  let uploaded = false;
  try { uploaded = JSON.parse(upload.body).ok === true; } catch {}
  check('attachment upload route', upload.status === 200 && uploaded, `HTTP ${upload.status}`);

  // A picker control must exist for the native WebChromeClient integration.
  check('file chooser input present', await evaluate("!!document.querySelector('input[type=file]')"));

  ws.close();
  const failed = checks.filter((c) => !c.ok);
  console.log(`\n${checks.length - failed.length}/${checks.length} checks passed`);
  if (failed.length) process.exit(1);
}

main().catch((error) => { console.error('E2E ERROR:', error.message); process.exit(1); });
