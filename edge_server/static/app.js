/* BlindSight voice-first PWA engine (v2 - reliable capture edition).
   Fixes the four field failures of v1:
   1. Chrome ends continuous recognition after silence; v1 only restarted from
      'idle', so the wake listener died seconds after opening. Now every
      recognition end schedules a guarded restart with backoff.
   2. Background noise tripped the wake word through interim results. A live
      energy gate (getUserMedia + AnalyserNode with noiseSuppression) computes
      a calibrated noise floor; wake matches are only accepted while real
      speech energy is present, or on a confident final result.
   3. The browser's default TTS voice is a robot. Voices are enumerated and
      "Natural"/neural voices are preferred; the user can pick one and the
      choice persists.
   4. "Auto detect voice, not clicking": a hands-free mode routes everything
      you say (no wake word), segmented by silence, while the energy gate
      keeps room noise out.
   The screen stays a secondary display: earcons + assertive announcements for
   every state change, as before. */
'use strict';

const $ = (id) => document.getElementById(id);

const WAKE_PHRASES = ['hey blindsight', 'hey blind sight', 'ok blindsight', 'okay blindsight', 'blindsight'];
const STOP_WORDS = ['stop', 'be quiet', 'quiet', 'cancel', 'shush'];
const HELP_WORDS = ['help', 'what can i say', 'commands'];
const STATUS_WORDS = ['read status', 'system status', 'status report'];
const SCENE_WORDS = ['describe', 'describe the scene', 'what is around me', 'what\'s around me', 'what is in front of me', 'look for me'];

const SPEECH_LANG = { en: 'en-IN', hi: 'hi-IN', bn: 'bn-IN', ta: 'ta-IN', te: 'te-IN', es: 'es-ES', fr: 'fr-FR' };
const LANG_CONFIRM = {
  en: 'Language set to English.',
  hi: 'भाषा हिंदी पर सेट हो गई।',
  bn: 'ভাষা বাংলায় সেট করা হয়েছে।',
  ta: 'மொழி தமிழக்கு அமைக்கப்பட்டது.',
  te: 'భాష తెలుగుకు సెట్ చేయబడింది.',
  es: 'Idioma cambiado a español.',
  fr: 'Langue réglée sur le français.'
};
const HELP_TEXT =
  'Say hey BlindSight, then ask your question. Or switch to hands-free mode and ' +
  'just talk. Commands: describe, for a scene description. what do you remember. ' +
  'remember, followed by a fact. forget. language, followed by a language, like ' +
  'hindi. read status. clear chat. stop. help.';

const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
const synth = window.speechSynthesis || null;

let appState = 'idle';         // idle | wake | listen | think | speak
let listenMode = localStorage.getItem('blindsight_mode') || 'wake';  // wake | handsfree | button
let language = 'en';
let chosenVoiceURI = localStorage.getItem('blindsight_voice') || '';
let authToken = localStorage.getItem('blindsight_token') || '';
let wakeRec = null;
let cmdRec = null;
let restartDelay = 300;
let lastRecEnd = 0;
let audioCtx = null;

/* ---------------------------------------------------- mic energy / noise gate */
let analyser = null;
let levelBuf = null;
let noiseFloor = 0.02;
let micLevel = 0;
let speechWindow = [];         // timestamps of frames whose energy beat the floor
let calibrating = false;

async function openMicAnalytics() {
  if (analyser || !navigator.mediaDevices) return true;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { noiseSuppression: true, echoCancellation: true, autoGainControl: true }
    });
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') await audioCtx.resume();
    const source = audioCtx.createMediaStreamSource(stream);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 1024;
    source.connect(analyser);
    levelBuf = new Uint8Array(analyser.fftSize);
    calibrating = true;
    window.setInterval(sampleMicLevel, 100);
    window.setTimeout(() => { calibrating = false; }, 1500);
    return true;
  } catch (err) {
    analyser = null;
    return false;
  }
}

function sampleMicLevel() {
  if (!analyser) return;
  analyser.getByteTimeDomainData(levelBuf);
  let sum = 0;
  for (let i = 0; i < levelBuf.length; i++) {
    const v = (levelBuf[i] - 128) / 128;
    sum += v * v;
  }
  const rms = Math.sqrt(sum / levelBuf.length);
  micLevel = rms;
  if (calibrating) {
    // Background-noise cancel: learn the room, then require speech to sit
    // clearly above it instead of reacting to fans and traffic.
    noiseFloor = Math.max(noiseFloor * 0.9 + rms * 0.1, 0.004);
  } else if (rms > noiseFloor * 2.5 + 0.012) {
    speechWindow.push(Date.now());
  }
  while (speechWindow.length && Date.now() - speechWindow[0] > 1200) speechWindow.shift();
  paintMeter();
}

function speechRecently() {
  return speechWindow.length > 0;
}

function paintMeter() {
  const meter = $('mic-meter');
  if (!meter) return;
  const gate = noiseFloor * 2.5 + 0.012;
  const pct = Math.min(100, (micLevel / Math.max(gate * 3, 0.06)) * 100);
  meter.style.width = pct.toFixed(0) + '%';
  meter.classList.toggle('hot', micLevel > gate);
}

/* ------------------------------------------------------------------ earcons */
function earcon(kind) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume();
    const seq = {
      wake: [[880, 0.09], [1320, 0.12]],
      listen: [[660, 0.1]],
      think: [[520, 0.08], [520, 0.08]],
      ok: [[740, 0.08], [980, 0.12]],
      error: [[330, 0.12], [220, 0.16]],
      tick: [[990, 0.04]]
    }[kind] || [[990, 0.04]];
    let at = audioCtx.currentTime;
    for (const [freq, dur] of seq) {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.frequency.value = freq;
      osc.type = 'sine';
      gain.gain.setValueAtTime(0.0001, at);
      gain.gain.exponentialRampToValueAtTime(0.18, at + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, at + dur);
      osc.connect(gain).connect(audioCtx.destination);
      osc.start(at);
      osc.stop(at + dur + 0.02);
      at += dur + 0.03;
    }
  } catch (err) { /* audio is a nicety, never a dependency */ }
}

/* ------------------------------------------------------- status + announcing */
const MODE_LABEL = { idle: 'Ready', wake: 'Listening for wake word', listen: 'Listening', think: 'Thinking', speak: 'Speaking' };

function setStatus(text, mode) {
  if (mode) appState = mode;
  $('status-line').textContent = text;
  const badge = $('mode-badge');
  badge.lastChild.textContent = ' ' + (MODE_LABEL[appState] || '');
  $('mic-btn').classList.toggle('listening', appState === 'listen');
  $('mic-btn').classList.toggle('speaking', appState === 'speak');
  $('mic-hint').textContent = {
    idle: listenMode === 'button' ? 'Tap the big button and speak.' : (listenMode === 'handsfree' ? 'Hands-free: just speak.' : 'Say "hey BlindSight", or tap the button.'),
    wake: 'Say "hey BlindSight"…',
    listen: 'Go ahead, I am listening.',
    think: 'One moment…',
    speak: 'Tap the button to interrupt me.'
  }[appState];
}

function announce(text) {
  const live = $('sr-announcer');
  live.textContent = '';
  window.setTimeout(() => { live.textContent = text; }, 30);
}

function showHeard(text) {
  const el = $('heard-line');
  if (el) el.textContent = text ? 'I heard: “' + text + '”' : '';
}

function addBubble(who, text, degraded) {
  const log = $('chat-log');
  const bubble = document.createElement('div');
  bubble.className = 'bubble ' + who + (degraded ? ' degraded' : '');
  const label = document.createElement('span');
  label.className = 'who';
  label.textContent = who === 'user' ? 'You' : 'BlindSight';
  bubble.appendChild(label);
  bubble.appendChild(document.createTextNode(text));
  log.appendChild(bubble);
  log.scrollTop = log.scrollHeight;
}

/* ------------------------------------------------------------------- speech */
function pickVoice() {
  if (!synth) return null;
  const voices = synth.getVoices();
  const langPrefix = (SPEECH_LANG[language] || 'en').slice(0, 2).toLowerCase();
  const mine = voices.filter((v) => (v.lang || '').toLowerCase().startsWith(langPrefix));
  if (chosenVoiceURI) {
    const saved = mine.find((v) => v.voiceURI === chosenVoiceURI) || voices.find((v) => v.voiceURI === chosenVoiceURI);
    if (saved) return saved;
  }
  const natural = mine.find((v) => /natural|neural|online\(natural\)/i.test(v.name));
  return natural || mine[0] || voices.find((v) => /natural|neural/i.test(v.name)) || null;
}

function fillVoiceSelect() {
  const select = $('voice-select');
  if (!select || !synth) return;
  const langPrefix = (SPEECH_LANG[language] || 'en').slice(0, 2).toLowerCase();
  const voices = synth.getVoices().filter((v) => (v.lang || '').toLowerCase().startsWith(langPrefix));
  select.innerHTML = '';
  if (!voices.length) {
    const opt = document.createElement('option');
    opt.value = '';
    opt.textContent = 'Default voice';
    select.appendChild(opt);
    return;
  }
  voices.sort((a, b) => (b.name.includes('Natural') ? 1 : 0) - (a.name.includes('Natural') ? 1 : 0));
  const current = pickVoice();
  voices.forEach((v) => {
    const opt = document.createElement('option');
    opt.value = v.voiceURI;
    opt.textContent = v.name + (v.voiceURI === (current && current.voiceURI) ? ' (current)' : '');
    select.appendChild(opt);
  });
  if (current) select.value = current.voiceURI;
}

function speak(text) {
  stopListeningAll();
  if (!synth) { announce(text); resumeListening(); return; }
  synth.cancel();
  appState = 'speak';
  setStatus(text, 'speak');
  const utter = new SpeechSynthesisUtterance(text);
  utter.lang = SPEECH_LANG[language] || 'en-US';
  const voice = pickVoice();
  if (voice) utter.voice = voice;
  utter.rate = 1.0;
  utter.onend = () => { earcon('ok'); resumeListening(); };
  utter.onerror = () => resumeListening();
  synth.speak(utter);
}

function stopSpeaking() { if (synth) synth.cancel(); }

/* --------------------------------------------------------- recognition core */
function normalize(text) {
  return text.toLowerCase().replace(/[^a-z\s']/g, '').replace(/\s+/g, ' ').trim();
}

function backoffDelay() {
  const now = Date.now();
  restartDelay = now - lastRecEnd < 1200 ? Math.min(restartDelay * 2, 8000) : 300;
  lastRecEnd = now;
  return restartDelay;
}

function stopListeningAll() {
  for (const rec of [wakeRec, cmdRec]) {
    if (rec) {
      rec.onend = null;
      rec.onresult = null;
      try { rec.stop(); } catch (err) { /* already stopped */ }
    }
  }
  wakeRec = null;
  cmdRec = null;
}

function resumeListening() {
  appState = 'idle';
  if (listenMode === 'button') { setStatus('Ready', 'idle'); return; }
  window.setTimeout(() => {
    if (appState === 'idle') startListening();
  }, 200);
}

function startListening() {
  if (!SR || appState === 'speak' || appState === 'think' || appState === 'listen') return;
  stopListeningAll();
  if (listenMode === 'handsfree') startCommandSession(true);
  else startWakeSession();
}

function handleRecError(event, label) {
  if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
    listenMode = 'button';
    $('mode-select').value = 'button';
    localStorage.setItem('blindsight_mode', 'button');
    setStatus('Microphone permission denied. Tap the big button, then allow the mic.', 'idle');
    announce('Microphone permission denied. Tap the big button, then allow the microphone.');
  } else if (event.error === 'audio-capture') {
    setStatus('No microphone found. Plug one in or pick another input.', 'idle');
    announce('No microphone found.');
  } else if (event.error === 'network') {
    setStatus('Browser speech needs internet once; retrying…', 'idle');
  }
  // 'no-speech' and 'aborted' are ordinary endings; the restart below handles them.
  void label;
}

let wakeBuffer = '';

function startWakeSession() {
  wakeBuffer = '';
  wakeRec = new SR();
  wakeRec.lang = SPEECH_LANG[language] || 'en-US';
  wakeRec.continuous = true;
  wakeRec.interimResults = true;
  wakeRec.onresult = (event) => {
    restartDelay = 300;
    let finalConfidence = 0;
    for (let i = event.resultIndex; i < event.results.length; i++) {
      wakeBuffer += ' ' + event.results[i][0].transcript;
      if (event.results[i].isFinal) finalConfidence = event.results[i][0].confidence || 0;
    }
    const words = normalize(wakeBuffer).split(' ').filter(Boolean).slice(-8);
    wakeBuffer = words.join(' ');
    const said = wakeBuffer;
    if (!said || !WAKE_PHRASES.some((p) => said.includes(p))) return;
    // Noise gate: ignore matches whispered by room noise unless the result is
    // final and confident, or real speech energy was present just now.
    if (!speechRecently() && finalConfidence < 0.6) return;
    wakeBuffer = '';
    earcon('wake');
    startCommandSession(false);
  };
  wakeRec.onerror = (event) => handleRecError(event, 'wake');
  wakeRec.onend = () => {
    wakeRec = null;
    // THE v1 bug lived here: restart no matter which listening state we were
    // in, otherwise Chrome's silence-timeout kills the wake listener forever.
    if (listenMode === 'wake' && appState !== 'speak' && appState !== 'think' && appState !== 'listen') {
      window.setTimeout(() => { if (listenMode === 'wake' && appState === 'idle') startWakeSession(); }, backoffDelay());
    }
  };
  try {
    wakeRec.start();
    appState = 'wake';
    setStatus('Waiting for wake word', 'wake');
  } catch (err) { /* start() throws when already started; the onend restart covers it */ }
}

let commandBuffer = '';
let commandTimer = null;

function startCommandSession(continuousMode) {
  stopListeningAll();
  commandBuffer = '';
  cmdRec = new SR();
  cmdRec.lang = SPEECH_LANG[language] || 'en-US';
  cmdRec.continuous = continuousMode;
  cmdRec.interimResults = false;
  const routeNow = () => {
    if (commandTimer) { window.clearTimeout(commandTimer); commandTimer = null; }
    const text = commandBuffer.trim();
    commandBuffer = '';
    cmdRec = null;
    if (text) handleCommand(text);
    else if (continuousMode) resumeListening();
    else {
      earcon('error');
      showHeard('');
      announce('I did not catch that. Say hey BlindSight to try again.');
      setStatus('I did not catch that.', 'idle');
      resumeListening();
    }
  };
  cmdRec.onresult = (event) => {
    restartDelay = 300;
    let fresh = '';
    for (let i = 0; i < event.results.length; i++) {
      if (event.results[i].isFinal) fresh += event.results[i][0].transcript + ' ';
    }
    if (!fresh) return;
    // Noise gate for hands-free mode: drop sentences that arrived while the
    // room was quiet-ish (fans, TV) rather than during real speech energy.
    if (continuousMode && !speechRecently()) return;
    commandBuffer += ' ' + fresh;
    showHeard(normalize(commandBuffer));
    if (continuousMode) {
      // Segment by silence: route 1.1s after the last final result.
      if (commandTimer) window.clearTimeout(commandTimer);
      commandTimer = window.setTimeout(routeNow, 1100);
    }
  };
  cmdRec.onerror = (event) => handleRecError(event, 'command');
  cmdRec.onend = () => {
    if (continuousMode && cmdRec) {
      // hands-free: keep the ear open; route whatever piled up, then reopen.
      if (commandBuffer.trim()) routeNow();
      cmdRec = null;
      if (listenMode === 'handsfree' && appState !== 'speak' && appState !== 'think') {
        window.setTimeout(() => { if (listenMode === 'handsfree' && appState === 'idle') startCommandSession(true); }, backoffDelay());
      }
    } else if (cmdRec) {
      routeNow();
    }
  };
  appState = 'listen';
  setStatus('Listening', 'listen');
  earcon('listen');
  try { cmdRec.start(); } catch (err) { /* already started */ }
}

/* ------------------------------------------------------------- command router */
function authHeaders() {
  return authToken ? { 'Authorization': 'Bearer ' + authToken } : {};
}

async function handleCommand(raw) {
  const said = normalize(raw);
  addBubble('user', raw);
  showHeard(said);

  if (STOP_WORDS.includes(said)) {
    stopSpeaking();
    earcon('tick');
    announce('Stopped.');
    setStatus('Stopped.', 'idle');
    resumeListening();
    return;
  }
  if (HELP_WORDS.includes(said)) { speak(HELP_TEXT); return; }
  if (STATUS_WORDS.includes(said)) { speak(await statusSummary()); return; }
  const forceCamera = SCENE_WORDS.some((p) => said === p || said.startsWith(p + ' '));
  await sendChat(raw, forceCamera || $('camera-toggle').checked);
}

async function sendChat(prompt, useCamera) {
  appState = 'think';
  setStatus('Thinking', 'think');
  earcon('think');
  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
      body: JSON.stringify({ prompt, use_camera: useCamera })
    });
    if (res.status === 401) {
      authToken = window.prompt('This gateway is password protected. Enter the access token:') || '';
      if (authToken) localStorage.setItem('blindsight_token', authToken);
      return sendChat(prompt, useCamera);
    }
    if (res.status === 429) { speak('Please wait a moment and ask again.'); return; }
    const data = await res.json();
    addBubble('bot', data.reply, data.degraded);
    speak(data.reply);
  } catch (err) {
    earcon('error');
    addBubble('bot', 'The gateway cannot be reached.', true);
    speak('The gateway cannot be reached.');
  }
}

async function statusSummary() {
  try {
    const res = await fetch('/health');
    const health = await res.json();
    const parts = [
      'vision ' + (health.vision_ready ? 'ready' : 'not ready'),
      'speech ' + (health.tts_ready ? 'ready' : 'not ready'),
      'cloud companion ' + (health.companion_ready ? 'ready' : 'not ready')
    ];
    return 'System status: ' + parts.join(', ') + '. ' +
      (health.issues && health.issues.length ? health.issues.length + ' open issue(s).' : 'No open issues.');
  } catch (err) {
    return 'I cannot reach the gateway to read the status.';
  }
}

/* ------------------------------------------------------------------- wiring */
function wireEvents() {
  $('mic-btn').addEventListener('click', async () => {
    if (appState === 'speak') { stopSpeaking(); resumeListening(); return; }
    if (appState === 'listen') { stopListeningAll(); announce('Listening stopped.'); resumeListening(); return; }
    await openMicAnalytics();
    stopSpeaking();
    startCommandSession(false);
  });

  $('mode-select').addEventListener('change', async (event) => {
    listenMode = event.target.value;
    localStorage.setItem('blindsight_mode', listenMode);
    stopListeningAll();
    appState = 'idle';
    if (listenMode === 'button') { setStatus('Button mode: tap to talk.', 'idle'); announce('Button mode.'); return; }
    await openMicAnalytics();
    announce(listenMode === 'handsfree' ? 'Hands-free mode on. Just speak.' : 'Wake word mode on. Say hey BlindSight.');
    startListening();
  });

  $('language-select').addEventListener('change', async (event) => {
    const code = event.target.value;
    try {
      const res = await fetch('/api/prefs', {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
        body: JSON.stringify({ language: code })
      });
      if (res.ok) {
        language = code;
        fillVoiceSelect();
        speak(LANG_CONFIRM[code] || 'Language updated.');
      } else announce('Could not change the language.');
    } catch (err) { announce('Could not reach the gateway to change language.'); }
  });

  $('voice-select').addEventListener('change', (event) => {
    chosenVoiceURI = event.target.value;
    localStorage.setItem('blindsight_voice', chosenVoiceURI);
    earcon('tick');
    speak('This is the voice I will use.');
  });

  $('chat-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const input = $('prompt-input');
    const text = input.value.trim();
    if (!text) return;
    input.value = '';
    handleCommand(text);
  });

  document.querySelectorAll('.text-size-group button').forEach((btn) => {
    btn.addEventListener('click', () => {
      const size = btn.dataset.size;
      if (size === 'md') document.documentElement.removeAttribute('data-text-size');
      else document.documentElement.setAttribute('data-text-size', size);
      localStorage.setItem('blindsight_text_size', size);
      document.querySelectorAll('.text-size-group button').forEach((b) => b.setAttribute('aria-pressed', String(b === btn)));
      earcon('tick');
      announce('Text size ' + btn.textContent.trim());
    });
  });

  $('help-btn').addEventListener('click', () => speak(HELP_TEXT));
  $('stop-btn').addEventListener('click', () => { stopSpeaking(); resumeListening(); announce('Stopped.'); });
  $('frame-btn').addEventListener('click', async () => {
    try {
      const res = await fetch('/api/latest-frame', { headers: authHeaders() });
      if (!res.ok) { announce('No camera frame available yet.'); return; }
      const blob = await res.blob();
      const img = $('frame-img');
      img.src = URL.createObjectURL(blob);
      img.hidden = false;
      announce('Latest camera frame shown for sighted helpers.');
    } catch (err) { announce('Could not fetch the camera frame.'); }
  });
}

async function loadPrefs() {
  try {
    const res = await fetch('/api/prefs');
    if (!res.ok) return;
    const prefs = await res.json();
    const select = $('language-select');
    select.innerHTML = '';
    (prefs.options || []).forEach((opt) => {
      const el = document.createElement('option');
      el.value = opt.code;
      el.textContent = opt.label;
      select.appendChild(el);
    });
    select.value = prefs.language || 'en';
    language = prefs.language || 'en';
  } catch (err) { /* gateway unreachable at boot */ }
}

async function bootstrapConfig() {
  try {
    const res = await fetch('/api/config');
    if (!res.ok) return;
    const cfg = await res.json();
    if (cfg.auth_required && !authToken) {
      authToken = window.prompt('This BlindSight gateway is password protected. Enter the access token:') || '';
      if (authToken) localStorage.setItem('blindsight_token', authToken);
    }
  } catch (err) { /* offline boot */ }
}

window.addEventListener('load', async () => {
  const savedSize = localStorage.getItem('blindsight_text_size');
  if (savedSize && savedSize !== 'md') document.documentElement.setAttribute('data-text-size', savedSize);
  if (synth) {
    synth.getVoices();
    synth.onvoiceschanged = fillVoiceSelect;
  }
  await bootstrapConfig();
  await loadPrefs();
  fillVoiceSelect();
  $('mode-select').value = listenMode;
  wireEvents();
  setStatus('Ready', 'idle');
  announce('BlindSight ready. ' + (listenMode === 'handsfree' ? 'Hands-free mode: just speak.' : 'Say hey BlindSight, or tap the big button.') + ' Say help for the command list.');
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/static/sw.js', { scope: '/' }).catch(() => { /* optional */ });
  }
  if (!SR) {
    $('mode-select').value = 'button';
    listenMode = 'button';
    setStatus('Ready. This browser has no speech input; use the button or text box.', 'idle');
    return;
  }
  // Open the mic analytics eagerly so the noise floor calibrates before the
  // first wake word; recognition itself starts only after a gesture if the
  // browser refuses pre-gesture capture.
  const micOk = await openMicAnalytics();
  if (micOk && listenMode !== 'button') startListening();
  else if (!micOk && listenMode !== 'button') {
    setStatus('Tap the big button once to allow the microphone, then I listen hands-free.', 'idle');
  }
});
