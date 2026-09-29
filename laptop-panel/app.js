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
    `<span class="chipwrap"><button class="chip" data-code="${c}">${fmtCode(c)}</button>` +
    `<button class="chipx" data-forget="${c}" title="Lupakan">×</button></span>`).join("");
  box.querySelectorAll("[data-code]").forEach(b => b.onclick = () => {
    $("pairCode").value = fmtCode(b.dataset.code);
    connect(b.dataset.code);
  });
  box.querySelectorAll("[data-forget]").forEach(b => b.onclick = e => {
    e.stopPropagation(); forgetCode(b.dataset.forget);
  });
}

// ---------- pairing
$("btnConnect").onclick = () => {
  const c = normCode($("pairCode").value);
  if (c.length !== 8) return alert("Kode 8 karakter dari app HP (contoh: K7MQ-2XPD).");
  connect(c);
};
$("pairCode").addEventListener("keydown", e => { if (e.key === "Enter") $("btnConnect").click(); });

/** Putuskan sesi & kembali ke layar pairing — selalu bisa dipakai. */
function disconnect(note) {
  try { clearInterval(onlineTimer); } catch {}
  try { clearTimeout(offTimer); offTimer = null; } catch {}
  try { client?.end(true); } catch {}
  client = null;
  code = null; token = null; pendingNonce = null;
  restoreNewCodeBtn();
  setConn(false, "Belum tersambung");
  $("dash").classList.add("hidden");
  $("pairCard").classList.remove("hidden");
  $("btnHome").classList.add("hidden");
  $("pairCode").value = "";
  renderSaved();
  if (note) $("pairHint").textContent = note;
}
$("btnHome").onclick = () => disconnect("");

/** Tombol hapus satu kode dari daftar tersimpan. */
function forgetCode(c) {
  const list = loadCodes().filter(x => x !== c);
  localStorage.setItem(LS_CODES, JSON.stringify(list));
  if (localStorage.getItem(LS_CODE) === c) localStorage.removeItem(LS_CODE);
  renderSaved();
}

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
  $("btnHome").classList.remove("hidden");
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
  client.on("close", () => { if (code) setConn(false, "Terputus — mencoba lagi…"); });
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

// ---------- kirim foto pencuri ke layar kunci HP ----------
$("btnSendPhoto").onclick = () => {
  const f = $("malingFile").files[0];
  if (!f) return alert("Pilih file foto dulu.");
  if (f.size > 8 * 1024 * 1024) return alert("Foto terlalu besar. Maksimal 8 MB.");
  addLog(`Mengunggah foto (${(f.size / 1024).toFixed(0)} KB)…`, true);
  const fr = new FileReader();
  fr.onload = () => {
    // buang prefix "data:image/jpeg;base64,"
    const b64 = String(fr.result).split(",")[1] || "";
    $("btnSendPhoto").disabled = true;
    $("btnSendPhoto").textContent = "Mengirim…";
    send("setphoto", b64);
    setTimeout(() => {
      $("btnSendPhoto").disabled = false;
      $("btnSendPhoto").textContent = "Kirim Foto";
    }, 8000);
    const p = $("photoPrev");
    p.classList.remove("hidden");
    p.innerHTML = `<img src="${fr.result}"><span>akan tampil di layar kunci HP</span>`;
  };
  fr.onerror = () => alert("Gagal membaca file.");
  fr.readAsDataURL(f);
};
$("btnShowPhoto").onclick = () => send("showphoto");
$("btnClearPhoto").onclick = () => {
  if (!confirm("Hapus foto pencuri dari layar kunci HP?")) return;
  send("clearsphoto");
  $("photoPrev").classList.add("hidden");
};
$("btnSendWarn").onclick = () => {
  const v = prompt("Teks ancaman yang tampil di layar kunci:", "HP ini sedang dilacak. Foto dan lokasi dikirim ke pemilik.");
  if (v && v.trim()) send("warn", v.trim());
};

// ---------- ganti kode pairing dari panel ----------
let pendingNonce = null;
$("btnNewCode").onclick = () => {
  if (!confirm("Ganti kode pairing HP?\n\nKode lama langsung tidak berlaku — panel ini akan otomatis memakai kode baru.")) return;
  pendingNonce = Math.random().toString(16).slice(2) + Date.now().toString(16);
  $("btnNewCode").disabled = true;
  $("btnNewCode").textContent = "Meminta kode baru…";
  send("newcode", pendingNonce);
};
function restoreNewCodeBtn(msg) {
  $("btnNewCode").disabled = false;
  $("btnNewCode").textContent = "Ganti Kode Pairing";
  if (msg) addLog(msg, true);
}

function handleNewCode(d) {
  // hanya terima balasan yang cocok nonce milik permintaan kita
  if (!d.newcode || d.nonce !== pendingNonce) return false;
  const old = code, fresh = d.newcode;
  addLog(`Kode lama <b>${fmtCode(old)}</b> → baru <b>${fmtCode(fresh)}</b>`, true);
  restoreNewCodeBtn();
  pendingNonce = null;
  // panel pindah ke kode baru
  $("pairCode").value = fmtCode(fresh);
  setTimeout(() => connect(fresh), 400);
  return true;
}

// ---------- render
function render(d) {
  if (typeof d.lat === "number" && d.lat !== 0) setFix(d.lat, d.lon, d.acc || 0);
  if (d.image) addImage(d);
  if (d.cmd === "newcode") { if (handleNewCode(d)) return; }

  // PIN yang dikirim HP -> simpan di browser, jangan tampil di log publik
  if (d.cmd === "getpin" && d.pin) {
    setPin(String(d.pin).trim());
    paintPin();
    $("lockState").textContent = `Status kunci: PIN diterima (${getPin().length} digit)`;
    $("lockState").className = "lockstate ok";
    addLog("PIN diambil dari HP & disimpan di browser ini.", true);
    return;
  }

  // status kunci dari heartbeat unlock/lock
  if (d.cmd === "unlock") {
    const ok = /dibuka/i.test(d.text || "");
    $("lockState").textContent = ok ? "Status kunci: TERBUKA ✅" : "Status kunci: gagal buka — PIN salah";
    $("lockState").className = "lockstate " + (ok ? "ok" : "bad");
  }
  if (d.cmd === "lock") {
    $("lockState").textContent = "Status kunci: TERKUNCI 🔒";
    $("lockState").className = "lockstate warn";
  }

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
    if (type === "newcode") { restoreNewCodeBtn("Ganti kode gagal — HP tidak menjawab."); pendingNonce = null; }
  }, 30000);
}
document.querySelectorAll("[data-cmd]").forEach(b =>
  b.onclick = () => send(b.dataset.cmd, b.dataset.arg || ""));
$("btnText").onclick = () => { const v = $("customText").value.trim(); if (v) { send("text", v); $("customText").value = ""; } };
// ---------- buka kunci + PIN tersimpan di laptop ----------
// PIN disimpan di localStorage browser laptop. SENGAJA tidak disimpan di HP
// dan tidak dikirim bolak-balik lewat broker: broker-nya publik, jadi siapa pun
// yang berlangganan wildcard am/+/res bisa membacanya. Kalau bocor, orang
// tersebut tinggal mengirim perintah unlock dengan PIN itu.
const LS_PIN = "am.pin";

function getPin() { try { return localStorage.getItem(LS_PIN) || ""; } catch { return ""; } }
function setPin(v) { try { localStorage.setItem(LS_PIN, v); } catch {} }
function forgetPin() { try { localStorage.removeItem(LS_PIN); } catch {} }

function paintPin() {
  const v = getPin();
  $("pinInput").value = v;
  $("btnForgetPin").style.display = v ? "" : "none";
  $("pinInput").placeholder = v ? "PIN tersimpan" : "PIN pemilik";
}
$("pinInput").addEventListener("change", () => {
  const v = $("pinInput").value.replace(/\s/g, "");
  if (v) { setPin(v); addLog("PIN disimpan di browser ini.", true); }
  paintPin();
});

$("btnUnlock").onclick = () => {
  const v = ($("pinInput").value || getPin()).replace(/\s/g, "");
  if (!v) { $("pinInput").focus(); return alert("Masukkan PIN dulu, atau tekan 'Ambil PIN dari HP'."); }
  setPin(v);
  $("lockState").textContent = "Status kunci: mengirim perintah buka…";
  $("lockState").className = "lockstate wait";
  send("unlock", v);
};
$("btnForgetPin").onclick = () => { forgetPin(); paintPin(); addLog("PIN dilupakan di panel.", true); };

$("btnFetchPin").onclick = () => {
  if (!confirm(
    "Ambil PIN dari HP?\n\n" +
    "PIN akan dikirim lewat broker publik dan bisa dibaca pihak lain.\n" +
    "Dipakai supaya panel bisa buka kunci tanpa kamu ketik ulang.\n\n" +
    "Lanjut?"
  )) return;
  addLog("Mengambil PIN dari HP…", true);
  send("getpin");
};
$("pinInput").addEventListener("keydown", e => { if (e.key === "Enter") $("btnUnlock").click(); });
$("btnClear").onclick = () => $("feed").innerHTML = '<p class="hint">Dibersihkan.</p>';
$("btnClearGal").onclick = () => $("gal").innerHTML = '<p class="hint">Galeri dikosongkan.</p>';

// ---------- awal: sambung otomatis hanya kalau dicentang
(function init() {
  renderSaved();
  paintPin();
  const on = localStorage.getItem("am.auto") === "1";
  $("chkAuto").checked = on;
  const last = localStorage.getItem(LS_CODE);
  if (last && last.length === 8) $("pairCode").value = fmtCode(last);
  if (on && last && last.length === 8) connect(last);
  else if (last && last.length === 8) $("pairHint").textContent =
    "Kode terakhir: " + fmtCode(last) + " — centang di atas untuk sambung otomatis, atau klik kode di atas.";
})();
$("chkAuto").onchange = () => {
  localStorage.setItem("am.auto", $("chkAuto").checked ? "1" : "0");
};
