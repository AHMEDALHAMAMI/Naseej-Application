// Email sign-up in steps: account type → your details → your home (resident) or your company (investor)
// → verify email (verify.html). One auth/register call at the end of step 3.
// A resident's home is a pin they place (the device location only helps place it); it decides where they vote.
import { api, ApiError, session } from '../api.js';
import { $, $$, icon, homeFor, params, withLoading, applyFieldErrors, errorMessage, toast, initPage, t } from '../ui.js';
import { langButton, lang } from '../i18n.js';
import { stepper } from '../components.js';
import { addressPicker } from '../address-picker.js';
import { setupInvestorFields, checkInvestor, normalizePhone, showFieldError, clearFieldErrors, wirePasswordToggle } from '../profile-fields.js';

$('#lang-slot').innerHTML = langButton();
initPage();
$$('.g-icon').forEach((el) => { el.innerHTML = icon.google; });
if (session.isLoggedIn() && session.user()) location.replace(homeFor(session.user()));

const form = $('#register-form');
let step = 0;
let picker = null;
let investor = null;
if (params().get('role') === 'INVESTOR') form.querySelector('input[value="INVESTOR"]').checked = true;
wirePasswordToggle($('#password'));

const role = () => form.querySelector('input[name="role"]:checked').value;
const labels = () => [t('signup.stepType'), t('signup.stepDetails'), role() === 'RESIDENT' ? t('signup.stepHome') : t('signup.stepCompany'), t('signup.stepVerify')];

function show(next, { focus = true } = {}) {
  step = next;
  $('#stepper').innerHTML = stepper(labels(), step);
  $$('[data-step]', form).forEach((s) => {
    const forRole = s.dataset.role;
    s.hidden = Number(s.dataset.step) !== step || (forRole && forRole !== role());
  });
  $('[data-final]', form).hidden = step !== 2;
  // Maps and sliders are created when their step first shows (a hidden map can't measure itself)
  if (step === 2 && role() === 'RESIDENT' && !picker) picker = addressPicker($('#home-picker'), { labelPlaceholder: t('addr.labelPh') });
  if (step === 2 && role() === 'INVESTOR' && !investor) investor = setupInvestorFields(form);
  if (step === 2 && picker) setTimeout(() => picker.map.invalidateSize(), 50);
  if (focus) form.querySelector(`[data-step="${step}"]:not([hidden]) h1`)?.focus();
}

// Each step is a history entry, so the browser's back button goes one step back
function go(next) {
  history.pushState({ step: next }, '', `#step-${next + 1}`);
  show(next);
  window.scrollTo({ top: 0 });
}
window.addEventListener('popstate', (e) => show(e.state?.step ?? 0));
history.replaceState({ step: 0 }, '', location.pathname + location.search);

function checkDetails() {
  clearFieldErrors(form);
  const pw = form.password.value;
  const problem = form.fullName.value.trim().length < 3 ? ['fullName', 'signup.nameInvalid']
    : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.value.trim()) ? ['email', 'signup.emailInvalid']
      : pw.length < 8 || !/[A-Za-z]/.test(pw) || !/\d/.test(pw) ? ['password', 'signup.passwordInvalid']
        : normalizePhone(form.phone.value) === false ? ['phone', 'signup.phoneInvalid'] : null;
  if (problem) showFieldError(form, problem[0], t(problem[1]));
  return !problem;
}

// The third step's name follows the account type
$$('input[name="role"]', form).forEach((r) => r.addEventListener('change', () => { $('#stepper').innerHTML = stepper(labels(), step); }));

$$('[data-next]', form).forEach((b) => b.addEventListener('click', () => {
  if (step === 1 && !checkDetails()) return;
  go(step + 1);
}));
$$('[data-back]', form).forEach((b) => b.addEventListener('click', () => history.back()));

// Server field errors go back to the step that has the field
const DETAILS_FIELDS = ['fullName', 'email', 'password', 'phone'];
function showServerError(err) {
  const fields = err instanceof ApiError && err.fieldErrors ? Object.keys(err.fieldErrors) : [];
  const onDetails = fields.some((f) => DETAILS_FIELDS.includes(f)) || /email|phone/i.test(err.message || '');
  if (onDetails) {
    show(1);
    if (!applyFieldErrors(form, err)) {
      const field = /phone/i.test(err.message) ? 'phone' : 'email';
      showFieldError(form, field, errorMessage(err));
    }
    return;
  }
  if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  if (step !== 2) return;
  clearFieldErrors(form);
  const body = {
    fullName: form.fullName.value.trim(),
    email: form.email.value.trim(),
    password: form.password.value,
    phone: normalizePhone(form.phone.value) || null,
    role: role(),
    preferredLanguage: lang,
  };
  if (role() === 'RESIDENT') {
    if (!picker.validate()) return;
    body.address = picker.values();
  } else {
    const inv = investor.values();
    const problem = checkInvestor(inv);
    if (problem) { showFieldError(form, problem[0], problem[1]); return; }
    Object.assign(body, inv);
  }
  if (!$('#terms').checked) { showFieldError(form, 'terms', t('form.acceptTerms')); return; }

  withLoading(form.querySelector('[type="submit"]'), async () => {
    try {
      await api.post('auth/register', body, { auth: false });
      try { sessionStorage.setItem('naseej.signupRole', body.role); } catch { /* only used for the step label */ }
      location.href = `verify.html?email=${encodeURIComponent(body.email)}&from=signup`;
    } catch (err) {
      showServerError(err);
    }
  });
});

show(0, { focus: false });
