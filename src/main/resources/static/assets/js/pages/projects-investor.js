// Investor projects (loaded by projects.js). Investors get two tabs, each its own topic: projects (contract status, opening progress,
// deadline) and the ideas they proposed. Residents keep both lists on one page.
import { api } from '../api.js';
import { $, $$, esc, fmt, guard, icon, toast, withLoading, errorMessage, statusBadge, progressBar, animateBars, emptyState, skeletonCards, label, categoryLabel, stageLabel, cityLabel, placeLabel, t } from '../ui.js';
import { serverText, tn } from '../i18n.js';

const user = await guard(['RESIDENT', 'INVESTOR']);
const isInvestor = user.role === 'INVESTOR';
const app = $('#app');

const projectsSection = `
  <section class="stack" style="gap:16px" aria-labelledby="projects-h" id="tab-projects">
    <h2 id="projects-h" class="${isInvestor ? 'sr-only' : ''}" style="font-size:28px">${esc(t('projInv.inProgress'))}</h2>
    <div class="stack" id="projects" style="gap:16px">${skeletonCards(2, 220)}</div>
  </section>`;
const ideasSection = `
  <section class="stack${isInvestor ? ' hidden' : ''}" style="gap:16px;${isInvestor ? '' : 'margin-top:40px'}" aria-labelledby="ideas-h" id="tab-ideas">
    <div class="row" style="justify-content:space-between;align-items:flex-end">
      <div class="stack" style="gap:4px"><h2 id="ideas-h" style="font-size:28px">${esc(t('projInv.myIdeas'))}</h2>${isInvestor ? `<p class="small muted">${esc(t('proj.myIdeasText'))}</p>` : ''}</div>
      <a class="btn btn-secondary" href="propose.html">${icon.spark} ${esc(t('navInv.propose'))}</a>
    </div>
    <div class="grid grid-3" id="ideas">${skeletonCards(3, 150)}</div>
  </section>`;

app.innerHTML = `
  <div class="page-head">
    <div class="stack" style="gap:8px">
      <span class="eyebrow">${esc(t(isInvestor ? 'role.INVESTOR' : 'proj.founder'))}</span>
      <h1>${esc(t(isInvestor ? 'proj.titleInvestor' : 'proj.titleResident'))}</h1>
    </div>
    ${isInvestor ? `
      <div class="segmented" style="align-self:flex-end" role="tablist" aria-label="${esc(t('proj.tabs'))}">
        <button type="button" role="tab" id="t-projects" aria-controls="tab-projects" aria-selected="true" aria-pressed="true">${esc(t('projInv.inProgress'))}</button>
        <button type="button" role="tab" id="t-ideas" aria-controls="tab-ideas" aria-selected="false" aria-pressed="false">${esc(t('projInv.myIdeas'))}</button>
      </div>` : `<a class="btn btn-secondary" href="propose.html">${icon.spark} ${esc(t('navInv.propose'))}</a>`}
  </div>
  ${projectsSection}
  ${ideasSection}`;

if (isInvestor) {
  const show = (tab) => {
    const ideas = tab === 'ideas';
    $('#tab-projects').classList.toggle('hidden', ideas);
    $('#tab-ideas').classList.toggle('hidden', !ideas);
    $('#t-projects').setAttribute('aria-selected', String(!ideas));
    $('#t-projects').setAttribute('aria-pressed', String(!ideas));
    $('#t-ideas').setAttribute('aria-selected', String(ideas));
    $('#t-ideas').setAttribute('aria-pressed', String(ideas));
    history.replaceState(null, '', ideas ? '#ideas' : location.pathname + location.search);
  };
  $('#t-projects').addEventListener('click', () => show('projects'));
  $('#t-ideas').addEventListener('click', () => show('ideas'));
  if (location.hash === '#ideas') show('ideas');
}

// ---------- Project card ----------

function statusLine(p) {
  const key = `proj.next.${p.nextStep}`;
  const text = t(key);
  return text === key ? '' : text;
}

const daysUntil = (date) => Math.ceil((new Date(`${date}T23:59:59`) - new Date()) / 86400000);

// The deadline that matters now: 14 days to sign, then 180 days to open (extendable once from the contract page)
function deadlineHtml(p) {
  if (p.signingDeadline) {
    const days = daysUntil(p.signingDeadline);
    return esc(days >= 0 ? t('proj.signLeft', { d: tn(days, 'count.days') }) : t('proj.signPassed'));
  }
  if (!p.openingDeadline || p.nextStep !== 'IN_PROGRESS') return '';
  const days = daysUntil(p.openingDeadline);
  return esc(days >= 0 ? t('proj.daysLeft', { d: tn(days, 'count.days') }) : t('proj.deadlinePassed'))
    + (p.extended ? ` (${esc(t('proj.extendedTag'))})` : '')
    + (p.canExtend ? ` · <a href="contract.html?id=${p.contractId}">${esc(t('proj.canExtend'))}</a>` : '');
}

function projectCard(p) {
  const needsMe = (p.myRole === 'INVESTOR' && p.nextStep === 'INVESTOR_SIGN') || p.canSign;
  const running = p.nextStep === 'IN_PROGRESS' || p.nextStep === 'OPENED';
  const stages = p.stages.map((s, i) => `<li class="${i <= p.stageIndex ? 'done' : ''} ${i === p.stageIndex ? 'current' : ''}">${esc(stageLabel(s))}</li>`).join('');
  const updates = [...(p.updates || [])].reverse();
  const nextStages = p.stages.slice(p.stageIndex + 1);
  // The current stage first: progress inside a long stage (needs a note), so updates can stay regular
  const form = p.canPostUpdate ? `
    <form class="surface-sand stack" style="gap:10px" data-form="${p.contractId}">
      <strong class="small">${esc(t('projInv.postProgress'))}</strong>
      <div class="grid grid-2" style="gap:10px">
        <div class="field"><label for="stage-${p.contractId}">${esc(t('projInv.stageReached'))}</label>
          <select class="select" id="stage-${p.contractId}" name="stage" data-current="${esc(p.currentStage)}"><option value="${esc(p.currentStage)}">${esc(t('projInv.sameStage', { stage: stageLabel(p.currentStage) }))}</option>${nextStages.map((s) => `<option value="${esc(s)}">${esc(stageLabel(s))}</option>`).join('')}</select></div>
        <div class="field"><label for="note-${p.contractId}">${esc(t('projInv.note'))} <span class="muted">${esc(t('form.optional'))}</span></label>
          <input class="input" id="note-${p.contractId}" name="note" maxlength="1000" placeholder="${esc(t('projInv.notePlaceholder'))}"></div>
      </div>
      <button class="btn btn-sm" type="submit" style="align-self:flex-start" data-loading="${esc(t('proj.posting'))}">${esc(t('projInv.post'))}</button>
    </form>` : '';
  const other = p.myRole === 'INVESTOR' ? t('projInv.founderName', { name: p.founderName }) : t('proj.investorName', { name: p.investorName });
  const deadline = deadlineHtml(p);

  return `
    <article class="card stack project-card" style="gap:16px">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div class="stack" style="gap:4px">
          <div class="row" style="gap:8px"><span class="badge ${p.myRole === 'INVESTOR' ? 'badge-solid' : ''}">${esc(t(p.myRole === 'INVESTOR' ? 'proj.youInvestor' : 'projInv.youFounder'))}</span><span class="badge badge-sand">${esc(label('accessInv', p.accessType))}</span></div>
          <h3 class="project-title" style="font-size:20px;margin-top:6px"><a href="idea.html?id=${p.ideaId}" style="color:inherit;text-decoration:none">${esc(p.ideaTitle)}</a></h3>
          <span class="small muted">${esc(placeLabel(p.neighborhoodName, p.city))} · ${esc(t('proj.contract', { n: p.contractNumber }))}</span>
          <span class="small muted">${esc(other)}${p.accessType === 'PARTNERSHIP' ? ` · ${esc(t('projInv.profitShare'))}` : ''}</span>
        </div>
        ${running ? `<div class="stack" style="align-items:center;gap:6px"><div class="ring" style="--p:${p.progressPercent}"><div><strong>${p.progressPercent}%</strong><span>${esc(t('proj.done'))}</span></div></div><span class="small" style="font-weight:600">${esc(stageLabel(p.currentStage))}</span></div>` : ''}
      </div>
      <div class="project-status ${needsMe ? 'is-action' : ''}">
        <span>${needsMe ? icon.info : icon.check} <strong>${esc(statusLine(p))}</strong>${deadline ? ` · <span class="muted">${deadline}</span>` : ''}</span>
        <a class="btn btn-sm ${needsMe ? '' : 'btn-secondary'}" href="contract.html?id=${p.contractId}">${esc(t(needsMe ? 'proj.continue' : 'proj.viewContract'))}</a>
      </div>
      ${running ? `
        ${progressBar(p.progressPercent, t('proj.execProgress'))}
        <ol class="stages" aria-label="${esc(t('proj.stages'))}">${stages}</ol>
        ${updates.length ? `<ul class="list">${updates.map((u) => `<li><span><strong>${esc(stageLabel(u.stage))}</strong>${u.note ? ` · ${esc(u.note)}` : ''}</span><span class="small muted">${fmt.date(u.createdAt)}</span></li>`).join('')}</ul>` : `<span class="small muted">${esc(t('projInv.noUpdates'))}</span>`}
        ${form}` : ''}
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
      <div class="actions"><a class="btn btn-secondary btn-sm" href="idea.html?id=${i.id}">${esc(t('commonInv.viewDetails'))}</a></div>
    </article>`;
}

async function load() {
  try {
    const [projects, ideas] = await Promise.all([api.get('projects/mine'), api.get('discover/my-ideas')]);
    $('#projects').innerHTML = projects.length
      ? projects.map(projectCard).join('')
      : emptyState(t('projInv.noProjects'), t(isInvestor ? 'projInv.noProjectsInvestor' : 'proj.noProjectsResident'),
        isInvestor ? `<a class="btn btn-sm" href="investor.html">${esc(t('projInv.browse'))}</a>` : '');
    $('#ideas').innerHTML = ideas.length
      ? ideas.map(ideaCard).join('')
      : `<div style="grid-column:1/-1">${emptyState(t('projInv.noIdeas'), t('proj.noIdeasText'), `<a class="btn btn-sm" href="propose.html">${esc(t('navInv.propose'))}</a>`)}</div>`;
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
    // Staying on the same stage needs a note saying what changed
    if (form.stage.value === form.stage.dataset.current && !form.note.value.trim()) {
      toast(t('projInv.noteNeeded'), 'error');
      form.note.focus();
      return;
    }
    withLoading(form.querySelector('[type="submit"]'), async () => {
      try {
        await api.post(`projects/stage/${contractId}`, { stage: form.stage.value, note: form.note.value.trim() || null });
        toast(t('projInv.posted'), 'success');
        await load();
      } catch (err) {
        toast(errorMessage(err), 'error');
      }
    });
  }));
}

load();
