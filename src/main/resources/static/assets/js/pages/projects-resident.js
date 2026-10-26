// Resident and admin view (loaded by projects.js).
// My ideas & projects: the ideas I founded (with their status) and adopted projects with execution progress.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, errorMessage, statusBadge, progressBar, animateBars, emptyState, skeletonCards, label, categoryLabel, stageLabel, modal, isolate, t } from '../ui.js';
import { wireTabs } from '../components.js';
import { serverText, tn } from '../i18n.js';

const user = await guard(['RESIDENT', 'INVESTOR']);
const isInvestor = user.role === 'INVESTOR';
const app = $('#app');

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t(isInvestor ? 'role.INVESTOR' : 'proj.founder'))}</span>
      <h1>${esc(t(isInvestor ? 'proj.titleInvestor' : 'proj.titleResident'))}</h1>
    </div>
    <a class="btn btn-secondary" href="propose.html">${icon.spark} ${esc(t('nav.propose'))}</a>
  </div>
  <div class="tabs" role="tablist" aria-label="${esc(t('proj.titleResident'))}">
    <button type="button" role="tab" id="tab-projects" data-tab="projects" aria-controls="panel-projects">${esc(t('proj.inProgress'))} <span class="tab-count" id="n-projects"></span></button>
    <button type="button" role="tab" id="tab-ideas" data-tab="ideas" aria-controls="panel-ideas">${esc(t('proj.myIdeas'))} <span class="tab-count" id="n-ideas"></span></button>
  </div>
  <section role="tabpanel" id="panel-projects" aria-labelledby="tab-projects" class="stack" style="gap:16px">
    <div class="stack" id="projects" style="gap:16px">${skeletonCards(2, 220)}</div>
  </section>
  <section role="tabpanel" id="panel-ideas" aria-labelledby="tab-ideas" class="stack" style="gap:24px" hidden>
    <div id="ideas">${skeletonCards(3, 150)}</div>
  </section>`;
let projectsById = new Map();

// The idea owner's side of the contract: what happens next, and the way to sign when it's their turn
function contractStatus(p) {
  if (p.myRole === 'INVESTOR') return '';
  const needsMe = !!p.canSign;
  const key = `proj.owner.${p.nextStep}`;
  const text = t(key) === key ? '' : t(key);
  const daysUntil = (date) => Math.ceil((new Date(`${date}T23:59:59`) - new Date()) / 86400000);
  let deadline = '';
  if (p.signingDeadline) {
    const days = daysUntil(p.signingDeadline);
    deadline = days >= 0 ? t('proj.signLeft', { d: tn(days, 'count.days') }) : t('proj.signPassed');
  } else if (p.nextStep === 'IN_PROGRESS' && p.openingDeadline) {
    const days = daysUntil(p.openingDeadline);
    deadline = (days >= 0 ? t('proj.daysLeft', { d: tn(days, 'count.days') }) : t('proj.deadlinePassed'))
      + (p.extended ? ` (${t('proj.extendedTag')})` : '');
  }
  return `<div class="project-status ${needsMe ? 'is-action' : ''}">
      <span>${needsMe ? icon.info : icon.check} <strong>${esc(text)}</strong>${deadline ? ` · <span class="muted">${esc(deadline)}</span>` : ''}</span>
      <a class="btn btn-sm ${needsMe ? '' : 'btn-secondary'}" href="contract.html?id=${p.contractId}">${esc(t(needsMe ? 'proj.owner.sign' : 'proj.owner.view'))}</a>
    </div>`;
}

function projectCard(p) {
  // Execution progress starts once the contract is active
  const running = p.nextStep === 'IN_PROGRESS' || p.nextStep === 'OPENED' || p.contractStatus === 'ACTIVE';
  const stages = p.stages.map((s, i) => `<li class="${i <= p.stageIndex ? 'done' : ''} ${i === p.stageIndex ? 'current' : ''}">${esc(stageLabel(s))}</li>`).join('');
  const updates = [...(p.updates || [])].reverse();
  const form = p.canPostUpdate && p.stageIndex < p.stages.length - 1
    ? `<button type="button" class="btn btn-sm" data-update="${p.contractId}">${esc(t('proj.postProgress'))}</button>`
    : (p.myRole === 'INVESTOR' && p.stageIndex < p.stages.length - 1 && p.nextUpdateAllowedAt
      ? `<span class="small muted">${esc(t('proj.nextUpdate', { date: fmt.date(p.nextUpdateAllowedAt) }))}</span>` : '');

  const other = p.myRole === 'INVESTOR' ? t('proj.founderName', { name: p.founderName }) : t('proj.investorName', { name: p.investorName });
  return `
    <article class="card stack" style="gap:16px">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:4px">
          <div class="row" style="gap:8px"><span class="badge ${p.myRole === 'INVESTOR' ? 'badge-solid' : ''}">${esc(t(p.myRole === 'INVESTOR' ? 'proj.youInvestor' : 'proj.youFounder'))}</span><span class="badge badge-sand">${esc(label('access', p.accessType))}</span></div>
          <h3 class="project-title" style="font-size:20px;margin-top:6px"><a href="idea.html?id=${p.ideaId}" style="color:inherit;text-decoration:none">${esc(p.ideaTitle)}</a></h3>
          <span class="small muted">${esc(isolate(`${p.neighborhoodName}, ${p.city}`))}${p.activatedAt ? ` · ${esc(t('proj.since', { date: fmt.date(p.activatedAt) }))}` : ''}</span>
          <span class="small muted num">${esc(t('proj.contract', { n: p.contractNumber }))}</span>
          <span class="small muted">${p.accessType === 'OWNER' ? esc(t('proj.ownerLaunch')) : esc(other)}${p.accessType === 'PARTNERSHIP' ? ` · ${esc(t('proj.profitShare'))}` : ''}</span>
        </div>
        ${running ? `<div class="stack" style="align-items:center;gap:6px"><div class="ring" style="--p:${p.progressPercent}"><div><strong>${p.progressPercent}%</strong><span>${esc(t('proj.done'))}</span></div></div><span class="small" style="font-weight:600">${esc(stageLabel(p.currentStage))}</span></div>` : ''}
      </div>
      ${contractStatus(p)}
      ${running ? `
      ${progressBar(p.progressPercent, t('proj.execProgress'))}
      <ol class="stages" aria-label="${esc(t('proj.stages'))}">${stages}</ol>
      ${updates.length ? `<ul class="list">${updates.map((u) => `<li><span><strong>${esc(stageLabel(u.stage))}</strong>${u.note ? ` · ${esc(u.note)}` : ''}</span><span class="small muted">${fmt.date(u.createdAt)}</span></li>`).join('')}</ul>` : `<span class="small muted">${esc(t('proj.noUpdates'))}</span>`}` : `<span class="small muted">${esc(t('proj.owner.notStarted'))}</span>`}
      <div class="row" style="justify-content:space-between">
        <a class="btn btn-ghost btn-sm" href="agreement.html?contract=${p.contractId}" title="${esc(t('proj.contract', { n: p.contractNumber }))}">${esc(t('agr.view'))}</a>
        ${form}
      </div>
    </article>`;
}

function ideaCard(i) {
  const seesVotes = i.votesCount !== null && i.votesCount !== undefined;
  const pending = i.status === 'DRAFT' || i.status === 'REJECTED';
  const action = pending
    ? `<a class="btn btn-sm" href="propose.html?draft=${i.id}">${esc(i.status === 'DRAFT' ? t('idea.continueReview') : t('idea.seeWhy'))}</a>`
    : `<a class="btn btn-secondary btn-sm" href="idea.html?id=${i.id}">${esc(t('common.viewDetails'))}</a>`;
  return `
    <article class="idea-card">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <span class="title" dir="auto">${esc(i.title)}</span>${statusBadge(i.status)}
      </div>
      <span class="meta"><span>${esc(categoryLabel(i.category))}</span><span>${esc(i.neighborhoodName)}</span></span>
      ${i.status === 'REJECTED' ? `<span class="small clamp-2" style="color:var(--red-700)">${esc(serverText(i.rejectionReason || ''))}</span>` : ''}
      ${i.status === 'DRAFT' ? `<span class="small muted">${esc(t('idea.draftShort'))}</span>` : ''}
      ${seesVotes && ['PUBLISHED', 'COMPLETED'].includes(i.status) ? `<div class="stack" style="gap:6px"><div class="row small" style="justify-content:space-between"><strong>${i.votePercent}%</strong><span class="muted num">${fmt.num(i.votesCount)} / ${fmt.num(i.membershipThreshold)} ${esc(t('common.votes'))}</span></div>${progressBar(i.votePercent, t('common.voteTarget'))}</div>` : ''}
      <div class="actions">${action}</div>
    </article>`;
}

async function load() {
  try {
    const [projects, ideas] = await Promise.all([api.get('projects/mine'), api.get('discover/my-ideas')]);
    $('#projects').innerHTML = projects.length
      ? projects.map(projectCard).join('')
      : emptyState(t('proj.noProjects'), t(isInvestor ? 'proj.noProjectsInvestor' : 'proj.noProjectsResident'),
        isInvestor ? `<a class="btn btn-sm" href="investor.html">${esc(t('proj.browse'))}</a>` : '');
    projectsById = new Map(projects.map((p) => [p.contractId, p]));
    // Ideas waiting for the author's decision come first, in their own group
    const pending = ideas.filter((i) => i.status === 'DRAFT' || i.status === 'REJECTED');
    const live = ideas.filter((i) => !pending.includes(i));
    $('#n-projects').textContent = projects.length ? `(${fmt.num(projects.length)})` : '';
    $('#n-ideas').textContent = ideas.length ? `(${fmt.num(ideas.length)})` : '';
    $('#ideas').innerHTML = ideas.length
      ? `${pending.length ? `<h2 class="small-h">${esc(t('proj.needsYou'))}</h2><div class="grid grid-3" style="margin:12px 0 28px">${pending.map(ideaCard).join('')}</div>` : ''}
         ${live.length ? `<h2 class="small-h">${esc(t('proj.published'))}</h2><div class="grid grid-3" style="margin-top:12px">${live.map(ideaCard).join('')}</div>` : ''}`
      : emptyState(t('proj.noIdeas'), t('proj.noIdeasText'), `<a class="btn btn-sm" href="propose.html">${esc(t('nav.propose'))}</a>`);
    // A resident without projects lands on their ideas
    if (!projects.length && ideas.length && !location.hash) $('#tab-ideas').click();
    animateBars(app);
    wireForms();
  } catch (err) {
    $('#projects').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    $('#ideas').innerHTML = '';
  }
}

// Posting progress: a sheet (a dialog on desktop) with the next stage, a note, and what happens
function wireForms() {
  $$('[data-update]').forEach((b) => b.addEventListener('click', async () => {
    const p = projectsById.get(Number(b.dataset.update));
    const next = p.stages.slice(p.stageIndex + 1);
    const ok = await modal({
      title: t('proj.postProgress'),
      body: `<p class="muted small">${esc(t('proj.updateIntro', { stage: stageLabel(p.currentStage) }))}</p>
        <div class="field"><label for="up-stage">${esc(t('proj.stageReached'))}</label>
          <select class="select" id="up-stage">${next.map((s) => `<option value="${esc(s)}">${esc(stageLabel(s))}</option>`).join('')}</select></div>
        <div class="field"><label for="up-note">${esc(t('proj.note'))} <span class="muted">${esc(t('form.optional'))}</span></label>
          <textarea class="textarea" id="up-note" maxlength="1000" rows="3" placeholder="${esc(t('proj.notePlaceholder'))}"></textarea></div>
        <p class="small muted">${esc(t('proj.updateWho'))}</p>`,
      confirmText: t('proj.post'),
      onConfirm: (box) => api.post(`projects/stage/${p.contractId}`, {
        stage: box.querySelector('#up-stage').value,
        note: box.querySelector('#up-note').value.trim() || null,
      }),
    });
    if (ok) {
      toast(t('proj.posted'), 'success');
      await load();
    }
  }));
}

wireTabs($('.tabs'));
load();
