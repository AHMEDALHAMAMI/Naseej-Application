// Google sign-up: step 1 choose the membership, step 2 the role's own form.
// Investors only confirm here; their preferences come next in onboarding.html and the CR at the first unlock.
import { api, session } from '../api.js';
import { $, homeFor, toast, withLoading, applyFieldErrors, errorMessage, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';
import { setupLocationCard, showFieldError } from '../profile-fields.js';

$('#lang-slot').innerHTML = langButton();
initPage();
if (!session.isLoggedIn()) location.replace('login.html');

const form = $('#role-form');
let role = null;
let location$ = null;

// Only PENDING users belong here
api.get('auth/me').then((user) => {
  session.setUser(user);
  if (user.role !== 'PENDING') location.replace(homeFor(user));
}).catch(() => {});

document.querySelectorAll('[data-pick]').forEach((card) => card.addEventListener('click', () => {
  role = card.dataset.pick;
  $('#step-1').classList.add('hidden');
  $('#step-2').classList.remove('hidden');
  $('#step2-title').textContent = role === 'RESIDENT' ? t('choose.residentTitle') : t('choose.investorTitle');
  $('#resident-fields').classList.toggle('hidden', role !== 'RESIDENT');
  $('#investor-fields').classList.toggle('hidden', role !== 'INVESTOR');
  if (role === 'RESIDENT' && !location$) location$ = setupLocationCard(form);
}));

$('#back').addEventListener('click', () => {
  $('#step-2').classList.add('hidden');
  $('#step-1').classList.remove('hidden');
});

form.addEventListener('submit', (e) => {
  e.preventDefault();
  applyFieldErrors(form, null);
  if (!$('#terms').checked) {
    toast(t('form.acceptTerms'), 'error');
    return;
  }
  const body = { role, phone: form.phone.value.trim() || null };
  if (role === 'RESIDENT') {
    Object.assign(body, location$.values());
    if (!body.neighborhoodId && body.latitude === null) {
      showFieldError(form, 'neighborhoodId', t('form.chooseYourHood'));
      return;
    }
  }
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
