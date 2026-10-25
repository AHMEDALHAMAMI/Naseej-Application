// Email sign-up. Residents add their neighborhood here; investors only create the account:
// their budget, fields and areas come from the onboarding questions, and the CR from the first unlock.
import { api, session } from '../api.js';
import { $, $$, icon, homeFor, params, toast, withLoading, applyFieldErrors, errorMessage, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';
import { setupLocationCard, showFieldError } from '../profile-fields.js';

$('#lang-slot').innerHTML = langButton();
initPage();
document.querySelectorAll('.g-icon').forEach((el) => { el.innerHTML = icon.google; });
if (session.isLoggedIn() && session.user()) location.replace(homeFor(session.user()));

const form = $('#register-form');
let location$ = null; // created the first time the resident form shows, so investors aren't asked for a location
let role = params().get('role') === 'INVESTOR' ? 'INVESTOR' : 'RESIDENT';

function setRole(next) {
  role = next;
  const investor = role === 'INVESTOR';
  form.querySelectorAll('[data-role]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.role === role)));
  $('#resident-fields').classList.toggle('hidden', investor);
  $('#investor-fields').classList.toggle('hidden', !investor);
  $('#role-hint').textContent = investor ? '' : t('register.residentHint');
  $('#role-hint').classList.toggle('hidden', investor);
  // The side panel speaks to the person signing up
  $('#aside-title').innerHTML = t(investor ? 'register.asideTitleInv' : 'register.asideTitle');
  $('#aside-text').textContent = t(investor ? 'register.asideTextInv' : 'register.asideText');
  if (!investor && !location$) location$ = setupLocationCard(form);
}
form.querySelectorAll('[data-role]').forEach((b) => b.addEventListener('click', () => setRole(b.dataset.role)));
setRole(role);

// ---------- Password: show/hide and live rules ----------
const password = form.password;
const toggle = $('#pw-toggle');
const eye = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
const eyeOff = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18M10.6 5.1A10.8 10.8 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.2M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7c1.7 0 3.2-.5 4.5-1.2"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';
toggle.innerHTML = eye;
toggle.addEventListener('click', () => {
  const show = password.type === 'password';
  password.type = show ? 'text' : 'password';
  toggle.setAttribute('aria-pressed', String(show));
  toggle.setAttribute('aria-label', t(show ? 'pw.hide' : 'pw.show'));
  toggle.innerHTML = show ? eyeOff : eye;
});

const RULES = {
  len: (v) => v.length >= 8,
  letter: (v) => /[A-Za-z]/.test(v), // the server asks for Latin letters
  digit: (v) => /\d/.test(v),
};
const checkPassword = () => {
  const v = password.value;
  $$('#pw-rules [data-rule]').forEach((li) => li.classList.toggle('ok', RULES[li.dataset.rule](v)));
  return Object.values(RULES).every((ok) => ok(v));
};
password.addEventListener('input', checkPassword);

// ---------- Submit ----------
form.addEventListener('submit', (e) => {
  e.preventDefault();
  applyFieldErrors(form, null);
  if (!checkPassword()) {
    showFieldError(form, 'password', t('form.passwordHint'));
    return;
  }
  if (!$('#terms').checked) {
    toast(t('form.acceptTerms'), 'error');
    return;
  }
  const body = {
    fullName: form.fullName.value.trim(),
    email: form.email.value.trim(),
    password: password.value,
    phone: form.phone.value.trim() || null,
    role,
  };
  if (role === 'RESIDENT') {
    const loc = location$.values();
    Object.assign(body, loc);
    if (!body.neighborhoodId && body.latitude === null) {
      showFieldError(form, 'neighborhoodId', t('form.chooseYourHood'));
      return;
    }
  }

  withLoading(form.querySelector('[type="submit"]'), async () => {
    try {
      await api.post('auth/register', body, { auth: false });
      location.href = `verify.html?email=${encodeURIComponent(body.email)}`;
    } catch (err) {
      if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
    }
  });
});
