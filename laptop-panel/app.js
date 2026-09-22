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
$("btnConnect").onclick = () => connectByCode(false);
$("btnDirect").onclick = async () => {
  const id = $("aidInput").value.trim();
  if (!id) return alert("Isi ID perangkat dulu (lihat di app HP bawah kode pairing).");
  try {
    if (firebaseConfig.apiKey.includes("PASTE")) return alert("Isi firebase-config.js dulu!");
    ensureApp();
    aid = id;
    startDash("langsung");
  } catch (e) { alert("Gagal: " + (e.message || e)); }
};

let appInited = false;
function ensureApp() {
  if (!appInited) {
    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    signInAnonymously(auth).catch(e => alert("Auth gagal: " + (e.message || e)));
    db = getFirestore(app);
    onAuthStateChanged(auth, u => {
      $("dot").className = "dot " + (u ? "on" : "off");
      $("connText").textContent = u ? "Tersambung (anonim)" : "Belum tersambung";
    });
    appInited = true;
  }
}

// retry otomatis 3x (tunggu HP heartbeat tiap 10 dtk)
async function connectByCode(retry) {
  const code = $("pairCode").value.trim();
  if (!/^\d{6}$/.test(code)) return alert("Kode harus 6 digit.");
  try {
    if (firebaseConfig.apiKey.includes("PASTE")) return alert("Isi firebase-config.js dulu!");
    ensureApp();
    toast("Mencari HP" + (retry ? " (coba lagi…)" : "") + " — pastikan Tes Koneksi Cloud hijau di HP.");
    const ptr = await lookupPointer(code);
    aid = ptr.aid;
    startDash(code);
  } catch (e) {
    if (!retry) {
      toast("Belum ketemu, coba lagi otomatis dalam 12 detik…");
      setTimeout(() => connectByCode(true), 12000);
    } else {
      alert("Gagal: " + (e.message || e) +
        "\n\nSolusi: 1) di HP klik Tes Koneksi Cloud sampai Online ✅ " +
        "2) klik Generate Ulang Kode Pairing, masukkan kode baru di sini " +
        "3) atau pakai Sambung via ID.");
    }
  }
}

function lookupPointer(code) {
  return new Promise((res, rej) => {
    const un = onSnapshot(doc(db, "devices", "pair_" + code),
      s => { un(); s.exists() ? res(s.data()) : rej(new Error("Kode tidak dikenal di database.")); },
      e => { un(); rej(e); });
    setTimeout(() => { try { un(); } catch {} rej(new Error("Timeout.")); }, 12000);
  });
}

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

// ---------- scan QR dari HP ----------
let scanOn = false, stream = null;
$("btnScan").onclick = async () => {
  try {
    if (typeof globalThis.jsQR !== "function") return alert("Library scan belum termuat (butuh internet sekali).");
    ensureApp();
    $("scanBox").classList.remove("hidden");
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" } });
    const v = $("cam");
    v.srcObject = stream;
    await v.play();
    scanOn = true;
    scanLoop();
  } catch (e) { alert("Kamera gagal: " + (e.message || e)); }
};
$("btnScanStop").onclick = stopScan;
function stopScan() {
  scanOn = false;
  try { stream?.getTracks().forEach(t => t.stop()); } catch {}
  stream = null;
  $("scanBox").classList.add("hidden");
}
function scanLoop() {
  if (!scanOn) return;
  try {
    const v = $("cam"), c = $("cap");
    if (v.readyState === v.HAVE_ENOUGH_DATA) {
      c.width = v.videoWidth; c.height = v.videoHeight;
      const ctx = c.getContext("2d");
      ctx.drawImage(v, 0, 0, c.width, c.height);
      const img = ctx.getImageData(0, 0, c.width, c.height);
      const res = globalThis.jsQR(img.data, c.width, c.height);
      if (res?.data) { onQr(res.data); return; }
    }
  } catch {}
  requestAnimationFrame(scanLoop);
}
function onQr(data) {
  // format HP: AM1|pairCode|aid
  const p = String(data).trim().split("|");
  stopScan();
  if (p.length === 3 && p[0] === "AM1") {
    $("pairCode").value = p[1];
    toast("QR terbaca ✅ kode " + p[1] + " — menyambungkan…");
    connectByCode(false);
  } else {
    alert("QR tidak dikenal: " + data);
  }
}

function toast(m) {
  const div = document.createElement("div");
  div.className = "item"; div.innerHTML = `<p>📤 ${esc(m)}</p>`;
  feed.prepend(div);
}
