// My account: profile (name, phone, language, password), my addresses (residents: up to two homes that decide
// where they vote; investors: one optional office) and the investor profile (company, CR, budget range, fields).
// Addresses change only here. Before saving a change the server previews what it does (votes withdrawn,
// monthly change used), and the person confirms.
import { api, session } from '../api.js';
import { $, $$, esc, fmt, guard, icon, label, toast, modal, withLoading, applyFieldErrors, errorMessage, emptyState, t } from '../ui.js';
import { lang, setLang, serverText } from '../i18n.js';
import { wireTabs } from '../components.js';
import { createMap } from '../maps.js';
import { addressPicker } from '../address-picker.js';
import { setupInvestorFields, checkInvestor, normalizePhone, displayPhone, showFieldError, clearFieldErrors } from '../profile-fields.js';

const user = await guard(['RESIDENT', 'INVESTOR', 'ADMIN']);
const isResident = user.role === 'RESIDENT';
const isInvestor = user.role === 'INVESTOR';
const hasAddresses = isResident || isInvestor;
const app = $('#app');

const tab = (key, text) => `<button type="button" role="tab" id="tab-${key}" data-tab="${key}" aria-controls="panel-${key}">${esc(text)}</button>`;
app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px"><span class="eyebrow">${esc(label('role', user.role))}</span><h1>${esc(t('acc.title'))}</h1></div>
  </div>
  <div class="tabs" role="tablist" aria-label="${esc(t('acc.tabs'))}">
    ${tab('profile', t('acc.tabProfile'))}
    ${hasAddresses ? tab('addresses', t('acc.tabAddresses')) : ''}
    ${isInvestor ? tab('investor', t('acc.tabInvestor')) : ''}
  </div>
  <section role="tabpanel" id="panel-profile" aria-labelledby="tab-profile"></section>
  ${hasAddresses ? '<section role="tabpanel" id="panel-addresses" aria-labelledby="tab-addresses" hidden></section>' : ''}
  ${isInvestor ? '<section role="tabpanel" id="panel-investor" aria-labelledby="tab-investor" hidden></section>' : ''}`;

let addressesLoaded = false;
wireTabs(app, {
  onChange: (key) => {
    if (key === 'addresses' && !addressesLoaded) { addressesLoaded = true; loadAddresses(); }
  },
});

// ---------- Profile ----------

function renderProfile(u) {
  const panel = $('#panel-profile');
  const google = u.authProvider === 'GOOGLE';
  panel.innerHTML = `
    <div class="stack account-form" style="gap:24px">
      <form class="card stack" id="profile-form" novalidate>
        <div class="field"><label for="p-name">${esc(t('form.fullName'))}</label>
          <input class="input" id="p-name" name="fullName" autocomplete="name" maxlength="100" value="${esc(u.fullName)}"><span class="error" aria-live="polite"></span></div>
        <div class="field"><label for="p-email">${esc(t('form.email'))}</label>
          <input class="input" id="p-email" value="${esc(u.email)}" readonly dir="ltr"><span class="hint">${esc(t('acc.emailLocked'))}</span></div>
        <div class="field"><label for="p-phone"><span>${esc(t('form.mobile'))}</span> <span class="muted">${esc(t('form.optional'))}</span></label>
          <input class="input" id="p-phone" name="phone" type="tel" inputmode="numeric" dir="ltr" placeholder="${esc(t('signup.phonePh'))}" value="${esc(displayPhone(u.phone))}"><span class="error" aria-live="polite"></span></div>
        <div class="field"><label for="p-lang">${esc(t('acc.language'))}</label>
          <select class="select" id="p-lang" name="preferredLanguage">
            <option value="ar" lang="ar"${(u.preferredLanguage || lang) === 'ar' ? ' selected' : ''}>العربية</option>
            <option value="en" lang="en"${(u.preferredLanguage || lang) === 'en' ? ' selected' : ''}>English</option>
          </select><span class="hint">${esc(t('acc.languageHint'))}</span></div>
        <button class="btn" type="submit" style="align-self:flex-start" data-loading="${esc(t('common.saving'))}">${esc(t('acc.save'))}</button>
      </form>
      <section class="card stack" style="gap:10px">
        <h2>${esc(t('acc.password'))}</h2>
        ${google
          ? `<p class="muted">${esc(t('acc.googleOnly'))}</p>`
          : `<p class="muted">${esc(t('acc.passwordText'))}</p><button type="button" class="btn btn-secondary" id="change-pass" style="align-self:flex-start">${esc(t('acc.changePassword'))}</button>`}
      </section>
    </div>`;

  const form = $('#profile-form');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    clearFieldErrors(form);
    const phone = normalizePhone(form.phone.value);
    if (form.fullName.value.trim().length < 3) { showFieldError(form, 'fullName', t('signup.nameInvalid')); return; }
    if (phone === false) { showFieldError(form, 'phone', t('signup.phoneInvalid')); return; }
    const chosenLang = form.preferredLanguage.value;
    withLoading(form.querySelector('[type="submit"]'), async () => {
      try {
        const saved = await api.put('account/profile', { fullName: form.fullName.value.trim(), phone: phone || '', preferredLanguage: chosenLang });
        session.setUser({ ...session.user(), ...saved });
        toast(t('acc.saved'), 'success');
        if (chosenLang !== lang) setLang(chosenLang);
      } catch (err) {
        if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
      }
    });
  });
  $('#change-pass')?.addEventListener('click', () => changePassword(u.email));
}

// Same email-code flow as "forgot password" on the login page
async function changePassword(email) {
  const sent = await modal({
    title: t('acc.changePassword'),
    body: `<p class="muted">${esc(t('acc.passwordText'))}</p><p dir="ltr" style="text-align:start"><strong>${esc(email)}</strong></p>`,
    confirmText: t('reset.send'),
    onConfirm: () => api.post('auth/send-otp', { email, purpose: 'RESET_PASSWORD' }, { auth: false }).then(() => true),
  });
  if (!sent) return;
  await modal({
    title: t('reset.newTitle'),
    body: `<p class="muted">${esc(t('reset.sentTo', { email }))}</p>
      <div class="field"><label for="reset-code">${esc(t('reset.code'))}</label><input class="input" id="reset-code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" dir="ltr"></div>
      <div class="field"><label for="reset-pass">${esc(t('reset.newPassword'))}</label><input class="input" id="reset-pass" type="password" autocomplete="new-password" dir="ltr"><span class="hint">${esc(t('form.passwordHint'))}</span></div>`,
    confirmText: t('reset.change'),
    onConfirm: async (box) => {
      await api.post('auth/reset-password', { email, code: box.querySelector('#reset-code').value.trim(), newPassword: box.querySelector('#reset-pass').value }, { auth: false });
      toast(t('login.resetDone'), 'success');
      return true;
    },
  });
}

// ---------- Addresses ----------

let book = null;
const miniMaps = [];

async function loadAddresses() {
  const panel = $('#panel-addresses');
  panel.innerHTML = '<div class="loader-page" style="min-height:200px"><span class="spinner"></span></div>';
  try {
    book = await api.get('account/addresses');
  } catch (err) {
    panel.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  renderAddresses();
}

const addressName = (a, i) => a.label || (isResident ? t('addr.homeN', { n: i + 1 }) : t('addr.office'));
const nationalLine = (a) => [a.buildingNumber, a.street, a.district, a.postalCode].filter(Boolean).join(' · ');

function renderAddresses() {
  const panel = $('#panel-addresses');
  miniMaps.splice(0).forEach((m) => m.remove());
  const list = book.addresses;
  const toConfirm = list.find((a) => a.needsConfirmation);
  const canAdd = list.length < book.maxAddresses;
  panel.innerHTML = `
    <div class="stack" style="gap:20px">
      <p class="muted" style="max-width:640px">${esc(isResident ? t('acc.addrIntro', { km: book.votingRadiusKm }) : t('acc.addrInvestorIntro'))}</p>
      ${toConfirm ? `<div class="notice notice-warn">${icon.warn}<div class="stack" style="gap:8px"><strong>${esc(t('acc.confirmTitle'))}</strong><span>${esc(t('acc.confirmText'))}</span>
        <button type="button" class="btn btn-sm" style="align-self:flex-start" data-edit="${toConfirm.id}">${esc(t('acc.confirmBtn'))}</button></div></div>` : ''}
      ${list.length ? `<div class="addr-grid">${list.map((a, i) => `
        <article class="addr-card${a.primary ? ' is-primary' : ''}">
          <div class="row" style="justify-content:space-between;align-items:flex-start">
            <div class="stack" style="gap:2px"><h3>${esc(addressName(a, i))}</h3><span class="small muted"><bdi>${esc(`${a.neighborhoodName}, ${a.city}`)}</bdi></span></div>
            ${a.primary && list.length > 1 ? `<span class="badge">${esc(t('acc.primary'))}</span>` : ''}
          </div>
          <div class="addr-map" id="addr-map-${a.id}" role="img" aria-label="${esc(addressName(a, i))}"></div>
          ${nationalLine(a) ? `<span class="small muted">${esc(nationalLine(a))}</span>` : ''}
          <div class="addr-actions">
            <button type="button" class="btn btn-secondary btn-sm" data-edit="${a.id}">${esc(t('acc.edit'))}</button>
            ${!a.primary ? `<button type="button" class="btn btn-ghost btn-sm" data-primary="${a.id}">${esc(t('acc.makePrimary'))}</button>` : ''}
            ${list.length > 1 || !isResident ? `<button type="button" class="btn btn-danger btn-sm" data-delete="${a.id}">${esc(t('acc.delete'))}</button>` : ''}
          </div>
        </article>`).join('')}</div>`
        : emptyState(t('acc.noAddress'), isResident ? t('res.noAddressText') : t('acc.addrInvestorIntro'))}
      ${canAdd ? `<button type="button" class="btn${list.length ? ' btn-secondary' : ''}" id="add-address" style="align-self:flex-start">+ ${esc(list.length && isResident ? t('acc.addSecond') : t('acc.add'))}</button>` : ''}
      ${isResident ? `<section class="rules" aria-labelledby="rules-h">
        <h3 id="rules-h">${esc(t('acc.rulesTitle'))}</h3>
        <ul>
          <li>${esc(t('acc.rule1', { n: book.maxAddresses, km: book.votingRadiusKm }))}</li>
          <li>${esc(t('acc.rule2', { days: book.changeCooldownDays, m: book.freeMoveMeters, h: book.freeMoveHours }))}</li>
          <li>${esc(t('acc.rule3'))}</li>
        </ul>
        <strong class="small">${esc(book.nextChangeAt ? t('acc.nextChange', { date: fmt.date(book.nextChangeAt) }) : t('acc.changeNow'))}</strong>
      </section>` : ''}
    </div>`;

  list.forEach((a) => {
    const m = createMap($(`#addr-map-${a.id}`), { center: { lat: a.latitude, lng: a.longitude }, zoom: 13, dragging: false, scrollWheelZoom: false, doubleClickZoom: false, touchZoom: false, boxZoom: false, keyboard: false });
    L.circleMarker([a.latitude, a.longitude], { radius: 7, color: '#fff', weight: 2, fillColor: '#2F5444', fillOpacity: 1 }).addTo(m);
    if (isResident) {
      const c = L.circle([a.latitude, a.longitude], { radius: book.votingRadiusKm * 1000, color: '#2F5444', weight: 1.2, dashArray: '5 6', fillColor: '#5A7D66', fillOpacity: 0.08, interactive: false }).addTo(m);
      m.fitBounds(c.getBounds(), { padding: [6, 6] });
    }
    $(`#addr-map-${a.id} .leaflet-control-zoom`)?.remove();
    miniMaps.push(m);
  });

  $$('[data-edit]', panel).forEach((b) => b.addEventListener('click', () => editAddress(list.find((a) => a.id === Number(b.dataset.edit)))));
  $$('[data-primary]', panel).forEach((b) => b.addEventListener('click', () => withLoading(b, async () => {
    try { await api.put(`account/addresses/${b.dataset.primary}/primary`); await loadAddresses(); toast(t('acc.saved'), 'success'); }
    catch (err) { toast(errorMessage(err), 'error'); }
  })));
  $$('[data-delete]', panel).forEach((b) => b.addEventListener('click', () => deleteAddress(list.find((a) => a.id === Number(b.dataset.delete)))));
  $('#add-address')?.addEventListener('click', () => editAddress(null));
}

// What a change does, in words, before it is saved
function reviewBody(preview) {
  const votes = preview.withdrawnVotes || [];
  return `${preview.countsAsChange ? `<div class="notice notice-warn small">${icon.warn}<span>${esc(t('acc.countsAsChange'))}</span></div>` : ''}
    ${votes.length ? `<p>${esc(t('acc.withdrawList', { km: book.votingRadiusKm }))}</p>
      <ul class="list">${votes.map((v) => `<li><span><bdi>${esc(v.title)}</bdi></span><span class="small muted"><bdi>${esc(v.neighborhoodName)}</bdi></span></li>`).join('')}</ul>` : ''}`;
}

async function confirmPreview(preview) {
  if (!preview.allowed) throw new Error(serverText(preview.reason));
  if (!preview.countsAsChange && !(preview.withdrawnVotes || []).length) return true;
  return modal({ title: t('acc.reviewTitle'), body: reviewBody(preview), confirmText: t('acc.saveAnyway') });
}

async function editAddress(address) {
  let picker = null;
  const pending = modal({
    title: address ? (address.needsConfirmation ? t('acc.confirmTitle') : t('acc.editTitle')) : t('acc.addTitle'),
    body: '<div id="edit-picker"></div>',
    confirmText: t('acc.save'),
    wide: true,
    onConfirm: async () => {
      if (!picker.validate()) return false;
      const body = picker.values();
      const preview = await api.post('account/addresses/preview', { addressId: address?.id ?? null, latitude: body.latitude, longitude: body.longitude });
      if (!(await confirmPreview(preview))) return false;
      return address ? api.put(`account/addresses/${address.id}`, body) : api.post('account/addresses', body);
    },
  });
  // The dialog is already in the page, so its map can measure itself
  picker = addressPicker($('#edit-picker'), {
    value: address,
    height: 280,
    circleKm: isResident ? book.votingRadiusKm : 0,
    labelPlaceholder: isResident ? t('addr.labelPh') : t('addr.office'),
  });
  if (await pending) {
    toast(t('acc.addrSaved'), 'success');
    await loadAddresses();
  }
}

async function deleteAddress(address) {
  const i = book.addresses.indexOf(address);
  let preview;
  try {
    preview = await api.post('account/addresses/preview', { addressId: address.id, delete: true });
  } catch (err) {
    toast(errorMessage(err), 'error');
    return;
  }
  if (!preview.allowed) { toast(serverText(preview.reason), 'error'); return; }
  const ok = await modal({
    title: t('acc.deleteTitle', { label: addressName(address, i) }),
    body: `<p class="muted">${esc(t('acc.deleteText'))}</p>${reviewBody(preview)}`,
    confirmText: t('acc.delete'),
    danger: true,
    onConfirm: () => api.del(`account/addresses/${address.id}`).then(() => true),
  });
  if (ok) {
    toast(t('acc.deleted'), 'success');
    await loadAddresses();
  }
}

// ---------- Investor profile ----------

function renderInvestor(u) {
  const panel = $('#panel-investor');
  const status = u.investorStatus || 'PENDING';
  panel.innerHTML = `
    <form class="card stack account-form" id="investor-form" novalidate>
      <div class="row" style="justify-content:space-between"><h2>${esc(t('acc.tabInvestor'))}</h2>
        <span class="badge ${status === 'VERIFIED' ? 'badge-solid' : status === 'REJECTED' ? 'badge-red' : 'badge-amber'}">${esc(t('acc.status'))}: ${esc(label('investorStatus', status))}</span></div>
      <div class="field"><label for="companyName"><span>${esc(t('acc.company'))}</span> <span class="muted">${esc(t('form.optional'))}</span></label>
        <input class="input" id="companyName" name="companyName" maxlength="120" autocomplete="organization"><span class="error" aria-live="polite"></span></div>
      <div class="field"><label for="crNumber">${esc(t('inv.cr'))}</label>
        <input class="input" id="crNumber" name="crNumber" inputmode="numeric" maxlength="10" dir="ltr">
        ${status === 'VERIFIED' ? `<span class="btn-warn-note">${icon.warn}<span>${esc(t('acc.crWarn'))}</span></span>` : ''}<span class="error" aria-live="polite"></span></div>
      <div class="field"><div id="budget-range"></div><span class="hint">${esc(t('signup.budgetHint'))}</span></div>
      <div class="field"><span class="label" id="fields-label">${esc(t('inv.fields'))}</span>
        <div class="chips" role="group" aria-labelledby="fields-label" id="field-chips"></div><input type="hidden" name="investmentFields"><span class="error" aria-live="polite"></span></div>
      <button class="btn" type="submit" style="align-self:flex-start" data-loading="${esc(t('common.saving'))}">${esc(t('acc.save'))}</button>
    </form>`;
  const form = $('#investor-form');
  const fields = setupInvestorFields(form, u);
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    clearFieldErrors(form);
    const v = fields.values();
    const problem = checkInvestor(v);
    if (problem) { showFieldError(form, problem[0], problem[1]); return; }
    withLoading(form.querySelector('[type="submit"]'), async () => {
      try {
        const saved = await api.put('account/profile', {
          companyName: v.companyName || '', crNumber: v.crNumber, investmentFields: v.investmentFields,
          budgetMin: v.budgetMin, budgetMax: v.budgetMax, budgetNoMax: v.budgetMax === null,
        });
        session.setUser({ ...session.user(), ...saved });
        toast(t('acc.saved'), 'success');
        if (saved.investorStatus !== status) renderInvestor(saved);
      } catch (err) {
        if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
      }
    });
  });
}

// ---------- Start ----------

const profile = await api.get('account/profile');
renderProfile(profile);
if (isInvestor) renderInvestor(profile);
if (hasAddresses && location.hash === '#addresses' && !addressesLoaded) { addressesLoaded = true; loadAddresses(); }
