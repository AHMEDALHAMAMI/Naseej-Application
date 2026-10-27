// Google sign-up in steps: account type → your home (resident) or a last confirmation (investor, whose
// preferences come next in onboarding.html and the CR before the first unlock).
import { api, session } from '../api.js';
import { $, $$, homeFor, withLoading, applyFieldErrors, errorMessage, toast, initPage, t } from '../ui.js';
import { langButton, lang } from '../i18n.js';
import { stepper } from '../components.js';
import { addressPicker } from '../address-picker.js';
import { normalizePhone, requiredPhone, showFieldError, clearFieldErrors } from '../profile-fields.js';

$('#lang-slot').innerHTML = langButton();
initPage();
if (!session.isLoggedIn()) location.replace('login.html');

const form = $('#role-form');
let step = 0;
let picker = null;

// Only PENDING users belong here
api.get('auth/me').then((user) => {
  session.setUser(user);
  if (user.role !== 'PENDING') location.replace(homeFor(user));
}).catch(() => {});

const role = () => form.querySelector('input[name="role"]:checked').value;
const labels = () => [t('signup.stepType'), role() === 'RESIDENT' ? t('signup.stepHome') : t('choose.investorTitle')];

function show(next, { focus = true } = {}) {
  step = next;
  $('#stepper').innerHTML = stepper(labels(), step);
  $$('[data-step]', form).forEach((s) => {
    const forRole = s.dataset.role;
    s.hidden = Number(s.dataset.step) !== step || (forRole && forRole !== role());
  });
  $('[data-final]', form).hidden = step !== 1;
  // The mobile number is required for residents, optional for investors
  $('[data-phone-optional]', form).hidden = role() === 'RESIDENT';
  if (step === 1 && role() === 'RESIDENT' && !picker) picker = addressPicker($('#home-picker'));
  if (step === 1 && picker) setTimeout(() => picker.map.invalidateSize(), 50);
  if (focus) form.querySelector(`[data-step="${step}"]:not([hidden]) h1`)?.focus();
}

window.addEventListener('popstate', (e) => show(e.state?.step ?? 0));
history.replaceState({ step: 0 }, '', location.pathname);
$('[data-next]', form).addEventListener('click', () => {
  history.pushState({ step: 1 }, '', '#step-2');
  show(1);
  window.scrollTo({ top: 0 });
});
$('[data-back]', form).addEventListener('click', () => history.back());
$$('input[name="role"]', form).forEach((r) => r.addEventListener('change', () => { $('#stepper').innerHTML = stepper(labels(), step); }));

form.addEventListener('submit', (e) => {
  e.preventDefault();
  if (step !== 1) return;
  clearFieldErrors(form);
  let phone;
  if (role() === 'RESIDENT') {
    const check = requiredPhone(form.phone.value);
    if (check.error) { showFieldError(form, 'phone', t(check.error)); return; }
    phone = check.phone;
  } else {
    phone = normalizePhone(form.phone.value);
    if (phone === false) { showFieldError(form, 'phone', t('signup.phoneInvalid')); return; }
  }
  const body = { role: role(), phone, preferredLanguage: lang };
  if (role() === 'RESIDENT') {
    if (!picker.validate()) return;
    body.address = picker.values();
  }
  if (!$('#terms').checked) { showFieldError(form, 'terms', t('form.acceptTerms')); return; }
  withLoading(form.querySelector('[type="submit"]'), async () => {
    try {
      const auth = await api.put('auth/select-role', body);
      session.save(auth);
      // New investors answer the onboarding questions first
      location.replace(auth.user.role === 'INVESTOR' ? 'onboarding.html' : homeFor(auth.user));
    } catch (err) {
      if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
    }
  });
});

show(0, { focus: false });
