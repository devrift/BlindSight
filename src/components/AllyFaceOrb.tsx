import React, { useEffect, useRef } from 'react';

export type AllyOrbState = 'idle' | 'listen' | 'think' | 'speak' | 'confirming' | 'caution' | 'paused';

interface AllyFaceOrbProps {
  state: AllyOrbState;
  onClick?: () => void;
  size?: number;
  isDarkMode?: boolean;
}

/**
 * Animated Ally Smiley Companion Face (Web React).
 *
 * An expressive, warm, and tactile presence for blind and low-vision users:
 * - IDLE: Friendly calm eyes with natural periodic blinks and relaxed smile curve.
 * - LISTENING: Eyes widen with curious upward tilt; dynamic concentric audio ripples.
 * - THINKING: Eyes narrow into focused squint; soft horizontal shimmer across brow.
 * - SPEAKING: Fluid audio-reactive soundwave mouth line undulating with speech amplitude.
 * - CONFIRMING: Joyful smiling crescent eyes and radiant warm smile curve.
 * - CAUTION: Alert warning state with soft red-amber aura.
 * - PAUSED / SLEEPING: Relaxed sleepy curved arcs (⌒ ⌒) with calm resting flat mouth.
 */
export const AllyFaceOrb: React.FC<AllyFaceOrbProps> = ({
  state,
  onClick,
  size = 240,
  isDarkMode = true
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    const startTime = performance.now();

    const render = (time: number) => {
      const elapsed = (time - startTime) / 1000;
      const dpr = window.devicePixelRatio || 1;
      canvas.width = size * dpr;
      canvas.height = size * dpr;
      ctx.scale(dpr, dpr);

      ctx.clearRect(0, 0, size, size);

      const cx = size / 2;
      const cy = size / 2;
      const baseRadius = size * 0.37;

      const isListening = state === 'listen';
      const isSpeaking = state === 'speak';
      const isThinking = state === 'think';
      const isConfirming = state === 'confirming';
      const isCaution = state === 'caution';
      const isPaused = state === 'paused';

      // Organic breathing scale
      const breathing = Math.sin(elapsed * 2.4) * 0.03 + 1.0;
      const orbRadius = baseRadius * (isListening || isSpeaking ? breathing : 1.0);

      // Dynamic Colors
      let primaryGlow = '#00D2FF';
      let secondaryGlow = '#1E6FFF';
      if (isCaution) {
        primaryGlow = '#EF4444';
        secondaryGlow = '#F97316';
      } else if (isConfirming) {
        primaryGlow = '#00D2FF';
        secondaryGlow = '#10B981';
      } else if (isThinking) {
        primaryGlow = '#1E6FFF';
        secondaryGlow = '#00D2FF';
      } else if (isPaused) {
        primaryGlow = '#475569';
        secondaryGlow = '#1E293B';
      }

      // 1. Concentric Ripple Waves for Listening
      if (isListening) {
        for (let i = 0; i < 3; i++) {
          const phase = ((elapsed * 0.7) + i * 0.33) % 1.0;
          const r = orbRadius + phase * (size * 0.24);
          const alpha = (1.0 - phase) * 0.45;
          ctx.beginPath();
          ctx.arc(cx, cy, r, 0, Math.PI * 2);
          ctx.strokeStyle = `rgba(0, 210, 255, ${alpha})`;
          ctx.lineWidth = 2.2;
          ctx.stroke();
        }
      }

      // 2. Ambient Volumetric Glow Halo
      const glowR = orbRadius * (isSpeaking ? 1.45 : 1.35);
      const glowGrad = ctx.createRadialGradient(cx, cy, orbRadius * 0.3, cx, cy, glowR);
      glowGrad.addColorStop(0, primaryGlow + (isSpeaking ? '88' : isPaused ? '22' : '44'));
      glowGrad.addColorStop(0.65, secondaryGlow + '18');
      glowGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = glowGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, glowR, 0, Math.PI * 2);
      ctx.fill();

      // 3. True Liquid Glass Orb Core
      const coreGrad = ctx.createRadialGradient(
        cx - orbRadius * 0.25,
        cy - orbRadius * 0.3,
        orbRadius * 0.1,
        cx,
        cy,
        orbRadius * 1.25
      );
      if (isDarkMode) {
        coreGrad.addColorStop(0, '#0f2752');
        coreGrad.addColorStop(0.6, '#0a1934');
        coreGrad.addColorStop(1, '#060b14');
      } else {
        coreGrad.addColorStop(0, '#ffffff');
        coreGrad.addColorStop(0.65, '#e2edff');
        coreGrad.addColorStop(1, '#c7deff');
      }
      ctx.fillStyle = coreGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, orbRadius, 0, Math.PI * 2);
      ctx.fill();

      // Liquid Glass Rim Border
      ctx.strokeStyle = primaryGlow + 'bb';
      ctx.lineWidth = 2.4;
      ctx.stroke();

      // Specular Reflection
      const specX = cx;
      const specY = cy - orbRadius * 0.45;
      const specW = orbRadius * 0.82;
      const specH = orbRadius * 0.25;
      ctx.save();
      const specGrad = ctx.createRadialGradient(specX, specY, 0, specX, specY, specW / 2);
      specGrad.addColorStop(0, isDarkMode ? 'rgba(255, 255, 255, 0.4)' : 'rgba(255, 255, 255, 0.7)');
      specGrad.addColorStop(0.7, 'rgba(255, 255, 255, 0.05)');
      specGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = specGrad;
      ctx.beginPath();
      ctx.ellipse(specX, specY, specW / 2, specH / 2, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      // 4. Horizontal Brow Shimmer (Thinking State)
      if (isThinking) {
        const browY = cy - orbRadius * 0.35;
        const browW = orbRadius * 0.85;
        const shimmerPhase = Math.sin(elapsed * 3.5);
        const shimmerX = cx + shimmerPhase * (browW / 2);
        ctx.save();
        const shimGrad = ctx.createLinearGradient(shimmerX - 35, 0, shimmerX + 35, 0);
        shimGrad.addColorStop(0, 'transparent');
        shimGrad.addColorStop(0.5, '#ffffff');
        shimGrad.addColorStop(1, 'transparent');
        ctx.strokeStyle = shimGrad;
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(cx - browW / 2, browY);
        ctx.lineTo(cx + browW / 2, browY);
        ctx.stroke();
        ctx.restore();
      }

      // 5. The Ally Smiley Eyes
      const eyeSpacing = orbRadius * 0.42;
      const eyeY = cy - orbRadius * 0.12;
      const baseEyeW = 16;
      const baseEyeH = 26;

      // Natural blink cycle: dips to 0.12 every 4.2s
      const blinkCycle = elapsed % 4.2;
      const isBlinking = blinkCycle > 4.04;
      const blinkScale = isBlinking ? 0.12 : 1.0;

      let eyeHeight = baseEyeH * blinkScale;
      let eyeWidth = baseEyeW;
      let eyeTilt = 0;

      if (isListening) {
        eyeTilt = 0.15; // Curious upward tilt
        eyeHeight *= 1.2;
        eyeWidth *= 1.15;
      } else if (isThinking) {
        eyeTilt = -0.10; // Focused squint inward
        eyeHeight = 8;
        eyeWidth = 22;
      } else if (isConfirming) {
        eyeHeight = 16;
      } else if (isCaution) {
        eyeHeight = 32;
        eyeWidth = 20;
      } else if (isSpeaking) {
        eyeHeight = baseEyeH * 1.15;
      }

      const eyeFill = isDarkMode ? '#FFFFFF' : '#0F172A';

      const drawEye = (x: number, angle: number) => {
        ctx.save();
        ctx.translate(x, eyeY);
        ctx.rotate(angle);

        if (isPaused) {
          // Relaxed, sleepy curved arc (⌒)
          ctx.beginPath();
          const w = eyeWidth * 1.3;
          ctx.moveTo(-w / 2, 4);
          ctx.quadraticCurveTo(0, -7, w / 2, 4);
          ctx.strokeStyle = '#00D2FF';
          ctx.lineWidth = 3.6;
          ctx.lineCap = 'round';
          ctx.stroke();
        } else if (isConfirming) {
          // Joyful arched crescent (^)
          ctx.beginPath();
          ctx.moveTo(-eyeWidth * 0.55, eyeHeight * 0.4);
          ctx.quadraticCurveTo(0, -eyeHeight * 0.5, eyeWidth * 0.55, eyeHeight * 0.4);
          ctx.strokeStyle = '#00D2FF';
          ctx.lineWidth = 4;
          ctx.lineCap = 'round';
          ctx.stroke();
        } else {
          // Rounded Pill Eye
          ctx.fillStyle = eyeFill;
          ctx.beginPath();
          const r = eyeWidth / 2;
          ctx.roundRect(-eyeWidth / 2, -eyeHeight / 2, eyeWidth, eyeHeight, [r, r, r, r]);
          ctx.fill();
        }
        ctx.restore();
      };

      drawEye(cx - eyeSpacing, eyeTilt);
      drawEye(cx + eyeSpacing, -eyeTilt);

      // 6. Dynamic Mouth Curve / Soundwave Waveform
      const mouthY = cy + orbRadius * 0.36;

      if (isSpeaking) {
        // Fluid audio-reactive undulating soundwave
        const waveWidth = orbRadius * 0.92;
        const startX = cx - waveWidth / 2;
        ctx.save();
        ctx.beginPath();
        const steps = 30;
        for (let i = 0; i <= steps; i++) {
          const frac = i / steps;
          const x = startX + frac * waveWidth;
          const envelope = Math.sin(frac * Math.PI);
          const y = mouthY + Math.sin(frac * 18 + elapsed * 7.5) * (12 * envelope);
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.strokeStyle = '#00D2FF';
        ctx.shadowColor = '#1E6FFF';
        ctx.shadowBlur = 10;
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.stroke();
        ctx.restore();
      } else if (isPaused) {
        // Calm flat resting mouth line
        const mouthW = orbRadius * 0.42;
        ctx.beginPath();
        ctx.moveTo(cx - mouthW / 2, mouthY);
        ctx.lineTo(cx + mouthW / 2, mouthY);
        ctx.strokeStyle = '#64748B';
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else if (isConfirming) {
        // Joyful warm smile curve
        const smileW = orbRadius * 0.78;
        ctx.beginPath();
        ctx.moveTo(cx - smileW / 2, mouthY - 4);
        ctx.quadraticCurveTo(cx, mouthY + 16, cx + smileW / 2, mouthY - 4);
        ctx.strokeStyle = '#00D2FF';
        ctx.shadowColor = '#00D2FF';
        ctx.shadowBlur = 12;
        ctx.lineWidth = 4.2;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else if (isThinking) {
        // Concentrated focus bar with center spark
        const barW = orbRadius * 0.44;
        ctx.beginPath();
        ctx.moveTo(cx - barW / 2, mouthY);
        ctx.lineTo(cx + barW / 2, mouthY);
        ctx.strokeStyle = '#00D2FF';
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(cx, mouthY, 3, 0, Math.PI * 2);
        ctx.fillStyle = '#FFFFFF';
        ctx.fill();
      } else if (isCaution) {
        // Alert straight warning line
        const warnW = orbRadius * 0.65;
        ctx.beginPath();
        ctx.moveTo(cx - warnW / 2, mouthY + 2);
        ctx.lineTo(cx + warnW / 2, mouthY + 2);
        ctx.strokeStyle = '#EF4444';
        ctx.lineWidth = 3.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else if (isListening) {
        // Open attentive curve
        const listenW = orbRadius * 0.52;
        ctx.beginPath();
        ctx.moveTo(cx - listenW / 2, mouthY);
        ctx.quadraticCurveTo(cx, mouthY + 8, cx + listenW / 2, mouthY);
        ctx.strokeStyle = '#00D2FF';
        ctx.lineWidth = 3.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else {
        // Warm relaxed friendly smile curve
        const idleW = orbRadius * 0.5;
        ctx.beginPath();
        ctx.moveTo(cx - idleW / 2, mouthY);
        ctx.quadraticCurveTo(cx, mouthY + 8, cx + idleW / 2, mouthY);
        ctx.strokeStyle = isDarkMode ? 'rgba(255, 255, 255, 0.9)' : '#0F172A';
        ctx.lineWidth = 3.2;
        ctx.lineCap = 'round';
        ctx.stroke();
      }

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [state, size, isDarkMode]);

  return (
    <div
      onClick={onClick}
      role="button"
      tabIndex={0}
      aria-label="BlindSight Ally Smiley Companion Face"
      className="cursor-pointer transition-transform duration-200 active:scale-95 flex items-center justify-center select-none"
      style={{ width: size, height: size }}
    >
      <canvas
        ref={canvasRef}
        style={{ width: size, height: size }}
        className="block"
      />
    </div>
  );
};
