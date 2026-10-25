// Resident home: map centered on the user with the 2 km voting radius.
// Pins show titles only; vote counts are never shown to residents.
import { api, session } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, withLoading, errorMessage, statusBadge, emptyState, skeletonCards, categoryLabel, t } from '../ui.js';
import { tn } from '../i18n.js';
import { createMap, locate, lastLocation, pinIcon, meIcon, keyboardMarker } from '../maps.js';

const VOTING_RADIUS_KM = 2;
const user = await guard(['RESIDENT']);

const app = $('#app');
app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t('res.hello', { name: user.fullName.split(' ')[0] }))}</span>
      <h1>${t('res.title')}</h1>
      <p class="muted">${esc(t('res.subtitle', { km: VOTING_RADIUS_KM }))}</p>
    </div>
    <div class="row">
      <button type="button" class="btn btn-secondary" id="relocate" data-loading="${esc(t('loc.locating'))}">${icon.pin} ${esc(t('res.updateLocation'))}</button>
      <a class="btn" href="propose.html">${icon.spark} ${esc(t('nav.propose'))}</a>
    </div>
  </div>
  <div class="split">
    <div class="stack" style="gap:12px">
      <div class="map map-tall" id="map" aria-label="${esc(t('res.mapLabel'))}">
        <div class="map-legend" aria-hidden="true">
          <span><i style="background:var(--green-800)"></i>${esc(t('res.legendCan'))}</span>
          <span><i style="background:var(--green-600)"></i>${esc(t('res.legendVoted'))}</span>
          <span><i style="background:#9C9384"></i>${esc(t('res.legendView'))}</span>
          <span><i style="background:#2B6CB0"></i>${esc(t('res.legendYou'))}</span>
        </div>
      </div>
    </div>
    <div class="stack" style="gap:14px">
      <div class="row" style="justify-content:space-between">
        <div class="segmented" role="group" aria-label="${esc(t('res.filter'))}">
          <button type="button" data-filter="all" aria-pressed="true">${esc(t('res.all'))}</button>
          <button type="button" data-filter="range" aria-pressed="false">${esc(t('res.inRadius'))}</button>
          <button type="button" data-filter="voted" aria-pressed="false">${esc(t('res.voted'))}</button>
        </div>
        <span class="small muted" id="count" aria-live="polite"></span>
      </div>
      <div class="stack" id="list" style="gap:12px">${skeletonCards(3, 130)}</div>
    </div>
  </div>`;

const map = createMap($('#map'), { zoom: 14 });
const pins = L.layerGroup().addTo(map);
let radius = null;
let me = null;
let ideas = [];
let filter = 'all';
let origin = null;
const markers = new Map();

function tipText(idea) {
  if (idea.mine) return t('res.yourIdea');
  if (idea.voted) return t('res.youVoted');
  if (['COMPLETED', 'ADOPTED', 'OPENED'].includes(idea.status)) return t(`status.${idea.status}`);
  if (idea.canVote) return t('res.canVote');
  if (!idea.inRange) return t('res.outside', { km: VOTING_RADIUS_KM });
  return t('res.legendView');
}

function pinKind(idea) {
  if (idea.voted) return 'voted';
  if (idea.canVote) return 'in-range';
  return 'muted';
}

function renderMap() {
  pins.clearLayers();
  markers.clear();
  ideas.forEach((idea) => {
    const m = keyboardMarker(L.marker([idea.latitude, idea.longitude], { icon: pinIcon(pinKind(idea)) }), idea.title)
      .bindTooltip(`<strong>${esc(idea.title)}</strong><br>${esc(tipText(idea))}`, { className: 'nsj-tip', direction: 'top' })
      .on('click', () => focusCard(idea.id))
      .addTo(pins);
    markers.set(idea.id, m);
  });
}

function card(idea) {
  const voteBtn = idea.canVote
    ? `<button type="button" class="btn btn-sm" data-vote="${idea.id}" data-loading="${esc(t('res.voting'))}">${esc(t('res.vote'))}</button>`
    : `<button type="button" class="btn btn-sm" disabled title="${esc(tipText(idea))}">${idea.voted ? `${icon.check} ${esc(t('res.votedBtn'))}` : esc(t('res.vote'))}</button>`;
  return `
    <article class="idea-card" id="idea-${idea.id}" data-id="${idea.id}">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:2px">
          <span class="title">${esc(idea.title)}</span>
          <span class="meta"><span>${esc(categoryLabel(idea.category))}</span><span>${esc(idea.neighborhoodName)}</span><span>${esc(t('res.away', { d: fmt.km(idea.distanceKm) }))}</span></span>
        </div>
        ${idea.mine ? `<span class="badge badge-sand">${esc(t('res.yourIdea'))}</span>` : statusBadge(idea.status)}
      </div>
      <span class="small ${idea.canVote ? '' : 'muted'}" style="${idea.canVote ? 'color:var(--green-800);font-weight:600' : ''}">${esc(tipText(idea))}</span>
      <div class="actions">
        ${voteBtn}
        <a class="btn btn-secondary btn-sm" href="idea.html?id=${idea.id}">${esc(t('common.viewDetails'))}</a>
      </div>
    </article>`;
}

function renderList() {
  const shown = ideas.filter((i) => filter === 'all' || (filter === 'range' && i.inRange) || (filter === 'voted' && i.voted));
  $('#count').textContent = tn(shown.length, 'count.ideas');
  $('#list').innerHTML = shown.length
    ? shown.map(card).join('')
    : emptyState(
      filter === 'voted' ? t('res.noVotes') : t('res.nothingNearby'),
      filter === 'voted' ? t('res.noVotesText') : t('res.nothingNearbyText'),
      `<a class="btn btn-sm" href="propose.html">${esc(t('nav.propose'))}</a>`,
    );
  $$('[data-vote]').forEach((btn) => btn.addEventListener('click', () => vote(Number(btn.dataset.vote), btn)));
  $$('.idea-card').forEach((el) => {
    el.addEventListener('mouseenter', () => highlight(Number(el.dataset.id)));
  });
}

function highlight(id) {
  markers.forEach((m, key) => {
    m.getElement()?.querySelector('.pin')?.classList.toggle('active', key === id);
    if (key === id) m.openTooltip(); else m.closeTooltip();
  });
}

function focusCard(id) {
  $$('.idea-card').forEach((el) => el.classList.toggle('active', Number(el.dataset.id) === id));
  const el = $(`#idea-${id}`);
  if (!el) {
    filter = 'all';
    $$('[data-filter]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === 'all')));
    renderList();
  }
  $(`#idea-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  highlight(id);
}

async function vote(id, btn) {
  await withLoading(btn, async () => {
    try {
      const body = origin ? { latitude: origin.lat, longitude: origin.lng } : {};
      await api.post(`discover/vote/${id}`, body);
      const idea = ideas.find((i) => i.id === id);
      Object.assign(idea, { voted: true, canVote: false });
      toast(t('res.voteDone'), 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  });
  renderMap();
  renderList();
  focusCard(id);
}

function drawMe(loc) {
  me?.remove();
  radius?.remove();
  me = L.marker([loc.lat, loc.lng], { icon: meIcon(), interactive: false, keyboard: false }).addTo(map);
  radius = L.circle([loc.lat, loc.lng], {
    radius: VOTING_RADIUS_KM * 1000, color: '#2F5444', weight: 1.5, fillColor: '#5A7D66', fillOpacity: 0.08, dashArray: '6 6',
  }).addTo(map);
  map.fitBounds(radius.getBounds(), { padding: [30, 30] });
}

async function load(loc) {
  try {
    ideas = await api.get('discover/map', loc ? { lat: loc.lat, lng: loc.lng } : null);
    renderMap();
    renderList();
  } catch (err) {
    $('#list').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
  }
}

async function useBrowserLocation() {
  const loc = await locate();
  origin = loc;
  await api.put('auth/location', { latitude: loc.lat, longitude: loc.lng }).then((u) => session.setUser(u)).catch(() => {});
  drawMe(loc);
  await load(loc);
}

$('#relocate').addEventListener('click', (e) => withLoading(e.currentTarget, () => useBrowserLocation().catch((err) => toast(err.message, 'error'))));
$$('[data-filter]').forEach((b) => b.addEventListener('click', () => {
  filter = b.dataset.filter;
  $$('[data-filter]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
  renderList();
}));

// Start from this tab's location, then the saved one, then ask the browser
const start = lastLocation() || (user.latitude != null ? { lat: user.latitude, lng: user.longitude } : null);
if (start) {
  origin = start;
  drawMe(start);
  load(start);
} else {
  useBrowserLocation().catch(async (err) => {
    // No location: fall back to the neighborhood center if the server knows it
    toast(err.message, 'error');
    try {
      ideas = await api.get('discover/map');
      renderMap();
      renderList();
      if (ideas.length) map.fitBounds(ideas.map((i) => [i.latitude, i.longitude]), { padding: [40, 40], maxZoom: 15 });
    } catch (e2) {
      $('#map').insertAdjacentHTML('beforeend', `<div class="map-overlay"><div class="stack" style="align-items:center">${icon.map}<h3 style="font-family:var(--font-display);font-size:24px;font-weight:400">${esc(t('res.shareTitle'))}</h3><p class="muted">${esc(t('res.shareText', { km: VOTING_RADIUS_KM }))}</p><button class="btn" id="ask-loc" type="button">${esc(t('loc.useMine'))}</button></div></div>`);
      $('#ask-loc').onclick = () => useBrowserLocation().then(() => $('.map-overlay')?.remove()).catch((e3) => toast(e3.message, 'error'));
      $('#list').innerHTML = emptyState(t('res.locationNeeded'), errorMessage(e2));
    }
  });
}
