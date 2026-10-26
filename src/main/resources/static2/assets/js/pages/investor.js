// Investor discovery, one decision per step instead of one long page:
//   1 City → 2 Neighborhood and budget → 3 Opportunities (list or map) → 4 the idea page (idea.html)
// The AI search sits on top of every step; its answer replaces the step until the investor goes back.
// The step lives in the URL hash (#/, #/city/Riyadh, #/results?city=…), so Back and shared links work.
// Search filters never change the investor's saved profile (budget preferences live in My account).
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, errorMessage, statusBadge, progressBar, animateBars, emptyState, skeletonCards, categoryLabel, ideaName, isolate, withLoading, t } from '../ui.js';
import { tn, locale } from '../i18n.js';
import { createMap, pinIcon, keyboardMarker, approxCircle, SAUDI_BOUNDS } from '../maps.js';
import { stepper, breadcrumb, scoreMeter, wirePopovers, rangeSlider, catTile, fitBadge, earlyStart } from '../components.js';

const user = await guard(['INVESTOR']);
const verified = user.investorStatus === 'VERIFIED';
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const COMPACT = new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 2 });
const sarShort = (v) => t('fmt.sar', { v: COMPACT.format(Number(v)) });
const PAGE = 9;
const LAST_KEY = 'naseej.investorSearch';
const SUGGESTIONS = ['ai.s1', 'ai.s2', 'ai.s3', 'ai.s4'];

// The profile budget is where a new search starts
const PROFILE_RANGE = [user.budgetMin ?? null, user.budgetMax ?? null];

const app = $('#app');
app.innerHTML = `
  ${verified ? '' : `<div class="notice ${user.investorStatus === 'REJECTED' ? 'notice-error' : 'notice-warn'}" style="margin-bottom:20px">${icon.info}
    <span>${user.investorStatus === 'REJECTED' ? t('inv.bannerRejected') : t('inv.bannerPending')}</span></div>`}
  <section class="inv-top" aria-labelledby="inv-title">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t('inv.hello', { name: user.fullName.split(' ')[0] }))}</span>
      <h1 id="inv-title" tabindex="-1">${t('inv.title')}</h1>
      <p class="muted inv-lead">${esc(t('inv.lead'))}</p>
    </div>
    <form class="ai-search ai-search-light" id="ai-form" role="search" novalidate>
      <span class="ai-search-icon" aria-hidden="true">${icon.spark}</span>
      <label class="sr-only" for="ai-q">${esc(t('ai.label'))}</label>
      <input id="ai-q" type="search" maxlength="200" autocomplete="off" placeholder="${esc(t('ai.placeholder'))}">
      <button class="btn ai-go" type="submit" data-loading="${esc(t('ai.thinking'))}">${esc(t('ai.ask'))}</button>
    </form>
    <div class="ai-chips" role="group" aria-label="${esc(t('ai.tryLabel'))}">
      <span class="ai-try">${esc(t('ai.try'))}</span>
      ${SUGGESTIONS.map((k) => `<button type="button" class="ai-chip" data-suggest="${esc(t(k))}">${esc(t(k))}</button>`).join('')}
    </div>
  </section>
  <div id="steps"></div>
  <div id="stage" aria-live="polite"></div>`;

const stage = $('#stage');
const STEP_LABELS = [t('disc.s1'), t('disc.s2'), t('disc.s3'), t('disc.s4')];

// ---------- Search state (from the hash) ----------

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [path, qs = ''] = raw.split('?');
  const p = new URLSearchParams(qs);
  const parts = path.split('/').filter(Boolean).map(decodeURIComponent);
  const num = (k) => (p.get(k) === null || p.get(k) === '' ? null : Number(p.get(k)));
  return {
    view: parts[0] || 'start',
    city: parts[0] === 'city' ? parts[1] : (p.get('city') || ''),
    hood: num('hood'),
    min: num('min'),
    max: num('max'),
    hasBudget: p.has('min') || p.has('max'),
  };
}

function resultsHash({ city, hood, min, max }) {
  const p = new URLSearchParams();
  if (city) p.set('city', city);
  if (hood) p.set('hood', hood);
  if (min !== null && min !== undefined) p.set('min', min);
  if (max !== null && max !== undefined) p.set('max', max);
  return `#/results?${p.toString()}`;
}

const remember = (s) => { try { localStorage.setItem(LAST_KEY, JSON.stringify(s)); } catch { /* ignore */ } };
const lastSearch = () => { try { return JSON.parse(localStorage.getItem(LAST_KEY) || 'null'); } catch { return null; } };

let cities = null;
async function loadCities() {
  cities ??= await api.get('explore/cities');
  return cities;
}
const hoodCache = new Map();
async function loadHoods(city) {
  if (!hoodCache.has(city)) hoodCache.set(city, await api.get(`explore/neighborhoods/${encodeURIComponent(city)}`));
  return hoodCache.get(city);
}

function showSteps(n, crumbs) {
  $('#steps').hidden = false;
  $('#steps').innerHTML = `<div class="disc-nav">${stepper(STEP_LABELS, n, { links: ['#/', crumbs?.cityHref || null] })}${crumbs ? breadcrumb(crumbs.items) : ''}</div>`;
}

const errorBox = (err) => `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;

// ---------- Step 1: city ----------

async function startStep() {
  showSteps(0);
  stage.innerHTML = `<div class="city-grid">${skeletonCards(4, 120)}</div>`;
  let list;
  try { list = await loadCities(); } catch (err) { stage.innerHTML = errorBox(err); return; }
  const last = lastSearch();
  const total = list.reduce((s, c) => s + c.opportunities, 0);
  stage.innerHTML = `
    <section class="disc-step" aria-labelledby="s1-h">
      <div class="disc-q"><h2 id="s1-h" class="flow-q">${esc(t('disc.q1'))}</h2>
        <p class="muted">${esc(t('disc.q1Sub'))}</p></div>
      ${last?.city ? `<a class="resume" href="${esc(resultsHash(last))}">${icon.back}<span>${esc(t('disc.resume', { place: last.hoodName ? `${last.hoodName}, ${last.city}` : last.city }))}</span></a>` : ''}
      ${list.length ? `<div class="city-grid">
        ${list.map((c) => `
          <a class="city-card" href="#/city/${encodeURIComponent(c.city)}">
            <strong>${esc(c.city)}</strong>
            <span class="num">${esc(tn(c.opportunities, 'count.opps'))}</span>
            <small>${esc(tn(c.neighborhoods, 'count.hoods'))}</small>
            <span class="city-go" aria-hidden="true">${icon.arrow}</span>
          </a>`).join('')}
        <a class="city-card city-all" href="${esc(resultsHash({ min: PROFILE_RANGE[0], max: PROFILE_RANGE[1] }))}">
          <strong>${esc(t('disc.allCities'))}</strong>
          <span class="num">${esc(tn(total, 'count.opps'))}</span>
          <small>${esc(t('disc.allCitiesSub'))}</small>
          <span class="city-go" aria-hidden="true">${icon.arrow}</span>
        </a>
      </div>` : emptyState(t('inv.noOpps'), t('inv.noOppsText'), `<div class="row"><a class="btn btn-sm" href="propose.html">${esc(t('ai.emptyCta'))}</a></div>`)}
    </section>`;
}

// ---------- Step 2: neighborhood and budget ----------

async function cityStep(state) {
  const city = state.city;
  showSteps(1, { items: [{ label: t('inv.ksa'), href: '#/' }, { label: city }] });
  stage.innerHTML = skeletonCards(1, 320);
  let hoods;
  try { hoods = await loadHoods(city); } catch (err) { stage.innerHTML = errorBox(err); return; }
  const last = lastSearch();
  const startRange = state.hasBudget ? [state.min, state.max]
    : (last && last.city === city ? [last.min, last.max] : PROFILE_RANGE);
  let chosenHood = state.hood || (last && last.city === city ? last.hood : null) || null;
  const total = hoods.reduce((s, h) => s + h.opportunities, 0);

  stage.innerHTML = `
    <form class="flow-card disc-step" id="f-hood" novalidate>
      <fieldset class="field">
        <legend class="flow-q">${esc(t('disc.q2', { city }))}</legend>
        <p class="muted" style="margin-top:4px">${esc(t('disc.q2Sub'))}</p>
        <div class="hood-grid" role="radiogroup" aria-label="${esc(t('form.hood'))}">
          <label class="hood-opt"><input type="radio" name="hood" value=""${chosenHood ? '' : ' checked'}><span><strong>${esc(t('disc.wholeCity', { city }))}</strong><small class="num">${esc(tn(total, 'count.opps'))}</small></span></label>
          ${hoods.map((h) => `<label class="hood-opt"><input type="radio" name="hood" value="${h.neighborhoodId}"${h.neighborhoodId === chosenHood ? ' checked' : ''}><span><strong dir="auto">${esc(h.name)}</strong><small class="num">${esc(tn(h.opportunities, 'count.opps'))}</small></span></label>`).join('')}
        </div>
      </fieldset>
      <div class="field">
        <span class="label flow-q" id="budget-q">${esc(t('disc.q3'))}</span>
        <p class="muted">${esc(t('disc.q3Sub'))}</p>
        <div id="range"></div>
        <p class="small muted">${esc(t('disc.profileNote'))} <a href="account.html#investor">${esc(t('disc.editProfile'))}</a></p>
      </div>
      <div class="action-bar">
        <a class="btn btn-secondary" href="#/">${icon.back} ${esc(t('common.back'))}</a>
        <button class="btn" type="submit">${esc(t('disc.show'))} ${icon.arrow}</button>
      </div>
    </form>`;
  const range = rangeSlider($('#range'), { value: startRange.every((v) => v === null) ? [null, null] : startRange, label: '' });
  $('#f-hood').addEventListener('change', (e) => { if (e.target.name === 'hood') chosenHood = Number(e.target.value) || null; });
  $('#f-hood').addEventListener('submit', (e) => {
    e.preventDefault();
    const [min, max] = range.get();
    location.hash = resultsHash({ city, hood: chosenHood, min, max });
  });
}

// ---------- Step 3: opportunities ----------

const SORTS = {
  strong: (a, b) => strength(b) - strength(a),
  close: (a, b) => (b.votePercent || 0) - (a.votePercent || 0),
  capital: (a, b) => (a.capitalMin ?? Infinity) - (b.capitalMin ?? Infinity),
};
function strength(o) { return (o.votePercent || 0) + 4 * (o.votesLast7Days || 0) + 0.3 * (o.checkScore || 0); }
const FIT_ORDER = { FULL: 0, PARTIAL: 1, UNKNOWN: 2 };

const view = { list: [], category: '', ready: 'any', sort: 'fit', shown: PAGE, mode: 'list', state: null };

async function resultsStep(state) {
  view.state = state;
  Object.assign(view, { category: '', ready: 'any', sort: 'fit', shown: PAGE });
  const hoods = state.city ? await loadHoods(state.city).catch(() => []) : [];
  const hood = hoods.find((h) => h.neighborhoodId === state.hood);
  const crumbs = [{ label: t('inv.ksa'), href: '#/' }];
  if (state.city) crumbs.push({ label: state.city, href: `#/city/${encodeURIComponent(state.city)}` });
  if (hood) crumbs.push({ label: hood.name });
  crumbs.push({ label: t('disc.s3') });
  showSteps(2, { cityHref: state.city ? `#/city/${encodeURIComponent(state.city)}` : null, items: crumbs });
  remember({ city: state.city, hood: state.hood, hoodName: hood?.name || null, min: state.min, max: state.max });

  const budgetText = state.min === null && state.max === null ? t('disc.anyBudget')
    : state.max === null ? t('range.noMax', { v: sarShort(state.min ?? 0) })
      : t('range.value', { min: sarShort(state.min ?? 0), max: sarShort(state.max) });
  stage.innerHTML = `
    <section class="disc-step">
      <div class="search-summary">
        <div class="pref-tags">
          <span class="pref-tag">${icon.pin}${esc(hood ? isolate(`${hood.name}, ${state.city}`) : (state.city || t('disc.allCities')))}</span>
          <span class="pref-tag">${esc(budgetText)}</span>
        </div>
        <a class="btn btn-ghost btn-sm" href="${state.city ? `#/city/${encodeURIComponent(state.city)}` : '#/'}">${esc(t('disc.change'))}</a>
      </div>
      <div class="filter-bar" id="filters" hidden>
        <div class="chips" id="cat-chips" role="group" aria-label="${esc(t('prop.category'))}"></div>
        <div class="filter-row">
          <div class="segmented" role="group" aria-label="${esc(t('disc.readiness'))}">
            <button type="button" data-ready="any" aria-pressed="true">${esc(t('disc.readyAny'))}</button>
            <button type="button" data-ready="collecting" aria-pressed="false">${esc(t('disc.readyCollecting'))}</button>
            <button type="button" data-ready="reached" aria-pressed="false">${esc(t('disc.readyReached'))}</button>
          </div>
          <label class="sort"><span class="sr-only">${esc(t('disc.sort'))}</span>
            <select class="select select-sm" id="sort">
              <option value="fit">${esc(t('disc.sortFit'))}</option>
              <option value="strong">${esc(t('disc.sortStrong'))}</option>
              <option value="close">${esc(t('disc.sortClose'))}</option>
              <option value="capital">${esc(t('disc.sortCapital'))}</option>
            </select></label>
          <div class="segmented" role="group" aria-label="${esc(t('disc.view'))}">
            <button type="button" data-mode="list" aria-pressed="true">${esc(t('disc.list'))}</button>
            <button type="button" data-mode="map" aria-pressed="false">${esc(t('disc.map'))}</button>
          </div>
        </div>
      </div>
      <p class="small muted" id="res-count" aria-live="polite"></p>
      <div id="results"><div class="opp-grid">${skeletonCards(3, 280)}</div></div>
    </section>`;

  try {
    const params = { limit: 60 };
    if (state.hood) params.neighborhoodId = state.hood; else if (state.city) params.city = state.city;
    if (state.min !== null) params.budgetMin = state.min;
    if (state.max !== null) params.budgetMax = state.max;
    view.list = await api.get('explore/match', params);
  } catch (err) {
    $('#results').innerHTML = errorBox(err);
    return;
  }
  wireFilters();
  renderResults();
}

function wireFilters() {
  const cats = [...new Set(view.list.map((o) => o.category))];
  $('#filters').hidden = view.list.length === 0;
  $('#cat-chips').innerHTML = [['', t('disc.allKinds')], ...cats.map((c) => [c, categoryLabel(c)])]
    .map(([v, l]) => `<button type="button" class="chip" data-cat="${esc(v)}" aria-pressed="${view.category === v}">${esc(l)}</button>`).join('');
  $('#cat-chips').addEventListener('click', (e) => {
    const b = e.target.closest('[data-cat]');
    if (!b) return;
    view.category = b.dataset.cat;
    view.shown = PAGE;
    $$('[data-cat]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    renderResults();
  });
  $$('[data-ready]').forEach((b) => b.addEventListener('click', () => {
    view.ready = b.dataset.ready;
    view.shown = PAGE;
    $$('[data-ready]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    renderResults();
  }));
  $('#sort').addEventListener('change', (e) => { view.sort = e.target.value; renderResults(); });
  $$('[data-mode]').forEach((b) => b.addEventListener('click', () => {
    view.mode = b.dataset.mode;
    $$('[data-mode]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    renderResults();
  }));
}

function filtered() {
  let list = view.list.filter((o) => !view.category || o.category === view.category)
    .filter((o) => view.ready === 'any' || (view.ready === 'reached' ? o.votePercent >= 100 : o.votePercent < 100));
  // "Best fit" keeps the server's order (inside the budget first, then partial, then no estimate)
  if (view.sort !== 'fit') list = [...list].sort((a, b) => (FIT_ORDER[a.budgetFit] ?? 0) - (FIT_ORDER[b.budgetFit] ?? 0) || SORTS[view.sort](a, b));
  return list;
}

function proposeHref(extra = {}) {
  const s = view.state || {};
  const p = new URLSearchParams({ new: '1' });
  if (s.city) p.set('city', s.city);
  if (s.hood) p.set('hood', s.hood);
  if (extra.category || view.category) p.set('category', extra.category || view.category);
  return `propose.html?${p.toString()}`;
}

function noResults(widen = true) {
  return `<div class="empty">${icon.map}
    <h3>${esc(t('ai.empty'))}</h3>
    <p>${esc(t('ai.emptyText'))}</p>
    <div class="row">
      <a class="btn btn-sm" href="${esc(proposeHref())}">${icon.spark} ${esc(t('ai.emptyCta'))}</a>
      ${widen ? `<a class="btn btn-secondary btn-sm" href="${view.state?.city ? `#/city/${encodeURIComponent(view.state.city)}` : '#/'}">${esc(t('sg.widen'))}</a>` : ''}
    </div>
  </div>`;
}

let resultsMap = null;
function renderResults() {
  const list = filtered();
  $('#res-count').textContent = tn(list.length, 'count.opps');
  const box = $('#results');
  if (!list.length) {
    box.innerHTML = noResults();
    return;
  }
  if (view.mode === 'map') {
    box.innerHTML = `<div class="map map-tall" id="res-map" aria-label="${esc(t('inv.mapLabel'))}"></div>
      <p class="small muted" style="margin-top:8px">${esc(t('teaser.listNote'))}</p>`;
    resultsMap = createMap($('#res-map'), { zoom: 11 });
    const pts = [];
    list.filter((o) => o.latitude != null).forEach((o) => {
      if (o.teaser) approxCircle(o.latitude, o.longitude, o.approxRadiusM).addTo(resultsMap);
      keyboardMarker(L.marker([o.latitude, o.longitude], { icon: pinIcon(o.votePercent >= 100 ? 'in-range' : '') }), ideaName(o))
        .bindTooltip(`<strong>${esc(ideaName(o))}</strong>`, { className: 'nsj-tip', direction: 'top' })
        .on('click', () => { location.href = `idea.html?id=${o.id}`; })
        .addTo(resultsMap);
      pts.push([o.latitude, o.longitude]);
    });
    if (pts.length) resultsMap.fitBounds(L.latLngBounds(pts).pad(0.3), { maxZoom: 15 });
    else resultsMap.fitBounds(SAUDI_BOUNDS);
    return;
  }
  box.innerHTML = `
    <div class="opp-grid">${list.slice(0, view.shown).map((o) => oppCard(o)).join('')}</div>
    ${list.length > view.shown ? `<div class="row" style="justify-content:center;margin-top:20px"><button type="button" class="btn btn-secondary" id="more">${esc(t('disc.more', { n: fmt.num(list.length - view.shown) }))}</button></div>` : ''}
    <div class="propose-hint">${icon.spark}<span>${esc(t('disc.notHere'))}</span><a class="btn btn-ghost btn-sm" href="${esc(proposeHref())}">${esc(t('ai.emptyCta'))}</a></div>`;
  $('#more')?.addEventListener('click', () => { view.shown += PAGE; renderResults(); });
  animateBars(box);
  wirePopovers(box);
}

// ---------- Opportunity card: only what's needed to decide whether to look closer ----------

const isTaken = (o) => o.status === 'ADOPTED' || o.status === 'OPENED';

function cardAction(o) {
  if (o.unlocked) return `<a class="btn btn-sm" href="projects.html">${esc(t('inv.inProjects'))}</a>`;
  if (isTaken(o)) return `<span class="badge badge-sand">${esc(t('inv.taken'))}</span>`;
  if (!o.canUnlock) return `<span class="small muted">${esc(verified ? t('disc.ownIdea') : t('inv.afterVerify'))}</span>`;
  if (o.status === 'PUBLISHED') return earlyStart(`unlock.html?id=${o.id}`, { small: true });
  return `<a class="btn btn-sm" href="unlock.html?id=${o.id}">${esc(t('inv.unlock'))}</a>`;
}

function oppCard(o, { reason = null } = {}) {
  const where = o.teaser ? `${o.neighborhoodName}, ${o.city}` : `${o.neighborhoodName}, ${o.city}`;
  const p = Math.max(0, Math.min(100, o.votePercent || 0));
  const cap = o.capitalMin != null ? `${COMPACT.format(Number(o.capitalMin))} – ${sarShort(o.capitalMax ?? o.capitalMin)}` : t('bm.notEstimated');
  return `
    <article class="opp${p >= 100 ? ' is-ready' : ''}" id="opp-${o.id}">
      <header class="opp-head">
        ${catTile(o.category)}
        <div class="opp-name">
          <strong>${o.teaser ? `<span class="lock-ic" title="${esc(t('teaser.badge'))}">${icon.lock}</span>` : ''}<span dir="auto">${esc(ideaName(o))}</span></strong>
          <span>${esc(isolate(where))}${o.distanceKm != null ? ` · ${esc(t('pref.away', { d: fmt.km(o.distanceKm) }))}` : ''}</span>
        </div>
        ${statusBadge(o.status)}
      </header>
      ${fitBadge(o.budgetFit)}
      ${reason ? `<p class="opp-reason">${icon.spark}<span>${esc(reason)}</span></p>` : ''}
      <div class="opp-votes">
        <div class="row" style="justify-content:space-between;gap:8px">
          <strong class="num">${esc(t('opp.votes', { v: fmt.num(o.votesCount), n: fmt.num(o.membershipThreshold) }))}</strong>
          ${o.votesLast7Days ? `<span class="trend" title="${esc(t('teaser.momentumLong'))}">+${fmt.num(o.votesLast7Days)} ${esc(t('disc.thisWeek'))}</span>` : ''}
        </div>
        ${progressBar(p, t('common.voteTarget'))}
      </div>
      ${scoreMeter(o.checkScore, {}, { compact: true })}
      <dl class="opp-facts">
        <div><dt>${esc(t('unlock.capital'))}</dt><dd class="num">${esc(cap)}</dd></div>
        <div><dt>${esc(t('opp.competitors'))}</dt><dd class="num">${o.competitorsCount != null ? fmt.num(o.competitorsCount) : '—'}</dd></div>
      </dl>
      <footer class="opp-actions">
        <a class="btn btn-secondary btn-sm" href="idea.html?id=${o.id}">${esc(t('common.viewDetails'))}</a>
        ${cardAction(o)}
      </footer>
    </article>`;
}

// ---------- AI search: a scoped assistant above every step ----------

const form = $('#ai-form');
const input = $('#ai-q');
$('.ai-chips').addEventListener('click', (e) => {
  const b = e.target.closest('[data-suggest]');
  if (!b) return;
  input.value = b.dataset.suggest;
  form.requestSubmit();
});
form.addEventListener('submit', (e) => {
  e.preventDefault();
  const query = input.value.trim();
  if (query.length < 2) {
    input.focus();
    toast(t('ai.tooShort'), 'error');
    return;
  }
  withLoading(form.querySelector('.ai-go'), () => runSearch(query));
});

async function runSearch(query) {
  // The search uses the current step's place and budget, so it never returns something outside them
  const s = parseHash();
  const filters = {};
  if (s.view === 'results' || s.view === 'city') {
    if (s.hood) filters.neighborhoodId = s.hood; else if (s.city) filters.city = s.city;
    if (s.min !== null) filters.budgetMin = s.min;
    if (s.max !== null) filters.budgetMax = s.max;
  }
  view.state = { city: s.city, hood: s.hood };
  $('#steps').hidden = true;
  stage.innerHTML = `
    <section class="ai-panel">
      <a class="back-link" href="${esc(location.hash || '#/')}" id="ai-back">${icon.back} ${esc(t('disc.backToSteps'))}</a>
      <div class="ai-answer is-loading">
        <span class="ai-avatar" aria-hidden="true">${icon.spark}</span>
        <div class="ai-body"><span class="ai-meta">${esc(t('ai.reading'))}</span><div class="ai-lines"><i></i><i></i><i style="width:60%"></i></div></div>
      </div>
      <div class="opp-grid">${skeletonCards(3, 260)}</div>
    </section>`;
  $('#ai-back').addEventListener('click', (e) => { e.preventDefault(); route(); });
  let res;
  try {
    res = await api.post('explore/ai-search', { query, ...filters });
  } catch (err) {
    stage.querySelector('.ai-answer').outerHTML = errorBox(err);
    stage.querySelector('.opp-grid').remove();
    return;
  }
  const results = res.results || [];
  const scope = filters.city || filters.neighborhoodId || filters.budgetMin != null || filters.budgetMax != null;
  stage.querySelector('.ai-answer').outerHTML = `
    ${res.summary ? `<div class="ai-block ai-answer-block">
      <span class="ai-block-label">${icon.spark}${esc(t('ai.blockLabel'))}</span>
      <p class="ai-summary" id="ai-summary"></p>
    </div>` : ''}
    <p class="small muted">${esc(t('ai.answerFor', { q: res.query }))}${scope ? ` · ${esc(t('disc.aiScoped'))}` : ''}${res.aiUsed ? '' : ` · ${esc(t('ai.badgeKeywords'))}`}</p>`;
  stage.querySelector('.opp-grid').outerHTML = results.length
    ? `<div class="opp-grid">${results.map((o) => oppCard(o, { reason: o.aiReason })).join('')}</div>
       <div class="propose-hint">${icon.spark}<span>${esc(t('disc.notHere'))}</span><a class="btn btn-ghost btn-sm" href="${esc(proposeHref())}">${esc(t('ai.emptyCta'))}</a></div>`
    : noResults(false);
  if (res.summary) typeText($('#ai-summary'), res.summary);
  animateBars(stage);
  wirePopovers(stage);
}

function typeText(el, text) {
  if (reduceMotion) { el.textContent = text; return; }
  let i = 0;
  el.classList.add('typing');
  const step = () => {
    i = Math.min(text.length, i + 3);
    el.textContent = text.slice(0, i);
    if (i < text.length) setTimeout(step, 16);
    else el.classList.remove('typing');
  };
  step();
}

// ---------- Router ----------

function route() {
  const state = parseHash();
  $('#steps').hidden = false;
  if (state.view === 'city' && state.city) cityStep(state);
  else if (state.view === 'results') resultsStep(state);
  else startStep();
}
window.addEventListener('hashchange', () => {
  route();
  window.scrollTo({ top: $('#steps').offsetTop - 90, behavior: reduceMotion ? 'auto' : 'smooth' });
});
route();
