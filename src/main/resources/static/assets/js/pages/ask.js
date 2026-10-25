// "Ask AI": its own frame. The investor describes the opportunity in plain words, the AI turns it into
// filters (shown as chips so it's clear what was understood) and the matching opportunities follow.
import { api } from '../api.js';
import { $, $$, esc, guard, t, emptyState, skeletonCards, animateBars, errorMessage, icon, fmt, categoryLabel, cityLabel, params } from '../ui.js';
import { tn, serverText } from '../i18n.js';
import { oppCard } from '../opp-card.js';

await guard(['INVESTOR']);
const app = $('#app');
const EXAMPLES = ['ask.ex1', 'ask.ex2', 'ask.ex3', 'ask.ex4'];

app.innerHTML = `
  <section class="ask-hero" aria-labelledby="ask-h">
    <span class="eyebrow">${icon.spark} ${esc(t('ask.eyebrow'))}</span>
    <h1 id="ask-h">${t('ask.title')}</h1>
    <p>${esc(t('ask.lead'))}</p>
    <form class="ask-box" id="ask-form" role="search">
      <label class="sr-only" for="q">${esc(t('ask.label'))}</label>
      <span class="ask-icon" aria-hidden="true">${icon.spark}</span>
      <input id="q" name="q" autocomplete="off" maxlength="300" placeholder="${esc(t('ask.placeholder'))}">
      <button class="btn" type="submit" data-loading="${esc(t('common.wait'))}">${esc(t('ask.submit'))}</button>
    </form>
    <div class="ask-examples" role="group" aria-label="${esc(t('ask.examples'))}">
      <span>${esc(t('ask.examples'))}</span>
      ${EXAMPLES.map((k) => `<button type="button" class="chip chip-dark" data-example="${esc(t(k))}">${esc(t(k))}</button>`).join('')}
    </div>
  </section>
  <div id="answer" class="stack" style="gap:20px;margin-top:28px" aria-live="polite"></div>`;

const form = $('#ask-form');
const input = $('#q');
const answer = $('#answer');

function chips(r) {
  const out = [];
  if (r.neighborhoodName) out.push(t('ask.chipHood', { v: r.neighborhoodName }));
  if (r.city) out.push(t('ask.chipCity', { v: cityLabel(r.city) }));
  if (r.category) out.push(t('ask.chipCat', { v: categoryLabel(r.category) }));
  if (r.minCapital != null) out.push(t('ask.chipMin', { v: fmt.short(r.minCapital) }));
  if (r.maxCapital != null) out.push(t('ask.chipMax', { v: fmt.short(r.maxCapital) }));
  if (r.readyOnly) out.push(t('ask.chipReady'));
  if (r.keywords) out.push(t('ask.chipWords', { v: r.keywords }));
  return out;
}

async function ask(question) {
  const q = question.trim();
  if (q.length < 3) {
    input.focus();
    answer.innerHTML = `<p class="small muted">${esc(t('ask.tooShort'))}</p>`;
    return;
  }
  answer.innerHTML = `
    <div class="ask-understood"><span class="spinner" aria-hidden="true"></span><span>${esc(t('ask.thinking'))}</span></div>
    <div class="opp-grid">${skeletonCards(3, 240)}</div>`;
  const button = form.querySelector('[type="submit"]');
  button.disabled = true;
  try {
    const r = await api.post('explore/ask', { question: q });
    const list = r.results || [];
    const found = chips(r);
    answer.innerHTML = `
      <div class="ask-understood">
        <span class="ask-avatar" aria-hidden="true">${icon.spark}</span>
        <div class="stack" style="gap:8px">
          <strong>${esc(r.summary || t('ask.understood'))}</strong>
          ${found.length ? `<div class="chips" aria-label="${esc(t('ask.understood'))}">${found.map((c) => `<span class="chip chip-static chip-sm">${esc(c)}</span>`).join('')}</div>` : ''}
        </div>
      </div>
      ${r.relaxed ? `<div class="notice notice-info">${icon.info}<span>${esc(t('ask.relaxed'))}</span></div>` : ''}
      ${list.length
        ? `<h2 class="section-title">${esc(tn(list.length, 'count.opps'))}</h2><div class="opp-grid">${list.map((o) => oppCard(o)).join('')}</div>`
        : emptyState(t('ask.none'), t('ask.noneText'))}`;
    animateBars(answer);
    history.replaceState(null, '', `?q=${encodeURIComponent(q)}`);
  } catch (err) {
    answer.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(serverText(errorMessage(err)))}</span></div>`;
  } finally {
    button.disabled = false;
  }
}

form.addEventListener('submit', (e) => { e.preventDefault(); ask(input.value); });
$$('[data-example]').forEach((b) => b.addEventListener('click', () => {
  input.value = b.dataset.example;
  ask(input.value);
}));

// A shared link like ask.html?q=… runs the question again
const shared = params().get('q');
if (shared) { input.value = shared; ask(shared); } else { input.focus(); }
