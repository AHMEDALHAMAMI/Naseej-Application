// Landing hero: an animated isometric "demand map".
// Each cycle: an idea is pinned on an empty lot, neighbors' votes fly in and fill the target,
// a verified investor unlocks it, and the shop rises through the opening stages.
// Everything is drawn from a single clock, so it can pause (off-screen, hidden tab) and show a still frame for reduced motion.
import { t, esc, fmt, categoryLabel, stageLabel } from './ui.js';
import { isAr } from './i18n.js';

const NS = 'http://www.w3.org/2000/svg';
const S = 38; // px per map unit
const PITCH = 1.45; // block + street
const N = 6; // blocks per side
const OFF = (N * PITCH - (PITCH - 1)) / 2;
const VB = { x: -320, y: -300, w: 640, h: 480 };
const CYCLE = 11000;
const VOTES = 26;
const STAGES = ['Licensing', 'Fit-out', 'Hiring', 'Opened'];

const IDEAS = [
  { cat: 'Cafe', title: 'hero.idea1', hood: 'hero.hood1', lot: [1, 4], unlock: 'paid', price: 18500 },
  { cat: 'Pharmacy', title: 'hero.idea2', hood: 'hero.hood2', lot: [4, 1], unlock: 'partner' },
  { cat: "Women's gym", title: 'hero.idea3', hood: 'hero.hood3', lot: [3, 3], unlock: 'paid', price: 42000 },
];
const LOTS = IDEAS.map((i) => i.lot.join(','));

const iso = (x, y, h = 0) => [(x - y) * 0.866 * S, (x + y) * 0.5 * S - h * S];
const pts = (list) => list.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
const ease = (v) => (v <= 0 ? 0 : v >= 1 ? 1 : 1 - Math.pow(1 - v, 3));
const clamp01 = (v) => Math.max(0, Math.min(1, v));
const blockOrigin = (i, j) => [i * PITCH - OFF, j * PITCH - OFF];

// Small seeded random so the city looks the same on every visit
let seed = 7;
const rand = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };

function el(name, attrs = {}, parent) {
  const node = document.createElementNS(NS, name);
  Object.entries(attrs).forEach(([k, v]) => node.setAttribute(k, v));
  if (parent) parent.appendChild(node);
  return node;
}

function boxFaces(a, b, c, d, h) {
  return {
    top: pts([iso(a, c, h), iso(b, c, h), iso(b, d, h), iso(a, d, h)]),
    left: pts([iso(a, d, 0), iso(b, d, 0), iso(b, d, h), iso(a, d, h)]),
    right: pts([iso(b, c, 0), iso(b, d, 0), iso(b, d, h), iso(b, c, h)]),
  };
}

function drawBox(parent, a, b, c, d, h, cls) {
  const g = el('g', { class: cls }, parent);
  const f = boxFaces(a, b, c, d, h);
  el('polygon', { points: f.left, class: 'f-l' }, g);
  el('polygon', { points: f.right, class: 'f-r' }, g);
  el('polygon', { points: f.top, class: 'f-t' }, g);
  return g;
}

function buildCity(svg) {
  const half = OFF + 0.45;
  // Plate with a little thickness
  el('polygon', { class: 'plate-edge', points: pts([iso(-half, half), iso(half, half), iso(half, -half), iso(half, -half, -0.3), iso(half, half, -0.3), iso(-half, half, -0.3)]) }, svg);
  el('polygon', { class: 'plate', points: pts([iso(-half, -half), iso(half, -half), iso(half, half), iso(-half, half)]) }, svg);

  // Streets: centre lines between blocks, with moving traffic
  const streets = el('g', { class: 'streets' }, svg);
  for (let k = 0; k <= N; k += 1) {
    const s = k * PITCH - OFF - (PITCH - 1) / 2;
    [[iso(s, -half), iso(s, half)], [iso(-half, s), iso(half, s)]].forEach(([p, q], dirIdx) => {
      el('line', { x1: p[0], y1: p[1], x2: q[0], y2: q[1], class: 'road' }, streets);
      el('line', { x1: p[0], y1: p[1], x2: q[0], y2: q[1], class: `traffic t${(k + dirIdx) % 3}`, style: `animation-delay:${-k * 0.7}s` }, streets);
    });
  }

  // Blocks, painted back to front
  const blocks = [];
  for (let i = 0; i < N; i += 1) for (let j = 0; j < N; j += 1) blocks.push([i, j]);
  blocks.sort((p, q) => p[0] + p[1] - (q[0] + q[1]));
  const city = el('g', { class: 'city' }, svg);
  const roofs = [];
  const shops = {};
  blocks.forEach(([i, j]) => {
    const [x, y] = blockOrigin(i, j);
    const key = `${i},${j}`;
    if (LOTS.includes(key)) {
      el('polygon', { class: 'lot', points: pts([iso(x + 0.08, y + 0.08), iso(x + 0.92, y + 0.08), iso(x + 0.92, y + 0.92), iso(x + 0.08, y + 0.92)]) }, city);
      shops[key] = el('g', { class: 'shop' }, city);
      return;
    }
    const r = rand();
    if (r < 0.14) {
      el('polygon', { class: 'park', points: pts([iso(x, y), iso(x + 1, y), iso(x + 1, y + 1), iso(x, y + 1)]) }, city);
      [[0.3, 0.3], [0.7, 0.4], [0.45, 0.72]].forEach(([u, v]) => {
        const [tx, ty] = iso(x + u, y + v);
        el('ellipse', { cx: tx, cy: ty - 6, rx: 7, ry: 6, class: 'tree' }, city);
      });
      return;
    }
    // One tall building, or two lower ones side by side
    const parts = r < 0.55 ? [[0, 1]] : [[0, 0.48], [0.52, 1]];
    parts.forEach(([u0, u1], n) => {
      const h = 0.35 + rand() * (parts.length === 1 ? 1.5 : 0.9);
      const tone = rand() < 0.3 ? 'b-green' : 'b-sand';
      drawBox(city, x + u0 + 0.04, x + u1 - 0.04, y + 0.04, y + 0.96, h, `bld ${tone}`);
      roofs.push({ i, j, pos: iso(x + (u0 + u1) / 2, y + 0.5, h), n });
    });
  });
  return { roofs, shops };
}

function pinPath() {
  return 'M0 0 C -3 -9 -12 -15 -12 -25 A 12 12 0 1 1 12 -25 C 12 -15 3 -9 0 0 Z';
}

export function startHero(root) {
  if (!root) return;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  root.innerHTML = `
    <svg class="hv-svg" viewBox="${VB.x} ${VB.y} ${VB.w} ${VB.h}" aria-hidden="true" focusable="false"></svg>
    <div class="hv-chip"><i></i><span>${esc(t('hero.live'))}</span></div>
    <div class="hv-card hv-idea">
      <div class="hv-ring"><svg viewBox="0 0 44 44"><circle cx="22" cy="22" r="18" class="bg"/><circle cx="22" cy="22" r="18" class="fg" pathLength="100"/></svg><b>0%</b></div>
      <div class="hv-text"><span class="hv-cat"></span><strong class="hv-title"></strong><span class="hv-status"></span></div>
    </div>
    <div class="hv-card hv-inv">
      <div class="hv-row"><span class="hv-avatar">${'◆'}</span><div class="hv-text"><span class="hv-cat">${esc(t('hero.verified'))}</span><strong class="hv-deal"></strong></div></div>
      <div class="hv-stages">${STAGES.map((s) => `<div><i><b></b></i><span>${esc(stageLabel(s))}</span></div>`).join('')}</div>
    </div>`;

  const svg = root.querySelector('.hv-svg');
  seed = 7;
  const { roofs, shops } = buildCity(svg);

  // Ambient pins: other ideas around the city
  const ambient = el('g', { class: 'ambient' }, svg);
  roofs.filter((_, k) => k % 7 === 3).slice(0, 6).forEach((r, k) => {
    const g = el('g', { transform: `translate(${r.pos[0]},${r.pos[1]}) scale(0.55)`, class: 'apin', style: `animation-delay:${k * 0.9}s` }, ambient);
    el('path', { d: pinPath() }, g);
    el('circle', { cx: 0, cy: -25, r: 4.5, class: 'dot' }, g);
  });

  // Active idea layer
  const radius = el('ellipse', { class: 'radius', rx: 2.6 * 1.2247 * S, ry: 2.6 * 0.7071 * S }, svg);
  const link = el('path', { class: 'link', pathLength: 100 }, svg);
  const particles = el('g', { class: 'votes' }, svg);
  const dots = Array.from({ length: VOTES }, () => el('circle', { r: 3.2, class: 'vote' }, particles));
  const pin = el('g', { class: 'pin' }, svg);
  const ripple1 = el('ellipse', { class: 'ripple', rx: 22, ry: 11 }, pin);
  const ripple2 = el('ellipse', { class: 'ripple r2', rx: 22, ry: 11 }, pin);
  el('ellipse', { class: 'pin-shadow', rx: 9, ry: 4 }, pin);
  const pinHead = el('g', { class: 'pin-head' }, pin);
  el('path', { d: pinPath(), class: 'pin-body' }, pinHead);
  el('circle', { cx: 0, cy: -25, r: 5, class: 'pin-dot' }, pinHead);
  const burst = el('circle', { cx: 0, cy: -25, r: 14, class: 'burst' }, pin);

  const ideaCard = root.querySelector('.hv-idea');
  const invCard = root.querySelector('.hv-inv');
  const ringFg = root.querySelector('.hv-ring .fg');
  const ringText = root.querySelector('.hv-ring b');
  const statusEl = root.querySelector('.hv-status');
  const stageBars = [...root.querySelectorAll('.hv-stages b')];
  const stageItems = [...root.querySelectorAll('.hv-stages > div')];

  let current = -1;
  let plan = null;

  function setIdea(index) {
    current = index;
    const idea = IDEAS[index];
    const [x, y] = blockOrigin(...idea.lot);
    const base = iso(x + 0.5, y + 0.5);
    // Votes come from roofs near the lot
    const near = roofs
      .map((r) => ({ r, d: Math.hypot(r.i - idea.lot[0], r.j - idea.lot[1]) }))
      .filter((o) => o.d > 0 && o.d < 3.2)
      .map((o) => o.r);
    const from = near.length ? near : roofs;
    plan = {
      idea,
      base,
      head: [base[0], base[1] - 42],
      lot: [x, y],
      votes: dots.map((_, k) => ({ start: 700 + k * 150, dur: 650 + (k % 5) * 70, src: from[(k * 7 + index * 3) % from.length].pos })),
    };
    root.querySelector('.hv-cat').textContent = `${categoryLabel(idea.cat)} · ${t(idea.hood)}`;
    root.querySelector('.hv-title').textContent = t(idea.title);
    root.querySelector('.hv-deal').textContent = idea.unlock === 'paid'
      ? t('hero.paid', { price: fmt.money(idea.price) })
      : t('hero.partner');
    pin.setAttribute('transform', `translate(${base[0]},${base[1]}) scale(1.4)`);
    radius.setAttribute('cx', base[0]);
    radius.setAttribute('cy', base[1]);
    Object.values(shops).forEach((g) => { g.innerHTML = ''; });
  }

  function anchor() {
    // Where the investor card sits, in map coordinates (it is on the inline-end side)
    const fx = isAr ? 0.3 : 0.7;
    return [VB.x + VB.w * fx, VB.y + VB.h * 0.7];
  }

  function frame(time) {
    const index = Math.floor(time / CYCLE) % IDEAS.length;
    if (index !== current) setIdea(index);
    const c = time % CYCLE;
    const p = plan;

    // Pin drop and fade out at the end
    const drop = ease(c / 600);
    const out = clamp01((c - 10300) / 600);
    pinHead.setAttribute('transform', `translate(0,${(-60 * (1 - drop)).toFixed(1)})`);
    pin.style.opacity = String(Math.min(drop * 1.5, 1) * (1 - out));
    radius.style.opacity = String(clamp01((c - 300) / 500) * (1 - clamp01((c - 5200) / 800)) * 0.9);

    // Votes travel in arcs and fill the target
    let arrived = 0;
    p.votes.forEach((v, k) => {
      const u = (c - v.start) / v.dur;
      const dot = dots[k];
      if (u < 0 || u > 1) {
        dot.style.opacity = '0';
        if (u > 1) arrived += 1;
        return;
      }
      const e = ease(u);
      const [sx, sy] = v.src;
      const [ex, ey] = p.head;
      const cx = (sx + ex) / 2;
      const cy = Math.min(sy, ey) - 70;
      const x = (1 - e) * (1 - e) * sx + 2 * (1 - e) * e * cx + e * e * ex;
      const y = (1 - e) * (1 - e) * sy + 2 * (1 - e) * e * cy + e * e * ey;
      dot.setAttribute('cx', x.toFixed(1));
      dot.setAttribute('cy', y.toFixed(1));
      dot.style.opacity = String(Math.min(1, u * 4, (1 - u) * 6));
    });
    const percent = Math.round((arrived / VOTES) * 100);
    ringFg.style.strokeDashoffset = String(100 - percent);
    ringText.textContent = `${percent}%`;

    const reached = arrived >= VOTES;
    root.classList.toggle('is-reached', reached);
    statusEl.textContent = reached ? t('hero.reached') : t('hero.collecting');
    burst.style.opacity = reached ? String(Math.max(0, 1 - (c - 5300) / 700)) : '0';
    burst.setAttribute('r', reached ? String(14 + clamp01((c - 5300) / 700) * 26) : '14');

    // Cards
    ideaCard.classList.toggle('show', c > 250 && c < 10300);
    invCard.classList.toggle('show', c > 5700 && c < 10300);

    // Investor link draws from the card to the pin
    const [ax, ay] = anchor();
    const [hx, hy] = p.head;
    link.setAttribute('d', `M${ax.toFixed(1)},${ay.toFixed(1)} Q${((ax + hx) / 2).toFixed(1)},${(Math.min(ay, hy) - 40).toFixed(1)} ${hx.toFixed(1)},${hy.toFixed(1)}`);
    const draw = ease((c - 5900) / 900);
    link.style.strokeDashoffset = String(100 - draw * 100);
    link.style.opacity = String(c > 5900 ? 1 - out : 0);

    // Opening stages and the shop rising on the lot
    stageBars.forEach((b, k) => {
      const v = clamp01((c - 6900 - k * 750) / 650);
      b.style.transform = `scaleX(${v.toFixed(3)})`;
      stageItems[k].classList.toggle('done', v >= 1);
    });
    const grow = ease((c - 6900) / 2600) * (1 - out);
    const key = p.idea.lot.join(',');
    const shop = shops[key];
    shop.innerHTML = '';
    if (grow > 0.01) {
      const [x, y] = p.lot;
      const h = 1.25 * grow;
      drawBox(shop, x + 0.12, x + 0.88, y + 0.12, y + 0.88, h, 'bld b-shop');
      if (grow > 0.6) {
        // Lit shop front and awning
        const a = clamp01((grow - 0.6) / 0.4);
        const front = el('polygon', { class: 'shop-glow', points: pts([iso(x + 0.25, y + 0.88, 0.1), iso(x + 0.75, y + 0.88, 0.1), iso(x + 0.75, y + 0.88, 0.55), iso(x + 0.25, y + 0.88, 0.55)]) }, shop);
        front.style.opacity = String(a);
        const awning = el('polygon', { class: 'awning', points: pts([iso(x + 0.2, y + 0.88, 0.62), iso(x + 0.8, y + 0.88, 0.62), iso(x + 0.8, y + 1.05, 0.5), iso(x + 0.2, y + 1.05, 0.5)]) }, shop);
        awning.style.opacity = String(a);
      }
    }
  }

  if (reduce) {
    frame(9800);
    return;
  }

  let clock = 0;
  let last = null;
  let visible = true;
  let raf = null;
  const tick = (now) => {
    if (last !== null) clock += Math.min(64, now - last);
    last = now;
    frame(clock);
    raf = requestAnimationFrame(tick);
  };
  const run = () => {
    const should = visible && !document.hidden;
    root.classList.toggle('paused', !should);
    if (should && raf === null) { last = null; raf = requestAnimationFrame(tick); }
    if (!should && raf !== null) { cancelAnimationFrame(raf); raf = null; }
  };
  if ('IntersectionObserver' in window) {
    new IntersectionObserver((entries) => { visible = entries[0].isIntersecting; run(); }).observe(root);
  }
  document.addEventListener('visibilitychange', run);
  frame(0);
  run();
}
