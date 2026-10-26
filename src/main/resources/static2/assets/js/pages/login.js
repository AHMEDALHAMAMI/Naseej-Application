// Log in with email/password or Google; forgot password with an email code.
import { api, session, ApiError } from '../api.js';
import { $, esc, icon, homeFor, params, toast, withLoading, applyFieldErrors, errorMessage, modal, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';

$('#lang-slot').innerHTML = langButton();
initPage();
document.querySelectorAll('.g-icon').forEach((el) => { el.innerHTML = icon.google; });

const form = $('#login-form');
const next = params().get('next');

if (params().get('error') === 'google') {
  $('#notice').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(t('login.googleFailed'))}</span></div>`;
}
if (params().get('reset') === '1') {
  $('#notice').innerHTML = `<div class="notice notice-info">${icon.check}<span>${esc(t('login.resetDone'))}</span></div>`;
}

// Already logged in: go straight in
if (session.isLoggedIn() && session.user()) location.replace(next || homeFor(session.user()));

form.addEventListener('submit', (e) => {
  e.preventDefault();
  const email = form.email.value.trim();
  const password = form.password.value;
  if (!email || !password) {
    toast(t('login.missing'), 'error');
    return;
  }
  withLoading(form.querySelector('[type="submit"]'), async () => {
    try {
      const auth = await api.post('auth/login', { email, password }, { auth: false });
      session.save(auth);
      if (auth.roleSelectionRequired) return location.replace('choose-role.html');
      location.replace(safeNext() || homeFor(auth.user));
    } catch (err) {
      // Not verified yet: the server just sent a new code
      if (err instanceof ApiError && err.status === 403 && /not verified/i.test(err.message)) {
        location.href = `verify.html?email=${encodeURIComponent(email)}`;
        return;
      }
      if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
    }
  });
});

// Only same-site page names are allowed as "next"
function safeNext() {
  return next && /^[a-z-]+\.html(\?.*)?$/i.test(next) ? next : null;
}

// ---------- Forgot password ----------
$('#forgot').addEventListener('click', async () => {
  const email = form.email.value.trim();
  const sent = await modal({
    title: t('reset.title'),
    body: `<p class="muted">${esc(t('reset.text'))}</p>
      <div class="field"><label for="reset-email">${esc(t('form.email'))}</label><input class="input" id="reset-email" type="email" value="${esc(email)}" autocomplete="email" dir="ltr"></div>`,
    confirmText: t('reset.send'),
    onConfirm: async (box) => {
      const value = box.querySelector('#reset-email').value.trim();
      if (!value) throw new Error(t('reset.enterEmail'));
      await api.post('auth/send-otp', { email: value, purpose: 'RESET_PASSWORD' }, { auth: false });
      return value;
    },
  });
  if (!sent) return;
  await modal({
    title: t('reset.newTitle'),
    body: `<p class="muted">${esc(t('reset.sentTo', { email: sent }))}</p>
      <div class="field"><label for="reset-code">${esc(t('reset.code'))}</label><input class="input" id="reset-code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" dir="ltr"></div>
      <div class="field"><label for="reset-pass">${esc(t('reset.newPassword'))}</label><input class="input" id="reset-pass" type="password" autocomplete="new-password" dir="ltr"><span class="hint">${esc(t('form.passwordHint'))}</span></div>`,
    confirmText: t('reset.change'),
    onConfirm: async (box) => {
      await api.post('auth/reset-password', {
        email: sent,
        code: box.querySelector('#reset-code').value.trim(),
        newPassword: box.querySelector('#reset-pass').value,
      }, { auth: false });
      location.replace('login.html?reset=1');
    },
  });
});
