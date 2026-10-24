// Unlock: AI-priced upfront payment (Moyasar) or a success partnership (5% of net profit to the founder).
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, progressBar, animateBars, modal, t } from '../ui.js';
import { serverText } from '../i18n.js';

const user = await guard(['INVESTOR']);
const id = Number(params().get('id'));
const app = $('#app');
if (!id) location.replace('investor.html');

app.innerHTML = `
  <a class="btn btn-ghost btn-sm" href="idea.html?id=${id}" style="padding-inline-start:0">${icon.back} ${esc(t('unlock.back'))}</a>
  <div class="page-head" style="margin-top:8px"><div class="stack" style="gap:8px"><span class="eyebrow">${esc(t('unlock.eyebrow'))}</span><h1 id="title">${esc(t('common.loading'))}</h1></div></div>
  <div id="body"><div class="card stack" style="gap:14px;max-width:720px" role="status"><div class="row"><span class="spinner"></span><strong>${esc(t('unlock.pricing'))}</strong></div><p class="muted small">${esc(t('unlock.pricingText'))}</p></div></div>`;

let idea;
let quote;
try {
  [idea, quote] = await Promise.all([api.get(`discover/idea/${id}`), api.get(`unlock/quote/${id}`)]);
} catch (err) {
  $('#title').textContent = t('unlock.eyebrow');
  $('#body').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
  await new Promise(() => {}); // stop here; the error is on screen
}

$('#title').innerHTML = esc(idea.title);
document.title = t('unlock.docTitle', { title: idea.title });

const blocked = !idea.canUnlock;
const factors = [
  [t('unlock.execution'), quote.executionScore],
  [t('unlock.success'), quote.successScore],
  [t('unlock.opportunity'), quote.opportunityScore],
];
const pct = quote.partnershipPercent ? Number(quote.partnershipPercent) : 5;
const years = quote.partnershipYears;

$('#body').innerHTML = `
  ${blocked ? `<div class="notice notice-warn" style="margin-bottom:20px">${icon.lock}<span>${esc(serverText(idea.unlockBlockedReason) || t('unlock.blocked'))}</span></div>` : ''}
  <div class="split">
    <div class="stack" style="gap:20px">
      <section class="card stack" style="gap:16px">
        <div class="card-head" style="margin:0"><h2>${esc(t('unlock.howPriced'))}</h2><span class="badge">${icon.spark} ${esc(t('unlock.aiQuote'))}</span></div>
        ${factors.map(([name, v]) => `
          <div class="stack" style="gap:6px">
            <div class="row small" style="justify-content:space-between"><span>${esc(name)}</span><strong class="num">${v ?? '—'}/100</strong></div>
            ${progressBar(v || 0, name)}
          </div>`).join('')}
        ${quote.reasoning ? `<p class="muted">${esc(quote.reasoning)}</p>` : ''}
        <span class="small muted">${esc(t('unlock.quoted', { date: fmt.dateTime(quote.quotedAt) }))}</span>
      </section>
      <section class="card stack" style="gap:10px">
        <h2>${esc(t('unlock.theOpp'))}</h2>
        <dl class="dl">
          <dt>${esc(t('common.location'))}</dt><dd>${esc(idea.neighborhoodName)}, ${esc(idea.city)} · ${esc(serverText(idea.locationLabel || ''))}</dd>
          <dt>${esc(t('common.votes'))}</dt><dd>${esc(t('unlock.votesOf', { v: fmt.num(idea.votesCount), n: fmt.num(idea.membershipThreshold), p: idea.votePercent }))}</dd>
          ${idea.gapScore != null ? `<dt>${esc(t('inv.gapScore'))}</dt><dd>${idea.gapScore}/100</dd>` : ''}
          ${idea.businessModel?.estimatedCapitalMin != null ? `<dt>${esc(t('unlock.capital'))}</dt><dd class="num">${fmt.money(idea.businessModel.estimatedCapitalMin)} – ${fmt.money(idea.businessModel.estimatedCapitalMax)}</dd>` : ''}
        </dl>
      </section>
    </div>
    <fieldset class="stack" style="gap:16px;border:0;margin:0;padding:0">
      <legend class="eyebrow" style="margin-bottom:12px">${esc(t('unlock.choose'))}</legend>
      <article class="card stack" style="gap:12px">
        <div class="row" style="justify-content:space-between"><h3>${esc(t('unlock.upfront'))}</h3><span class="badge">${esc(t('unlock.ownFully'))}</span></div>
        <div><span style="font-family:var(--font-display);font-size:40px;font-weight:300" class="num">${fmt.sar(quote.price)}</span><span class="muted small"> ${esc(t('unlock.plusVat'))}</span></div>
        <dl class="dl small"><dt>${esc(t('unlock.price'))}</dt><dd class="num">${fmt.sar(quote.price)}</dd><dt>${esc(t('unlock.vat'))}</dt><dd class="num">${fmt.sar(quote.vat)}</dd><dt><strong>${esc(t('unlock.total'))}</strong></dt><dd class="num"><strong>${fmt.sar(quote.total)}</strong></dd></dl>
        <p class="small muted">${esc(t('unlock.upfrontText'))}</p>
        <button type="button" class="btn btn-block" id="pay" ${blocked ? 'disabled' : ''} data-loading="${esc(t('unlock.opening'))}">${esc(t('unlock.pay', { total: fmt.sar(quote.total) }))}</button>
      </article>
      <article class="card stack" style="gap:12px">
        <div class="row" style="justify-content:space-between"><h3>${esc(t('unlock.partnership'))}</h3><span class="badge badge-sand">${esc(t('unlock.nothingUpfront'))}</span></div>
        <div><span style="font-family:var(--font-display);font-size:40px;font-weight:300">${pct}%</span><span class="muted small"> ${esc(t('unlock.ofProfit'))}</span></div>
        <p class="small muted">${esc(t('unlock.partnershipText', { pct, years }))}</p>
        <button type="button" class="btn btn-secondary btn-block" id="partner" ${blocked ? 'disabled' : ''}>${esc(t('unlock.review'))}</button>
      </article>
    </fieldset>
  </div>`;
animateBars($('#body'));

$('#pay')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
  try {
    // Moyasar sends the browser back to this site after 3-D Secure
    const callbackUrl = new URL(`payment-return.html?ideaId=${id}`, location.href).href;
    const res = await api.post(`unlock/pay/${id}`, { callbackUrl });
    if (res.payment?.status === 'PAID') {
      location.href = `payment-return.html?ideaId=${id}&paymentId=${res.payment.id}`;
    } else if (res.transactionUrl) {
      location.href = res.transactionUrl;
    } else {
      toast(serverText(res.message) || t('unlock.payFailed'), 'error');
    }
  } catch (err) {
    toast(errorMessage(err), 'error');
  }
}));

$('#partner')?.addEventListener('click', async () => {
  const result = await modal({
    title: t('unlock.agreement'),
    body: `
      <div class="surface-sand stack small" style="gap:8px">
        <span>1. ${esc(t('unlock.term1', { name: user.fullName, title: idea.title }))}</span>
        <span>2. ${t('unlock.term2', { pct, years })}</span>
        <span>3. ${esc(t('unlock.term3'))}</span>
      </div>
      <div class="field"><label for="signed-name">${esc(t('unlock.signName'))}</label><input class="input" id="signed-name" value="${esc(user.fullName)}" autocomplete="name"></div>
      <label class="check"><input type="checkbox" id="agree"><span>${esc(t('unlock.accept'))}</span></label>`,
    confirmText: t('unlock.sign'),
    onConfirm: async (box) => {
      if (!box.querySelector('#agree').checked) throw new Error(t('unlock.acceptFirst'));
      return api.post(`unlock/partnership/${id}`, { termsAccepted: true, signedName: box.querySelector('#signed-name').value.trim() });
    },
  });
  if (result) {
    toast(serverText(result.message) || t('unlock.signed'), 'success');
    location.href = 'projects.html';
  }
});
