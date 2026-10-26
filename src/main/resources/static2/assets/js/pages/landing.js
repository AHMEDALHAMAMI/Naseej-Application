// Landing page, kept short: a photo hero with the question and the two ways in, what neighbors are asking for
// (city chips, list and map), how an idea opens, what investors get (with the early-price slider), one close.
// Nothing asks for the device location on load.
import { api, session } from '../api.js';
import { $, $$, esc, fmt, homeFor, reveal, toast, withLoading, t, categoryLabel } from '../ui.js';
import { catTile } from '../components.js';
import { applyI18n, langButton, wireLangButtons } from '../i18n.js';
import { createMap, locate, pinIcon, meIcon, RIYADH, CITIES, keyboardMarker } from '../maps.js';

applyI18n();
$('#lang-slot').innerHTML = langButton('btn-outline-light');
$('#lang-foot').innerHTML = langButton('btn-ghost');
wireLangButtons();
$('#year').textContent = new Date().getFullYear();

// ---------- Who is looking ----------
// visitor · resident · investor · member (admins and Google sign-ups that haven't picked a role)
const user = session.isLoggedIn() ? session.user() : null;
const viewer = !user ? 'visitor'
  : user.role === 'RESIDENT' ? 'resident'
  : user.role === 'INVESTOR' ? 'investor'
  : 'member';
document.body.dataset.viewer = viewer;
const isMember = viewer !== 'visitor';
$$('[data-show]').forEach((el) => { el.hidden = el.dataset.show === 'visitor' ? isMember : !isMember; });
$$('[data-hide]').forEach((el) => { el.hidden = el.dataset.hide === viewer; });

const link = (href, key, cls = 'btn btn-light') => `<a class="${cls}" href="${href}">${esc(t(key))}</a>`;
if (isMember) {
  $('#home-link').href = homeFor(user);
  const cta = $('#member-cta');
  if (viewer === 'resident') {
    cta.innerHTML = link('resident.html', 'land.ctaResident') + link('propose.html', 'land.ctaPropose', 'btn btn-outline-light');
    setFoot('resident.html', 'land.ctaResident');
    $('#around-cta').href = 'resident.html';
    $('#around-cta').textContent = t('land.ctaResident');
    $('#vote-rule').textContent = t('land.voteRuleYou');
  } else if (viewer === 'investor') {
    $('#hero-title').textContent = `${t('land.invTitle1')} ${t('land.invTitle2')}`;
    $('#hero-sub').textContent = t('land.invSub');
    cta.innerHTML = link('investor.html', 'land.ctaInvestor');
    setFoot('investor.html', 'land.ctaInvestor');
    $('#inv-cta').href = 'investor.html';
    $('#inv-cta').textContent = t('land.ctaInvestor');
  } else {
    cta.innerHTML = link(homeFor(user), 'land.open');
    setFoot(homeFor(user), 'land.open');
  }
}

function setFoot(href, key) {
  $('#foot-cta').href = href;
  $('#foot-cta-text').textContent = t(key);
}

// ---------- Live numbers ----------
api.get('public/stats', null, { auth: false })
  .then((stats) => $$('[data-stat]').forEach((el) => countUp(el, Number(stats[el.dataset.stat] ?? 0))))
  .catch(() => { /* keep the dashes; the page still works */ });

function countUp(el, target) {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce || target === 0) { el.textContent = fmt.num(target); return; }
  const start = performance.now();
  const step = (now) => {
    const k = Math.min(1, (now - start) / 1100);
    el.textContent = fmt.num(Math.round(target * (1 - Math.pow(1 - k, 3))));
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

// ---------- Ideas by city ----------
// Tabs: the resident's home (when saved), "near you" (after the visitor shares a location), then the cities.
const around = $('#around');
const tabs = CITIES.map((c) => ({ key: c.key, center: { lat: c.lat, lng: c.lng }, name: t(`city.${c.key}`), where: t('land.inCity', { city: t(`city.${c.key}`) }) }));
const pinsByTab = new Map();
let current = null;
let map = null;
let layer = null;
let meMarker = null;
const markers = new Map();
let ticket = 0; // the latest tab load; older answers are ignored
const SHORT_LIST = 5;

if (!around.hidden) startAround();

async function startAround() {
  map = createMap($('#landing-map'), { center: RIYADH, zoom: 12, scrollWheelZoom: false });
  layer = L.layerGroup().addTo(map);

  if (viewer === 'resident') {
    try {
      const addresses = (await api.get('account/addresses')).addresses || [];
      const home = addresses.find((a) => a.primary) ?? addresses[0];
      if (home?.latitude != null) {
        tabs.unshift({ key: 'home', center: { lat: home.latitude, lng: home.longitude }, name: t('land.homeTab'), where: t('land.aroundHome'), distances: true });
      }
    } catch { /* no saved address yet: the cities still work */ }
  }
  renderTabs();
  select(tabs[0].key);
  // Counts for every tab, in the background
  tabs.forEach((tab) => loadPins(tab).then(() => updateCount(tab)).catch(() => {}));
}

function loadPins(tab) {
  if (!pinsByTab.has(tab.key)) {
    pinsByTab.set(tab.key, api.get('public/ideas-nearby', { lat: tab.center.lat, lng: tab.center.lng, radiusKm: 15 }, { auth: false }));
  }
  return pinsByTab.get(tab.key);
}

function renderTabs() {
  $('#city-tabs').innerHTML = tabs.map((tab) => `
    <button type="button" role="tab" id="tab-${tab.key}" aria-controls="city-panel" aria-selected="false" tabindex="-1" data-key="${tab.key}">
      <span>${esc(tab.name)}</span><small data-count></small>
    </button>`).join('');
  $$('#city-tabs [role="tab"]').forEach((btn) => {
    btn.addEventListener('click', () => select(btn.dataset.key));
    btn.addEventListener('keydown', onTabKey);
  });
  tabs.forEach((tab) => { if (pinsByTab.has(tab.key)) loadPins(tab).then(() => updateCount(tab)).catch(() => {}); });
}

function onTabKey(e) {
  const all = $$('#city-tabs [role="tab"]');
  const i = all.indexOf(e.currentTarget);
  const rtl = document.dir === 'rtl';
  const next = { ArrowDown: 1, ArrowRight: rtl ? -1 : 1, ArrowUp: -1, ArrowLeft: rtl ? 1 : -1, Home: -i, End: all.length - 1 - i }[e.key];
  if (next === undefined) return;
  e.preventDefault();
  const target = all[(i + next + all.length) % all.length];
  target.focus();
  select(target.dataset.key);
}

async function updateCount(tab) {
  const pins = await loadPins(tab);
  const el = $(`#tab-${tab.key} [data-count]`);
  if (el) el.textContent = pins.length ? fmt.num(pins.length) : '';
}

async function select(key) {
  const tab = tabs.find((x) => x.key === key);
  current = tab;
  $$('#city-tabs [role="tab"]').forEach((b) => {
    const on = b.dataset.key === key;
    b.setAttribute('aria-selected', String(on));
    b.tabIndex = on ? 0 : -1;
  });
  $('#city-panel').setAttribute('aria-labelledby', `tab-${key}`);
  $('#around-where').textContent = tab.where;

  const mine = ++ticket;
  const list = $('#places');
  list.innerHTML = `<li class="lx-msg">${esc(t('land.loading'))}</li>`;
  layer.clearLayers();
  markers.clear();
  try {
    const pins = await loadPins(tab);
    if (mine !== ticket) return; // the visitor picked another tab meanwhile
    drawPins(tab, pins);
  } catch {
    if (mine === ticket) list.innerHTML = `<li class="lx-msg">${esc(t('land.loadFailed'))}</li>`;
  }
}

function drawPins(tab, pins) {
  const list = $('#places');
  $('#show-all').hidden = true;
  list.classList.remove('is-collapsed');
  if (!pins.length) {
    const first = viewer === 'resident' ? `<a href="propose.html">${esc(t('land.ctaPropose'))}</a>` : `<a href="register.html?role=RESIDENT">${esc(t('land.joinNaseej'))}</a>`;
    list.innerHTML = `<li class="lx-msg"><strong>${esc(t('land.nothingNear'))}</strong> ${esc(t('land.beFirst'))} ${first}</li>`;
    map.setView([tab.center.lat, tab.center.lng], 12);
    return;
  }
  list.innerHTML = pins.map((p) => `
    <li><button type="button" class="lx-item" data-id="${p.id}" aria-pressed="false">
      ${catTile(p.category, { size: 20 })}
      <span class="lx-item-main"><span class="lx-item-title"><bdi>${esc(p.title)}</bdi></span><span class="lx-item-meta">${esc(categoryLabel(p.category))} · <bdi>${esc(p.neighborhoodName)}</bdi>${tab.distances ? ` · ${esc(fmt.km(p.distanceKm))}` : ''}</span></span>
      <span class="lx-item-status">${esc(t('status.PUBLISHED'))}</span>
    </button></li>`).join('');

  // Phones show the first few; the rest open with one tap
  const more = $('#show-all');
  list.classList.toggle('is-collapsed', pins.length > SHORT_LIST);
  more.hidden = pins.length <= SHORT_LIST;
  more.textContent = t('land.showAll', { n: pins.length });

  const bounds = [];
  pins.forEach((p) => {
    const marker = keyboardMarker(L.marker([p.latitude, p.longitude], { icon: pinIcon() }), p.title)
      .bindTooltip(`<strong>${esc(p.title)}</strong><br><span>${esc(p.neighborhoodName)} · ${esc(categoryLabel(p.category))}</span>`, { className: 'nsj-tip', direction: 'top' })
      .on('click', () => activate(p.id, false))
      .on('mouseover', () => activate(p.id, false))
      .addTo(layer);
    markers.set(p.id, marker);
    bounds.push([p.latitude, p.longitude]);
  });
  if (meMarker && tab.key === 'me') bounds.push(meMarker.getLatLng());
  map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 });

  $$('#places .lx-item').forEach((btn) => {
    const id = Number(btn.dataset.id);
    btn.addEventListener('mouseenter', () => activate(id, false));
    btn.addEventListener('focus', () => activate(id, false));
    btn.addEventListener('click', () => activate(id, true));
  });
}

function activate(id, pan) {
  $$('#places .lx-item').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.id) === id)));
  markers.forEach((m, key) => {
    m.getElement()?.querySelector('.pin')?.classList.toggle('active', key === id);
    if (key === id) m.openTooltip(); else m.closeTooltip();
  });
  if (pan && markers.has(id)) map.panTo(markers.get(id).getLatLng(), { animate: true });
}

$('#show-all').addEventListener('click', (e) => {
  $('#places').classList.remove('is-collapsed');
  e.currentTarget.hidden = true;
});

// Only when asked: adds a "Near you" tab around the shared location
$('#use-loc').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
  try {
    const loc = await locate();
    meMarker?.remove();
    meMarker = L.marker([loc.lat, loc.lng], { icon: meIcon(), interactive: false, keyboard: false }).addTo(map);
    pinsByTab.delete('me');
    const existing = tabs.findIndex((x) => x.key === 'me');
    if (existing >= 0) tabs.splice(existing, 1);
    tabs.splice(viewer === 'resident' && tabs[0]?.key === 'home' ? 1 : 0, 0,
      { key: 'me', center: loc, name: t('land.nearMeTab'), where: t('land.nearMe'), distances: true });
    renderTabs();
    loadPins(tabs.find((x) => x.key === 'me')).then(() => updateCount(tabs.find((x) => x.key === 'me'))).catch(() => {});
    await select('me');
  } catch (err) {
    toast(err.message, 'error');
  }
}));

// ---------- Opening early ----------
// Same rule as UnlockService.currentPrice: 60% of the full price, rising with the vote percentage to 100%.
const votes = $('#early-votes');
const showEarly = () => {
  const p = Number(votes.value);
  const share = Math.round(60 + 0.4 * p);
  $('#early-out').textContent = t('land.earlyValue', { p });
  votes.setAttribute('aria-valuetext', t('land.earlyValue', { p }));
  $('#early-fig').textContent = share;
};
votes.addEventListener('input', showEarly);
showEarly();

reveal();
