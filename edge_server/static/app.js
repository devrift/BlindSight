/* BlindSight voice-first PWA engine (v3.2 - cross-platform edition).
   One codebase, three honest experiences:
   - Android (Chrome/Edge) & desktop: wake word, hands-free mode, button talk,
     neural gateway voices, optional noise gate. Recognition restarts with
     backoff after every end, so Chrome's silence timeout can never kill the
     wake listener, and finals-only results keep room noise out.
   - iPhone/iPad (Safari): Apple exposes no SpeechRecognition to web apps, so
     instead of a dead mic button the app switches to dictation mode - the big
     button opens the keyboard and tells the user to tap its microphone.
     Replies are still spoken (gateway neural audio), and audio is unlocked on
     the first touch because iOS blocks playback before any user gesture.
   - Anywhere: gateway-rendered neural audio is preferred, browser speech
     synthesis is the fallback, the Stop button silences both, and the screen
     stays a secondary display with earcons and assertive announcements. */
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
const hasSR = typeof SR === 'function';
// iPadOS 13+ reports itself as MacIntel; the touch-point count gives it away.
const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const synth = window.speechSynthesis || null;

let appState = 'idle';         // idle | wake | listen | think | speak
let listenMode = localStorage.getItem('blindsight_mode') || 'wake';  // wake | handsfree | button
let language = 'en';
let chosenVoiceURI = localStorage.getItem('blindsight_voice') || '';
let authToken = localStorage.getItem('blindsight_token') || '';
let wakeRec = null;
let lastNetworkRetry = 0;
let lastMicTap = 0;
let cmdRec = null;
let restartDelay = 300;
let lastRecEnd = 0;
let audioCtx = null;
let currentPlayer = null;   // gateway-audio element, so Stop really stops it
let noiseGate = localStorage.getItem('blindsight_noisegate') === 'on';

/* ---------------------------------------------------- mic energy / noise gate */
let analyser = null;
let levelBuf = null;
let micStream = null;
let micInterval = null;
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
    micStream = stream;
    micInterval = window.setInterval(sampleMicLevel, 100);
    window.setTimeout(() => { calibrating = false; }, 1500);
    return true;
  } catch (err) {
    analyser = null;
    return false;
  }
}

function stopMicAnalytics() {
  // A second live mic stream (this analytics tap) can starve Chrome's
  // SpeechRecognition on Android, so it is paused for the whole duration of
  // every recognition session and recalibrated afterwards.
  if (micInterval) { window.clearInterval(micInterval); micInterval = null; }
  if (micStream) { micStream.getTracks().forEach((t) => t.stop()); micStream = null; }
  analyser = null;
  speechWindow = [];
  paintMeter();
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

/* ------------------------------------------------- mobile audio unlock (iOS) */
// iOS (and some Android browsers) refuse every play() until the user has
// touched the page once. Without this unlock the first spoken reply - the one
// that matters most - is silently swallowed. A silent WAV plus a zero-volume
// utterance inside the first gesture makes all later playback legitimate.
let audioUnlocked = false;
const SILENT_WAV = 'data:audio/wav;base64,UklGRigAAABXQVZFZm10IBIAAAABAAEARKwAAIhYAQACABAAAABkYXRhAgAAAAEA';

function unlockAudio() {
  if (audioUnlocked) return;
  audioUnlocked = true;
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume();
    const silent = new Audio(SILENT_WAV);
    silent.play().catch(() => { /* next gesture will unlock instead */ });
    if (synth) {
      const warm = new SpeechSynthesisUtterance(' ');
      warm.volume = 0;
      synth.speak(warm);
    }
  } catch (err) { /* unlocking is best-effort, never fatal */ }
}
document.addEventListener('touchend', unlockAudio);
document.addEventListener('click', unlockAudio);
document.addEventListener('keydown', unlockAudio);

function flashCue() {
  // Visual-only attention cue. Any sound within ~250 ms of rec.start() steals
  // the microphone from SpeechRecognition on Android (confirmed field bug).
  const btn = $('mic-btn');
  if (!btn) return;
  btn.classList.add('cue');
  window.setTimeout(() => btn.classList.remove('cue'), 400);
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
    idle: !hasSR
      ? (isIOS ? 'Tap the big button, then the keyboard mic to dictate.' : 'Type below, or use Chrome/Edge for voice.')
      : (listenMode === 'button' ? 'Tap the big button and speak.' : (listenMode === 'handsfree' ? 'Hands-free: just speak.' : 'Say "hey BlindSight", or tap the button.')),
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

async function fillVoiceSelect() {
  const select = $('voice-select');
  if (!select) return;
  select.innerHTML = '';
  let serverVoices = [];
  try {
    const res = await fetch('/api/tts/voices');
    if (res.ok) serverVoices = (await res.json()).voices || [];
  } catch (err) { /* gateway offline: browser voices only */ }
  if (serverVoices.length) {
    const group = document.createElement('optgroup');
    group.label = 'Gateway neural voices (human-sounding)';
    serverVoices.slice(0, 12).forEach((v) => {
      const opt = document.createElement('option');
      opt.value = v.voice;
      opt.textContent = v.label || v.voice;
      group.appendChild(opt);
    });
    select.appendChild(group);
  }
  if (synth) {
    const langPrefix = (SPEECH_LANG[language] || 'en').slice(0, 2).toLowerCase();
    const browserVoices = synth.getVoices().filter((v) => (v.lang || '').toLowerCase().startsWith(langPrefix));
    if (browserVoices.length) {
      const group = document.createElement('optgroup');
      group.label = 'Browser voices (offline fallback)';
      browserVoices.forEach((v) => {
        const opt = document.createElement('option');
        opt.value = 'browser|' + v.voiceURI;
        opt.textContent = v.name;
        group.appendChild(opt);
      });
      select.appendChild(group);
    }
  }
  const current = $('voice-select') ? serverCurrentVoice : '';
  if (current) select.value = current;
}

let serverCurrentVoice = '';

function speak(text, audioUrl) {
  stopListeningAll();
  appState = 'speak';
  setStatus(text, 'speak');
  const done = () => { earcon('ok'); resumeListening(); };
  if (audioUrl) {
    // Gateway-rendered neural voice: the same human-sounding audio on every
    // browser and phone, independent of each OS's built-in robot voices.
    const player = new Audio(audioUrl);
    currentPlayer = player;
    player.onended = () => { currentPlayer = null; done(); };
    player.onerror = () => { currentPlayer = null; browserSpeak(text, done); };
    player.play().catch(() => { currentPlayer = null; browserSpeak(text, done); });
    return;
  }
  browserSpeak(text, done);
}

function browserSpeak(text, done) {
  if (!synth) { announce(text); done(); return; }
  synth.cancel();
  const utter = new SpeechSynthesisUtterance(text);
  utter.lang = SPEECH_LANG[language] || 'en-US';
  const voice = pickVoice();
  if (voice) utter.voice = voice;
  utter.rate = 1.0;
  utter.onend = done;
  utter.onerror = done;
  synth.speak(utter);
}

function stopSpeaking() {
  // Stop must silence the gateway audio element too, not just browser synth -
  // on a phone the neural mp3 is what is actually playing.
  if (currentPlayer) {
    try { currentPlayer.pause(); } catch (err) { /* already stopped */ }
    currentPlayer = null;
  }
  if (synth) synth.cancel();
}

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
  if (noiseGate) openMicAnalytics();
  if (listenMode === 'button') { setStatus('Ready', 'idle'); return; }
  window.setTimeout(() => {
    if (appState === 'idle') startListening();
  }, 200);
}

function startListening() {
  if (!hasSR || appState === 'speak' || appState === 'think' || appState === 'listen') return;
  stopListeningAll();
  if (listenMode === 'handsfree') startCommandSession(true);
  else startWakeSession();
}

function handleRecError(event, label) {
  // A voice-first app must never die silently: every fatal recognition error
  // is announced AND spoken, and network drops retry once on their own.
  if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
    listenMode = 'button';
    $('mode-select').value = 'button';
    localStorage.setItem('blindsight_mode', 'button');
    const msg = 'Microphone permission denied. Tap the big button, then allow the microphone.';
    setStatus(msg, 'idle');
    announce(msg);
    speak(msg);
  } else if (event.error === 'audio-capture') {
    const msg = 'No microphone found. Plug one in or pick another input.';
    setStatus(msg, 'idle');
    announce(msg);
    speak(msg);
  } else if (event.error === 'network') {
    const msg = 'Browser speech cannot reach its internet service. Check the connection; trying again.';
    setStatus(msg, 'idle');
    announce(msg);
    speak(msg);
    const now = Date.now();
    if (now - lastNetworkRetry > 5000) {
      lastNetworkRetry = now;
      window.setTimeout(() => { if (appState === 'idle') startListening(); }, 2000);
    }
  }
  // 'no-speech' and 'aborted' are ordinary endings; the restart below handles them.
  void label;
}

let wakeBuffer = '';

function startWakeSession() {
  if (!hasSR) return;
  stopMicAnalytics();
  wakeBuffer = '';
  wakeRec = new SR();
  wakeRec.lang = SPEECH_LANG[language] || 'en-US';
  wakeRec.continuous = true;
  wakeRec.interimResults = false;   // finals only: room noise rarely survives finalisation
  wakeRec.onresult = (event) => {
    restartDelay = 300;
    let confidence = 0;
    for (let i = event.resultIndex; i < event.results.length; i++) {
      if (!event.results[i].isFinal) continue;
      wakeBuffer += ' ' + event.results[i][0].transcript;
      confidence = Math.max(confidence, event.results[i][0].confidence || 0);
    }
    const words = normalize(wakeBuffer).split(' ').filter(Boolean).slice(-8);
    wakeBuffer = words.join(' ');
    const said = wakeBuffer;
    if (!said || !WAKE_PHRASES.some((ph) => said.includes(ph))) return;
    if (noiseGate && !speechRecently() && confidence < 0.6) return;
    wakeBuffer = '';
    flashCue();
    startCommandSession(false);
  };
  wakeRec.onerror = (event) => handleRecError(event, 'wake');
  wakeRec.onend = () => {
    wakeRec = null;
    // Restart no matter which listening state we were in: Chrome's silence
    // timeout must never be able to kill the wake listener again.
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

function focusDictation() {
  // No SpeechRecognition (every iOS browser, some Android ones): the honest
  // path is the on-screen keyboard's own dictation microphone.
  const input = $('prompt-input');
  if (input) input.focus();
  const hint = isIOS
    ? 'Tap the microphone on the iPhone keyboard to dictate, then tap Ask.'
    : 'This browser has no speech input. Type your question, or use Chrome or Edge for voice.';
  setStatus(hint, 'idle');
  announce(hint);
}

function startCommandSession(continuousMode) {
  if (!hasSR) { focusDictation(); return; }
  stopMicAnalytics();
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
      if (!event.results[i].isFinal) continue;
      const conf = event.results[i][0].confidence || 0;
      const words = normalize(event.results[i][0].transcript).split(' ').filter(Boolean).length;
      if (conf > 0 && conf < 0.4) continue;                    // low-confidence noise
      if (conf === 0 && words < 3 && !speechRecently()) continue;  // stray short noise
      fresh += event.results[i][0].transcript + ' ';
    }
    if (!fresh) return;
    // Noise gate for hands-free mode: drop sentences that arrived while the
    // room was quiet-ish (fans, TV) rather than during real speech energy.
    if (continuousMode && analyser && !speechRecently()) return;
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
  // No earcon here on purpose: on Android a beep played at the instant the
  // recognizer opens grabs audio focus and kills the session ~1s later.
  try { cmdRec.start(); } catch (err) { /* already started */ }
}

/* ------------------------------------------------------------- command router */
async function savePrefs(patch) {
  try {
    await fetch('/api/prefs', {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
      body: JSON.stringify(patch)
    });
  } catch (err) { /* gateway offline */ }
}

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
      // window.prompt never appears in iOS home-screen PWAs, and re-prompting
      // on a wrong token recursed forever. The inline token field in settings
      // is visible, screen-reader friendly and cannot loop.
      showTokenControl('This gateway is password protected. Enter the access token in settings, tap Save, then ask again.');
      return;
    }
    if (res.status === 429) { speak('Slow down a little - I heard you, try again in a few seconds.'); return; }
    const data = await res.json();
    addBubble('bot', data.reply, data.degraded);
    speak(data.reply, data.audio_url || null);
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
    if (appState === 'think') { announce('One moment, still thinking.'); return; }
    const tap = Date.now();
    if (tap - lastMicTap < 300) return;  // a tap must never stop what it just started
    lastMicTap = tap;
    unlockAudio();
    if (appState === 'speak') { stopSpeaking(); resumeListening(); return; }
    if (appState === 'listen') { stopListeningAll(); announce('Listening stopped.'); resumeListening(); return; }
    if (!hasSR) { focusDictation(); return; }
    if (noiseGate) await openMicAnalytics();
    stopSpeaking();
    startCommandSession(false);
  });

  $('noise-gate-toggle').addEventListener('change', async (event) => {
    noiseGate = event.target.checked;
    localStorage.setItem('blindsight_noisegate', noiseGate ? 'on' : 'off');
    if (noiseGate) {
      await openMicAnalytics();
      announce('Noise gate on. The level bar shows what I hear.');
    } else {
      announce('Noise gate off.');
    }
  });

  $('mode-select').addEventListener('change', async (event) => {
    listenMode = event.target.value;
    localStorage.setItem('blindsight_mode', listenMode);
    stopListeningAll();
    appState = 'idle';
    if (listenMode === 'button') { setStatus('Button mode: tap to talk.', 'idle'); announce('Button mode.'); return; }
    if (noiseGate) await openMicAnalytics();
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

  $('voice-select').addEventListener('change', async (event) => {
    const value = event.target.value;
    earcon('tick');
    if (value.startsWith('browser|')) {
      chosenVoiceURI = value.slice(8);
      localStorage.setItem('blindsight_voice', chosenVoiceURI);
      await savePrefs({ voice: '' });
      browserSpeak('This is the voice I will use when the gateway voice is unavailable.', () => {});
    } else {
      serverCurrentVoice = value;
      await savePrefs({ voice: value });
      speak('This is the voice I will use.', null);
    }
  });

  const tokenSave = $('token-save');
  if (tokenSave) {
    tokenSave.addEventListener('click', () => {
      const value = ($('token-input').value || '').trim();
      if (!value) { announce('Enter the access token first.'); return; }
      authToken = value;
      localStorage.setItem('blindsight_token', authToken);
      $('token-control').hidden = true;
      earcon('ok');
      setStatus('Token saved.', 'idle');
      announce('Token saved. Ask your question again.');
    });
  }

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

  // Keep the mic button and text box reachable while Android's soft keyboard
  // covers the lower half of the screen.
  if (window.visualViewport) {
    const keepVisible = () => {
      const el = document.activeElement;
      if (el && (el.id === 'prompt-input' || el.id === 'token-input')) {
        el.scrollIntoView({ block: 'nearest' });
      }
    };
    window.visualViewport.addEventListener('resize', keepVisible);
    window.visualViewport.addEventListener('scroll', keepVisible);
  }
  $('prompt-input').addEventListener('focus', () => {
    window.setTimeout(() => $('prompt-input').scrollIntoView({ block: 'nearest' }), 200);
  });

  $('help-btn').addEventListener('click', () => speak(HELP_TEXT));
  $('stop-btn').addEventListener('click', () => { stopSpeaking(); resumeListening(); announce('Stopped.'); });
  $('frame-btn').addEventListener('click', async () => {
    try {
      const res = await fetch('/api/latest-frame', { headers: authHeaders() });
      if (res.status === 401) {
        showTokenControl('The gateway needs its access token before showing camera frames. Enter it in settings and tap Save.');
        return;
      }
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
    serverCurrentVoice = prefs.voice || '';
    const chip = $('lang-badge');
    if (chip) {
      const opt = (prefs.options || []).find((o) => o.code === language);
      chip.textContent = opt ? opt.label : language;
    }
  } catch (err) { /* gateway unreachable at boot */ }
}

function showTokenControl(message) {
  const control = $('token-control');
  if (control) {
    control.hidden = false;
    const input = $('token-input');
    if (input && !input.value) input.focus();
  }
  setStatus(message, 'idle');
  announce(message);
}

async function bootstrapConfig() {
  try {
    const res = await fetch('/api/config');
    if (!res.ok) return;
    const cfg = await res.json();
    if (cfg.auth_required && !authToken) {
      showTokenControl('This BlindSight gateway is password protected. Enter the access token in settings and tap Save.');
    }
  } catch (err) { /* offline boot */ }
}

window.addEventListener('load', async () => {
  if (!window.isSecureContext) {
    // Browsers only expose the microphone on localhost or HTTPS. Opening the
    // app as http://192.168.x.x:5000 silently kills every voice feature, which
    // is the most common cause of "the button does nothing".
    const msg = isIOS || /android/i.test(navigator.userAgent)
      ? 'Voice input needs HTTPS on a phone. Ask your helper to open the https:// address the Raspberry Pi starter prints, and accept the certificate warning once.'
      : 'Voice input needs localhost or HTTPS. Open http://localhost:5000 on this device, or restart the gateway with BLINDSIGHT_TLS set and accept the certificate once.';
    window.setTimeout(() => {
      setStatus(msg, 'idle');
      announce(msg);
    }, 500);
  }
  const savedSize = localStorage.getItem('blindsight_text_size');
  if (savedSize && savedSize !== 'md') document.documentElement.setAttribute('data-text-size', savedSize);
  if (synth) {
    synth.getVoices();
    synth.onvoiceschanged = fillVoiceSelect;
  }
  await bootstrapConfig();
  await loadPrefs();
  fillVoiceSelect();
  const gate = $('noise-gate-toggle');
  if (gate) gate.checked = noiseGate;
  wireEvents();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/static/sw.js', { scope: '/' }).catch(() => { /* optional */ });
  }
  if (!hasSR) {
    // iPhone Safari (and every iOS browser) exposes no SpeechRecognition at
    // all. Degrade honestly: dictation through the keyboard mic, spoken
    // replies as usual, and say so out loud instead of leaving a dead button.
    listenMode = 'button';
    localStorage.setItem('blindsight_mode', 'button');
    const modeSel = $('mode-select');
    modeSel.value = 'button';
    Array.from(modeSel.options).forEach((opt) => {
      if (opt.value !== 'button') {
        opt.disabled = true;
        opt.textContent += isIOS ? ' (not on iPhone)' : ' (needs Chrome/Edge)';
      }
    });
    if (gate) gate.disabled = true;
    setStatus(isIOS ? 'Tap the big button, then the keyboard mic.' : 'Type below, or use Chrome/Edge for voice.', 'idle');
    announce(isIOS
      ? 'BlindSight ready. On iPhone the browser cannot listen for a wake word. Tap the big button to open the keyboard, then tap its microphone to dictate. My answers will still be spoken to you. Type help for the command list.'
      : 'BlindSight ready. This browser has no speech input. Type your question, or use Chrome or Edge for voice. Type help for the command list.');
    return;
  }
  $('mode-select').value = listenMode;
  setStatus('Ready', 'idle');
  announce('BlindSight ready. ' + (listenMode === 'handsfree' ? 'Hands-free mode: just speak.' : 'Say hey BlindSight, or tap the big button.') + ' Say help for the command list.');
  // Open the mic analytics eagerly so the noise floor calibrates before the
  // first wake word; recognition itself starts only after a gesture if the
  // browser refuses pre-gesture capture.
  if (noiseGate) {
    const micOk = await openMicAnalytics();
    if (!micOk) announce('Noise gate needs microphone permission; it stays off.');
  }
  if (listenMode !== 'button') startListening();
});
