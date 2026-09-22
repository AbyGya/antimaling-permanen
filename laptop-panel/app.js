// Panel AntiMaling via MQTT publik — tanpa daftar, tanpa API key.
// HP subscribe am/{kode}/cmd, panel subscribe am/{kode}/res + am/{kode}/status.
const $ = id => document.getElementById(id);
const feed = $("feed");
const BROKER = "wss://broker.emqx.io:8084/mqtt";
let client = null, code = null, lastSeen = 0, onlineTimer = null;

$("btnConnect").onclick = () => {
  const c = $("pairCode").value.replace(/\D/g, "");
  if (c.length !== 6) return alert("Kode harus 6 digit angka.");
  connect(c);
};

function setConn(on, txt) {
  $("dot").className = "dot " + (on ? "on" : "off");
  $("connText").textContent = txt;
}

function connect(c) {
  try { client?.end(true); } catch {}
  code = c;
  clearInterval(onlineTimer);
  setConn(false, "Menghubungkan…");
  toast("Menghubungkan ke kode " + c + "…");
  client = mqtt.connect(BROKER, {
    clientId: "am-panel-" + Math.random().toString(16).slice(2, 10),
    clean: true, reconnectPeriod: 3000, connectTimeout: 8000
  });
  client.on("connect", () => {
    setConn(true, "Tersambung ke broker");
    client.subscribe([`am/${code}/res`, `am/${code}/status`], { qos: 1 }, err => {
      if (err) { alert("Subscribe gagal: " + err.message); return; }
      toast("Menunggu HP… (buka app HP 1x bila perlu)");
      startDash();
      // sapa HP: minta status
      send("ping");
    });
  });
  client.on("message", (t, p) => {
    try {
      if (t === `am/${code}/status`) {
        const v = p.toString();
        if (v === "offline") return setOffline();
        lastSeen = parseInt(v) || Date.now();
        return setOnline();
      }
      if (t === `am/${code}/res`) {
        const d = JSON.parse(p.toString());
        if (d.ts) lastSeen = parseInt(d.ts) || lastSeen;
        setOnline();
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

function startDash() {
  $("pairCard").classList.add("hidden");
  $("dash").classList.remove("hidden");
  $("devId").textContent = code;
  feed.innerHTML = '<p class="hint">Tersambung. Klik perintah di atas.</p>';
}

function render(d) {
  if (!feed.querySelector(".item")) feed.innerHTML = "";
  const div = document.createElement("div");
  div.className = "item";
  const t = d.ts ? new Date(parseInt(d.ts)).toLocaleString("id-ID") : "";
  const kind = (d.kind === "image" ? "🖼" : "💬") + " <b>" + esc(d.cmd || "") + "</b> • " + t;
  let html = `<div class="meta">${kind}</div><p>${esc(d.text || "")}</p>`;
  if (d.image) html += `<img src="data:image/jpeg;base64,${d.image}" loading="lazy">`;
  div.innerHTML = html;
  feed.prepend(div);
  while (feed.children.length > 20) feed.lastChild.remove();
}
const esc = s => String(s).replace(/[&<>"]/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[m]));

function send(type, arg = "") {
  if (!client || !code) return alert("Sambungkan dulu.");
  client.publish(`am/${code}/cmd`, JSON.stringify({
    id: Math.random().toString(16).slice(2), type, arg: String(arg), ts: Date.now().toString()
  }), { qos: 1 });
}
document.querySelectorAll("[data-cmd]").forEach(b =>
  b.onclick = () => {
    send(b.dataset.cmd, b.dataset.arg || "");
    toast(`Perintah ${b.dataset.cmd} terkirim — hasil muncul di bawah.`);
  });
$("btnText").onclick = () => { const v = $("customText").value.trim(); if (v) { send("text", v); $("customText").value = ""; } };
$("btnUnlock").onclick = () => { const v = $("pinInput").value.trim(); if (v) send("unlock", v); };
$("btnClear").onclick = () => feed.innerHTML = '<p class="hint">Dibersihkan.</p>';

function toast(m) {
  if (!feed.querySelector(".item")) feed.innerHTML = "";
  const div = document.createElement("div");
  div.className = "item"; div.innerHTML = `<p>📤 ${esc(m)}</p>`;
  feed.prepend(div);
}
