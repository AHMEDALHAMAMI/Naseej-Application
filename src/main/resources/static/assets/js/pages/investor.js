// Investor home: Saudi map with cities → zoom into a city's neighborhoods → a neighborhood's opportunities.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, errorMessage, statusBadge, label, progressBar, animateBars, emptyState, skeletonCards, categoryLabel, t } from '../ui.js';
import { tn, serverText } from '../i18n.js';
import { createMap, bubbleIcon, pinIcon, keyboardMarker, SAUDI_BOUNDS } from '../maps.js';

const user = await guard(['INVESTOR']);
const verified = user.investorStatus === 'VERIFIED';
const app = $('#app');

app.innerHTML = `
  ${verified ? '' : `<div class="notice ${user.investorStatus === 'REJECTED' ? 'notice-error' : 'notice-warn'}" style="margin-bottom:20px">${icon.info}
    <span>${user.investorStatus === 'REJECTED'
      ? t('inv.bannerRejected')
      : t('inv.bannerPending')}</span></div>`}
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t('inv.eyebrow'))}</span>
      <h1>${t('inv.title')}</h1>
    </div>
    <a class="btn btn-secondary" href="propose.html">${icon.spark} ${esc(t('inv.proposeOwn'))}</a>
  </div>
  <div class="grid grid-4" style="margin-bottom:24px" id="kpis">${skeletonCards(4, 92)}</div>
  <div class="split">
    <div class="stack" style="gap:12px">
      <nav class="crumbs" aria-label="${esc(t('inv.mapLevel'))}" id="crumbs"></nav>
      <div class="map map-tall" id="map" aria-label="${esc(t('inv.mapLabel'))}"></div>
    </div>
    <div class="stack" style="gap:14px" id="side"></div>
  </div>`;

const map = createMap($('#map'), { zoom: 5, minZoom: 4 });
map.fitBounds(SAUDI_BOUNDS);
const layer = L.layerGroup().addTo(map);
let cities = [];
let chart = null;
const view = { city: null, hood: null };

// ---------- Level 1: cities ----------
async function showCountry() {
  view.city = null;
  view.hood = null;
  crumbs();
  layer.clearLayers();
  map.flyToBounds(SAUDI_BOUNDS, { duration: 0.8 });
  const side = $('#side');
  side.innerHTML = skeletonCards(2, 160);
  try {
    cities = await api.get('explore/cities');
  } catch (err) {
    side.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  kpis();
  if (!cities.length) {
    side.innerHTML = emptyState(t('inv.noOpps'), t('inv.noOppsText'));
    return;
  }
  cities.filter((c) => c.latitude != null).forEach((c) => {
    keyboardMarker(L.marker([c.latitude, c.longitude], { icon: bubbleIcon(c.opportunities) }), `${c.city}, ${tn(c.opportunities, 'count.opps')}`)
      .bindTooltip(c.city, { className: 'nsj-label', direction: 'bottom', permanent: true, offset: [0, 16] })
      .on('click', () => showCity(c.city))
      .addTo(layer);
  });
  side.innerHTML = `
    <section class="card stack" style="gap:12px">
      <div class="card-head" style="margin:0"><h2>${esc(t('inv.byCity'))}</h2><span class="small muted">${esc(t('inv.tapCity'))}</span></div>
      <div style="height:${Math.max(160, cities.length * 34)}px"><canvas id="city-chart" aria-label="${esc(t('inv.chartLabel'))}" role="img"></canvas></div>
    </section>
    <ul class="list" aria-label="${esc(t('inv.cities'))}">
      ${cities.map((c) => `<li><button type="button" class="btn btn-ghost btn-sm" data-city="${esc(c.city)}" style="padding:0">${esc(c.city)}</button><span class="muted small num">${esc(tn(c.opportunities, 'count.opps'))} · ${esc(tn(c.neighborhoods, 'count.hoods'))}</span></li>`).join('')}
    </ul>`;
  $$('[data-city]').forEach((b) => b.addEventListener('click', () => showCity(b.dataset.city)));
  drawCityChart();
}

function drawCityChart() {
  if (!window.Chart) return;
  chart?.destroy();
  const top = cities.slice(0, 10);
  chart = new Chart($('#city-chart'), {
    type: 'bar',
    data: {
      labels: top.map((c) => c.city),
      datasets: [{ data: top.map((c) => c.opportunities), backgroundColor: '#5A7D66', hoverBackgroundColor: '#2F5444', borderRadius: 6, barThickness: 18 }],
    },
    options: {
      indexAxis: 'y',
      maintainAspectRatio: false,
      animation: { duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 700 },
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (ctx) => tn(ctx.parsed.x, 'count.opps') } } },
      scales: {
        x: { beginAtZero: true, ticks: { precision: 0, color: '#5E6A63' }, grid: { color: '#ECE6DC' } },
        y: { ticks: { color: '#1F2A24', font: { weight: 600 } }, grid: { display: false } },
      },
      onClick: (_, els) => { if (els.length) showCity(top[els[0].index].city); },
    },
  });
}

function kpis() {
  const total = cities.reduce((s, c) => s + c.opportunities, 0);
  const hoods = cities.reduce((s, c) => s + c.neighborhoods, 0);
  $('#kpis').innerHTML = [
    [fmt.num(total), t('inv.kpiOpen')],
    [fmt.num(cities.length), t('inv.kpiCities')],
    [fmt.num(hoods), t('inv.kpiHoods')],
    [label('investorStatus', user.investorStatus || 'PENDING'), t('inv.kpiStatus')],
  ].map(([v, l]) => `<div class="card kpi"><span class="value" style="font-size:${String(v).length > 6 ? 26 : 40}px">${esc(v)}</span><span class="label">${esc(l)}</span></div>`).join('');
}

// ---------- Level 2: a city's neighborhoods ----------
async function showCity(city) {
  view.city = city;
  view.hood = null;
  crumbs();
  layer.clearLayers();
  const side = $('#side');
  side.innerHTML = skeletonCards(3, 70);
  let hoods;
  try {
    hoods = await api.get(`explore/neighborhoods/${encodeURIComponent(city)}`);
  } catch (err) {
    side.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  const located = hoods.filter((h) => h.latitude != null);
  if (located.length) {
    map.flyToBounds(L.latLngBounds(located.map((h) => [h.latitude, h.longitude])).pad(0.35), { duration: 1.1, maxZoom: 14 });
  }
  located.forEach((h) => {
    keyboardMarker(L.marker([h.latitude, h.longitude], { icon: bubbleIcon(h.opportunities, { size: 40 + Math.min(24, h.opportunities * 3) }) }), `${h.name}, ${tn(h.opportunities, 'count.opps')}`)
      .bindTooltip(h.name, { className: 'nsj-label', direction: 'bottom', permanent: true, offset: [0, 22] })
      .on('click', () => showNeighborhood(h))
      .addTo(layer);
  });
  side.innerHTML = `
    <div class="stack" style="gap:4px"><h2 style="font-size:28px">${esc(city)}</h2><span class="muted">${esc(t('inv.cityTotal', { opps: tn(hoods.reduce((s, h) => s + h.opportunities, 0), 'count.opps'), hoods: tn(hoods.length, 'count.hoods') }))}</span></div>
    <ul class="list">${hoods.map((h) => `<li><button type="button" class="btn btn-ghost btn-sm" data-hood="${h.neighborhoodId}" style="padding:0">${esc(h.name)}</button><span class="badge">${fmt.num(h.opportunities)}</span></li>`).join('')}</ul>`;
  $$('[data-hood]').forEach((b) => b.addEventListener('click', () => showNeighborhood(hoods.find((h) => h.neighborhoodId === Number(b.dataset.hood)))));
}

const isTaken = (o) => o.status === 'ADOPTED' || o.status === 'OPENED';

// ---------- Level 3: one neighborhood's opportunities ----------
async function showNeighborhood(hood) {
  view.hood = hood;
  crumbs();
  layer.clearLayers();
  const side = $('#side');
  side.innerHTML = skeletonCards(3, 170);
  let opps;
  try {
    opps = await api.get(`explore/opportunities/${hood.neighborhoodId}`);
  } catch (err) {
    side.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  const points = opps.filter((o) => o.latitude != null).map((o) => [o.latitude, o.longitude]);
  if (points.length) map.flyToBounds(L.latLngBounds(points).pad(0.5), { duration: 1, maxZoom: 16 });
  else if (hood.latitude != null) map.flyTo([hood.latitude, hood.longitude], 15);

  opps.filter((o) => o.latitude != null).forEach((o) => {
    keyboardMarker(L.marker([o.latitude, o.longitude], { icon: pinIcon(isTaken(o) && !o.unlocked ? 'muted' : o.status === 'COMPLETED' ? 'in-range' : '') }), o.title)
      .bindTooltip(`<strong>${esc(o.title)}</strong><br>${esc(t('inv.ofTarget', { p: o.votePercent }))}`, { className: 'nsj-tip', direction: 'top' })
      .on('click', () => $(`#opp-${o.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }))
      .addTo(layer);
  });

  side.innerHTML = `
    <div class="stack" style="gap:4px"><h2 style="font-size:28px">${esc(hood.name)}</h2><span class="muted">${esc(tn(opps.length, 'count.opps'))} · ${esc(hood.city)}</span></div>
    ${opps.length ? opps.map(oppCard).join('') : emptyState(t('inv.nothingHere'), t('inv.nothingHereText'))}`;
  animateBars(side);
}

function oppCard(o) {
  const unlock = o.unlocked
    ? `<a class="btn btn-sm" href="projects.html">${esc(t('inv.inProjects'))}</a>`
    : isTaken(o)
      ? `<span class="badge badge-sand">${esc(t('inv.taken'))}</span>`
      : o.canUnlock
      ? `<a class="btn btn-sm" href="unlock.html?id=${o.id}">${esc(t('inv.unlock'))}</a>`
      : `<button type="button" class="btn btn-sm" disabled title="${esc(!verified ? t('inv.afterVerify') : t('inv.opensAt100'))}">${icon.lock} ${esc(t('inv.unlock'))}</button>`;
  return `
    <article class="idea-card" id="opp-${o.id}">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:2px"><span class="title">${esc(o.title)}</span><span class="meta"><span>${esc(categoryLabel(o.category))}</span><span>${esc(serverText(o.locationLabel))}</span></span></div>
        ${statusBadge(o.status)}
      </div>
      <div class="stack" style="gap:6px">
        <div class="row small" style="justify-content:space-between"><strong>${esc(t('inv.ofTarget', { p: o.votePercent }))}</strong><span class="muted num">${fmt.num(o.votesCount)} / ${fmt.num(o.membershipThreshold)} ${esc(t('common.votes'))}</span></div>
        ${progressBar(o.votePercent, t('common.voteTarget'))}
      </div>
      ${o.gapScore != null ? `<span class="small muted">${esc(t('inv.gapScore'))} <strong style="color:var(--ink)">${o.gapScore}</strong>/100</span>` : ''}
      <div class="actions"><a class="btn btn-secondary btn-sm" href="idea.html?id=${o.id}">${esc(t('common.viewDetails'))}</a>${unlock}</div>
    </article>`;
}

function crumbs() {
  const parts = [`<button type="button" data-go="country">${esc(t('inv.ksa'))}</button>`];
  if (view.city) parts.push(`<span aria-hidden="true" class="flip-rtl">›</span><button type="button" data-go="city">${esc(view.city)}</button>`);
  if (view.hood) parts.push(`<span aria-hidden="true" class="flip-rtl">›</span><span style="color:var(--ink);font-weight:600" aria-current="location">${esc(view.hood.name)}</span>`);
  $('#crumbs').innerHTML = parts.join('');
  $('[data-go="country"]').onclick = showCountry;
  const cityBtn = $('[data-go="city"]');
  if (cityBtn) cityBtn.onclick = () => showCity(view.city);
}

showCountry().catch((err) => toast(errorMessage(err), 'error'));
