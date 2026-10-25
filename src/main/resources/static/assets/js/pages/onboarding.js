// Investor onboarding, right after sign-up: three skippable questions, one per screen
// (budget, fields, areas). Answers are saved together at the end and shape the "For you" list.
import { api, session } from '../api.js';
import { $, esc, guard, toast, withLoading, errorMessage, t } from '../ui.js';
import { langButton, wireLangButtons } from '../i18n.js';
import { budgetPicker, fieldsPicker, areasPicker, prefsSummary, loadNeighborhoods } from '../investor-prefs.js';

const user = await guard(['INVESTOR'], { header: false });
$('#lang-slot').innerHTML = langButton('btn-ghost');
wireLangButtons($('.flow-top'));

const app = $('#app');
const progress = $('#progress');
const firstName = (user.fullName || '').split(/\s+/)[0];

const STEPS = [
  { q: 'onb.q1', hint: 'onb.q1hint', make: (root) => budgetPicker(root, user) },
  { q: 'onb.q2', hint: 'onb.q2hint', make: (root) => fieldsPicker(root, user) },
  { q: 'onb.q3', hint: 'onb.q3hint', make: (root) => areasPicker(root, user) },
];
const answers = {}; // step index → value for PUT auth/me (skipped steps stay out)
const pickers = [];

function setProgress(n) {
  progress.classList.toggle('hidden', n === 0);
  progress.setAttribute('aria-valuenow', String(Math.min(n, 3)));
  [...progress.children].forEach((bar, i) => bar.classList.toggle('on', i < n));
}

function intro() {
  setProgress(0);
  app.innerHTML = `
    <section class="flow-card" aria-labelledby="flow-h">
      <span class="flow-emoji" aria-hidden="true">👋</span>
      <h1 id="flow-h" tabindex="-1">${esc(t('onb.hello', { name: firstName }))}</h1>
      <p class="muted">${esc(t('onb.intro'))}</p>
      <div class="flow-actions"><button type="button" class="btn" id="start">${esc(t('onb.start'))}</button></div>
    </section>`;
  $('#start').addEventListener('click', () => step(0));
  $('#start').focus();
}

function step(i) {
  setProgress(i + 1);
  const s = STEPS[i];
  app.innerHTML = `
    <section class="flow-card" aria-labelledby="flow-h">
      <span class="eyebrow">${esc(t('onb.step', { n: i + 1 }))}</span>
      <h1 id="flow-h" tabindex="-1">${esc(t(s.q))}</h1>
      <p class="muted">${esc(t(s.hint))}</p>
      <div class="stack" id="picker" style="gap:14px"></div>
      <div class="flow-actions">
        <button type="button" class="btn btn-ghost" id="back">${esc(t('onb.back'))}</button>
        <span style="flex:1"></span>
        <button type="button" class="btn btn-ghost" id="skip">${esc(t('onb.skip'))}</button>
        <button type="button" class="btn" id="next" data-loading="${esc(t('onb.saving'))}">${esc(t(i === STEPS.length - 1 ? 'onb.save' : 'onb.next'))}</button>
      </div>
    </section>`;
  pickers[i] = s.make($('#picker'));
  $('#flow-h').focus({ preventScroll: true });

  $('#back').addEventListener('click', () => (i === 0 ? intro() : step(i - 1)));
  $('#skip').addEventListener('click', () => { delete answers[i]; advance(i); });
  $('#next').addEventListener('click', () => {
    const picker = pickers[i];
    if (picker.isEmpty()) { delete answers[i]; advance(i); return; }
    const value = picker.value();
    if (!value) return; // the picker shows what's missing
    answers[i] = value;
    advance(i);
  });
}

async function advance(i) {
  if (i < STEPS.length - 1) { step(i + 1); return; }
  const body = Object.assign({}, ...Object.values(answers));
  if (!Object.keys(body).length) { done(user); return; }
  await withLoading($('#next'), async () => {
    try {
      const saved = await api.put('auth/me', body);
      session.setUser(saved);
      done(saved);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  });
}

async function done(saved) {
  setProgress(3);
  const hoods = await loadNeighborhoods().catch(() => []);
  const parts = prefsSummary(saved, hoods);
  app.innerHTML = `
    <section class="flow-card" aria-labelledby="flow-h">
      <span class="flow-emoji" aria-hidden="true">✨</span>
      <h1 id="flow-h" tabindex="-1">${esc(t('onb.doneTitle'))}</h1>
      ${parts.length ? `<div class="chips">${parts.map((p) => `<span class="chip chip-static">${esc(p)}</span>`).join('')}</div>` : ''}
      <p class="muted">${esc(t('onb.doneText'))}</p>
      <div class="flow-actions"><a class="btn" href="investor.html" id="finish">${esc(t('onb.finish'))}</a></div>
    </section>`;
  $('#skip-all').classList.add('hidden');
  $('#finish').focus();
}

intro();
