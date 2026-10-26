// Resident home: map centered on a saved home address with the 2 km voting radius (never the device's location).
// Residents with two homes switch between them. Pins show titles only; vote counts are never shown to residents.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, withLoading, errorMessage, statusBadge, emptyState, skeletonCards, categoryLabel, lifeTrack, isolate, t } from '../ui.js';
import { createMap, pinIcon, meIcon, keyboardMarker, cardPopup } from '../maps.js';

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
      <span id="addr-slot"></span>
      <a class="btn" href="propose.html">${icon.spark} ${esc(t('nav.propose'))}</a>
    </div>
  </div>
  <div id="addr-notice"></div>
  <div class="split">
    <div class="stack" style="gap:12px">
      <div class="map map-tall" id="map" aria-label="${esc(t('res.mapLabel'))}">
        <div class="map-legend" aria-hidden="true">
          <span><i style="background:var(--green-800)"></i>${esc(t('res.legendCan'))}</span>
          <span><i style="background:var(--green-600)"></i>${esc(t('res.legendVoted'))}</span>
          <span><i style="background:#9C9384"></i>${esc(t('res.legendView'))}</span>
          <span><i style="background:#C9A35A"></i>${esc(t('res.legendAdopted'))}</span>
          <span><i style="background:#2B6CB0"></i>${esc(t('res.legendYou'))}</span>
        </div>
      </div>
    </div>
    <div class="stack" style="gap:14px">
      <div class="row" style="justify-content:space-between">
        <div class="segmented" role="group" aria-label="${esc(t('res.filter'))}">
          <button type="button" data-filter="can" aria-pressed="true">${esc(t('res.tabCan'))} <span class="tab-count" data-n="can"></span></button>
          <button type="button" data-filter="voted" aria-pressed="false">${esc(t('res.voted'))} <span class="tab-count" data-n="voted"></span></button>
          <button type="button" data-filter="view" aria-pressed="false">${esc(t('res.tabView'))} <span class="tab-count" data-n="view"></span></button>
        </div>
        <span class="small muted" id="count" aria-live="polite"></span>
      </div>
      <div class="stack" id="list" style="gap:12px">${skeletonCards(3, 130)}</div>
    </div>
  </div>`;

const map = createMap($('#map'), { zoom: 14 });
const pins = L.layerGroup().addTo(map);
const homeLayer = L.layerGroup().addTo(map);
let ideas = [];
let filter = 'can';
// Three groups that never overlap: ideas you can vote on now, ones you voted for, and the rest (view only)
const groupOf = (i) => (i.canVote ? 'can' : i.voted ? 'voted' : 'view');
let addresses = [];
let current = null;
const markers = new Map();
const addressName = (a, i) => a.label || t('addr.homeN', { n: i + 1 });

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

function pinKindFor(idea) {
  if (idea.status === 'ADOPTED' || idea.status === 'OPENED') return 'adopted';
  return pinKind(idea);
}

// Small card on the map: what the idea is, where it stands, and what the resident can do
function popupHtml(idea) {
  const vote = idea.canVote
    ? `<button type="button" class="btn btn-sm" data-pop-vote="${idea.id}" data-loading="${esc(t('res.voting'))}">${esc(t('res.vote'))}</button>`
    : '';
  return `
    <div class="pop">
      <div class="pop-tags"><span class="badge badge-sand">${esc(categoryLabel(idea.category))}</span>${idea.mine ? `<span class="badge badge-sand">${esc(t('res.yourIdea'))}</span>` : statusBadge(idea.status)}</div>
      <strong class="pop-title">${esc(idea.title)}</strong>
      ${idea.summary ? `<p class="pop-text">${esc(idea.summary)}</p>` : ''}
      ${lifeTrack(idea, { compact: true })}
      <div class="pop-meta">${icon.pin}<span>${esc(idea.neighborhoodName)} · ${esc(t('res.away', { d: fmt.km(idea.distanceKm) }))}</span></div>
      <span class="pop-note${idea.canVote ? ' ok' : ''}">${esc(tipText(idea))}</span>
      <div class="pop-actions">${vote}<a class="btn btn-secondary btn-sm" href="idea.html?id=${idea.id}">${esc(t('common.viewDetails'))}</a></div>
    </div>`;
}

function renderMap() {
  pins.clearLayers();
  markers.clear();
  ideas.forEach((idea) => {
    const m = keyboardMarker(L.marker([idea.latitude, idea.longitude], { icon: pinIcon(pinKindFor(idea)), riseOnHover: true }), idea.title)
      .bindTooltip(`<strong>${esc(idea.title)}</strong>`, { className: 'nsj-tip', direction: 'top' })
      .bindPopup(cardPopup(popupHtml(idea)))
      .on('click', () => focusCard(idea.id, false))
      .addTo(pins);
    markers.set(idea.id, m);
  });
}

// Vote straight from the map card
map.on('popupopen', (e) => {
  const btn = e.popup.getElement()?.querySelector('[data-pop-vote]');
  if (btn) btn.addEventListener('click', () => vote(Number(btn.dataset.popVote), btn));
});

function card(idea) {
  const stage = idea.stage && idea.status === 'ADOPTED'
    ? `<span class="small muted">${esc(t('life.stageNow', { stage: t(`stage.${idea.stage}`) }))}</span>` : '';
  const voteBtn = idea.canVote
    ? `<button type="button" class="btn btn-sm" data-vote="${idea.id}" data-loading="${esc(t('res.voting'))}">${esc(t('res.vote'))}</button>`
    : `<button type="button" class="btn btn-sm" disabled title="${esc(tipText(idea))}">${idea.voted ? `${icon.check} ${esc(t('res.votedBtn'))}` : esc(t('res.vote'))}</button>`;
  return `
    <article class="idea-card" id="idea-${idea.id}" data-id="${idea.id}">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:2px">
          <span class="title" dir="auto">${esc(idea.title)}</span>
          <span class="meta"><span>${esc(categoryLabel(idea.category))}</span><span>${esc(idea.neighborhoodName)}</span><span>${esc(t('res.away', { d: fmt.km(idea.distanceKm) }))}</span></span>
        </div>
        ${idea.mine ? `<span class="badge badge-sand">${esc(t('res.yourIdea'))}</span>` : statusBadge(idea.status)}
      </div>
      ${idea.summary ? `<p class="small muted clamp-2">${esc(idea.summary)}</p>` : ''}
      ${stage}
      <span class="small ${idea.canVote ? '' : 'muted'}" style="${idea.canVote ? 'color:var(--green-800);font-weight:600' : ''}">${esc(tipText(idea))}</span>
      <div class="actions">
        ${voteBtn}
        <a class="btn btn-secondary btn-sm" href="idea.html?id=${idea.id}">${esc(t('common.viewDetails'))}</a>
      </div>
    </article>`;
}

function renderList() {
  const shown = ideas.filter((i) => groupOf(i) === filter);
  ['can', 'voted', 'view'].forEach((g) => { $(`[data-n="${g}"]`).textContent = `(${fmt.num(ideas.filter((i) => groupOf(i) === g).length)})`; });
  $('#count').textContent = filter === 'view' ? t('res.viewNote', { km: VOTING_RADIUS_KM }) : '';
  $('#list').innerHTML = shown.length
    ? shown.map(card).join('')
    : emptyState(
      filter === 'voted' ? t('res.noVotes') : filter === 'can' ? t('res.noneToVote') : t('res.nothingNearby'),
      filter === 'voted' ? t('res.noVotesText') : filter === 'can' ? t('res.noneToVoteText') : t('res.nothingNearbyText'),
      `<a class="btn btn-sm" href="propose.html">${esc(t('nav.propose'))}</a>`,
    );
  $$('[data-vote]').forEach((btn) => btn.addEventListener('click', () => vote(Number(btn.dataset.vote), btn)));
  $$('.idea-card').forEach((el) => {
    el.addEventListener('mouseenter', () => highlight(Number(el.dataset.id)));
    // Clicking the card (not its buttons) opens the idea's card on the map
    el.addEventListener('click', (e) => {
      if (e.target.closest('a, button')) return;
      const m = markers.get(Number(el.dataset.id));
      if (m) { map.panTo(m.getLatLng()); m.openPopup(); }
    });
  });
}

function highlight(id) {
  markers.forEach((m, key) => {
    m.getElement()?.querySelector('.pin')?.classList.toggle('active', key === id);
    if (key === id) m.openTooltip(); else m.closeTooltip();
  });
}

function focusCard(id, openPopup = true) {
  $$('.idea-card').forEach((el) => el.classList.toggle('active', Number(el.dataset.id) === id));
  const el = $(`#idea-${id}`);
  if (!el) {
    const idea = ideas.find((i) => i.id === id);
    filter = idea ? groupOf(idea) : 'can';
    $$('[data-filter]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === filter)));
    renderList();
  }
  $(`#idea-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  highlight(id);
  if (openPopup) markers.get(id)?.openPopup();
}

async function vote(id, btn) {
  await withLoading(btn, async () => {
    try {
      // The server decides eligibility from the saved addresses; no location is sent
      await api.post(`discover/vote/${id}`);
      const idea = ideas.find((i) => i.id === id);
      Object.assign(idea, { voted: true, canVote: false });
      toast(t('res.voteDone'), 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  });
  map.closePopup();
  renderMap();
  renderList();
  focusCard(id);
}

// The chosen home (with its 2 km circle) and, faintly, the other home
function drawHomes() {
  homeLayer.clearLayers();
  addresses.forEach((a) => {
    const chosen = a.id === current.id;
    L.circle([a.latitude, a.longitude], {
      radius: VOTING_RADIUS_KM * 1000, color: '#2F5444', weight: chosen ? 1.5 : 1, fillColor: '#5A7D66', fillOpacity: chosen ? 0.08 : 0.03, dashArray: '6 6', interactive: false,
    }).addTo(homeLayer);
    L.marker([a.latitude, a.longitude], { icon: meIcon(), interactive: false, keyboard: false, opacity: chosen ? 1 : 0.6 }).addTo(homeLayer);
  });
  map.fitBounds(L.latLng(current.latitude, current.longitude).toBounds(VOTING_RADIUS_KM * 2000), { padding: [30, 30] });
}

async function load() {
  $('#list').innerHTML = skeletonCards(3, 130);
  try {
    ideas = await api.get('discover/map', { addressId: current.id });
    // Open on the first group that has something in it
    if (!ideas.some((i) => groupOf(i) === filter)) {
      filter = ['can', 'voted', 'view'].find((g) => ideas.some((i) => groupOf(i) === g)) || 'can';
      $$('[data-filter]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === filter)));
    }
    renderMap();
    renderList();
  } catch (err) {
    $('#list').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
  }
}

// Which home the map shows: a switcher when there are two, otherwise a chip that links to the addresses
function renderAddressSlot() {
  const slot = $('#addr-slot');
  const chipText = (a, i) => `${addressName(a, i)} · ${isolate(a.neighborhoodName)}`;
  if (addresses.length > 1) {
    slot.innerHTML = `<label class="sr-only" for="addr-switch">${esc(t('res.addrSwitch'))}</label>
      <select class="addr-chip" id="addr-switch">${addresses.map((a, i) => `<option value="${a.id}"${a.id === current.id ? ' selected' : ''}>${esc(chipText(a, i))}</option>`).join('')}</select>`;
    $('#addr-switch').addEventListener('change', (e) => {
      current = addresses.find((a) => a.id === Number(e.target.value));
      drawHomes();
      load();
    });
  } else {
    slot.innerHTML = `<a class="addr-chip" href="account.html#addresses" title="${esc(t('res.manageAddresses'))}">${icon.pin}<span>${esc(chipText(current, 0))}</span></a>`;
  }
}

function noAddress() {
  $('#addr-slot').innerHTML = '';
  $('#map').insertAdjacentHTML('beforeend', `<div class="map-overlay"><div class="stack" style="align-items:center">${icon.map}
    <h3 style="font-size:22px;font-weight:600">${esc(t('res.noAddressTitle'))}</h3><p class="muted">${esc(t('res.noAddressText'))}</p>
    <a class="btn" href="account.html#addresses">${esc(t('res.addHome'))}</a></div></div>`);
  $('#list').innerHTML = emptyState(t('res.noAddressTitle'), t('res.noAddressText'), `<a class="btn btn-sm" href="account.html#addresses">${esc(t('res.addHome'))}</a>`);
}

$$('[data-filter]').forEach((b) => b.addEventListener('click', () => {
  filter = b.dataset.filter;
  $$('[data-filter]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
  renderList();
}));

// Start from the saved addresses (primary first)
try {
  addresses = (await api.get('account/addresses')).addresses || [];
} catch (err) {
  toast(errorMessage(err), 'error');
}
if (!addresses.length) {
  noAddress();
} else {
  current = addresses[0];
  const toConfirm = addresses.find((a) => a.needsConfirmation);
  if (toConfirm) {
    $('#addr-notice').innerHTML = `<div class="notice notice-warn" style="margin-bottom:20px">${icon.warn}<div class="stack" style="gap:8px">
      <strong>${esc(t('acc.confirmTitle'))}</strong><span>${esc(t('acc.confirmText'))}</span>
      <a class="btn btn-sm" style="align-self:flex-start" href="account.html#addresses">${esc(t('acc.confirmBtn'))}</a></div></div>`;
  }
  renderAddressSlot();
  drawHomes();
  load();
}
