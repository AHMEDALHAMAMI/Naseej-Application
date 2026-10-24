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
export const stageLabel = (value) => label('stage', value);

// ---------- Icons ----------

export const icon = {
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

export function modal({ title, body, confirmText = t('common.confirm'), cancelText = t('common.cancel'), danger = false, onConfirm }) {
  return new Promise((resolve) => {
    const back = document.createElement('div');
    back.className = 'modal-backdrop';
    back.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <h2 id="modal-title">${esc(title)}</h2>
        <div class="stack" style="margin-top:8px">${body}</div>
        <div class="row" style="justify-content:flex-end;margin-top:22px">
          <button type="button" class="btn btn-secondary" data-act="cancel">${esc(cancelText)}</button>
          <button type="button" class="btn ${danger ? 'btn-danger' : ''}" data-act="ok">${esc(confirmText)}</button>
        </div>
      </div>`;
    const previous = document.activeElement;
    const close = (value) => { back.remove(); document.removeEventListener('keydown', onKey); previous?.focus?.(); resolve(value); };
    const onKey = (e) => { if (e.key === 'Escape') close(false); };
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
export async function guard(roles) {
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
  renderHeader(user);
  return user;
}

const NAV = {
  RESIDENT: [['resident.html', 'nav.around'], ['propose.html', 'nav.propose'], ['projects.html', 'nav.myIdeas']],
  INVESTOR: [['investor.html', 'nav.opportunities'], ['propose.html', 'nav.propose'], ['projects.html', 'nav.myProjects']],
  ADMIN: [['admin.html', 'nav.dashboard'], ['admin.html#investors', 'nav.verification'], ['admin.html#neighborhoods', 'nav.neighborhoods']],
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
      <div class="account">
        <button type="button" class="account-btn" aria-haspopup="menu" aria-expanded="false">
          <span class="avatar" aria-hidden="true">${esc(initials)}</span>
          <span class="who"><strong>${esc(user.fullName)}</strong><span>${esc(roleText)}</span></span>
        </button>
        <div class="menu" role="menu">
          <div class="small muted" style="padding:8px 12px">${esc(user.email)}</div>
          <button type="button" role="menuitem" data-act="logout">${esc(t('nav.logout'))}</button>
        </div>
      </div>
    </div>`;
  const skip = document.querySelector('a.sr-only');
  if (skip) skip.after(header); else document.body.prepend(header);
  const btn = header.querySelector('.account-btn');
  const menu = header.querySelector('.menu');
  const toggle = (open) => { menu.classList.toggle('open', open); btn.setAttribute('aria-expanded', String(open)); };
  btn.onclick = (e) => { e.stopPropagation(); toggle(!menu.classList.contains('open')); };
  document.addEventListener('click', () => toggle(false));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') toggle(false); });
  header.querySelector('[data-act="logout"]').onclick = logout;
  wireLangButtons(header);
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
