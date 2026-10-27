// The contract after buying (unlocking or adopting) an idea: its own flow, three steps.
// 1 purchase (done) → 2 the investor signs (within 14 days) → 3 active (180 days to open, extendable once by 60 days
// near the deadline). The idea owner doesn't sign and gets no discount; they can read the contract and follow along.
// Signing = typed name + drawn signature + a code emailed to the investor. Calm, plain wording.
import { api } from '../api.js';
import { $, esc, fmt, guard, icon, params, toast, withLoading, errorMessage, applyFieldErrors, placeLabel, modal, t } from '../ui.js';
import { serverText, tn } from '../i18n.js';
import { stepper } from '../components.js';

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
  // The signature is named from the reader's side
  const owner = p.myRole !== 'INVESTOR';
  return [
    { key: 'ctr.s1', done: true },
    { key: owner ? 'ctr.s2Owner' : 'ctr.s2', done: p.investorSigned || active },
    { key: 'ctr.s3', done: active },
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
    ${stepper(steps.map((s) => t(s.key)), current === -1 ? steps.length - 1 : current)}
    <div class="contract-body" id="body"></div>`;
  $('#read-full').addEventListener('click', openDocument);
  body();
}

function body() {
  const p = project;
  const box = $('#body');
  const step = p.nextStep;

  if (p.canSign) return signForm(box);
  if (step === 'WAITING_PAYMENT') return info(box, 'ctr.paymentTitle', 'ctr.paymentText', 'amber', signDeadline());
  if (step === 'IN_PROGRESS') return active(box);
  if (step === 'OPENED') return info(box, 'ctr.openedTitle', null, 'solid', `<a class="btn" href="projects.html">${esc(t('nav.myProjects'))}</a>`);
  if (step === 'ENDED') return info(box, 'ctr.endedTitle', null, 'red');
  // The idea owner waits while the investor signs; nothing for them to sign
  return info(box, 'ctr.waitTitle', 'ctr.investorFirst', 'amber', signDeadline());
}

const daysUntil = (date) => Math.ceil((new Date(`${date}T23:59:59`) - new Date()) / 86400000);

// Until the investor signs: the last day to sign (14 days after the purchase)
function signDeadline() {
  const p = project;
  if (!p.signingDeadline) return '';
  const days = daysUntil(p.signingDeadline);
  if (days < 0) return `<div class="notice notice-warn small">${icon.info}<span>${esc(t('proj.signPassed'))}</span></div>`;
  return `<div class="notice ${days <= 3 ? 'notice-warn' : 'notice-info'} small">${icon.info}<span><strong>${esc(t('ctr.signBy', { date: fmt.date(p.signingDeadline), d: tn(days, 'count.days') }))}</strong><br>${esc(t('ctr.signByHint'))}</span></div>`;
}

function info(box, titleKey, textKey, tone, extra = '') {
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:12px">
      <span class="badge ${tone === 'solid' ? 'badge-solid' : tone === 'red' ? 'badge-red' : 'badge-amber'}" style="align-self:flex-start">${esc(t(titleKey))}</span>
      ${textKey ? `<p class="muted">${esc(t(textKey))}</p>` : ''}
      ${extra}
    </section>`;
}

function active(box) {
  const p = project;
  const days = p.openingDeadline ? daysUntil(p.openingDeadline) : null;
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:12px">
      <span class="badge badge-solid" style="align-self:flex-start">${esc(t('ctr.activeTitle'))}</span>
      ${p.openingDeadline ? `<p><strong>${esc(t('ctr.deadline', { date: fmt.date(p.openingDeadline) }))}</strong>${p.extended ? ` <span class="badge badge-sand">${esc(t('proj.extendedTag'))}</span>` : ''}${days != null ? ` · <span class="muted">${esc(days >= 0 ? t('proj.daysLeft', { d: tn(days, 'count.days') }) : t('proj.deadlinePassed'))}</span>` : ''}</p>` : ''}
      ${extension()}
      <a class="btn" href="projects.html" style="align-self:flex-start">${esc(t('ctr.goProjects'))}</a>
    </section>`;
  $('#extend')?.addEventListener('click', extend);
}

// ---------- The one 60-day extension (investor, near the deadline, with recent progress) ----------

function extension() {
  const p = project;
  if (p.myRole !== 'INVESTOR' || p.nextStep !== 'IN_PROGRESS') return '';
  if (p.extended) return `<p class="small muted">${esc(t('ctr.alreadyExtended'))}</p>`;
  let action = '';
  if (p.canExtend) {
    action = `<button type="button" class="btn btn-secondary btn-sm" id="extend" style="align-self:flex-start" data-loading="${esc(t('ctr.extending'))}">${esc(t('ctr.extendBtn'))}</button>`;
  } else if (p.extensionBlockedReason) {
    action = `<span class="small" style="color:var(--amber-800)">${esc(serverText(p.extensionBlockedReason))}</span>`;
  } else if (p.extensionOpensOn) {
    action = `<span class="small muted">${esc(t('ctr.extendFrom', { date: fmt.date(p.extensionOpensOn) }))}</span>`;
  } else {
    return '';
  }
  return `<div class="surface-sand stack" style="gap:8px">
    <strong class="small">${esc(t('ctr.extendTitle'))}</strong>
    <span class="small muted">${esc(t('ctr.extendText'))}</span>
    ${action}
  </div>`;
}

async function extend() {
  const next = new Date(`${project.openingDeadline}T12:00:00`);
  next.setDate(next.getDate() + 60);
  const nextDate = next.toISOString().slice(0, 10);
  const result = await modal({
    title: t('ctr.extendTitle'),
    body: `<p class="muted">${esc(t('ctr.extendConfirm', { date: fmt.date(nextDate) }))}</p>`,
    confirmText: t('ctr.extendBtn'),
    onConfirm: () => api.post(`projects/${id}/extend`),
  });
  if (result) {
    project = result;
    toast(t('ctr.extendedDone'), 'success');
    render();
  }
}

// ---------- 2. Signing (the investor) ----------

function signForm(box) {
  const p = project;
  box.innerHTML = `
    <section class="card stack contract-card" style="gap:18px" aria-labelledby="sign-h">
      <h2 id="sign-h">${esc(t('ctr.signTitle'))}</h2>
      <p class="small muted">${esc(t('ctr.signActivates'))}</p>
      ${signDeadline()}
      <div class="surface-sand stack" style="gap:8px">
        <strong class="small">${esc(t('ctr.terms'))}</strong>
        <ul class="ticks small">
          <li>${esc(t('ctr.t1'))}</li>${p.accessType === 'OWNER' ? '' : `<li>${esc(t('ctr.t2'))}</li>`}<li>${esc(t('ctr.t3'))}</li><li>${esc(t('ctr.t4'))}</li>
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
            <button type="button" class="btn btn-secondary" id="send-code" data-loading="${esc(t('verifyInv.sending'))}">${esc(t('ctr.sendCode'))}</button>
          </div>
          <span class="error" aria-live="polite"></span>
        </div>
        <label class="check"><input type="checkbox" id="agree"><span>${esc(t('ctr.agree'))}</span></label>
        <button class="btn btn-block" type="submit" data-loading="${esc(t('ctr.signing'))}">${esc(t('ctr.sign'))}</button>
      </form>
    </section>`;

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
        toast(t(project.nextStep === 'IN_PROGRESS' ? 'ctr.signedActive' : 'ctr.signed'), 'success');
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
