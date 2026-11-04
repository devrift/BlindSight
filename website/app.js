/**
 * BlindSight Showcase Dashboard & Simulator
 */
document.addEventListener('DOMContentLoaded', () => {
  // Elements
  const leftSlider = document.getElementById('leftSlider');
  const centerSlider = document.getElementById('centerSlider');
  const rightSlider = document.getElementById('rightSlider');
  const leftVal = document.getElementById('leftVal');
  const centerVal = document.getElementById('centerVal');
  const rightVal = document.getElementById('rightVal');

  const telemetryBadge = document.getElementById('telemetryBadge');
  const telemetryText = document.getElementById('telemetryText');
  const boxDecision = document.getElementById('boxDecision');
  const activeClipName = document.getElementById('activeClipName');
  const triggerAudioBtn = document.getElementById('triggerAudioBtn');
  const presetDangerBtn = document.getElementById('presetDangerBtn');
  const presetClearBtn = document.getElementById('presetClearBtn');

  const canvas = document.getElementById('radarCanvas');
  const ctx = canvas.getContext('2d');

  let currentDecision = 'clear';
  let currentClip = 'audio/clear.wav';
  let activeAudio = null;

  // ------------------------------------------------ Radar Canvas Rendering
  let sweepAngle = 0;

  function renderRadar() {
    const w = canvas.width;
    const h = canvas.height;
    const cx = w / 2;
    const cy = h / 2;
    const maxRadius = w * 0.44;

    ctx.clearRect(0, 0, w, h);

    // Background gradient
    const bgGrad = ctx.createRadialGradient(cx, cy, 10, cx, cy, maxRadius);
    bgGrad.addColorStop(0, 'rgba(15, 23, 42, 0.4)');
    bgGrad.addColorStop(1, 'rgba(3, 7, 18, 0.95)');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, w, h);

    // Concentric Range Rings: 25cm, 45cm, 100cm, 200cm
    const rings = [
      { cm: 25, label: '25cm STOP', color: 'rgba(239, 68, 68, 0.45)' },
      { cm: 45, label: '45cm STEER', color: 'rgba(245, 158, 11, 0.35)' },
      { cm: 100, label: '100cm CAUTION', color: 'rgba(234, 179, 8, 0.25)' },
      { cm: 200, label: '200cm', color: 'rgba(255, 255, 255, 0.1)' }
    ];

    rings.forEach(r => {
      const radius = (r.cm / 250) * maxRadius;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.strokeStyle = r.color;
      ctx.lineWidth = 1;
      ctx.setLineDash([4, 4]);
      ctx.stroke();
      ctx.setLineDash([]);

      // Label
      ctx.fillStyle = r.color;
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.fillText(r.label, cx + 4, cy - radius + 11);
    });

    // Crosshairs
    ctx.beginPath();
    ctx.moveTo(cx, cy - maxRadius);
    ctx.lineTo(cx, cy + maxRadius);
    ctx.moveTo(cx - maxRadius, cy);
    ctx.lineTo(cx + maxRadius, cy);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // 3 Field of View Cones: Left (-30 deg), Center (0 deg / top), Right (+30 deg)
    const angles = [
      { name: 'left', deg: -35, dist: parseInt(leftSlider.value, 10) },
      { name: 'center', deg: 0, dist: parseInt(centerSlider.value, 10) },
      { name: 'right', deg: 35, dist: parseInt(rightSlider.value, 10) }
    ];

    angles.forEach(sensor => {
      const rad = ((sensor.deg - 90) * Math.PI) / 180;
      const targetR = Math.min((sensor.dist / 250) * maxRadius, maxRadius);
      const px = cx + Math.cos(rad) * targetR;
      const py = cy + Math.sin(rad) * targetR;

      // Distance ray
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(px, py);
      ctx.strokeStyle = 'rgba(245, 158, 11, 0.2)';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Blip color determination
      let blipColor = '#10b981';
      if (sensor.dist < 25) blipColor = '#ef4444';
      else if (sensor.dist <= 45) blipColor = '#f59e0b';
      else if (sensor.dist < 100) blipColor = '#eab308';

      // Blip glow
      ctx.beginPath();
      ctx.arc(px, py, 9, 0, Math.PI * 2);
      ctx.fillStyle = blipColor;
      ctx.shadowColor = blipColor;
      ctx.shadowBlur = 15;
      ctx.fill();
      ctx.shadowBlur = 0;

      // Inner white dot
      ctx.beginPath();
      ctx.arc(px, py, 3, 0, Math.PI * 2);
      ctx.fillStyle = '#ffffff';
      ctx.fill();

      // Distance label next to blip
      ctx.fillStyle = '#f8fafc';
      ctx.font = 'bold 10px "JetBrains Mono", monospace';
      ctx.fillText(`${sensor.dist}cm`, px + 10, py - 6);
    });

    // Center user marker (Wearer)
    ctx.beginPath();
    ctx.arc(cx, cy, 6, 0, Math.PI * 2);
    ctx.fillStyle = '#f59e0b';
    ctx.shadowColor = '#f59e0b';
    ctx.shadowBlur = 10;
    ctx.fill();
    ctx.shadowBlur = 0;

    sweepAngle += 0.02;
    requestAnimationFrame(renderRadar);
  }

  // ------------------------------------------------ Decision Evaluation
  function evaluateSimulation() {
    const l = parseInt(leftSlider.value, 10);
    const c = parseInt(centerSlider.value, 10);
    const r = parseInt(rightSlider.value, 10);

    leftVal.textContent = `${l} cm`;
    centerVal.textContent = `${c} cm`;
    rightVal.textContent = `${r} cm`;

    const min = Math.min(l, c, r);

    telemetryBadge.classList.remove('danger', 'warn');

    if (min < 25) {
      currentDecision = 'stop';
      currentClip = 'audio/stop.wav';
      telemetryBadge.classList.add('danger');
      telemetryText.textContent = 'STATUS: CRITICAL STOP (<25cm)';
      boxDecision.innerHTML = '<span style="color:#ef4444">⚠️ CRITICAL STOP!</span> Immediate obstacle detected under 25 cm.';
    } else if (min <= 45) {
      telemetryBadge.classList.add('warn');
      if (l < r) {
        currentDecision = 'steer_right';
        currentClip = 'audio/right.wav';
        telemetryText.textContent = 'STATUS: STEER RIGHT';
        boxDecision.innerHTML = '<span style="color:#f59e0b">➡️ STEER RIGHT.</span> Obstacle near left sensor (25-45 cm).';
      } else {
        currentDecision = 'steer_left';
        currentClip = 'audio/left.wav';
        telemetryText.textContent = 'STATUS: STEER LEFT';
        boxDecision.innerHTML = '<span style="color:#f59e0b">⬅️ STEER LEFT.</span> Obstacle near right sensor (25-45 cm).';
      }
    } else if (min < 100) {
      currentDecision = 'careful';
      currentClip = 'audio/careful.wav';
      telemetryBadge.classList.add('warn');
      telemetryText.textContent = 'STATUS: PROCEED CAREFULLY (<100cm)';
      boxDecision.innerHTML = '<span style="color:#eab308">⚡ CAUTION.</span> Obstacle in path under 1 meter.';
    } else {
      currentDecision = 'clear';
      currentClip = 'audio/clear.wav';
      telemetryText.textContent = 'STATUS: PATH CLEAR (>100cm)';
      boxDecision.innerHTML = '<span style="color:#10b981">✅ PATH CLEAR.</span> Walking corridor unobstructed.';
    }

    activeClipName.textContent = currentClip;
  }

  // Audio Playback
  function playCurrentClip() {
    if (activeAudio) {
      activeAudio.pause();
      activeAudio.currentTime = 0;
    }
    activeAudio = new Audio(currentClip);
    activeAudio.play().catch(err => {
      console.warn('Audio play restricted by browser policy:', err);
    });
  }

  // Event Listeners for Sliders
  [leftSlider, centerSlider, rightSlider].forEach(slider => {
    slider.addEventListener('input', evaluateSimulation);
  });

  triggerAudioBtn.addEventListener('click', playCurrentClip);

  presetDangerBtn.addEventListener('click', () => {
    leftSlider.value = 18;
    centerSlider.value = 22;
    rightSlider.value = 40;
    evaluateSimulation();
    playCurrentClip();
  });

  presetClearBtn.addEventListener('click', () => {
    leftSlider.value = 160;
    centerSlider.value = 200;
    rightSlider.value = 180;
    evaluateSimulation();
    playCurrentClip();
  });

  // ------------------------------------------------ 2-Minute Practice Timer
  let timerInterval = null;
  let timeRemaining = 120; // 2 minutes

  const timerClock = document.getElementById('scriptTimer');
  const startTimerBtn = document.getElementById('startTimerBtn');
  const resetTimerBtn = document.getElementById('resetTimerBtn');

  function updateClockDisplay() {
    const mins = Math.floor(timeRemaining / 60);
    const secs = timeRemaining % 60;
    timerClock.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  startTimerBtn.addEventListener('click', () => {
    if (timerInterval) {
      // Pause
      clearInterval(timerInterval);
      timerInterval = null;
      startTimerBtn.textContent = 'Resume Timer';
    } else {
      // Start
      startTimerBtn.textContent = 'Pause Timer';
      timerInterval = setInterval(() => {
        if (timeRemaining > 0) {
          timeRemaining--;
          updateClockDisplay();
        } else {
          clearInterval(timerInterval);
          timerInterval = null;
          timerClock.textContent = '00:00 (Done!)';
          startTimerBtn.textContent = 'Start Timer';
        }
      }, 1000);
    }
  });

  resetTimerBtn.addEventListener('click', () => {
    clearInterval(timerInterval);
    timerInterval = null;
    timeRemaining = 120;
    updateClockDisplay();
    startTimerBtn.textContent = 'Start Timer';
  });

  // Initial runs
  evaluateSimulation();
  renderRadar();
});
