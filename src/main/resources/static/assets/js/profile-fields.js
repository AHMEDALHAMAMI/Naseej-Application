// Sign-up pieces shared by register.html and choose-role.html:
// the neighborhood filled from the browser location, and the investor profile chips.
import { api } from './api.js';
import { $, esc, LABELS, withLoading, errorMessage, t } from './ui.js';
import { locate } from './maps.js';

/** Location card: detects the nearest neighborhood, with city/neighborhood dropdowns as a fallback. */
export function setupLocationCard(root) {
  const state = { latitude: null, longitude: null, neighborhoodId: null };
  const status = $('#loc-status', root);
  const result = $('#loc-result', root);
  const citySel = $('#city', root);
  const hoodSel = $('#neighborhoodId', root);
  let all = [];

  const setStatus = (text, cls = 'badge-sand') => {
    status.className = `badge ${cls}`;
    status.textContent = text;
  };

  const fillHoods = (city, selectedId) => {
    const hoods = all.filter((n) => n.city === city).sort((a, b) => a.name.localeCompare(b.name));
    hoodSel.innerHTML = `<option value="">${esc(t('form.chooseHood'))}</option>`
      + hoods.map((n) => `<option value="${n.id}"${n.id === selectedId ? ' selected' : ''}>${esc(n.name)}</option>`).join('');
  };

  const ready = api.get('neighborhoods/get-all', null, { auth: false }).then((list) => {
    all = list || [];
    const cities = [...new Set(all.map((n) => n.city))].sort();
    citySel.innerHTML = `<option value="">${esc(t('form.chooseCity'))}</option>`
      + cities.map((c) => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
  }).catch(() => {
    result.textContent = t('loc.hoodsFailed');
  });

  citySel.addEventListener('change', () => { fillHoods(citySel.value); state.neighborhoodId = null; });
  hoodSel.addEventListener('change', () => {
    state.neighborhoodId = hoodSel.value ? Number(hoodSel.value) : null;
    if (state.neighborhoodId) setStatus(t('loc.chosen'), '');
  });

  const detect = async () => {
    setStatus(t('loc.detecting'));
    try {
      const loc = await locate();
      state.latitude = loc.lat;
      state.longitude = loc.lng;
      await ready;
      const hood = await api.get('public/nearest-neighborhood', { lat: loc.lat, lng: loc.lng }, { auth: false });
      state.neighborhoodId = hood.id;
      citySel.value = hood.city;
      fillHoods(hood.city, hood.id);
      result.innerHTML = `<strong style="color:var(--ink);font-size:16px">${esc(hood.name)}, ${esc(hood.city)}</strong><br>${esc(t('loc.fromLocationHint'))}`;
      setStatus(t('loc.fromLocation'), '');
    } catch (err) {
      result.textContent = errorMessage(err) || t('loc.pickFromList');
      setStatus(t('loc.chooseManually'), 'badge-amber');
    }
  };

  $('#detect', root).addEventListener('click', (e) => withLoading(e.currentTarget, detect));
  detect();

  return {
    values: () => ({
      neighborhoodId: hoodSel.value ? Number(hoodSel.value) : state.neighborhoodId,
      latitude: state.latitude,
      longitude: state.longitude,
    }),
  };
}

/** Budget (single choice) and fields (multiple) chips for investors. */
export function setupInvestorFields(root) {
  const budgetBox = $('#budget-chips', root);
  const fieldBox = $('#field-chips', root);
  const budgetInput = root.querySelector('[name="investmentBudget"]');
  const fieldsInput = root.querySelector('[name="investmentFields"]');

  budgetBox.innerHTML = Object.entries(LABELS.budget)
    .map(([k, v]) => `<button type="button" class="chip" data-v="${k}" aria-pressed="false">${esc(v)}</button>`).join('');
  fieldBox.innerHTML = Object.entries(LABELS.field)
    .map(([k, v]) => `<button type="button" class="chip" data-v="${k}" aria-pressed="false">${esc(v)}</button>`).join('');

  budgetBox.addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    budgetBox.querySelectorAll('.chip').forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
    budgetInput.value = chip.dataset.v;
  });
  fieldBox.addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    chip.setAttribute('aria-pressed', String(chip.getAttribute('aria-pressed') !== 'true'));
    fieldsInput.value = [...fieldBox.querySelectorAll('.chip[aria-pressed="true"]')].map((c) => c.dataset.v).join(',');
  });

  return {
    values: () => ({
      crNumber: $('#crNumber', root).value.trim(),
      investmentBudget: budgetInput.value || null,
      investmentFields: fieldsInput.value || null,
    }),
  };
}

/** Client-side checks that mirror the server rules, so mistakes show before sending. */
export function checkInvestor(values) {
  if (!/^\d{10}$/.test(values.crNumber || '')) return ['crNumber', t('inv.crInvalid')];
  if (!values.investmentBudget) return ['investmentBudget', t('inv.budgetRequired')];
  if (!values.investmentFields) return ['investmentFields', t('inv.fieldsRequired')];
  return null;
}

export function showFieldError(form, name, message) {
  const input = form.querySelector(`[name="${name}"]`);
  const box = input?.closest('.field')?.querySelector('.error');
  if (box) box.textContent = message;
  if (input && input.type !== 'hidden') {
    input.setAttribute('aria-invalid', 'true');
    input.focus();
  }
}
