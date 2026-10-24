// Admin dashboard: live numbers and charts from the database, and investor verification.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, errorMessage, modal, skeletonCards, LABELS, label, withLoading, applyFieldErrors, t } from '../ui.js';
import { locale, tn } from '../i18n.js';

await guard(['ADMIN']);
const app = $('#app');
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const PALETTE = ['#2F5444', '#5A7D66', '#A9C3B2', '#C9B386', '#E8E0D3', '#9C9384', '#C2543F'];

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px"><span class="eyebrow">${esc(t('role.ADMIN'))}</span><h1>${t('adm.title')}</h1></div>
    <button type="button" class="btn btn-secondary" id="refresh" data-loading="${esc(t('adm.refreshing'))}">${esc(t('adm.refresh'))}</button>
  </div>
  <div class="grid grid-4" id="kpis" style="margin-bottom:24px">${skeletonCards(4, 100)}</div>
  <div class="grid" style="margin-bottom:24px;grid-template-columns:repeat(auto-fit,minmax(min(100%,480px),1fr))">
    <section class="card"><div class="card-head"><h2>${esc(t('adm.votes30'))}</h2></div><div style="height:240px"><canvas id="c-votes" role="img" aria-label="${esc(t('adm.votes30'))}"></canvas></div></section>
    <section class="card"><div class="card-head"><h2>${esc(t('adm.ideasByStatus'))}</h2></div><div style="height:240px"><canvas id="c-ideas" role="img" aria-label="${esc(t('adm.ideasByStatus'))}"></canvas></div></section>
    <section class="card"><div class="card-head"><h2>${esc(t('adm.topHoods'))}</h2><span class="small muted">${esc(t('adm.publishedIdeas'))}</span></div><div style="height:240px"><canvas id="c-hoods" role="img" aria-label="${esc(t('adm.topHoods'))}"></canvas></div></section>
    <section class="card"><div class="card-head"><h2>${esc(t('adm.membersUnlocks'))}</h2></div><div class="grid" style="gap:12px;grid-template-columns:repeat(auto-fit,minmax(min(100%,180px),1fr))"><div style="height:220px"><canvas id="c-roles" role="img" aria-label="${esc(t('adm.byRole'))}"></canvas></div><div style="height:220px"><canvas id="c-access" role="img" aria-label="${esc(t('adm.byType'))}"></canvas></div></div></section>
  </div>
  <section class="card stack" id="investors" style="gap:16px;scroll-margin-top:90px" aria-labelledby="inv-h">
    <div class="card-head" style="margin:0;flex-wrap:wrap">
      <div class="stack" style="gap:4px"><h2 id="inv-h">${esc(t('adm.invTitle'))}</h2><span class="small muted">${esc(t('adm.invText'))}</span></div>
      <div class="segmented" role="group" aria-label="${esc(t('adm.statusFilter'))}" id="status-filter">
        ${['PENDING', 'VERIFIED', 'REJECTED', 'ALL'].map((s, i) => `<button type="button" data-status="${s}" aria-pressed="${i === 0}">${esc(s === 'ALL' ? t('res.all') : label('investorStatus', s))}</button>`).join('')}
      </div>
    </div>
    <div class="row">
      <label class="sr-only" for="search">${esc(t('adm.searchInv'))}</label>
      <input class="input" id="search" type="search" placeholder="${esc(t('adm.searchInvPh'))}" style="max-width:360px">
      <span class="small muted" id="inv-count" aria-live="polite"></span>
    </div>
    <div class="table-wrap">
      <table class="table">
        <thead><tr>
          <th scope="col"><button type="button" data-sort="fullName">${esc(t('role.INVESTOR'))} ↕</button></th>
          <th scope="col"><button type="button" data-sort="crNumber">${esc(t('adm.cr'))} ↕</button></th>
          <th scope="col">${esc(t('adm.budget'))}</th>
          <th scope="col">${esc(t('adm.fields'))}</th>
          <th scope="col"><button type="button" data-sort="investorStatus">${esc(t('adm.status'))} ↕</button></th>
          <th scope="col"><span class="sr-only">${esc(t('adm.actions'))}</span></th>
        </tr></thead>
        <tbody id="inv-rows"><tr><td colspan="6"><div class="skeleton" style="height:48px"></div></td></tr></tbody>
      </table>
    </div>
    <div class="pager"><span id="page-info"></span><div class="row"><button type="button" class="btn btn-secondary btn-sm" id="prev">${esc(t('adm.prev'))}</button><button type="button" class="btn btn-secondary btn-sm" id="next">${esc(t('adm.next'))}</button></div></div>
  </section>
  <section class="card stack" id="neighborhoods" style="gap:16px;margin-top:24px;scroll-margin-top:90px" aria-labelledby="hood-h">
    <div class="card-head" style="margin:0;flex-wrap:wrap">
      <div class="stack" style="gap:4px"><h2 id="hood-h">${esc(t('adm.hoods'))}</h2><span class="small muted">${esc(t('adm.hoodsText'))}</span></div>
      <span class="small muted" id="hood-count" aria-live="polite"></span>
    </div>
    <div class="split" style="grid-template-columns:minmax(0,1.6fr) minmax(0,1fr)">
      <div class="stack" style="gap:12px">
        <label class="sr-only" for="hood-search">${esc(t('adm.searchHoods'))}</label>
        <input class="input" id="hood-search" type="search" placeholder="${esc(t('adm.searchHoodsPh'))}" style="max-width:360px">
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th scope="col">${esc(t('adm.name'))}</th><th scope="col">${esc(t('form.city'))}</th><th scope="col">${esc(t('common.location'))}</th><th scope="col"><span class="sr-only">${esc(t('adm.actions'))}</span></th></tr></thead>
            <tbody id="hood-rows"><tr><td colspan="4"><div class="skeleton" style="height:48px"></div></td></tr></tbody>
          </table>
        </div>
      </div>
      <form class="surface-sand stack" id="hood-form" style="gap:12px" novalidate>
        <strong>${esc(t('adm.addHood'))}</strong>
        <div class="field"><label for="h-name">${esc(t('adm.name'))}</label><input class="input" id="h-name" name="name" required maxlength="100"><span class="error" aria-live="polite"></span></div>
        <div class="field"><label for="h-city">${esc(t('form.city'))}</label><input class="input" id="h-city" name="city" required maxlength="50" value="Riyadh"><span class="error" aria-live="polite"></span></div>
        <div class="grid grid-2" style="gap:10px;grid-template-columns:1fr 1fr">
          <div class="field"><label for="h-lat">${esc(t('adm.lat'))}</label><input class="input" id="h-lat" name="latitude" inputmode="decimal" placeholder="24.8270"><span class="error" aria-live="polite"></span></div>
          <div class="field"><label for="h-lng">${esc(t('adm.lng'))}</label><input class="input" id="h-lng" name="longitude" inputmode="decimal" placeholder="46.6540"><span class="error" aria-live="polite"></span></div>
        </div>
        <button class="btn" type="submit" data-loading="${esc(t('adm.adding'))}">${esc(t('adm.addHood'))}</button>
        <span class="small muted">${esc(t('adm.hoodRules'))}</span>
      </form>
    </div>
  </section>`;

// Jump to a section from the header links
function jump() {
  const target = location.hash && document.querySelector(location.hash);
  if (target) setTimeout(() => target.scrollIntoView({ behavior: 'smooth' }), 300);
}
window.addEventListener('hashchange', jump);
jump();

// ---------- Charts ----------
const charts = {};
function chart(id, config) {
  charts[id]?.destroy();
  if (!window.Chart) return;
  config.options = {
    maintainAspectRatio: false,
    animation: { duration: reduce ? 0 : 700 },
    plugins: { legend: { labels: { color: '#3E4A43', boxWidth: 12, font: { family: 'Inter' } } } },
    ...config.options,
  };
  charts[id] = new Chart($(`#${id}`), config);
}

const nonZero = (rows) => rows.filter((r) => r.count > 0);

async function loadStats() {
  let s;
  try {
    s = await api.get('admin/stats');
  } catch (err) {
    $('#kpis').innerHTML = `<div class="notice notice-error" style="grid-column:1/-1">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  const users = s.usersByRole.reduce((sum, r) => sum + r.count, 0);
  const live = s.ideasByStatus.filter((r) => ['PUBLISHED', 'COMPLETED'].includes(r.label)).reduce((sum, r) => sum + r.count, 0);
  $('#kpis').innerHTML = [
    [fmt.num(users), t('adm.kpiMembers')],
    [fmt.num(live), t('adm.kpiOpen')],
    [fmt.num(s.totalVotes), t('adm.kpiVotes')],
    [fmt.num(s.pendingInvestors), t('adm.kpiPending'), s.pendingInvestors > 0],
  ].map(([v, l, alert]) => `<div class="card kpi"${alert ? ' style="border-color:#E9CFA0;background:var(--amber-50)"' : ''}><span class="value">${esc(v)}</span><span class="label">${esc(l)}</span></div>`).join('')
    + `<div class="card kpi"><span class="value" style="font-size:28px">${fmt.money(s.paidRevenue)}</span><span class="label">${esc(t('adm.kpiRevenue'))}</span></div>`;

  chart('c-votes', {
    type: 'line',
    data: {
      labels: s.votesPerDay.map((d) => new Date(d.label).toLocaleDateString(locale, { day: 'numeric', month: 'short' })),
      datasets: [{ label: t('common.votes'), data: s.votesPerDay.map((d) => d.count), borderColor: '#2F5444', backgroundColor: 'rgba(90,125,102,0.15)', fill: true, tension: 0.35, pointRadius: 0, pointHoverRadius: 5 }],
    },
    options: {
      plugins: { legend: { display: false } },
      interaction: { intersect: false, mode: 'index' },
      scales: { x: { ticks: { maxTicksLimit: 8, color: '#5E6A63' }, grid: { display: false } }, y: { beginAtZero: true, ticks: { precision: 0, color: '#5E6A63' }, grid: { color: '#ECE6DC' } } },
    },
  });

  const ideas = nonZero(s.ideasByStatus);
  chart('c-ideas', {
    type: 'doughnut',
    data: { labels: ideas.map((r) => label('status', r.label)), datasets: [{ data: ideas.map((r) => r.count), backgroundColor: PALETTE, borderColor: '#fff', borderWidth: 2 }] },
    options: { cutout: '62%', plugins: { legend: { position: 'right', rtl: document.dir === 'rtl', labels: { color: '#3E4A43', boxWidth: 12 } } } },
  });

  chart('c-hoods', {
    type: 'bar',
    data: { labels: s.topNeighborhoods.map((r) => r.label), datasets: [{ label: t('adm.ideas'), data: s.topNeighborhoods.map((r) => r.count), backgroundColor: '#5A7D66', hoverBackgroundColor: '#2F5444', borderRadius: 6 }] },
    options: { indexAxis: 'y', plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#ECE6DC' } }, y: { grid: { display: false } } } },
  });

  const roles = nonZero(s.usersByRole);
  chart('c-roles', {
    type: 'doughnut',
    data: { labels: roles.map((r) => label('role', r.label)), datasets: [{ data: roles.map((r) => r.count), backgroundColor: PALETTE, borderColor: '#fff', borderWidth: 2 }] },
    options: { cutout: '60%', plugins: { legend: { position: 'bottom' }, title: { display: true, text: t('adm.byRole'), color: '#1F2A24' } } },
  });

  chart('c-access', {
    type: 'bar',
    data: { labels: s.accessByType.map((r) => label('access', r.label)), datasets: [{ data: s.accessByType.map((r) => r.count), backgroundColor: ['#2F5444', '#A9C3B2', '#C9B386'], borderRadius: 6 }] },
    options: { plugins: { legend: { display: false }, title: { display: true, text: t('adm.byType'), color: '#1F2A24' } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#ECE6DC' } }, x: { grid: { display: false } } } },
  });
}

// ---------- Investor verification table ----------
const PAGE_SIZE = 10;
const table = { status: 'PENDING', rows: [], query: '', sort: 'fullName', dir: 1, page: 0 };

async function loadInvestors() {
  $('#inv-rows').innerHTML = '<tr><td colspan="6"><div class="skeleton" style="height:48px"></div></td></tr>';
  try {
    table.rows = await api.get('admin/investors', { status: table.status });
    table.page = 0;
    renderTable();
  } catch (err) {
    $('#inv-rows').innerHTML = `<tr><td colspan="6"><div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div></td></tr>`;
  }
}

function statusOf(u) {
  return u.investorStatus || 'PENDING';
}

function renderTable() {
  const q = table.query.toLowerCase();
  const rows = table.rows
    .filter((u) => !q || [u.fullName, u.email, u.crNumber].some((v) => (v || '').toLowerCase().includes(q)))
    .sort((a, b) => {
      const av = table.sort === 'investorStatus' ? statusOf(a) : (a[table.sort] || '');
      const bv = table.sort === 'investorStatus' ? statusOf(b) : (b[table.sort] || '');
      return av.localeCompare(bv) * table.dir;
    });
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  table.page = Math.min(table.page, pages - 1);
  const shown = rows.slice(table.page * PAGE_SIZE, (table.page + 1) * PAGE_SIZE);

  $('#inv-count').textContent = tn(rows.length, 'count.investors');
  $('#page-info').textContent = rows.length ? t('adm.page', { p: table.page + 1, n: pages }) : '';
  $('#prev').disabled = table.page === 0;
  $('#next').disabled = table.page >= pages - 1;

  $('#inv-rows').innerHTML = shown.length ? shown.map((u) => {
    const st = statusOf(u);
    const badge = st === 'VERIFIED' ? 'badge-solid' : st === 'REJECTED' ? 'badge-red' : 'badge-amber';
    const fields = (u.investmentFields || '').split(',').filter(Boolean).map((f) => LABELS.field[f] || f).join(', ');
    return `<tr>
      <td><strong>${esc(u.fullName)}</strong><br><span class="small muted">${esc(u.email)}${u.phone ? ' · ' + esc(u.phone) : ''}</span></td>
      <td class="num">${esc(u.crNumber || '—')}</td>
      <td>${esc(LABELS.budget[u.investmentBudget] || '—')}</td>
      <td class="small">${esc(fields || '—')}</td>
      <td><span class="badge ${badge}">${esc(label('investorStatus', st))}</span>${u.emailVerified ? '' : `<br><span class="small muted">${esc(t('adm.emailNotVerified'))}</span>`}</td>
      <td style="white-space:nowrap;text-align:end">
        ${st !== 'VERIFIED' ? `<button type="button" class="btn btn-sm" data-verify="${u.id}">${esc(t('adm.verify'))}</button>` : ''}
        ${st !== 'REJECTED' ? `<button type="button" class="btn btn-danger btn-sm" data-reject="${u.id}">${esc(t('adm.reject'))}</button>` : ''}
      </td></tr>`;
  }).join('') : `<tr><td colspan="6"><div class="empty" style="padding:24px">${icon.check}<span>${esc(t(table.status === 'PENDING' ? 'adm.noPending' : 'adm.noInvestors'))}</span></div></td></tr>`;

  $$('[data-verify]').forEach((b) => b.addEventListener('click', () => review(Number(b.dataset.verify), 'verify')));
  $$('[data-reject]').forEach((b) => b.addEventListener('click', () => review(Number(b.dataset.reject), 'reject')));
}

async function review(id, action) {
  const u = table.rows.find((x) => x.id === id);
  const ok = await modal({
    title: t(action === 'verify' ? 'adm.verifyQ' : 'adm.rejectQ'),
    body: `<p class="muted">${esc(u.fullName)} · ${esc(t('adm.crShort'))} <strong class="num">${esc(u.crNumber || '—')}</strong></p>
      <p class="small muted">${esc(t(action === 'verify' ? 'adm.verifyText' : 'adm.rejectText'))}</p>`,
    confirmText: t(action === 'verify' ? 'adm.verify' : 'adm.reject'),
    danger: action === 'reject',
    onConfirm: () => api.put(`admin/investors/${action}/${id}`),
  });
  if (ok) {
    toast(t(action === 'verify' ? 'adm.verified' : 'adm.rejected', { name: u.fullName }), 'success');
    await Promise.all([loadInvestors(), loadStats()]);
  }
}

$$('#status-filter button').forEach((b) => b.addEventListener('click', () => {
  table.status = b.dataset.status;
  $$('#status-filter button').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
  loadInvestors();
}));
let searchTimer;
$('#search').addEventListener('input', (e) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => { table.query = e.target.value.trim(); table.page = 0; renderTable(); }, 200);
});
$$('[data-sort]').forEach((b) => b.addEventListener('click', () => {
  table.dir = table.sort === b.dataset.sort ? -table.dir : 1;
  table.sort = b.dataset.sort;
  renderTable();
}));
$('#prev').addEventListener('click', () => { table.page -= 1; renderTable(); });
$('#next').addEventListener('click', () => { table.page += 1; renderTable(); });
$('#refresh').addEventListener('click', async (e) => {
  const btn = e.currentTarget;
  btn.disabled = true;
  try { await Promise.all([loadStats(), loadInvestors()]); } catch (err) { toast(errorMessage(err), 'error'); }
  btn.disabled = false;
});

// ---------- Neighborhoods ----------
const hoods = { rows: [], query: '' };

async function loadHoods() {
  try {
    hoods.rows = (await api.get('neighborhoods/get-all')).sort((a, b) => a.city.localeCompare(b.city) || a.name.localeCompare(b.name));
    renderHoods();
  } catch (err) {
    $('#hood-rows').innerHTML = `<tr><td colspan="4"><div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div></td></tr>`;
  }
}

function renderHoods() {
  const q = hoods.query.toLowerCase();
  const rows = hoods.rows.filter((h) => !q || h.name.toLowerCase().includes(q) || h.city.toLowerCase().includes(q));
  $('#hood-count').textContent = tn(hoods.rows.length, 'count.hoods');
  $('#hood-rows').innerHTML = rows.length ? rows.map((h) => `<tr>
      <td><strong>${esc(h.name)}</strong></td>
      <td>${esc(h.city)}</td>
      <td class="num small">${h.latitude != null ? `${Number(h.latitude).toFixed(4)}, ${Number(h.longitude).toFixed(4)}` : `<span class="badge badge-amber">${esc(t('adm.noCoords'))}</span>`}</td>
      <td style="white-space:nowrap;text-align:end">
        <button type="button" class="btn btn-secondary btn-sm" data-edit="${h.id}">${esc(t('adm.edit'))}</button>
        <button type="button" class="btn btn-danger btn-sm" data-delete="${h.id}">${esc(t('adm.delete'))}</button>
      </td></tr>`).join('')
    : `<tr><td colspan="4" class="muted">${q ? esc(t('adm.noMatch', { q: hoods.query })) : esc(t('adm.noHoods'))}</td></tr>`;
  $$('[data-edit]').forEach((b) => b.addEventListener('click', () => editHood(Number(b.dataset.edit))));
  $$('[data-delete]').forEach((b) => b.addEventListener('click', () => deleteHood(Number(b.dataset.delete))));
}

function hoodBody(name, city, lat, lng) {
  const num = (v) => (String(v).trim() === '' ? null : Number(v));
  return { name: name.trim(), city: city.trim(), latitude: num(lat), longitude: num(lng) };
}

async function editHood(id) {
  const h = hoods.rows.find((x) => x.id === id);
  const ok = await modal({
    title: t('adm.editTitle', { name: h.name }),
    body: `<div class="field"><label for="e-name">${esc(t('adm.name'))}</label><input class="input" id="e-name" value="${esc(h.name)}"></div>
      <div class="field"><label for="e-city">${esc(t('form.city'))}</label><input class="input" id="e-city" value="${esc(h.city)}"></div>
      <div class="grid" style="gap:10px;grid-template-columns:1fr 1fr">
        <div class="field"><label for="e-lat">${esc(t('adm.lat'))}</label><input class="input" id="e-lat" inputmode="decimal" value="${h.latitude ?? ''}"></div>
        <div class="field"><label for="e-lng">${esc(t('adm.lng'))}</label><input class="input" id="e-lng" inputmode="decimal" value="${h.longitude ?? ''}"></div>
      </div>`,
    confirmText: t('common.save'),
    onConfirm: (box) => api.put(`neighborhoods/update/${id}`, hoodBody(box.querySelector('#e-name').value, box.querySelector('#e-city').value, box.querySelector('#e-lat').value, box.querySelector('#e-lng').value)),
  });
  if (ok) { toast(t('adm.hoodSaved'), 'success'); loadHoods(); }
}

async function deleteHood(id) {
  const h = hoods.rows.find((x) => x.id === id);
  const ok = await modal({
    title: t('adm.deleteTitle', { name: h.name }),
    body: `<p class="muted">${esc(t('adm.deleteText'))}</p>`,
    confirmText: t('adm.delete'),
    danger: true,
    onConfirm: () => api.del(`neighborhoods/delete/${id}`),
  });
  if (ok) { toast(t('adm.hoodDeleted', { name: h.name }), 'success'); loadHoods(); }
}

$('#hood-search').addEventListener('input', (e) => { hoods.query = e.target.value.trim(); renderHoods(); });
$('#hood-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  applyFieldErrors(form, null);
  const body = hoodBody(form.name.value, form.city.value, form.latitude.value, form.longitude.value);
  withLoading(form.querySelector('[type="submit"]'), async () => {
    try {
      await api.post('neighborhoods/create', body);
      toast(t('adm.hoodAdded', { name: body.name }), 'success');
      form.name.value = '';
      form.latitude.value = '';
      form.longitude.value = '';
      loadHoods();
    } catch (err) {
      if (!applyFieldErrors(form, err)) toast(errorMessage(err), 'error');
    }
  });
});

loadStats();
loadInvestors();
loadHoods();
