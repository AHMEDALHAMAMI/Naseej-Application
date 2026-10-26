// Resident, founder and admin view of the idea page (loaded by idea.js). The server decides what each viewer may see:
// residents never get vote counts or the business model; the founder and investors do.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, statusBadge, progressBar, animateBars, label, categoryLabel, ideaName, lifeTrack, isolate, t } from '../ui.js';
import { aiBlock, scoreMeter, wirePopovers, earlyStart } from '../components.js';
import { serverText } from '../i18n.js';
import { createMap, pinIcon, meIcon, approxCircle } from '../maps.js';

const user = await guard(['RESIDENT', 'INVESTOR', 'ADMIN']);
const id = Number(params().get('id'));
const app = $('#app');
if (!id) location.replace('index.html');

const backHref = user.role === 'INVESTOR' ? 'investor.html' : user.role === 'ADMIN' ? 'admin.html' : 'resident.html';
const backLink = (style = '') => `<a class="btn btn-ghost btn-sm" href="${backHref}" style="padding-inline-start:0;${style}">${icon.back} ${esc(t('common.back'))}</a>`;

async function load() {
  try {
    // Residents are measured from their saved addresses on the server; no location is sent
    const [idea, book] = await Promise.all([
      api.get(`discover/idea/${id}`),
      user.role === 'RESIDENT' ? api.get('account/addresses').catch(() => null) : null,
    ]);
    homes = book?.addresses || [];
    render(idea);
  } catch (err) {
    app.innerHTML = `<div class="notice notice-error" style="margin-top:24px">${icon.x}<span>${esc(errorMessage(err))}</span></div>
      <p style="margin-top:16px">${backLink()}</p>`;
  }
}

function render(idea) {
  if (idea.teaser) {
    renderTeaser(idea);
    return;
  }
  document.title = `${idea.title} — ${t('brand.short')}`;
  const seesNumbers = idea.votesCount !== null && idea.votesCount !== undefined;
  const isResident = user.role === 'RESIDENT';
  const isInvestor = user.role === 'INVESTOR';

  app.innerHTML = `
    ${backLink()}
    <div class="page-head" style="margin-top:8px">
      <div class="stack" style="gap:10px">
        <div class="row">${statusBadge(idea.status)}${idea.founder ? `<span class="badge badge-sand">${esc(t('idea.founderBadge'))}</span>` : ''}${idea.category ? `<span class="badge badge-sand">${esc(categoryLabel(idea.category))}</span>` : ''}</div>
        <h1 dir="auto">${esc(idea.title)}</h1>
        <span class="muted row" style="gap:6px">${icon.pin}${esc(isolate(`${idea.neighborhoodName}, ${idea.city}`))} · ${esc(serverText(idea.locationLabel || ''))}</span>
      </div>
    </div>
    ${(idea.warnings || []).map((w) => `<div class="notice notice-warn" style="margin-bottom:12px">${icon.warn}<span>${esc(serverText(w))}</span></div>`).join('')}
    ${idea.founder && ['DRAFT', 'REJECTED'].includes(idea.status) ? draftBox(idea) : ''}
    <div class="split">
      <div class="stack" style="gap:20px">
        <section class="card stack" style="gap:12px">
          <h2>${esc(t('idea.why'))}</h2>
          <p style="white-space:pre-line" dir="auto">${esc(idea.description || "—")}</p>
          <div class="map" id="mini-map" style="height:280px" aria-label="${esc(t('idea.mapLabel'))}"></div>
        </section>
        ${idea.businessModel ? businessModelCard(idea.businessModel) : (seesNumbers && idea.founder && ['PUBLISHED', 'COMPLETED'].includes(idea.status) ? missingModelCard() : '')}
        ${idea.feasibilityReport ? feasibilityCard(idea.feasibilityReport) : ''}
        ${checkCard(idea)}
        ${mapCard(idea)}
      </div>
      <aside class="stack" style="gap:20px">
        ${lifeCard(idea)}
        ${isResident ? voteCard(idea) : ''}
        ${seesNumbers ? votesCard(idea) : ''}
        ${isInvestor ? investorCard(idea) : ''}
      </aside>
    </div>`;

  // Mini map with the idea and, for residents, their voting radius
  if (idea.latitude != null) {
    const map = createMap($('#mini-map'), { center: { lat: idea.latitude, lng: idea.longitude }, zoom: 15, scrollWheelZoom: false });
    L.marker([idea.latitude, idea.longitude], { icon: pinIcon(idea.canVote ? 'in-range' : '') }).addTo(map);
    const home = isResident ? homes.find((a) => a.id === idea.voteAddressId) : null;
    if (home) {
      L.marker([home.latitude, home.longitude], { icon: meIcon(), interactive: false, keyboard: false }).addTo(map);
      const circle = L.circle([home.latitude, home.longitude], { radius: (idea.votingRadiusKm || 2) * 1000, color: '#2F5444', weight: 1.5, fillOpacity: 0.06, dashArray: '6 6' }).addTo(map);
      map.fitBounds(circle.getBounds().extend([idea.latitude, idea.longitude]), { padding: [20, 20] });
    }
  } else {
    $('#mini-map').remove();
  }
  animateBars(app);
  wirePopovers(app);
  wire(idea);
}

// ---------- Investor before unlocking: a confidential summary ----------

function renderTeaser(idea) {
  const name = ideaName(idea);
  document.title = `${name} — ${t('brand.short')}`;
  const cap = idea.capitalMin != null ? `${fmt.money(idea.capitalMin)} – ${fmt.money(idea.capitalMax)}` : t('bm.notEstimated');
  const kpi = (value, text, extra = '') => `<div class="surface-sand kpi"><span class="value" style="font-size:${String(value).length > 7 ? 20 : 30}px">${value}</span><span class="label">${esc(text)}</span>${extra}</div>`;

  app.innerHTML = `
    ${backLink()}
    <div class="page-head" style="margin-top:8px">
      <div class="stack" style="gap:10px">
        <div class="row">${statusBadge(idea.status)}<span class="badge badge-lock">${icon.lock} ${esc(t('teaser.badge'))}</span>${idea.category ? `<span class="badge badge-sand">${esc(categoryLabel(idea.category))}</span>` : ''}</div>
        <h1 dir="auto">${esc(name)}</h1>
        <span class="muted row" style="gap:6px">${icon.pin}${esc(isolate(`${idea.neighborhoodName}, ${idea.city}`))} · ${esc(t('teaser.approxArea'))}</span>
      </div>
    </div>
    <div class="split">
      <div class="stack" style="gap:20px">
        <section class="card stack" style="gap:14px">
          <div class="card-head" style="margin:0"><h2>${esc(t('teaser.signals'))}</h2><span class="badge">${icon.spark} ${esc(t('teaser.verified'))}</span></div>
          <div class="grid grid-3" style="gap:12px">
            ${kpi(`${idea.votePercent ?? 0}%`, t('teaser.ofTarget', { v: fmt.num(idea.votesCount), n: fmt.num(idea.membershipThreshold) }), progressBar(idea.votePercent, t('common.voteTarget')))}
            ${kpi(`+${fmt.num(idea.votesLast7Days || 0)}`, t('teaser.momentumLong'))}
            ${kpi(fmt.num(idea.competitorsCount ?? 0), t('teaser.competitorsWithin', { d: fmt.km(idea.searchRadiusKm) }))}
            ${kpi(esc(cap), t('unlock.capital'))}
          </div>
          ${idea.nearestCompetitorKm != null ? `<span class="small muted">${esc(t('check.nearest', { d: fmt.km(idea.nearestCompetitorKm) }))}</span>` : ''}
          ${idea.checkScore != null ? `<div class="score-box">${scoreMeter(idea.checkScore, { shortage: idea.shortageScore, competition: idea.competitionScore, demand: idea.demandScore })}<p class="score-note">${esc(t('score.indicator'))}</p></div>` : ''}
          <div class="map" id="mini-map" style="height:300px" aria-label="${esc(t('teaser.mapLabel'))}"></div>
          <span class="small muted">${esc(t('teaser.areaNote'))}</span>
        </section>
        <section class="card stack" style="gap:12px">
          <h2>${esc(t('teaser.afterTitle'))}</h2>
          <ul class="locked-list">
            ${['teaser.r1', 'teaser.r2', 'teaser.r3', 'teaser.r4', 'teaser.r5'].map((k) => `<li>${icon.lock}<span>${esc(t(k))}</span></li>`).join('')}
          </ul>
          <div class="notice notice-info small">${icon.info}<span>${esc(t('teaser.protection'))}</span></div>
        </section>
      </div>
      <aside class="stack" style="gap:20px">
        ${lifeCard(idea)}
        ${investorCard(idea)}
      </aside>
    </div>`;

  if (idea.latitude != null) {
    const map = createMap($('#mini-map'), { center: { lat: idea.latitude, lng: idea.longitude }, zoom: 14, scrollWheelZoom: false });
    const area = approxCircle(idea.latitude, idea.longitude, idea.approxRadiusM, { active: true }).addTo(map);
    map.fitBounds(area.getBounds(), { padding: [30, 30] });
  } else {
    $('#mini-map').remove();
  }
  animateBars(app);
  wirePopovers(app);
  wire(idea);
}

function lifeCard(idea) {
  const track = lifeTrack(idea);
  if (!track) return '';
  return `<section class="card stack" style="gap:12px"><h2>${esc(t('life.title'))}</h2>${track}</section>`;
}

// The author's own idea that isn't public: a draft waiting for review, or one that didn't pass
function draftBox(idea) {
  const rejected = idea.status === 'REJECTED';
  return `<div class="notice ${rejected ? 'notice-error' : 'notice-warn'}" style="margin-bottom:20px">${rejected ? icon.x : icon.info}
    <div class="stack" style="gap:8px"><strong>${esc(rejected ? t('idea.notPublished') : t('idea.draftTitle'))}</strong>
    <span>${esc(rejected ? serverText(idea.rejectionReason || '') : t('idea.draftText'))}</span>
    <span class="row"><a class="btn btn-sm" href="propose.html?draft=${idea.id}">${esc(rejected ? t('idea.seeWhy') : t('idea.continueReview'))}</a>
    ${idea.similarIdeaId ? `<a href="idea.html?id=${idea.similarIdeaId}">${esc(t('idea.seeSimilar', { title: idea.similarIdeaTitle }))}</a>` : ''}</span></div></div>`;
}

// A saved address's name: its label, or "Home 1/2" by its place in the list
let homes = [];
function homeName(addressId, labelText) {
  if (labelText) return labelText;
  const i = homes.findIndex((a) => a.id === addressId);
  return t('addr.homeN', { n: i >= 0 ? i + 1 : 1 });
}

function voteCard(idea) {
  let body;
  if (idea.founder) {
    body = `<p class="muted">${esc(t('idea.youProposed'))}</p>`;
  } else if (idea.voted) {
    body = `<div class="notice notice-info">${icon.check}<span>${esc(t('idea.youVoted'))}</span></div>`;
  } else if (idea.canVote) {
    body = `<p class="muted">${esc(t('idea.inRangeFrom', { d: fmt.km(idea.distanceKm), label: homeName(idea.voteAddressId, idea.voteAddressLabel) }))}</p>
      <button type="button" class="btn btn-block" id="vote" data-loading="${esc(t('res.voting'))}">${esc(t('idea.voteBtn'))}</button>`;
  } else if (idea.status !== 'PUBLISHED') {
    body = `<p class="muted">${esc(t('idea.votingClosed'))}</p>`;
  } else if (!idea.hasAddress) {
    body = `<p class="muted">${esc(t('idea.addHome'))}</p><a class="btn btn-secondary btn-block" href="account.html#addresses">${esc(t('res.addHome'))}</a>`;
  } else if (idea.addressNeedsConfirmation) {
    body = `<p class="muted">${esc(t('idea.confirmHome'))}</p><a class="btn btn-secondary btn-block" href="account.html#addresses">${esc(t('acc.confirmBtn'))}</a>`;
  } else {
    body = `<p class="muted">${esc(t('idea.outOfRange', { d: fmt.km(idea.distanceKm), km: idea.votingRadiusKm }))}</p>`;
  }
  return `<section class="card stack" style="gap:12px"><h2>${esc(t('idea.yourVote'))}</h2>${body}
    <span class="small muted">${esc(t('common.privateVotes'))}</span></section>`;
}

function votesCard(idea) {
  const note = idea.status === 'COMPLETED' ? t('idea.targetReached') : idea.status === 'PUBLISHED' ? t('idea.collecting') : label('status', idea.status);
  return `<section class="card stack" style="gap:14px">
    <div class="row" style="gap:16px">
      <div class="ring" style="--p:${idea.votePercent}"><div><strong>${idea.votePercent}%</strong><span>${esc(t('idea.ofTarget'))}</span></div></div>
      <div class="stack" style="gap:2px"><strong style="font-size:18px">${esc(t('idea.votes', { n: fmt.num(idea.votesCount) }))}</strong><span class="muted small">${esc(t('idea.target', { n: fmt.num(idea.membershipThreshold) }))}</span></div>
    </div>
    ${progressBar(idea.votePercent, t('idea.voteTarget'))}
    <span class="small muted">${esc(note)}</span>
  </section>`;
}

function investorCard(idea) {
  let action = '';
  if (idea.canAdopt) {
    // The owner doesn't buy their own idea: they start it under the platform's service agreement
    action = `<a class="btn btn-block" href="unlock.html?id=${idea.id}&mode=owner">${esc(t('owner.cta'))}</a>
      <span class="small muted">${esc(t('owner.hint'))}</span>`;
  } else if (idea.unlocked) {
    action = `<div class="notice notice-info">${icon.check}<span>${esc(t('idea.youAdopted'))}</span></div><a class="btn btn-secondary btn-block" href="projects.html">${esc(t('idea.goProjects'))}</a>`;
  } else if (idea.canUnlock) {
    const early = idea.status === 'PUBLISHED';
    action = early
      ? `${earlyStart(`unlock.html?id=${idea.id}`)}${priceHow()}`
      : `<a class="btn btn-block" href="unlock.html?id=${idea.id}">${esc(t('idea.unlockBtn'))}</a>`;
  } else {
    action = `<button type="button" class="btn btn-block" disabled>${icon.lock} ${esc(t('common.unlock'))}</button>
      <span class="small muted">${esc(serverText(idea.unlockBlockedReason || ''))}</span>`;
  }
  const pending = idea.investorStatus && idea.investorStatus !== 'VERIFIED';
  return `<section class="card stack" style="gap:14px">
    <h2>${esc(t('idea.forInvestors'))}</h2>
    ${pending ? `<div class="notice notice-warn">${icon.info}<span>${esc(idea.investorStatus === 'REJECTED' ? t('inv.rejected') : t('idea.underReview'))}</span></div>` : ''}
    <dl class="dl">
      <dt>${esc(t('unlock.capital'))}</dt><dd class="num">${idea.capitalMin != null ? `${fmt.money(idea.capitalMin)} – ${fmt.money(idea.capitalMax)}` : esc(t('bm.notEstimated'))}</dd>
      <dt>${esc(t('idea.location'))}</dt><dd>${esc(idea.teaser ? t('teaser.approxArea') : serverText(idea.locationLabel || '—'))}</dd>
      <dt>${esc(t('idea.status'))}</dt><dd>${esc(label('status', idea.status))}</dd>
    </dl>
    ${action}
  </section>`;
}

function businessModelRows(m) {
  const capital = m.estimatedCapitalMin != null ? `${fmt.money(m.estimatedCapitalMin)} – ${fmt.money(m.estimatedCapitalMax)}` : t('bm.notEstimated');
  return `
      <dt>${esc(t('bm.value'))}</dt><dd>${esc(m.valueProposition)}</dd>
      <dt>${esc(t('bm.customers'))}</dt><dd>${esc(m.targetCustomers)}</dd>
      <dt>${esc(t('bm.revenue'))}</dt><dd>${esc(m.revenueStreams)}</dd>
      <dt>${esc(t('bm.costs'))}</dt><dd>${esc(m.costStructure)}</dd>
      <dt>${esc(t('bm.channels'))}</dt><dd>${esc(m.channels)}</dd>
      <dt>${esc(t('bm.capital'))}</dt><dd class="num">${capital}</dd>
      ${m.risks ? `<dt>${esc(t('bm.risks'))}</dt><dd>${esc(m.risks)}</dd>` : ''}`;
}

function businessModelCard(m) {
  return aiBlock(`<dl class="dl">${businessModelRows(m)}</dl>`, { title: t('bm.title') });
}

// "How is the price worked out?" — the rule the server applies (UnlockService.currentPrice)
function priceHow() {
  return `<details class="disclosure"><summary>${esc(t('price.how'))}</summary><p>${esc(t('price.howText'))}</p></details>`;
}

function missingModelCard() {
  return `<section class="card stack" style="gap:12px">
    <h2>${esc(t('bm.title'))}</h2>
    <p class="muted">${esc(t('bm.missing'))}</p>
    <button type="button" class="btn btn-secondary" id="retry-ai" data-loading="${esc(t('bm.generating'))}" style="align-self:flex-start">${icon.spark} ${esc(t('bm.generate'))}</button>
  </section>`;
}

function feasibilityCard(r) {
  return aiBlock(`
    <dl class="dl">
      <dt>${esc(t('fr.gapName'))}</dt><dd><strong class="num">${r.gapScore}</strong><span class="muted">/100</span> · <span class="small muted">${esc(t('fr.gapHelp'))}</span></dd>
      <dt>${esc(t('fr.peak'))}</dt><dd>${esc(r.peakDemandTime || '—')}</dd>
    </dl>
    <p>${esc(r.recommendation)}</p>
    ${r.risks ? `<p class="small"><strong>${esc(t('bm.risks'))}:</strong> ${esc(r.risks)}</p>` : ''}`, { title: t('fr.title') });
}

// What the AI concluded (score + summary) — AI text only
function checkCard(idea) {
  if (!idea.checkDecision) return '';
  return aiBlock(`
    ${idea.checkScore != null ? scoreMeter(idea.checkScore, { shortage: idea.shortageScore, competition: idea.competitionScore, demand: idea.demandScore }) : ''}
    ${idea.feasibilitySummary ? `<p>${esc(idea.feasibilitySummary)}</p>` : ''}
    <p class="score-note">${esc(t('score.indicator'))}</p>`, { title: t('check.title') });
}

// What the map services found — data, not AI, so it sits outside the AI panel
function mapCard(idea) {
  if (!idea.checkDecision) return '';
  const competitors = idea.competitors || [];
  return `<section class="source-card">
    <h3>${esc(t('prop.fromMap'))}</h3>
    <div class="row small muted"><span>${esc(t('check.similarWithin', { n: fmt.num(idea.competitorsCount), d: fmt.km(idea.searchRadiusKm) }))}</span>${idea.nearestCompetitorKm != null ? `<span>· ${esc(t('check.nearest', { d: fmt.km(idea.nearestCompetitorKm) }))}</span>` : ''}</div>
    ${competitors.length ? `<ul class="list">${competitors.slice(0, 8).map((c) => `<li><span dir="auto">${esc(c.name)}</span><span class="muted num">${fmt.km(c.distanceKm)}</span></li>`).join('')}</ul>` : ''}
    <span class="source-note">${esc(t('check.source'))}</span>
  </section>`;
}

function wire(idea) {
  $('#vote')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const updated = await api.post(`discover/vote/${idea.id}`);
      toast(t('res.voteDone'), 'success');
      render(updated);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));

  $('#retry-ai')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const updated = await api.post(`discover/retry-ai/${idea.id}`);
      render(updated);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

load();
