/* Deterministic 1920x1080 YouTube promo scene.
 *
 * The vertical first-use demo is embedded in the phone and is seeked for every
 * frame by render.mjs. All copy and layout are fixed; no wall-clock animation.
 */
const params = new URLSearchParams(location.search);
const LANG = params.get('lang') === 'zh' ? 'zh' : 'en';
const TOTAL = 31.2;
const OUTRO_GAP = 0.6;

const COPY = {
  en: {
    label: 'DEEPSEEK HARNESS DESKTOP',
    edition: 'ANDROID',
    phoneLabel: 'PRODUCT DEMO · FIRST RUN',
    stepEnd: 'OPEN',
    foot: ['LOCAL RUNTIME', 'BRING YOUR OWN MODEL', 'APACHE-2.0'],
    outroCaption: 'Find DSHD on GitHub. Link in the description.',
    cta: 'GITHUB · LINK IN DESCRIPTION',
    slides: {
      title: {
        kicker: 'DSHD',
        h1: 'A full AI workstation.\nOn your phone.',
        p: 'DeepSeek Harness Desktop runs a real Linux runtime and its web UI directly on Android.',
      },
      boot: {
        kicker: '01 · BUILT IN',
        h1: 'The runtime\nis already there.',
        p: 'One-time unpack. After that, the workspace opens without a sidecar computer.',
      },
      provider: {
        kicker: '02 · YOUR MODEL',
        h1: 'Bring your\nown model.',
        p: 'Connect any OpenAI-compatible endpoint. The key stays in app-private storage.',
      },
      files: {
        kicker: '03 · WORKSPACE',
        h1: 'Open files.\nRead instantly.',
        p: 'Browse the workspace and preview documents without leaving the app.',
      },
      terminal: {
        kicker: '04 · TERMINAL',
        h1: 'A real terminal.\nOn device.',
        p: 'Run commands locally and follow the output live.',
      },
      privacy: {
        kicker: '05 · PRIVACY',
        h1: 'Your files stay\non your phone.',
        p: 'The runtime and workspace are local. Model prompts go to the endpoint you configure.',
      },
      outro: {
        kicker: 'OPEN SOURCE',
        h1: 'Built for Android.\nOpen on GitHub.',
        p: 'DSHD is Apache-2.0. Download the APK, read the docs, and run it on your own device.',
      },
    },
  },
  zh: {
    label: 'DEEPSEEK HARNESS DESKTOP',
    edition: '安卓',
    phoneLabel: '产品演示 · 首次运行',
    stepEnd: '开源',
    foot: ['本地运行时', '接入自己的模型', 'APACHE-2.0'],
    outroCaption: 'DSHD 已在 GitHub 开源，链接见视频简介。',
    cta: 'GITHUB · 链接见视频简介',
    slides: {
      title: {
        kicker: 'DSHD',
        h1: '完整的 AI 工作站\n装进手机。',
        p: 'DeepSeek Harness Desktop 在 Android 上运行真实 Linux 运行时和 Web UI。',
      },
      boot: {
        kicker: '01 · 内置运行时',
        h1: '开箱即用，\n运行时已就绪。',
        p: '首次解包一次，之后直接打开工作区。',
      },
      provider: {
        kicker: '02 · 模型接入',
        h1: '接入你自己的\n模型服务。',
        p: '兼容 OpenAI 接口，密钥保存在应用私有存储。',
      },
      files: {
        kicker: '03 · 工作区',
        h1: '打开文件，\n立即预览。',
        p: '在应用内浏览工作区、查看文档。',
      },
      terminal: {
        kicker: '04 · 终端',
        h1: '真正的终端，\n就在设备上。',
        p: '本地执行命令，实时查看输出。',
      },
      privacy: {
        kicker: '05 · 隐私',
        h1: '文件留在\n你的手机里。',
        p: '运行时和工作区都在本地；模型请求会发送到你配置的服务端点。',
      },
      outro: {
        kicker: '开源',
        h1: '为 Android 而生，\n已在 GitHub 开源。',
        p: 'Apache-2.0 许可。下载 APK、阅读文档，在你自己的设备上运行。',
      },
    },
  },
};

const copy = COPY[LANG];
const slidesHost = document.getElementById('slides');
const caption = document.getElementById('caption');
const stepNum = document.getElementById('stepNum');
const stepWrap = document.querySelector('.step');
const stepSlash = document.getElementById('stepSlash');
const features = document.querySelector('.features');
const outroCta = document.getElementById('outroCta');
const ctaText = document.getElementById('ctaText');
const progressFill = document.getElementById('progressFill');
const phone = document.getElementById('phone');
const video = document.getElementById('screenVideo');

let timeline;
let outroAt;
let slides = [];
let slideMeta = [];

document.documentElement.lang = LANG === 'zh' ? 'zh-CN' : 'en';
document.body.dataset.lang = LANG;

function clamp(value) {
  return Math.max(0, Math.min(1, value));
}

function buildSlides() {
  const items = timeline.beats.map((beat) => ({
    id: beat.id,
    start: beat.start,
    end: beat.start + beat.dur,
    copy: copy.slides[beat.id] || copy.slides.title,
  }));
  if (items.length) items[items.length - 1].end = outroAt;
  items.push({ id: 'outro', start: outroAt, end: TOTAL, copy: copy.slides.outro });

  slides = items.map((item) => {
    const el = document.createElement('section');
    el.className = 'slide';
    const kicker = document.createElement('div');
    kicker.className = 'kicker';
    kicker.textContent = item.copy.kicker;
    const h1 = document.createElement('h1');
    h1.textContent = item.copy.h1;
    const p = document.createElement('p');
    p.textContent = item.copy.p;
    const rule = document.createElement('div');
    rule.className = 'rule';
    el.append(kicker, h1, p, rule);
    slidesHost.append(el);
    return el;
  });
  slideMeta = items;
}

function seekTo(target) {
  return new Promise((resolve) => {
    if (Math.abs(video.currentTime - target) <= 0.012) return resolve();
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      video.removeEventListener('seeked', finish);
      resolve();
    };
    video.addEventListener('seeked', finish);
    video.currentTime = target;
    // Chromium may not emit seeked for a frame that is already decoded.
    setTimeout(finish, 140);
  });
}

async function update(t) {
  // Seek the vertical demo to the same deterministic time.
  const duration = Math.max(0, (timeline.total || 25.4) - 0.05);
  const target = Math.max(0, Math.min(duration, t));
  await seekTo(target);

  for (let i = 0; i < slideMeta.length; i++) {
    const meta = slideMeta[i];
    const inP = clamp((t - meta.start + 0.08) / 0.36);
    const outP = clamp((meta.end - t) / 0.26);
    const visible = Math.min(inP, outP);
    const el = slides[i];
    el.style.opacity = visible.toFixed(4);
    el.style.transform = `translateY(${((1 - inP) * 18).toFixed(2)}px)`;
    el.style.zIndex = visible > 0 ? '2' : '1';
  }

  const active = slideMeta.findIndex((meta) => t >= meta.start && t < meta.end);
  if (active >= 0) {
    const meta = slideMeta[active];
    if (meta.id === 'outro') {
      stepWrap.style.opacity = '1';
      stepNum.textContent = copy.stepEnd;
      stepSlash.style.opacity = '0';
    } else {
      stepWrap.style.opacity = '1';
      stepNum.textContent = String(active + 1).padStart(2, '0');
      stepSlash.style.opacity = '1';
    }
  }

  let cueText = '';
  if (t >= outroAt) {
    cueText = copy.outroCaption;
  } else {
    const cue = timeline.cues.find((item) => t >= item.start && t <= item.end);
    if (cue) cueText = cue.text;
  }
  caption.textContent = cueText;
  caption.style.opacity = cueText ? '1' : '0';

  const isOutro = t >= outroAt;
  phone.classList.toggle('dim', isOutro);
  features.style.opacity = isOutro ? '0' : '1';
  outroCta.style.opacity = isOutro ? '1' : '0';
  progressFill.style.width = `${Math.max(0, Math.min(100, (t / TOTAL) * 100))}%`;
}

async function init() {
  timeline = await fetch(`../../out/timeline.${LANG}.json`).then((response) => response.json());
  outroAt = timeline.total + OUTRO_GAP;
  document.querySelector('.brandSub').textContent = copy.label;
  document.querySelector('.edition').textContent = copy.edition;
  features.innerHTML = copy.foot.map((item) => `<span>${item}</span>`).join('<i></i>');
  ctaText.textContent = copy.cta;
  document.getElementById('phoneLabel').textContent = copy.phoneLabel;

  video.src = `../../out/dshd-demo-${LANG}.mp4`;
  video.load();
  await new Promise((resolve, reject) => {
    if (video.readyState >= 2) return resolve();
    video.addEventListener('loadeddata', resolve, { once: true });
    video.addEventListener('error', () => reject(new Error('demo video failed to load')), { once: true });
  });
  video.pause();
  video.currentTime = 0;

  buildSlides();
  caption.style.transition = 'none';
  phone.style.transition = 'none';
  await update(0);
  window.__setTime = async (t) => {
    await update(t);
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  };
  window.__ready = true;
}

init().catch((error) => {
  console.error(error);
  window.__error = String(error);
});
