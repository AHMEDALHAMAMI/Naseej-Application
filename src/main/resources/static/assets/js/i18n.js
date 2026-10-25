// Arabic / English for the whole website.
// - t('key', { name: 'x' }) returns the text in the current language ({name} placeholders are filled in)
// - Static HTML uses data-i18n="key" (text), data-i18n-html="key" (allows <em>), data-i18n-attr="placeholder:key;aria-label:key"
// - The choice is kept in localStorage; each page sets <html lang dir> in its <head> before anything renders.
import { STRINGS, SERVER_MESSAGES } from './strings.js';

const KEY = 'naseej.lang';

function detect() {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === 'ar' || saved === 'en') return saved;
  } catch { /* storage blocked */ }
  // Arabic is the default for everyone; English is one tap away and remembered once chosen
  return 'ar';
}

export const lang = detect();
export const isAr = lang === 'ar';
export const dir = isAr ? 'rtl' : 'ltr';
// Latin digits and the Gregorian calendar in both languages, so numbers and dates match what people type
export const locale = isAr ? 'ar-SA-u-nu-latn-ca-gregory' : 'en-GB';

document.documentElement.lang = lang;
document.documentElement.dir = dir;

export function t(key, vars) {
  const entry = STRINGS[key];
  let text = entry ? (isAr ? entry[1] : entry[0]) : key;
  if (!entry && typeof console !== 'undefined') console.warn('[i18n] missing key:', key);
  if (vars) {
    text = text.replace(/\{(\w+)\}/g, (m, name) => (vars[name] !== undefined && vars[name] !== null ? String(vars[name]) : m));
  }
  return text;
}

/**
 * Plural text: tn(3, 'count.ideas') picks 'count.ideas.<form>' using the language's plural rules
 * (Arabic has zero, one, two, few, many and other), falling back to '.other'.
 */
const plurals = typeof Intl !== 'undefined' && Intl.PluralRules ? new Intl.PluralRules(lang) : null;
export function tn(count, key, vars = {}) {
  const n = Number(count);
  const form = plurals ? plurals.select(n) : (n === 1 ? 'one' : 'other');
  const exact = `${key}.${form}`;
  const value = new Intl.NumberFormat(locale).format(n);
  return t(STRINGS[exact] ? exact : `${key}.other`, { n: value, ...vars });
}

/** Messages that come from the server are in English; known ones are shown in Arabic too. */
export function serverText(message) {
  if (!isAr || !message) return message;
  if (SERVER_MESSAGES[message]) return SERVER_MESSAGES[message];
  for (const [pattern, ar] of Object.entries(SERVER_MESSAGES)) {
    if (pattern.startsWith('^')) {
      const m = message.match(new RegExp(pattern));
      if (m) return ar.replace(/\$(\d)/g, (_, i) => m[Number(i)] ?? '');
    }
  }
  return message;
}

export function setLang(next) {
  try { localStorage.setItem(KEY, next); } catch { /* ignore */ }
  location.reload();
}

/** Fills data-i18n attributes inside root. */
export function applyI18n(root = document) {
  root.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n); });
  root.querySelectorAll('[data-i18n-html]').forEach((el) => { el.innerHTML = t(el.dataset.i18nHtml); });
  root.querySelectorAll('[data-i18n-attr]').forEach((el) => {
    el.dataset.i18nAttr.split(';').forEach((pair) => {
      const [attr, key] = pair.split(':').map((s) => s.trim());
      if (attr && key) el.setAttribute(attr, t(key));
    });
  });
  const title = document.querySelector('title[data-i18n-title]');
  if (title) document.title = t(title.dataset.i18nTitle);
}

/** The button that switches to the other language. */
export function langButton(extraClass = 'btn-secondary') {
  const other = isAr ? 'en' : 'ar';
  return `<button type="button" class="btn ${extraClass} btn-sm lang-btn" data-lang="${other}" lang="${other}" aria-label="${t('lang.switch')}">${isAr ? 'English' : 'العربية'}</button>`;
}

export function wireLangButtons(root = document) {
  root.querySelectorAll('[data-lang]').forEach((b) => b.addEventListener('click', () => setLang(b.dataset.lang)));
}
