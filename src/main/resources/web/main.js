const STORE_TOKEN = "ib_web_token";
const STORE_LANG = "ib_web_lang";

const I18N = {
  "zh-CN": {
    title: "InvBackup 只读网页",
    subtitle: "筛选备份、预览快照、生成命令；不会直接执行恢复。",
    lang: "语言",
    token: "令牌",
    save: "保存令牌",
    refresh: "刷新",
    idle: "空闲",
    loading: "加载中...",
    ready: "就绪",
    players: "玩家",
    snapshots: "快照",
    search: "搜索",
    searchPlaceholder: "昵称或 UUID",
    sort: "排序",
    sortLatest: "按最新备份",
    sortName: "按昵称 A-Z",
    detail: "快照详情",
    target: "恢复目标玩家",
    targetPlaceholder: "可选，留空使用快照玩家",
    useUuid: "命令中使用 UUID",
    previewCmd: "预览命令",
    restoreCmd: "恢复请求命令",
    copy: "复制",
    noPlayers: "未找到玩家备份。",
    selectPlayer: "请先选择玩家。",
    noSnapshots: "该玩家没有快照。",
    emptyDetail: "选择一个快照后显示详情。",
    emptyStatus: "当前未选择状态。",
    noItems: "无物品数据",
    noStatus: "该快照不包含状态。",
    inspectorHint: "点击物品格查看详细属性。",
    snapshotsUnit: "个快照",
    player: "玩家",
    snapshotId: "快照 ID",
    time: "时间",
    triggeredBy: "触发者",
    triggerType: "触发类型",
    backupLevel: "备份级别",
    label: "标签",
    status: "玩家状态",
    health: "生命",
    food: "饥饿",
    level: "等级",
    exp: "经验进度",
    location: "位置",
    effects: "药水效果",
    inventory: "背包/快捷栏",
    armor: "装备栏",
    offhand: "副手",
    ender: "末影箱",
    amount: "数量",
    slot: "槽位",
    lore: "描述",
    enchants: "附魔",
    attributes: "属性",
    copied: "已复制",
    unauthorized: "未授权：请填写令牌后刷新。",
    apiError: "API 错误",
    error: "错误",
  },
  "en-US": {
    title: "InvBackup Read-Only Web",
    subtitle: "Filter backups, preview snapshots, and generate commands. This page never restores directly.",
    lang: "Language",
    token: "Token",
    save: "Save Token",
    refresh: "Refresh",
    idle: "Idle",
    loading: "Loading...",
    ready: "Ready",
    players: "Players",
    snapshots: "Snapshots",
    search: "Search",
    searchPlaceholder: "Name or UUID",
    sort: "Sort",
    sortLatest: "Last backup (newest)",
    sortName: "Name A-Z",
    detail: "Snapshot Detail",
    target: "Target Player",
    targetPlaceholder: "Optional override",
    useUuid: "Use UUID in commands",
    previewCmd: "Preview command",
    restoreCmd: "Restore request command",
    copy: "Copy",
    noPlayers: "No players found in backups.",
    selectPlayer: "Select a player first.",
    noSnapshots: "No snapshots found for this player.",
    emptyDetail: "Select a snapshot to preview details.",
    emptyStatus: "No status selected.",
    noItems: "No item data",
    noStatus: "No status data in this snapshot.",
    inspectorHint: "Click an item slot to inspect metadata.",
    snapshotsUnit: "snapshots",
    player: "Player",
    snapshotId: "Snapshot ID",
    time: "Time",
    triggeredBy: "Triggered by",
    triggerType: "Trigger type",
    backupLevel: "Backup level",
    label: "Label",
    status: "Player Status",
    health: "Health",
    food: "Food",
    level: "Level",
    exp: "Exp progress",
    location: "Location",
    effects: "Effects",
    inventory: "Inventory / Hotbar",
    armor: "Armor",
    offhand: "Offhand",
    ender: "Ender Chest",
    amount: "Amount",
    slot: "Slot",
    lore: "Lore",
    enchants: "Enchantments",
    attributes: "Attributes",
    copied: "Copied",
    unauthorized: "Unauthorized: set token and refresh.",
    apiError: "API error",
    error: "Error",
  }
};

const ENCH = {
  "minecraft:sharpness": { "zh-CN": "锋利", "en-US": "Sharpness" },
  "minecraft:smite": { "zh-CN": "亡灵杀手", "en-US": "Smite" },
  "minecraft:bane_of_arthropods": { "zh-CN": "节肢杀手", "en-US": "Bane of Arthropods" },
  "minecraft:efficiency": { "zh-CN": "效率", "en-US": "Efficiency" },
  "minecraft:unbreaking": { "zh-CN": "耐久", "en-US": "Unbreaking" },
  "minecraft:mending": { "zh-CN": "经验修补", "en-US": "Mending" },
  "minecraft:fortune": { "zh-CN": "时运", "en-US": "Fortune" },
  "minecraft:silk_touch": { "zh-CN": "精准采集", "en-US": "Silk Touch" },
  "minecraft:protection": { "zh-CN": "保护", "en-US": "Protection" },
  "minecraft:fire_protection": { "zh-CN": "火焰保护", "en-US": "Fire Protection" },
  "minecraft:blast_protection": { "zh-CN": "爆炸保护", "en-US": "Blast Protection" },
  "minecraft:projectile_protection": { "zh-CN": "弹射物保护", "en-US": "Projectile Protection" },
  "minecraft:power": { "zh-CN": "力量", "en-US": "Power" },
  "minecraft:punch": { "zh-CN": "冲击", "en-US": "Punch" },
  "minecraft:flame": { "zh-CN": "火矢", "en-US": "Flame" },
  "minecraft:infinity": { "zh-CN": "无限", "en-US": "Infinity" },
};

const ATTR = {
  "minecraft:generic.attack_damage": { "zh-CN": "攻击伤害", "en-US": "Attack Damage" },
  "minecraft:generic.attack_speed": { "zh-CN": "攻击速度", "en-US": "Attack Speed" },
  "minecraft:generic.armor": { "zh-CN": "护甲值", "en-US": "Armor" },
  "minecraft:generic.armor_toughness": { "zh-CN": "护甲韧性", "en-US": "Armor Toughness" },
  "minecraft:generic.max_health": { "zh-CN": "最大生命值", "en-US": "Max Health" },
  "minecraft:generic.movement_speed": { "zh-CN": "移动速度", "en-US": "Movement Speed" },
  "minecraft:generic.knockback_resistance": { "zh-CN": "击退抗性", "en-US": "Knockback Resistance" },
  "minecraft:player.block_interaction_range": { "zh-CN": "方块交互距离", "en-US": "Block Interaction Range" },
  "minecraft:player.entity_interaction_range": { "zh-CN": "实体交互距离", "en-US": "Entity Interaction Range" },
};

const OP = {
  "add_number": { "zh-CN": "直接加值", "en-US": "Add Number" },
  "add_scalar": { "zh-CN": "百分比加值", "en-US": "Add Scalar" },
  "multiply_scalar_1": { "zh-CN": "最终倍率", "en-US": "Multiply Scalar" },
};

const S = {
  lang: "zh-CN",
  token: "",
  settings: null,
  players: [],
  snapshots: [],
  selectedPlayer: null,
  selectedSnapshotId: null,
  selectedSnapshot: null,
  iconCache: new Map(),
  iconLoading: new Map(),
  forceLoadAllIcons: false,
};

const E = {
  titleMain: document.getElementById("title-main"),
  titleSub: document.getElementById("title-sub"),
  labelLanguage: document.getElementById("label-language"),
  languageSwitch: document.getElementById("language-switch"),
  labelToken: document.getElementById("label-token"),
  authToken: document.getElementById("auth-token"),
  saveToken: document.getElementById("save-token"),
  refreshAll: document.getElementById("refresh-all"),
  statusPill: document.getElementById("status-pill"),
  playersTitle: document.getElementById("players-title"),
  labelPlayerQuery: document.getElementById("label-player-query"),
  playerQuery: document.getElementById("player-query"),
  labelPlayerSort: document.getElementById("label-player-sort"),
  playerSort: document.getElementById("player-sort"),
  playersCount: document.getElementById("players-count"),
  playersList: document.getElementById("players-list"),
  snapshotsTitle: document.getElementById("snapshots-title"),
  snapshotsCount: document.getElementById("snapshots-count"),
  snapshotsList: document.getElementById("snapshots-list"),
  detailTitle: document.getElementById("detail-title"),
  commandsBox: document.getElementById("commands-box"),
  labelTargetPlayer: document.getElementById("label-target-player"),
  targetPlayer: document.getElementById("target-player"),
  useUuid: document.getElementById("use-uuid"),
  labelUseUuid: document.getElementById("label-use-uuid"),
  labelPreviewCommand: document.getElementById("label-preview-command"),
  labelRestoreCommand: document.getElementById("label-restore-command"),
  cmdPreview: document.getElementById("cmd-preview"),
  cmdRestore: document.getElementById("cmd-restore"),
  copyPreview: document.getElementById("copy-preview"),
  copyRestore: document.getElementById("copy-restore"),
  metaBox: document.getElementById("meta-box"),
  invContent: document.getElementById("inv-content"),
  invArmor: document.getElementById("inv-armor"),
  invOffhand: document.getElementById("inv-offhand"),
  invEnder: document.getElementById("inv-ender"),
  statusBox: document.getElementById("status-box"),
  itemInspector: document.getElementById("item-inspector"),
};

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function t(k) { return (I18N[S.lang] || I18N["zh-CN"])[k] || k; }

function init() {
  S.lang = localStorage.getItem(STORE_LANG) || "zh-CN";
  if (!I18N[S.lang]) S.lang = "zh-CN";
  S.token = new URLSearchParams(location.search).get("token") || localStorage.getItem(STORE_TOKEN) || "";
  E.languageSwitch.value = S.lang;
  E.authToken.value = S.token;

  bindEvents();
  applyText();
  renderEmpty();
  refreshAll();
}

function bindEvents() {
  E.languageSwitch.addEventListener("change", () => {
    S.lang = E.languageSwitch.value;
    localStorage.setItem(STORE_LANG, S.lang);
    applyText();
    renderPlayers();
    renderSnapshots();
    renderDetail();
  });

  E.saveToken.addEventListener("click", () => {
    S.token = E.authToken.value.trim();
    localStorage.setItem(STORE_TOKEN, S.token);
    refreshAll();
  });
  E.refreshAll.addEventListener("click", () => refreshAll(true));

  const onFilter = debounce(async () => {
    setStatus(t("loading"), "loading");
    try {
      await loadPlayersAndChildren();
      setStatus(t("ready"), "ok");
    } catch (e) { onError(e); }
  }, 220);
  E.playerQuery.addEventListener("input", onFilter);
  E.playerSort.addEventListener("change", onFilter);

  E.targetPlayer.addEventListener("input", renderCommands);
  E.useUuid.addEventListener("change", renderCommands);
  E.copyPreview.addEventListener("click", () => copyText(E.cmdPreview.textContent || ""));
  E.copyRestore.addEventListener("click", () => copyText(E.cmdRestore.textContent || ""));
}

async function refreshAll(forceIcons = false) {
  if (forceIcons) {
    clearIconCache();
    S.forceLoadAllIcons = true;
  }
  setStatus(t("loading"), "loading");
  try {
    S.settings = await apiJson("/api/settings", {}, false);
    await loadPlayersAndChildren();
    setStatus(t("ready"), "ok");
  } catch (e) { onError(e); }
}

async function loadPlayersAndChildren() {
  await loadPlayers();
  if (S.selectedPlayer) await loadSnapshots();
  else {
    S.snapshots = [];
    S.selectedSnapshotId = null;
    S.selectedSnapshot = null;
    renderSnapshots();
    renderDetail();
  }
}

async function loadPlayers() {
  const data = await apiJson("/api/players", {
    q: E.playerQuery.value.trim(),
    sort: E.playerSort.value,
    limit: 500
  }, true);
  S.players = Array.isArray(data.items) ? data.items : [];
  if (!S.players.some((x) => x.uuid === S.selectedPlayer)) {
    S.selectedPlayer = S.players.length ? S.players[0].uuid : null;
  }
  renderPlayers();
}

async function loadSnapshots() {
  const data = await apiJson("/api/snapshots", { player: S.selectedPlayer, limit: 500 }, true);
  S.snapshots = Array.isArray(data.items) ? data.items : [];
  if (!S.snapshots.some((x) => x.id === S.selectedSnapshotId)) {
    S.selectedSnapshotId = S.snapshots.length ? S.snapshots[0].id : null;
  }
  renderSnapshots();
  if (S.selectedSnapshotId) await loadSnapshotDetail();
  else { S.selectedSnapshot = null; renderDetail(); }
}

async function loadSnapshotDetail() {
  const data = await apiJson("/api/snapshot", { player: S.selectedPlayer, id: S.selectedSnapshotId }, true);
  S.selectedSnapshot = data.snapshot || null;
  renderDetail();
}

function applyText() {
  document.title = t("title");
  document.documentElement.lang = S.lang === "zh-CN" ? "zh-CN" : "en";
  E.titleMain.textContent = t("title");
  E.titleSub.textContent = t("subtitle");
  E.labelLanguage.textContent = t("lang");
  E.labelToken.textContent = t("token");
  E.saveToken.textContent = t("save");
  E.refreshAll.textContent = t("refresh");
  E.playersTitle.textContent = t("players");
  E.labelPlayerQuery.textContent = t("search");
  E.playerQuery.placeholder = t("searchPlaceholder");
  E.labelPlayerSort.textContent = t("sort");
  E.playerSort.options[0].textContent = t("sortLatest");
  E.playerSort.options[1].textContent = t("sortName");
  E.snapshotsTitle.textContent = t("snapshots");
  E.labelTargetPlayer.textContent = t("target");
  E.targetPlayer.placeholder = t("targetPlaceholder");
  E.labelUseUuid.textContent = t("useUuid");
  E.labelPreviewCommand.textContent = t("previewCmd");
  E.labelRestoreCommand.textContent = t("restoreCmd");
  E.copyPreview.textContent = t("copy");
  E.copyRestore.textContent = t("copy");
  if (!E.statusPill.classList.contains("loading")
      && !E.statusPill.classList.contains("error")
      && !E.statusPill.classList.contains("ok")) {
    E.statusPill.textContent = t("idle");
  }
}

function renderPlayers() {
  E.playersCount.textContent = String(S.players.length);
  E.playersList.innerHTML = "";
  if (!S.players.length) {
    E.playersList.innerHTML = `<div class="empty-state">${esc(t("noPlayers"))}</div>`;
    return;
  }
  S.players.forEach((p) => {
    const card = el("article", "list-card hoverable" + (p.uuid === S.selectedPlayer ? " active" : ""));
    const title = el("div", "list-title", p.name || p.uuid);
    const meta = el("div", "list-meta");
    meta.innerHTML = [
      `<span>${esc(p.uuid)}</span>`,
      `<span class="chip">${esc(String(p.snapshotCount || 0))} ${esc(t("snapshotsUnit"))}</span>`,
      p.latestTimestamp ? `<span class="chip">${esc(fmtTime(p.latestTimestamp))}</span>` : ""
    ].join("");
    card.append(title, meta);
    card.addEventListener("click", async () => {
      if (S.selectedPlayer === p.uuid) return;
      S.selectedPlayer = p.uuid;
      S.selectedSnapshotId = null;
      S.selectedSnapshot = null;
      renderPlayers();
      setStatus(t("loading"), "loading");
      try { await loadSnapshots(); setStatus(t("ready"), "ok"); } catch (e) { onError(e); }
    });
    E.playersList.appendChild(card);
  });
}

function renderSnapshots() {
  E.snapshotsCount.textContent = String(S.snapshots.length);
  E.snapshotsList.innerHTML = "";
  if (!S.selectedPlayer) {
    E.snapshotsList.innerHTML = `<div class="empty-state">${esc(t("selectPlayer"))}</div>`;
    return;
  }
  if (!S.snapshots.length) {
    E.snapshotsList.innerHTML = `<div class="empty-state">${esc(t("noSnapshots"))}</div>`;
    return;
  }
  S.snapshots.forEach((s) => {
    const card = el("article", "list-card hoverable" + (s.id === S.selectedSnapshotId ? " active" : ""));
    const title = el("div", "list-title", s.id);
    const meta = el("div", "list-meta");
    meta.innerHTML = [
      `<span>${esc(fmtTime(s.timestamp || 0))}</span>`,
      `<span class="chip">${esc(localizeId(s.triggerType || "unknown"))}</span>`,
      `<span class="chip">${esc(s.backupLevel || "minimal")}</span>`,
      s.label ? `<span class="chip">${esc(s.label)}</span>` : "",
    ].join("");
    card.append(title, meta);
    card.addEventListener("click", async () => {
      if (S.selectedSnapshotId === s.id) return;
      S.selectedSnapshotId = s.id;
      S.selectedSnapshot = null;
      renderSnapshots();
      setStatus(t("loading"), "loading");
      try { await loadSnapshotDetail(); setStatus(t("ready"), "ok"); } catch (e) { onError(e); }
    });
    E.snapshotsList.appendChild(card);
  });
}

function renderDetail() {
  const player = S.players.find((x) => x.uuid === S.selectedPlayer);
  const snap = S.selectedSnapshot;
  if (!player || !snap) { renderEmpty(); return; }

  E.commandsBox.classList.remove("hidden");
  E.detailTitle.textContent = `${t("detail")} | ${player.name || player.uuid} | ${snap.id || ""}`;
  renderCommands();
  renderMeta(player, snap);
  renderInv(snap);
  renderStatus(snap);
  E.itemInspector.textContent = t("inspectorHint");
  S.forceLoadAllIcons = false;
}

function renderCommands() {
  const player = S.players.find((x) => x.uuid === S.selectedPlayer);
  const snap = S.selectedSnapshot;
  if (!player || !snap) return;
  const target = E.targetPlayer.value.trim() || (E.useUuid.checked ? player.uuid : (player.name || player.uuid)) || "<player>";
  const sid = snap.id || "<snapshotId>";
  E.cmdPreview.textContent = `/ib preview ${q(target)} ${q(sid)}`;
  E.cmdRestore.textContent = `/ib restore ${q(target)} ${q(sid)}`;
}

function renderMeta(player, snap) {
  const m = snap.meta || {};
  E.metaBox.innerHTML = [
    row(t("player"), `${player.name || player.uuid} (${player.uuid})`),
    row(t("snapshotId"), snap.id || "-"),
    row(t("time"), fmtTime(num(m.timestamp))),
    row(t("triggeredBy"), m["triggered-by"] || "-"),
    row(t("triggerType"), localizeId(m["trigger-type"] || "-")),
    row(t("backupLevel"), m["backup-level"] || "-"),
    row(t("label"), m.label || "-"),
  ].join("");
}

function renderInv(snap) {
  const inv = snap.inventory || {};
  drawInv(E.invContent, t("inventory"), 36, normItems(inv.content));
  drawInv(E.invArmor, t("armor"), 9, normItems(inv.armor), { 0: S.lang === "zh-CN" ? "靴" : "Boots", 1: S.lang === "zh-CN" ? "腿" : "Legs", 2: S.lang === "zh-CN" ? "胸" : "Chest", 3: S.lang === "zh-CN" ? "头" : "Head" });
  drawInv(E.invOffhand, t("offhand"), 9, inv.offhand ? [normItem(inv.offhand, 0)] : [], { 0: S.lang === "zh-CN" ? "副手" : "Off" });
  drawInv(E.invEnder, t("ender"), 27, normItems(inv.enderchest));
}

function drawInv(container, title, size, items, labels = null) {
  container.innerHTML = "";
  container.appendChild(el("div", "inv-title", title));
  if (!items.length) {
    container.innerHTML += `<div class="empty-state">${esc(t("noItems"))}</div>`;
    return;
  }
  const bySlot = new Map(items.map((x) => [x.slot, x]));
  const grid = el("div", "grid");
  for (let i = 0; i < size; i++) {
    const slot = el("div", "slot hoverable");
    const item = bySlot.get(i);
    if (!item) slot.classList.add("empty");
    else {
      const img = document.createElement("img");
      img.alt = item.id;
      img.src = S.settings?.icons?.placeholderPath || "./assets/item_placeholder.svg";
      slot.appendChild(img);
      setIcon(img, item.id, S.forceLoadAllIcons);
      if (item.amount > 1) slot.appendChild(el("span", "count", String(item.amount)));
      slot.title = itemTooltip(item);
      slot.addEventListener("click", async () => {
        inspectItem(item);
        await setIcon(img, item.id, true);
      });
    }
    slot.appendChild(el("span", "slot-label", labels?.[i] || String(i)));
    grid.appendChild(slot);
  }
  container.appendChild(grid);
}

function renderStatus(snap) {
  const st = snap.status || {};
  const keys = Object.keys(st);
  if (!keys.length) {
    E.statusBox.innerHTML = `<div class="empty-state">${esc(t("noStatus"))}</div>`;
    return;
  }
  const out = [];
  const seen = new Set();
  if (st.health != null) { out.push(row(t("health"), `${st.health}/${st["max-health"] ?? 20}`)); seen.add("health"); seen.add("max-health"); }
  if (st.food != null) { out.push(row(t("food"), st.food)); seen.add("food"); }
  if (st.level != null) { out.push(row(t("level"), st.level)); seen.add("level"); }
  if (st.exp != null) { out.push(row(t("exp"), st.exp)); seen.add("exp"); }
  if (st["location.world"] != null || st["location.x"] != null || st["location.y"] != null || st["location.z"] != null) {
    out.push(row(t("location"), `${st["location.world"] ?? "?"} (${st["location.x"] ?? "?"}, ${st["location.y"] ?? "?"}, ${st["location.z"] ?? "?"})`));
    ["location.world", "location.x", "location.y", "location.z", "location.yaw", "location.pitch"].forEach((k) => seen.add(k));
  }
  if (Array.isArray(st.effects)) { out.push(row(t("effects"), st.effects.map(localizeId).join(" | "))); seen.add("effects"); }
  keys.sort().forEach((k) => { if (!seen.has(k)) out.push(row(localizeId(k), asText(st[k]))); });
  E.statusBox.innerHTML = `<div class="inv-title">${esc(t("status"))}</div>${out.join("")}`;
}

function inspectItem(item) {
  const lines = [
    `<div><strong>${esc(strip(item.displayName) || localizeItem(item.id))}</strong></div>`,
    `<div>ID: ${esc(item.id)} | ${esc(t("amount"))}: ${esc(item.amount)} | ${esc(t("slot"))}: ${esc(item.slot)}</div>`,
  ];
  if (item.lore.length) lines.push(`<div><strong>${esc(t("lore"))}:</strong> ${item.lore.map((x) => esc(strip(x))).join(" | ")}</div>`);
  const ench = Object.entries(item.enchantments || {});
  if (ench.length) lines.push(`<div><strong>${esc(t("enchants"))}:</strong> ${ench.map(([id, lv]) => `${esc(localizeEnchant(id))} ${esc(roman(lv))}`).join(" | ")}</div>`);
  const attrs = Array.isArray(item.attributeModifiers) ? item.attributeModifiers : [];
  if (attrs.length) lines.push(`<div><strong>${esc(t("attributes"))}:</strong> ${attrs.map((a) => `${esc(localizeAttr(a.attribute))} ${esc(attrValue(a.amount, a.operation))} [${esc(localizeOp(a.operation))}]`).join(" | ")}</div>`);
  lines.push(`<pre>${esc(JSON.stringify(item, null, 2))}</pre>`);
  E.itemInspector.innerHTML = lines.join("");
}

function renderEmpty() {
  E.commandsBox.classList.add("hidden");
  E.detailTitle.textContent = t("detail");
  E.metaBox.innerHTML = `<div class="empty-state">${esc(t("emptyDetail"))}</div>`;
  E.statusBox.innerHTML = `<div class="empty-state">${esc(t("emptyStatus"))}</div>`;
  E.invContent.innerHTML = "";
  E.invArmor.innerHTML = "";
  E.invOffhand.innerHTML = "";
  E.invEnder.innerHTML = "";
  E.itemInspector.textContent = t("inspectorHint");
}

function normItems(arr) { return Array.isArray(arr) ? arr.map((x) => normItem(x, Number(x?.slot))).filter((x) => x && x.id && Number.isFinite(x.slot)) : []; }
function normItem(raw, slot) {
  if (!raw || typeof raw !== "object") return null;
  return {
    slot: Number.isFinite(slot) ? slot : num(raw.slot),
    id: String(raw.id || ""),
    amount: Math.max(1, num(raw.amount) || 1),
    displayName: raw.displayName ? String(raw.displayName) : "",
    lore: Array.isArray(raw.lore) ? raw.lore.map(String) : [],
    enchantments: raw.enchantments && typeof raw.enchantments === "object" ? raw.enchantments : {},
    attributeModifiers: Array.isArray(raw.attributeModifiers) ? raw.attributeModifiers : [],
  };
}

async function setIcon(img, id, force = false) {
  const item = normalizeItemId(id);
  if (!item) return;
  try {
    const url = await iconUrl(item, force);
    if (url) img.src = url;
  } catch { /* keep fallback */ }
}

async function iconUrl(item, force = false) {
  const now = Date.now();
  const ttl = (S.settings?.icons?.cacheTtlSeconds || 600) * 1000;
  const cached = force ? null : S.iconCache.get(item);
  if (cached && cached.expireAt > now) {
    cached.last = now;
    return cached.url;
  }
  if (!force && S.iconLoading.has(item)) return S.iconLoading.get(item);
  const p = (async () => {
    const blob = await apiBlob("/api/icon", {
      id: item,
      force: force ? 1 : 0,
      t: force ? Date.now() : undefined
    }, true);
    const url = URL.createObjectURL(blob);
    const old = S.iconCache.get(item);
    if (old) URL.revokeObjectURL(old.url);
    S.iconCache.set(item, { url, expireAt: now + ttl, last: now });
    pruneIconCache();
    return url;
  })().finally(() => {
    if (!force) S.iconLoading.delete(item);
  });
  if (!force) S.iconLoading.set(item, p);
  return p;
}

function clearIconCache() {
  for (const [, v] of S.iconCache.entries()) {
    URL.revokeObjectURL(v.url);
  }
  S.iconCache.clear();
}

function pruneIconCache() {
  const now = Date.now();
  const max = S.settings?.icons?.cacheMaxEntries || 1024;
  for (const [k, v] of S.iconCache.entries()) {
    if (v.expireAt <= now) {
      URL.revokeObjectURL(v.url);
      S.iconCache.delete(k);
    }
  }
  if (S.iconCache.size <= max) return;
  const sorted = [...S.iconCache.entries()].sort((a, b) => a[1].last - b[1].last);
  const remove = S.iconCache.size - max;
  for (let i = 0; i < remove; i++) {
    const [k, v] = sorted[i];
    URL.revokeObjectURL(v.url);
    S.iconCache.delete(k);
  }
}

async function apiJson(path, params = {}, auth = true) {
  const r = await fetchApi(path, params, auth);
  const text = await r.text();
  let d = {};
  if (text) { try { d = JSON.parse(text); } catch { d = { message: text }; } }
  if (!r.ok) throw new ApiError(r.status, d.message || r.statusText || "Request failed");
  return d;
}

async function apiBlob(path, params = {}, auth = true) {
  const r = await fetchApi(path, params, auth);
  if (!r.ok) throw new ApiError(r.status, await r.text());
  return r.blob();
}

function fetchApi(path, params = {}, auth = true) {
  const u = new URL(path, location.origin);
  for (const [k, v] of Object.entries(params)) {
    if (v != null && String(v) !== "") u.searchParams.set(k, String(v));
  }
  const headers = {};
  if (auth && S.token) headers["X-InvBackup-Token"] = S.token;
  return fetch(u, { method: "GET", headers, cache: "no-store" });
}

function onError(e) {
  if (e instanceof ApiError && e.status === 401) { setStatus(t("unauthorized"), "error"); return; }
  if (e instanceof ApiError) { setStatus(`${t("apiError")} ${e.status}: ${e.message}`, "error"); return; }
  setStatus(`${t("error")}: ${e.message || String(e)}`, "error");
}

function setStatus(text, cls = "normal") {
  E.statusPill.textContent = text;
  E.statusPill.className = `status-pill ${cls}`;
}

function localizeItem(id) { return localizeId(normNs(id)); }
function localizeEnchant(id) {
  const key = normNs(id);
  const v = ENCH[key];
  if (v && v[S.lang]) return v[S.lang];
  return localizeId(key);
}
function localizeAttr(id) {
  const key = normNs(id);
  const v = ATTR[key];
  if (v && v[S.lang]) return v[S.lang];
  return localizeId(key);
}
function localizeOp(op) {
  const key = String(op || "").toLowerCase();
  const v = OP[key];
  if (v && v[S.lang]) return v[S.lang];
  return localizeId(key);
}

function localizeId(v) {
  const raw = String(v || "");
  const core = raw.includes(":") ? raw.split(":").pop() : raw;
  if (!core) return "-";
  const parts = core.split(/[_.-]/g).filter(Boolean);
  if (!parts.length) return core;
  if (S.lang === "zh-CN") return parts.join(" ");
  return parts.map((x) => x[0].toUpperCase() + x.slice(1)).join(" ");
}

function itemTooltip(item) {
  const out = [strip(item.displayName) || localizeItem(item.id)];
  if (item.amount > 1) out.push(`${t("amount")}: ${item.amount}`);
  if (item.lore.length) { out.push("---"); item.lore.forEach((x) => out.push(strip(x))); }
  const e = Object.entries(item.enchantments || {});
  if (e.length) { out.push("---"); e.forEach(([id, lv]) => out.push(`${localizeEnchant(id)} ${roman(lv)}`)); }
  const a = Array.isArray(item.attributeModifiers) ? item.attributeModifiers : [];
  if (a.length) { out.push("---"); a.forEach((x) => out.push(`${localizeAttr(x.attribute)} ${attrValue(x.amount, x.operation)} [${localizeOp(x.operation)}]`)); }
  return out.join("\n");
}

function attrValue(amount, op) {
  const n = Number(amount || 0);
  const o = String(op || "").toLowerCase();
  if (o === "add_scalar" || o === "multiply_scalar_1") return `${(n * 100).toFixed(2)}%`;
  return Number.isInteger(n) ? String(n) : n.toFixed(2);
}

function q(s) { const t = String(s ?? ""); return /\\s/.test(t) || t.includes("\"") ? `"${t.replaceAll("\"", "\\\"")}"` : t; }
function row(k, v) { return `<div><strong>${esc(k)}:</strong> ${esc(String(v ?? "-"))}</div>`; }
function esc(s) { return String(s).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;").replaceAll("'", "&#39;"); }
function strip(s) { return String(s || "").replace(/\u00A7[0-9A-FK-OR]/gi, "").replace(/&[0-9A-FK-OR]/gi, ""); }
function num(v) { const n = Number(v); return Number.isFinite(n) ? n : 0; }
function fmtTime(ts) { return num(ts) ? new Date(num(ts)).toLocaleString(S.lang === "zh-CN" ? "zh-CN" : "en-US") : "-"; }
function asText(v) { if (v == null) return "null"; if (Array.isArray(v)) return v.join(", "); if (typeof v === "object") return JSON.stringify(v); return String(v); }
function normalizeItemId(v) { const s = String(v || "").toLowerCase().trim(); return s.startsWith("minecraft:") ? s.slice(10) : s; }
function normNs(v) { const s = String(v || "").toLowerCase().trim(); return s.includes(":") ? s : `minecraft:${s}`; }
function roman(level) {
  let n = Math.max(1, Math.floor(Number(level) || 1));
  const map = [[10, "X"], [9, "IX"], [5, "V"], [4, "IV"], [1, "I"]];
  let out = "";
  for (const [v, s] of map) while (n >= v) { out += s; n -= v; }
  return out || "I";
}
function el(tag, cls = "", text = null) { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }
async function copyText(text) {
  if (!text) return;
  try { await navigator.clipboard.writeText(text); } catch {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    document.execCommand("copy");
    ta.remove();
  }
  const old = document.title;
  document.title = t("copied");
  setTimeout(() => { document.title = old; }, 600);
}
function debounce(fn, ms) { let t = null; return (...a) => { if (t) clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

document.addEventListener("DOMContentLoaded", init);
