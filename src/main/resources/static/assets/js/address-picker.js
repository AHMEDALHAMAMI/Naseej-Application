// Home address picker shared by sign-up, Google sign-up and the account page:
// a map with a draggable pin (tap or search to place it), the district found from the pin, an optional label
// and the optional Saudi National Address. "Find me" only moves the pin; the person still checks it and saves.
// Live location is never sent as the address by itself: what gets saved is where the pin is.
import { api } from './api.js';
import { $, esc, icon, toast, withLoading, t } from './ui.js';
import { serverText } from './i18n.js';
import { createMap, locate, pinIcon, RIYADH } from './maps.js';

let uid = 0;

/**
 * addressPicker(el, { value, label, showLabel, height, circleKm })
 * value: an existing address ({ latitude, longitude, label, buildingNumber, … }) to edit.
 * Returns { values(), validate(), map }.
 */
export function addressPicker(el, { value = null, labelPlaceholder = t('addr.labelPh'), showLabel = true, height = 320, circleKm = 0 } = {}) {
  const id = `ap${++uid}`;
  const v = value || {};
  const field = (name, key, attrs = '') => `
    <div class="field"><label for="${id}-${name}">${esc(t(key))}</label>
      <input class="input" id="${id}-${name}" name="${name}" value="${esc(v[name] || '')}" ${attrs}><span class="error" aria-live="polite"></span></div>`;

  el.innerHTML = `
    <div class="stack" style="gap:12px">
      <div class="place-search" role="search">
        <label class="sr-only" for="${id}-q">${esc(t('addr.search'))}</label>
        <input class="input" id="${id}-q" type="search" autocomplete="off" placeholder="${esc(t('addr.search'))}">
        <ul class="place-results" id="${id}-results" role="listbox" hidden></ul>
      </div>
      <div class="map" id="${id}-map" style="height:${height}px" role="application" aria-label="${esc(t('addr.mapLabel'))}"></div>
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <p class="small" id="${id}-status" aria-live="polite">${esc(value ? t('addr.dragHint') : t('addr.tapToPlace'))}</p>
        <button type="button" class="btn btn-secondary btn-sm" id="${id}-me" data-loading="${esc(t('loc.locating'))}">${icon.pin} ${esc(t('addr.findMe'))}</button>
      </div>
      <span class="hint small muted" style="margin-top:-6px">${esc(t('addr.findMeHint'))}</span>
      <input type="hidden" name="address" data-pin>
      <span class="error" data-pin-error aria-live="polite"></span>
      ${showLabel ? field('label', 'addr.label', `maxlength="40" placeholder="${esc(labelPlaceholder)}"`) : ''}
      <details class="surface-sand" style="padding:12px 16px"${v.buildingNumber || v.street || v.postalCode ? ' open' : ''}>
        <summary style="cursor:pointer;font-weight:600;min-height:var(--tap);display:flex;align-items:center">${esc(t('addr.national'))} <span class="muted small" style="margin-inline-start:6px">${esc(t('form.optional'))}</span></summary>
        <p class="small muted" style="margin-bottom:10px">${esc(t('addr.nationalHint'))}</p>
        <div class="grid grid-2" style="gap:12px">
          ${field('buildingNumber', 'addr.building', 'inputmode="numeric" maxlength="4" dir="ltr" autocomplete="off"')}
          ${field('street', 'addr.street', 'maxlength="120"')}
          ${field('district', 'addr.district', 'maxlength="100"')}
          ${field('postalCode', 'addr.postal', 'inputmode="numeric" maxlength="5" dir="ltr" autocomplete="postal-code"')}
          ${field('additionalNumber', 'addr.additional', 'inputmode="numeric" maxlength="4" dir="ltr" autocomplete="off"')}
        </div>
      </details>
    </div>`;

  const status = $(`#${id}-status`, el);
  const hasValue = v.latitude != null && v.longitude != null;
  const map = createMap($(`#${id}-map`, el), { center: hasValue ? { lat: v.latitude, lng: v.longitude } : RIYADH, zoom: hasValue ? 16 : 11 });
  let point = hasValue ? { lat: v.latitude, lng: v.longitude } : null;
  let marker = null;
  let circle = null;
  let hoodOk = hasValue;

  const describe = async () => {
    status.textContent = t('addr.findingHood');
    try {
      const hood = await api.get('public/nearest-neighborhood', { lat: point.lat, lng: point.lng }, { auth: false });
      hoodOk = true;
      status.innerHTML = `${icon.pin} ${esc(t('addr.inHood', { hood: `${hood.name}, ${hood.city}` }))} · <span class="muted">${esc(t('addr.dragHint'))}</span>`;
    } catch {
      hoodOk = false;
      status.textContent = t('addr.noHood');
    }
  };

  const place = (loc, { pan = true } = {}) => {
    point = { lat: loc.lat, lng: loc.lng };
    if (!marker) {
      marker = L.marker([point.lat, point.lng], { icon: pinIcon('in-range'), draggable: true, autoPan: true, keyboard: true, title: t('addr.dragHint') }).addTo(map);
      marker.on('dragend', () => { const p = marker.getLatLng(); place({ lat: p.lat, lng: p.lng }, { pan: false }); });
    } else {
      marker.setLatLng([point.lat, point.lng]);
    }
    if (circleKm) {
      circle?.remove();
      circle = L.circle([point.lat, point.lng], { radius: circleKm * 1000, color: '#2F5444', weight: 1.4, dashArray: '6 6', fillColor: '#5A7D66', fillOpacity: 0.06, interactive: false }).addTo(map);
    }
    if (pan) map.setView([point.lat, point.lng], Math.max(map.getZoom(), 16));
    $('[data-pin-error]', el).textContent = '';
    describe();
  };

  if (hasValue) place(point, { pan: false });
  map.on('click', (e) => place({ lat: e.latlng.lat, lng: e.latlng.lng }, { pan: false }));
  setTimeout(() => map.invalidateSize(), 150); // the picker may have been hidden (stepper, dialog) while the map was created

  $(`#${id}-me`, el).addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      place(await locate());
    } catch (err) {
      toast(err.message, 'error');
    }
  }));

  // Search a district or street (OpenStreetMap through the server)
  const input = $(`#${id}-q`, el);
  const list = $(`#${id}-results`, el);
  let timer = null;
  let ticket = 0;
  const close = () => { list.hidden = true; list.innerHTML = ''; };
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 2) { close(); return; }
    timer = setTimeout(async () => {
      const mine = ++ticket;
      try {
        const results = await api.get('public/search-places', { q }, { auth: false });
        if (mine !== ticket) return;
        list.innerHTML = results.length
          ? results.map((r, i) => `<li role="option"><button type="button" data-i="${i}"><strong>${esc(r.name)}</strong><span>${esc(r.detail)}</span></button></li>`).join('')
          : `<li class="muted small" style="padding:10px 12px">${esc(t('prop.noPlaces'))}</li>`;
        list.hidden = false;
        list.querySelectorAll('button[data-i]').forEach((b) => b.addEventListener('click', () => {
          const r = results[Number(b.dataset.i)];
          input.value = r.name;
          close();
          place({ lat: r.latitude, lng: r.longitude });
        }));
      } catch {
        close();
      }
    }, 400);
  });
  input.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); if (e.key === 'Enter') e.preventDefault(); });
  document.addEventListener('click', (e) => { if (!e.target.closest('.place-search')) close(); });

  const val = (name) => el.querySelector(`[name="${name}"]`)?.value.trim() || null;
  return {
    map,
    /** The AddressRequest body, or null when there is no pin yet. */
    values() {
      if (!point) return null;
      return {
        latitude: point.lat,
        longitude: point.lng,
        label: val('label'),
        buildingNumber: val('buildingNumber'),
        street: val('street'),
        district: val('district'),
        postalCode: val('postalCode'),
        additionalNumber: val('additionalNumber'),
      };
    },
    /** Shows the first problem next to its field and returns false, or true when it can be sent. */
    validate() {
      const err = (name, msg) => {
        const input2 = el.querySelector(`[name="${name}"]`);
        const box = input2?.closest('.field')?.querySelector('.error');
        if (box) box.textContent = msg;
        input2?.setAttribute('aria-invalid', 'true');
        input2?.focus();
        return false;
      };
      el.querySelectorAll('[aria-invalid]').forEach((x) => x.removeAttribute('aria-invalid'));
      el.querySelectorAll('.field .error').forEach((x) => { x.textContent = ''; });
      if (!point) {
        $('[data-pin-error]', el).textContent = t('addr.pinRequired');
        input.focus();
        return false;
      }
      if (!hoodOk) {
        $('[data-pin-error]', el).textContent = t('addr.noHood');
        return false;
      }
      const checks = [['buildingNumber', /^\d{4}$/, 'Building number must be 4 digits'], ['postalCode', /^\d{5}$/, 'Postal code must be 5 digits'], ['additionalNumber', /^\d{4}$/, 'Additional number must be 4 digits']];
      for (const [name, re, msg] of checks) {
        const value2 = val(name);
        if (value2 && !re.test(value2)) {
          // Same wording as the server, translated by SERVER_MESSAGES
          return err(name, serverText(msg));
        }
      }
      return true;
    },
  };
}
