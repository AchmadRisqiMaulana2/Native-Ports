#!/usr/bin/env python3
"""
Feeding Frenzy Native Port — Oprek Studio, Live Simulator & One-Click APK / PAK Builder Server.

Binds strictly to 0.0.0.0:8080 and uses strictly relative browser URLs (`/api/...`) for full compatibility
with Arena Live Preview (`https://8080-<sandbox>.e2b.app`).
"""

from email.parser import BytesParser
from email.policy import default as EMAIL_DEFAULT_POLICY
import http.server
import json
import os
import subprocess
import sys
import threading
from pathlib import Path
from urllib.parse import urlparse

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR / "tools"))
import pak_tool  # noqa: E402

APK_PATH = ROOT_DIR / "FeedingFrenzy-Android9Plus-Oprek-debug.apk"
WORK_DIR = ROOT_DIR / "work"
UNPACKED_DIR = ROOT_DIR / "unpacked_dump"
BUILD_LOCK = threading.Lock()
LAST_IMPORT_REPORT: dict = {
    "loaded": False,
    "message": "Belum ada Feeding_Frenzy_Win_Preinstalled_EN.zip / main.pak yang diupload ke Studio. APK siap dimainkan langsung atau drop ZIP di sini untuk auto-bundle aset asli!",
}


HTML_PAGE = r"""<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
<title>Feeding Frenzy — Android 9+ Native Port & Oprek Studio</title>
<style>
  :root {
    --bg: #041529;
    --panel: rgba(8, 33, 64, 0.88);
    --border: rgba(56, 189, 248, 0.35);
    --gold: #ffb703;
    --cyan: #38bdf8;
    --mint: #34d399;
    --purple: #8b5cf6;
  }
  * { box-sizing: border-box; font-family: system-ui, -apple-system, sans-serif; }
  body {
    margin: 0; background: radial-gradient(circle at top, #0c4a6e 0%, #041529 70%);
    color: #f1f5f9; min-height: 100vh; padding: 14px;
  }
  .container { max-width: 1220px; margin: 0 auto; display: grid; grid-template-columns: 1fr 370px; gap: 14px; }
  @media (max-width: 960px) { .container { grid-template-columns: 1fr; } }
  .card {
    background: var(--panel); border: 1px solid var(--border);
    border-radius: 16px; padding: 14px; box-shadow: 0 12px 32px rgba(0,0,0,0.4);
  }
  .header-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
  h1 { margin: 0; font-size: 20px; color: var(--gold); letter-spacing: 0.4px; }
  .badge { background: rgba(52,211,153,0.18); color: #6ee7b7; border: 1px solid rgba(52,211,153,0.4); padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; }
  canvas {
    width: 100%; aspect-ratio: 16/9; border-radius: 12px; display: block;
    background: #062c59; border: 1px solid rgba(125,211,252,0.3); touch-action: none; cursor: crosshair;
  }
  .btn {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    border: none; border-radius: 11px; padding: 10px 14px; font-weight: 700; font-size: 13.5px;
    color: #fff; cursor: pointer; text-decoration: none; transition: transform 0.1s, filter 0.15s; width: 100%;
  }
  .btn:hover { filter: brightness(1.1); }
  .btn:active { transform: scale(0.98); }
  .btn-apk { background: linear-gradient(135deg, #059669, #10b981); font-size: 14.5px; padding: 12px; margin-bottom: 10px; }
  .btn-purple { background: linear-gradient(135deg, #6d28d9, #8b5cf6); }
  .btn-blue { background: linear-gradient(135deg, #0369a1, #0ea5e9); }
  .btn-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px; margin: 8px 0; }
  .dropzone {
    border: 2px dashed rgba(251, 191, 36, 0.55); border-radius: 12px; padding: 12px;
    text-align: center; background: rgba(251, 191, 36, 0.06); cursor: pointer; margin: 8px 0;
  }
  .dropzone:hover { background: rgba(251, 191, 36, 0.13); }
  label.toggle { display: flex; align-items: center; gap: 8px; font-size: 13px; margin: 6px 0; cursor: pointer; }
  .slider-group { margin: 8px 0; font-size: 12.5px; color: #bae6fd; }
  .slider-group input[type=range] { width: 100%; margin-top: 3px; accent-color: var(--cyan); }
  pre {
    background: #020b16; border: 1px solid rgba(56,189,248,0.2); border-radius: 10px;
    padding: 10px; font-size: 11.5px; color: #94a3b8; max-height: 185px; overflow: auto; white-space: pre-wrap; margin: 8px 0 0;
  }
  .hint { font-size: 12px; color: #94a3b8; margin-top: 6px; }
</style>
</head>
<body>
<div class="container">
  <div class="card">
    <div class="header-row">
      <div>
        <h1>🐟 FEEDING FRENZY — NATIVE ANDROID 9+ PORT & OPREK LAB</h1>
        <div class="hint">Java Core Engine + PAK XOR-0xF7 Unpacker • Kotlin Android 9+ UI/Audio • Live Simulator</div>
      </div>
      <span class="badge" id="apkBadge">APK READY • API 28–35</span>
    </div>

    <canvas id="ocean" width="1280" height="720"></canvas>
    <div class="hint">
      🎮 Gerakkan kursor / sentuh layar untuk berenang • Klik kiri / tahan untuk <b>BOOST</b> • Klik kanan / tombol <b>INHALE</b> untuk menyedot ikan kecil!
    </div>
  </div>

  <div class="card">
    <a class="btn btn-apk" href="api/download-apk" id="downloadBtn">⬇ Download Signed APK (Android 9+)</a>

    <div class="dropzone" onclick="document.getElementById('pakInput').click()" id="dropZone">
      <div style="font-weight:700;color:#fbbf24;font-size:13.5px;">📦 Upload / Drop Original ZIP atau main.pak</div>
      <div style="font-size:11.5px;color:#cbd5e1;margin-top:4px;">
        Drop <code>Feeding_Frenzy_Win_Preinstalled_EN.zip</code> (7.57 MB) di sini → otomatis ekstrak <code>main.pak</code> (XOR 0xF7) + rebuild APK dengan sprite & audio asli!
      </div>
      <input type="file" id="pakInput" accept=".zip,.pak,.exe" style="display:none" />
    </div>

    <div style="font-weight:700;color:#7dd3fc;font-size:13px;margin-top:10px;">⚡ LIVE OPREK / MOD MENU</div>
    <div class="btn-row">
      <button class="btn btn-blue" onclick="applyPreset('vanilla')">Vanilla</button>
      <button class="btn btn-purple" onclick="applyPreset('sultan')">🔥 Sultan</button>
      <button class="btn btn-purple" onclick="applyPreset('titan')">🐋 Titan</button>
    </div>
    <div class="btn-row">
      <button class="btn btn-blue" onclick="instantGrow()">⬆ Grow +1</button>
      <button class="btn btn-blue" onclick="inhaleNow()">🌀 Inhale</button>
      <button class="btn btn-blue" onclick="nextStage()">🌊 Stage +1</button>
    </div>

    <label class="toggle"><input type="checkbox" id="chkGod" /> 🛡️ God Mode (Kebal Hiu & Ranjau)</label>
    <label class="toggle"><input type="checkbox" id="chkApex" /> 🦈 Apex Eat-Anything (Makan Hiu Putih!)</label>
    <label class="toggle"><input type="checkbox" id="chkFrenzy" /> 🔥 Always Feeding Frenzy x8</label>
    <label class="toggle"><input type="checkbox" id="chkHitbox" /> 🎯 Tampilkan Hitbox & AI Vector</label>

    <div class="slider-group">
      Speed Multiplier: <b id="lblSpeed">1.00x</b>
      <input type="range" id="rngSpeed" min="50" max="300" value="100" />
    </div>
    <div class="slider-group">
      Food Magnet Radius: <b id="lblMagnet">0 px</b>
      <input type="range" id="rngMagnet" min="0" max="420" value="0" />
    </div>
    <div class="slider-group">
      Teleport Stage (1–40): <b id="lblStage">Stage 1 (Andy Angelfish)</b>
      <input type="range" id="rngStage" min="1" max="40" value="1" />
    </div>

    <pre id="statusLog">Memuat status build APK & PAK Inspector...</pre>
  </div>
</div>

<script>
const canvas = document.getElementById('ocean');
const ctx = canvas.getContext('2d');
const HEROES = [
  { name: 'Andy Angelfish', col: '#ffb018', acc: '#1a56db', tier: 1, r: 27 },
  { name: 'Leon Lionfish', col: '#ff5e3a', acc: '#ffe6d0', tier: 2, r: 34 },
  { name: 'Eddie Anglerfish', col: '#7e57c2', acc: '#80ff72', tier: 4, r: 42 },
  { name: 'J.D. John Dory', col: '#8ce99a', acc: '#2b8a3e', tier: 5, r: 46 },
  { name: 'Orville Orca', col: '#1e293b', acc: '#f8fafc', tier: 7, r: 58 }
];
const SPECIES = [
  { name: 'Minnow', tier: 1, r: 15, spd: 2.3, pts: 10, col: '#4ad8ff', acc: '#0e7cb8' },
  { name: 'Surgeonfish', tier: 2, r: 23, spd: 2.6, pts: 25, col: '#3377ff', acc: '#ffd426' },
  { name: 'Butterflyfish', tier: 3, r: 32, spd: 2.8, pts: 55, col: '#ffdf38', acc: '#222831' },
  { name: 'Tuna', tier: 5, r: 46, spd: 3.3, pts: 130, col: '#5ce1e6', acc: '#1d4ed8' },
  { name: 'Barracuda', tier: 7, r: 62, spd: 4.3, pts: 340, col: '#a5b4fc', acc: '#312e81' },
  { name: 'Great White Shark', tier: 9, r: 84, spd: 3.7, pts: 600, col: '#94a3b8', acc: '#e2e8f0' }
];

let oprek = { god: false, apex: false, frenzy: false, hitbox: false, speed: 1.0, magnet: 0 };
let stage = 1, score = 0, bank = 0, lives = 3, eaten = 0, growth = 0, frenzyMult = 1, frenzyBar = 0, shake = 0;
let pointer = { x: 640, y: 360, boost: false, inhale: 0 };
let player = { x: 640, y: 360, vx: 0, vy: 0, right: true, phase: 0, invuln: 90 };
let fishes = [], popups = [], mines = [];

function loadStage(s) {
  stage = Math.max(1, Math.min(40, s));
  eaten = 0; growth = 0; player.invuln = 90;
  fishes = []; mines = [];
  for (let i = 0; i < 18; i++) spawnFish(true);
  if (stage >= 4) {
    for (let m = 0; m < Math.min(4, Math.floor(stage / 6) + 1); m++) {
      mines.push({ x: 180 + Math.random() * 920, y: 150 + Math.random() * 430, r: 24, p: Math.random() * 6 });
    }
  }
  const h = HEROES[Math.min(4, Math.floor((stage - 1) / 8))];
  document.getElementById('rngStage').value = stage;
  document.getElementById('lblStage').textContent = `Stage ${stage} (${h.name})`;
}

function spawnFish(anyX) {
  const roll = Math.random();
  const idx = roll < 0.42 ? 0 : roll < 0.68 ? 1 : roll < 0.83 ? 2 : roll < 0.93 ? 3 : roll < 0.98 ? 4 : 5;
  const sp = SPECIES[idx];
  const fromLeft = Math.random() < 0.5;
  fishes.push({
    sp, x: anyX ? (Math.random() < 0.5 ? 90 + Math.random()*320 : 870 + Math.random()*320) : (fromLeft ? -90 : 1370),
    y: 85 + Math.random() * 545, vx: (fromLeft ? 1 : -1) * sp.spd, vy: (Math.random() - 0.5) * 1.2,
    right: fromLeft, phase: Math.random() * 6
  });
}

canvas.addEventListener('pointermove', e => {
  const r = canvas.getBoundingClientRect();
  pointer.x = (e.clientX - r.left) * (1280 / r.width);
  pointer.y = (e.clientY - r.top) * (720 / r.height);
});
canvas.addEventListener('pointerdown', e => {
  const r = canvas.getBoundingClientRect();
  pointer.x = (e.clientX - r.left) * (1280 / r.width);
  pointer.y = (e.clientY - r.top) * (720 / r.height);
  pointer.boost = true;
});
window.addEventListener('pointerup', () => pointer.boost = false);

function heroInfo() { return HEROES[Math.min(4, Math.floor((stage - 1) / 8))]; }
function playerTier() { return oprek.apex ? 99 : heroInfo().tier + growth * 2; }
function playerRadius() { return heroInfo().r * (1 + growth * 0.34); }

function drawFishShape(x, y, r, col, acc, right, phase, isPlayer) {
  ctx.save(); ctx.translate(x, y); if (!right) ctx.scale(-1, 1);
  const wag = Math.sin(phase) * r * 0.22;
  ctx.fillStyle = acc;
  ctx.beginPath(); ctx.moveTo(-r*0.7, 0); ctx.lineTo(-r*1.32, -r*0.6+wag); ctx.lineTo(-r*1.04, wag*0.3); ctx.lineTo(-r*1.32, r*0.6+wag); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(-r*0.3, -r*0.55); ctx.lineTo(r*0.05, -r*1.05); ctx.lineTo(r*0.36, -r*0.52); ctx.closePath(); ctx.fill();
  ctx.fillStyle = col;
  ctx.beginPath(); ctx.ellipse(0, 0, r*0.94, r*0.68, 0, 0, Math.PI*2); ctx.fill();
  ctx.strokeStyle = acc; ctx.lineWidth = Math.max(2.5, r*0.1);
  ctx.beginPath(); ctx.moveTo(-r*0.18, -r*0.52); ctx.lineTo(-r*0.26, r*0.52); ctx.moveTo(r*0.12, -r*0.5); ctx.lineTo(r*0.04, r*0.5); ctx.stroke();
  const er = Math.max(4, r*0.2);
  ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(r*0.46, -r*0.18, er, 0, Math.PI*2); ctx.fill();
  ctx.fillStyle = '#0f172a'; ctx.beginPath(); ctx.arc(r*0.51, -r*0.18, er*0.52, 0, Math.PI*2); ctx.fill();
  if (isPlayer) { ctx.fillStyle = '#69f0ae'; ctx.beginPath(); ctx.arc(0, -r*1.2, 5, 0, Math.PI*2); ctx.fill(); }
  ctx.restore();
}

let t = 0;
function loop() {
  t += 0.016;
  const h = heroInfo(), pr = playerRadius(), pt = playerTier(), goal = 24 + stage * 6;
  const dx = pointer.x - player.x, dy = pointer.y - player.y, d = Math.hypot(dx, dy) || 1;
  const spd = (4.8 * oprek.speed) * (pointer.boost ? 1.55 : 1);
  if (d > 6) { player.vx += ((dx/d)*spd - player.vx)*0.16; player.vy += ((dy/d)*spd - player.vy)*0.16; }
  player.x = Math.max(pr, Math.min(1280-pr, player.x + player.vx));
  player.y = Math.max(66, Math.min(660, player.y + player.vy));
  if (Math.abs(player.vx) > 0.3) player.right = player.vx > 0;
  player.phase += 0.22;
  if (player.invuln > 0) player.invuln--;
  if (pointer.inhale > 0) pointer.inhale--;
  if (oprek.frenzy) { frenzyBar = 100; frenzyMult = 8; } else { frenzyBar = Math.max(0, frenzyBar - 0.18); frenzyMult = frenzyBar > 80 ? 6 : frenzyBar > 50 ? 4 : frenzyBar > 25 ? 2 : 1; }

  const grad = ctx.createLinearGradient(0, 0, 0, 720);
  grad.addColorStop(0, '#1496d4'); grad.addColorStop(1, '#052246');
  ctx.fillStyle = grad; ctx.fillRect(0, 0, 1280, 720);

  // Light shafts + seabed
  ctx.fillStyle = 'rgba(186,230,253,0.06)';
  for (let i = 0; i < 5; i++) {
    const sx = 160 + i*240 + Math.sin(t + i)*35;
    ctx.beginPath(); ctx.moveTo(sx-35, 60); ctx.lineTo(sx+40, 60); ctx.lineTo(sx+130, 720); ctx.lineTo(sx-90, 720); ctx.fill();
  }
  ctx.fillStyle = '#8d6e53'; ctx.fillRect(0, 668, 1280, 52);

  const mag = Math.max(oprek.magnet, pointer.inhale > 0 ? 300 : 0);
  if (mag > 0) { ctx.strokeStyle = 'rgba(114,239,221,0.35)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(player.x, player.y, mag, 0, Math.PI*2); ctx.stroke(); }

  while (fishes.length < 20) spawnFish(false);
  for (let i = fishes.length - 1; i >= 0; i--) {
    const f = fishes[i];
    const fdx = player.x - f.x, fdy = player.y - f.y, fd = Math.hypot(fdx, fdy) || 1;
    const edible = pt >= f.sp.tier;
    if (edible && mag > 0 && fd < mag) { f.vx = (fdx/fd)*6.5; f.vy = (fdy/fd)*6.5; }
    f.x += f.vx; f.y = Math.max(72, Math.min(655, f.y + f.vy)); f.right = f.vx >= 0; f.phase += 0.2;
    if (f.x < -160 || f.x > 1440) { fishes.splice(i, 1); continue; }
    drawFishShape(f.x, f.y, f.sp.r, f.sp.col, f.sp.acc, f.right, f.phase, false);
    if (oprek.hitbox) {
      ctx.strokeStyle = edible ? '#4ade80' : '#ef4444'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(f.x, f.y, f.sp.r*0.76, 0, Math.PI*2); ctx.stroke();
    }
    if (fd < (pr + f.sp.r) * 0.76) {
      if (edible) {
        const gain = f.sp.pts * frenzyMult;
        score += gain; bank += f.sp.tier; eaten += f.sp.tier; frenzyBar = Math.min(100, frenzyBar + 26);
        popups.push({ x: f.x, y: f.y, txt: '+' + gain, life: 45, col: '#fde047' });
        fishes.splice(i, 1);
        if (growth === 0 && eaten >= goal/3) { growth = 1; popups.push({ x: player.x, y: player.y-40, txt: 'GROWTH! MEDIUM!', life: 65, col: '#69f0ae' }); }
        if (growth === 1 && eaten >= (goal*2)/3) { growth = 2; popups.push({ x: player.x, y: player.y-40, txt: 'GROWTH! LARGE!', life: 65, col: '#69f0ae' }); }
        if (eaten >= goal) { popups.push({ x: 640, y: 320, txt: `STAGE ${stage} CLEARED!`, life: 80, col: '#69f0ae' }); loadStage(stage + 1); break; }
      } else if (!oprek.god && player.invuln <= 0) {
        lives = Math.max(1, lives - 1); player.invuln = 110;
        popups.push({ x: player.x, y: player.y-35, txt: 'OUCH! CHOMPED!', life: 60, col: '#ff5252' });
      }
    }
  }

  for (const m of mines) {
    m.p += 0.05;
    ctx.fillStyle = '#1e293b'; ctx.beginPath(); ctx.arc(m.x, m.y, m.r, 0, Math.PI*2); ctx.fill();
    ctx.fillStyle = Math.sin(m.p*3) > 0 ? '#ef4444' : '#7f1d1d'; ctx.beginPath(); ctx.arc(m.x, m.y, 6, 0, Math.PI*2); ctx.fill();
  }

  if (player.invuln % 6 < 4) drawFishShape(player.x, player.y, pr, h.col, h.acc, player.right, player.phase, true);
  if (oprek.god) { ctx.strokeStyle = '#ffd54f'; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(player.x, player.y, pr*1.25, 0, Math.PI*2); ctx.stroke(); }

  for (let i = popups.length - 1; i >= 0; i--) {
    const p = popups[i]; p.y -= 1.1; p.life--;
    ctx.fillStyle = p.col; ctx.font = 'bold 21px sans-serif'; ctx.textAlign = 'center'; ctx.fillText(p.txt, p.x, p.y);
    if (p.life <= 0) popups.splice(i, 1);
  }

  // Top HUD Bar
  ctx.fillStyle = 'rgba(4,21,41,0.82)'; ctx.fillRect(0, 0, 1280, 58);
  ctx.fillStyle = '#ffd54f'; ctx.font = 'bold 21px sans-serif'; ctx.textAlign = 'left';
  ctx.fillText(`SCORE ${score}`, 20, 26);
  ctx.fillStyle = '#bae6fd'; ctx.font = 'bold 14px sans-serif';
  ctx.fillText(`Stage ${stage}/40 (${h.name})  •  ❤ x${lives}  •  Food Bank ${bank}`, 20, 47);

  ctx.fillStyle = '#0f172a'; ctx.fillRect(520, 26, 330, 18);
  ctx.fillStyle = frenzyMult >= 2 ? '#ffb703' : '#38bdf8';
  ctx.fillRect(520, 26, 330 * Math.min(1, eaten / goal), 18);
  ctx.fillStyle = '#fff'; ctx.font = 'bold 12px sans-serif';
  ctx.fillText(`GROWTH BAR: ${eaten}/${goal}`, 520, 19);

  if (frenzyMult >= 2) {
    ctx.fillStyle = '#fde047'; ctx.font = 'bold 25px sans-serif'; ctx.textAlign = 'right';
    ctx.fillText(`🔥 FEEDING FRENZY x${frenzyMult}!`, 1255, 38);
  }
  requestAnimationFrame(loop);
}

function syncChecks() {
  document.getElementById('chkGod').checked = oprek.god;
  document.getElementById('chkApex').checked = oprek.apex;
  document.getElementById('chkFrenzy').checked = oprek.frenzy;
  document.getElementById('chkHitbox').checked = oprek.hitbox;
  document.getElementById('rngSpeed').value = Math.round(oprek.speed * 100);
  document.getElementById('lblSpeed').textContent = oprek.speed.toFixed(2) + 'x';
  document.getElementById('rngMagnet').value = oprek.magnet;
  document.getElementById('lblMagnet').textContent = oprek.magnet + ' px';
}
window.applyPreset = function(p) {
  if (p === 'vanilla') oprek = { god: false, apex: false, frenzy: false, hitbox: false, speed: 1.0, magnet: 0 };
  if (p === 'sultan') oprek = { god: true, apex: false, frenzy: true, hitbox: false, speed: 1.65, magnet: 260 };
  if (p === 'titan') oprek = { god: true, apex: true, frenzy: true, hitbox: true, speed: 1.45, magnet: 340 };
  syncChecks();
};
window.instantGrow = function() { if (growth < 2) growth++; else loadStage(stage + 1); };
window.inhaleNow = function() { pointer.inhale = 45; };
window.nextStage = function() { loadStage(stage + 1); };

document.getElementById('chkGod').onchange = e => oprek.god = e.target.checked;
document.getElementById('chkApex').onchange = e => oprek.apex = e.target.checked;
document.getElementById('chkFrenzy').onchange = e => oprek.frenzy = e.target.checked;
document.getElementById('chkHitbox').onchange = e => oprek.hitbox = e.target.checked;
document.getElementById('rngSpeed').oninput = e => { oprek.speed = e.target.value / 100; syncChecks(); };
document.getElementById('rngMagnet').oninput = e => { oprek.magnet = +e.target.value; syncChecks(); };
document.getElementById('rngStage').oninput = e => loadStage(+e.target.value);

async function refreshStatus() {
  try {
    const res = await fetch('api/status');
    const data = await res.json();
    document.getElementById('apkBadge').textContent = `APK ${data.apk_size_kb} KB • Android 9+ Ready`;
    document.getElementById('downloadBtn').textContent = `⬇ Download APK (${data.apk_size_kb} KB • Signed v1+v2+v3)`;
    document.getElementById('statusLog').textContent = JSON.stringify(data, null, 2);
  } catch (err) {
    document.getElementById('statusLog').textContent = 'Error status: ' + err;
  }
}

async function uploadArchive(file) {
  const log = document.getElementById('statusLog');
  log.textContent = `⏳ Mengupload ${file.name} (${(file.size/1024/1024).toFixed(2)} MB), mendekripsi XOR 0xF7 main.pak, & me-rebuild Signed APK...`;
  const fd = new FormData();
  fd.append('file', file);
  const res = await fetch('api/upload-pak', { method: 'POST', body: fd });
  const data = await res.json();
  log.textContent = JSON.stringify(data, null, 2);
  await refreshStatus();
}

document.getElementById('pakInput').onchange = e => { if (e.target.files[0]) uploadArchive(e.target.files[0]); };
const dz = document.getElementById('dropZone');
dz.ondragover = e => { e.preventDefault(); };
dz.ondrop = e => { e.preventDefault(); if (e.dataTransfer.files[0]) uploadArchive(e.dataTransfer.files[0]); };

loadStage(1);
refreshStatus();
loop();
</script>
</body>
</html>
"""


class OprekHandler(http.server.BaseHTTPRequestHandler):
    def do_HEAD(self) -> None:
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

    def do_OPTIONS(self) -> None:
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"

        if path == "/":
            body = HTML_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if path.endswith("/api/status"):
            payload = {
                "project": "Feeding Frenzy Native Android Port (Java + Kotlin)",
                "min_android_sdk": "API 28 (Android 9.0 Pie+)",
                "target_android_sdk": "API 34",
                "apk_exists": APK_PATH.exists(),
                "apk_filename": APK_PATH.name,
                "apk_size_kb": round(APK_PATH.stat().st_size / 1024, 1) if APK_PATH.exists() else 0,
                "original_pak_status": LAST_IMPORT_REPORT,
            }
            self._send_json(200, payload)
            return

        if path.endswith("/api/download-apk"):
            if not APK_PATH.exists():
                self._send_json(404, {"error": "APK not built yet"})
                return
            apk_bytes = APK_PATH.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Disposition", f'attachment; filename="{APK_PATH.name}"')
            self.send_header("Content-Length", str(len(apk_bytes)))
            self.end_headers()
            self.wfile.write(apk_bytes)
            return

        self._send_json(404, {"error": f"Unknown route: {path}"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.endswith("/api/upload-pak"):
            self._send_json(404, {"error": "Unknown POST endpoint"})
            return

        content_type = self.headers.get("Content-Type", "")
        content_len = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(content_len) if content_len > 0 else b""

        header_bytes = f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode("utf-8")
        msg = BytesParser(policy=EMAIL_DEFAULT_POLICY).parsebytes(header_bytes + body)

        filename = None
        raw_bytes = None
        for part in msg.iter_parts():
            if part.get_param("name", header="content-disposition") == "file":
                filename = os.path.basename(part.get_filename() or "Feeding_Frenzy_Win_Preinstalled_EN.zip")
                raw_bytes = part.get_payload(decode=True)
                break

        if raw_bytes is None:
            self._send_json(400, {"error": "Missing file upload field"})
            return

        WORK_DIR.mkdir(parents=True, exist_ok=True)
        saved_path = WORK_DIR / filename
        saved_path.write_bytes(raw_bytes)

        with BUILD_LOCK:
            try:
                pak_bytes, exe_bytes, label = pak_tool.load_pak_bytes(str(saved_path))
                _, version, records = pak_tool.parse_pak(pak_bytes)
                cats: dict[str, int] = {}
                for r in records:
                    cats[r.category] = cats.get(r.category, 0) + 1

                proc = subprocess.run(
                    ["bash", str(ROOT_DIR / "scripts/import-original-zip.sh"), str(saved_path)],
                    cwd=str(ROOT_DIR),
                    capture_output=True,
                    text=True,
                    timeout=180,
                )
                global LAST_IMPORT_REPORT
                LAST_IMPORT_REPORT = {
                    "loaded": proc.returncode == 0,
                    "source_file": filename,
                    "pak_label": label,
                    "entry_count": len(records),
                    "categories": cats,
                    "exe_info": pak_tool.inspect_exe_bytes(exe_bytes) if exe_bytes else None,
                    "rebuild_exit_code": proc.returncode,
                    "new_apk_size_kb": round(APK_PATH.stat().st_size / 1024, 1) if APK_PATH.exists() else 0,
                }
                self._send_json(200, LAST_IMPORT_REPORT)
            except Exception as exc:
                self._send_json(400, {"loaded": False, "error": str(exc)})

    def _send_json(self, code: int, obj: dict) -> None:
        raw = json.dumps(obj, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt: str, *args) -> None:
        sys.stdout.write("[oprek-studio] " + (fmt % args) + "\n")
        sys.stdout.flush()


def main() -> None:
    port = int(os.environ.get("PORT", "8080"))
    server = http.server.ThreadingHTTPServer(("0.0.0.0", port), OprekHandler)
    print(f"Feeding Frenzy Oprek Studio listening on http://0.0.0.0:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
