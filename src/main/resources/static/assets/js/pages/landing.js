// Landing page: live public numbers and a map of open ideas near the visitor (titles only).
import { api, session } from '../api.js';
import { $, esc, fmt, homeFor, reveal, toast, withLoading, t } from '../ui.js';
import { tn, applyI18n, langButton, wireLangButtons } from '../i18n.js';
import { startHero } from '../hero.js';
import { createMap, locate, lastLocation, pinIcon, meIcon, RIYADH, keyboardMarker } from '../maps.js';

applyI18n();
$('#lang-slot').innerHTML = langButton('btn-outline-light');
$('#lang-foot').innerHTML = langButton('btn-secondary');
wireLangButtons();
$('#year').textContent = new Date().getFullYear();
reveal();
startHero($('#hero-viz'));

// Logged-in visitors get a shortcut to their home
const user = session.user();
if (session.isLoggedIn() && user) {
  $('#nav-links').innerHTML = `<a class="btn btn-light btn-sm" href="${homeFor(user)}">${esc(t('land.open'))}</a>`;
}

// ---------- Numbers ----------
api.get('public/stats', null, { auth: false })
  .then((stats) => {
    document.querySelectorAll('[data-stat]').forEach((el) => {
      countUp(el, Number(stats[el.dataset.stat] ?? 0));
    });
  })
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

// ---------- Map ----------
const map = createMap($('#landing-map'), { center: RIYADH, zoom: 12, scrollWheelZoom: false });
const layer = L.layerGroup().addTo(map);
let meMarker = null;
const markers = new Map();

let loadTicket = 0;

async function load(center, isVisitor) {
  const ticket = ++loadTicket;
  layer.clearLayers();
  markers.clear();
  const list = $('#places');
  list.innerHTML = `<li class="small muted" style="padding:10px 12px">${esc(t('land.loading'))}</li>`;
  try {
    const pins = await api.get('public/ideas-nearby', { lat: center.lat, lng: center.lng, radiusKm: 15 }, { auth: false });
    if (ticket !== loadTicket) return; // a newer load (e.g. the visitor's location) replaced this one
    $('#around-title').textContent = isVisitor
      ? (pins.length ? tn(pins.length, 'land.nearYou') : t('land.nothingNear'))
      : t('land.aroundTitle');
    if (!pins.length) {
      list.innerHTML = `<li class="small muted" style="padding:10px 12px">${esc(t('land.beFirst'))} <a href="register.html">${esc(t('land.joinNaseej'))}</a></li>`;
      return;
    }
    list.innerHTML = pins.map((p) => `
      <li><button type="button" data-id="${p.id}" aria-pressed="false">${esc(p.title)}
        <span class="small muted" style="display:block;font-family:var(--font-body);font-size:13px">${esc(p.neighborhoodName)} · ${fmt.km(p.distanceKm)}</span>
      </button></li>`).join('');

    const bounds = [];
    pins.forEach((p) => {
      const marker = keyboardMarker(L.marker([p.latitude, p.longitude], { icon: pinIcon() }), p.title)
        .bindTooltip(`<strong>${esc(p.title)}</strong><br><span>${esc(p.neighborhoodName)} · ${esc(t('status.PUBLISHED'))}</span>`, { className: 'nsj-tip', direction: 'top' })
        .on('click', () => activate(p.id, true))
        .on('mouseover', () => activate(p.id, false))
        .addTo(layer);
      markers.set(p.id, marker);
      bounds.push([p.latitude, p.longitude]);
    });
    if (meMarker) bounds.push(meMarker.getLatLng());
    map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15 });

    list.querySelectorAll('button[data-id]').forEach((btn) => {
      const id = Number(btn.dataset.id);
      btn.addEventListener('mouseenter', () => activate(id, false));
      btn.addEventListener('focus', () => activate(id, false));
      btn.addEventListener('click', () => activate(id, true));
    });
  } catch {
    list.innerHTML = `<li class="small muted" style="padding:10px 12px">${esc(t('land.loadFailed'))}</li>`;
  }
}

function activate(id, pan) {
  document.querySelectorAll('#places button').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.id) === id)));
  markers.forEach((m, key) => {
    m.getElement()?.querySelector('.pin')?.classList.toggle('active', key === id);
    if (key === id) m.openTooltip(); else m.closeTooltip();
  });
  if (pan) map.panTo(markers.get(id).getLatLng(), { animate: true });
}

$('#use-loc').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
  try {
    const loc = await locate();
    showMe(loc);
    await load(loc, true);
  } catch (err) {
    toast(err.message, 'error');
  }
}));

function showMe(loc) {
  meMarker?.remove();
  meMarker = L.marker([loc.lat, loc.lng], { icon: meIcon(), interactive: false, keyboard: false }).addTo(map);
  $('#loc-note').textContent = t('land.within15');
}

// The landing page asks for the location right away; Riyadh is shown until (or unless) it is shared
const known = lastLocation();
if (known) {
  showMe(known);
  load(known, true);
} else {
  load(RIYADH, false);
  locate()
    .then((loc) => { showMe(loc); return load(loc, true); })
    .catch(() => { $('#loc-note').textContent = t('land.showingRiyadh'); });
}
