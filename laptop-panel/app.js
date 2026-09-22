import { initializeApp } from "firebase/app";
import { getAuth, signInAnonymously, onAuthStateChanged } from "firebase/auth";
import { getFirestore, doc, collection, addDoc, onSnapshot, query, orderBy, limit } from "firebase/firestore";
import { firebaseConfig } from "./firebase-config.js";

const $ = id => document.getElementById(id);
const feed = $("feed");
let db = null, aid = null, unsubs = [];

// ---------- pairing ----------
// Kode pairing 6 digit -> cari devices/{aid} yang field pair-nya cocok.
// Karena rules butuh auth, scan dibatasi: coba listen doc devices/pair_{kode} (pointer ditulis HP).
$("btnConnect").onclick = async () => {
  const code = $("pairCode").value.trim();
  if (!/^\d{6}$/.test(code)) return alert("Kode harus 6 digit.");
  try {
    if (firebaseConfig.apiKey.includes("PASTE")) return alert("Isi firebase-config.js dulu!");
    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    await signInAnonymously(auth);
    db = getFirestore(app);
    onAuthStateChanged(auth, u => {
      $("dot").className = "dot " + (u ? "on" : "off");
      $("connText").textContent = u ? "Tersambung (anonim)" : "Belum tersambung";
    });
    // pointer: devices/pair_{kode} = { aid }
    const ptr = await new Promise((res, rej) => {
      const un = onSnapshot(doc(db, "devices", "pair_" + code),
        s => { un(); s.exists() ? res(s.data()) : rej(new Error("Kode tidak dikenal. Generate ulang di app HP.")); },
        e => { un(); rej(e); });
      setTimeout(() => { try { un(); } catch {} rej(new Error("Timeout.")); }, 12000);
    });
    aid = ptr.aid;
    startDash(code);
  } catch (e) { alert("Gagal: " + (e.message || e)); }
};

function startDash(code) {
  $("pairCard").classList.add("hidden");
  $("dash").classList.remove("hidden");
  $("devId").textContent = aid + " • pairing " + code;

  // status online via heartbeat
  unsubs.push(onSnapshot(doc(db, "devices", aid), s => {
    const d = s.data() || {};
    const last = parseInt(d.lastSeen || "0");
    const online = Date.now() - last < 30000;
    const el = $("online");
    el.textContent = online ? "● online" : "○ offline";
    el.className = "pill" + (online ? "" : " off");
    $("devInfo").textContent = (d.model ? d.model + " • " : "") +
      "terakhir aktif " + (last ? new Date(last).toLocaleString("id-ID") : "-");
  }));

  // hasil realtime
  const q = query(collection(db, "devices", aid, "outbox"), orderBy("ts", "desc"), limit(20));
  let first = true;
  unsubs.push(onSnapshot(q, snap => {
    if (first) { first = false; feed.innerHTML = ""; }
    snap.docChanges().forEach(ch => {
      if (ch.type === "added") render(ch.doc.data());
    });
    if (!feed.children.length) feed.innerHTML = '<p class="hint">Belum ada hasil.</p>';
  }));
}

function render(d) {
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

// ---------- kirim perintah ----------
async function send(type, arg = "") {
  if (!db || !aid) return alert("Sambungkan dulu.");
  await addDoc(collection(db, "devices", aid, "inbox"), {
    type, arg: String(arg), ts: Date.now().toString(), from: "panel", status: "pending"
  });
  toast(`Perintah ${type} terkirim — tunggu hasil di bawah (±10 dtk).`);
}
document.querySelectorAll("[data-cmd]").forEach(b =>
  b.onclick = () => send(b.dataset.cmd, b.dataset.arg || ""));
$("btnText").onclick = () => { const v = $("customText").value.trim(); if (v) { send("text", v); $("customText").value = ""; } };
$("btnUnlock").onclick = () => { const v = $("pinInput").value.trim(); if (v) send("unlock", v); };
$("btnClear").onclick = () => feed.innerHTML = '<p class="hint">Dibersihkan.</p>';

function toast(m) {
  const div = document.createElement("div");
  div.className = "item"; div.innerHTML = `<p>📤 ${esc(m)}</p>`;
  feed.prepend(div);
}
