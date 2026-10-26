// Sign-up and account pieces shared by register.html, choose-role.html and account.html:
// the investor profile (CR, company, budget range, fields) and small form helpers.
import { $, esc, LABELS, t } from './ui.js';
import { rangeSlider } from './components.js';

/**
 * Investor fields inside root: #crNumber, #companyName, #budget-range and #field-chips.
 * value: an existing profile ({ crNumber, companyName, budgetMin, budgetMax, investmentFields }).
 */
export function setupInvestorFields(root, value = {}) {
  const fieldBox = $('#field-chips', root);
  const fieldsInput = root.querySelector('[name="investmentFields"]');
  const chosen = new Set((value.investmentFields || '').split(',').filter(Boolean));

  if (value.crNumber) $('#crNumber', root).value = value.crNumber;
  if (value.companyName && $('#companyName', root)) $('#companyName', root).value = value.companyName;

  fieldBox.innerHTML = Object.entries(LABELS.field)
    .map(([k, v]) => `<button type="button" class="chip" data-v="${k}" aria-pressed="${chosen.has(k)}">${esc(v)}</button>`).join('');
  fieldsInput.value = [...chosen].join(',');
  fieldBox.addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    chip.setAttribute('aria-pressed', String(chip.getAttribute('aria-pressed') !== 'true'));
    fieldsInput.value = [...fieldBox.querySelectorAll('.chip[aria-pressed="true"]')].map((c) => c.dataset.v).join(',');
  });

  const min = value.budgetMin != null ? Number(value.budgetMin) : 200000;
  const max = value.budgetMin != null ? (value.budgetMax != null ? Number(value.budgetMax) : null) : 800000;
  const budget = rangeSlider($('#budget-range', root), { value: [min, max], label: t('inv.budget') });

  return {
    values: () => {
      const [budgetMin, budgetMax] = budget.get();
      return {
        crNumber: $('#crNumber', root).value.trim(),
        companyName: $('#companyName', root)?.value.trim() || null,
        budgetMin,
        budgetMax,
        investmentFields: fieldsInput.value || null,
      };
    },
  };
}

/** Client-side checks that mirror the server rules, so mistakes show before sending. */
export function checkInvestor(values) {
  if (!/^\d{10}$/.test(values.crNumber || '')) return ['crNumber', t('inv.crInvalid')];
  if (!values.investmentFields) return ['investmentFields', t('inv.fieldsRequired')];
  return null;
}

/** 05XXXXXXXX, 5XXXXXXXX, +9665… or 9665… → 9665XXXXXXXX (what the server stores). Null when empty, false when invalid. */
export function normalizePhone(raw) {
  const digits = String(raw || '').replace(/[^\d]/g, '');
  if (!digits) return null;
  const local = digits.replace(/^(00966|966|0)/, '');
  return /^5\d{8}$/.test(local) ? `966${local}` : false;
}

/** 9665XXXXXXXX → 05XXXXXXXX for showing in a form. */
export function displayPhone(stored) {
  return stored && /^9665\d{8}$/.test(stored) ? `0${stored.slice(3)}` : (stored || '');
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

/** Clears the errors shown by showFieldError / applyFieldErrors inside root. */
export function clearFieldErrors(root) {
  root.querySelectorAll('[aria-invalid="true"]').forEach((el) => el.removeAttribute('aria-invalid'));
  root.querySelectorAll('.field .error').forEach((el) => { el.textContent = ''; });
}

/** A show/hide button for a password input (the button sits after the input inside .password-wrap). */
export function wirePasswordToggle(input) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'btn btn-ghost btn-sm password-toggle';
  btn.textContent = t('signup.show');
  btn.setAttribute('aria-pressed', 'false');
  btn.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.textContent = show ? t('signup.hide') : t('signup.show');
    btn.setAttribute('aria-pressed', String(show));
  });
  input.after(btn);
}
