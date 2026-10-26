// One opportunity card, shared by the investor pages (For you with its map, Ask AI), so an opportunity
// looks the same wherever it shows up.
import { esc, fmt, progressBar, categoryLabel, placeLabel, shortRange, icon, t } from './ui.js';
import { serverText } from './i18n.js';

export const isTaken = (o) => (o.status === 'ADOPTED' || o.status === 'OPENED') && !o.unlocked;

/**
 * o: an OpportunityResponse. Options: showPlace (false where the neighborhood is already known).
 * "Unlock" leads to the unlock page even before the CR is verified: that page asks for the CR at that moment.
 */
export function oppCard(o, { showPlace = true } = {}) {
  const ready = o.status === 'COMPLETED';
  const capital = o.estimatedCapitalMin != null ? shortRange(o.estimatedCapitalMin, o.estimatedCapitalMax) : null;

  let action;
  if (o.unlocked) {
    action = `<a class="btn btn-sm" href="projects.html">${esc(t('inv.inProjects'))}</a>`;
  } else if (isTaken(o)) {
    action = `<span class="badge badge-sand" style="justify-content:center">${esc(t('inv.taken'))}</span>`;
  } else if (ready) {
    action = `<a class="btn btn-sm" href="unlock.html?id=${o.id}">${esc(t('common.unlock'))}</a>`;
  } else {
    action = `<span class="small muted opp-later">${icon.lock} ${esc(t('inv.opensAt100'))}</span>`;
  }

  return `
    <article class="opp-card${ready ? ' is-ready' : ''}" id="opp-${o.id}">
      <div class="opp-top">
        <span class="opp-cat">${esc(categoryLabel(o.category) || '—')}</span>
        <span class="pill ${ready ? 'pill-ready' : ''}">${esc(ready ? t('fy.ready') : t('fy.sectionRising'))}</span>
      </div>
      <h3 class="opp-title"><a href="idea.html?id=${o.id}">${esc(o.title)}</a></h3>
      ${showPlace ? `<span class="opp-place">${icon.pin}<span>${esc(placeLabel(o.neighborhoodName, o.city) || serverText(o.locationLabel || ''))}</span></span>` : `<span class="opp-place">${icon.pin}<span>${esc(serverText(o.locationLabel || ''))}</span></span>`}
      <div class="stack" style="gap:6px">
        ${progressBar(o.votePercent, t('common.voteTarget'))}
        <span class="small muted num">${esc(t('fy.votesOf', { v: fmt.num(o.votesCount), n: fmt.num(o.membershipThreshold) }))}</span>
      </div>
      <dl class="opp-facts">
        ${capital ? `<div><dt>${esc(t('fy.capital'))}</dt><dd class="num">${esc(capital)}</dd></div>` : ''}
        ${o.gapScore != null ? `<div><dt>${esc(t('idea.gapScore'))}</dt><dd class="num">${o.gapScore}/100</dd></div>` : ''}
      </dl>
      <div class="opp-actions">
        <a class="btn btn-secondary btn-sm" href="idea.html?id=${o.id}">${esc(t('commonInv.viewDetails'))}</a>
        ${action}
      </div>
    </article>`;
}
