// Shared UI: app header, role guard, toasts, modals, formatting and small helpers.
import { api, session, ApiError } from './api.js';
import { t, locale, isAr, serverText, applyI18n, langButton, wireLangButtons } from './i18n.js';

export { t };

// ---------- Text helpers ----------

export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const NUM = new Intl.NumberFormat(locale);
const SAR_NUM = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const fmt = {
  sar: (v) => (v === null || v === undefined ? '—' : t('fmt.sar', { v: SAR_NUM.format(Number(v)) })),
  num: (v) => (v === null || v === undefined ? '—' : NUM.format(Number(v))),
  km: (v) => (v === null || v === undefined ? '—' : t('fmt.km', { v: Number(v).toFixed(Number(v) < 10 ? 1 : 0) })),
  date: (v) => (v ? new Date(v).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' }) : '—'),
  dateTime: (v) => (v ? new Date(v).toLocaleString(locale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'),
  money: (v) => (v === null || v === undefined ? '—' : t('fmt.sar', { v: NUM.format(Math.round(Number(v))) })),
  /** Short money for chips and cards: 450000 → "450 ألف ر.س", 1200000 → "1.2 مليون ر.س". */
  short: (v) => {
    if (v === null || v === undefined || v === '') return '—';
    const n = Number(v);
    const trim = (x, d) => new Intl.NumberFormat(locale, { maximumFractionDigits: d }).format(x);
    if (n >= 1e6) return t('fmt.m', { v: trim(n / 1e6, 2) });
    if (n >= 1e3) return t('fmt.k', { v: trim(n / 1e3, 0) });
    return t('fmt.sar', { v: NUM.format(Math.round(n)) });
  },
};

/** Label for a value that comes from the server: label('status', 'PUBLISHED') → "Voting open" / "التصويت مفتوح". */
export function label(group, value) {
  if (value === null || value === undefined || value === '') return '';
  const key = `${group}.${value}`;
  const text = t(key);
  return text === key ? String(value) : text;
}

const groupKeys = (group, values) => Object.fromEntries(values.map((v) => [v, label(group, v)]));
export const LABELS = {
  role: groupKeys('role', ['RESIDENT', 'INVESTOR', 'ADMIN', 'LANDLORD', 'PENDING']),
  budget: groupKeys('budget', ['UP_TO_300K', 'FROM_300K_TO_700K', 'ABOVE_700K']),
  budgetAll: groupKeys('budget', ['UP_TO_300K', 'FROM_300K_TO_700K', 'ABOVE_700K', 'CUSTOM']),
  field: groupKeys('field', ['FOOD', 'RETAIL', 'HEALTH', 'FITNESS', 'HOME_SERVICES', 'EDUCATION']),
  status: groupKeys('status', ['SCREENING', 'PUBLISHED', 'COMPLETED', 'ADOPTED', 'OPENED', 'REJECTED']),
  access: groupKeys('access', ['PAID', 'PARTNERSHIP', 'OWNER']),
};

// Category values are saved in English; only the label shown changes with the language
export const CATEGORIES = [
  'Grocery', 'Bakery', 'Cafe', 'Restaurant', 'Pharmacy', 'Clinic', "Women's gym", 'Gym', 'Laundry',
  'Barber', 'Salon', 'Kids activities', 'School supplies', 'Car services', 'Other',
];
export const categoryLabel = (value) => label('cat', value);

// ---------- Investor experience helpers (budget, fields, places) ----------

// Which idea categories belong to each investment field (used to match opportunities to an investor)
export const FIELD_CATEGORIES = {
  FOOD: ['Grocery', 'Bakery', 'Cafe', 'Restaurant'],
  RETAIL: ['Grocery', 'School supplies'],
  HEALTH: ['Pharmacy', 'Clinic'],
  FITNESS: ["Women's gym", 'Gym'],
  HOME_SERVICES: ['Laundry', 'Car services', 'Barber', 'Salon'],
  EDUCATION: ['Kids activities', 'School supplies'],
};

// Budget presets as SAR ranges
export const BUDGET_RANGES = {
  UP_TO_300K: [null, 300000],
  FROM_300K_TO_700K: [300000, 700000],
  ABOVE_700K: [700000, null],
};

/** The investor's budget as a [min, max] range in SAR (either end may be null), or null when not set. */
export function budgetRange(user) {
  if (!user?.investmentBudget) return null;
  if (user.investmentBudget === 'CUSTOM') {
    if (user.budgetMin == null && user.budgetMax == null) return null;
    return [user.budgetMin ?? null, user.budgetMax ?? null];
  }
  return BUDGET_RANGES[user.investmentBudget] || null;
}

/** A money range in short form, sharing the unit when it can: "250 – 450 ألف ر.س". */
export function shortRange(min, max) {
  if (min == null && max == null) return '';
  if (min == null || max == null || Number(min) === Number(max)) return fmt.short(min ?? max);
  const lo = Number(min);
  const hi = Number(max);
  const n = (x, d) => new Intl.NumberFormat(locale, { maximumFractionDigits: d }).format(x);
  if (lo >= 1e3 && hi < 1e6) return t('fmt.k', { v: `${n(lo / 1e3, 0)} – ${n(hi / 1e3, 0)}` });
  if (lo >= 1e6) return t('fmt.m', { v: `${n(lo / 1e6, 2)} – ${n(hi / 1e6, 2)}` });
  return t('pref.range', { min: fmt.short(lo), max: fmt.short(hi) });
}

/** The investor's budget in words: a preset label or the custom range. */
export function budgetText(user) {
  if (!user?.investmentBudget) return '';
  if (user.investmentBudget !== 'CUSTOM') return label('budget', user.investmentBudget);
  const { budgetMin: min, budgetMax: max } = user;
  if (min != null && max != null) return shortRange(min, max);
  if (max != null) return t('pref.upTo', { v: fmt.short(max) });
  if (min != null) return t('pref.from', { v: fmt.short(min) });
  return '';
}

/** Comma-separated text from the server as a list ("A,B" → ['A', 'B']). */
export const csv = (value) => (value ? String(value).split(',').map((s) => s.trim()).filter(Boolean) : []);

// Saudi city names are saved in English; show them in Arabic when the page is Arabic
const CITY_AR = {
  riyadh: 'الرياض', jeddah: 'جدة', jiddah: 'جدة', makkah: 'مكة المكرمة', mecca: 'مكة المكرمة',
  madinah: 'المدينة المنورة', medina: 'المدينة المنورة', dammam: 'الدمام', khobar: 'الخبر', dhahran: 'الظهران',
  taif: 'الطائف', tabuk: 'تبوك', abha: 'أبها', 'khamis mushait': 'خميس مشيط', buraidah: 'بريدة', buraydah: 'بريدة',
  unaizah: 'عنيزة', hail: 'حائل', "ha'il": 'حائل', jazan: 'جازان', jizan: 'جازان', najran: 'نجران', ahsa: 'الأحساء',
  hofuf: 'الهفوف', jubail: 'الجبيل', yanbu: 'ينبع', kharj: 'الخرج', qatif: 'القطيف', arar: 'عرعر', sakaka: 'سكاكا',
  baha: 'الباحة', qassim: 'القصيم', diriyah: 'الدرعية',
};
export function cityLabel(city) {
  if (!city || !isAr) return city || '';
  const key = String(city).trim().toLowerCase().replace(/^al[\s-]+/, '').replace(/-/g, ' ');
  return CITY_AR[key] || city;
}
/** "Neighborhood, City" with the city translated. */
export const placeLabel = (hood, city) => [hood, cityLabel(city)].filter(Boolean).join(isAr ? '، ' : ', ');
export const stageLabel = (value) => label('stage', value);

// ---------- Icons ----------

export const icon = {
  bell: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9.5a6 6 0 0 1 12 0c0 4.5 1.8 6.5 2.5 7.5h-17C4.2 16 6 14 6 9.5z"/><path d="M10 20a2.2 2.2 0 0 0 4 0"/></svg>',
  pin: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/></svg>',
  check: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 12l5 5L20 6"/></svg>',
  info: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/></svg>',
  warn: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l9.5 17h-19z"/><path d="M12 10v4M12 17v.5"/></svg>',
  x: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6M15 9l-6 6"/></svg>',
  spark: '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.2 5.6 5.8 2.2-5.8 2.2L12 18.1l-2.2-5.6L4 10.3l5.8-2.2z"/></svg>',
  arrow: '<svg class="flip-rtl" width="18" height="10" viewBox="0 0 18 10" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 5h16M13 1l4 4-4 4"/></svg>',
  back: '<svg class="flip-rtl" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>',
  map: '<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 4L3 6.5v13.5L9 17.5l6 2.5 6-2.5V4L15 6.5z"/><path d="M9 4v13.5M15 6.5V20"/></svg>',
  lock: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>',
  google: '<svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.1-4.1 5.6l6.2 5.2C41.4 35.3 44 30 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg>',
};

// ---------- Toasts ----------

function toastHost() {
  let host = $('.toasts');
  if (!host) {
    host = document.createElement('div');
    host.className = 'toasts';
    host.setAttribute('role', 'status');
    host.setAttribute('aria-live', 'polite');
    document.body.appendChild(host);
  }
  return host;
}

export function toast(message, type = 'info', ms = 4500) {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span>${esc(message)}</span><button type="button" aria-label="${esc(t('common.dismiss'))}">×</button>`;
  el.querySelector('button').onclick = () => el.remove();
  toastHost().appendChild(el);
  setTimeout(() => el.remove(), ms);
}

export function errorMessage(err) {
  if (err instanceof ApiError) {
    if (err.fieldErrors) {
      const first = Object.values(err.fieldErrors)[0];
      if (first) return serverText(first);
    }
    return serverText(err.message);
  }
  return serverText(err?.message) || t('common.error');
}

export function showError(err) {
  toast(errorMessage(err), 'error', 6000);
}

/** Shows field errors from a 400 response next to inputs with matching name/id. */
export function applyFieldErrors(form, err) {
  $$('[aria-invalid="true"]', form).forEach((el) => el.removeAttribute('aria-invalid'));
  $$('.field .error', form).forEach((el) => { el.textContent = ''; });
  if (!(err instanceof ApiError) || !err.fieldErrors) return false;
  let first = null;
  for (const [name, message] of Object.entries(err.fieldErrors)) {
    const input = form.querySelector(`[name="${name}"]`);
    if (!input) continue;
    input.setAttribute('aria-invalid', 'true');
    const box = input.closest('.field')?.querySelector('.error');
    if (box) box.textContent = serverText(message);
    first ??= input;
  }
  first?.focus();
  return !!first;
}

// ---------- Buttons ----------

export async function withLoading(button, task) {
  if (!button) return task();
  const original = button.innerHTML;
  button.disabled = true;
  button.setAttribute('aria-busy', 'true');
  button.innerHTML = `<span class="spinner" aria-hidden="true"></span><span>${esc(button.dataset.loading || t('common.wait'))}</span>`;
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.innerHTML = original;
  }
}

// ---------- Modal ----------

export function modal({ title, body, confirmText = t('common.confirm'), cancelText = t('common.cancel'), danger = false, wide = false, onConfirm }) {
  return new Promise((resolve) => {
    const back = document.createElement('div');
    back.className = 'modal-backdrop';
    back.innerHTML = `
      <div class="modal${wide ? ' modal-wide' : ''}" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <h2 id="modal-title">${esc(title)}</h2>
        <div class="stack" style="margin-top:8px">${body}</div>
        <div class="row modal-actions" style="justify-content:flex-end;margin-top:22px">
          <button type="button" class="btn btn-secondary" data-act="cancel">${esc(cancelText)}</button>
          <button type="button" class="btn ${danger ? 'btn-danger' : ''}" data-act="ok">${esc(confirmText)}</button>
        </div>
      </div>`;
    const previous = document.activeElement;
    const close = (value) => { back.remove(); document.removeEventListener('keydown', onKey); previous?.focus?.(); resolve(value); };
    // Escape closes; Tab stays inside the dialog
    const onKey = (e) => {
      if (e.key === 'Escape') { close(false); return; }
      if (e.key !== 'Tab') return;
      const items = $$('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])', back)
        .filter((el) => el.offsetParent !== null);
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    back.addEventListener('click', (e) => { if (e.target === back) close(false); });
    back.querySelector('[data-act="cancel"]').onclick = () => close(false);
    const ok = back.querySelector('[data-act="ok"]');
    ok.onclick = async () => {
      if (!onConfirm) return close(true);
      try {
        const result = await withLoading(ok, () => onConfirm(back));
        if (result !== false) close(result ?? true);
      } catch (err) {
        showError(err);
      }
    };
    document.body.appendChild(back);
    (back.querySelector('input, textarea, select') || ok).focus();
  });
}

// ---------- Session / guard / header ----------

export function homeFor(user) {
  if (!user) return 'login.html';
  if (user.role === 'PENDING') return 'choose-role.html';
  if (user.role === 'INVESTOR') return 'investor.html';
  if (user.role === 'ADMIN') return 'admin.html';
  return 'resident.html';
}

/**
 * Checks the user is logged in with one of the roles, refreshes the profile from /auth/me,
 * and draws the header. Redirects when the user can't be on this page.
 */
export async function guard(roles, { header = true } = {}) {
  initPage();
  if (!session.isLoggedIn()) {
    const here = location.pathname.split('/').pop() + location.search;
    location.replace('login.html?next=' + encodeURIComponent(here));
    return new Promise(() => {});
  }
  let user;
  try {
    user = await api.get('auth/me');
    session.setUser(user);
  } catch (err) {
    if (err instanceof ApiError && err.status === 0) {
      user = session.user();
      toast(errorMessage(err), 'error');
    } else {
      return new Promise(() => {}); // 401 already redirected to login
    }
  }
  if (user.role === 'PENDING') {
    location.replace('choose-role.html');
    return new Promise(() => {});
  }
  if (roles && !roles.includes(user.role)) {
    location.replace(homeFor(user));
    return new Promise(() => {});
  }
  if (header) renderHeader(user);
  return user;
}

const NAV = {
  RESIDENT: [['resident.html', 'nav.around'], ['propose.html', 'nav.propose'], ['projects.html', 'nav.myIdeas']],
  // Opportunities that fit (list + map), AI search, projects (and proposed ideas). The account is in the avatar menu
  INVESTOR: [['investor.html', 'nav.forYou'], ['ask.html', 'nav.ask'], ['projects.html', 'nav.myProjects']],
  ADMIN: [['admin.html', 'nav.dashboard'], ['admin.html#members', 'nav.members'], ['admin.html#investors', 'nav.verification'], ['admin.html#neighborhoods', 'nav.neighborhoods']],
};

export function renderHeader(user) {
  const here = location.pathname.split('/').pop();
  const links = (NAV[user.role] || [])
    .map(([href, key]) => `<a href="${href}"${href === here ? ' aria-current="page"' : ''}>${esc(t(key))}</a>`)
    .join('');
  const initials = (user.fullName || '?').split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  const roleText = isAr ? label('role', user.role) : label('role', user.role).toUpperCase();
  const header = document.createElement('header');
  header.className = 'app-header';
  header.innerHTML = `
    <div class="container bar">
      <a class="brand" href="${homeFor(user)}" aria-label="${esc(t('brand.home'))}"><img src="assets/img/mark.png" alt=""><span>${esc(t('brand.name'))}</span></a>
      <nav class="app-nav" aria-label="${esc(t('nav.main'))}">${links}</nav>
      ${langButton('btn-ghost')}
      <div class="bell">
        <button type="button" class="bell-btn" aria-expanded="false" aria-controls="notif-panel" aria-label="${esc(t('notif.title'))}">
          ${icon.bell}<span class="notif-badge" hidden></span>
        </button>
        <section class="menu notif" id="notif-panel" aria-labelledby="notif-h">
          <div class="notif-head"><strong id="notif-h">${esc(t('notif.title'))}</strong><span class="small muted" id="notif-count" aria-live="polite"></span></div>
          <ul class="notif-list" id="notif-list"><li class="notif-empty">${esc(t('common.loading'))}</li></ul>
        </section>
      </div>
      <div class="account">
        <button type="button" class="account-btn" aria-haspopup="menu" aria-expanded="false" aria-label="${esc(t('nav.accountMenu'))}">
          <span class="avatar" aria-hidden="true">${esc(initials)}</span>
          <span class="who"><strong>${esc(user.fullName)}</strong><span>${esc(roleText)}</span></span>
        </button>
        <div class="menu" role="menu">
          <div class="small muted menu-email">${esc(user.email)}</div>
          <a role="menuitem" href="account.html">${esc(t('nav.account'))}</a>
          <button type="button" role="menuitem" data-act="logout">${esc(t('nav.logout'))}</button>
        </div>
      </div>
    </div>`;
  const skip = document.querySelector('a.sr-only');
  if (skip) skip.after(header); else document.body.prepend(header);
  // Two popovers that never overlap: the notifications under the bell, the account menu under the name
  const pops = [
    [header.querySelector('.bell-btn'), header.querySelector('#notif-panel')],
    [header.querySelector('.account-btn'), header.querySelector('.account .menu')],
  ];
  const closeAll = () => pops.forEach(([b, m]) => { m.classList.remove('open'); b.setAttribute('aria-expanded', 'false'); });
  pops.forEach(([b, m], i) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const opening = !m.classList.contains('open');
      closeAll();
      if (!opening) return;
      m.classList.add('open');
      b.setAttribute('aria-expanded', 'true');
      if (i === 0) openNotifications();
    };
    m.addEventListener('click', (e) => e.stopPropagation());
  });
  document.addEventListener('click', closeAll);
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    const open = pops.find(([, m]) => m.classList.contains('open'));
    closeAll();
    open?.[0].focus();
  });
  header.querySelector('[data-act="logout"]').onclick = logout;
  wireLangButtons(header);
  watchNotifications();
  renderFooter(user);
}

// A quiet close for every app page: the name, the home for this role, the other language
function renderFooter(user) {
  if (document.querySelector('.app-foot')) return;
  const foot = document.createElement('footer');
  foot.className = 'app-foot';
  foot.innerHTML = `<div class="container">
    <span>© ${new Date().getFullYear()} ${esc(t('brand.name'))} · ${esc(t('land.footer'))}</span>
    <span class="row" style="gap:16px"><a href="index.html">${esc(t('foot.about'))}</a><a href="${homeFor(user)}">${esc(t('foot.home'))}</a><a href="account.html">${esc(t('nav.account'))}</a></span>
  </div>`;
  document.body.appendChild(foot);
}

// ---------- Notifications (the bell next to the account button, with a red count of new ones) ----------

let notifItems = [];
const AGO = typeof Intl.RelativeTimeFormat === 'function' ? new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }) : null;
function ago(value) {
  if (!value || !AGO) return fmt.dateTime(value);
  const mins = Math.round((new Date(value) - Date.now()) / 60000);
  if (Math.abs(mins) < 60) return AGO.format(mins, 'minute');
  if (Math.abs(mins) < 1440) return AGO.format(Math.round(mins / 60), 'hour');
  return AGO.format(Math.round(mins / 1440), 'day');
}

/** The sentence for a notification, in the reader's language (only the facts come from the server). */
export function notifText(n) {
  const title = isolate(n.ideaTitle || '');
  if (n.type === 'IDEA_ADOPTED') return t(n.detail === 'PARTNERSHIP' ? 'notif.adoptedPartner' : 'notif.adopted', { title });
  if (n.type === 'VOTED_IDEA_ADOPTED') return t('notif.votedAdopted', { title });
  if (n.type === 'VOTE_TARGET_REACHED') return t('notif.target', { title });
  if (n.type === 'PROJECT_STAGE') return t('notif.stage', { title, stage: stageLabel(n.detail) });
  if (n.type === 'CONTRACT_TO_SIGN') return t('notif.signContract', { title });
  return t('notif.generic', { title });
}

function setBadge(unread) {
  const badge = $('.notif-badge');
  if (!badge) return;
  badge.hidden = !unread;
  badge.textContent = unread > 9 ? '9+' : String(unread || '');
  $('.bell-btn')?.setAttribute('aria-label', unread ? t('notif.titleUnread', { n: unread }) : t('notif.title'));
}

function renderNotifications() {
  const list = $('#notif-list');
  if (!list) return;
  list.innerHTML = notifItems.length
    ? notifItems.map((n) => `<li><a class="notif-item${n.read ? '' : ' unread'}" href="${esc(n.link || 'projects.html')}">
        <span>${esc(notifText(n))}</span><small>${esc(ago(n.createdAt))}</small></a></li>`).join('')
    : `<li class="notif-empty">${esc(t('notif.empty'))}</li>`;
}

async function loadNotifications() {
  try {
    const res = await api.get('notifications');
    notifItems = res.items || [];
    setBadge(res.unread || 0);
    renderNotifications();
  } catch { /* the header works without them */ }
}

// Opening the menu shows the list and marks everything as read
async function openNotifications() {
  await loadNotifications();
  const unread = notifItems.filter((n) => !n.read).length;
  $('#notif-count').textContent = unread ? t('notif.new', { n: unread }) : '';
  if (unread) {
    try { await api.put('notifications/read-all'); setBadge(0); } catch { /* try again next time */ }
  }
}

function watchNotifications() {
  loadNotifications();
  // A light check every minute while the tab is open
  setInterval(async () => {
    if (document.hidden) return;
    try { setBadge((await api.get('notifications/unread-count')).unread || 0); } catch { /* ignore */ }
  }, 60000);
}

export async function logout() {
  try { await api.post('auth/logout'); } catch { /* token may already be gone */ }
  session.clear();
  location.href = 'login.html';
}

/** Translates the page's static text and wires the language buttons. Runs once per page. */
let pageReady = false;
export function initPage() {
  if (pageReady) return;
  pageReady = true;
  applyI18n(document);
  wireLangButtons(document);
}

// ---------- Misc ----------

export function params() {
  return new URLSearchParams(location.search);
}

/** Fades sections in as they scroll into view (respects reduced motion through CSS). */
export function reveal(root = document) {
  const items = $$('.reveal', root);
  if (!('IntersectionObserver' in window)) { items.forEach((el) => el.classList.add('in')); return; }
  const io = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) { entry.target.classList.add('in'); io.unobserve(entry.target); }
    });
  }, { threshold: 0.12 });
  items.forEach((el) => io.observe(el));
}

export function emptyState(title, text, action = '') {
  return `<div class="empty">${icon.map}<h3>${esc(title)}</h3><p>${esc(text)}</p>${action}</div>`;
}

export function skeletonCards(n = 3, height = 150) {
  return Array.from({ length: n }, () => `<div class="skeleton" style="height:${height}px;border-radius:22px"></div>`).join('');
}

export function statusBadge(status) {
  const cls = { PUBLISHED: '', COMPLETED: 'badge-solid', ADOPTED: 'badge-solid', OPENED: 'badge-solid', REJECTED: 'badge-red', SCREENING: 'badge-amber' }[status] ?? 'badge-sand';
  return `<span class="badge ${cls}">${esc(label('status', status))}</span>`;
}

/**
 * Wraps a name in Unicode isolation marks so an English name inside Arabic text (or the other way round)
 * keeps its own word order. Plain text, so it still goes through esc().
 */
const FSI = String.fromCharCode(0x2068); // first strong isolate
const PDI = String.fromCharCode(0x2069); // pop directional isolate
export const isolate = (text) => (text ? FSI + text + PDI : '');

/** An idea's name: its title, or "Pharmacy in Al Yasmin" for a confidential opportunity (investors before unlocking). */
export function ideaName(idea) {
  return idea.title || t('teaser.headline', { cat: categoryLabel(idea.category), hood: isolate(idea.neighborhoodName) });
}

const LIFE = ['PUBLISHED', 'COMPLETED', 'ADOPTED', 'OPENED'];

/**
 * Where an idea is in its life: voting → target reached → adopted (with the project stage) → opened.
 * compact = small dots for map popups; otherwise labelled steps.
 */
export function lifeTrack(idea, { compact = false } = {}) {
  const at = LIFE.indexOf(idea.status);
  if (at < 0) return '';
  const steps = LIFE.map((s, i) => {
    const state = i < at ? 'done' : i === at ? 'now' : '';
    let sub = '';
    if (s === 'ADOPTED' && idea.stage && idea.status === 'ADOPTED') {
      sub = `<small>${esc(stageLabel(idea.stage))}${idea.stageCount ? ` · ${idea.stageIndex}/${idea.stageCount - 1}` : ''}</small>`;
    }
    return `<li class="${state}"><i aria-hidden="true"></i><span>${esc(t(`life.${s}`))}</span>${sub}</li>`;
  }).join('');
  return `<ol class="life${compact ? ' compact' : ''}" aria-label="${esc(t('life.label'))}">${steps}</ol>`;
}

export function progressBar(percent, text) {
  const p = Math.max(0, Math.min(100, Number(percent) || 0));
  return `<div class="progress${p >= 100 ? ' done' : ''}" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${p}" aria-label="${esc(text || t('common.progress'))}"><span data-w="${p}"></span></div>`;
}

/** Animates progress bars from 0 after they are in the DOM. */
export function animateBars(root = document) {
  requestAnimationFrame(() => requestAnimationFrame(() => {
    $$('.progress > span[data-w]', root).forEach((bar) => { bar.style.width = bar.dataset.w + '%'; });
  }));
}
