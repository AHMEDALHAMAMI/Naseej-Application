// Investor view of the idea page (loaded by idea.js). The server decides what each viewer may see:
// residents never get vote counts or the business model; the founder and investors do.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, statusBadge, progressBar, animateBars, modal, label, categoryLabel, cityLabel, placeLabel, shortRange, t } from '../ui.js';
import { serverText } from '../i18n.js';
import { createMap, pinIcon, meIcon, lastLocation } from '../maps.js';

const user = await guard(['RESIDENT', 'INVESTOR', 'ADMIN']);
const id = Number(params().get('id'));
const app = $('#app');
if (!id) location.replace('index.html');

// Back to where the investor came from (For you, Ask AI or the map), otherwise their home
const sameSite = document.referrer && new URL(document.referrer).origin === location.origin && !document.referrer.includes('idea.html');
const backHref = sameSite ? document.referrer : user.role === 'INVESTOR' ? 'investor.html' : user.role === 'ADMIN' ? 'admin.html' : 'resident.html';
const backLink = (style = '') => `<a class="btn btn-ghost btn-sm" href="${backHref}" style="padding-inline-start:0;${style}">${icon.back} ${esc(t('common.back'))}</a>`;

async function load() {
  const loc = lastLocation();
  try {
    const idea = await api.get(`discover/idea/${id}`, loc ? { lat: loc.lat, lng: loc.lng } : null);
    render(idea);
  } catch (err) {
    app.innerHTML = `<div class="notice notice-error" style="margin-top:24px">${icon.x}<span>${esc(errorMessage(err))}</span></div>
      <p style="margin-top:16px">${backLink()}</p>`;
  }
}

function render(idea) {
  document.title = `${idea.title} — ${t('brand.short')}`;
  const seesNumbers = idea.votesCount !== null && idea.votesCount !== undefined;
  const isResident = user.role === 'RESIDENT';
  const isInvestor = user.role === 'INVESTOR';

  app.innerHTML = `
    ${backLink()}
    <div class="page-head" style="margin-top:8px">
      <div class="stack" style="gap:10px">
        <div class="row">${statusBadge(idea.status)}${idea.founder ? `<span class="badge badge-sand">${esc(t('idea.founderBadge'))}</span>` : ''}${idea.category ? `<span class="badge badge-sand">${esc(categoryLabel(idea.category))}</span>` : ''}</div>
        <h1>${esc(idea.title)}</h1>
        <span class="muted row" style="gap:6px">${icon.pin}${esc(placeLabel(idea.neighborhoodName, idea.city))} · ${esc(serverText(idea.locationLabel || ''))}</span>
      </div>
    </div>
    ${(idea.warnings || []).map((w) => `<div class="notice notice-warn" style="margin-bottom:12px">${icon.warn}<span>${esc(serverText(w))}</span></div>`).join('')}
    ${idea.status === 'REJECTED' ? rejectedBox(idea) : ''}
    <div class="split">
      <div class="stack" style="gap:20px">
        <section class="card stack" style="gap:12px">
          <h2>${esc(t('idea.why'))}</h2>
          <p style="white-space:pre-line">${esc(idea.description || '—')}</p>
          <div class="map" id="mini-map" style="height:280px" aria-label="${esc(t('idea.mapLabel'))}"></div>
        </section>
        ${idea.businessModel ? businessModelCard(idea.businessModel) : (seesNumbers && idea.founder && ['PUBLISHED', 'COMPLETED'].includes(idea.status) ? missingModelCard() : '')}
        ${idea.feasibilityReport ? feasibilityCard(idea.feasibilityReport) : (isInvestor && !idea.unlocked && !idea.founder ? lockedStudyCard() : '')}
        ${checkCard(idea)}
      </div>
      <aside class="stack" style="gap:20px">
        ${isResident ? voteCard(idea) : ''}
        ${seesNumbers ? votesCard(idea) : ''}
        ${isInvestor ? investorCard(idea) : ''}
      </aside>
    </div>`;

  // Mini map with the idea and, for residents, their voting radius
  if (idea.latitude != null) {
    const map = createMap($('#mini-map'), { center: { lat: idea.latitude, lng: idea.longitude }, zoom: 15, scrollWheelZoom: false });
    L.marker([idea.latitude, idea.longitude], { icon: pinIcon(idea.canVote ? 'in-range' : '') }).addTo(map);
    const loc = lastLocation();
    if (isResident && loc) {
      L.marker([loc.lat, loc.lng], { icon: meIcon(), interactive: false, keyboard: false }).addTo(map);
      const circle = L.circle([loc.lat, loc.lng], { radius: (idea.votingRadiusKm || 2) * 1000, color: '#2F5444', weight: 1.5, fillOpacity: 0.06, dashArray: '6 6' }).addTo(map);
      map.fitBounds(circle.getBounds().extend([idea.latitude, idea.longitude]), { padding: [20, 20] });
    }
  } else {
    $('#mini-map').remove();
  }
  animateBars(app);
  wire(idea);
}

function rejectedBox(idea) {
  return `<div class="notice notice-error" style="margin-bottom:20px">${icon.x}
    <div class="stack" style="gap:6px"><strong>${esc(t('idea.notPublished'))}</strong><span>${esc(serverText(idea.rejectionReason || ''))}</span>
    <span class="row">${idea.similarIdeaId ? `<a href="idea.html?id=${idea.similarIdeaId}">${esc(t('idea.seeSimilar', { title: idea.similarIdeaTitle }))}</a>` : ''}<a href="propose.html">${esc(t('idea.proposeDifferent'))}</a></span></div></div>`;
}

function voteCard(idea) {
  let body;
  if (idea.founder) {
    body = `<p class="muted">${esc(t('idea.youProposed'))}</p>`;
  } else if (idea.voted) {
    body = `<div class="notice notice-info">${icon.check}<span>${esc(t('idea.youVoted'))}</span></div>`;
  } else if (idea.canVote) {
    body = `<p class="muted">${esc(t('idea.inRange', { d: fmt.km(idea.distanceKm), km: idea.votingRadiusKm }))}</p>
      <button type="button" class="btn btn-block" id="vote" data-loading="${esc(t('res.voting'))}">${esc(t('idea.voteBtn'))}</button>`;
  } else if (idea.status !== 'PUBLISHED') {
    body = `<p class="muted">${esc(t('idea.votingClosed'))}</p>`;
  } else if (idea.distanceKm == null) {
    body = `<p class="muted">${esc(t('idea.shareLocation'))}</p>`;
  } else {
    body = `<p class="muted">${esc(t('idea.outOfRange', { d: fmt.km(idea.distanceKm), km: idea.votingRadiusKm }))}</p>`;
  }
  return `<section class="card stack" style="gap:12px"><h2>${esc(t('idea.yourVote'))}</h2>${body}
    <span class="small muted">${esc(t('common.privateVotes'))}</span></section>`;
}

function votesCard(idea) {
  const note = idea.status === 'COMPLETED' ? t('idea.targetReached') : idea.status === 'PUBLISHED' ? t('idea.unlockAt100') : label('status', idea.status);
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
  const verified = idea.investorStatus === 'VERIFIED';
  const readyForAnyone = idea.status === 'COMPLETED' && !idea.adopted && !idea.founder;
  let action = '';
  if (idea.canAdopt) {
    action = `<button type="button" class="btn btn-block" id="adopt" data-loading="${esc(t('commonInv.wait'))}">${esc(t('idea.adopt'))}</button>
      <span class="small muted">${esc(t('idea.adoptHint'))}</span>`;
  } else if (idea.unlocked) {
    action = `<div class="notice notice-info">${icon.check}<span>${esc(t('idea.youAdopted'))}</span></div><a class="btn btn-secondary btn-block" href="projects.html">${esc(t('ideaInv.goProjects'))}</a>`;
  } else if (idea.canUnlock || (readyForAnyone && !verified)) {
    // Not verified yet? The unlock page asks for the CR right there
    action = `<a class="btn btn-block" href="unlock.html?id=${idea.id}">${esc(t('idea.unlockBtn'))}</a>`;
  } else if (idea.founder && !verified) {
    action = `<div class="notice notice-warn">${icon.info}<span>${esc(t('prop.adoptAfter'))}</span></div><a class="btn btn-secondary btn-block" href="account.html">${esc(t('gate.goAccount'))}</a>`;
  } else {
    action = `<button type="button" class="btn btn-block" disabled>${icon.lock} ${esc(t('common.unlock'))}</button>
      <span class="small muted">${esc(serverText(idea.unlockBlockedReason || ''))}</span>`;
  }
  const capital = idea.businessModel?.estimatedCapitalMin != null
    ? shortRange(idea.businessModel.estimatedCapitalMin, idea.businessModel.estimatedCapitalMax) : null;
  const gets = idea.unlocked || idea.founder ? '' : `
    <div class="stack" style="gap:8px">
      <span class="small" style="font-weight:600">${esc(t('idea.afterUnlock'))}</span>
      <ul class="ticks">
        <li>${esc(t('idea.get1'))}</li><li>${esc(t('idea.get2'))}</li><li>${esc(t('idea.get3'))}</li>
      </ul>
    </div>`;
  return `<section class="card stack" style="gap:14px">
    <h2>${esc(t('idea.forInvestors'))}</h2>
    <dl class="dl">
      <dt>${esc(t('idea.gapScore'))}</dt><dd>${idea.gapScore != null ? `<strong>${idea.gapScore}</strong>/100` : `<span class="muted">${esc(t('idea.afterQuote'))}</span>`}</dd>
      ${capital ? `<dt>${esc(t('idea.capital'))}</dt><dd class="num">${esc(capital)}</dd>` : ''}
      <dt>${esc(t('idea.status'))}</dt><dd>${esc(label('status', idea.status))}</dd>
    </dl>
    ${gets}
    ${action}
  </section>`;
}

function lockedStudyCard() {
  return `<section class="card stack locked-card" style="gap:12px">
    <div class="card-head" style="margin:0"><h2>${esc(t('idea.lockedStudy'))}</h2><span class="badge badge-sand">${icon.lock}</span></div>
    <div class="locked-preview" aria-hidden="true"><span></span><span></span><span></span></div>
    <p class="small muted">${esc(t('idea.lockedText'))}</p>
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
  return `<section class="card ai-card stack" style="gap:14px">
    <div class="card-head" style="margin:0"><h2>${esc(t('bm.title'))}</h2><span class="badge badge-ai">${icon.spark} ${esc(t('bm.aiDraft'))}</span></div>
    <dl class="dl">${businessModelRows(m)}</dl>
  </section>`;
}

function missingModelCard() {
  return `<section class="card stack" style="gap:12px">
    <h2>${esc(t('bm.title'))}</h2>
    <p class="muted">${esc(t('bm.missing'))}</p>
    <button type="button" class="btn btn-secondary" id="retry-ai" data-loading="${esc(t('bm.generating'))}" style="align-self:flex-start">${icon.spark} ${esc(t('bm.generate'))}</button>
  </section>`;
}

function feasibilityCard(r) {
  return `<section class="card stack" style="gap:14px">
    <div class="card-head" style="margin:0"><h2>${esc(t('fr.title'))}</h2><span class="badge badge-solid">${esc(t('fr.unlocked'))}</span></div>
    <div class="grid grid-3" style="gap:12px">
      <div class="surface-sand kpi"><span class="value">${r.gapScore}</span><span class="label">${esc(t('fr.gap'))}</span></div>
      <div class="surface-sand kpi"><span class="value">${r.memberGrowthPercent ?? 0}%</span><span class="label">${esc(t('fr.ofTarget'))}</span></div>
      <div class="surface-sand kpi"><span class="value" style="font-size:22px">${esc(r.peakDemandTime || '—')}</span><span class="label">${esc(t('fr.peak'))}</span></div>
    </div>
    <p>${esc(r.recommendation)}</p>
    ${r.risks ? `<p class="small muted"><strong>${esc(t('bm.risks'))}:</strong> ${esc(r.risks)}</p>` : ''}
  </section>`;
}

function checkCard(idea) {
  if (!idea.checkDecision) return '';
  const competitors = idea.competitors || [];
  return `<section class="card ai-card stack" style="gap:14px">
    <div class="card-head" style="margin:0"><h2>${esc(t('check.title'))}</h2>${idea.checkScore != null ? `<span class="badge badge-ai">${icon.spark} ${esc(t('check.score', { n: idea.checkScore }))}</span>` : ''}</div>
    ${idea.feasibilitySummary ? `<p>${esc(idea.feasibilitySummary)}</p>` : ''}
    <div class="row small muted"><span>${esc(t('check.similarWithin', { n: fmt.num(idea.competitorsCount), d: fmt.km(idea.searchRadiusKm) }))}</span>${idea.nearestCompetitorKm != null ? `<span>· ${esc(t('check.nearest', { d: fmt.km(idea.nearestCompetitorKm) }))}</span>` : ''}</div>
    ${competitors.length ? `<ul class="list">${competitors.slice(0, 8).map((c) => `<li><span>${esc(c.name)}</span><span class="muted num">${fmt.km(c.distanceKm)}</span></li>`).join('')}</ul>` : ''}
    <span class="small muted">${esc(t('check.source'))}</span>
  </section>`;
}

function wire(idea) {
  $('#vote')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const loc = lastLocation();
      const updated = await api.post(`discover/vote/${idea.id}`, loc ? { latitude: loc.lat, longitude: loc.lng } : {});
      toast(t('res.voteDone'), 'success');
      render(updated);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));

  $('#adopt')?.addEventListener('click', async () => {
    const result = await modal({
      title: t('adopt.title'),
      body: `<p class="muted">${esc(t('adopt.text'))}</p>`,
      confirmText: t('adopt.confirm'),
      onConfirm: () => api.post(`unlock/adopt/${idea.id}`),
    });
    if (result) {
      toast(serverText(result.message) || t('adopt.done'), 'success');
      // Next: the founding members' benefits and the signature
      location.href = result.contractId ? `contract.html?id=${result.contractId}` : 'projects.html';
    }
  });

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
