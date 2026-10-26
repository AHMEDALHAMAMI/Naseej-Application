// Propose an idea, one small step at a time:
//   1 What is missing → 2 Where → 3 Why → 4 AI check → 5 Review → publish, edit and recheck, or cancel.
// The AI check never publishes: a passing idea waits as a private draft until its author presses «انشر الفكرة».
// Residents place the idea within 5 km of a saved home; investors can place it anywhere in Saudi Arabia.
// What the person typed is kept (sessionStorage) when they go back, switch language or reload.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, withLoading, applyFieldErrors, errorMessage, CATEGORIES, categoryLabel, modal, params, isolate, animateBars, t } from '../ui.js';
import { serverText } from '../i18n.js';
import { createMap, locate, pinIcon, RIYADH, SAUDI_BOUNDS, CITIES } from '../maps.js';
import { stepper, aiBlock, scoreMeter, wirePopovers, catTile, actionBar } from '../components.js';

const RESIDENT_RADIUS_KM = 5;
const STORE = 'naseej.proposeDraft';

const user = await guard(['RESIDENT', 'INVESTOR']);
const isInvestor = user.role === 'INVESTOR';
const app = $('#app');

// ---------- State ----------

const blank = () => ({ step: 0, title: '', category: '', description: '', point: null, neighborhoodId: null, city: '', ideaId: null });
let draft = blank();
try { Object.assign(draft, JSON.parse(sessionStorage.getItem(STORE) || 'null') || {}); } catch { /* storage blocked */ }
const save = () => { try { sessionStorage.setItem(STORE, JSON.stringify(draft)); } catch { /* ignore */ } };
const forget = () => { try { sessionStorage.removeItem(STORE); } catch { /* ignore */ } };

// Coming from an investor search with no results: the category, city and neighborhood are filled in
const q = params();
if (q.get('category') && CATEGORIES.includes(q.get('category'))) draft.category = q.get('category');
if (q.get('city')) draft.city = q.get('city');
if (q.get('hood')) draft.neighborhoodId = Number(q.get('hood')) || null;
if (q.get('new') === '1') draft = { ...blank(), category: draft.category, city: draft.city, neighborhoodId: draft.neighborhoodId };

const homes = isInvestor ? [] : ((await api.get('account/addresses').catch(() => null))?.addresses || [])
  .map((a) => ({ lat: a.latitude, lng: a.longitude, label: a.label, hood: a.neighborhoodName }));
if (!isInvestor && !draft.point && homes[0]) draft.point = { lat: homes[0].lat, lng: homes[0].lng };
// A pin saved before the resident added or moved a home may be outside every home's area now:
// start again from the primary home, so the idea goes where they live today
if (!isInvestor && homes[0] && draft.point && outsideHome(draft.point)) {
  draft.point = { lat: homes[0].lat, lng: homes[0].lng };
  draft.hoodName = null;
  save();
}
let neighborhoods = [];

const LABELS = [t('prop.s.what'), t('prop.s.where'), t('prop.s.why'), t('prop.s.check'), t('prop.s.review')];

app.innerHTML = `
  <div class="flow">
    <header class="flow-head">
      <span class="eyebrow">${esc(isInvestor ? t('prop.eyebrowInv') : t('prop.eyebrow'))}</span>
      <h1 tabindex="-1" id="flow-title">${t('prop.title')}</h1>
    </header>
    <div id="steps"></div>
    <div id="stage"></div>
  </div>`;
const stage = $('#stage');

function setStep(n, { focus = true } = {}) {
  draft.step = n;
  save();
  $('#steps').innerHTML = stepper(LABELS, n);
  if (focus) {
    window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    $('#flow-title').focus({ preventScroll: true });
  }
}

// Each history entry knows how many steps this page pushed before it (depth), so "Back" can tell whether the
// previous entry is one of our steps or the page the person came from
const here = () => location.pathname + location.search;
const depth = () => Number(history.state?.depth) || 0;
const push = (n) => history.pushState({ step: n, depth: depth() + 1 }, '', here());
let published = false;

// The browser's back button moves one step back inside the flow
window.addEventListener('popstate', (e) => {
  const n = e.state?.step;
  if (n === undefined || published) return;
  if (n <= 2) [whatStep, whereStep, whyStep][n]({ push: false });
  else if (n === 4 && draft.ideaId) {
    api.get(`discover/idea/${draft.ideaId}`).then((idea) => reviewView(idea, { history: 'keep' })).catch(() => whyStep({ push: false }));
  }
});

// The "Back" button: the browser's back when the step before was pushed here, otherwise draw it in place.
// (A draft restored after a reload has no earlier steps in the history; going back must not leave the page.)
function stepBack(from) {
  if (depth() > 0) { history.back(); return; }
  const n = Math.max(0, from - 1);
  history.replaceState({ step: n, depth: 0 }, '', here());
  [whatStep, whereStep, whyStep][n]({ push: false });
}

function fieldError(input, text) {
  input.setAttribute('aria-invalid', 'true');
  const box = input.closest('.field')?.querySelector('.error');
  if (box) box.textContent = text;
  input.focus();
}

// ---------- Step 1: what is missing ----------

function whatStep({ push: doPush = true } = {}) {
  setStep(0);
  if (doPush) push(0);
  stage.innerHTML = `
    <form id="f-what" class="flow-card" novalidate>
      <div class="field">
        <label for="title">${esc(t('prop.q.what'))}</label>
        <input class="input input-lg" id="title" name="title" maxlength="120" autocomplete="off" placeholder="${esc(t('prop.titlePh'))}" value="${esc(draft.title)}" required>
        <span class="hint">${esc(t('prop.titleHint'))}</span>
        <span class="error" aria-live="polite"></span>
      </div>
      <fieldset class="field cat-pick">
        <legend class="label">${esc(t('prop.category'))}</legend>
        <div class="cat-grid" role="radiogroup" aria-label="${esc(t('prop.category'))}">
          ${CATEGORIES.map((c) => `
            <label class="cat-opt">
              <input type="radio" name="category" value="${esc(c)}"${draft.category === c ? ' checked' : ''}>
              ${catTile(c, { size: 20 })}<span>${esc(categoryLabel(c))}</span>
            </label>`).join('')}
        </div>
        <span class="error" aria-live="polite"></span>
      </fieldset>
      ${actionBar(`<span></span><button class="btn" type="submit">${esc(t('common.next'))} ${icon.arrow}</button>`)}
    </form>`;
  $('#f-what').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.currentTarget;
    applyFieldErrors(f, null);
    draft.title = f.title.value.trim();
    draft.category = f.category.value || '';
    save();
    if (draft.title.length < 3) return fieldError(f.title, t('prop.titleShort'));
    if (!draft.category) return fieldError(f.querySelector('input[name="category"]'), t('prop.pickCategory'));
    whereStep();
  });
  $('#f-what').addEventListener('input', (e) => { draft[e.target.name] = e.target.value; save(); });
}

// ---------- Step 2: where ----------

let map;
let marker;

function whereStep({ push: doPush = true } = {}) {
  setStep(1);
  if (doPush) push(1);
  stage.innerHTML = `
    <form id="f-where" class="flow-card" novalidate>
      <div class="stack" style="gap:6px">
        <h2 class="flow-q">${esc(t('prop.q.where'))}</h2>
        <p class="muted">${esc(isInvestor ? t('prop.whereInv') : t('prop.whereRes', { km: RESIDENT_RADIUS_KM }))}</p>
      </div>
      ${homes.length > 1 ? `
      <div class="chips" role="group" aria-label="${esc(t('prop.yourHomes'))}">
        ${homes.map((h, i) => `<button type="button" class="chip" data-home="${i}">${icon.pin} ${esc(isolate(h.label || t('addr.homeN', { n: i + 1 })))} · ${esc(isolate(h.hood))}</button>`).join('')}
      </div>` : ''}
      ${isInvestor ? `
      <div class="place-search" role="search">
        <label class="sr-only" for="place-q">${esc(t('prop.searchPh'))}</label>
        <input class="input" id="place-q" type="search" autocomplete="off" placeholder="${esc(t('prop.searchPh'))}">
        <ul class="place-results" id="place-results" role="listbox" hidden></ul>
      </div>
      <div class="chips" role="group" aria-label="${esc(t('prop.jumpCity'))}">
        ${CITIES.map((c) => `<button type="button" class="chip" data-city-jump="${c.key}">${esc(t(`city.${c.key}`))}</button>`).join('')}
      </div>` : ''}
      <div class="map" id="map" style="height:380px" aria-label="${esc(t('prop.spotLabel'))}"></div>
      <div class="spot-row">
        <p class="spot" id="spot" aria-live="polite">${icon.pin}<span>${esc(isInvestor ? t('prop.anywhere') : t('prop.drag'))}</span></p>
        <button type="button" class="btn btn-secondary btn-sm" id="here" data-loading="${esc(t('loc.locating'))}">${icon.pin} ${esc(t('prop.findMe'))}</button>
      </div>
      ${isInvestor ? `
      <div class="grid grid-2" style="gap:12px">
        <div class="field"><label for="city">${esc(t('form.city'))}</label><select class="select" id="city"><option value="">${esc(t('form.chooseCity'))}</option></select></div>
        <div class="field"><label for="neighborhoodId">${esc(t('form.hood'))}</label><select class="select" id="neighborhoodId" name="neighborhoodId"><option value="">${esc(t('form.chooseHood'))}</option></select><span class="error" aria-live="polite"></span></div>
      </div>` : ''}
      ${actionBar(`<button class="btn btn-secondary" type="button" data-back>${icon.back} ${esc(t('common.back'))}</button>
        <button class="btn" type="submit">${esc(t('common.next'))} ${icon.arrow}</button>`)}
    </form>`;

  marker = null;
  map = createMap($('#map'), { center: draft.point || RIYADH, zoom: draft.point ? 15 : 12 });
  if (isInvestor && !draft.point) map.fitBounds(SAUDI_BOUNDS);
  homes.forEach((h) => L.circle([h.lat, h.lng], {
    radius: RESIDENT_RADIUS_KM * 1000, color: '#2F5444', weight: 1.4, dashArray: '6 6', fillColor: '#5A7D66', fillOpacity: 0.06, interactive: false,
  }).addTo(map));
  if (draft.point) setMarker(draft.point, true);
  map.on('click', (e) => setMarker({ lat: e.latlng.lat, lng: e.latlng.lng }, true));

  // "Find me" only moves the pin; nothing is saved until the person continues
  $('#here').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const loc = await locate();
      setMarker(loc, true);
      map.setView([loc.lat, loc.lng], 16);
    } catch (err) {
      toast(err.message, 'error');
    }
  }));
  if (isInvestor) {
    setupNeighborhoodPicker();
    setupPlaceSearch();
  }
  $$('[data-home]').forEach((b) => b.addEventListener('click', () => {
    const h = homes[Number(b.dataset.home)];
    map.setView([h.lat, h.lng], 15);
    setMarker({ lat: h.lat, lng: h.lng }, true);
  }));
  $('[data-back]').addEventListener('click', () => stepBack(1));
  $('#f-where').addEventListener('submit', (e) => {
    e.preventDefault();
    if (!draft.point) { toast(t('prop.needSpot'), 'error'); return; }
    if (isInvestor) {
      draft.neighborhoodId = Number($('#neighborhoodId').value) || null;
      save();
      if (!draft.neighborhoodId) return fieldError($('#neighborhoodId'), t('prop.pickHood'));
    }
    whyStep();
  });
}

function outsideHome(loc) {
  return !isInvestor && homes.length > 0
    && homes.every((h) => L.latLng(h.lat, h.lng).distanceTo([loc.lat, loc.lng]) > RESIDENT_RADIUS_KM * 1000);
}

function setMarker(loc, chosen) {
  if (chosen && outsideHome(loc)) {
    toast(t('prop.residentRadius', { km: RESIDENT_RADIUS_KM }), 'error');
    return;
  }
  if (chosen) { draft.point = loc; save(); }
  if (!marker) {
    marker = L.marker([loc.lat, loc.lng], { icon: pinIcon('in-range'), draggable: true, autoPan: true }).addTo(map);
    marker.on('dragend', () => {
      const p = marker.getLatLng();
      if (outsideHome({ lat: p.lat, lng: p.lng })) {
        toast(t('prop.residentRadius', { km: RESIDENT_RADIUS_KM }), 'error');
        marker.setLatLng([draft.point.lat, draft.point.lng]);
        return;
      }
      draft.point = { lat: p.lat, lng: p.lng };
      save();
      describeSpot();
    });
  } else {
    marker.setLatLng([loc.lat, loc.lng]);
  }
  if (chosen) describeSpot();
}

let spotTimer = null;
function describeSpot() {
  clearTimeout(spotTimer);
  spotTimer = setTimeout(async () => {
    if (!draft.point || !$('#spot')) return;
    try {
      const hood = await api.get('public/nearest-neighborhood', { lat: draft.point.lat, lng: draft.point.lng }, { auth: false });
      if (!$('#spot')) return;
      $('#spot').innerHTML = `${icon.pin}<span>${t('prop.inHood', { hood: `<strong>${esc(isolate(`${hood.name}, ${hood.city}`))}</strong>` })}</span>`;
      draft.hoodName = `${hood.name}, ${hood.city}`;
      save();
      if (isInvestor) {
        if (!neighborhoods.some((n) => n.id === hood.id)) neighborhoods.push(hood);
        if (![...$('#city').options].some((o) => o.value === hood.city)) {
          $('#city').insertAdjacentHTML('beforeend', `<option value="${esc(hood.city)}">${esc(hood.city)}</option>`);
        }
        $('#city').value = hood.city;
        fillHoods(hood.city, hood.id);
      }
    } catch {
      if ($('#spot')) $('#spot').innerHTML = `${icon.pin}<span>${esc(isInvestor ? t('prop.pickHood') : t('prop.ownHood'))}</span>`;
    }
  }, 350);
}

function fillHoods(city, selected) {
  const list = neighborhoods.filter((n) => n.city === city).sort((a, b) => a.name.localeCompare(b.name));
  $('#neighborhoodId').innerHTML = `<option value="">${esc(t('form.chooseHood'))}</option>`
    + list.map((n) => `<option value="${n.id}"${n.id === selected ? ' selected' : ''}>${esc(n.name)}</option>`).join('');
}

async function setupNeighborhoodPicker() {
  if (!neighborhoods.length) {
    try { neighborhoods = await api.get('neighborhoods/get-all', null, { auth: false }); } catch { neighborhoods = []; }
  }
  if (!$('#city')) return;
  const cities = [...new Set(neighborhoods.map((n) => n.city))].sort();
  $('#city').innerHTML = `<option value="">${esc(t('form.chooseCity'))}</option>` + cities.map((c) => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
  const chosen = neighborhoods.find((n) => n.id === draft.neighborhoodId);
  const city = chosen?.city || (cities.includes(draft.city) ? draft.city : '');
  if (city) { $('#city').value = city; fillHoods(city, chosen?.id); }
  if (chosen && !draft.point && chosen.latitude != null) {
    setMarker({ lat: chosen.latitude, lng: chosen.longitude }, true);
    map.setView([chosen.latitude, chosen.longitude], 15);
  }
  $('#city').addEventListener('change', () => { draft.city = $('#city').value; save(); fillHoods($('#city').value); });
  $('#neighborhoodId').addEventListener('change', () => {
    const n = neighborhoods.find((x) => x.id === Number($('#neighborhoodId').value));
    draft.neighborhoodId = n?.id || null;
    save();
    if (n && n.latitude != null) {
      setMarker({ lat: n.latitude, lng: n.longitude }, true);
      map.setView([n.latitude, n.longitude], 15);
    }
  });
}

function setupPlaceSearch() {
  const input = $('#place-q');
  const list = $('#place-results');
  let timer = null;
  let ticket = 0;
  const close = () => { list.hidden = true; list.innerHTML = ''; };
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const text = input.value.trim();
    if (text.length < 2) { close(); return; }
    timer = setTimeout(async () => {
      const mine = ++ticket;
      try {
        const results = await api.get('public/search-places', { q: text }, { auth: false });
        if (mine !== ticket) return;
        list.innerHTML = results.length
          ? results.map((r, i) => `<li role="option"><button type="button" data-i="${i}"><strong>${esc(r.name)}</strong><span>${esc(r.detail)}</span></button></li>`).join('')
          : `<li class="muted small" style="padding:10px 12px">${esc(t('prop.noPlaces'))}</li>`;
        list.hidden = false;
        list.querySelectorAll('button[data-i]').forEach((b) => b.addEventListener('click', () => {
          const r = results[Number(b.dataset.i)];
          input.value = r.name;
          close();
          map.setView([r.latitude, r.longitude], 15);
          setMarker({ lat: r.latitude, lng: r.longitude }, true);
        }));
      } catch {
        close();
      }
    }, 400);
  });
  input.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); if (e.key === 'Enter') e.preventDefault(); });
  document.addEventListener('click', (e) => { if (!e.target.closest('.place-search')) close(); });
  $$('[data-city-jump]').forEach((b) => b.addEventListener('click', () => {
    const c = CITIES.find((x) => x.key === b.dataset.cityJump);
    map.flyTo([c.lat, c.lng], 12, { duration: 0.8 });
    toast(t('prop.tapToPlace'), 'info', 2500);
  }));
}

// ---------- Step 3: why ----------

function whyStep({ push: doPush = true } = {}) {
  setStep(2);
  if (doPush) push(2);
  stage.innerHTML = `
    <form id="f-why" class="flow-card" novalidate>
      <div class="field">
        <label for="description" class="flow-q">${esc(t('prop.q.why'))}</label>
        <span class="hint">${esc(t('prop.whyHint'))}</span>
        <textarea class="textarea" id="description" name="description" maxlength="2000" rows="6" placeholder="${esc(t('prop.whyPh'))}">${esc(draft.description)}</textarea>
        <span class="hint num" id="count" aria-live="polite"></span>
        <span class="error" aria-live="polite"></span>
      </div>
      <div class="notice notice-info">${icon.spark}<span>${esc(t('prop.checkExplain'))}</span></div>
      ${actionBar(`<button class="btn btn-secondary" type="button" data-back>${icon.back} ${esc(t('common.back'))}</button>
        <button class="btn" type="submit">${icon.spark} ${esc(t('prop.check'))}</button>`)}
    </form>`;
  const area = $('#description');
  const count = () => { $('#count').textContent = t('prop.chars', { n: fmt.num(area.value.length) }); };
  count();
  area.addEventListener('input', () => { draft.description = area.value; save(); count(); });
  $('[data-back]').addEventListener('click', () => stepBack(2));
  $('#f-why').addEventListener('submit', (e) => {
    e.preventDefault();
    draft.description = area.value.trim();
    save();
    if (draft.description.length < 10) return fieldError(area, t('prop.whyShort'));
    runCheck();
  });
}

// ---------- Step 4: the AI check ----------

const CHECK_STEPS = [t('prop.step1'), t('prop.step2'), t('prop.step3'), t('prop.step4')];

function analyzingView() {
  setStep(3);
  stage.innerHTML = `
    <div class="flow-card checking" role="status" aria-live="polite">
      <div class="row"><span class="spinner" aria-hidden="true"></span><h2 class="flow-q">${esc(t('prop.checking'))}</h2></div>
      <ol class="check-steps" id="check-steps">${CHECK_STEPS.map((s) => `<li><i aria-hidden="true"></i><span>${esc(s)}</span></li>`).join('')}</ol>
      <p class="small muted">${esc(t('prop.takes'))} ${esc(t('prop.notPublic'))}</p>
    </div>`;
  let i = 0;
  const tick = () => {
    $$('#check-steps li').forEach((li, j) => { li.className = j < i ? 'done' : j === i ? 'now' : ''; });
    if (i < CHECK_STEPS.length - 1) i += 1;
  };
  tick();
  return setInterval(tick, 4500);
}

function body() {
  return {
    title: draft.title,
    category: draft.category,
    description: draft.description,
    latitude: draft.point ? draft.point.lat : null,
    longitude: draft.point ? draft.point.lng : null,
    neighborhoodId: isInvestor ? draft.neighborhoodId : null,
  };
}

async function runCheck() {
  const timer = analyzingView();
  try {
    // A draft that exists already is edited and checked again; otherwise a new one is made
    const idea = draft.ideaId
      ? await api.put(`discover/draft/${draft.ideaId}`, body())
      : await api.post('discover/propose', body());
    clearInterval(timer);
    draft.ideaId = idea.id;
    save();
    // The review gets its own history entry, so the browser's back returns to "Why"
    reviewView(idea, { history: 'push' });
  } catch (err) {
    clearInterval(timer);
    whyStep({ push: false });
    if (!applyFieldErrors($('#f-why'), err)) toast(errorMessage(err), 'error', 7000);
  }
}

// ---------- Step 5: review, then publish, edit or cancel ----------

function yourIdeaCard(idea) {
  return `<section class="source-card">
    <div class="card-head" style="margin:0"><h3>${esc(t('prop.yourIdea'))}</h3>
      <button type="button" class="btn btn-ghost btn-sm" data-edit>${esc(t('prop.edit'))}</button></div>
    <div class="row" style="gap:12px;align-items:flex-start">${catTile(idea.category)}
      <div class="stack" style="gap:2px;min-width:0"><strong dir="auto">${esc(idea.title)}</strong>
      <span class="small muted">${esc(categoryLabel(idea.category))} · ${esc(isolate(`${idea.neighborhoodName}, ${idea.city}`))}</span></div></div>
    <p class="small" dir="auto">${esc(idea.description || '')}</p>
  </section>`;
}

function competitorsCard(idea) {
  const list = idea.competitors || [];
  return `<section class="source-card">
    <h3>${esc(t('prop.fromMap'))}</h3>
    <p class="small">${esc(t('prop.competitorsWithin', { n: fmt.num(idea.competitorsCount ?? 0), d: fmt.km(idea.searchRadiusKm) }))}</p>
    ${list.length ? `<ul class="list">${list.slice(0, 6).map((c) => `<li><span dir="auto">${esc(c.name)}</span><span class="muted num">${fmt.km(c.distanceKm)}</span></li>`).join('')}</ul>`
      : `<p class="small muted">${esc(t('prop.noCompetitors'))}</p>`}
    <span class="source-note">${esc(t('check.source'))}</span>
  </section>`;
}

function aiPanel(idea) {
  const m = idea.businessModel;
  const parts = { shortage: idea.shortageScore, competition: idea.competitionScore, demand: idea.demandScore };
  return aiBlock(`
    ${scoreMeter(idea.checkScore, parts)}
    ${idea.feasibilitySummary ? `<p>${esc(idea.feasibilitySummary)}</p>` : ''}
    ${m ? `<h3>${esc(t('bm.title'))}</h3>
    <dl class="dl">
      <dt>${esc(t('bm.value'))}</dt><dd>${esc(m.valueProposition)}</dd>
      <dt>${esc(t('bm.customers'))}</dt><dd>${esc(m.targetCustomers)}</dd>
      <dt>${esc(t('bm.revenue'))}</dt><dd>${esc(m.revenueStreams)}</dd>
      <dt>${esc(t('bm.costs'))}</dt><dd>${esc(m.costStructure)}</dd>
      <dt>${esc(t('bm.capital'))}</dt><dd class="num">${m.estimatedCapitalMin != null ? `${fmt.money(m.estimatedCapitalMin)} – ${fmt.money(m.estimatedCapitalMax)}` : esc(t('bm.notEstimated'))}</dd>
    </dl>` : ''}
    <p class="score-note">${esc(t('score.indicator'))}</p>`);
}

function notices(idea) {
  return (idea.warnings || []).map((w) => `<div class="notice notice-warn">${icon.warn}<span>${esc(serverText(w))}</span></div>`).join('');
}

function reviewView(idea, { history: mode = 'replace' } = {}) {
  setStep(4);
  if (mode === 'push') push(4);
  else if (mode === 'replace') history.replaceState({ step: 4, depth: depth() }, '', here());
  if (idea.status === 'REJECTED') return rejectedView(idea);
  if (idea.status !== 'DRAFT') return publishedView(idea);

  const left = idea.rechecksLeft ?? 0;
  const stale = !!idea.checkStale;
  stage.innerHTML = `
    <div class="stack" style="gap:16px">
      <div class="review-head">
        <span class="badge badge-amber">${esc(t('status.DRAFT'))}</span>
        <h2 class="flow-q">${esc(t('prop.readyTitle'))}</h2>
        <p class="muted">${esc(t('prop.readyText'))}</p>
      </div>
      ${notices(idea)}
      ${stale ? `<div class="notice notice-warn">${icon.warn}<span>${esc(t('prop.staleText'))}</span></div>` : ''}
      <div class="review-grid">
        <div class="stack" style="gap:16px">${aiPanel(idea)}</div>
        <div class="stack" style="gap:16px">${yourIdeaCard(idea)}${competitorsCard(idea)}</div>
      </div>
      ${actionBar(`
        <button type="button" class="btn btn-ghost btn-danger-text" id="cancel">${esc(t('prop.cancelDraft'))}</button>
        <div class="row" style="gap:8px">
          <button type="button" class="btn btn-secondary" id="recheck" ${!stale && left <= 0 ? 'disabled' : ''}>${esc(stale ? t('prop.recheckNow') : t('prop.editRecheck'))}${!stale ? ` <span class="muted small">(${esc(t('prop.left', { n: left }))})</span>` : ''}</button>
          <button type="button" class="btn" id="publish" ${idea.canPublish ? '' : 'disabled'} data-loading="${esc(t('prop.publishing'))}">${icon.check} ${esc(t('prop.publish'))}</button>
        </div>`)}
    </div>`;
  wirePopovers(stage);
  animateBars(stage);
  wireReview(idea, stale);
}

function wireReview(idea, stale) {
  $$('[data-edit]').forEach((b) => b.addEventListener('click', () => whatStep()));
  $('#recheck')?.addEventListener('click', () => (stale ? runCheck() : whatStep()));
  $('#publish')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const published = await api.post(`discover/publish/${idea.id}`);
      publishedView(published);
    } catch (err) {
      toast(errorMessage(err), 'error', 7000);
    }
  }));
  $('#cancel')?.addEventListener('click', async () => {
    const ok = await modal({
      title: t('prop.cancelTitle'),
      body: `<p class="muted">${esc(t('prop.cancelText'))}</p>`,
      confirmText: t('prop.cancelConfirm'),
      cancelText: t('prop.keepDraft'),
      danger: true,
      onConfirm: () => api.del(`discover/draft/${idea.id}`),
    });
    if (ok) {
      forget();
      toast(t('prop.cancelled'), 'success');
      location.href = 'projects.html';
    }
  });
}

function rejectedView(idea) {
  const similar = idea.checkDecision === 'SIMILAR';
  const left = idea.rechecksLeft ?? 0;
  stage.innerHTML = `
    <div class="stack" style="gap:16px">
      <section class="flow-card rejected">
        <span class="badge badge-red">${icon.x} ${esc(t('status.REJECTED'))}</span>
        <h2 class="flow-q">${esc(similar ? t('prop.similarTitle') : t('prop.saturatedTitle'))}</h2>
        <p>${esc(serverText(idea.rejectionReason || ''))}</p>
        <h3 class="small-h">${esc(t('prop.nextSteps'))}</h3>
        <div class="next-steps">
          ${similar && idea.similarIdeaId ? `<a class="btn" href="idea.html?id=${idea.similarIdeaId}">${esc(t('prop.voteSimilar'))}</a>` : ''}
          ${!similar ? `<button type="button" class="btn" data-other>${esc(t('prop.tryOther'))}</button>` : ''}
          <button type="button" class="btn btn-secondary" data-edit ${left <= 0 ? 'disabled' : ''}>${esc(t('prop.editRecheck'))} <span class="muted small">(${esc(t('prop.left', { n: left }))})</span></button>
          <button type="button" class="btn btn-ghost" data-new>${esc(t('prop.startNew'))}</button>
        </div>
      </section>
      ${idea.checkScore != null ? aiBlock(`${scoreMeter(idea.checkScore, { shortage: idea.shortageScore, competition: idea.competitionScore, demand: idea.demandScore })}${idea.feasibilitySummary ? `<p>${esc(idea.feasibilitySummary)}</p>` : ''}`) : ''}
    </div>`;
  wirePopovers(stage);
  animateBars(stage);
  $('[data-edit]')?.addEventListener('click', () => whatStep());
  $('[data-other]')?.addEventListener('click', () => { draft.category = ''; save(); whatStep(); });
  $('[data-new]')?.addEventListener('click', async () => {
    // The idea that didn't pass is removed, so "My ideas" doesn't fill up with dead ends
    try { await api.del(`discover/draft/${idea.id}`); } catch { /* already gone */ }
    forget();
    draft = blank();
    if (!isInvestor && homes[0]) draft.point = { lat: homes[0].lat, lng: homes[0].lng };
    whatStep();
  });
}

function publishedView(idea) {
  forget();
  published = true;
  setStep(4, { focus: false });
  stage.innerHTML = `
    <div class="flow-card done-card" role="status">
      <span class="done-mark" aria-hidden="true">${icon.check}</span>
      <h2 class="flow-q" tabindex="-1" id="done-h">${esc(t('prop.publishedTitle'))}</h2>
      <p class="muted">${esc(isInvestor ? t('prop.publishedInv') : t('prop.liveNote'))}</p>
      ${notices(idea)}
      <div class="next-steps">
        <a class="btn" href="idea.html?id=${idea.id}">${esc(t('prop.openPage'))}</a>
        ${idea.canAdopt ? `<button type="button" class="btn btn-secondary" id="adopt">${esc(t('prop.adoptNow'))}</button>` : ''}
        ${isInvestor && !idea.canAdopt && idea.investorStatus !== 'VERIFIED' ? `<span class="badge badge-amber">${esc(t('prop.adoptAfter'))}</span>` : ''}
        <a class="btn btn-ghost" href="propose.html?new=1">${esc(t('prop.another'))}</a>
      </div>
    </div>`;
  $('#done-h').focus();
  // An investor adopts the idea they proposed right away, with no fee (the investor experience):
  // next they sign the contract
  $('#adopt')?.addEventListener('click', async () => {
    const result = await modal({
      title: t('adopt.title'),
      body: `<p class="muted">${esc(t('adopt.text'))}</p>`,
      confirmText: t('adopt.confirm'),
      onConfirm: () => api.post(`unlock/adopt/${idea.id}`),
    });
    if (result) {
      toast(serverText(result.message) || t('adopt.done'), 'success');
      location.href = result.contractId ? `contract.html?id=${result.contractId}` : 'projects.html';
    }
  });
}

// ---------- Start ----------

// "Continue" from My ideas opens the saved draft at its review
const resume = Number(q.get('draft')) || null;
if (resume) {
  try {
    const idea = await api.get(`discover/idea/${resume}`);
    Object.assign(draft, {
      ideaId: idea.id, title: idea.title, category: idea.category, description: idea.description,
      point: idea.latitude != null ? { lat: idea.latitude, lng: idea.longitude } : draft.point, neighborhoodId: idea.neighborhoodId,
    });
    save();
    reviewView(idea);
  } catch (err) {
    toast(errorMessage(err), 'error');
    whatStep({ push: false });
  }
} else if (draft.ideaId && draft.step >= 3) {
  // A reload on the review screen shows the saved draft again
  api.get(`discover/idea/${draft.ideaId}`).then(reviewView).catch(() => { draft.ideaId = null; save(); whatStep({ push: false }); });
} else if (history.state?.step !== undefined && history.state.step <= 2) {
  // A reload keeps the history entries the browser already has
  [whatStep, whereStep, whyStep][history.state.step]({ push: false });
} else {
  // Coming back to a half-written idea: put the earlier steps in the history, so the browser's back
  // button goes to "Where" and "What" instead of the page before
  const n = Math.min(draft.step || 0, 2);
  history.replaceState({ step: 0, depth: 0 }, '', here());
  for (let i = 1; i <= n; i += 1) history.pushState({ step: i, depth: i }, '', here());
  [whatStep, whereStep, whyStep][n]({ push: false });
}
