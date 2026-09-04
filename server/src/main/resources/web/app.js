"use strict";

// ---- Shared state pulled from the server (the source of truth) ----
const store = {
  state: null,      // PlannerState { snapshot, goals, settings }
  config: null,     // CompensationConfig
  calc: { CURRENT: null, IDEAL: null }, // CalculatorResponse per structure
  gap: null,        // StructureGap
};

const NODE_W = 220;
const NODE_H = 140;
const NODE_H_COUPLE = 160;

const view = {
  map: { box: null, selected: null },
  plan: { box: null, selected: null },
};

// ---------- API helpers ----------
async function api(path, method = "GET", body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  if (!res.ok) {
    let msg = res.statusText;
    try { msg = (await res.json()).error || msg; } catch (_) {}
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  return res.json();
}

function setSync(text, cls) {
  const el = document.getElementById("sync-status");
  el.textContent = text;
  el.className = "sync-status" + (cls ? " " + cls : "");
}

// ---------- Formatting ----------
const money = (n) => "$" + (n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pv = (n) => Math.round(n || 0).toLocaleString();
const pct = (f) => Math.round((f || 0) * 100) + "%";

// ---------- Data lookups ----------
function memberOf(node) {
  return store.state.snapshot.members.find((m) => m.id === node.memberId);
}
function displayName(node) {
  const m = memberOf(node);
  if (!m) return "Unknown";
  if (m.isCouple && m.partnerName) return `${m.name} & ${m.partnerName}`;
  return m.name || "Unnamed";
}
function nodesOfKind(kind) {
  return store.state.snapshot.nodes.filter((n) => n.kind === kind);
}
function nodeHeight(node) {
  const m = memberOf(node);
  return m && m.isCouple ? NODE_H_COUPLE : NODE_H;
}
function isYou(node) {
  const m = memberOf(node);
  return m && m.isYou;
}

// ---------- Load everything ----------
async function loadAll() {
  setSync("Loading…");
  store.config = await api("/api/config");
  await loadState(await api("/api/state"));
  populateGoalsForm();
  populateProfile();
  setSync("Synced with server", "ok");
}

async function loadState(state) {
  store.state = state;
  await loadDerived();
  renderActiveTab();
}

async function loadDerived() {
  const [cur, ideal] = await Promise.all([
    api("/api/calculator?kind=CURRENT"),
    api("/api/calculator?kind=IDEAL"),
  ]);
  store.calc.CURRENT = cur;
  store.calc.IDEAL = ideal;
  store.gap = await api("/api/gap").catch(() => null);
}

// Mutations: send to server, adopt returned state, re-render.
async function mutate(promise) {
  setSync("Saving…", "saving");
  try {
    const result = await promise;
    const newState = result && result.state ? result.state : result;
    await loadState(newState);
    setSync("Synced with server", "ok");
    return result;
  } catch (e) {
    setSync("Error: " + e.message, "error");
    throw e;
  }
}

// ---------- SVG org rendering ----------
function elbowPath(px, py, cx, cy) {
  const midY = (py + cy) / 2;
  return `M ${px} ${py} L ${px} ${midY} L ${cx} ${midY} L ${cx} ${cy}`;
}

function renderCanvas(kind) {
  const which = kind === "IDEAL" ? "plan" : "map";
  const svg = document.getElementById(which + "-canvas");
  const nodes = nodesOfKind(kind);
  svg.innerHTML = "";

  if (!view[which].box) view[which].box = fitBox(nodes, svg);
  const box = view[which].box;
  svg.setAttribute("viewBox", `${box.x} ${box.y} ${box.w} ${box.h}`);

  const ns = "http://www.w3.org/2000/svg";
  const byId = Object.fromEntries(nodes.map((n) => [n.id, n]));

  // Edges first (so they sit behind cards)
  nodes.forEach((n) => {
    if (!n.parentId || !byId[n.parentId]) return;
    const p = byId[n.parentId];
    const px = p.canvasX + NODE_W / 2;
    const py = p.canvasY + nodeHeight(p);
    const cx = n.canvasX + NODE_W / 2;
    const cy = n.canvasY;
    const path = document.createElementNS(ns, "path");
    path.setAttribute("d", elbowPath(px, py, cx, cy));
    path.setAttribute("class", "edge");
    svg.appendChild(path);
  });

  // Node cards
  nodes.forEach((n) => {
    const h = nodeHeight(n);
    const g = document.createElementNS(ns, "g");
    g.style.cursor = "pointer";

    const rect = document.createElementNS(ns, "rect");
    rect.setAttribute("x", n.canvasX);
    rect.setAttribute("y", n.canvasY);
    rect.setAttribute("width", NODE_W);
    rect.setAttribute("height", h);
    rect.setAttribute("rx", 12);
    let cls = "node-rect";
    if (isYou(n)) cls += " you";
    if (view[which].selected === n.id) cls += " selected";
    rect.setAttribute("class", cls);
    g.appendChild(rect);

    const title = document.createElementNS(ns, "text");
    title.setAttribute("x", n.canvasX + 16);
    title.setAttribute("y", n.canvasY + 30);
    title.setAttribute("class", "node-title");
    title.textContent = displayName(n);
    g.appendChild(title);

    const sub = document.createElementNS(ns, "text");
    sub.setAttribute("x", n.canvasX + 16);
    sub.setAttribute("y", n.canvasY + 54);
    sub.setAttribute("class", "node-sub");
    sub.textContent = `${pv(n.personalPv)} PV · ${pv(n.personalBv)} BV`;
    g.appendChild(sub);

    // per-node group payout badge
    const payout = store.calc[kind] && store.calc[kind].perNode[n.id];
    if (payout) {
      const badge = document.createElementNS(ns, "text");
      badge.setAttribute("x", n.canvasX + 16);
      badge.setAttribute("y", n.canvasY + 78);
      badge.setAttribute("class", "node-sub");
      badge.textContent = `G ${pv(payout.group.pv)} PV · ${pct(payout.performancePercent)}`;
      g.appendChild(badge);
    }

    g.addEventListener("click", (ev) => {
      ev.stopPropagation();
      view[which].selected = n.id;
      renderCanvas(kind);
      renderInspector(which, kind, n.id);
    });
    svg.appendChild(g);
  });

  attachPanZoom(svg, which, kind);
  renderSummary(kind);
}

function fitBox(nodes, svg) {
  if (!nodes.length) return { x: 0, y: 0, w: 1000, h: 700 };
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  nodes.forEach((n) => {
    minX = Math.min(minX, n.canvasX);
    minY = Math.min(minY, n.canvasY);
    maxX = Math.max(maxX, n.canvasX + NODE_W);
    maxY = Math.max(maxY, n.canvasY + nodeHeight(n));
  });
  const pad = 60;
  minX -= pad; minY -= pad; maxX += pad; maxY += pad;
  let w = maxX - minX;
  let h = maxY - minY;
  // Keep aspect ratio close to the SVG element so nothing is squashed.
  const rect = svg.getBoundingClientRect();
  const aspect = rect.width && rect.height ? rect.width / rect.height : 1.4;
  if (w / h < aspect) { const nw = h * aspect; minX -= (nw - w) / 2; w = nw; }
  else { const nh = w / aspect; minY -= (nh - h) / 2; h = nh; }
  return { x: minX, y: minY, w, h };
}

function attachPanZoom(svg, which, kind) {
  if (svg.dataset.wired) return;
  svg.dataset.wired = "1";
  let dragging = false, lastX = 0, lastY = 0;
  svg.addEventListener("pointerdown", (e) => {
    dragging = true; lastX = e.clientX; lastY = e.clientY; svg.setPointerCapture(e.pointerId);
  });
  svg.addEventListener("pointermove", (e) => {
    if (!dragging) return;
    const box = view[which].box;
    const rect = svg.getBoundingClientRect();
    const scale = box.w / rect.width;
    box.x -= (e.clientX - lastX) * scale;
    box.y -= (e.clientY - lastY) * scale;
    lastX = e.clientX; lastY = e.clientY;
    svg.setAttribute("viewBox", `${box.x} ${box.y} ${box.w} ${box.h}`);
  });
  const stop = () => { dragging = false; };
  svg.addEventListener("pointerup", stop);
  svg.addEventListener("pointerleave", stop);
  svg.addEventListener("wheel", (e) => {
    e.preventDefault();
    zoom(which, e.deltaY < 0 ? 0.9 : 1.1);
  }, { passive: false });
}

function zoom(which, factor) {
  const box = view[which].box;
  if (!box) return;
  const cx = box.x + box.w / 2;
  const cy = box.y + box.h / 2;
  box.w *= factor; box.h *= factor;
  box.x = cx - box.w / 2; box.y = cy - box.h / 2;
  const svg = document.getElementById(which + "-canvas");
  svg.setAttribute("viewBox", `${box.x} ${box.y} ${box.w} ${box.h}`);
}

function renderSummary(kind) {
  const which = kind === "IDEAL" ? "plan" : "map";
  const root = store.calc[kind] && store.calc[kind].root;
  const el = document.getElementById(which + "-summary");
  if (!root) { el.textContent = "No structure yet."; return; }
  const rank = root.currentRank ? root.currentRank.title : "";
  el.textContent = `${money(root.estimatedMonthly)} · ${rank} · ${pct(root.performancePercent)} · G ${pv(root.group.pv)} PV · ${root.maxPercentLegs}×25%`;
}

// ---------- Node inspector ----------
function renderInspector(which, kind, nodeId) {
  const node = store.state.snapshot.nodes.find((n) => n.id === nodeId);
  const inspector = document.getElementById(which + "-inspector");
  if (!node) { inspector.classList.add("hidden"); return; }
  const m = memberOf(node) || {};
  const you = isYou(node);

  const parentOptions = nodesOfKind(kind)
    .filter((n) => n.id !== node.id)
    .map((n) => `<option value="${n.id}" ${node.parentId === n.id ? "selected" : ""}>${escapeHtml(displayName(n))}</option>`) 
    .join("");

  inspector.innerHTML = `
    <h3>${escapeHtml(displayName(node))}</h3>
    <label>Name<input id="insp-name" value="${escapeHtml(m.name || "")}" /></label>
    <label class="toggle"><input type="checkbox" id="insp-couple" ${m.isCouple ? "checked" : ""}/> Couple</label>
    <label id="insp-partner-wrap" style="${m.isCouple ? "" : "display:none"}">Partner name<input id="insp-partner" value="${escapeHtml(m.partnerName || "")}" /></label>
    <label>Personal PV<input id="insp-pv" type="number" min="0" step="10" value="${node.personalPv}" /></label>
    <label>Notes<input id="insp-notes" value="${escapeHtml(m.notes || "")}" /></label>
    ${you ? "" : `<label>Upline<select id="insp-parent"><option value="">— none —</option>${parentOptions}</select></label>`}
    <div class="row-btns">
      <button class="btn primary" id="insp-save">Save</button>
      <button class="btn" id="insp-add">Add downline</button>
      ${you ? "" : `<button class="btn danger" id="insp-del">Delete</button>`}
      <button class="btn ghost" id="insp-close">Close</button>
    </div>
  `;
  inspector.classList.remove("hidden");

  document.getElementById("insp-couple").addEventListener("change", (e) => {
    document.getElementById("insp-partner-wrap").style.display = e.target.checked ? "" : "none";
  });
  document.getElementById("insp-close").addEventListener("click", () => {
    inspector.classList.add("hidden");
    view[which].selected = null;
    renderCanvas(kind);
  });
  document.getElementById("insp-save").addEventListener("click", async () => {
    const body = {
      name: val("insp-name"),
      isCouple: document.getElementById("insp-couple").checked,
      partnerName: val("insp-partner") || "",
      notes: val("insp-notes"),
      personalPv: numVal("insp-pv"),
    };
    await mutate(api(`/api/nodes/${node.id}`, "PUT", body));
    const parentSel = document.getElementById("insp-parent");
    if (parentSel && parentSel.value !== (node.parentId || "")) {
      await mutate(api(`/api/nodes/${node.id}/reparent`, "POST", { parentId: parentSel.value || null }))
        .catch((e) => alert(e.message));
    }
    renderInspector(which, kind, node.id);
  });
  document.getElementById("insp-add").addEventListener("click", async () => {
    const res = await mutate(api("/api/nodes", "POST", { kind, parentId: node.id, name: "New partner", personalPv: 100 }));
    view[which].selected = res.nodeId;
    renderCanvas(kind);
    renderInspector(which, kind, res.nodeId);
  });
  const del = document.getElementById("insp-del");
  if (del) del.addEventListener("click", async () => {
    if (!confirm("Delete this person and everyone below them?")) return;
    await mutate(api(`/api/nodes/${node.id}`, "DELETE"));
    inspector.classList.add("hidden");
    view[which].selected = null;
    renderCanvas(kind);
  });
}

// ---------- Calculator ----------
function renderCalculator() {
  const root = store.calc.CURRENT && store.calc.CURRENT.root;
  const goal = store.state.goals.monthlyIncomeTarget;
  const amt = document.getElementById("estimate-amount");
  const sub = document.getElementById("estimate-sub");
  const prog = document.getElementById("estimate-progress");
  const goalEl = document.getElementById("estimate-goal");
  const cards = document.getElementById("calc-cards");
  if (!root) {
    amt.textContent = "$0.00"; sub.textContent = "No current structure"; cards.innerHTML = ""; return;
  }
  amt.textContent = money(root.estimatedMonthly);
  sub.textContent = `${root.currentRank.title} · ${pct(root.performancePercent)} · Group ${pv(root.group.pv)} PV`;
  const ratio = goal > 0 ? Math.min(1, root.estimatedMonthly / goal) : 1;
  prog.style.width = (ratio * 100) + "%";
  goalEl.textContent = `Progress toward ${money(goal)} income goal`;

  const c = [
    ["Group PV", pv(root.group.pv), `${pv(root.passUp.pv)} pass-up`],
    ["Ruby / side", pv(root.ruby.pv), `${pv(root.totalDownline.pv)} total downline PV`],
    ["Performance", money(root.performanceBonus), pct(root.performancePercent)],
    ["Differential", money(root.differential), "in the performance total"],
    ["Leadership", money(root.leadershipBonus), `pass-up ${money(root.leadershipPassedToSponsor)}`],
    ["Depth", money(root.depthBonus), `${root.maxPercentLegs} legs at 25%`],
    ["Ruby bonus", money(root.rubyBonus), "≥15k Ruby PV"],
    ["Plus / Elite", money(root.corePlus.performancePlusAmount), pct(root.corePlus.performancePlusPercent)],
    ["Retail", money(root.retailMargin), pct(store.state.settings.retailMarginPercent)],
  ];
  cards.innerHTML = c.map(([k, v, d]) => `<div class="stat-card"><div class="k">${k}</div><div class="v">${v}</div><div class="d">${d}</div></div>`).join("");
}

// ---------- Goals / settings ----------
function populateGoalsForm() {
  const g = store.state.goals;
  const s = store.state.settings;
  document.getElementById("goal-income").value = g.monthlyIncomeTarget;
  const rankSel = document.getElementById("goal-rank");
  rankSel.innerHTML = store.config.ranks
    .map((r) => `<option value="${r.id}" ${g.targetRankId === r.id ? "selected" : ""}>${escapeHtml(r.title)}</option>`) 
    .join("");
  document.getElementById("set-bvpv").value = s.bvPerPv;
  document.getElementById("set-retail").value = s.retailMarginPercent.toFixed(2);
  document.getElementById("set-rule413").checked = s.meetsRule413;
  document.getElementById("set-leadership").checked = s.includeLeadershipBonus;
  document.getElementById("set-depth").checked = s.includeDepthBonus;
  document.getElementById("set-ruby").checked = s.includeRubyBonus;
  document.getElementById("set-plus").checked = s.includePerformancePlus;
  document.getElementById("set-retail-on").checked = s.includeRetailMargin;
}

function populateProfile() {
  document.getElementById("profile-title").textContent = store.config.profileTitle;
  document.getElementById("profile-note").textContent = store.config.sourceNote;
  document.getElementById("assumptions").innerHTML =
    (store.config.assumptions || []).map((a) => `<div>${escapeHtml(a)}</div>`).join("");
}

async function saveGoals() {
  const goals = Object.assign({}, store.state.goals, {
    monthlyIncomeTarget: numVal("goal-income"),
    targetRankId: document.getElementById("goal-rank").value,
    onboardingComplete: true,
    disclaimerAccepted: true,
  });
  const settings = Object.assign({}, store.state.settings, {
    bvPerPv: numVal("set-bvpv"),
    retailMarginPercent: parseFloat(document.getElementById("set-retail").value),
    meetsRule413: document.getElementById("set-rule413").checked,
    includeLeadershipBonus: document.getElementById("set-leadership").checked,
    includeDepthBonus: document.getElementById("set-depth").checked,
    includeRubyBonus: document.getElementById("set-ruby").checked,
    includePerformancePlus: document.getElementById("set-plus").checked,
    includeRetailMargin: document.getElementById("set-retail-on").checked,
  });
  setSync("Saving…", "saving");
  try {
    await api("/api/goals", "PUT", goals);
    await loadState(await api("/api/settings", "PUT", settings));
    setSync("Synced with server", "ok");
    const flag = document.getElementById("goals-saved");
    flag.textContent = "Saved ✓";
    setTimeout(() => (flag.textContent = ""), 2000);
  } catch (e) {
    setSync("Error: " + e.message, "error");
  }
}

function renderGap() {
  const el = document.getElementById("gap-suggestions");
  if (!store.gap) { el.innerHTML = ""; return; }
  el.innerHTML = store.gap.suggestions.map((s) => `<div class="suggestion">${escapeHtml(s)}</div>`).join("");
}

// ---------- Tabs ----------
function renderActiveTab() {
  const active = document.querySelector(".tab.active").dataset.tab;
  if (active === "map") renderCanvas("CURRENT");
  else if (active === "plan") { renderCanvas("IDEAL"); renderGap(); }
  else if (active === "calculator") renderCalculator();
  else if (active === "goals") { populateGoalsForm(); populateProfile(); }
}

function setupTabs() {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
      document.querySelectorAll(".tab-panel").forEach((p) => p.classList.remove("active"));
      tab.classList.add("active");
      document.getElementById("tab-" + tab.dataset.tab).classList.add("active");
      renderActiveTab();
    });
  });
}

function setupToolbars() {
  document.querySelectorAll("[data-zoom]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const which = btn.dataset.target === "plan" ? "plan" : (btn.closest("#tab-plan") ? "plan" : "map");
      const kind = which === "plan" ? "IDEAL" : "CURRENT";
      const svg = document.getElementById(which + "-canvas");
      if (btn.dataset.zoom === "in") zoom(which, 0.8);
      else if (btn.dataset.zoom === "out") zoom(which, 1.25);
      else { view[which].box = fitBox(nodesOfKind(kind), svg); renderCanvas(kind); }
    });
  });
  document.getElementById("map-layout").addEventListener("click", () =>
    mutate(api("/api/layout?kind=CURRENT", "POST")).then(() => { view.map.box = null; renderCanvas("CURRENT"); }));
  document.getElementById("plan-layout").addEventListener("click", () =>
    mutate(api("/api/layout?kind=IDEAL", "POST")).then(() => { view.plan.box = null; renderCanvas("IDEAL"); }));
  document.getElementById("map-sample").addEventListener("click", () => {
    if (!confirm("Replace the current organization with the sample team?")) return;
    mutate(api("/api/sample-data", "POST")).then(() => { view.map.box = null; view.plan.box = null; renderCanvas("CURRENT"); });
  });
  document.getElementById("goals-save").addEventListener("click", saveGoals);
}

// ---------- utils ----------
function val(id) { const e = document.getElementById(id); return e ? e.value : ""; }
function numVal(id) { return parseFloat(val(id)) || 0; }
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------- boot ----------
window.addEventListener("DOMContentLoaded", () => {
  setupTabs();
  setupToolbars();
  loadAll().catch((e) => setSync("Error: " + e.message, "error"));
});
