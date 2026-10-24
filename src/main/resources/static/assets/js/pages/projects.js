// My ideas & projects: the ideas I founded (with their status) and adopted projects with execution progress.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, withLoading, errorMessage, statusBadge, progressBar, animateBars, emptyState, skeletonCards, label, categoryLabel, stageLabel, t } from '../ui.js';
import { serverText } from '../i18n.js';

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
  <section class="stack" style="gap:16px;margin-bottom:40px" aria-labelledby="projects-h">
    <h2 id="projects-h" style="font-size:28px">${esc(t('proj.inProgress'))}</h2>
    <div class="stack" id="projects" style="gap:16px">${skeletonCards(2, 220)}</div>
  </section>
  <section class="stack" style="gap:16px" aria-labelledby="ideas-h">
    <h2 id="ideas-h" style="font-size:28px">${esc(t('proj.myIdeas'))}</h2>
    <div class="grid grid-3" id="ideas">${skeletonCards(3, 150)}</div>
  </section>`;

function projectCard(p) {
  const stages = p.stages.map((s, i) => `<li class="${i <= p.stageIndex ? 'done' : ''} ${i === p.stageIndex ? 'current' : ''}">${esc(stageLabel(s))}</li>`).join('');
  const updates = [...(p.updates || [])].reverse();
  const nextStages = p.stages.slice(p.stageIndex + 1);
  const form = p.canPostUpdate ? `
    <form class="surface-sand stack" style="gap:10px" data-form="${p.contractId}">
      <strong class="small">${esc(t('proj.postProgress'))}</strong>
      <div class="grid grid-2" style="gap:10px">
        <div class="field"><label for="stage-${p.contractId}">${esc(t('proj.stageReached'))}</label>
          <select class="select" id="stage-${p.contractId}" name="stage">${nextStages.map((s) => `<option value="${esc(s)}">${esc(stageLabel(s))}</option>`).join('')}</select></div>
        <div class="field"><label for="note-${p.contractId}">${esc(t('proj.note'))} <span class="muted">${esc(t('form.optional'))}</span></label>
          <input class="input" id="note-${p.contractId}" name="note" maxlength="1000" placeholder="${esc(t('proj.notePlaceholder'))}"></div>
      </div>
      <button class="btn btn-sm" type="submit" style="align-self:flex-start" data-loading="${esc(t('proj.posting'))}">${esc(t('proj.post'))}</button>
    </form>`
    : (p.myRole === 'INVESTOR' && p.stageIndex < p.stages.length - 1 && p.nextUpdateAllowedAt
      ? `<span class="small muted">${esc(t('proj.nextUpdate', { date: fmt.date(p.nextUpdateAllowedAt) }))}</span>` : '');

  const other = p.myRole === 'INVESTOR' ? t('proj.founderName', { name: p.founderName }) : t('proj.investorName', { name: p.investorName });
  return `
    <article class="card stack" style="gap:16px">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:4px">
          <div class="row" style="gap:8px"><span class="badge ${p.myRole === 'INVESTOR' ? 'badge-solid' : ''}">${esc(t(p.myRole === 'INVESTOR' ? 'proj.youInvestor' : 'proj.youFounder'))}</span><span class="badge badge-sand">${esc(label('access', p.accessType))}</span></div>
          <h3 class="project-title" style="font-size:20px;margin-top:6px"><a href="idea.html?id=${p.ideaId}" style="color:inherit;text-decoration:none">${esc(p.ideaTitle)}</a></h3>
          <span class="small muted">${esc(p.neighborhoodName)}, ${esc(p.city)} · ${esc(t('proj.contract', { n: p.contractNumber }))} · ${esc(t('proj.since', { date: fmt.date(p.activatedAt) }))}</span>
          <span class="small muted">${esc(other)}${p.accessType === 'PARTNERSHIP' ? ` · ${esc(t('proj.profitShare'))}` : ''}</span>
        </div>
        <div class="stack" style="align-items:center;gap:6px"><div class="ring" style="--p:${p.progressPercent}"><div><strong>${p.progressPercent}%</strong><span>${esc(t('proj.done'))}</span></div></div><span class="small" style="font-weight:600">${esc(stageLabel(p.currentStage))}</span></div>
      </div>
      ${progressBar(p.progressPercent, t('proj.execProgress'))}
      <ol class="stages" aria-label="${esc(t('proj.stages'))}">${stages}</ol>
      ${updates.length ? `<ul class="list">${updates.map((u) => `<li><span><strong>${esc(stageLabel(u.stage))}</strong>${u.note ? ` · ${esc(u.note)}` : ''}</span><span class="small muted">${fmt.date(u.createdAt)}</span></li>`).join('')}</ul>` : `<span class="small muted">${esc(t('proj.noUpdates'))}</span>`}
      ${form}
    </article>`;
}

function ideaCard(i) {
  const seesVotes = i.votesCount !== null && i.votesCount !== undefined;
  return `
    <article class="idea-card">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <span class="title">${esc(i.title)}</span>${statusBadge(i.status)}
      </div>
      <span class="meta"><span>${esc(categoryLabel(i.category))}</span><span>${esc(i.neighborhoodName)}</span></span>
      ${i.status === 'REJECTED' ? `<span class="small" style="color:var(--red-700)">${esc(serverText(i.rejectionReason || ''))}</span>` : ''}
      ${seesVotes && ['PUBLISHED', 'COMPLETED'].includes(i.status) ? `<div class="stack" style="gap:6px"><div class="row small" style="justify-content:space-between"><strong>${i.votePercent}%</strong><span class="muted num">${fmt.num(i.votesCount)} / ${fmt.num(i.membershipThreshold)} ${esc(t('common.votes'))}</span></div>${progressBar(i.votePercent, t('common.voteTarget'))}</div>` : ''}
      <div class="actions"><a class="btn btn-secondary btn-sm" href="idea.html?id=${i.id}">${esc(t('common.viewDetails'))}</a></div>
    </article>`;
}

async function load() {
  try {
    const [projects, ideas] = await Promise.all([api.get('projects/mine'), api.get('discover/my-ideas')]);
    $('#projects').innerHTML = projects.length
      ? projects.map(projectCard).join('')
      : emptyState(t('proj.noProjects'), t(isInvestor ? 'proj.noProjectsInvestor' : 'proj.noProjectsResident'),
        isInvestor ? `<a class="btn btn-sm" href="investor.html">${esc(t('proj.browse'))}</a>` : '');
    $('#ideas').innerHTML = ideas.length
      ? ideas.map(ideaCard).join('')
      : `<div style="grid-column:1/-1">${emptyState(t('proj.noIdeas'), t('proj.noIdeasText'), `<a class="btn btn-sm" href="propose.html">${esc(t('nav.propose'))}</a>`)}</div>`;
    animateBars(app);
    wireForms();
  } catch (err) {
    $('#projects').innerHTML = `<div class="notice notice-error">${icon.x}<span>${esc(errorMessage(err))}</span></div>`;
    $('#ideas').innerHTML = '';
  }
}

function wireForms() {
  $$('[data-form]').forEach((form) => form.addEventListener('submit', (e) => {
    e.preventDefault();
    const contractId = form.dataset.form;
    withLoading(form.querySelector('[type="submit"]'), async () => {
      try {
        await api.post(`projects/stage/${contractId}`, { stage: form.stage.value, note: form.note.value.trim() || null });
        toast(t('proj.posted'), 'success');
        await load();
      } catch (err) {
        toast(errorMessage(err), 'error');
      }
    });
  }));
}

load();
