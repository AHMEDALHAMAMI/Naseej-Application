// The contract after unlocking or adopting: its own flow, one step at a time.
// 1 benefits (investor) → 2 investor signs → 3 idea owner signs → 4 active (90 days to open).
// Signing = typed name + drawn signature + a code emailed to the signer. Calm, plain wording.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, applyFieldErrors, cityLabel, placeLabel, t } from '../ui.js';
import { serverText, tn } from '../i18n.js';

const user = await guard(['INVESTOR', 'RESIDENT']);
const id = Number(params().get('id'));
const app = $('#app');
if (!id) location.replace('projects.html');

let project;

async function load() {
  try {
    const all = await api.get('projects/mine');
    project = all.find((p) => p.contractId === id);
  } catch (err) {
    app.innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    return;
  }
  if (!project) {
    app.innerHTML = `<a class="btn btn-ghost btn-sm" href="projects.html" style="padding-inline-start:0">${icon.back} ${esc(t('nav.myProjects'))}</a>
      <div class="notice notice-error" style="margin-top:16px">${icon.x}<span>${esc(t('ctr.notFound'))}</span></div>`;
    return;
  }
  render();
}

// ---------- Layout ----------

function stepState() {
  const p = project;
  const active = p.contractStatus === 'ACTIVE' || p.nextStep === 'IN_PROGRESS' || p.nextStep === 'OPENED';
  return [
    { key: 'ctr.s1', done: p.benefitsSet },
    { key: 'ctr.s2', done: p.investorSigned },
    { key: 'ctr.s3', done: p.founderSigned },
    { key: 'ctr.s4', done: active },
  ];
}

function render() {
  const p = project;
  const steps = stepState();
  const current = steps.findIndex((s) => !s.done);
  document.title = `${p.ideaTitle} — ${t('title.contract')}`;
  app.innerHTML = `
    <a class="btn btn-ghost btn-sm" href="projects.html" style="padding-inline-start:0">${icon.back} ${esc(t('nav.myProjects'))}</a>
    <div class="page-head" style="margin-top:8px">
      <div class="stack" style="gap:8px">
        <span class="eyebrow">${esc(t('ctr.eyebrow', { n: p.contractNumber }))}</span>
        <h1 class="project-title">${esc(p.ideaTitle)}</h1>
        <span class="muted small">${esc(placeLabel(p.neighborhoodName, p.city))} · ${esc(t('ctr.parties', { i: p.investorName, o: p.founderName }))}</span>
      </div>
      <button type="button" class="btn btn-secondary btn-sm" id="read-full">${esc(t('ctr.readFull'))}</button>
    </div>
    <ol class="stepper" aria-label="${esc(t('ctr.steps'))}">
      ${steps.map((s, i) => `<li class="${s.done ? 'done' : ''} ${i === current ? 'current' : ''}" ${i === current ? 'aria-current="step"' : ''}><span class="dot">${s.done ? icon.check : i + 1}</span><span>${esc(t(s.key))}</span></li>`).join('')}
    </ol>
    <div class="contract-body" id="body"></div>`;
  $('#read-full').addEventListener('click', openDocument);
  body();
}

function body() {
  const p = project;
  const investor = p.myRole === 'INVESTOR';
  const box = $('#body');
  const step = p.nextStep;

  if (investor && step === 'SET_BENEFITS') return benefitsForm(box);
  if (p.canSign) return signForm(box);
  if (step === 'FOUNDER_SIGN') return info(box, 'ctr.waitingTitle', 'ctr.waitingText', 'amber');
  if (step === 'WAITING_PAYMENT') return info(box, 'ctr.paymentTitle', 'ctr.paymentText', 'amber');
  if (step === 'IN_PROGRESS') return active(box);
  if (step === 'OPENED') return info(box, 'ctr.openedTitle', null, 'solid', `<a class="btn" href="projects.html">${esc(t('nav.myProjects'))}</a>`);
  if (step === 'ENDED') return info(box, 'ctr.endedTitle', null, 'red');
  // The idea owner waits while the investor prepares the contract
  return info(box, 'ctr.waitTitle', 'ctr.investorFirst', 'amber', benefitsSummary());
}

function info(box, titleKey, textKey, tone, extra = '') {
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:12px">
      <span class="badge ${tone === 'solid' ? 'badge-solid' : tone === 'red' ? 'badge-red' : 'badge-amber'}" style="align-self:flex-start">${esc(t(titleKey))}</span>
      ${textKey ? `<p class="muted">${esc(t(textKey))}</p>` : ''}
      ${extra}
    </section>`;
}

function benefitsSummary() {
  const p = project;
  if (!p.benefitsSet) return '';
  return `<dl class="dl benefits-dl">
    <dt>${esc(t('ctr.membersGet'))}</dt><dd>${esc(t('ctr.discount', { n: Number(p.foundingMemberDiscount) }))}</dd>
    <dt>${esc(t('ctr.ownerGets'))}</dt><dd>${esc(t('ctr.discount', { n: Number(p.ideaOwnerDiscount) }))}</dd>
    ${p.benefitsNote ? `<dt>${esc(t('ctr.note'))}</dt><dd>${esc(p.benefitsNote)}</dd>` : ''}
  </dl>`;
}

function active(box) {
  const p = project;
  const days = p.openingDeadline ? Math.ceil((new Date(`${p.openingDeadline}T23:59:59`) - new Date()) / 86400000) : null;
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:12px">
      <span class="badge badge-solid" style="align-self:flex-start">${esc(t('ctr.activeTitle'))}</span>
      ${p.openingDeadline ? `<p><strong>${esc(t('ctr.deadline', { date: fmt.date(p.openingDeadline) }))}</strong>${days != null ? ` · <span class="muted">${esc(days >= 0 ? t('proj.daysLeft', { d: tn(days, 'count.days') }) : t('proj.deadlinePassed'))}</span>` : ''}</p>` : ''}
      ${benefitsSummary()}
      <a class="btn" href="projects.html" style="align-self:flex-start">${esc(t('ctr.goProjects'))}</a>
    </section>`;
}

// ---------- 1. Benefits ----------

function benefitsForm(box) {
  const p = project;
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:16px" aria-labelledby="ben-h">
      <div class="stack" style="gap:4px"><h2 id="ben-h">${esc(t('ctr.benefitsTitle'))}</h2><p class="small muted">${esc(t('ctr.benefitsText'))}</p></div>
      <form id="benefits" class="stack" style="gap:14px" novalidate>
        <div class="grid grid-2" style="gap:14px">
          <div class="field"><label for="foundingMemberDiscount">${esc(t('ctr.membersDiscount'))}</label>
            <input class="input" id="foundingMemberDiscount" name="foundingMemberDiscount" inputmode="decimal" dir="ltr" placeholder="10" value="${p.foundingMemberDiscount ?? ''}"><span class="error" aria-live="polite"></span></div>
          <div class="field"><label for="ideaOwnerDiscount">${esc(t('ctr.ownerDiscount'))}</label>
            <input class="input" id="ideaOwnerDiscount" name="ideaOwnerDiscount" inputmode="decimal" dir="ltr" placeholder="15" value="${p.ideaOwnerDiscount ?? ''}"><span class="hint">${esc(t('ctr.ownerHint'))}</span><span class="error" aria-live="polite"></span></div>
        </div>
        <div class="field"><label for="benefitsNote">${esc(t('ctr.note'))} <span class="muted">${esc(t('form.optional'))}</span></label>
          <input class="input" id="benefitsNote" name="benefitsNote" maxlength="500" placeholder="${esc(t('ctr.notePh'))}" value="${esc(p.benefitsNote || '')}"><span class="error" aria-live="polite"></span></div>
        <div class="row" style="justify-content:space-between">
          <span class="small muted">${esc(t('ctr.benefitsLocked'))}</span>
          <button class="btn" type="submit" data-loading="${esc(t('common.saving'))}">${esc(t('ctr.saveBenefits'))}</button>
        </div>
      </form>
    </section>`;
  $('#benefits').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.currentTarget;
    applyFieldErrors(f, null);
    const num = (v) => { const clean = String(v).replace(/[^\d.]/g, ''); return clean === '' ? null : Number(clean); };
    const bodyData = {
      foundingMemberDiscount: num(f.foundingMemberDiscount.value),
      ideaOwnerDiscount: num(f.ideaOwnerDiscount.value),
      benefitsNote: f.benefitsNote.value.trim() || null,
    };
    withLoading(f.querySelector('[type="submit"]'), async () => {
      try {
        project = await api.put(`projects/${id}/benefits`, bodyData);
        render();
      } catch (err) {
        if (!applyFieldErrors(f, err)) toast(errorMessage(err), 'error');
      }
    });
  });
}

// ---------- 2/3. Signing ----------

function signForm(box) {
  const p = project;
  const investor = p.myRole === 'INVESTOR';
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:18px" aria-labelledby="sign-h">
      <div class="row" style="justify-content:space-between">
        <h2 id="sign-h">${esc(t(investor ? 'ctr.signTitle' : 'ctr.founderTurn'))}</h2>
        ${investor && p.canSetBenefits ? `<button type="button" class="btn btn-ghost btn-sm" id="edit-benefits">${esc(t('ctr.editBenefits'))}</button>` : ''}
      </div>
      ${investor ? '' : `<span class="small" style="font-weight:600">${esc(t('ctr.founderBenefits'))}</span>`}
      ${benefitsSummary()}
      <div class="surface-sand stack" style="gap:8px">
        <strong class="small">${esc(t('ctr.terms'))}</strong>
        <ul class="ticks small">
          <li>${esc(t('ctr.t1'))}</li><li>${esc(t('ctr.t2'))}</li><li>${esc(t('ctr.t3'))}</li><li>${esc(t('ctr.t4'))}</li>
        </ul>
      </div>
      <form id="sign" class="stack" style="gap:14px" novalidate>
        <div class="field"><label for="typedName">${esc(t('ctr.typedName'))}</label>
          <input class="input" id="typedName" name="typedName" autocomplete="name" value="${esc(user.fullName)}"><span class="error" aria-live="polite"></span></div>
        <div class="field">
          <div class="row" style="justify-content:space-between"><span class="label" id="pad-label">${esc(t('ctr.draw'))}</span><button type="button" class="btn btn-ghost btn-sm" id="clear-pad">${esc(t('ctr.clear'))}</button></div>
          <canvas class="sig-pad" id="pad" aria-labelledby="pad-label" role="img"></canvas>
          <span class="hint">${esc(t('ctr.drawHint'))}</span>
        </div>
        <div class="field"><label for="code">${esc(t('ctr.code'))}</label>
          <div class="code-row">
            <input class="input" id="code" name="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" dir="ltr" placeholder="••••••">
            <button type="button" class="btn btn-secondary" id="send-code" data-loading="${esc(t('verify.sending'))}">${esc(t('ctr.sendCode'))}</button>
          </div>
          <span class="error" aria-live="polite"></span>
        </div>
        <label class="check"><input type="checkbox" id="agree"><span>${esc(t('ctr.agree'))}</span></label>
        <button class="btn btn-block" type="submit" data-loading="${esc(t('ctr.signing'))}">${esc(t('ctr.sign'))}</button>
      </form>
    </section>`;

  $('#edit-benefits')?.addEventListener('click', () => benefitsForm(box));
  const pad = signaturePad($('#pad'));
  $('#clear-pad').addEventListener('click', pad.clear);

  // Code: one send at a time, with a short wait before sending again
  const sendBtn = $('#send-code');
  let timer = null;
  sendBtn.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const res = await api.post(`projects/${id}/sign-otp`);
      toast(serverText(res?.message) || t('ctr.codeSent'), 'success');
      $('#code').focus();
      let left = 60;
      clearInterval(timer);
      timer = setInterval(() => {
        left -= 1;
        sendBtn.disabled = left > 0;
        sendBtn.textContent = left > 0 ? t('ctr.resendIn', { n: left }) : t('ctr.resend');
        if (left <= 0) clearInterval(timer);
      }, 1000);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }).then(() => { if (timer) sendBtn.disabled = true; }));

  $('#sign').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.currentTarget;
    applyFieldErrors(f, null);
    const code = f.code.value.replace(/\D/g, '');
    if (!pad.hasInk()) { toast(t('ctr.needSignature'), 'error'); return; }
    if (!/^\d{6}$/.test(code)) {
      f.code.setAttribute('aria-invalid', 'true');
      f.code.closest('.field').querySelector('.error').textContent = t('ctr.needCode');
      f.code.focus();
      return;
    }
    if (!$('#agree').checked) { toast(t('ctr.needAgree'), 'error'); return; }
    withLoading(f.querySelector('[type="submit"]'), async () => {
      try {
        project = await api.post(`projects/${id}/sign`, {
          typedName: f.typedName.value.trim(),
          signatureImage: pad.toDataURL(),
          termsAccepted: true,
          code,
        });
        clearInterval(timer);
        toast(t('ctr.signed'), 'success');
        render();
      } catch (err) {
        if (!applyFieldErrors(f, err)) toast(errorMessage(err), 'error');
      }
    });
  });
}

/** A small drawing pad: pointer events (mouse, pen, touch), crisp on high-density screens. */
function signaturePad(canvas) {
  const ctx = canvas.getContext('2d');
  let ink = false;
  let drawing = false;
  const size = () => {
    const ratio = window.devicePixelRatio || 1;
    const { width, height } = canvas.getBoundingClientRect();
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.lineWidth = 2.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#1F2A24';
    ink = false;
  };
  const point = (e) => {
    const r = canvas.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  };
  canvas.addEventListener('pointerdown', (e) => {
    drawing = true;
    canvas.setPointerCapture(e.pointerId);
    const [x, y] = point(e);
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + 0.01, y);
    ctx.stroke();
    ink = true;
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!drawing) return;
    const [x, y] = point(e);
    ctx.lineTo(x, y);
    ctx.stroke();
  });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach((type) => canvas.addEventListener(type, () => { drawing = false; }));
  size();
  return {
    clear: size,
    hasInk: () => ink,
    toDataURL: () => canvas.toDataURL('image/png'),
  };
}

// ---------- The full contract (print-ready page from the server) ----------

async function openDocument() {
  const win = window.open('', '_blank'); // opened now, filled after the authorised request
  try {
    const html = await api.get(`projects/${id}/contract`);
    const url = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }));
    if (win) win.location.href = url; else location.href = url;
  } catch (err) {
    win?.close();
    toast(errorMessage(err), 'error');
  }
}

load();
