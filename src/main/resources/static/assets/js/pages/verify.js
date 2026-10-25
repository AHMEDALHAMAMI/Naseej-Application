// Email code after sign-up (or after logging in with an unverified email).
import { api, session } from '../api.js';
import { $, esc, homeFor, params, toast, withLoading, errorMessage, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';

$('#lang-slot').innerHTML = langButton();
initPage();

const email = params().get('email') || '';
if (!email) location.replace('register.html');
$('#sent-to').innerHTML = t('verify.sentTo', { email: `<strong dir="ltr">${esc(email)}</strong>` });

// Six boxes that behave like one field (typing, paste, backspace)
const box = $('#otp');
box.innerHTML = Array.from({ length: 6 }, (_, i) =>
  `<input inputmode="numeric" maxlength="1" autocomplete="${i === 0 ? 'one-time-code' : 'off'}" aria-label="${esc(t('verify.digit', { n: i + 1 }))}">`).join('');
const inputs = [...box.querySelectorAll('input')];
inputs[0].focus();
inputs.forEach((input, i) => {
  input.addEventListener('input', () => {
    input.value = input.value.replace(/\D/g, '').slice(-1);
    if (input.value && i < 5) inputs[i + 1].focus();
    if (code().length === 6) $('#otp-form').requestSubmit();
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Backspace' && !input.value && i > 0) inputs[i - 1].focus();
  });
  input.addEventListener('paste', (e) => {
    const digits = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, 6);
    if (!digits) return;
    e.preventDefault();
    digits.split('').forEach((d, j) => { inputs[j].value = d; });
    inputs[Math.min(digits.length, 5)].focus();
    if (digits.length === 6) $('#otp-form').requestSubmit();
  });
});
const code = () => inputs.map((x) => x.value).join('');

let busy = false;
$('#otp-form').addEventListener('submit', (e) => {
  e.preventDefault();
  if (busy) return;
  if (code().length !== 6) {
    toast(t('verify.enterCode'), 'error');
    return;
  }
  busy = true;
  withLoading($('#otp-form [type="submit"]'), async () => {
    try {
      const auth = await api.post('auth/verify-otp', { email, code: code(), purpose: 'REGISTER' }, { auth: false });
      session.save(auth);
      toast(t('verify.done'), 'success');
      // New investors answer the onboarding questions first
      location.replace(auth.user?.role === 'INVESTOR' ? 'onboarding.html' : homeFor(auth.user));
    } catch (err) {
      toast(errorMessage(err), 'error');
      inputs.forEach((x) => { x.value = ''; });
      inputs[0].focus();
    } finally {
      busy = false;
    }
  });
});

// Resend with a 60-second cooldown
const resend = $('#resend');
let timer = null;
function cooldown(seconds) {
  clearInterval(timer);
  resend.disabled = true;
  let left = seconds;
  resend.textContent = t('verify.resendIn', { n: left });
  timer = setInterval(() => {
    left -= 1;
    resend.textContent = left > 0 ? t('verify.resendIn', { n: left }) : t('verify.resend');
    if (left <= 0) { clearInterval(timer); resend.disabled = false; }
  }, 1000);
}
cooldown(60);
resend.addEventListener('click', () => withLoading(resend, async () => {
  try {
    await api.post('auth/send-otp', { email, purpose: 'REGISTER' }, { auth: false });
    toast(t('verify.newCode'), 'success');
  } catch (err) {
    toast(errorMessage(err), 'error');
  }
}).then(() => cooldown(60)));
