// "For you" with the map: the investor's home. The list and the map show the same opportunities.
// "Your fit" = the ones that match the investor's preferences (budget, fields, areas); "All" = every
// opportunity, with field and budget filters. Zoomed out, the map groups pins by city.
import { api } from '../api.js';
import { $, $$, esc, guard, t, emptyState, skeletonCards, animateBars, errorMessage, icon, LABELS, FIELD_CATEGORIES, BUDGET_RANGES, budgetRange, csv, categoryLabel, cityLabel } from '../ui.js';
import { tn } from '../i18n.js';
import { oppCard, isTaken } from '../opp-card.js';
import { prefsSummary, hasPrefs, loadNeighborhoods } from '../investor-prefs.js';
import { createMap, bubbleIcon, pinIcon, keyboardMarker, SAUDI_BOUNDS } from '../maps.js';

const user = await guard(['INVESTOR']);
const app = $('#app');
const firstName = (user.fullName || '').split(/\s+/)[0];
const CITY_ZOOM = 10; // below this, pins are grouped by city

const state = { mode: location.hash === '#all' ? 'all' : 'mine', field: '', budget: '', all: [], open: [], relaxed: false };

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t('fy.hello', { name: firstName }))}</span>
      <h1 id="fy-title">${t('fy.title')}</h1>
      <p class="prefs-line" id="prefs-line"></p>
    </div>
    <div class="segmented" role="group" aria-label="${esc(t('fy.modes'))}">
      <button type="button" data-mode="mine" aria-pressed="true">${esc(t('fy.modeMine'))}</button>
      <button type="button" data-mode="all" aria-pressed="false">${esc(t('fy.modeAll'))}</button>
    </div>
  </div>
  <div class="fy-layout">
    <div class="stack fy-list" style="gap:24px">
      <form class="map-filters hidden" id="filters" aria-label="${esc(t('map.filters'))}">
        <div class="field"><label for="f-field">${esc(t('map.field'))}</label>
          <select class="select" id="f-field"><option value="">${esc(t('map.any'))}</option>${Object.entries(LABELS.field).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select></div>
        <div class="field"><label for="f-budget">${esc(t('map.budget'))}</label>
          <select class="select" id="f-budget"><option value="">${esc(t('map.any'))}</option>${Object.entries(LABELS.budget).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select></div>
      </form>
      <div id="list" class="stack" style="gap:28px"><div class="opp-grid">${skeletonCards(2, 260)}</div></div>
    </div>
    <div class="fy-map-wrap">
      <div class="map fy-map" id="map" aria-label="${esc(t('inv.mapLabel'))}"></div>
      <button type="button" class="btn btn-secondary btn-sm fy-map-reset hidden" id="map-reset">${esc(t('fy.mapAll'))}</button>
    </div>
  </div>`;

const map = createMap($('#map'), { zoom: 5, minZoom: 4 });
map.fitBounds(SAUDI_BOUNDS);
const layer = L.layerGroup().addTo(map);
const markers = new Map(); // opportunity id → marker (only when pins are shown)
let shown = [];

// ---------- Matching (Your fit) ----------

function matcher(u) {
  const areas = new Set(csv(u.investmentAreas).map(Number));
  const cats = new Set([
    ...csv(u.investmentFields).flatMap((f) => FIELD_CATEGORIES[f] || []),
    ...csv(u.investmentFieldsOtherCategories),
  ]);
  // Words from "something else", matched against the title and category only when the AI couldn't map them
  const words = csv(u.investmentFieldsOtherCategories).length ? []
    : (u.investmentFieldsOther || '').toLowerCase().split(/[\s,،]+/).filter((w) => w.length > 2);
  const range = budgetRange(u);

  const areaOk = (o) => !areas.size || areas.has(o.neighborhoodId);
  const fieldOk = (o) => {
    if (!cats.size && !words.length) return true;
    if (cats.has(o.category)) return true;
    const text = `${o.title} ${o.category} ${categoryLabel(o.category)}`.toLowerCase();
    return words.some((w) => text.includes(w));
  };
  const budgetOk = (o) => fitsBudget(o, range);
  return { areaOk, fieldOk, budgetOk };
}

// Capital unknown → keep it; otherwise the opportunity's range must overlap the budget
function fitsBudget(o, range) {
  if (!range || o.estimatedCapitalMin == null) return true;
  const [lo, hi] = range;
  const oMax = o.estimatedCapitalMax ?? o.estimatedCapitalMin;
  return (hi == null || Number(o.estimatedCapitalMin) <= hi) && (lo == null || Number(oMax) >= lo);
}

/** The strict matches, or (when none) the closest ones by dropping area, then budget. */
function pickMine(list, u) {
  if (!hasPrefs(u)) return { items: list, relaxed: false };
  const { areaOk, fieldOk, budgetOk } = matcher(u);
  const tries = [(o) => areaOk(o) && fieldOk(o) && budgetOk(o), (o) => fieldOk(o) && budgetOk(o), (o) => fieldOk(o), () => true];
  for (let i = 0; i < tries.length; i += 1) {
    const found = list.filter(tries[i]);
    if (found.length) return { items: found, relaxed: i > 0 };
  }
  return { items: [], relaxed: false };
}

function pickAll(list) {
  const range = state.budget ? BUDGET_RANGES[state.budget] : null;
  return list.filter((o) => (!state.field || (FIELD_CATEGORIES[state.field] || []).includes(o.category)) && fitsBudget(o, range));
}

// ---------- List ----------

function drawList() {
  const mine = state.mode === 'mine';
  const picked = mine ? pickMine(state.open, user) : { items: pickAll(state.all), relaxed: false };
  shown = picked.items;
  const byStatus = (s) => shown.filter((o) => (s === 'TAKEN' ? isTaken(o) : o.status === s && !isTaken(o)));
  const ready = byStatus('COMPLETED').sort((a, b) => (b.gapScore ?? -1) - (a.gapScore ?? -1) || b.votePercent - a.votePercent);
  const rising = byStatus('PUBLISHED').sort((a, b) => b.votePercent - a.votePercent);
  const taken = mine ? [] : byStatus('TAKEN');

  const section = (title, list) => (list.length ? `
    <section class="stack" style="gap:14px" aria-label="${esc(title)}">
      <h2 class="section-title">${esc(title)} <span class="count">${list.length}</span></h2>
      <div class="opp-grid">${list.map((o) => oppCard(o)).join('')}</div>
    </section>` : '');

  const box = $('#list');
  if (!shown.length) {
    box.innerHTML = state.all.length && !mine
      ? emptyState(t('ask.none'), t('ask.noneText'))
      : emptyState(t('fy.empty'), t('fy.emptyText'));
  } else {
    box.innerHTML = `
      ${picked.relaxed ? `<div class="notice notice-info">${icon.info}<span>${esc(t('fy.relaxed'))}</span></div>` : ''}
      ${section(t('fy.sectionReady'), ready)}
      ${section(t('fy.sectionRising'), rising)}
      ${section(t('fy.sectionTaken'), taken)}`;
  }
  animateBars(box);
  // Card ↔ pin: hovering or focusing a card lights its pin
  $$('.opp-card', box).forEach((card) => {
    const id = Number(card.id.replace('opp-', ''));
    const on = (active) => markers.get(id)?.getElement()?.querySelector('.pin')?.classList.toggle('active', active);
    card.addEventListener('mouseenter', () => on(true));
    card.addEventListener('mouseleave', () => on(false));
    card.addEventListener('focusin', () => on(true));
    card.addEventListener('focusout', () => on(false));
  });
}

// ---------- Map ----------

const located = () => shown.filter((o) => o.latitude != null);

function fitToShown() {
  const pts = located().map((o) => [o.latitude, o.longitude]);
  if (pts.length) map.flyToBounds(L.latLngBounds(pts).pad(0.25), { duration: 0.8, maxZoom: 14 });
  else map.flyToBounds(SAUDI_BOUNDS, { duration: 0.8 });
}

function drawMarkers() {
  layer.clearLayers();
  markers.clear();
  const pts = located();
  const cities = [...new Set(pts.map((o) => o.city))];
  $('#map-reset').classList.toggle('hidden', map.getZoom() < CITY_ZOOM || cities.length < 2);

  if (map.getZoom() < CITY_ZOOM && cities.length > 1) {
    // Zoomed out: one bubble per city, tap to zoom in
    cities.forEach((city) => {
      const list = pts.filter((o) => o.city === city);
      const lat = list.reduce((s, o) => s + o.latitude, 0) / list.length;
      const lng = list.reduce((s, o) => s + o.longitude, 0) / list.length;
      keyboardMarker(L.marker([lat, lng], { icon: bubbleIcon(list.length) }), `${cityLabel(city)}, ${tn(list.length, 'count.opps')}`)
        .bindTooltip(cityLabel(city), { className: 'nsj-label', direction: 'bottom', permanent: true, offset: [0, 18] })
        .on('click', () => map.flyToBounds(L.latLngBounds(list.map((o) => [o.latitude, o.longitude])).pad(0.3), { duration: 0.9, maxZoom: 15 }))
        .addTo(layer);
    });
    return;
  }
  pts.forEach((o) => {
    const kind = isTaken(o) ? 'muted' : o.status === 'COMPLETED' ? 'in-range' : '';
    const marker = keyboardMarker(L.marker([o.latitude, o.longitude], { icon: pinIcon(kind) }), o.title)
      .bindTooltip(`<strong>${esc(o.title)}</strong><br>${esc(o.neighborhoodName || '')} · ${esc(t('inv.ofTarget', { p: o.votePercent }))}`, { className: 'nsj-tip', direction: 'top' })
      .on('click', () => {
        const card = $(`#opp-${o.id}`);
        if (!card) return;
        card.scrollIntoView({ behavior: 'smooth', block: 'center' });
        $$('.opp-card.active').forEach((c) => c.classList.remove('active'));
        card.classList.add('active');
      })
      .addTo(layer);
    markers.set(o.id, marker);
  });
}
map.on('zoomend', drawMarkers);
$('#map-reset').addEventListener('click', fitToShown);

// ---------- Modes and filters ----------

function setMode(mode) {
  state.mode = mode;
  $$('[data-mode]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.mode === mode)));
  $('#filters').classList.toggle('hidden', mode !== 'all');
  $('#prefs-line').classList.toggle('hidden', mode !== 'mine');
  $('#fy-title').innerHTML = mode === 'mine' ? t('fy.title') : t('map.title');
  history.replaceState(null, '', mode === 'all' ? '#all' : location.pathname + location.search);
  refresh();
}

function refresh() {
  drawList();
  drawMarkers();
  fitToShown();
}

$$('[data-mode]').forEach((b) => b.addEventListener('click', () => { if (state.mode !== b.dataset.mode) setMode(b.dataset.mode); }));
$$('#filters select').forEach((sel) => sel.addEventListener('change', () => {
  state.field = $('#f-field').value;
  state.budget = $('#f-budget').value;
  refresh();
}));

// ---------- Load ----------

async function load() {
  try {
    state.all = (await api.get('explore/search', { limit: 100 })).filter((o) => !o.unlocked);
  } catch (err) {
    $('#list').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  // "Your fit" only lists what can still be acted on; unlocked ones live in My projects
  state.open = state.all.filter((o) => !isTaken(o) && (o.status === 'COMPLETED' || o.status === 'PUBLISHED'));

  const hoods = await loadNeighborhoods().catch(() => []);
  const parts = prefsSummary(user, hoods);
  $('#prefs-line').innerHTML = hasPrefs(user) && parts.length
    ? `<span class="muted">${esc(t('fy.basedOn'))}</span> ${parts.map((p) => `<span class="chip chip-static chip-sm">${esc(p)}</span>`).join(' ')} <a href="account.html#prefs">${esc(t('fy.edit'))}</a>`
    : `<span class="muted">${esc(t('fy.noPrefs'))}</span> <a href="onboarding.html">${esc(t('fy.setPrefs'))}</a>`;

  setMode(state.mode);
}

load();
