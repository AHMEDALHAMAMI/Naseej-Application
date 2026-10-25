// Browser location + Leaflet helpers (Leaflet is loaded as a global script on map pages).
import { esc } from './ui.js';
import { t } from './i18n.js';

const LOC_KEY = 'naseej.lastLocation';

function position(options) {
  return new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, options));
}

/**
 * Asks the browser for the current position. Resolves { lat, lng } or rejects with a readable message.
 * Laptops often can't get a precise (GPS) fix, so a timeout or "position unavailable" retries once with
 * Wi-Fi/network accuracy, which is still precise enough to find the neighborhood.
 */
export async function locate({ timeout = 10000 } = {}) {
  if (!('geolocation' in navigator)) throw new Error(t('geo.unsupported'));
  let pos;
  try {
    pos = await position({ enableHighAccuracy: true, timeout, maximumAge: 60000 });
  } catch (err) {
    if (err.code === 1) throw new Error(t('geo.blocked'));
    try {
      pos = await position({ enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 });
    } catch (err2) {
      throw new Error(err2.code === 1 ? t('geo.blocked') : t('geo.failed'));
    }
  }
  const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
  try { sessionStorage.setItem(LOC_KEY, JSON.stringify(loc)); } catch { /* ignore */ }
  return loc;
}

/** Last location shared in this tab, if any (avoids asking again on every page). */
export function lastLocation() {
  try {
    const raw = sessionStorage.getItem(LOC_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

// Riyadh center, used before a location is known
export const RIYADH = { lat: 24.7136, lng: 46.6753 };
export const SAUDI_BOUNDS = [[16.3, 34.5], [32.2, 55.7]];

// Quick jumps on the investor's map (labels come from strings.js: city.<key>)
export const CITIES = [
  { key: 'Riyadh', lat: 24.7136, lng: 46.6753 },
  { key: 'Jeddah', lat: 21.5433, lng: 39.1728 },
  { key: 'Makkah', lat: 21.3891, lng: 39.8579 },
  { key: 'Madinah', lat: 24.5247, lng: 39.5692 },
  { key: 'Dammam', lat: 26.4207, lng: 50.0888 },
  { key: 'Khobar', lat: 26.2794, lng: 50.2083 },
  { key: 'Abha', lat: 18.2164, lng: 42.5053 },
  { key: 'Tabuk', lat: 28.3835, lng: 36.5662 },
];

/** Approximate area of a confidential opportunity: a soft circle that contains the real spot. */
export function approxCircle(lat, lng, radiusM, { active = false } = {}) {
  return L.circle([lat, lng], {
    radius: radiusM || 600,
    color: active ? '#1F3B2F' : '#2F5444',
    weight: active ? 2.2 : 1.4,
    dashArray: '5 6',
    fillColor: '#5A7D66',
    fillOpacity: active ? 0.22 : 0.12,
  });
}

/** Popup with the site's card style. */
export function cardPopup(html) {
  return L.popup({ className: 'nsj-pop', maxWidth: 320, minWidth: 260, autoPanPadding: [24, 24], closeButton: true }).setContent(html);
}

let configPromise = null;
const FALLBACK_TILES = {
  tileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  maxZoom: 19,
};

/** Map settings from GET public/map-config, loaded once per page. */
function mapConfig() {
  configPromise ??= fetch('public/map-config', { headers: { Accept: 'application/json' } })
    .then((res) => (res.ok ? res.json() : FALLBACK_TILES))
    .catch(() => FALLBACK_TILES);
  return configPromise;
}

/** Creates a Leaflet map with the tiles from the server's map settings (attribution included). */
export function createMap(el, { center = RIYADH, zoom = 13, ...options } = {}) {
  const map = L.map(el, { zoomControl: false, scrollWheelZoom: true, ...options }).setView([center.lat, center.lng], zoom);
  L.control.zoom({ position: 'topright' }).addTo(map);
  // The tile source comes from the server (MapTiler with a key, or OpenStreetMap without one)
  mapConfig().then((cfg) => {
    L.tileLayer(cfg.tileUrl, { maxZoom: cfg.maxZoom, attribution: cfg.attribution }).addTo(map);
  });
  return map;
}

export function pinIcon(kind = '') {
  return L.divIcon({ className: '', html: `<div class="pin ${kind}"><i></i></div>`, iconSize: [34, 34], iconAnchor: [5, 34], tooltipAnchor: [12, -30] });
}

export function meIcon() {
  return L.divIcon({ className: '', html: '<div class="me-dot" aria-hidden="true"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });
}

export function bubbleIcon(count, { size, active = false } = {}) {
  const s = size || Math.round(Math.min(64, 34 + Math.sqrt(count) * 6));
  return L.divIcon({
    className: '',
    html: `<div class="bubble${active ? ' active' : ''}" style="width:${s}px;height:${s}px">${esc(count)}</div>`,
    iconSize: [s, s],
    iconAnchor: [s / 2, s / 2],
  });
}

/** Keyboard access for markers: Enter/Space triggers the marker's click. */
export function keyboardMarker(marker, label) {
  marker.on('add', () => {
    const el = marker.getElement();
    if (!el) return;
    el.setAttribute('tabindex', '0');
    el.setAttribute('role', 'button');
    el.setAttribute('aria-label', label);
    el.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); marker.fire('click'); }
    });
  });
  return marker;
}
