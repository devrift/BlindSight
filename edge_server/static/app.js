/* BlindSight voice-first PWA engine.
   Interaction model copied from what makes "hey Google" work for blind users:
   a wake word starts a listening window, one spoken sentence is captured, the
   answer is spoken back, and the microphone sleeps while the assistant talks
   (so it never hears itself). Every state change also gets a short earcon and
   a screen-reader announcement; the screen is a secondary display.
   Push-to-talk (the big button) and the text box always work, including in
   browsers without speech recognition. */
'use strict';

const $ = (id) => document.getElementById(id);

const WAKE_PHRASES = ['hey blindsight', 'hey blind sight', 'ok blindsight', 'okay blindsight', 'blindsight'];
const STOP_WORDS = ['stop', 'be quiet', 'quiet', 'cancel', 'shush'];
const HELP_WORDS = ['help', 'what can i say', 'commands'];
const STATUS_WORDS = ['read status', 'system status', 'status report', 'how are you feeling'];
const SCENE_WORDS = ['describe', 'describe the scene', 'what is around me', 'what\'s around me', 'what is in front of me', 'look for me'];
const MEMORY_WORDS = ['remember', 'forget', 'what do you remember', 'list memories', 'clear chat', 'start over', 'language', 'switch language'];

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
  'Say hey BlindSight, then ask your question. Commands: describe, for a scene ' +
  'description. what do you remember. remember, followed by a fact. forget. ' +
  'language, followed by a language, like hindi. read status. clear chat. stop. help.';

const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
const synth = window.speechSynthesis || null;

let state = 'idle';            // idle | wake | listen | think | speak
let wakeEnabled = true;
let language = 'en';
let authToken = localStorage.getItem('blindsight_token') || '';
let wakeRec = null;
let cmdRec = null;
let audioCtx = null;

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
const MODE_LABEL = {
  idle: 'Ready',
  wake: 'Waiting for wake word',
  listen: 'Listening',
  think: 'Thinking',
  speak: 'Speaking'
};

function setStatus(text, mode) {
  if (mode) state = mode;
  $('status-line').textContent = text;
  $('mode-badge').lastChild.textContent = ' ' + (MODE_LABEL[state] || '');
  const badge = $('mode-badge');
  badge.className = '';
  if (state === 'listen') badge.classList.add('listening');
  if (state === 'speak') badge.classList.add('speaking');
  $('mic-btn').classList.toggle('listening', state === 'listen');
  $('mic-btn').classList.toggle('speaking', state === 'speak');
  $('mic-hint').textContent = {
    idle: wakeEnabled ? 'Say "hey BlindSight", or tap the big button.' : 'Tap the big button and speak.',
    wake: 'Say "hey BlindSight"…',
    listen: 'Go ahead, I am listening.',
    think: 'One moment…',
    speak: 'Tap the button to interrupt me.'
  }[state];
}

function announce(text) {
  const live = $('sr-announcer');
  live.textContent = '';
  window.setTimeout(() => { live.textContent = text; }, 30);
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
function speak(text) {
  if (!synth) { announce(text); toWake(); return; }
  stopWake();
  synth.cancel();
  state = 'speak';
  setStatus(text, 'speak');
  const utter = new SpeechSynthesisUtterance(text);
  utter.lang = SPEECH_LANG[language] || 'en-US';
  const voices = synth.getVoices();
  const match = voices.find((v) => v.lang && v.lang.toLowerCase().startsWith((SPEECH_LANG[language] || 'en').slice(0, 2).toLowerCase()));
  if (match) utter.voice = match;
  utter.rate = 1.02;
  utter.onend = () => { earcon('ok'); toWake(); };
  utter.onerror = () => toWake();
  synth.speak(utter);
}

function stopSpeaking() {
  if (synth) synth.cancel();
}

/* --------------------------------------------------------- speech recognition */
function normalize(text) {
  return text.toLowerCase().replace(/[^a-z\s']/g, '').replace(/\s+/g, ' ').trim();
}

function stopWake() {
  if (wakeRec) {
    wakeRec.onend = null;
    try { wakeRec.stop(); } catch (err) { /* already stopped */ }
    wakeRec = null;
  }
}

let wakeBuffer = '';

function startWake() {
  if (!SR || !wakeEnabled || state === 'speak' || state === 'listen' || state === 'think') return;
  stopWake();
  wakeBuffer = '';
  wakeRec = new SR();
  wakeRec.lang = SPEECH_LANG[language] || 'en-US';
  wakeRec.continuous = true;
  wakeRec.interimResults = true;
  wakeRec.onresult = (event) => {
    // The wake phrase can arrive split over several events ("hey…" / "…blind
    // sight"), so keep a rolling buffer of the last words instead of testing
    // each event in isolation.
    for (let i = event.resultIndex; i < event.results.length; i++) {
      wakeBuffer += ' ' + event.results[i][0].transcript;
    }
    const words = normalize(wakeBuffer).split(' ').filter(Boolean).slice(-8);
    wakeBuffer = words.join(' ');
    const said = wakeBuffer;
    if (said && WAKE_PHRASES.some((p) => said.includes(p))) {
      wakeBuffer = '';
      earcon('wake');
      startCommand();
    }
  };
  wakeRec.onerror = (event) => {
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      wakeEnabled = false;
      $('wake-toggle').checked = false;
      setStatus('Microphone permission denied. Tap the big button to talk.', 'idle');
      announce('Microphone permission denied. Tap the big button to talk.');
    }
  };
  wakeRec.onend = () => {
    if (wakeEnabled && state === 'idle') window.setTimeout(startWake, 250);
  };
  try {
    wakeRec.start();
    state = 'wake';
    setStatus('Waiting for wake word', 'wake');
  } catch (err) { /* start() throws if already started; harmless */ }
}

function startCommand() {
  stopWake();
  if (!SR) { announce('Voice input is not supported here. Use the text box.'); return; }
  cmdRec = new SR();
  cmdRec.lang = SPEECH_LANG[language] || 'en-US';
  cmdRec.continuous = false;
  cmdRec.interimResults = false;
  let heard = '';
  cmdRec.onresult = (event) => {
    for (let i = 0; i < event.results.length; i++) {
      if (event.results[i].isFinal) heard += event.results[i][0].transcript + ' ';
    }
  };
  cmdRec.onerror = () => { /* onend reports the outcome */ };
  cmdRec.onend = () => {
    const text = heard.trim();
    if (text) {
      handleCommand(text);
    } else {
      earcon('error');
      announce('I did not catch that. Say hey BlindSight to try again.');
      setStatus('I did not catch that.', 'idle');
      toWake();
    }
  };
  state = 'listen';
  setStatus('Listening', 'listen');
  earcon('listen');
  try { cmdRec.start(); } catch (err) { /* already started */ }
}

function toWake() {
  state = 'idle';
  if (wakeEnabled) {
    window.setTimeout(startWake, 200);
  } else {
    setStatus('Ready', 'idle');
  }
}

/* ------------------------------------------------------------- command router */
function authHeaders() {
  return authToken ? { 'Authorization': 'Bearer ' + authToken } : {};
}

async function handleCommand(raw) {
  const said = normalize(raw);
  addBubble('user', raw);

  if (STOP_WORDS.includes(said)) {
    stopSpeaking();
    earcon('tick');
    announce('Stopped.');
    setStatus('Stopped.', 'idle');
    toWake();
    return;
  }
  if (HELP_WORDS.includes(said)) {
    speak(HELP_TEXT);
    return;
  }
  if (STATUS_WORDS.includes(said)) {
    const summary = await statusSummary();
    speak(summary);
    return;
  }
  const forceCamera = SCENE_WORDS.some((p) => said === p || said.startsWith(p + ' '));
  const useCamera = forceCamera || $('camera-toggle').checked;
  await sendChat(raw, useCamera);
}

async function sendChat(prompt, useCamera) {
  state = 'think';
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
    if (res.status === 429) {
      speak('Please wait a moment and ask again.');
      return;
    }
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
  $('mic-btn').addEventListener('click', () => {
    if (state === 'speak') { stopSpeaking(); toWake(); return; }
    if (state === 'listen') { if (cmdRec) { try { cmdRec.stop(); } catch (err) { /* noop */ } } return; }
    stopSpeaking();
    startCommand();
  });

  $('wake-toggle').addEventListener('change', (event) => {
    wakeEnabled = event.target.checked;
    if (wakeEnabled) {
      announce('Wake word on. Say hey BlindSight.');
      startWake();
    } else {
      stopWake();
      state = 'idle';
      setStatus('Wake word off. Tap the button to talk.', 'idle');
      announce('Wake word off.');
    }
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
        speak(LANG_CONFIRM[code] || 'Language updated.');
      } else {
        announce('Could not change the language.');
      }
    } catch (err) {
      announce('Could not reach the gateway to change language.');
    }
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
      document.querySelectorAll('.text-size-group button').forEach((b) => {
        b.setAttribute('aria-pressed', String(b === btn));
      });
      earcon('tick');
      announce('Text size ' + btn.textContent.trim());
    });
  });

  $('help-btn').addEventListener('click', () => speak(HELP_TEXT));
  $('stop-btn').addEventListener('click', () => { stopSpeaking(); toWake(); announce('Stopped.'); });
  $('frame-btn').addEventListener('click', async () => {
    try {
      const res = await fetch('/api/latest-frame', { headers: authHeaders() });
      if (!res.ok) { announce('No camera frame available yet.'); return; }
      const blob = await res.blob();
      const img = $('frame-img');
      img.src = URL.createObjectURL(blob);
      img.hidden = false;
      announce('Latest camera frame shown for sighted helpers.');
    } catch (err) {
      announce('Could not fetch the camera frame.');
    }
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
  if (synth) synth.getVoices();
  await bootstrapConfig();
  await loadPrefs();
  wireEvents();
  setStatus('Ready', 'idle');
  announce('BlindSight ready. Say hey BlindSight, or tap the big button and speak. Say help for the command list.');
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/static/sw.js', { scope: '/' }).catch(() => { /* offline-first is optional */ });
  }
  if (SR && wakeEnabled) startWake();
  else if (!SR) {
    $('wake-toggle').disabled = true;
    setStatus('Ready. Voice input unsupported in this browser; use the button or text box.', 'idle');
  }
});
