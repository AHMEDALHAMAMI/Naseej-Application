// Email sign-up: the role is chosen inside the form; the neighborhood comes from the location.
import { api, session } from '../api.js';
import { $, icon, homeFor, params, toast, withLoading, applyFieldErrors, errorMessage, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';
import { setupLocationCard, setupInvestorFields, checkInvestor, showFieldError } from '../profile-fields.js';

$('#lang-slot').innerHTML = langButton();
initPage();
document.querySelectorAll('.g-icon').forEach((el) => { el.innerHTML = icon.google; });
if (session.isLoggedIn() && session.user()) location.replace(homeFor(session.user()));

const form = $('#register-form');
const location$ = setupLocationCard(form);
const investor$ = setupInvestorFields(form);
let role = params().get('role') === 'INVESTOR' ? 'INVESTOR' : 'RESIDENT';

function setRole(next) {
  role = next;
  form.querySelectorAll('[data-role]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.role === role)));
  $('#resident-fields').classList.toggle('hidden', role !== 'RESIDENT');
  $('#investor-fields').classList.toggle('hidden', role !== 'INVESTOR');
  $('#role-hint').textContent = role === 'RESIDENT' ? t('register.residentHint') : t('register.investorHint');
}
form.querySelectorAll('[data-role]').forEach((b) => b.addEventListener('click', () => setRole(b.dataset.role)));
setRole(role);

form.addEventListener('submit', (e) => {
  e.preventDefault();
  applyFieldErrors(form, null);
  if (!$('#terms').checked) {
    toast(t('form.acceptTerms'), 'error');
    return;
  }
  const body = {
    fullName: form.fullName.value.trim(),
    email: form.email.value.trim(),
    password: form.password.value,
    phone: form.phone.value.trim() || null,
    role,
  };
  const loc = location$.values();
  body.latitude = loc.latitude;
  body.longitude = loc.longitude;
  if (role === 'RESIDENT') {
    body.neighborhoodId = loc.neighborhoodId;
    if (!body.neighborhoodId && body.latitude === null) {
      showFieldError(form, 'neighborhoodId', t('form.chooseYourHood'));
      return;
    }
  } else {
    const inv = investor$.values();
    const problem = checkInvestor(inv);
    if (problem) {
      showFieldError(form, problem[0], problem[1]);
      return;
    }
    Object.assign(body, inv);
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
