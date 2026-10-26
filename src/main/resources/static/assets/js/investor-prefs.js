// Investor preference pickers, shared by the onboarding questions and the account page:
// budget (presets or a custom range), fields (with "something else" in the investor's own words)
// and the neighborhoods they want to invest in.
import { api } from './api.js';
import { $, $$, esc, LABELS, csv, cityLabel, label, budgetText, t } from './ui.js';
import { tn } from './i18n.js';

const digits = (value) => String(value || '').replace(/[^\d٠-٩]/g, '').replace(/[٠-٩]/g, (d) => '٠١٢٣٤٥٦٧٨٩'.indexOf(d));
const grouped = (value) => (value === '' || value == null ? '' : new Intl.NumberFormat('en-US').format(Number(value)));

// ---------- Budget ----------

/** Budget chips plus "another amount" with a from/to range. */
export function budgetPicker(root, user = {}) {
  const presets = Object.entries(LABELS.budget);
  root.innerHTML = `
    <div class="chips" role="group" aria-label="${esc(t('pref.budget'))}">
      ${presets.map(([k, v]) => `<button type="button" class="chip" data-budget="${k}" aria-pressed="false">${esc(v)}</button>`).join('')}
      <button type="button" class="chip" data-budget="CUSTOM" aria-pressed="false" aria-controls="budget-range">${esc(t('pref.custom'))}</button>
    </div>
    <div class="range-box hidden" id="budget-range">
      <div class="prefs-range">
        <div class="field">
          <label for="budget-min">${esc(t('pref.min'))}</label>
          <div class="input-affix"><input class="input num" id="budget-min" inputmode="numeric" autocomplete="off" placeholder="200,000"><span>${esc(t('fmt.sar', { v: '' }).trim())}</span></div>
        </div>
        <div class="field">
          <label for="budget-max">${esc(t('pref.max'))}</label>
          <div class="input-affix"><input class="input num" id="budget-max" inputmode="numeric" autocomplete="off" placeholder="450,000"><span>${esc(t('fmt.sar', { v: '' }).trim())}</span></div>
        </div>
      </div>
      <span class="hint">${esc(t('pref.rangeHint'))}</span>
      <span class="error" aria-live="polite"></span>
    </div>`;

  const box = $('#budget-range', root);
  const min = $('#budget-min', root);
  const max = $('#budget-max', root);
  const error = $('.error', box);
  let chosen = null;

  const pick = (value) => {
    chosen = chosen === value ? null : value; // tapping the chosen chip again clears it
    $$('[data-budget]', root).forEach((c) => c.setAttribute('aria-pressed', String(c.dataset.budget === chosen)));
    box.classList.toggle('hidden', chosen !== 'CUSTOM');
    error.textContent = '';
    if (chosen === 'CUSTOM') min.focus();
  };
  $$('[data-budget]', root).forEach((c) => c.addEventListener('click', () => pick(c.dataset.budget)));
  [min, max].forEach((input) => {
    input.addEventListener('input', () => {
      const raw = digits(input.value).slice(0, 10);
      input.value = grouped(raw);
      error.textContent = '';
    });
  });

  // Starting values
  if (user.investmentBudget) {
    pick(user.investmentBudget);
    if (user.investmentBudget === 'CUSTOM') {
      min.value = grouped(user.budgetMin ?? '');
      max.value = grouped(user.budgetMax ?? '');
    }
  }

  return {
    /** { investmentBudget, budgetMin, budgetMax } ready for PUT auth/me, or null with the error shown. */
    value() {
      if (!chosen) return { investmentBudget: '' };
      if (chosen !== 'CUSTOM') return { investmentBudget: chosen };
      const lo = digits(min.value) ? Number(digits(min.value)) : null;
      const hi = digits(max.value) ? Number(digits(max.value)) : null;
      if (lo === null && hi === null) { error.textContent = t('pref.rangeEmpty'); min.focus(); return null; }
      if (lo !== null && hi !== null && lo > hi) { error.textContent = t('pref.rangeOrder'); max.focus(); return null; }
      return { investmentBudget: 'CUSTOM', budgetMin: lo, budgetMax: hi };
    },
    isEmpty: () => !chosen,
  };
}

// ---------- Fields ----------

/** Field chips (several allowed) plus "something else" in the investor's own words. */
export function fieldsPicker(root, user = {}) {
  root.innerHTML = `
    <div class="chips" role="group" aria-label="${esc(t('pref.fields'))}">
      ${Object.entries(LABELS.field).map(([k, v]) => `<button type="button" class="chip" data-field="${k}" aria-pressed="false">${esc(v)}</button>`).join('')}
      <button type="button" class="chip" data-other aria-pressed="false" aria-controls="field-other-box">${esc(t('pref.other'))}</button>
    </div>
    <div class="field hidden" id="field-other-box">
      <label for="field-other">${esc(t('pref.otherLabel'))}</label>
      <input class="input" id="field-other" maxlength="200" autocomplete="off" placeholder="${esc(t('pref.otherPh'))}">
    </div>`;

  const otherBox = $('#field-other-box', root);
  const other = $('#field-other', root);
  const otherChip = $('[data-other]', root);
  const toggle = (chip, on = chip.getAttribute('aria-pressed') !== 'true') => chip.setAttribute('aria-pressed', String(on));

  $$('[data-field]', root).forEach((c) => c.addEventListener('click', () => toggle(c)));
  otherChip.addEventListener('click', () => {
    toggle(otherChip);
    const on = otherChip.getAttribute('aria-pressed') === 'true';
    otherBox.classList.toggle('hidden', !on);
    if (on) other.focus();
  });

  const chosen = csv(user.investmentFields);
  $$('[data-field]', root).forEach((c) => toggle(c, chosen.includes(c.dataset.field)));
  if (user.investmentFieldsOther) {
    toggle(otherChip, true);
    otherBox.classList.remove('hidden');
    other.value = user.investmentFieldsOther;
  }

  return {
    value() {
      const fields = $$('[data-field][aria-pressed="true"]', root).map((c) => c.dataset.field).join(',');
      const otherOn = otherChip.getAttribute('aria-pressed') === 'true';
      return { investmentFields: fields, investmentFieldsOther: otherOn ? other.value.trim() : '' };
    },
    isEmpty() {
      const v = this.value();
      return !v.investmentFields && !v.investmentFieldsOther;
    },
  };
}

// ---------- Areas ----------

let hoodsPromise = null;
/** Every neighborhood (public list), loaded once per page. */
export function loadNeighborhoods() {
  hoodsPromise ??= api.get('neighborhoods/get-all', null, { auth: false }).then((list) => list || []);
  return hoodsPromise;
}

/** City dropdown, then that city's neighborhoods as chips. The selection is kept across cities. */
export function areasPicker(root, user = {}) {
  const selected = new Set(csv(user.investmentAreas).map(Number));
  root.innerHTML = `
    <div class="field">
      <label for="area-city">${esc(t('pref.city'))}</label>
      <select class="select" id="area-city"><option value="">${esc(t('pref.chooseCity'))}</option></select>
    </div>
    <div class="stack" style="gap:8px">
      <div class="row" style="justify-content:space-between">
        <span class="label" id="area-hoods-label">${esc(t('pref.areas'))}</span>
        <span class="small muted" id="area-count" aria-live="polite"></span>
      </div>
      <div class="chips" role="group" aria-labelledby="area-hoods-label" id="area-hoods"><div class="skeleton" style="height:40px;width:100%"></div></div>
    </div>`;

  const citySel = $('#area-city', root);
  const hoodBox = $('#area-hoods', root);
  const count = $('#area-count', root);
  let all = [];

  const updateCount = () => { count.textContent = selected.size ? tn(selected.size, 'count.selected') : ''; };
  const drawHoods = () => {
    const hoods = all.filter((n) => n.city === citySel.value).sort((a, b) => a.name.localeCompare(b.name, 'ar'));
    hoodBox.innerHTML = !citySel.value ? ''
      : hoods.length
        ? hoods.map((n) => `<button type="button" class="chip" data-hood="${n.id}" aria-pressed="${selected.has(n.id)}">${esc(n.name)}</button>`).join('')
        : `<span class="small muted">${esc(t('pref.noHoods'))}</span>`;
    $$('[data-hood]', hoodBox).forEach((c) => c.addEventListener('click', () => {
      const id = Number(c.dataset.hood);
      if (selected.has(id)) selected.delete(id); else selected.add(id);
      c.setAttribute('aria-pressed', String(selected.has(id)));
      updateCount();
    }));
  };

  const ready = loadNeighborhoods().then((list) => {
    all = list;
    const cities = [...new Set(all.map((n) => n.city))]
      .sort((a, b) => all.filter((n) => n.city === b).length - all.filter((n) => n.city === a).length);
    citySel.innerHTML = `<option value="">${esc(t('pref.chooseCity'))}</option>`
      + cities.map((c) => `<option value="${esc(c)}">${esc(cityLabel(c))}</option>`).join('');
    // Open on the city of the first chosen neighborhood, or the city with the most neighborhoods
    const first = all.find((n) => selected.has(n.id));
    citySel.value = first ? first.city : (cities[0] || '');
    drawHoods();
    updateCount();
  }).catch(() => {
    hoodBox.innerHTML = `<span class="small" style="color:var(--red-700)">${esc(t('pref.hoodsFailed'))}</span>`;
  });
  citySel.addEventListener('change', drawHoods);

  return {
    ready,
    value: () => ({ investmentAreas: [...selected].join(',') }),
    isEmpty: () => selected.size === 0,
  };
}

// ---------- Summary ----------

/** Short labels of what the investor chose, for "Based on: …" lines. Neighborhood names need the list. */
export function prefsSummary(user, hoods = []) {
  const parts = [];
  const budget = budgetText(user);
  if (budget) parts.push(budget);
  const fields = csv(user.investmentFields).map((f) => label('field', f));
  if (user.investmentFieldsOther) fields.push(user.investmentFieldsOther);
  if (fields.length) parts.push(fields.length > 2 ? `${fields.slice(0, 2).join('، ')} +${fields.length - 2}` : fields.join('، '));
  const ids = csv(user.investmentAreas).map(Number);
  const names = ids.map((id) => hoods.find((n) => n.id === id)?.name).filter(Boolean);
  if (names.length) parts.push(names.length > 2 ? `${names.slice(0, 2).join('، ')} +${names.length - 2}` : names.join('، '));
  return parts;
}

export const hasPrefs = (user) => !!(user.investmentBudget || user.investmentFields || user.investmentFieldsOther || user.investmentAreas);

