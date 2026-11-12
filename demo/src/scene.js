import * as THREE from 'three';

/* ====================================================================== */
/*  DSHD first-use demo — deterministic 1080x1920 motion piece            */
/*  Every frame is a pure function of t (window.__setTime).               */
/* ====================================================================== */

const params = new URLSearchParams(location.search);
const LANG = params.get('lang') === 'en' ? 'en' : 'zh';
document.body.classList.add(`lang-${LANG}`);
document.documentElement.lang = LANG === 'zh' ? 'zh-CN' : 'en';

const timeline = await fetch(`/out/timeline.${LANG}.json`).then((r) => r.json());
const BEATS = timeline.beats;
const TOTAL = timeline.total;

const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, v) => {
  const t = clamp((v - a) / (b - a || 1e-6));
  return t * t * (3 - 2 * t);
};
const easeOut = (v) => 1 - Math.pow(1 - clamp(v), 3);

/* --------------------------------------------------------------- icons */
const I = {
  logo: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="12" cy="4.6" r="2.1"/><circle cx="4.8" cy="17.4" r="2.1"/><circle cx="19.2" cy="17.4" r="2.1"/><path d="M10.7 6.6 6.2 15.3M13.3 6.6l4.5 8.7M6.9 17.4h10.2"/></svg>`,
  check: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12.5 4.4 4.4L19 6.6"/></svg>`,
  back: `<svg class="back" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m14.5 5-7 7 7 7"/></svg>`,
  chev: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 5 7 7-7 7"/></svg>`,
  lock: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="10.5" width="15" height="10" rx="2.6"/><path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7"/><circle cx="12" cy="15.6" r="1.1" fill="currentColor" stroke="none"/></svg>`,
  shield: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2.8 4.8 5.7v5.4c0 4.6 3 8.3 7.2 10.1 4.2-1.8 7.2-5.5 7.2-10.1V5.7L12 2.8Z"/><path d="m8.7 11.8 2.3 2.3 4.3-4.6"/></svg>`,
  file: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3.5h7.5L19 9v11.5H6z"/><path d="M13.3 3.7V9H19"/></svg>`,
  text: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M5 6.5h14M5 11h14M5 15.5h9M5 20h6"/></svg>`,
  folder: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3.8 6.8h6l1.8 2.2h8.6v9.6H3.8z"/></svg>`,
  terminal: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4.5" width="18" height="15" rx="2.5"/><path d="m7 9.5 3.2 2.8L7 15M12.8 15H17"/></svg>`,
  spark: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9z"/></svg>`,
  globe: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><circle cx="12" cy="12" r="8.5"/><path d="M3.7 12h16.6M12 3.5c2.4 2.3 3.6 5.2 3.6 8.5s-1.2 6.2-3.6 8.5c-2.4-2.3-3.6-5.2-3.6-8.5S9.6 5.8 12 3.5Z"/></svg>`,
  camera: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4.2 8.2h3.2l1.4-2h6.4l1.4 2h3.2v11H4.2z"/><circle cx="12" cy="13.5" r="3.2"/></svg>`,
  pin: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s6.2-5.6 6.2-10.1A6.2 6.2 0 0 0 5.8 10.9C5.8 15.4 12 21 12 21Z"/><circle cx="12" cy="10.6" r="2.2"/></svg>`,
  db: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><ellipse cx="12" cy="6" rx="7.5" ry="3"/><path d="M4.5 6v12c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V6M4.5 12c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3"/></svg>`,
  signal: `<svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5.4" width="3" height="6.6" rx="1"/><rect x="10" y="2.8" width="3" height="9.2" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></svg>`,
  wifi: `<svg width="17" height="13" viewBox="0 0 17 13" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M1.4 4.2a11 11 0 0 1 14.2 0M3.8 6.9a7.3 7.3 0 0 1 9.4 0M6.2 9.6a3.6 3.6 0 0 1 4.6 0"/><circle cx="8.5" cy="11.8" r="0.9" fill="currentColor" stroke="none"/></svg>`,
  battery: `<svg width="26" height="13" viewBox="0 0 26 13" fill="none"><rect x="0.6" y="0.6" width="21.5" height="11.8" rx="3.2" stroke="currentColor" stroke-opacity="0.5"/><rect x="2.6" y="2.6" width="16" height="7.8" rx="1.8" fill="currentColor"/><path d="M23.6 4.2v4.6c1.2-.3 1.9-1.1 1.9-2.3s-.7-2-1.9-2.3Z" fill="currentColor" fill-opacity="0.55"/></svg>`,
};

/* --------------------------------------------------------------- copy */
const S = {
  zh: {
    hudNote: '本地运行',
    splash: { sub: '随身智能工作站', foot: '安卓 · 本地运行' },
    boot: {
      title: '首次启动',
      pill: '内置运行时',
      state: '正在启动',
      done: '已就绪',
      lines: [
        ['解压内置运行时', '一次性'],
        ['启动本地服务', '数秒'],
        ['挂载工作区', '完成'],
        ['运行时就绪', '就绪'],
      ],
      hint: '一次性解包 · 无需下载',
      tiles: ['终端', '模型', '文件'],
    },
    provider: {
      bar: '设置',
      h2: '连接你的模型',
      desc: '填入服务地址与访问密钥，保存在应用私有目录。',
      labels: ['服务地址', '访问密钥', '模型'],
      values: ['私有网关', '••••••••••••', '自建模型'],
      filled: '已填写',
      note: '仅保存在本机，加密存储',
      cta: '保存并启动',
      foot: '保存后立即启动本地运行时',
      local: {
        title: '本机运行时',
        rows: [['运行时', '已内置'], ['存储位置', '应用私有目录'], ['网络请求', '仅访问模型服务']],
      },
    },
    files: {
      bar: '文件',
      title: '工作区',
      count: '4 个文件',
      rows: [
        ['项目说明', '文本 · 12 千字节', 'doc', ''],
        ['会议纪要', '文本 · 8 千字节', 'text', 'green'],
        ['调研笔记', '文本 · 2.4 千字节', 'doc', 'amber'],
        ['发布清单', '目录 · 6 项', 'folder', 'green'],
      ],
      preview: {
        name: '调研笔记',
        tag: '文本',
        body: '端侧运行时已经就绪。所有文件都保存在手机本地，预览不需要上传。',
        meta: ['2.4 千字节', '刚刚修改'],
        action: '在编辑器中打开',
      },
      storage: {
        label: '本地存储',
        used: '2.1 吉字节',
        total: '64 吉字节',
        foot: ['应用私有', '离线可用'],
        pct: 3.3,
      },
    },
    terminal: {
      bar: '终端',
      chip: '本地',
      lines: [
        ['cmd', '查看工作区'],
        ['out', '项目说明   调研笔记   发布清单'],
        ['cmd', '检查运行时'],
        ['ok', '✓ 运行时正常 · 延迟 8 毫秒'],
        ['cmd', '查看设备'],
        ['out', '处理器 8 核 · 内存 7.4 吉字节'],
        ['cmd', '查看存储'],
        ['out', '工作区 2.1 吉字节 · 可用 61 吉字节'],
        ['cmd', ''],
      ],
      stats: [['处理器', '12%', 12], ['内存', '3.1 吉字节', 42], ['存储', '2.1 吉字节', 3.3]],
      foot: '本地执行',
      right: '延迟 8 毫秒',
    },
    privacy: {
      bar: '权限与隐私',
      h2: '本地文件，本机运行时',
      desc: '只申请完成工作所需的权限。',
      rows: [
        ['存储', '工作区文件', '已允许', 'on', 'folder', ''],
        ['网络', '模型连接', '已允许', 'on', 'globe', 'teal'],
        ['相机', '按需调用', '询问', 'ask', 'camera', 'amber'],
        ['位置', '不申请', '关闭', 'off', 'pin', 'grey'],
      ],
      foot: '应用私有存储 · 本地运行',
      notes: [
        ['应用私有存储', '工作区与密钥只保存在本机'],
        ['请求可控', '仅连接你配置的模型服务'],
        ['随时可撤销', '权限变更立即生效'],
      ],
      sealTitle: '文件与运行时留在本机',
      sealSub: '本地文件 · 本机运行时',
    },
  },
  en: {
    hudNote: 'ON DEVICE',
    splash: { sub: 'AGENT WORKSPACE', foot: 'ANDROID · ON DEVICE' },
    boot: {
      title: 'First launch',
      pill: 'BUILT IN',
      state: 'STARTING',
      done: 'READY',
      lines: [
        ['Unpacking runtime', 'ONE-TIME'],
        ['Starting local service', '~5s'],
        ['Mounting workspace', 'DONE'],
        ['Runtime ready', 'READY'],
      ],
      hint: 'ONE-TIME UNPACK · NO DOWNLOAD',
      tiles: ['TERMINAL', 'MODEL', 'FILES'],
    },
    provider: {
      bar: 'Settings',
      h2: 'Connect your model',
      desc: 'Add a base URL and API key. They stay in app-private storage.',
      labels: ['BASE URL', 'API KEY', 'MODEL'],
      values: ['https://api.example.com/v1', '••••••••••••', 'DeepSeek V4'],
      filled: 'Saved',
      note: 'Stored only on this device, encrypted',
      cta: 'SAVE & START',
      foot: 'Saving starts the on-device runtime',
      local: {
        title: 'ON-DEVICE RUNTIME',
        rows: [['Runtime', 'Built in'], ['Storage', 'App-private directory'], ['Network', 'Model requests only']],
      },
    },
    files: {
      bar: 'Files',
      title: 'Workspace',
      count: '4 files',
      rows: [
        ['Project brief', 'TEXT · 12 KB', 'doc', ''],
        ['Meeting notes', 'TEXT · 8 KB', 'text', 'green'],
        ['Research notes', 'TEXT · 2.4 KB', 'doc', 'amber'],
        ['Release checklist', 'FOLDER · 6 items', 'folder', 'green'],
      ],
      preview: {
        name: 'Research notes',
        tag: 'TEXT',
        body: 'The on-device runtime is ready. Every file stays on this phone, and previews never leave it.',
        meta: ['2.4 KB', 'edited just now'],
        action: 'OPEN IN EDITOR',
      },
      storage: {
        label: 'LOCAL STORAGE',
        used: '2.1 GB',
        total: '64 GB',
        foot: ['App-private', 'Available offline'],
        pct: 3.3,
      },
    },
    terminal: {
      bar: 'Terminal',
      chip: 'LOCAL',
      lines: [
        ['cmd', 'dshd status'],
        ['out', 'runtime ready · latency 8 ms'],
        ['cmd', 'ls workspace'],
        ['ok', '✓ 3 files · all local'],
        ['cmd', 'dshd doctor'],
        ['out', 'workspace mounted · model reachable'],
        ['cmd', 'df -h'],
        ['out', 'workspace 2.1 GB · 61 GB free'],
        ['cmd', ''],
      ],
      stats: [['CPU', '12%', 12], ['MEMORY', '3.1 GB', 42], ['STORAGE', '2.1 GB', 3.3]],
      foot: 'Local execution',
      right: '8 ms',
    },
    privacy: {
      bar: 'Permissions',
      h2: 'Local files, local runtime',
      desc: 'Only the permissions the work needs.',
      rows: [
        ['Storage', 'workspace files', 'Allowed', 'on', 'folder', ''],
        ['Network', 'model access', 'Allowed', 'on', 'globe', 'teal'],
        ['Camera', 'on demand', 'Ask', 'ask', 'camera', 'amber'],
        ['Location', 'not requested', 'Off', 'off', 'pin', 'grey'],
      ],
      foot: 'APP-PRIVATE STORAGE · LOCAL RUNTIME',
      notes: [
        ['App-private storage', 'Workspace and keys stay on this phone'],
        ['Scoped requests', 'Only the model service you configure'],
        ['Revocable anytime', 'Permission changes apply instantly'],
      ],
      sealTitle: 'FILES & RUNTIME',
      sealSub: 'STAY ON YOUR DEVICE',
    },
  },
}[LANG];

const statusbar = () =>
  `<div class="statusbar"><span class="sb-time">9:41</span><span class="sb-icons">${I.signal}${I.wifi}${I.battery}</span></div>`;
const homebar = `<div class="homebar"></div>`;

/* --------------------------------------------------------------- hero */
const heroRoot = document.getElementById('hero');
const heroEls = BEATS.map((b) => {
  const el = document.createElement('div');
  el.className = `hero-slide${b.id === 'title' ? ' is-title' : ''}`;
  el.innerHTML = `
    <div class="hero-kicker">${b.kicker}</div>
    <div class="hero-title">${b.title}</div>
    <div class="hero-sub">${b.subtitle}</div>`;
  heroRoot.appendChild(el);
  return el;
});

document.getElementById('hudNote').textContent = S.hudNote;
document.getElementById('hudMark').innerHTML = I.logo;

/* ------------------------------------------------------------- screens */
const screensRoot = document.getElementById('screens');

function bootScreen() {
  return `<div class="scr scr-boot" data-screen="boot">
    ${statusbar()}
    <div class="boot-head"><div class="bh-title">${S.boot.title}</div><div class="pill">${S.boot.pill}</div></div>
    <div class="boot-ring-wrap">
      <svg class="boot-ring" viewBox="0 0 214 214">
        <defs><linearGradient id="bootGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#6f9cff"/><stop offset="0.55" stop-color="#66d9ff"/><stop offset="1" stop-color="#7ef2c7"/>
        </linearGradient></defs>
        <circle class="track" cx="107" cy="107" r="96"/>
        <circle class="prog" id="bootProg" cx="107" cy="107" r="96" stroke-dasharray="603.19" stroke-dashoffset="603.19"/>
      </svg>
      <div class="boot-center"><div class="boot-pct"><span id="bootPct">0</span><small>%</small></div>
      <div class="boot-state" id="bootState">${S.boot.state}</div></div>
    </div>
    <div class="boot-lines">${S.boot.lines
      .map(
        (l, i) =>
          `<div class="boot-line pending" data-boot="${i}"><span class="bcheck">${I.check}</span><span>${l[0]}</span><span class="btime">${l[1]}</span></div>`
      )
      .join('')}</div>
    <div class="feature-tiles" id="bootTiles">
      ${S.boot.tiles
        .map(
          (label, i) =>
            `<div class="feature-tile">${[I.terminal, I.globe, I.folder][i]}<span>${label}</span></div>`
        )
        .join('')}
    </div>
    <div class="boot-hint">${S.boot.hint}</div>
    ${homebar}
  </div>`;
}

function providerScreen() {
  const fields = S.provider.labels
    .map((label, i) => {
      const value =
        i === 0
          ? `<span>${S.provider.values[0]}</span><span class="muted" style="margin-left:8px;font-size:14px">${S.provider.filled}</span><span class="fcheck">${I.check}</span>`
          : i === 1
          ? `<span class="keydots">${S.provider.values[1]}</span><span class="fcheck">${I.lock}</span>`
          : `<span>${S.provider.values[2]}</span><span class="chev">${I.chev}</span>`;
      const mono = i === 1 ? ' mono' : '';
      return `<div class="field" data-field="${i}">
        <div class="flabel">${label}</div>
        <div class="fvalue${mono}">${value}</div>
      </div>`;
    })
    .join('');
  return `<div class="scr scr-provider light" data-screen="provider">
    ${statusbar()}
    <div class="appbar">${I.back}<div class="ab-title">${S.provider.bar}</div><div class="ab-right"><span class="dot-live"></span></div></div>
    <div class="body">
      <div class="form-title">${S.provider.h2}</div>
      <div class="form-desc">${S.provider.desc}</div>
      ${fields}
      <div class="field-note">${I.lock}<span>${S.provider.note}</span></div>
      <div class="cta" id="providerCta">${I.spark}${S.provider.cta}</div>
      <div class="form-foot">${S.provider.foot}</div>
    </div>
    <div class="local-card">
      <div class="lc-head"><span class="dot-live"></span><span>${S.provider.local.title}</span></div>
      ${S.provider.local.rows
        .map((r) => `<div class="lc-row"><span>${r[0]}</span><b>${r[1]}</b></div>`)
        .join('')}
    </div>
    <div class="deco-ring"></div><div class="deco-orb"></div>
    ${homebar}
  </div>`;
}

function filesScreen() {
  const rows = S.files.rows
    .map((r, i) => {
      const ico = r[2] === 'folder' ? I.folder : r[2] === 'text' ? I.text : I.file;
      return `<div class="file-row" data-row="${i}">
        <div class="file-ico ${r[3]}">${ico}</div>
        <div><div class="file-name">${r[0]}</div><div class="file-meta">${r[1]}</div></div>
        <div class="chev">${I.chev}</div>
      </div>`;
    })
    .join('');
  return `<div class="scr scr-files light" data-screen="files">
    ${statusbar()}
    <div class="appbar">${I.back}<div class="ab-title">${S.files.bar}</div><div class="ab-right"><span class="dot-live"></span></div></div>
    <div class="body">
      <div class="files-head"><h2>${S.files.title}</h2><span class="count">${S.files.count}</span></div>
      <div class="file-list">${rows}</div>
      <div class="storage-card">
        <div class="sc-top"><span>${S.files.storage.label}</span><b>${S.files.storage.used} / ${S.files.storage.total}</b></div>
        <div class="sc-bar"><i style="width:${S.files.storage.pct}%"></i></div>
        <div class="sc-foot"><span>${S.files.storage.foot[0]}</span><span>${S.files.storage.foot[1]}</span></div>
      </div>
    </div>
    <div class="preview-sheet" id="previewSheet">
      <div class="sheet-grip"></div>
      <div class="preview-head"><span class="pname">${S.files.preview.name}</span><span class="tag">${S.files.preview.tag}</span></div>
      <p class="preview-body">${S.files.preview.body}</p>
      <div class="preview-meta"><span>${S.files.preview.meta[0]}</span><span>·</span><span>${S.files.preview.meta[1]}</span></div>
      <div class="preview-action"><span>${S.files.preview.action}</span><span class="chev">${I.chev}</span></div>
    </div>
    ${homebar}
  </div>`;
}

function terminalScreen() {
  const lines = S.terminal.lines
    .map((l, i) => {
      if (i === S.terminal.lines.length - 1) {
        return `<div class="tline cmd" data-line="${i}"><span class="ps">❯</span><span class="tw"></span><span class="caret" id="termCaret"></span></div>`;
      }
      const inner =
        l[0] === 'cmd'
          ? `<span class="ps">❯</span><span class="tw"></span>`
          : `<span class="tw"></span>`;
      return `<div class="tline ${l[0]}" data-line="${i}">${inner}</div>`;
    })
    .join('');
  return `<div class="scr scr-terminal" data-screen="terminal">
    ${statusbar()}
    <div class="term-top"><div class="term-dots"><i></i><i></i><i></i></div><div class="term-title">${S.terminal.bar}</div><div class="term-chip">${S.terminal.chip}</div></div>
    <div class="term-watermark">DSHD</div>
    <div class="term-body">${lines}</div>
    <div class="term-stats">
      ${S.terminal.stats
        .map(
          (st) =>
            `<div class="ts-card"><div class="ts-label">${st[0]}</div><div class="ts-value">${st[1]}</div><div class="ts-bar"><i style="width:${st[2]}%"></i></div></div>`
        )
        .join('')}
    </div>
    <div class="term-foot"><span class="dot"></span><span>${S.terminal.foot}</span><span class="right">${S.terminal.right}</span></div>
    ${homebar}
  </div>`;
}

function privacyScreen() {
  const rows = S.privacy.rows
    .map(
      (r, i) => `<div class="perm-row" data-perm="${i}">
        <div class="perm-ico ${r[5]}">${I[r[4]]}</div>
        <div><div class="perm-name">${r[0]}</div><div class="perm-sub">${r[1]}</div></div>
        <div class="perm-status ${r[3]}">${r[2]}</div>
      </div>`
    )
    .join('');
  return `<div class="scr scr-privacy light" data-screen="privacy">
    ${statusbar()}
    <div class="appbar">${I.back}<div class="ab-title">${S.privacy.bar}</div><div class="ab-right"><span class="dot-live"></span></div></div>
    <div class="body">
      <div class="perm-hero">
        <div class="shield-badge">${I.shield}</div>
        <h2>${S.privacy.h2}</h2>
        <p>${S.privacy.desc}</p>
      </div>
      <div class="perm-list">${rows}</div>
      <div class="perm-foot">${I.lock}<span>${S.privacy.foot}</span></div>
      <div class="perm-notes">
        ${S.privacy.notes
          .map(
            (n, i) =>
              `<div class="perm-note"><div class="pn-ico">${[I.lock, I.globe, I.shield][i]}</div><div><b>${n[0]}</b><span>${n[1]}</span></div></div>`
          )
          .join('')}
      </div>
    </div>
    <div class="seal" id="permSeal">
      <div class="seal-brand">DSHD</div>
      <div class="seal-lock">${I.lock}</div>
      <h3>${S.privacy.sealTitle}</h3>
      <p>${S.privacy.sealSub}</p>
      <div class="seal-line"></div>
      <div class="seal-foot">${S.privacy.foot}</div>
    </div>
    ${homebar}
  </div>`;
}

function titleScreen() {
  return `<div class="scr scr-title" data-screen="title">
    ${statusbar()}
    <div class="splash">
      <div class="logo-mark" id="splashLogo">${I.logo}</div>
      <div class="logo-word">DSHD</div>
      <div class="logo-sub">${S.splash.sub}</div>
      <div class="spinner" id="splashSpinner"></div>
      <div class="splash-foot">${S.splash.foot}</div>
    </div>
    ${homebar}
  </div>`;
}

screensRoot.innerHTML = [
  titleScreen(),
  bootScreen(),
  providerScreen(),
  filesScreen(),
  terminalScreen(),
  privacyScreen(),
].join('');

const screens = [...screensRoot.querySelectorAll('.scr')];

/* --------------------------------------------------------------- refs */
const phoneWrap = document.getElementById('phoneWrap');
const captionEl = document.getElementById('caption');
const capText = document.getElementById('capText');
const hudStep = document.getElementById('hudStep');
const ambient = document.getElementById('ambient');
const flashEl = document.getElementById('flash');

const bootPct = document.querySelector('#bootPct');
const bootState = document.querySelector('#bootState');
const bootProg = document.querySelector('#bootProg');
const bootLines = [...document.querySelectorAll('[data-boot]')];
const fields = [...document.querySelectorAll('[data-field]')];
const providerCta = document.querySelector('#providerCta');
const previewSheet = document.querySelector('#previewSheet');
const fileRows = [...document.querySelectorAll('[data-row]')];
const termLines = [...document.querySelectorAll('[data-line]')];
const termCaret = document.querySelector('#termCaret');
const permRows = [...document.querySelectorAll('[data-perm]')];
const permSeal = document.querySelector('#permSeal');
const bootTiles = document.querySelector('#bootTiles');
const splashSpinner = document.querySelector('#splashSpinner');
const splashLogo = document.querySelector('#splashLogo');

/* ----------------------------------------------------------- progress */
const progressRoot = document.getElementById('progress');
BEATS.forEach((b) => {
  const seg = document.createElement('div');
  seg.className = 'seg';
  seg.style.flexGrow = String(b.dur);
  seg.innerHTML = '<i></i>';
  progressRoot.appendChild(seg);
});
const segFills = [...progressRoot.querySelectorAll('.seg > i')];

/* ==================================================================== */
/*  three.js background                                                  */
/* ==================================================================== */
const canvas = document.getElementById('gl');
const renderer = new THREE.WebGLRenderer({
  canvas,
  antialias: true,
  alpha: false,
  powerPreference: 'high-performance',
});
renderer.setPixelRatio(1);
renderer.setSize(1080, 1920, false);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setClearColor(0x04060c, 1);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(50, 1080 / 1920, 0.1, 120);
camera.position.set(0, 0, 7);
camera.lookAt(0, 0, 0);

/* seeded PRNG so every run builds the exact same world */
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(0xd5ad2026);

const PALETTES = [
  ['#060a1c', '#282c74', '#2ec6d8'],
  ['#041018', '#0b4c60', '#49e6d2'],
  ['#0a0718', '#3d2878', '#e779c8'],
  ['#06120f', '#0d5a4d', '#9bf7cf'],
  ['#150d06', '#63380f', '#ffb15e'],
  ['#050a14', '#153a7a', '#54e7bd'],
].map((p) => p.map((c) => new THREE.Color(c)));

/* --- nebula --------------------------------------------------------- */
const nebulaUniforms = {
  uTime: { value: 0 },
  uFlash: { value: 0 },
  uSweep: { value: 1 },
  uColA: { value: PALETTES[0][0].clone() },
  uColB: { value: PALETTES[0][1].clone() },
  uColC: { value: PALETTES[0][2].clone() },
};
const nebula = new THREE.Mesh(
  new THREE.PlaneGeometry(2, 2),
  new THREE.ShaderMaterial({
    uniforms: nebulaUniforms,
    depthWrite: false,
    depthTest: false,
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main(){ vUv = uv; gl_Position = vec4(position.xy, 1.0, 1.0); }
    `,
    fragmentShader: /* glsl */ `
      precision highp float;
      varying vec2 vUv;
      uniform float uTime, uFlash, uSweep;
      uniform vec3 uColA, uColB, uColC;

      float hash(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
      float noise(vec2 p){
        vec2 i = floor(p), f = fract(p);
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
                   mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
      }
      float fbm(vec2 p){
        float v = 0.0, a = 0.5;
        mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
        for (int i = 0; i < 5; i++){ v += a * noise(p); p = rot * p * 2.03 + 11.7; a *= 0.5; }
        return v;
      }
      void main(){
        vec2 uv = vUv;
        vec2 p = (uv - 0.5) * vec2(1080.0 / 1920.0, 1.0) * 2.6;
        float t = uTime * 0.030;
        float n1 = fbm(p * 1.55 + vec2(t, -t * 0.62));
        float n2 = fbm(p * 2.70 + vec2(-t * 0.85, n1 * 0.9) + 4.2);
        float n3 = fbm(p * 5.20 + vec2(t * 0.4, -n2 * 0.7) + 8.4);
        float neb = smoothstep(0.30, 0.95, n2 * 0.72 + n1 * 0.38 + n3 * 0.14);

        vec3 col = mix(uColA * 0.55, uColB, smoothstep(0.12, 0.78, n1));
        col = mix(col, uColC, smoothstep(0.42, 0.98, n2) * 0.62);
        col *= neb * 1.25;

        // faint filaments
        float fil = smoothstep(0.86, 1.0, noise(p * 7.0 + vec2(-t * 1.6, t)));
        col += uColC * fil * 0.05;

        // deep base + vignette
        col += uColA * 0.16;
        float d = length((uv - 0.5) * vec2(0.82, 1.0));
        col *= smoothstep(0.86, 0.08, d) * 0.95 + 0.05;

        // transition sweep
        float band = exp(-pow((uv.y - (1.02 - uSweep * 1.18)) * 9.0, 2.0));
        col += uColC * band * uFlash * 0.30;
        col += uFlash * 0.035;

        gl_FragColor = vec4(col, 1.0);
        #include <colorspace_fragment>
      }
    `,
  })
);
nebula.frustumCulled = false;
nebula.renderOrder = -10;
scene.add(nebula);

/* --- stars ---------------------------------------------------------- */
const starCount = 2200;
const starPos = new Float32Array(starCount * 3);
const starSize = new Float32Array(starCount);
const starSeed = new Float32Array(starCount);
const starColor = new Float32Array(starCount * 3);
const starTints = [new THREE.Color('#cfe0ff'), new THREE.Color('#9fb9ff'), new THREE.Color('#a8ffe4'), new THREE.Color('#ffe0bb')];
for (let i = 0; i < starCount; i++) {
  starPos[i * 3] = (rand() - 0.5) * 20;
  starPos[i * 3 + 1] = (rand() - 0.5) * 24;
  starPos[i * 3 + 2] = -16 + rand() * 14;
  starSize[i] = 0.6 + Math.pow(rand(), 3) * 4.2;
  starSeed[i] = rand();
  const c = starTints[Math.floor(rand() * starTints.length)].clone().multiplyScalar(0.55 + rand() * 0.45);
  starColor[i * 3] = c.r;
  starColor[i * 3 + 1] = c.g;
  starColor[i * 3 + 2] = c.b;
}
const starGeo = new THREE.BufferGeometry();
starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
starGeo.setAttribute('aSize', new THREE.BufferAttribute(starSize, 1));
starGeo.setAttribute('aSeed', new THREE.BufferAttribute(starSeed, 1));
starGeo.setAttribute('aColor', new THREE.BufferAttribute(starColor, 3));
const starUniforms = { uTime: { value: 0 }, uPixelRatio: { value: 1 } };
const stars = new THREE.Points(
  starGeo,
  new THREE.ShaderMaterial({
    uniforms: starUniforms,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */ `
      attribute float aSize; attribute float aSeed; attribute vec3 aColor;
      uniform float uTime, uPixelRatio;
      varying vec3 vColor; varying float vAlpha;
      void main(){
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        float tw = 0.55 + 0.45 * sin(uTime * (0.7 + aSeed * 1.6) + aSeed * 31.4);
        vColor = aColor;
        vAlpha = 0.35 + 0.65 * tw;
        gl_PointSize = aSize * uPixelRatio * (150.0 / max(2.0, -mv.z)) * (0.75 + 0.5 * tw);
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */ `
      varying vec3 vColor; varying float vAlpha;
      void main(){
        vec2 c = gl_PointCoord - 0.5;
        float d = length(c);
        float a = smoothstep(0.5, 0.0, d);
        a = pow(a, 1.6);
        gl_FragColor = vec4(vColor, a * vAlpha);
        #include <colorspace_fragment>
      }
    `,
  })
);
stars.frustumCulled = false;
scene.add(stars);

/* --- glass shards --------------------------------------------------- */
const glassVert = /* glsl */ `
  varying vec2 vUv;
  void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }
`;
const glassFrag = /* glsl */ `
  precision highp float;
  varying vec2 vUv;
  uniform vec3 uTint;
  uniform float uAspect, uOpacity, uSeed;
  void main(){
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float r = 0.16;
    vec2 b = vec2(uAspect * 0.5, 0.5) - r;
    vec2 q = abs(p) - b;
    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    if (sd > 0.0) discard;
    float border = smoothstep(0.018, 0.0, abs(sd + 0.006));
    float sheen = smoothstep(0.15, 1.0, vUv.y) * 0.6;
    sheen += smoothstep(0.18, 0.0, abs(vUv.x - 0.22 - uSeed * 0.3)) * 0.25;
    vec3 col = mix(uTint, vec3(1.0), 0.30 * sheen);
    float alpha = (0.035 + 0.085 * sheen + border * 0.22) * uOpacity;
    gl_FragColor = vec4(col, alpha);
    #include <colorspace_fragment>
  }
`;
const glassGroup = new THREE.Group();
for (let i = 0; i < 11; i++) {
  const w = 1.3 + rand() * 2.2;
  const h = 0.8 + rand() * 1.5;
  const mat = new THREE.ShaderMaterial({
    uniforms: {
      uTint: { value: new THREE.Color(i % 3 === 0 ? '#6ea8ff' : i % 3 === 1 ? '#57e6c4' : '#8b7bff') },
      uAspect: { value: w / h },
      uOpacity: { value: 0.55 + rand() * 0.45 },
      uSeed: { value: rand() },
    },
    vertexShader: glassVert,
    fragmentShader: glassFrag,
    transparent: true,
    depthWrite: false,
    side: THREE.DoubleSide,
  });
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
  const side = i % 2 === 0 ? 1 : -1;
  m.position.set(side * (2.5 + rand() * 2.6), (rand() - 0.5) * 5.2, -4.5 + rand() * 2.8);
  m.rotation.set((rand() - 0.5) * 0.5, side * (0.35 + rand() * 0.7), (rand() - 0.5) * 0.7);
  m.userData = { spin: (rand() - 0.5) * 0.12, float: rand() * 6.28, baseY: m.position.y, z: m.position.z };
  glassGroup.add(m);
}
scene.add(glassGroup);

/* --- soft glows ----------------------------------------------------- */
function glowTexture() {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const ctx = c.getContext('2d');
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
  g.addColorStop(0, 'rgba(255,255,255,0.9)');
  g.addColorStop(0.18, 'rgba(255,255,255,0.32)');
  g.addColorStop(0.5, 'rgba(255,255,255,0.07)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 128, 128);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}
const glowTex = glowTexture();
const glowDefs = [
  ['#4d7dff', -4.1, 1.6, -5.0, 7.4],
  ['#2fd8c0', 4.2, -1.4, -5.2, 6.2],
  ['#8b5cff', 0.4, 3.2, -7.5, 9.0],
  ['#ff9a5c', -2.9, -3.2, -6.2, 4.6],
  ['#4f8dff', 3.4, 3.7, -8.0, 5.6],
];
const glows = glowDefs.map(([color, x, y, z, s], i) => {
  const sp = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: glowTex,
      color: new THREE.Color(color),
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      opacity: 0.30,
    })
  );
  sp.position.set(x, y, z);
  sp.scale.set(s, s, 1);
  sp.userData = { base: 0.26, phase: i * 1.7, x, y, z };
  scene.add(sp);
  return sp;
});

/* --- background update ---------------------------------------------- */
const tmpA = new THREE.Color();
const tmpB = new THREE.Color();
const tmpC = new THREE.Color();

function backgroundAt(t, beatIndex, local) {
  const prev = Math.max(0, beatIndex - 1);
  const cur = beatIndex;
  const q = beatIndex === 0 ? 1 : smooth(0, 0.9, local);
  const pa = PALETTES[prev];
  const ca = PALETTES[cur];
  tmpA.copy(pa[0]).lerp(ca[0], q);
  tmpB.copy(pa[1]).lerp(ca[1], q);
  tmpC.copy(pa[2]).lerp(ca[2], q);
  nebulaUniforms.uColA.value.copy(tmpA);
  nebulaUniforms.uColB.value.copy(tmpB);
  nebulaUniforms.uColC.value.copy(tmpC);
  nebulaUniforms.uTime.value = t;

  const flash =
    beatIndex === 0 ? 0 : Math.exp(-Math.pow((local - 0.30) / 0.24, 2)) * 0.95;
  nebulaUniforms.uFlash.value = flash;
  nebulaUniforms.uSweep.value = beatIndex === 0 ? 1 : smooth(0, 1.05, local);
  starUniforms.uTime.value = t;

  camera.position.x = Math.sin(t * 0.13) * 0.30;
  camera.position.y = Math.cos(t * 0.11) * 0.18;
  camera.lookAt(0, 0, 0);

  stars.rotation.y = t * 0.006;
  stars.rotation.x = Math.sin(t * 0.035) * 0.02;

  glassGroup.children.forEach((m, i) => {
    const d = m.userData;
    m.position.y = d.baseY + Math.sin(t * 0.22 + d.float) * 0.16;
    m.rotation.z += 0; // static per frame; time driven below
    m.rotation.z = Math.sin(t * 0.12 + d.float) * 0.09 + (i % 2 ? 0.2 : -0.15);
    m.rotation.y = (i % 2 ? 1 : -1) * (0.35 + Math.sin(t * 0.10 + d.float) * 0.12);
  });
  glows.forEach((g, i) => {
    const d = g.userData;
    g.position.x = d.x + Math.sin(t * 0.16 + d.phase) * 0.22;
    g.position.y = d.y + Math.cos(t * 0.13 + d.phase) * 0.18;
    g.material.opacity = d.base + Math.sin(t * 0.5 + d.phase) * 0.04;
  });

  // CSS ambient tint follows the palette
  ambient.style.background = `radial-gradient(38% 30% at 18% 26%, ${hexA(tmpB, 0.30)}, transparent 70%),
    radial-gradient(42% 32% at 84% 62%, ${hexA(tmpC, 0.20)}, transparent 70%),
    radial-gradient(46% 40% at 50% 88%, ${hexA(tmpB, 0.22)}, transparent 70%)`;
  flashEl.style.opacity = String(flash * 0.55);
}
function hexA(c, a) {
  const r = Math.round(clamp(c.r) * 255);
  const g = Math.round(clamp(c.g) * 255);
  const b = Math.round(clamp(c.b) * 255);
  return `rgba(${r},${g},${b},${a})`;
}

/* ==================================================================== */
/*  frame update                                                         */
/* ==================================================================== */
const CAM = [
  { ry: -7, rx: 3.4, s: 0.945, y: 8 },
  { ry: -10.5, rx: 4.8, s: 1.0, y: 2 },
  { ry: -7.5, rx: 3.2, s: 1.008, y: -2 },
  { ry: -11.5, rx: 4.2, s: 1.0, y: 1 },
  { ry: -5.5, rx: 2.4, s: 1.016, y: 3 },
  { ry: -8.5, rx: 3.0, s: 0.992, y: -3 },
];

let lastIndex = -1;

function beatIndexAt(t) {
  let idx = 0;
  for (let i = 0; i < BEATS.length; i++) if (t >= BEATS[i].start - 1e-6) idx = i;
  return idx;
}

function setTime(rawT) {
  const t = clamp(rawT, 0, TOTAL);
  const idx = beatIndexAt(t);
  const beat = BEATS[idx];
  const local = t - beat.start;
  const q = idx === 0 ? 1 : smooth(0, 0.85, local);

  /* phone float / camera-ish motion */
  const prev = Math.max(0, idx - 1);
  const cp = CAM[prev];
  const cc = CAM[idx];
  const ry = lerp(cp.ry, cc.ry, q) + Math.sin(t * 0.22) * 0.9;
  const rx = lerp(cp.rx, cc.rx, q) + Math.cos(t * 0.18) * 0.5;
  const sc = lerp(cp.s, cc.s, q);
  const yy = lerp(cp.y, cc.y, q) + Math.sin(t * 0.7) * 3.2;
  phoneWrap.style.transform =
    `translate3d(0px, ${yy.toFixed(3)}px, 0px) rotateX(${rx.toFixed(3)}deg) ` +
    `rotateY(${ry.toFixed(3)}deg) rotateZ(${(Math.sin(t * 0.32) * 0.55).toFixed(3)}deg) ` +
    `scale(${sc.toFixed(4)})`;

  /* hero copy */
  heroEls.forEach((el, i) => {
    const b = BEATS[i];
    const inP = i === 0 ? 1 : smooth(b.start - 0.04, b.start + 0.48, t);
    const outP = smooth(b.start + b.dur - 0.30, b.start + b.dur + 0.14, t);
    const op = inP * (1 - outP);
    if (op < 0.002) {
      el.style.visibility = 'hidden';
      el.style.opacity = '0';
      return;
    }
    el.style.visibility = 'visible';
    el.style.opacity = String(op);
    el.style.transform = `translateY(${(14 * (1 - inP) - 14 * outP).toFixed(2)}px)`;
    el.style.filter = `blur(${(6 * (1 - inP) + 6 * outP).toFixed(2)}px)`;
  });

  /* phone screens */
  screens.forEach((el, i) => {
    let op = 0;
    let ty = 0;
    let scl = 1;
    let blur = 0;
    if (i === idx) {
      const p = i === 0 ? 1 : smooth(0, 0.70, local);
      op = p;
      ty = 26 * (1 - p);
      scl = 0.985 + 0.015 * p;
      blur = 7 * (1 - p);
    } else if (i === idx - 1 && local < 1.0) {
      const p = smooth(0, 0.55, local);
      op = 1 - p;
      ty = -20 * p;
      blur = 6 * p;
    }
    el.style.opacity = op.toFixed(4);
    el.style.visibility = op > 0.002 ? 'visible' : 'hidden';
    el.style.transform = `translate3d(0,${ty.toFixed(2)}px,0) scale(${scl.toFixed(4)})`;
    el.style.filter = blur > 0.01 ? `blur(${blur.toFixed(2)}px)` : 'none';
  });

  /* per-screen micro animation */
  const scr = screens[idx];
  if (scr.dataset.screen === 'title') {
    if (splashSpinner) splashSpinner.style.transform = `rotate(${(t * 230) % 360}deg)`;
    if (splashLogo) splashLogo.style.transform = `scale(${(1 + 0.018 * Math.sin(t * 1.9)).toFixed(4)})`;
  }
  if (scr.dataset.screen === 'boot') {
    const p = clamp((local - 0.30) / 2.15);
    if (bootPct) bootPct.textContent = String(Math.round(p * 100));
    if (bootState) bootState.textContent = p >= 0.995 ? S.boot.done : S.boot.state;
    if (bootProg) bootProg.setAttribute('stroke-dashoffset', String(603.19 * (1 - p)));
    bootLines.forEach((ln, i) => {
      const on = p > 0.16 + i * 0.21;
      ln.classList.toggle('pending', !on);
    });
    if (bootTiles) {
      const tp = smooth(0.55, 1.0, p);
      bootTiles.style.opacity = String(0.22 + 0.78 * tp);
      bootTiles.style.transform = `translateY(${(1 - tp) * 18}px)`;
    }
  }
  if (scr.dataset.screen === 'provider') {
    const active = local < 0.45 ? -1 : Math.min(2, Math.floor((local - 0.45) / 0.78));
    fields.forEach((f, i) => {
      const on = i === active;
      f.style.borderColor = on ? 'rgba(88, 120, 255, 0.58)' : '#e3e7f1';
      f.style.boxShadow = on
        ? '0 0 0 4px rgba(88,120,255,0.10), 0 8px 20px rgba(24,36,68,0.06)'
        : '0 4px 14px rgba(24,36,68,0.035)';
    });
    if (providerCta) {
      const pulse = local > 2.10 ? 0.5 + 0.5 * Math.sin((local - 2.1) * 5.0) : 0;
      providerCta.style.boxShadow = `0 ${16 + pulse * 6}px ${34 + pulse * 14}px rgba(66,103,255,${0.28 + pulse * 0.16}), inset 0 1px 0 rgba(255,255,255,0.3)`;
    }
  }
  if (scr.dataset.screen === 'files') {
    fileRows.forEach((r, i) => {
      const on = smooth(0.25 + i * 0.13, 0.55 + i * 0.13, local);
      r.style.opacity = String(0.15 + 0.85 * on);
      r.style.transform = `translateY(${(1 - on) * 10}px)`;
    });
    if (previewSheet) {
      const p = smooth(0.72, 1.40, local);
      previewSheet.style.transform = `translateY(${(1 - p) * 400}px)`;
      previewSheet.style.boxShadow = '0 -18px 48px rgba(24,36,68,0.15)';
    }
    const activeRow = fileRows[2];
    if (activeRow) activeRow.style.background = local > 0.9 ? 'rgba(238,243,255,0.9)' : 'transparent';
  }
  if (scr.dataset.screen === 'terminal') {
    const schedule = [
      { at: 0.12, type: 'cmd' },
      { at: 0.40, type: 'out' },
      { at: 0.70, type: 'cmd' },
      { at: 0.98, type: 'ok' },
      { at: 1.28, type: 'cmd' },
      { at: 1.56, type: 'out' },
      { at: 1.84, type: 'cmd' },
      { at: 2.12, type: 'out' },
      { at: 2.38, type: 'caret' },
    ];
    termLines.forEach((ln, i) => {
      const tw = ln.querySelector('.tw');
      const sched = schedule[i];
      if (!tw || !sched) return;
      const full = S.terminal.lines[i][1];
      if (sched.type === 'cmd') {
        const p = clamp((local - sched.at) / 0.34);
        const n = Math.floor(p * full.length + 0.0001);
        tw.textContent = full.slice(0, n);
        ln.style.opacity = local >= sched.at - 0.05 ? '1' : '0.0';
      } else {
        const p = smooth(sched.at, sched.at + 0.24, local);
        tw.textContent = p > 0.001 ? full : '';
        ln.style.opacity = String(p);
      }
    });
    if (termCaret) {
      const on = local >= 2.38;
      termCaret.style.opacity = on ? (((t * 1.9) % 1) < 0.55 ? '1' : '0.15') : '0';
    }
  }
  if (scr.dataset.screen === 'privacy') {
    permRows.forEach((r, i) => {
      const on = smooth(0.15 + i * 0.11, 0.45 + i * 0.11, local);
      r.style.opacity = String(0.12 + 0.88 * on);
      r.style.transform = `translateY(${(1 - on) * 12}px)`;
    });
    if (permSeal) {
      const p = smooth(1.95, 2.42, local);
      permSeal.style.transform = `translateY(${(1 - p) * 100}%)`;
      permSeal.style.visibility = p > 0.001 ? 'visible' : 'hidden';
    }
  }

  /* captions */
  let cue = null;
  for (const c of timeline.cues) {
    if (t >= c.start - 0.001 && t <= c.end + 0.001) {
      cue = c;
      break;
    }
  }
  if (cue) {
    const fin = smooth(cue.start, cue.start + 0.16, t);
    const fout = 1 - smooth(cue.end - 0.18, cue.end, t);
    const a = fin * fout;
    if (capText.textContent !== cue.text) capText.textContent = cue.text;
    captionEl.style.opacity = String(a);
    captionEl.style.transform = `translateY(${(1 - fin) * 12 - (1 - fout) * 8}px)`;
  } else {
    captionEl.style.opacity = '0';
  }

  /* progress */
  segFills.forEach((f, i) => {
    const b = BEATS[i];
    const p = clamp((t - b.start) / b.dur);
    f.style.width = `${(p * 100).toFixed(2)}%`;
    f.style.opacity = i === idx ? '1' : i < idx ? '0.55' : '0.18';
  });

  /* hud */
  hudStep.textContent = `${String(idx + 1).padStart(2, '0')} / ${String(BEATS.length).padStart(2, '0')}`;

  backgroundAt(t, idx, local);
  renderer.render(scene, camera);

  if (idx !== lastIndex) lastIndex = idx;
}

/* boot the page */
await document.fonts.ready;
await new Promise((r) => requestAnimationFrame(r));
window.__timeline = timeline;
window.__setTime = setTime;
window.__renderAt = (t) => {
  setTime(t);
  return true;
};
setTime(0);
window.__ready = true;
