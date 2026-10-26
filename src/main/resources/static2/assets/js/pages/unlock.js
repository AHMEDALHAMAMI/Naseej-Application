// Two different journeys share this page:
// - An investor unlocks someone else's idea: 1 Summary → 2 Choose a way (pay upfront or success partnership)
//   → 3 Terms and confirm. The price comes from the server's quote (UnlockService); nothing is calculated here.
// - The idea's owner launches their own idea (?mode=owner): they don't buy it. 1 What launching means →
//   2 Read and sign the platform service agreement → 3 Pay the one-time service fee (+ VAT) by card →
//   payment-return.html confirms it and the project starts, with the contract number and a copy.
// Nothing is marked paid or signed here: payment is confirmed by Moyasar on the server, signatures by the server.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, progressBar, animateBars, ideaName, isolate, t } from '../ui.js';
import { serverText } from '../i18n.js';
import { stepper, aiBlock, warnNote, actionBar } from '../components.js';

const user = await guard(['INVESTOR']);
const id = Number(params().get('id'));
const ownerMode = params().get('mode') === 'owner';
const app = $('#app');
if (!id) location.replace('investor.html');

app.innerHTML = `
  <div class="flow">
    <a class="back-link" href="idea.html?id=${id}">${icon.back} ${esc(t('unlock.back'))}</a>
    <header class="flow-head" style="margin-top:8px">
      <span class="eyebrow">${esc(ownerMode ? t('owner.eyebrow') : t('unlock.eyebrow'))}</span>
      <h1 id="title" tabindex="-1" dir="auto">${esc(t('common.loading'))}</h1>
    </header>
    <div id="steps"></div>
    <div id="body"><div class="flow-card" role="status"><div class="row"><span class="spinner" aria-hidden="true"></span><strong>${esc(ownerMode ? t('common.loading') : t('unlock.pricing'))}</strong></div>${ownerMode ? '' : `<p class="muted small">${esc(t('unlock.pricingText'))}</p>`}</div></div>
  </div>`;
const body = $('#body');
const fail = (err) => {
  $('#title').textContent = ownerMode ? t('owner.eyebrow') : t('unlock.eyebrow');
  body.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
};
const focusTop = () => { window.scrollTo({ top: 0, behavior: 'smooth' }); $('#title').focus({ preventScroll: true }); };

if (ownerMode) ownerFlow(); else investorFlow();

// =====================================================================
// Investor unlocks another person's idea
// =====================================================================

async function investorFlow() {
  let idea;
  let quote;
  try {
    [idea, quote] = await Promise.all([api.get(`discover/idea/${id}`), api.get(`unlock/quote/${id}`)]);
  } catch (err) { fail(err); return; }
  if (idea.founder) { location.replace(`unlock.html?id=${id}&mode=owner`); return; }

  $('#title').textContent = ideaName(idea);
  document.title = t('unlock.docTitle', { title: ideaName(idea) });
  const LABELS = [t('unlock.s1'), t('unlock.s2'), t('unlock.s3')];
  const blocked = !idea.canUnlock;
  const pct = quote.partnershipPercent ? Number(quote.partnershipPercent) : 5;
  const years = quote.partnershipYears;
  let way = null;

  // Step 1: what this is, what you get, what it costs now and why
  function summary() {
    $('#steps').innerHTML = stepper(LABELS, 0);
    body.innerHTML = `
      ${blocked ? `<div class="notice notice-warn" style="margin-bottom:16px">${icon.lock}<span>${esc(serverText(idea.unlockBlockedReason) || t('unlock.blocked'))}</span></div>` : ''}
      <div class="review-grid">
        <div class="stack" style="gap:16px">
          <section class="flow-card" style="gap:14px">
            <h2 class="flow-q">${esc(t('unlock.theOpp'))}</h2>
            <dl class="dl">
              <dt>${esc(t('common.location'))}</dt><dd>${esc(isolate(`${idea.neighborhoodName}, ${idea.city}`))} · ${esc(idea.teaser ? t('teaser.approxArea') : serverText(idea.locationLabel || ''))}</dd>
              <dt>${esc(t('common.votes'))}</dt><dd>${esc(t('unlock.votesOf', { v: fmt.num(idea.votesCount), n: fmt.num(idea.membershipThreshold), p: idea.votePercent }))}</dd>
              ${idea.capitalMin != null ? `<dt>${esc(t('unlock.capital'))}</dt><dd class="num">${fmt.money(idea.capitalMin)} – ${fmt.money(idea.capitalMax)}</dd>` : ''}
            </dl>
            ${progressBar(idea.votePercent, t('common.voteTarget'))}
          </section>
          <section class="flow-card" style="gap:12px">
            <h2 class="flow-q">${esc(t('unlock.youGet'))}</h2>
            <ul class="locked-list open">${['teaser.r1', 'teaser.r2', 'teaser.r3', 'teaser.r4', 'teaser.r5'].map((k) => `<li>${icon.check}<span>${esc(t(k))}</span></li>`).join('')}</ul>
            <span class="small muted">${esc(t('unlock.exclusive'))}</span>
          </section>
        </div>
        <div class="stack" style="gap:16px">
          <section class="flow-card price-card" style="gap:12px">
            <span class="label">${esc(quote.earlyAccess ? t('price.nowEarly') : t('price.now'))}</span>
            <div><span class="price-big num">${fmt.money(quote.price)}</span> <span class="muted small">${esc(t('unlock.plusVat'))}</span></div>
            ${quote.earlyAccess ? `<p class="small">${esc(t('price.atTarget', { full: fmt.money(quote.fullPrice) }))}</p>${warnNote(t('early.warn'))}` : ''}
            <details class="disclosure"><summary>${esc(t('price.how'))}</summary><p>${esc(t('price.howText'))}</p></details>
          </section>
          ${aiBlock(`
            ${[[t('unlock.execution'), quote.executionScore], [t('unlock.success'), quote.successScore], [t('unlock.opportunity'), quote.opportunityScore]].map(([name, v]) => `
              <div class="stack" style="gap:4px"><div class="row small" style="justify-content:space-between"><span>${esc(name)}</span><strong class="num">${v ?? '—'}/100</strong></div>${progressBar(v || 0, name)}</div>`).join('')}
            ${quote.reasoning ? `<p>${esc(quote.reasoning)}</p>` : ''}`, { title: t('price.why') })}
          <span class="small muted">${esc(t('price.quoted', { date: fmt.date(quote.quotedAt) }))}</span>
        </div>
      </div>
      ${actionBar(`<a class="btn btn-secondary" href="idea.html?id=${id}">${icon.back} ${esc(t('common.back'))}</a>
        <button type="button" class="btn" id="next" ${blocked ? 'disabled' : ''}>${esc(t('unlock.chooseWay'))} ${icon.arrow}</button>`)}`;
    animateBars(body);
    $('#next')?.addEventListener('click', choose);
  }

  // Step 2: pay upfront or partnership
  function choose() {
    $('#steps').innerHTML = stepper(LABELS, 1, { links: ['#'] });
    $('#steps a')?.addEventListener('click', (e) => { e.preventDefault(); summary(); });
    body.innerHTML = `
      <form class="flow-card" id="f-way" novalidate>
        <fieldset class="field"><legend class="flow-q">${esc(t('unlock.choose'))}</legend>
        <div class="grid grid-2" style="gap:12px;margin-top:12px">
          <label class="option"><input type="radio" name="way" value="PAID"${way === 'PAID' ? ' checked' : ''}>
            <strong>${esc(t('unlock.upfront'))}</strong>
            <span class="price-big num" style="font-size:28px">${fmt.money(quote.price)}</span>
            <span class="small muted">${esc(t('unlock.plusVat'))} · ${esc(t('unlock.upfrontText'))}</span>
            ${quote.earlyAccess ? warnNote(t('early.warn')) : ''}
          </label>
          <label class="option"><input type="radio" name="way" value="PARTNERSHIP"${way === 'PARTNERSHIP' ? ' checked' : ''}>
            <strong>${esc(t('unlock.partnership'))}</strong>
            <span class="price-big num" style="font-size:28px">${esc(t('unlock.nothingUpfront'))}</span>
            <span class="small muted">${esc(t('unlock.partnershipText', { pct, years }))}</span>
          </label>
        </div></fieldset>
        ${actionBar(`<button type="button" class="btn btn-secondary" data-back>${icon.back} ${esc(t('common.back'))}</button>
          <button class="btn" type="submit">${esc(t('common.next'))} ${icon.arrow}</button>`)}
      </form>`;
    $('[data-back]').addEventListener('click', summary);
    $('#f-way').addEventListener('submit', (e) => {
      e.preventDefault();
      way = e.currentTarget.way.value || null;
      if (!way) { toast(t('unlock.pickWay'), 'error'); return; }
      terms();
    });
    focusTop();
  }

  // Step 3: the terms, then pay or sign
  function terms() {
    $('#steps').innerHTML = stepper(LABELS, 2);
    const paid = way === 'PAID';
    body.innerHTML = `
      <form class="flow-card" id="f-terms" novalidate>
        <h2 class="flow-q">${esc(paid ? t('unlock.termsPaid') : t('unlock.agreement'))}</h2>
        <div class="doc">
          <ol>
            <li>${esc(t('unlock.ndaTerm'))}</li>
            ${paid ? `<li>${esc(t('unlock.paidTerm'))}</li>` : `
            <li>${esc(t('unlock.term1', { name: user.fullName, title: ideaName(idea) }))}</li>
            <li>${t('unlock.term2', { pct, years })}</li>
            <li>${esc(t('unlock.term3'))}</li>
            <li>${esc(t('unlock.term4'))}</li>`}
          </ol>
        </div>
        ${paid ? `<dl class="dl totals"><dt>${esc(t('unlock.price'))}</dt><dd class="num">${fmt.sar(quote.price)}</dd><dt>${esc(t('unlock.vat'))}</dt><dd class="num">${fmt.sar(quote.vat)}</dd><dt><strong>${esc(t('unlock.total'))}</strong></dt><dd class="num"><strong>${fmt.sar(quote.total)}</strong></dd></dl>
          <p class="small muted">${esc(t('unlock.recheckPrice'))}</p>`
        : `<div class="field"><label for="signed-name">${esc(t('unlock.signName'))}</label><input class="input" id="signed-name" value="${esc(user.fullName)}" autocomplete="name" maxlength="120"><span class="error" aria-live="polite"></span></div>`}
        <label class="check"><input type="checkbox" id="agree"><span>${esc(paid ? t('unlock.nda') : t('unlock.accept'))}</span></label>
        ${actionBar(`<button type="button" class="btn btn-secondary" data-back>${icon.back} ${esc(t('common.back'))}</button>
          <button class="btn" type="submit" data-loading="${esc(paid ? t('pay.opening') : t('common.wait'))}">${esc(paid ? t('pay.continue') : t('unlock.sign'))}</button>`)}
      </form>`;
    $('[data-back]').addEventListener('click', choose);
    $('#f-terms').addEventListener('submit', (e) => {
      e.preventDefault();
      if (!$('#agree').checked) { toast(paid ? t('unlock.ndaFirst') : t('unlock.acceptFirst'), 'error'); $('#agree').focus(); return; }
      const button = e.currentTarget.querySelector('button[type="submit"]');
      if (paid) pay(button); else sign(button);
    });
    focusTop();
  }

  // Pay with Moyasar's payment form: card details go straight to Moyasar, never through Naseej.
  // The server opens the checkout (price, VAT, metadata); after 3-D Secure Moyasar returns to payment-return.html,
  // which asks the server to check the payment with Moyasar before anything is unlocked.
  const pay = (button) => withLoading(button, async () => {
    let co;
    try {
      co = await api.post(`unlock/checkout/${id}`, { ndaAccepted: true });
      await loadMoyasar();
    } catch (err) {
      toast(errorMessage(err), 'error', 7000);
      return;
    }
    cardForm(co, {
      labels: LABELS,
      step: 2,
      lineLabel: t('unlock.price'),
      onBack: terms,
      returnUrl: `payment-return.html?ideaId=${id}&paymentId=${co.paymentId}`,
    });
  });

  const sign = (button) => withLoading(button, async () => {
    const name = $('#signed-name').value.trim();
    if (name.length < 3) { $('#signed-name').setAttribute('aria-invalid', 'true'); $('#signed-name').focus(); toast(t('unlock.signName'), 'error'); return; }
    try {
      const result = await api.post(`unlock/partnership/${id}`, { termsAccepted: true, signedName: name });
      doneView(result, t('unlock.signed'));
    } catch (err) {
      toast(errorMessage(err), 'error', 7000);
    }
  });

  summary();
}

// =====================================================================
// The owner launches their own idea
// =====================================================================

async function ownerFlow() {
  let terms;
  try { terms = await api.get(`unlock/owner-terms/${id}`); } catch (err) { fail(err); return; }
  $('#title').textContent = terms.ideaTitle;
  document.title = `${t('owner.eyebrow')} — ${terms.ideaTitle}`;
  const LABELS = [t('owner.s1'), t('owner.s2'), t('owner.sPay'), t('owner.s3')];
  const fee = terms.feeAmount != null ? { fee: fmt.sar(terms.feeAmount), total: fmt.sar(terms.feeTotal) } : null;

  function intro() {
    $('#steps').innerHTML = stepper(LABELS, 0);
    body.innerHTML = `
      <div class="stack" style="gap:16px">
        <section class="flow-card" style="gap:14px">
          <h2 class="flow-q">${esc(t('owner.q1'))}</h2>
          <ul class="locked-list open">
            <li>${icon.check}<span>${esc(t('owner.p1'))}</span></li>
            <li>${icon.check}<span>${esc(t('owner.p2'))}</span></li>
            <li>${icon.check}<span>${esc(fee ? t('owner.p3', fee) : t('owner.p3Pending'))}</span></li>
            <li>${icon.check}<span>${esc(t('owner.p4', { days: terms.openingDeadlineDays }))}</span></li>
          </ul>
        </section>
        <section class="flow-card" style="gap:10px">
          <h2 class="flow-q">${esc(t('owner.demand'))}</h2>
          <div class="row" style="justify-content:space-between"><strong class="num">${esc(t('opp.votes', { v: fmt.num(terms.votesCount), n: fmt.num(terms.membershipThreshold) }))}</strong><span class="small muted">${esc(isolate(`${terms.neighborhoodName}, ${terms.city}`))}</span></div>
          ${progressBar(terms.votePercent, t('common.voteTarget'))}
          <p class="small muted">${esc(t('owner.demandNote'))}</p>
        </section>
        ${terms.available ? '' : `<div class="notice notice-warn">${icon.info}<span>${esc(serverText(terms.blockedReason))}</span></div>`}
        ${terms.available && terms.paidPaymentId ? `<div class="notice notice-info">${icon.check}<span>${esc(t('owner.paidPending'))}</span></div>` : ''}
        ${actionBar(`<a class="btn btn-secondary" href="idea.html?id=${id}">${icon.back} ${esc(t('common.back'))}</a>
          ${terms.available && terms.paidPaymentId
            ? `<button type="button" class="btn" id="finish" data-loading="${esc(t('common.wait'))}">${esc(t('owner.finish'))} ${icon.arrow}</button>`
            : `<button type="button" class="btn" id="next" ${terms.available ? '' : 'disabled'}>${esc(t('owner.readAgreement'))} ${icon.arrow}</button>`}`)}
      </div>`;
    animateBars(body);
    $('#next')?.addEventListener('click', agreement);
    // Paid earlier but the page was closed before the payment was confirmed: start the project with it
    $('#finish')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
      try {
        const result = await api.post(`unlock/owner-confirm/${id}`, { paymentId: terms.paidPaymentId });
        doneView(result, t('owner.donePaid'));
      } catch (err) {
        toast(errorMessage(err), 'error', 7000);
      }
    }));
  }

  function agreement() {
    $('#steps').innerHTML = stepper(LABELS, 1);
    const today = fmt.date(new Date());
    body.innerHTML = `
      <form class="flow-card" id="f-sign" novalidate>
        <article class="doc" aria-labelledby="doc-h">
          <h2 id="doc-h">${esc(t('owner.docTitle'))}</h2>
          <p class="small muted">${esc(t('owner.docMeta', { version: terms.termsVersion, date: today }))}</p>
          <h3>${esc(t('owner.parties'))}</h3>
          <ol>
            <li>${esc(t('owner.party1'))}</li>
            <li>${esc(t('owner.party2', { name: user.fullName }))}</li>
          </ol>
          <h3>${esc(t('owner.terms'))}</h3>
          <ol>
            <li>${esc(t('owner.t1', { title: terms.ideaTitle }))}</li>
            <li>${esc(t('owner.t2fee', fee || {}))}</li>
            <li>${esc(t('owner.t3', { days: terms.openingDeadlineDays }))}</li>
            <li>${esc(t('owner.t4'))}</li>
            <li>${esc(t('owner.t5'))}</li>
          </ol>
          <p class="small muted">${esc(t('owner.legal'))}</p>
        </article>
        <div class="field"><label for="signed-name">${esc(t('unlock.signName'))}</label>
          <input class="input" id="signed-name" value="${esc(user.fullName)}" autocomplete="name" maxlength="120"><span class="error" aria-live="polite"></span></div>
        <label class="check"><input type="checkbox" id="agree"><span>${esc(t('owner.accept'))}</span></label>
        ${actionBar(`<button type="button" class="btn btn-secondary" data-back>${icon.back} ${esc(t('common.back'))}</button>
          <button class="btn" type="submit" data-loading="${esc(t('pay.opening'))}">${esc(t('owner.signPay'))} ${icon.arrow}</button>`)}
      </form>`;
    $('[data-back]').addEventListener('click', intro);
    $('#f-sign').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = $('#signed-name').value.trim();
      if (name.length < 3) { $('#signed-name').setAttribute('aria-invalid', 'true'); $('#signed-name').focus(); toast(t('unlock.signName'), 'error'); return; }
      if (!$('#agree').checked) { toast(t('unlock.acceptFirst'), 'error'); $('#agree').focus(); return; }
      // Signing opens the payment form for the service fee; the project starts once Moyasar confirms it
      withLoading(e.currentTarget.querySelector('button[type="submit"]'), async () => {
        let co;
        try {
          co = await api.post(`unlock/owner-checkout/${id}`, { termsAccepted: true, signedName: name, termsVersion: terms.termsVersion });
          await loadMoyasar();
        } catch (err) {
          toast(errorMessage(err), 'error', 7000);
          return;
        }
        cardForm(co, {
          labels: LABELS,
          step: 2,
          lineLabel: t('owner.feeLine'),
          onBack: agreement,
          returnUrl: `payment-return.html?mode=owner&ideaId=${id}&paymentId=${co.paymentId}`,
        });
      });
    });
    focusTop();
  }

  intro();
}

// The server confirmed the agreement: show the contract and where to find a copy
function doneView(result, title) {
  $('#steps').innerHTML = '';
  body.innerHTML = `
    <div class="flow-card done-card" role="status">
      <span class="done-mark" aria-hidden="true">${icon.check}</span>
      <h2 class="flow-q" tabindex="-1" id="done-h">${esc(title)}</h2>
      <p class="muted">${esc(serverText(result.message) || '')}</p>
      <p class="small">${esc(t('proj.contract', { n: result.contractNumber }))}</p>
      <div class="next-steps">
        <a class="btn" href="projects.html">${esc(t('idea.goProjects'))}</a>
        <a class="btn btn-secondary" href="agreement.html?contract=${result.contractId}">${esc(t('agr.view'))}</a>
      </div>
    </div>`;
  $('#done-h').focus();
}

// The card step: the totals from the server's checkout, then Moyasar's card fields. After 3-D Secure Moyasar
// returns to payment-return.html, which asks the server to check the payment with Moyasar.
function cardForm(co, { labels, step, lineLabel, onBack, returnUrl }) {
  $('#steps').innerHTML = stepper(labels, step);
  body.innerHTML = `
    <section class="flow-card pay-card">
      <div class="stack" style="gap:4px">
        <h2 class="flow-q">${esc(t('pay.cardTitle'))}</h2>
        <p class="muted small">${esc(t('pay.cardText'))}</p>
      </div>
      <dl class="dl totals"><dt>${esc(lineLabel)}</dt><dd class="num">${fmt.sar(co.amount)}</dd><dt>${esc(t('unlock.vat'))}</dt><dd class="num">${fmt.sar(co.vat)}</dd><dt><strong>${esc(t('unlock.total'))}</strong></dt><dd class="num"><strong>${fmt.sar(co.total)}</strong></dd></dl>
      ${co.testMode ? `<div class="notice notice-warn small">${icon.info}<span>${esc(t('pay.testMode'))}</span></div>` : ''}
      <div class="mysr-form" id="mysr"></div>
      <p class="small muted pay-secure">${icon.lock}<span>${esc(t('pay.secure'))}</span></p>
      <button type="button" class="btn btn-ghost btn-sm" id="pay-back" style="align-self:flex-start">${icon.back} ${esc(t('common.back'))}</button>
    </section>`;
  $('#pay-back').addEventListener('click', onBack);
  // Moyasar renames the element's id once it starts and then looks it up again, so it gets the element
  // itself, never the '#mysr' selector (with the selector the card fields crash and the box stays empty)
  const box = $('#mysr');
  // When the card fields can't be drawn, say so (with Moyasar's reason) instead of leaving an empty card
  const formFailed = (reason) => {
    if (!box.isConnected) return;
    box.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(t('pay.formFailed'))}${reason ? `<br><small dir="ltr">${esc(reason)}</small>` : ''}</span></div>
      <button type="button" class="btn btn-secondary btn-sm" id="pay-retry" style="margin-top:12px">${esc(t('pay.tryAgain'))}</button>`;
    $('#pay-retry').addEventListener('click', () => cardForm(co, { labels, step, lineLabel, onBack, returnUrl }));
  };
  try {
    window.Moyasar.init({
      element: box,
      amount: co.amountHalalas,
      currency: co.currency,
      description: co.description,
      publishable_api_key: co.publishableKey,
      callback_url: new URL(returnUrl, location.href).href,
      methods: ['creditcard'],
      supported_networks: ['mada', 'visa', 'mastercard'],
      language: document.documentElement.lang === 'ar' ? 'ar' : 'en',
      metadata: co.metadata,
      // Moyasar sends the browser to callback_url after 3-D Secure; these report problems before that
      on_failure: (error) => { console.error('Moyasar payment failed', error); toast(String(error?.message || error || t('pay.failed')), 'error', 8000); },
    });
  } catch (err) {
    console.error('Moyasar form', err);
    formFailed(err?.message || String(err));
    return;
  }
  // The form draws its card fields into the box; none after a few seconds means it couldn't
  setTimeout(() => { if (!box.querySelector('input')) formFailed(''); }, 6000);
  focusTop();
}

// Moyasar's payment form (script + styles), loaded only when someone pays
const MOYASAR_VERSION = '1.14.0';
function loadMoyasar() {
  if (window.Moyasar) return Promise.resolve();
  const css = document.createElement('link');
  css.rel = 'stylesheet';
  css.href = `https://cdn.moyasar.com/mpf/${MOYASAR_VERSION}/moyasar.css`;
  document.head.appendChild(css);
  return new Promise((resolve, reject) => {
    const js = document.createElement('script');
    js.src = `https://cdn.moyasar.com/mpf/${MOYASAR_VERSION}/moyasar.js`;
    const timer = setTimeout(() => reject(new Error(t('pay.formFailed'))), 20000);
    js.onload = () => { clearTimeout(timer); if (window.Moyasar) resolve(); else reject(new Error(t('pay.formFailed'))); };
    js.onerror = () => { clearTimeout(timer); reject(new Error(t('pay.formFailed'))); };
    document.head.appendChild(js);
  });
}
