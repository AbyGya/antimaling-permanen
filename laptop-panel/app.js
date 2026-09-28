// Panel AntiMaling via MQTT publik — tanpa daftar, tanpa API key.
// HP subscribe am/{kode}/cmd, panel subscribe am/{kode}/res + am/{kode}/status.
const $ = id => document.getElementById(id);
const BROKER = "wss://broker.emqx.io:8084/mqtt";
const LS_CODES = "am.codes";
const LS_CODE = "am.code";

let client = null, code = null, lastSeen = 0, onlineTimer = null;
let offTimer = null, token = null, trail = [], map = null, marker = null, line = null;
let follow = true, showTrail = true;

const esc = s => String(s).replace(/[&<>"]/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[m]));
const linkify = s => s.replace(/(https?:\/\/[^\s<]+)/g, u => `<a href="${u}" target="_blank" rel="noopener">${u}</a>`);

// ---------- token (HARUS sama dgn HP: sha256("antimaling:"+kode) 8 byte pertama)
async function makeToken(c) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("antimaling:" + c));
  return Array.from(new Uint8Array(buf).slice(0, 8)).map(b => b.toString(16).padStart(2, "0")).join("");
}
function normCode(raw) {
  return String(raw).toUpperCase().replace(/[^A-Z0-9]/g, "").replace(/[IO01]/g, "").slice(0, 8);
}
function fmtCode(c) { return c.length === 8 ? c.slice(0, 4) + "-" + c.slice(4) : c; }

// ---------- kode tersimpan (biar tidak perlu ketik ulang tiap buka panel)
function loadCodes() { try { return JSON.parse(localStorage.getItem(LS_CODES)) || []; } catch { return []; } }
function saveCode(c) {
  const list = loadCodes().filter(x => x !== c);
  list.unshift(c);
  localStorage.setItem(LS_CODES, JSON.stringify(list.slice(0, 8)));
  localStorage.setItem(LS_CODE, c);
  renderSaved();
}
function renderSaved() {
  const box = $("savedCodes");
  const list = loadCodes();
  if (!list.length) { box.classList.add("hidden"); return; }
  box.classList.remove("hidden");
  box.innerHTML = "Tersimpan: " + list.map(c =>
    `<button class="chip" data-code="${c}">${fmtCode(c)}</button>`).join("");
  box.querySelectorAll("[data-code]").forEach(b => b.onclick = () => {
    $("pairCode").value = fmtCode(b.dataset.code);
    connect(b.dataset.code);
  });
}

// ---------- pairing
$("btnConnect").onclick = () => {
  const c = normCode($("pairCode").value);
  if (c.length !== 8) return alert("Kode 8 karakter dari app HP (contoh: K7MQ-2XPD).");
  connect(c);
};
$("pairCode").addEventListener("keydown", e => { if (e.key === "Enter") $("btnConnect").click(); });

function setConn(on, txt) {
  $("dot").className = "dot " + (on ? "on" : "off");
  $("connText").textContent = txt;
}

async function connect(c) {
  try { client?.end(true); } catch {}
  code = c;
  token = await makeToken(c);
  saveCode(c);
  clearInterval(onlineTimer);
  setConn(false, "Menghubungkan…");
  addLog(`Menghubungkan ke ${fmtCode(c)}…`, true);
  client = mqtt.connect(BROKER, {
    clientId: "am-panel-" + Math.random().toString(16).slice(2, 10),
    clean: true, reconnectPeriod: 3000, connectTimeout: 8000
  });
  client.on("connect", () => {
    setConn(true, "Tersambung ke broker");
    client.subscribe([`am/${code}/res`, `am/${code}/status`], { qos: 1 }, err => {
      if (err) { alert("Subscribe gagal: " + err.message); return; }
      addLog("Menunggu HP… (buka app HP 1x bila perlu)");
      startDash();
      send("ping");
    });
  });
  client.on("message", (t, p) => {
    try {
      if (t === `am/${code}/status`) {
        const v = p.toString();
        if (v === "offline") return scheduleOffline();
        cancelOffline();
        lastSeen = parseInt(v) || Date.now();
        return setOnline();
      }
      if (t === `am/${code}/res`) {
        const d = JSON.parse(p.toString());
        // verifikasi token: cegah orang lain menyuntik pesan palsu ke panel
        if (d.t && token && d.t !== token) return;
        if (d.ts) lastSeen = parseInt(d.ts) || lastSeen;
        cancelOffline();
        setOnline();
        if (d.id && pending[d.id]) { clearTimeout(pending[d.id]); delete pending[d.id]; }
        return render(d);
      }
    } catch {}
  });
  client.on("error", e => setConn(false, "Error: " + (e.message || e)));
  client.on("close", () => setConn(false, "Terputus — mencoba lagi…"));
  onlineTimer = setInterval(() => {
    if (!code) return;
    if (Date.now() - lastSeen > 45000) setOffline();
  }, 5000);
}

function setOnline() {
  const el = $("online");
  el.textContent = "● online"; el.className = "pill";
  $("devInfo").textContent = "HP terhubung • terakhir aktif " + new Date(lastSeen).toLocaleString("id-ID");
}
function setOffline() {
  const el = $("online");
  el.textContent = "○ offline"; el.className = "pill off";
}
function scheduleOffline() {
  if (offTimer) return;
  offTimer = setTimeout(() => { offTimer = null; setOffline(); }, 20000);
}
function cancelOffline() { if (offTimer) { clearTimeout(offTimer); offTimer = null; } }

const pending = {};

function startDash() {
  $("pairCard").classList.add("hidden");
  $("dash").classList.remove("hidden");
  $("devId").textContent = fmtCode(code);
  if (!document.querySelector("#feed .item")) $("feed").innerHTML = '<p class="hint">Tersambung. Klik perintah di atas.</p>';
  initMap();
}

// ---------- peta
function initMap() {
  if (map) return;
  map = L.map("map").setView([-6.2, 106.8], 5);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19, attribution: "© OpenStreetMap"
  }).addTo(map);
  line = L.polyline([], { color: "#ff4d5e", weight: 3, opacity: .8 }).addTo(map);
}
function setFix(lat, lon, acc) {
  if (!map) initMap();
  const here = [lat, lon];
  const pos = marker ? marker.getLatLng() : null;
  marker = marker ? marker.setLatLng(here) : L.marker(here).addTo(map);
  if (pos && (pos.lat !== lat || pos.lng !== lon)) {
    trail.push([pos.lat, pos.lng]);
    if (trail.length > 300) trail.shift();
  }
  if (showTrail) line.setLatLngs(trail.concat([here]));
  if (follow) map.setView(here, Math.max(map.getZoom(), 16));
  $("fixInfo").innerHTML = acc
    ? `${lat.toFixed(6)}, ${lon.toFixed(6)} • akurasi ±${Math.round(acc)}m`
    : `${lat.toFixed(6)}, ${lon.toFixed(6)}`;
}
$("btnFollow").onclick = () => {
  follow = !follow;
  $("btnFollow").classList.toggle("on", follow);
  $("btnFollow").textContent = follow ? "following" : "free";
};
$("btnTrail").onclick = () => {
  showTrail = !showTrail;
  $("btnTrail").classList.toggle("on", showTrail);
  if (line) line.setLatLngs(showTrail ? trail : []);
};

// ---------- render
function render(d) {
  if (typeof d.lat === "number" && d.lat !== 0) setFix(d.lat, d.lon, d.acc || 0);
  if (d.image) addImage(d);
  addLog(`<b>${esc(d.cmd || "")}</b> — ${linkify(esc(d.text || ""))}`);
}

function addImage(d) {
  if (!$("gal").querySelector(".shot")) $("gal").innerHTML = "";
  const box = document.createElement("div");
  box.className = "shot";
  const when = d.ts ? new Date(parseInt(d.ts)).toLocaleString("id-ID") : "";
  box.innerHTML = `<img src="data:image/jpeg;base64,${d.image}" loading="lazy">
    <div class="cap">${esc(d.text || d.cmd || "")}<br><span>${when}</span></div>`;
  box.querySelector("img").onclick = () => window.open(box.querySelector("img").src, "_blank");
  $("gal").prepend(box);
  while ($("gal").children.length > 12) $("gal").lastChild.remove();
}

function addLog(html, isMeta) {
  if (!$("feed").querySelector(".item")) $("feed").innerHTML = "";
  const div = document.createElement("div");
  div.className = "item";
  if (isMeta) { div.innerHTML = `<p>📤 ${html}</p>`; }
  else {
    const t = Date.now();
    div.innerHTML = `<div class="meta">${new Date(t).toLocaleTimeString("id-ID")}</div><p>${html}</p>`;
  }
  $("feed").prepend(div);
  while ($("feed").children.length > 30) $("feed").lastChild.remove();
}

// ---------- kirim perintah
function send(type, arg = "") {
  if (!client || !code) return alert("Sambungkan dulu.");
  const id = Math.random().toString(16).slice(2);
  client.publish(`am/${code}/cmd`, JSON.stringify({
    id, type, arg: String(arg), ts: Date.now().toString()
  }), { qos: 1 });
  addLog(`Perintah <b>${esc(type)}</b> terkirim…`, true);
  pending[id] = setTimeout(() => {
    delete pending[id];
    addLog(`⌛ <b>${esc(type)}</b>: belum ada balasan 30 dtk — HP mungkin offline.`, true);
  }, 30000);
}
document.querySelectorAll("[data-cmd]").forEach(b =>
  b.onclick = () => send(b.dataset.cmd, b.dataset.arg || ""));
$("btnText").onclick = () => { const v = $("customText").value.trim(); if (v) { send("text", v); $("customText").value = ""; } };
$("btnUnlock").onclick = () => { const v = $("pinInput").value.trim(); if (v) send("unlock", v); };
$("btnClear").onclick = () => $("feed").innerHTML = '<p class="hint">Dibersihkan.</p>';
$("btnClearGal").onclick = () => $("gal").innerHTML = '<p class="hint">Galeri dikosongkan.</p>';

// ---------- auto-sambung ulang kode terakhir
(function init() {
  renderSaved();
  const last = localStorage.getItem(LS_CODE);
  if (last && last.length === 8) { $("pairCode").value = fmtCode(last); connect(last); }
})();
