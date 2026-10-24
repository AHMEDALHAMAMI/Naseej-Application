// Propose an idea → the AI checks competitors and similar ideas → published (with business model) or rejected.
// Investors also get a feasibility report and can adopt their own idea right away.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, toast, withLoading, applyFieldErrors, errorMessage, CATEGORIES, categoryLabel, modal, progressBar, animateBars, t } from '../ui.js';
import { serverText } from '../i18n.js';
import { createMap, locate, lastLocation, pinIcon, RIYADH } from '../maps.js';

const user = await guard(['RESIDENT', 'INVESTOR']);
const isInvestor = user.role === 'INVESTOR';
const app = $('#app');

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(isInvestor ? t('prop.eyebrowInv') : t('prop.eyebrow'))}</span>
      <h1>${t('prop.title')}</h1>
      <p class="muted" style="max-width:640px">${esc(isInvestor ? t('prop.introInv') : t('prop.intro'))}</p>
    </div>
  </div>
  <div id="stage"></div>`;

const stage = $('#stage');
let map;
let marker;
let point = lastLocation() || (user.latitude != null ? { lat: user.latitude, lng: user.longitude } : null);
let neighborhoods = [];

function formView() {
  stage.innerHTML = `
    <form id="idea-form" class="split" novalidate>
      <div class="card stack" style="gap:16px">
        <div class="field">
          <label for="title">${esc(t('prop.ideaTitle'))}</label>
          <input class="input" id="title" name="title" maxlength="120" placeholder="${esc(t('prop.titlePh'))}" required>
          <span class="error" aria-live="polite"></span>
        </div>
        <div class="field">
          <label for="category">${esc(t('prop.category'))}</label>
          <select class="select" id="category" name="category" required>
            <option value="">${esc(t('prop.chooseCategory'))}</option>
            ${CATEGORIES.map((c) => `<option value="${esc(c)}">${esc(categoryLabel(c))}</option>`).join('')}
          </select>
          <span class="error" aria-live="polite"></span>
        </div>
        <div class="field">
          <label for="description">${esc(t('prop.why'))}</label>
          <textarea class="textarea" id="description" name="description" maxlength="2000" required placeholder="${esc(t('prop.whyPh'))}"></textarea>
          <span class="hint">${esc(t('prop.whyHint'))}</span>
          <span class="error" aria-live="polite"></span>
        </div>
        ${isInvestor ? `
        <div class="grid grid-2" style="gap:12px">
          <div class="field"><label for="city">${esc(t('form.city'))}</label><select class="select" id="city"><option value="">${esc(t('form.chooseCity'))}</option></select></div>
          <div class="field"><label for="neighborhoodId">${esc(t('form.hood'))}</label><select class="select" id="neighborhoodId" name="neighborhoodId"><option value="">${esc(t('form.chooseHood'))}</option></select><span class="error" aria-live="polite"></span></div>
        </div>` : ''}
        <button class="btn btn-block" type="submit">${icon.spark} ${esc(t('prop.check'))}</button>
      </div>
      <div class="stack" style="gap:12px">
        <div class="map" id="map" style="height:420px" aria-label="${esc(t('prop.spotLabel'))}"></div>
        <div class="row" style="justify-content:space-between">
          <span class="small muted" id="spot">${esc(t('prop.drag'))}</span>
          <button type="button" class="btn btn-secondary btn-sm" id="here" data-loading="${esc(t('loc.locating'))}">${icon.pin} ${esc(t('loc.useMine'))}</button>
        </div>
      </div>
    </form>`;

  map = createMap($('#map'), { center: point || RIYADH, zoom: point ? 15 : 12 });
  setMarker(point || RIYADH, !!point);
  map.on('click', (e) => setMarker({ lat: e.latlng.lat, lng: e.latlng.lng }, true));

  $('#here').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const loc = await locate();
      setMarker(loc, true);
      map.setView([loc.lat, loc.lng], 16);
    } catch (err) {
      toast(err.message, 'error');
    }
  }));

  if (isInvestor) setupNeighborhoodPicker();
  $('#idea-form').addEventListener('submit', submit);
}

function setMarker(loc, chosen) {
  point = chosen ? loc : point;
  if (!marker) {
    marker = L.marker([loc.lat, loc.lng], { icon: pinIcon('in-range'), draggable: true, autoPan: true }).addTo(map);
    marker.on('dragend', () => {
      const p = marker.getLatLng();
      point = { lat: p.lat, lng: p.lng };
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
    if (!point || !$('#spot')) return; // the form may be gone (AI check running or done)
    try {
      const hood = await api.get('public/nearest-neighborhood', { lat: point.lat, lng: point.lng }, { auth: false });
      if (!$('#spot')) return;
      $('#spot').innerHTML = `${icon.pin} ${t('prop.inHood', { hood: `<strong>${esc(hood.name)}, ${esc(hood.city)}</strong>` })}`;
      if (isInvestor && !$('#neighborhoodId').value) {
        $('#city').value = hood.city;
        fillHoods(hood.city, hood.id);
      }
    } catch {
      if (!$('#spot')) return;
      $('#spot').textContent = isInvestor ? t('prop.pickHood') : t('prop.ownHood');
    }
  }, 350);
}

function fillHoods(city, selected) {
  const list = neighborhoods.filter((n) => n.city === city).sort((a, b) => a.name.localeCompare(b.name));
  $('#neighborhoodId').innerHTML = `<option value="">${esc(t('form.chooseHood'))}</option>`
    + list.map((n) => `<option value="${n.id}"${n.id === selected ? ' selected' : ''}>${esc(n.name)}</option>`).join('');
}

async function setupNeighborhoodPicker() {
  // Loaded once; reused when the form is drawn again after an error
  if (!neighborhoods.length) {
    try {
      neighborhoods = await api.get('neighborhoods/get-all', null, { auth: false });
    } catch {
      neighborhoods = [];
    }
  }
  const cities = [...new Set(neighborhoods.map((n) => n.city))].sort();
  $('#city').innerHTML = `<option value="">${esc(t('form.chooseCity'))}</option>` + cities.map((c) => `<option>${esc(c)}</option>`).join('');
  $('#city').addEventListener('change', () => fillHoods($('#city').value));
  $('#neighborhoodId').addEventListener('change', () => {
    const n = neighborhoods.find((x) => x.id === Number($('#neighborhoodId').value));
    if (n && n.latitude != null) {
      setMarker({ lat: n.latitude, lng: n.longitude }, true);
      map.setView([n.latitude, n.longitude], 15);
    }
  });
}

const STEPS = [t('prop.step1'), t('prop.step2'), t('prop.step3'), t('prop.step4')];

function analyzingView() {
  stage.innerHTML = `
    <div class="card stack" style="gap:18px;max-width:640px;margin:0 auto" role="status" aria-live="polite">
      <div class="row"><span class="spinner"></span><h2 style="font-size:26px">${esc(t('prop.checking'))}</h2></div>
      <ol class="list" id="steps">${STEPS.map((s, i) => `<li data-i="${i}"><span>${esc(s)}</span><span class="muted small">${esc(t('prop.waiting'))}</span></li>`).join('')}</ol>
      <p class="small muted">${esc(t('prop.takes'))}</p>
    </div>`;
  let i = 0;
  const tick = () => {
    const items = stage.querySelectorAll('#steps li');
    items.forEach((li, j) => {
      li.querySelector('span:last-child').innerHTML = j < i ? `<span style="color:var(--green-800)">${icon.check}</span>` : j === i ? '<span class="spinner" style="width:16px;height:16px;border-width:2px"></span>' : esc(t('prop.waiting'));
    });
    if (i < STEPS.length - 1) i += 1;
  };
  tick();
  return setInterval(tick, 4500);
}

async function submit(e) {
  e.preventDefault();
  const form = e.currentTarget;
  applyFieldErrors(form, null);
  const body = {
    title: form.title.value.trim(),
    category: form.category.value,
    description: form.description.value.trim(),
    latitude: point ? point.lat : null,
    longitude: point ? point.lng : null,
    neighborhoodId: isInvestor ? (Number(form.neighborhoodId.value) || null) : null,
  };
  const missing = !body.title ? 'title' : !body.category ? 'category' : !body.description ? 'description' : (isInvestor && !body.neighborhoodId) ? 'neighborhoodId' : null;
  if (missing) {
    const input = form.querySelector(`[name="${missing}"]`);
    input.setAttribute('aria-invalid', 'true');
    input.closest('.field').querySelector('.error').textContent = t('form.required');
    input.focus();
    return;
  }

  const timer = analyzingView();
  try {
    const idea = await api.post('discover/propose', body);
    clearInterval(timer);
    resultView(idea);
  } catch (err) {
    clearInterval(timer);
    formView();
    // Put the user's text back
    ['title', 'category', 'description'].forEach((k) => { $('#idea-form')[k].value = body[k]; });
    if (isInvestor && body.neighborhoodId) {
      const n = neighborhoods.find((x) => x.id === body.neighborhoodId);
      if (n) { $('#city').value = n.city; fillHoods(n.city, n.id); }
    }
    if (!applyFieldErrors($('#idea-form'), err)) toast(errorMessage(err), 'error');
  }
}

function resultView(idea) {
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (idea.status === 'REJECTED') {
    stage.innerHTML = `
      <div class="card stack" style="gap:16px;max-width:720px;margin:0 auto;border:2px solid var(--red-600)">
        <div class="row"><span class="badge badge-red">${icon.x} ${esc(t('status.REJECTED'))}</span>${idea.checkScore != null ? `<span class="badge badge-sand">${esc(t('check.score', { n: idea.checkScore }))}</span>` : ''}</div>
        <h2 style="font-size:30px">${esc(idea.checkDecision === 'SIMILAR' ? t('prop.similarTitle') : t('prop.saturatedTitle'))}</h2>
        <p>${esc(serverText(idea.rejectionReason || ''))}</p>
        <div class="row">
          ${idea.similarIdeaId ? `<a class="btn" href="idea.html?id=${idea.similarIdeaId}">${esc(t('idea.seeSimilar', { title: idea.similarIdeaTitle }))}</a>` : ''}
          <a class="btn btn-secondary" href="propose.html">${esc(t('idea.proposeDifferent'))}</a>
        </div>
      </div>`;
    return;
  }

  const m = idea.businessModel;
  const r = idea.feasibilityReport;
  stage.innerHTML = `
    <div class="stack" style="gap:20px">
      ${(idea.warnings || []).map((w) => `<div class="notice notice-warn">${icon.warn}<span>${esc(serverText(w))}</span></div>`).join('')}
      <div class="card row" style="gap:20px;align-items:center">
        <div class="ring" style="--p:${idea.checkScore || 0}"><div><strong>${idea.checkScore ?? '—'}</strong><span>${esc(t('common.of100'))}</span></div></div>
        <div class="stack" style="gap:6px;flex:1;min-width:240px">
          <span class="badge badge-solid" style="align-self:flex-start">${icon.check} ${esc(t('prop.published'))}</span>
          <h2 style="font-size:30px">${esc(idea.title)}</h2>
          <span class="muted">${esc(idea.neighborhoodName)}, ${esc(idea.city)} · ${esc(t('prop.competitorsWithin', { n: fmt.num(idea.competitorsCount), d: fmt.km(idea.searchRadiusKm) }))}</span>
        </div>
        <div class="row">
          ${idea.canAdopt ? `<button type="button" class="btn" id="adopt">${esc(t('prop.adoptNow'))}</button>` : ''}
          ${isInvestor && !idea.canAdopt && idea.investorStatus !== 'VERIFIED' ? `<span class="badge badge-amber">${esc(t('prop.adoptAfter'))}</span>` : ''}
          <a class="btn btn-secondary" href="idea.html?id=${idea.id}">${esc(t('prop.openPage'))}</a>
        </div>
      </div>
      <div class="grid grid-2">
        <section class="card stack" style="gap:12px">
          <h2>${esc(t('prop.nearby'))}</h2>
          ${idea.feasibilitySummary ? `<p>${esc(idea.feasibilitySummary)}</p>` : ''}
          ${(idea.competitors || []).length
            ? `<ul class="list">${idea.competitors.slice(0, 8).map((c) => `<li><span>${esc(c.name)}</span><span class="muted num">${fmt.km(c.distanceKm)}</span></li>`).join('')}</ul>`
            : `<p class="muted">${esc(t('prop.noCompetitors'))}</p>`}
        </section>
        ${m ? `<section class="card stack" style="gap:12px">
          <div class="card-head" style="margin:0"><h2>${esc(t('bm.title'))}</h2><span class="badge">${icon.spark} ${esc(t('bm.aiDraft'))}</span></div>
          <dl class="dl">
            <dt>${esc(t('bm.value'))}</dt><dd>${esc(m.valueProposition)}</dd>
            <dt>${esc(t('bm.customers'))}</dt><dd>${esc(m.targetCustomers)}</dd>
            <dt>${esc(t('bm.revenue'))}</dt><dd>${esc(m.revenueStreams)}</dd>
            <dt>${esc(t('bm.costs'))}</dt><dd>${esc(m.costStructure)}</dd>
            <dt>${esc(t('bm.capital'))}</dt><dd class="num">${m.estimatedCapitalMin != null ? `${fmt.money(m.estimatedCapitalMin)} – ${fmt.money(m.estimatedCapitalMax)}` : esc(t('bm.notEstimated'))}</dd>
          </dl></section>` : ''}
      </div>
      ${r ? `<section class="card stack" style="gap:14px">
        <h2>${esc(t('fr.title'))}</h2>
        <div class="grid grid-3" style="gap:12px">
          <div class="surface-sand kpi"><span class="value">${r.gapScore}</span><span class="label">${esc(t('fr.gap'))}</span>${progressBar(r.gapScore, t('idea.gapScore'))}</div>
          <div class="surface-sand kpi"><span class="value" style="font-size:22px">${esc(r.peakDemandTime || '—')}</span><span class="label">${esc(t('fr.peak'))}</span></div>
          <div class="surface-sand kpi"><span class="value" style="font-size:22px">${fmt.num(idea.competitorsCount)}</span><span class="label">${esc(t('prop.competitorsNearby'))}</span></div>
        </div>
        <p>${esc(r.recommendation)}</p>
        ${r.risks ? `<p class="small muted"><strong>${esc(t('bm.risks'))}:</strong> ${esc(r.risks)}</p>` : ''}
      </section>` : ''}
      ${!isInvestor ? `<div class="notice notice-info">${icon.info}<span>${esc(t('prop.liveNote'))}</span></div>` : ''}
    </div>`;
  animateBars(stage);

  $('#adopt')?.addEventListener('click', async () => {
    const result = await modal({
      title: t('adopt.title'),
      body: `<p class="muted">${esc(t('adopt.text'))}</p>`,
      confirmText: t('adopt.confirm'),
      onConfirm: () => api.post(`unlock/adopt/${idea.id}`),
    });
    if (result) {
      toast(serverText(result.message) || t('adopt.done'), 'success');
      location.href = 'projects.html';
    }
  });
}

formView();
