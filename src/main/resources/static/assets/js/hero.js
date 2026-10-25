// Landing hero: an animated isometric "demand map" of a neighborhood.
// Each cycle: an idea is pinned on an empty lot, neighbors' votes fly in and fill the target,
// a verified investor unlocks it, and the business rises on the lot: a cafe, a pharmacy, a family
// amusement park, a gym. Opened places stay, so the neighborhood fills up over the loop.
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
  { kind: 'cafe', cat: 'Cafe', title: 'hero.idea1', hood: 'hero.hood1', lot: [1, 4], unlock: 'paid', price: 18500 },
  { kind: 'pharmacy', cat: 'Pharmacy', title: 'hero.idea2', hood: 'hero.hood2', lot: [4, 1], unlock: 'partner' },
  { kind: 'fun', cat: 'Kids activities', title: 'hero.idea4', hood: 'hero.hood4', lot: [3, 3], unlock: 'paid', price: 45000 },
  { kind: 'gym', cat: "Women's gym", title: 'hero.idea3', hood: 'hero.hood3', lot: [1, 1], unlock: 'paid', price: 42000 },
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

// Rows of windows on both visible faces of a building
function drawWindows(parent, a, b, c, d, h, litChance) {
  const floors = Math.max(1, Math.floor(h / 0.32));
  for (let f = 0; f < floors; f += 1) {
    const z0 = 0.12 + f * 0.32;
    const z1 = z0 + 0.15;
    if (z1 > h - 0.06) break;
    // Left face (along x at y = d)
    for (let u = a + 0.1; u + 0.12 < b - 0.04; u += 0.24) {
      el('polygon', { class: rand() < litChance ? 'win lit' : 'win', points: pts([iso(u, d, z0), iso(u + 0.12, d, z0), iso(u + 0.12, d, z1), iso(u, d, z1)]) }, parent);
    }
    // Right face (along y at x = b)
    for (let v = c + 0.1; v + 0.12 < d - 0.04; v += 0.24) {
      el('polygon', { class: rand() < litChance ? 'win lit' : 'win', points: pts([iso(b, v, z0), iso(b, v + 0.12, z0), iso(b, v + 0.12, z1), iso(b, v, z1)]) }, parent);
    }
  }
}

function drawPalm(parent, x, y, scale = 1) {
  const [px, py] = iso(x, y);
  const g = el('g', { class: 'palm', transform: `translate(${px.toFixed(1)},${py.toFixed(1)}) scale(${scale})` }, parent);
  el('ellipse', { cx: 0, cy: 0, rx: 5, ry: 2.4, class: 'palm-shadow' }, g);
  el('path', { d: 'M0 0 Q 2 -9 0.5 -20', class: 'palm-trunk' }, g);
  ['M0.5 -20 Q -7 -24 -12 -18', 'M0.5 -20 Q 8 -25 13 -18', 'M0.5 -20 Q -3 -28 -9 -27', 'M0.5 -20 Q 5 -29 11 -26', 'M0.5 -20 Q 1 -27 1 -30']
    .forEach((d) => el('path', { d, class: 'palm-leaf' }, g));
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
      el('polygon', { class: 'path', points: pts([iso(x + 0.45, y), iso(x + 0.55, y), iso(x + 0.55, y + 1), iso(x + 0.45, y + 1)]) }, city);
      [[0.22, 0.25], [0.78, 0.35], [0.25, 0.75], [0.8, 0.8]].forEach(([u, v]) => drawPalm(city, x + u, y + v, 0.8));
      return;
    }
    // One tall building, or two lower ones side by side
    const parts = r < 0.55 ? [[0, 1]] : [[0, 0.48], [0.52, 1]];
    parts.forEach(([u0, u1], n) => {
      const h = 0.35 + rand() * (parts.length === 1 ? 1.5 : 0.9);
      const tone = rand() < 0.3 ? 'b-green' : 'b-sand';
      const g = drawBox(city, x + u0 + 0.04, x + u1 - 0.04, y + 0.04, y + 0.96, h, `bld ${tone}`);
      drawWindows(g, x + u0 + 0.04, x + u1 - 0.04, y + 0.04, y + 0.96, h, 0.35);
      roofs.push({ i, j, pos: iso(x + (u0 + u1) / 2, y + 0.5, h), n });
    });
  });
  return { roofs, shops };
}

function pinPath() {
  return 'M0 0 C -3 -9 -12 -15 -12 -25 A 12 12 0 1 1 12 -25 C 12 -15 3 -9 0 0 Z';
}

// ---------- The businesses that open on the lots ----------
// Each is drawn once at full size inside a "rise" group; the clock only scales it up from the ground.

// A round sign floating above the roof, with an icon drawn in a 24×24 box
function roofSign(parent, cx, cy, cls, iconPath, shape = 'circle') {
  const g = el('g', { class: `sign ${cls}`, transform: `translate(${cx.toFixed(1)},${cy.toFixed(1)})` }, parent);
  el('line', { x1: 0, y1: 4, x2: 0, y2: 16, class: 'sign-pole' }, g);
  if (shape === 'circle') el('circle', { cx: 0, cy: -8, r: 13, class: 'sign-bg' }, g);
  else el('rect', { x: -13, y: -21, width: 26, height: 26, rx: 6, class: 'sign-bg' }, g);
  el('path', { d: iconPath, class: 'sign-icon', transform: 'translate(-12,-20)' }, g);
  return g;
}

function awning(parent, x0, x1, y, zTop, cls = '') {
  const stripes = 6;
  const w = (x1 - x0) / stripes;
  for (let k = 0; k < stripes; k += 1) {
    const a = x0 + k * w;
    el('polygon', { class: `awn ${k % 2 ? 'b' : 'a'} ${cls}`, points: pts([iso(a, y, zTop), iso(a + w, y, zTop), iso(a + w, y + 0.18, zTop - 0.13), iso(a, y + 0.18, zTop - 0.13)]) }, parent);
  }
}

function drawCafe(g, x, y) {
  drawBox(g, x + 0.14, x + 0.86, y + 0.14, y + 0.82, 0.8, 'bld b-cafe');
  el('polygon', { class: 'shop-glow', points: pts([iso(x + 0.24, y + 0.82, 0.08), iso(x + 0.76, y + 0.82, 0.08), iso(x + 0.76, y + 0.82, 0.5), iso(x + 0.24, y + 0.82, 0.5)]) }, g);
  awning(g, x + 0.18, x + 0.82, y + 0.82, 0.62);
  // Two tables with umbrellas on the pavement
  [[0.3, 1.0], [0.68, 1.02]].forEach(([u, v]) => {
    const [tx, ty] = iso(x + u, y + v);
    el('line', { x1: tx, y1: ty, x2: tx, y2: ty - 13, class: 'pole' }, g);
    el('path', { d: `M${tx - 9} ${ty - 12} Q ${tx} ${ty - 22} ${tx + 9} ${ty - 12} Z`, class: 'umbrella' }, g);
    el('ellipse', { cx: tx, cy: ty, rx: 5, ry: 2.2, class: 'table' }, g);
  });
  const [sx, sy] = iso(x + 0.5, y + 0.48, 0.8);
  // Coffee cup with steam
  roofSign(g, sx, sy - 14, 'sign-cafe', 'M5 9h11v5a5.5 5.5 0 0 1-11 0zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 7c0-1.5 1.5-1.5 1.5-3M12 7c0-1.5 1.5-1.5 1.5-3');
}

function drawPharmacy(g, x, y) {
  drawBox(g, x + 0.12, x + 0.88, y + 0.12, y + 0.84, 1.05, 'bld b-pharm');
  el('polygon', { class: 'glass', points: pts([iso(x + 0.2, y + 0.84, 0.06), iso(x + 0.8, y + 0.84, 0.06), iso(x + 0.8, y + 0.84, 0.55), iso(x + 0.2, y + 0.84, 0.55)]) }, g);
  el('polygon', { class: 'band', points: pts([iso(x + 0.12, y + 0.84, 0.62), iso(x + 0.88, y + 0.84, 0.62), iso(x + 0.88, y + 0.84, 0.74), iso(x + 0.12, y + 0.84, 0.74)]) }, g);
  el('polygon', { class: 'band', points: pts([iso(x + 0.88, y + 0.12, 0.62), iso(x + 0.88, y + 0.84, 0.62), iso(x + 0.88, y + 0.84, 0.74), iso(x + 0.88, y + 0.12, 0.74)]) }, g);
  const [sx, sy] = iso(x + 0.5, y + 0.48, 1.05);
  // Green cross
  roofSign(g, sx, sy - 14, 'sign-pharm', 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z', 'square');
}

function drawGym(g, x, y) {
  drawBox(g, x + 0.1, x + 0.9, y + 0.14, y + 0.86, 0.95, 'bld b-gym');
  for (let k = 0; k < 4; k += 1) {
    const a = x + 0.18 + k * 0.17;
    el('polygon', { class: 'glass', points: pts([iso(a, y + 0.86, 0.12), iso(a + 0.12, y + 0.86, 0.12), iso(a + 0.12, y + 0.86, 0.72), iso(a, y + 0.86, 0.72)]) }, g);
  }
  const [sx, sy] = iso(x + 0.5, y + 0.5, 0.95);
  // Dumbbell
  roofSign(g, sx, sy - 14, 'sign-gym', 'M2 10h3v4H2zM5 8h3v8H5zM16 8h3v8h-3zM19 10h3v4h-3zM8 11h8v2H8z', 'square');
}

function drawFun(g, x, y) {
  // Plaza, fence and paths
  el('polygon', { class: 'fun-ground', points: pts([iso(x + 0.04, y + 0.04), iso(x + 0.96, y + 0.04), iso(x + 0.96, y + 0.96), iso(x + 0.04, y + 0.96)]) }, g);
  el('polygon', { class: 'fun-path', points: pts([iso(x + 0.04, y + 0.62), iso(x + 0.96, y + 0.62), iso(x + 0.96, y + 0.72), iso(x + 0.04, y + 0.72)]) }, g);
  drawPalm(g, x + 0.12, y + 0.14, 0.75);
  drawPalm(g, x + 0.9, y + 0.88, 0.75);

  // Ferris wheel (spins with CSS; the cabins counter-rotate so they hang straight)
  const [bx, by] = iso(x + 0.6, y + 0.36);
  const R = 30;
  const cy = by - 58;
  el('line', { x1: bx - 16, y1: by, x2: bx, y2: cy, class: 'wheel-leg' }, g);
  el('line', { x1: bx + 16, y1: by, x2: bx, y2: cy, class: 'wheel-leg' }, g);
  const wheel = el('g', { class: 'wheel', style: `transform-origin:${bx}px ${cy}px` }, g);
  el('circle', { cx: bx, cy, r: R, class: 'wheel-rim' }, wheel);
  el('circle', { cx: bx, cy, r: R - 7, class: 'wheel-rim inner' }, wheel);
  const colors = ['c1', 'c2', 'c3', 'c4'];
  for (let k = 0; k < 8; k += 1) {
    const a = (k / 8) * Math.PI * 2;
    const ex = bx + Math.cos(a) * R;
    const ey = cy + Math.sin(a) * R;
    el('line', { x1: bx, y1: cy, x2: ex, y2: ey, class: 'wheel-spoke' }, wheel);
    const cabin = el('g', { class: 'cabin', style: `transform-origin:${ex.toFixed(1)}px ${ey.toFixed(1)}px` }, wheel);
    el('rect', { x: (ex - 4.5).toFixed(1), y: ey.toFixed(1), width: 9, height: 8, rx: 2.5, class: `cabin-box ${colors[k % 4]}` }, cabin);
  }
  el('circle', { cx: bx, cy, r: 3.5, class: 'wheel-hub' }, g);

  // Carousel: base, striped cone roof and a flag
  const [kx, ky] = iso(x + 0.28, y + 0.8);
  el('ellipse', { cx: kx, cy: ky, rx: 17, ry: 8, class: 'carousel-base' }, g);
  el('rect', { x: kx - 14, y: ky - 13, width: 28, height: 13, class: 'carousel-body' }, g);
  const horses = el('g', { class: 'horses' }, g);
  [-8, 0, 8].forEach((dx, k) => el('line', { x1: kx + dx, y1: ky - 13, x2: kx + dx, y2: ky - 2, class: `horse-pole p${k}` }, horses));
  for (let k = 0; k < 6; k += 1) {
    const a0 = -17 + (k * 34) / 6;
    el('polygon', { class: `cone ${k % 2 ? 'b' : 'a'}`, points: pts([[kx, ky - 30], [kx + a0, ky - 13], [kx + a0 + 34 / 6, ky - 13]]) }, g);
  }
  el('line', { x1: kx, y1: ky - 30, x2: kx, y2: ky - 38, class: 'pole' }, g);
  el('path', { d: `M${kx} ${ky - 38} l8 2.5 l-8 2.5 z`, class: 'flag' }, g);

  // Balloons drifting above
  [[x + 0.18, y + 0.4, 'b1', 0], [x + 0.84, y + 0.5, 'b2', 1.2], [x + 0.5, y + 0.9, 'b3', 2.1]].forEach(([u, v, c, delay]) => {
    const [px, py] = iso(u, v);
    const b = el('g', { class: 'balloon', style: `animation-delay:${delay}s` }, g);
    el('path', { d: `M${px} ${py - 30} q 2 8 0 16`, class: 'balloon-string' }, b);
    el('ellipse', { cx: px, cy: py - 35, rx: 4.5, ry: 5.5, class: `balloon-body ${c}` }, b);
  });
}

const DRAW = { cafe: drawCafe, pharmacy: drawPharmacy, gym: drawGym, fun: drawFun };

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

  // Each lot's business, drawn once and hidden until it rises
  const rises = {};
  IDEAS.forEach((idea) => {
    const key = idea.lot.join(',');
    const [x, y] = blockOrigin(...idea.lot);
    const base = iso(x + 0.5, y + 0.6);
    const rise = el('g', { class: `rise rise-${idea.kind}` }, shops[key]);
    DRAW[idea.kind](rise, x, y);
    rises[key] = { g: rise, base, kind: idea.kind };
    setRise(rises[key], 0);
  });

  function setRise(r, v) {
    const k = Math.max(0.001, v);
    r.g.setAttribute('transform', `translate(${r.base[0].toFixed(1)},${r.base[1].toFixed(1)}) scale(${(0.6 + 0.4 * k).toFixed(3)},${k.toFixed(3)}) translate(${(-r.base[0]).toFixed(1)},${(-r.base[1]).toFixed(1)})`);
    r.g.style.opacity = v <= 0.01 ? '0' : String(Math.min(1, v * 2.5));
    r.g.classList.toggle('open', v >= 0.98);
  }

  // Active idea layer
  const radius = el('ellipse', { class: 'radius', rx: 2.6 * 1.2247 * S, ry: 2.6 * 0.7071 * S }, svg);
  const link = el('path', { class: 'link', pathLength: 100 }, svg);
  const particles = el('g', { class: 'votes' }, svg);
  const dots = Array.from({ length: VOTES }, () => el('circle', { r: 3.2, class: 'vote' }, particles));
  const pin = el('g', { class: 'pin' }, svg);
  el('ellipse', { class: 'ripple', rx: 22, ry: 11 }, pin);
  el('ellipse', { class: 'ripple r2', rx: 22, ry: 11 }, pin);
  el('ellipse', { class: 'pin-shadow', rx: 9, ry: 4 }, pin);
  const pinHead = el('g', { class: 'pin-head' }, pin);
  el('path', { d: pinPath(), class: 'pin-body' }, pinHead);
  el('circle', { cx: 0, cy: -25, r: 5, class: 'pin-dot' }, pinHead);
  const burst = el('circle', { cx: 0, cy: -25, r: 14, class: 'burst' }, pin);
  const confetti = el('g', { class: 'confetti' }, svg);
  const bits = Array.from({ length: 18 }, (_, k) => el('rect', { width: 4, height: 7, rx: 1, class: `bit k${k % 4}` }, confetti));

  const ideaCard = root.querySelector('.hv-idea');
  const invCard = root.querySelector('.hv-inv');
  const ringFg = root.querySelector('.hv-ring .fg');
  const ringText = root.querySelector('.hv-ring b');
  const statusEl = root.querySelector('.hv-status');
  const stageBars = [...root.querySelectorAll('.hv-stages b')];
  const stageItems = [...root.querySelectorAll('.hv-stages > div')];

  let current = -1;
  let loop = -1;
  let plan = null;

  function setIdea(index, loopNumber) {
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
      key: idea.lot.join(','),
      base,
      head: [base[0], base[1] - 42],
      votes: dots.map((_, k) => ({ start: 700 + k * 150, dur: 650 + (k % 5) * 70, src: from[(k * 7 + index * 3) % from.length].pos })),
      bits: bits.map((_, k) => ({ a: (k / bits.length) * Math.PI * 2 + (k % 3) * 0.3, v: 55 + (k % 5) * 14, spin: (k % 2 ? 1 : -1) * (200 + k * 30) })),
    };
    root.querySelector('.hv-cat').textContent = `${categoryLabel(idea.cat)} · ${t(idea.hood)}`;
    root.querySelector('.hv-title').textContent = t(idea.title);
    root.querySelector('.hv-deal').textContent = idea.unlock === 'paid'
      ? t('hero.paid', { price: fmt.money(idea.price) })
      : t('hero.partner');
    pin.setAttribute('transform', `translate(${base[0]},${base[1]}) scale(1.4)`);
    radius.setAttribute('cx', base[0]);
    radius.setAttribute('cy', base[1]);
    // A new loop starts from empty lots; within a loop the opened places stay
    if (loopNumber !== loop) {
      loop = loopNumber;
      IDEAS.forEach((other, k) => setRise(rises[other.lot.join(',')], k < index ? 1 : 0));
    }
  }

  function anchor() {
    // Where the investor card sits, in map coordinates (it is on the inline-end side)
    const fx = isAr ? 0.3 : 0.7;
    return [VB.x + VB.w * fx, VB.y + VB.h * 0.7];
  }

  function frame(time) {
    const step = Math.floor(time / CYCLE);
    const index = step % IDEAS.length;
    if (index !== current) setIdea(index, Math.floor(step / IDEAS.length));
    const c = time % CYCLE;
    const p = plan;

    // Pin drop, then it makes room for the building
    const drop = ease(c / 600);
    const out = clamp01((c - 8200) / 700);
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
    statusEl.textContent = c > 9600 ? t('hero.opened') : reached ? t('hero.reached') : t('hero.collecting');
    burst.style.opacity = reached ? String(Math.max(0, 1 - (c - 5300) / 700)) : '0';
    burst.setAttribute('r', reached ? String(14 + clamp01((c - 5300) / 700) * 26) : '14');

    // Cards
    ideaCard.classList.toggle('show', c > 250 && c < 10500);
    invCard.classList.toggle('show', c > 5700 && c < 10500);

    // Investor link draws from the card to the pin
    const [ax, ay] = anchor();
    const [hx, hy] = p.head;
    link.setAttribute('d', `M${ax.toFixed(1)},${ay.toFixed(1)} Q${((ax + hx) / 2).toFixed(1)},${(Math.min(ay, hy) - 40).toFixed(1)} ${hx.toFixed(1)},${hy.toFixed(1)}`);
    const draw = ease((c - 5900) / 900);
    link.style.strokeDashoffset = String(100 - draw * 100);
    link.style.opacity = String(c > 5900 ? 1 - out : 0);

    // Opening stages and the business rising on the lot
    stageBars.forEach((b, k) => {
      const v = clamp01((c - 6900 - k * 750) / 650);
      b.style.transform = `scaleX(${v.toFixed(3)})`;
      stageItems[k].classList.toggle('done', v >= 1);
    });
    setRise(rises[p.key], ease((c - 6900) / 2600));

    // Confetti when it opens
    const pop = (c - 9500) / 1300;
    if (pop > 0 && pop < 1) {
      const [ox, oy] = [p.base[0], p.base[1] - 50];
      plan.bits.forEach((b, k) => {
        const x = ox + Math.cos(b.a) * b.v * pop;
        const y = oy + Math.sin(b.a) * b.v * 0.6 * pop + 90 * pop * pop;
        bits[k].setAttribute('transform', `translate(${x.toFixed(1)},${y.toFixed(1)}) rotate(${(b.spin * pop).toFixed(0)})`);
        bits[k].style.opacity = String(1 - pop);
      });
    } else {
      bits.forEach((b) => { b.style.opacity = '0'; });
    }
  }

  if (reduce) {
    // A still frame with every place open
    frame(CYCLE * (IDEAS.length - 1) + 9800);
    Object.values(rises).forEach((r) => setRise(r, 1));
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
