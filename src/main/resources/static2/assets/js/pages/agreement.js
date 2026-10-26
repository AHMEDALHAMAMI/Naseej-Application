// A readable copy of a project's agreement for the people in it: who the parties are, the terms they
// signed, who signed and when, and the contract's status. Everything comes from the server (unlock/agreement).
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, errorMessage, label, t } from '../ui.js';

await guard(['RESIDENT', 'INVESTOR', 'ADMIN']);
const id = Number(params().get('contract'));
const app = $('#app');
if (!id) location.replace('projects.html');

let a;
try {
  a = await api.get(`unlock/agreement/${id}`);
} catch (err) {
  app.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
  await new Promise(() => {});
}

const type = a.agreementType;
const title = t(`agr.type.${type}`);
document.title = `${title} — ${a.contractNumber}`;

// The terms in plain words, by kind of agreement
const terms = {
  OWNER_SERVICE: [
    t('owner.t1', { title: a.ideaTitle }),
    a.sharePercent != null
      ? t('owner.t2', { pct: fmt.num(a.sharePercent), years: a.shareYears })
      : t('owner.t2fee', { fee: fmt.sar(a.paidAmount), total: fmt.sar(a.paidTotal) }),
    t('agr.deadline', { date: fmt.date(a.openingDeadline) }),
    t('owner.t4'),
    t('owner.t5'),
  ],
  INVESTOR_PARTNERSHIP: [
    t('agr.p1', { investor: a.investorName, title: a.ideaTitle }),
    t('agr.p2', { pct: fmt.num(a.sharePercent), years: a.shareYears, end: fmt.date(a.shareEndsAt) }),
    t('agr.deadline', { date: fmt.date(a.openingDeadline) }),
    t('unlock.term3'),
    t('unlock.ndaTerm'),
  ],
  INVESTOR_PAID: [
    t('agr.paid1', { investor: a.investorName, title: a.ideaTitle, amount: fmt.sar(a.paidAmount) }),
    t('agr.deadline', { date: fmt.date(a.openingDeadline) }),
    t('unlock.ndaTerm'),
  ],
}[type] || [];

const parties = type === 'OWNER_SERVICE'
  ? [t('owner.party1'), t('owner.party2', { name: a.ownerName })]
  : type === 'INVESTOR_PARTNERSHIP'
    ? [t('agr.partyInvestor', { name: a.investorName }), t('agr.partyFounder', { name: a.ownerName })]
    : [t('owner.party1'), t('agr.partyInvestor', { name: a.investorName })];

// Who still needs to act: shown plainly so nobody wonders who is waiting
const waiting = a.status === 'ACTIVE' ? t('agr.allSigned') : a.status === 'TERMINATED' ? t('agr.ended') : t('agr.waiting');

app.innerHTML = `
  <div class="flow-narrow">
    <a class="back-link no-print" href="projects.html">${icon.back} ${esc(t('idea.goProjects'))}</a>
    <article class="doc doc-page" aria-labelledby="agr-h">
      <header class="stack" style="gap:6px">
        <span class="eyebrow">${esc(t('proj.contract', { n: a.contractNumber }))}</span>
        <h1 id="agr-h">${esc(title)}</h1>
        <div class="row"><span class="badge ${a.status === 'ACTIVE' ? 'badge-solid' : 'badge-amber'}">${esc(label('contract', a.status))}</span>
          ${a.termsVersion ? `<span class="badge badge-sand">${esc(t('agr.version', { v: a.termsVersion }))}</span>` : ''}</div>
      </header>
      <p class="notice notice-info">${icon.info}<span>${esc(waiting)}</span></p>
      <h2>${esc(t('agr.idea'))}</h2>
      <p><strong dir="auto">${esc(a.ideaTitle)}</strong> · ${esc(`${a.neighborhoodName}, ${a.city}`)}</p>
      <h2>${esc(t('owner.parties'))}</h2>
      <ol>${parties.map((p) => `<li>${esc(p)}</li>`).join('')}</ol>
      <h2>${esc(t('owner.terms'))}</h2>
      <ol>${terms.map((x) => `<li>${esc(x)}</li>`).join('')}</ol>
      <h2>${esc(t('agr.signatures'))}</h2>
      ${(a.signatures || []).length ? `<ul class="sig-list">${a.signatures.map((s) => `<li><strong dir="auto">${esc(s.typedName)}</strong><span>${esc(label('signer', s.role))} · ${esc(fmt.dateTime(s.signedAt))}</span></li>`).join('')}</ul>`
        : `<p class="muted">${esc(type === 'INVESTOR_PAID' ? t('agr.paidNoSig') : t('agr.noSig'))}</p>`}
      ${a.activatedAt ? `<p class="small muted">${esc(t('agr.activated', { date: fmt.dateTime(a.activatedAt) }))}</p>` : ''}
      <p class="small muted">${esc(t('owner.legal'))}</p>
      <div class="row no-print"><button type="button" class="btn btn-secondary" id="print">${esc(t('agr.print'))}</button></div>
    </article>
  </div>`;
$('#print').addEventListener('click', () => window.print());
