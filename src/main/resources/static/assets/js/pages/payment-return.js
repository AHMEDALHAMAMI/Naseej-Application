// Moyasar sends the browser here after checkout (?id=<moyasar id>&status=...). We confirm with our server.
import { api } from '../api.js';
import { $, esc, guard, icon, params, errorMessage, t } from '../ui.js';
import { serverText } from '../i18n.js';

await guard(['INVESTOR']);
const p = params();
const ideaId = Number(p.get('ideaId'));
const moyasarPaymentId = p.get('id');
const paymentId = p.get('paymentId') ? Number(p.get('paymentId')) : null;
const app = $('#app');

app.innerHTML = `<div class="card stack" style="gap:14px;max-width:560px;margin:40px auto;text-align:center;align-items:center" role="status">
  <span class="spinner"></span><h1 style="font-size:30px">${esc(t('pay.confirming'))}</h1><p class="muted">${esc(t('pay.keepOpen'))}</p></div>`;

function done(html) {
  app.innerHTML = `<div class="card stack" style="gap:16px;max-width:560px;margin:40px auto;text-align:center;align-items:center">${html}</div>`;
}

if (!ideaId || (!moyasarPaymentId && !paymentId)) {
  done(`${icon.x}<h1 style="font-size:30px">${esc(t('pay.missing'))}</h1><a class="btn" href="investor.html">${esc(t('pay.backOpps'))}</a>`);
} else if (p.get('status') && p.get('status') !== 'paid') {
  done(`<span class="badge badge-red">${icon.x} ${esc(t('pay.notCompleted'))}</span><h1 style="font-size:30px">${esc(t('pay.failed'))}</h1>
    <p class="muted">${esc(p.get('message') || t('pay.nothingCharged'))}</p>
    <a class="btn" href="unlock.html?id=${ideaId}">${esc(t('pay.tryAgain'))}</a>`);
} else {
  try {
    const result = await api.post(`unlock/confirm-payment/${ideaId}`, paymentId ? { paymentId } : { moyasarPaymentId });
    done(`<span class="badge badge-solid">${icon.check} ${esc(t('pay.paid'))}</span><h1 style="font-size:34px">${t('pay.yours')}</h1>
      <p class="muted">${esc(serverText(result.message || ''))}<br>${esc(t('pay.contract', { n: result.contractNumber }))}</p>
      <div class="row" style="justify-content:center"><a class="btn" href="${result.contractId ? `contract.html?id=${result.contractId}` : 'projects.html'}">${esc(t('pay.goProjects'))}</a><a class="btn btn-secondary" href="idea.html?id=${ideaId}">${esc(t('pay.openStudy'))}</a></div>`);
  } catch (err) {
    done(`<span class="badge badge-amber">${icon.warn} ${esc(t('pay.notConfirmed'))}</span><h1 style="font-size:30px">${esc(t('pay.couldNot'))}</h1>
      <p class="muted">${esc(errorMessage(err))}</p>
      <div class="row" style="justify-content:center"><button class="btn" type="button" onclick="location.reload()">${esc(t('pay.checkAgain'))}</button><a class="btn btn-secondary" href="unlock.html?id=${ideaId}">${esc(t('common.back'))}</a></div>`);
  }
}
