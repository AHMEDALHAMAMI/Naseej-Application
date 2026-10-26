// Design system pieces shared by the pages: stepper, breadcrumb, tabs, AI block, score meter with its
// "how is this calculated?" popover, the min–max budget range, and the warning note under a button.
// Each returns HTML (or wires existing HTML) and uses the classes in app.css.
import { esc, icon, progressBar, t } from './ui.js';
import { locale } from './i18n.js';

// ---------- Stepper ----------

/**
 * stepper(['City', 'Neighborhood', 'Compare'], 1) → "Step 2 of 3" with the bar.
 * links: optional hrefs (or null) for earlier steps, so people can go back to them.
 */
export function stepper(labels, current, { links = [], buttons = [] } = {}) {
  // links[i]: a done step opens that page; buttons[i]: a done step is a button (data-step="i") the page handles
  const items = labels.map((text, i) => {
    const state = i < current ? 'done' : '';
    const aria = i === current ? ' aria-current="step"' : '';
    const inner = i < current && links[i]
      ? `<a href="${esc(links[i])}">${esc(text)}</a>`
      : i < current && buttons[i]
        ? `<button type="button" data-step="${i}">${esc(text)}</button>`
        : `<span>${esc(text)}</span>`;
    return `<li class="${state}"${aria}>${inner}</li>`;
  }).join('');
  return `<nav class="stepper" aria-label="${esc(t('ds.steps'))}">
    <span class="stepper-label">${esc(t('ds.step', { n: current + 1, total: labels.length }))}: ${esc(labels[current] || '')}</span>
    <ol>${items}</ol>
  </nav>`;
}

// ---------- Breadcrumb ----------

/** breadcrumb([{ label, href }, { label, action: 'city' }, { label }]) — the last item is the current page. */
export function breadcrumb(items) {
  const parts = items.map((item, i) => {
    const last = i === items.length - 1;
    if (last) return `<li><span aria-current="page">${esc(item.label)}</span></li>`;
    if (item.href) return `<li><a href="${esc(item.href)}">${esc(item.label)}</a></li>`;
    return `<li><button type="button" data-crumb="${esc(item.action || i)}">${esc(item.label)}</button></li>`;
  }).join('');
  return `<nav class="breadcrumb" aria-label="${esc(t('ds.breadcrumb'))}"><ol>${parts}</ol></nav>`;
}

// ---------- Tabs ----------

/**
 * Wires <div class="tabs" role="tablist"> buttons (role="tab", aria-controls) to their panels.
 * The selected tab is kept in the URL hash, so links like account.html#addresses open it.
 */
export function wireTabs(root, { onChange } = {}) {
  const tabs = [...root.querySelectorAll('[role="tab"]')];
  const select = (tab, focus = false) => {
    tabs.forEach((x) => {
      const on = x === tab;
      x.setAttribute('aria-selected', String(on));
      x.tabIndex = on ? 0 : -1;
      document.getElementById(x.getAttribute('aria-controls'))?.toggleAttribute('hidden', !on);
    });
    if (focus) tab.focus();
    history.replaceState(null, '', `#${tab.dataset.tab}`);
    onChange?.(tab.dataset.tab);
  };
  tabs.forEach((tab, i) => {
    tab.addEventListener('click', () => select(tab));
    tab.addEventListener('keydown', (e) => {
      // Arrow keys follow the reading direction
      const rtl = document.documentElement.dir === 'rtl';
      const next = { ArrowRight: rtl ? -1 : 1, ArrowLeft: rtl ? 1 : -1 }[e.key];
      if (next) { e.preventDefault(); select(tabs[(i + next + tabs.length) % tabs.length], true); }
      if (e.key === 'Home') { e.preventDefault(); select(tabs[0], true); }
      if (e.key === 'End') { e.preventDefault(); select(tabs[tabs.length - 1], true); }
    });
  });
  const fromHash = tabs.find((x) => `#${x.dataset.tab}` === location.hash);
  select(fromHash || tabs.find((x) => !x.hidden) || tabs[0]);
}

// ---------- AI block ----------

/** A labelled panel for AI output only (no buttons, no system messages inside it). */
export function aiBlock(innerHtml, { title = '' } = {}) {
  return `<section class="ai-block">
    <span class="ai-block-label">${icon.spark}${esc(t('ai.blockLabel'))}</span>
    ${title ? `<h3>${esc(title)}</h3>` : ''}
    ${innerHtml}
  </section>`;
}

// ---------- Warning note under a button ----------

export function warnNote(text) {
  return `<p class="btn-warn-note">${icon.warn}<span>${esc(text)}</span></p>`;
}

// ---------- Score meter ----------

export function scoreBand(score) {
  if (score === null || score === undefined) return null;
  return score >= 75 ? 'strong' : score >= 50 ? 'fair' : 'weak';
}

let popoverId = 0;

/**
 * The feasibility score with its band, a "how is this calculated?" popover and the three parts.
 * parts: { shortage, competition, demand } (any may be null for older ideas).
 * compact: the headline only (for cards); the parts stay in the popover.
 */
export function scoreMeter(score, parts = {}, { compact = false } = {}) {
  if (score === null || score === undefined) return '';
  const band = scoreBand(score);
  const has = ['shortage', 'competition', 'demand'].some((k) => parts[k] !== null && parts[k] !== undefined);
  const id = `pop-${++popoverId}`;
  const rows = has ? `<ul class="score-parts">${['shortage', 'competition', 'demand'].map((k) => `
      <li><span>${esc(t(`score.${k}`))}</span><strong>${parts[k] ?? '—'}</strong>${progressBar(parts[k] ?? 0, t(`score.${k}`))}</li>`).join('')}
    </ul>` : '';
  return `<div class="score${compact ? ' compact' : ''}">
    <div class="score-head">
      <span class="score-name">${esc(t('score.name'))}</span>
      <span class="score-value">${score}<small>/100</small></span>
      <span class="score-band ${band}">${esc(t(`score.band.${band}`))}</span>
      <span class="popover-wrap">
        <button type="button" class="info-btn" aria-expanded="false" aria-controls="${id}">${icon.info}<span${compact ? ' class="sr-only"' : ''}>${esc(t('score.how'))}</span></button>
        <div class="popover" id="${id}" role="region" aria-label="${esc(t('score.how'))}" hidden>
          <h4>${esc(t('score.name'))}</h4>
          <p>${esc(t('score.what'))}</p>
          <ul>
            <li><strong>${esc(t('score.shortage'))}:</strong> ${esc(t('score.shortageHelp'))}</li>
            <li><strong>${esc(t('score.competition'))}:</strong> ${esc(t('score.competitionHelp'))}</li>
            <li><strong>${esc(t('score.demand'))}:</strong> ${esc(t('score.demandHelp'))}</li>
          </ul>
          <p class="popover-bands">${esc(t('score.bands'))}</p>
        </div>
      </span>
    </div>
    ${compact ? '' : (rows || `<span class="score-note">${esc(t('score.noParts'))}</span>`)}
  </div>`;
}

/** Opens and closes the popovers inside root (click or Enter/Space; Escape or a click outside closes). */
export function wirePopovers(root = document) {
  root.querySelectorAll('.popover-wrap > button[aria-controls]').forEach((btn) => {
    if (btn.dataset.wired) return;
    btn.dataset.wired = '1';
    const pop = document.getElementById(btn.getAttribute('aria-controls'));
    const set = (open) => { pop.hidden = !open; btn.setAttribute('aria-expanded', String(open)); };
    btn.addEventListener('click', (e) => { e.stopPropagation(); set(pop.hidden); });
    document.addEventListener('click', (e) => { if (!pop.contains(e.target)) set(false); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !pop.hidden) { set(false); btn.focus(); } });
  });
}

// ---------- Budget range (min–max) ----------

// 50K to 1M in 25K steps, then to 5M in 250K steps. The last stop means "5M and above" (no upper limit).
export const BUDGET_STOPS = [
  ...Array.from({ length: 39 }, (_, i) => 50000 + i * 25000),
  ...Array.from({ length: 16 }, (_, i) => 1250000 + i * 250000),
];
const COMPACT = new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 2 });
const PLAIN = new Intl.NumberFormat('en-US');

const nearestStop = (value, stops) => stops.reduce((best, s, i) => (Math.abs(s - value) < Math.abs(stops[best] - value) ? i : best), 0);

/**
 * Two-thumb range with typed inputs as the accessible alternative.
 * rangeSlider(el, { value: [min, max|null], onChange([min, max|null]) })
 * max === null means "and above" (the last stop). Returns { get, set }.
 */
export function rangeSlider(el, { stops = BUDGET_STOPS, value = [stops[0], null], onChange, label = '' } = {}) {
  const last = stops.length - 1;
  const uid = `rng-${++popoverId}`;
  const money = (v) => t('fmt.sar', { v: COMPACT.format(v) });
  el.innerHTML = `
    <div class="range" role="group" aria-labelledby="${uid}-label">
      ${label ? `<span class="label" id="${uid}-label">${esc(label)}</span>` : `<span class="sr-only" id="${uid}-label">${esc(t('pref.budget'))}</span>`}
      <span class="range-value" aria-live="polite"></span>
      <div class="range-track">
        <span class="range-fill"></span>
        <input type="range" min="0" max="${last}" step="1" aria-label="${esc(t('range.minLabel'))}" data-end="min">
        <input type="range" min="0" max="${last}" step="1" aria-label="${esc(t('range.maxLabel'))}" data-end="max">
      </div>
      <div class="range-inputs">
        <div class="field"><label for="${uid}-min">${esc(t('range.from'))}</label><input class="input num" id="${uid}-min" inputmode="numeric" autocomplete="off"></div>
        <span class="sep" aria-hidden="true">–</span>
        <div class="field"><label for="${uid}-max">${esc(t('range.to'))}</label><input class="input num" id="${uid}-max" inputmode="numeric" autocomplete="off"></div>
      </div>
    </div>`;
  const [lo, hi] = el.querySelectorAll('input[type="range"]');
  const minIn = el.querySelector(`#${uid}-min`);
  const maxIn = el.querySelector(`#${uid}-max`);
  const out = el.querySelector('.range-value');
  const fill = el.querySelector('.range-fill');
  let a = nearestStop(value[0] ?? stops[0], stops);
  let b = value[1] === null || value[1] === undefined ? last : nearestStop(value[1], stops);

  const current = () => [stops[a], b === last ? null : stops[b]];
  const render = (emit) => {
    lo.value = a;
    hi.value = b;
    const maxText = b === last ? t('range.noMax', { v: money(stops[last]) }) : money(stops[b]);
    lo.setAttribute('aria-valuetext', money(stops[a]));
    hi.setAttribute('aria-valuetext', maxText);
    out.textContent = t('range.value', { min: money(stops[a]), max: maxText });
    if (document.activeElement !== minIn) minIn.value = PLAIN.format(stops[a]);
    if (document.activeElement !== maxIn) maxIn.value = b === last ? `${PLAIN.format(stops[last])}+` : PLAIN.format(stops[b]);
    // The fill runs between the thumbs in the reading direction
    const start = (a / last) * 100;
    const end = (b / last) * 100;
    fill.style.insetInlineStart = `calc(11px + (100% - 22px) * ${start / 100})`;
    fill.style.width = `calc((100% - 22px) * ${(end - start) / 100})`;
    if (emit) onChange?.(current());
  };

  lo.addEventListener('input', () => { a = Math.min(Number(lo.value), b); render(true); });
  hi.addEventListener('input', () => { b = Math.max(Number(hi.value), a); render(true); });
  const typed = (input, end) => () => {
    const digits = Number(String(input.value).replace(/[^\d]/g, ''));
    if (!digits) { render(false); return; }
    const i = nearestStop(digits, stops);
    if (end === 'min') a = Math.min(i, b); else b = Math.max(i, a);
    render(true);
  };
  minIn.addEventListener('change', typed(minIn, 'min'));
  maxIn.addEventListener('change', typed(maxIn, 'max'));
  [minIn, maxIn].forEach((input) => input.addEventListener('blur', () => render(false)));
  render(false);

  return {
    get: current,
    set([min, max]) {
      a = nearestStop(min ?? stops[0], stops);
      b = max === null || max === undefined ? last : Math.max(a, nearestStop(max, stops));
      render(false);
    },
  };
}

// ---------- Category icons: one stroke icon and one color family per kind of business ----------

const CAT_ICONS = {
  Pharmacy: ['health', 'M10 4h4v6h6v4h-6v6h-4v-6H4v-4h6z'],
  Clinic: ['health', 'M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10zM12 9v4M10 11h4'],
  Cafe: ['food', 'M4 9h12v5a6 6 0 0 1-12 0zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M12 3v3'],
  Restaurant: ['food', 'M7 3v8a2 2 0 0 0 2 2v8M11 3v8M3 3v8a2 2 0 0 0 2 2M17 3c-2 0-3 3-3 6s1 4 3 4v8'],
  Bakery: ['food', 'M4 13a8 5 0 0 1 16 0v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM9 10l1 4M14 10l-1 4'],
  Grocery: ['retail', 'M3 4h2l2.4 10.5a1 1 0 0 0 1 .8h8.8a1 1 0 0 0 1-.8L20 8H6.2M9 20h.01M17 20h.01'],
  'School supplies': ['retail', 'M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 19a2 2 0 0 1 2-2h13'],
  Gym: ['fit', 'M3 10v4M6 8v8M18 8v8M21 10v4M6 12h12'],
  "Women's gym": ['fit', 'M3 10v4M6 8v8M18 8v8M21 10v4M6 12h12'],
  'Kids activities': ['kids', 'M12 3a5 5 0 0 1 5 5c0 4-5 7-5 7s-5-3-5-7a5 5 0 0 1 5-5zM12 15v6'],
  Salon: ['care', 'M12 3l2 5 5 2-5 2-2 5-2-5-5-2 5-2z'],
  Barber: ['care', 'M6 6m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0M6 18m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0M8.5 8.5L20 20M8.5 15.5L20 4'],
  Laundry: ['service', 'M5 3h14v18H5zM5 7h14M12 15m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0'],
  'Car services': ['service', 'M5 16l1.5-5h11l1.5 5M4 16h16v3H4zM7 19v1M17 19v1'],
};

/** The category's icon in a tinted tile (decorative: the category name is always shown next to it). */
export function catTile(category, { size = 22 } = {}) {
  const [tone, d] = CAT_ICONS[category] || ['service', 'M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0'];
  return `<span class="cat-tile tone-${tone}" aria-hidden="true"><svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg></span>`;
}

// ---------- Budget fit label ----------

/** «ضمن ميزانيتك» / partly / no estimate. Shown only when the server worked it out from a real budget. */
export function fitBadge(fit) {
  if (!fit || fit === 'NONE') return '';
  const cls = { FULL: 'full', PARTIAL: 'partial', UNKNOWN: 'unknown' }[fit];
  return `<span class="fit ${cls}">${esc(t(`fit.${fit}`))}</span>`;
}

// ---------- Early start: the button's warning, always right under it ----------

/** «ابدأ قبل اكتمال التصويت» with «تنبيه: كل ما زادت الأصوات زاد سعر الفكرة.» under it. */
export function earlyStart(href, { small = false } = {}) {
  return `<div class="early-cta">
    <a class="btn${small ? ' btn-sm' : ''}" href="${esc(href)}">${esc(t('early.cta'))}</a>
    ${warnNote(t('early.warn'))}
  </div>`;
}

// ---------- Sticky action bar for the last step of a flow ----------

export function actionBar(inner) {
  return `<div class="action-bar">${inner}</div>`;
}
