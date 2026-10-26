// Design system reference: every shared component in the current language, for review and for building pages.
import { $, esc, icon, toast, modal, emptyState, skeletonCards, progressBar, animateBars, initPage, t } from '../ui.js';
import { langButton, wireLangButtons } from '../i18n.js';
import { stepper, breadcrumb, wireTabs, aiBlock, warnNote, scoreMeter, wirePopovers, rangeSlider } from '../components.js';

initPage();
const app = $('#app');

const swatch = (name, value) => `<div class="sg-swatch"><i style="background:${value}"></i><strong dir="ltr">${esc(name)}</strong><span class="muted num" dir="ltr">${esc(value)}</span></div>`;
const section = (title, body) => `<section class="sg-section"><h2>${esc(title)}</h2>${body}</section>`;

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px"><span class="eyebrow">NASEEJ</span><h1>${esc(t('sg.title'))}</h1></div>
    ${langButton()}
  </div>

  ${section(t('sg.colors'), `<div class="sg-swatches">
    ${swatch('--primary', '#2F5444')}${swatch('--primary-hover', '#1F3B2F')}${swatch('--green-600', '#5A7D66')}${swatch('--primary-soft', '#E7EEE9')}
    ${swatch('--bg', '#FCF9F4')}${swatch('--surface-muted', '#F3EEE5')}${swatch('--border', '#E8E0D3')}${swatch('--gold', '#F3D58A')}
    ${swatch('--text', '#1F2A24')}${swatch('--text-muted', '#5E6A63')}${swatch('--warning-text', '#7A4E00')}${swatch('--danger', '#A33A2B')}
  </div>`)}

  ${section(t('sg.type'), `<div class="sg-type">
    <h1 style="font-size:var(--fs-4xl)">${t('sg.sampleTitle')}</h1>
    <h2 style="font-size:var(--fs-2xl)">${esc(t('sg.sampleH2'))}</h2>
    <p style="font-size:var(--fs-lg)">${esc(t('sg.sampleLead'))}</p>
    <p>${esc(t('sg.sampleBody'))}</p>
    <p class="small muted">${esc(t('sg.sampleSmall'))}</p>
  </div>`)}

  ${section(t('sg.buttons'), `
    <div class="row">
      <button class="btn" type="button">${esc(t('sg.primary'))}</button>
      <button class="btn btn-secondary" type="button">${esc(t('sg.secondary'))}</button>
      <button class="btn btn-ghost" type="button">${esc(t('sg.ghost'))}</button>
      <button class="btn btn-danger" type="button">${esc(t('sg.danger'))}</button>
      <button class="btn" type="button" disabled>${esc(t('sg.disabled'))}</button>
      <button class="btn btn-sm" type="button">${esc(t('sg.small'))}</button>
    </div>
    <div class="stack" style="gap:6px;max-width:360px">
      <button class="btn btn-block" type="button">${esc(t('sg.earlyCta'))}</button>
      ${warnNote(t('sg.earlyWarn'))}
    </div>`)}

  ${section(t('sg.forms'), `
    <div class="grid grid-2" style="gap:16px;max-width:820px">
      <div class="field"><label for="sg-name">${esc(t('form.fullName'))}</label><input class="input" id="sg-name"><span class="hint">${esc(t('sg.hint'))}</span></div>
      <div class="field"><label for="sg-err">${esc(t('form.email'))}</label><input class="input" id="sg-err" aria-invalid="true" value="sara@" dir="ltr"><span class="error">${esc(t('sg.error'))}</span></div>
      <div class="field"><label for="sg-sel">${esc(t('form.city'))}</label><select class="select" id="sg-sel"><option>${esc(t('city.Riyadh'))}</option><option>${esc(t('city.Jeddah'))}</option></select></div>
      <div class="field"><span class="label">${esc(t('inv.fields'))}</span><div class="chips"><button type="button" class="chip" aria-pressed="true">${esc(t('field.HEALTH'))}</button><button type="button" class="chip" aria-pressed="false">${esc(t('field.FOOD'))}</button><button type="button" class="chip" aria-pressed="false">${esc(t('field.RETAIL'))}</button></div></div>
    </div>
    <label class="check"><input type="checkbox"><span>${esc(t('form.terms'))}</span></label>
    <div class="segmented" role="group" aria-label="${esc(t('res.filter'))}"><button type="button" aria-pressed="true">${esc(t('res.all'))}</button><button type="button" aria-pressed="false">${esc(t('res.inRadius'))}</button><button type="button" aria-pressed="false">${esc(t('res.voted'))}</button></div>
    <div style="max-width:520px" id="sg-range"></div>`)}

  ${section(t('sg.navigation'), `
    ${stepper([t('sg.step1'), t('sg.step2'), t('sg.step3'), t('sg.step4')], 2, { links: ['#', '#'] })}
    ${breadcrumb([{ label: t('inv.ksa'), href: '#' }, { label: t('city.Riyadh'), href: '#' }, { label: t('sg.hood') }])}
    <div>
      <div class="tabs" role="tablist" aria-label="${esc(t('sg.tabs'))}">
        <button type="button" role="tab" id="tab-a" data-tab="profile" aria-controls="panel-a">${esc(t('sg.tabProfile'))}</button>
        <button type="button" role="tab" id="tab-b" data-tab="addresses" aria-controls="panel-b">${esc(t('sg.tabAddresses'))}</button>
        <button type="button" role="tab" id="tab-c" data-tab="investor" aria-controls="panel-c">${esc(t('sg.tabInvestor'))}</button>
      </div>
      <div role="tabpanel" id="panel-a" aria-labelledby="tab-a"><p class="muted">${esc(t('sg.tabProfile'))}</p></div>
      <div role="tabpanel" id="panel-b" aria-labelledby="tab-b" hidden><p class="muted">${esc(t('sg.tabAddresses'))}</p></div>
      <div role="tabpanel" id="panel-c" aria-labelledby="tab-c" hidden><p class="muted">${esc(t('sg.tabInvestor'))}</p></div>
    </div>
    <span class="addr-chip">${icon.pin}<span>${esc(t('sg.home'))}</span><span class="muted">· ${esc(t('sg.hood'))}</span></span>`)}

  ${section(t('sg.data'), `
    <div class="grid grid-2" style="gap:16px">
      <div class="card">${scoreMeter(78, { shortage: 85, competition: 80, demand: 70 })}</div>
      <div class="card stack" style="gap:12px">
        ${scoreMeter(62, {}, { compact: true })}
        ${scoreMeter(41, { shortage: 30, competition: 45, demand: 48 })}
      </div>
    </div>
    <div class="row"><span class="badge">${esc(t('status.PUBLISHED'))}</span><span class="badge badge-solid">${esc(t('status.COMPLETED'))}</span><span class="badge badge-sand">${esc(t('cat.Pharmacy'))}</span><span class="badge badge-amber">${esc(t('status.SCREENING'))}</span><span class="badge badge-red">${esc(t('status.REJECTED'))}</span><span class="badge badge-lock">${icon.lock} ${esc(t('teaser.badge'))}</span></div>
    <div class="row"><span class="fit full">${esc(t('sg.fitFull'))}</span><span class="fit partial">${esc(t('sg.fitPartial'))}</span><span class="fit unknown">${esc(t('sg.fitUnknown'))}</span></div>
    <div style="max-width:420px">${progressBar(64, t('common.voteTarget'))}</div>`)}

  ${section(t('sg.ai'), `
    <div class="grid grid-2" style="gap:16px;align-items:start">
      ${aiBlock(`<p>${esc(t('sg.aiSample'))}</p><dl class="dl"><dt>${esc(t('bm.customers'))}</dt><dd>${esc(t('sg.aiCustomers'))}</dd><dt>${esc(t('bm.capital'))}</dt><dd class="num">${esc(t('sg.aiCapital'))}</dd></dl>`, { title: t('bm.title') })}
      <section class="source-card"><h3>${esc(t('sg.fromMap'))}</h3><ul class="list"><li><span>${esc(t('sg.competitor1'))}</span><span class="muted num">0.8 km</span></li><li><span>${esc(t('sg.competitor2'))}</span><span class="muted num">1.4 km</span></li></ul><span class="source-note">${esc(t('check.source'))}</span></section>
    </div>`)}

  ${section(t('sg.feedback'), `
    <div class="stack" style="gap:10px;max-width:720px">
      <div class="notice notice-info">${icon.info}<span>${esc(t('sg.noticeInfo'))}</span></div>
      <div class="notice notice-warn">${icon.warn}<span>${esc(t('sg.noticeWarn'))}</span></div>
      <div class="notice notice-error">${icon.x}<span>${esc(t('sg.noticeError'))}</span></div>
    </div>
    <div class="row">
      <button class="btn btn-secondary" type="button" id="sg-toast">${esc(t('sg.showToast'))}</button>
      <button class="btn btn-secondary" type="button" id="sg-modal">${esc(t('sg.showModal'))}</button>
    </div>
    <div class="grid grid-2" style="gap:16px">
      ${emptyState(t('ai.noResults'), t('sg.emptyText'), `<div class="row"><a class="btn btn-sm" href="#">${esc(t('inv.proposeOwn'))}</a><button class="btn btn-secondary btn-sm" type="button">${esc(t('sg.widen'))}</button></div>`)}
      <div class="stack" style="gap:12px">${skeletonCards(2, 90)}</div>
    </div>`)}

  ${section(t('sg.options'), `
    <fieldset class="grid grid-2" style="gap:12px;border:0;padding:0;margin:0">
      <legend class="sr-only">${esc(t('unlock.choose'))}</legend>
      <label class="option"><input type="radio" name="sg-way" checked><strong>${esc(t('unlock.upfront'))}</strong><span class="small muted">${esc(t('land.upfrontText'))}</span></label>
      <label class="option"><input type="radio" name="sg-way"><strong>${esc(t('unlock.partnership'))}</strong><span class="small muted">${esc(t('land.partnerText'))}</span></label>
    </fieldset>`)}
`;

wireLangButtons(app);
wireTabs(app.querySelector('.tabs').parentElement);
wirePopovers(app);
animateBars(app);
rangeSlider($('#sg-range'), { value: [200000, 800000], label: t('pref.budget') });
$('#sg-toast').onclick = () => toast(t('sg.toastText'), 'success');
$('#sg-modal').onclick = () => modal({ title: t('sg.modalTitle'), body: `<p class="muted">${esc(t('sg.modalText'))}</p>` });
