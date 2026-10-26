// Investor account (loaded by account.js).
// My account: everything about the account in one frame. Details, investment preferences,
// CR verification, alerts and language. Each card saves on its own.
import { api, session } from '../api.js';
import { $, esc, guard, toast, withLoading, errorMessage, applyFieldErrors, icon, logout, t } from '../ui.js';
import { isAr, setLang } from '../i18n.js';
import { budgetPicker, fieldsPicker, areasPicker } from '../investor-prefs.js';

let user = await guard(['INVESTOR']);
const app = $('#app');

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t('acct.eyebrow'))}</span>
      <h1>${t('acct.title')}</h1>
    </div>
  </div>
  <div class="account-grid">
    <section class="card stack" id="verify" aria-labelledby="verify-h" style="gap:14px"></section>

    <section class="card stack" id="prefs" aria-labelledby="prefs-h" style="gap:18px">
      <div class="stack" style="gap:4px"><h2 id="prefs-h">${esc(t('acct.prefs'))}</h2><p class="small muted">${esc(t('acct.prefsText'))}</p></div>
      <div class="stack" style="gap:10px"><span class="label">${esc(t('pref.budget'))}</span><div id="p-budget" class="stack" style="gap:10px"></div></div>
      <div class="stack" style="gap:10px"><span class="label">${esc(t('pref.fields'))}</span><div id="p-fields" class="stack" style="gap:10px"></div></div>
      <div class="stack" style="gap:10px" id="p-areas"></div>
      <button type="button" class="btn" id="save-prefs" style="align-self:flex-start" data-loading="${esc(t('commonInv.saving'))}">${esc(t('acct.savePrefs'))}</button>
    </section>

    <section class="card stack" aria-labelledby="profile-h" style="gap:14px">
      <h2 id="profile-h">${esc(t('acct.profile'))}</h2>
      <form id="profile-form" class="stack" style="gap:14px" novalidate>
        <div class="field"><label for="fullName">${esc(t('form.fullName'))}</label>
          <input class="input" id="fullName" name="fullName" autocomplete="name" minlength="3" maxlength="100" value="${esc(user.fullName)}"><span class="error" aria-live="polite"></span></div>
        <div class="field"><label for="phone">${esc(t('form.mobile'))} <span class="muted">${esc(t('form.optional'))}</span></label>
          <input class="input" id="phone" name="phone" type="tel" inputmode="numeric" dir="ltr" placeholder="9665XXXXXXXX" value="${esc(user.phone || '')}"><span class="error" aria-live="polite"></span></div>
        <div class="field"><label for="email">${esc(t('formInv.email'))}</label>
          <input class="input" id="email" value="${esc(user.email)}" readonly dir="ltr"></div>
        <button class="btn btn-secondary" type="submit" style="align-self:flex-start" data-loading="${esc(t('commonInv.saving'))}">${esc(t('acct.saveProfile'))}</button>
      </form>
    </section>

    <section class="card stack" aria-labelledby="more-h" style="gap:16px">
      <h2 id="more-h" class="sr-only">${esc(t('acct.notifs'))}</h2>
      <div class="stack" style="gap:8px">
        <span class="label">${esc(t('acct.notifs'))}</span>
        <label class="switch"><input type="checkbox" id="whatsapp" ${user.whatsappOptIn ? 'checked' : ''}><span class="track" aria-hidden="true"></span><span>${esc(t('acct.whatsapp'))}</span></label>
      </div>
      <hr class="divider">
      <div class="stack" style="gap:8px">
        <span class="label" id="lang-label">${esc(t('acct.language'))}</span>
        <div class="segmented" role="group" aria-labelledby="lang-label">
          <button type="button" data-set-lang="ar" aria-pressed="${isAr}" lang="ar">العربية</button>
          <button type="button" data-set-lang="en" aria-pressed="${!isAr}" lang="en">English</button>
        </div>
      </div>
      <hr class="divider">
      <button type="button" class="btn btn-danger" id="logout" style="align-self:flex-start">${esc(t('nav.logout'))}</button>
    </section>
  </div>`;

// ---------- Verification (CR) ----------
function drawVerify() {
  const box = $('#verify');
  const status = user.investorStatus || 'PENDING';
  const hasCr = !!user.crNumber;
  let badge;
  let text;
  let form = '';
  if (status === 'VERIFIED') {
    badge = `<span class="badge badge-solid">${icon.check} ${esc(t('investorStatus.VERIFIED'))}</span>`;
    text = t('acct.verified');
  } else if (!hasCr) {
    badge = `<span class="badge badge-sand">${esc(t('acct.noCrBadge'))}</span>`;
    text = t('acct.noCr');
  } else if (status === 'REJECTED') {
    badge = `<span class="badge badge-red">${esc(t('investorStatus.REJECTED'))}</span>`;
    text = t('acct.rejected');
  } else {
    badge = `<span class="badge badge-amber">${esc(t('investorStatusInv.PENDING'))}</span>`;
    text = t('acct.pending');
  }
  if (status !== 'VERIFIED' && (!hasCr || status === 'REJECTED')) {
    form = `
      <form id="cr-form" class="cr-form" novalidate>
        <div class="field"><label for="crNumber">${esc(t('inv.cr'))}</label>
          <input class="input" id="crNumber" name="crNumber" inputmode="numeric" maxlength="10" dir="ltr" autocomplete="off" placeholder="${esc(t('gate.crPh'))}" value="${esc(user.crNumber || '')}">
          <span class="error" aria-live="polite"></span></div>
        <button class="btn" type="submit" data-loading="${esc(t('commonInv.saving'))}">${esc(t('acct.sendCr'))}</button>
      </form>`;
  }
  box.innerHTML = `
    <div class="row" style="justify-content:space-between"><h2 id="verify-h">${esc(t('acct.verify'))}</h2>${badge}</div>
    <p class="muted">${esc(text)}</p>
    ${hasCr ? `<span class="small muted">${esc(t('inv.cr'))}: <span class="num" dir="ltr">${esc(user.crNumber)}</span></span>` : ''}
    ${form}`;

  $('#cr-form')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.currentTarget;
    applyFieldErrors(f, null);
    const cr = f.crNumber.value.replace(/\D/g, '');
    if (!/^\d{10}$/.test(cr)) {
      f.crNumber.setAttribute('aria-invalid', 'true');
      f.querySelector('.error').textContent = t('inv.crInvalid');
      f.crNumber.focus();
      return;
    }
    withLoading(f.querySelector('[type="submit"]'), async () => {
      try {
        user = session.setUser(await api.put('auth/me', { crNumber: cr })).user;
        toast(t('acct.crSent'), 'success');
        drawVerify();
      } catch (err) {
        if (!applyFieldErrors(f, err)) toast(errorMessage(err), 'error');
      }
    });
  });
}
drawVerify();

// ---------- Preferences ----------
const budget$ = budgetPicker($('#p-budget'), user);
const fields$ = fieldsPicker($('#p-fields'), user);
const areas$ = areasPicker($('#p-areas'), user);

$('#save-prefs').addEventListener('click', (e) => {
  const budget = budget$.value();
  if (!budget) return;
  const body = { ...budget, ...fields$.value(), ...areas$.value() };
  // A preset clears the old custom range on the server; nothing extra to send
  withLoading(e.currentTarget, async () => {
    try {
      user = session.setUser(await api.put('auth/me', body)).user;
      toast(t('acct.saved'), 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  });
});
if (location.hash === '#prefs') $('#prefs').scrollIntoView({ block: 'start' });

// ---------- Details ----------
$('#profile-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = e.currentTarget;
  applyFieldErrors(f, null);
  const body = { fullName: f.fullName.value.trim() };
  const phone = f.phone.value.trim();
  if (phone) body.phone = phone;
  withLoading(f.querySelector('[type="submit"]'), async () => {
    try {
      user = session.setUser(await api.put('auth/me', body)).user;
      toast(t('acct.saved'), 'success');
    } catch (err) {
      if (!applyFieldErrors(f, err)) toast(errorMessage(err), 'error');
    }
  });
});

// ---------- Alerts, language, log out ----------
$('#whatsapp').addEventListener('change', async (e) => {
  const on = e.currentTarget.checked;
  if (on && !user.phone) {
    e.currentTarget.checked = false;
    toast(t('acct.whatsappNeedsPhone'), 'error');
    $('#phone').focus();
    return;
  }
  try {
    user = session.setUser(await api.put('auth/me', { whatsappOptIn: on })).user;
    toast(t('acct.saved'), 'success');
  } catch (err) {
    e.currentTarget.checked = !on;
    toast(errorMessage(err), 'error');
  }
});
document.querySelectorAll('[data-set-lang]').forEach((b) => b.addEventListener('click', () => {
  if (b.getAttribute('aria-pressed') !== 'true') setLang(b.dataset.setLang);
}));
$('#logout').addEventListener('click', logout);
