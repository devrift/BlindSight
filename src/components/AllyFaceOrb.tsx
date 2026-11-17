import React, { useEffect, useRef } from 'react';

export type AllyOrbState = 'idle' | 'listen' | 'think' | 'speak' | 'confirming' | 'caution';

interface AllyFaceOrbProps {
  state: AllyOrbState;
  onClick?: () => void;
  size?: number;
}

export const AllyFaceOrb: React.FC<AllyFaceOrbProps> = ({
  state,
  onClick,
  size = 250
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let startTime = performance.now();

    const render = (time: number) => {
      const elapsed = (time - startTime) / 1000;
      const dpr = window.devicePixelRatio || 1;
      canvas.width = size * dpr;
      canvas.height = size * dpr;
      ctx.scale(dpr, dpr);

      ctx.clearRect(0, 0, size, size);

      const cx = size / 2;
      const cy = size / 2;
      const baseRadius = size * 0.36;

      // Organic breathing scale
      const breathing = Math.sin(elapsed * 2.2) * 0.04 + 1.0;
      const isListening = state === 'listen';
      const isSpeaking = state === 'speak';
      const isThinking = state === 'think';
      const isConfirming = state === 'confirming';
      const isCaution = state === 'caution';

      const orbRadius = baseRadius * (isListening || isSpeaking ? breathing : 1.0);

      // Colors
      let primaryGlow = '#38bdf8';
      let secondaryGlow = '#0f172a';
      if (isCaution) {
        primaryGlow = '#ef4444';
        secondaryGlow = '#f97316';
      } else if (isConfirming) {
        primaryGlow = '#fbbf24';
        secondaryGlow = '#10b981';
      } else if (isThinking || isSpeaking) {
        primaryGlow = '#f59e0b';
        secondaryGlow = '#fbbf24';
      }

      // 1. Concentric Ripple Waves for Listening
      if (isListening) {
        for (let i = 0; i < 3; i++) {
          const phase = ((elapsed * 0.7) + i * 0.33) % 1.0;
          const r = orbRadius + phase * (size * 0.24);
          const alpha = (1.0 - phase) * 0.45;
          ctx.beginPath();
          ctx.arc(cx, cy, r, 0, Math.PI * 2);
          ctx.strokeStyle = `rgba(56, 189, 248, ${alpha})`;
          ctx.lineWidth = 2.5;
          ctx.stroke();
        }
      }

      // 2. Ambient Glowing Halo
      const glowGrad = ctx.createRadialGradient(cx, cy, orbRadius * 0.5, cx, cy, orbRadius * 1.4);
      glowGrad.addColorStop(0, primaryGlow + (isSpeaking || isConfirming ? '88' : '55'));
      glowGrad.addColorStop(0.6, secondaryGlow + '22');
      glowGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = glowGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, orbRadius * 1.4, 0, Math.PI * 2);
      ctx.fill();

      // 3. Dark Obsidian Glass Core
      const coreGrad = ctx.createRadialGradient(cx, cy * 0.9, orbRadius * 0.1, cx, cy, orbRadius);
      coreGrad.addColorStop(0, '#161f30');
      coreGrad.addColorStop(0.7, '#0f172a');
      coreGrad.addColorStop(1, '#0b0e14');
      ctx.fillStyle = coreGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, orbRadius, 0, Math.PI * 2);
      ctx.fill();

      // Rim Stroke
      ctx.strokeStyle = primaryGlow + 'aa';
      ctx.lineWidth = 2.2;
      ctx.stroke();

      // 4. Rotational Shimmer for Thinking
      if (isThinking) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(elapsed * 3.2);
        const arcGrad = ctx.createLinearGradient(-orbRadius, 0, orbRadius, 0);
        arcGrad.addColorStop(0, 'transparent');
        arcGrad.addColorStop(0.5, '#fbbf24');
        arcGrad.addColorStop(1, '#f59e0b');
        ctx.strokeStyle = arcGrad;
        ctx.lineWidth = 3.5;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.arc(0, 0, orbRadius, 0, Math.PI * 0.65);
        ctx.stroke();
        ctx.restore();
      }

      // 5. Living Eyes
      const eyeSpacing = orbRadius * 0.44;
      const eyeY = cy - (orbRadius * 0.16);
      const baseEyeW = 18;
      const baseEyeH = 28;

      // Periodic blink every ~4.2s
      const blinkCycle = elapsed % 4.2;
      const isBlinking = blinkCycle > 4.0;
      const blinkScale = isBlinking ? 0.12 : 1.0;

      let eyeHeight = baseEyeH * blinkScale;
      let eyeWidth = baseEyeW;
      let tilt = 0;

      if (isListening) {
        tilt = 0.14; // curious upward tilt
        eyeHeight *= 1.05;
      } else if (isThinking) {
        tilt = -0.10; // focused inward squint
        eyeHeight = 8;
        eyeWidth = 24;
      } else if (isConfirming) {
        eyeHeight = 16;
      } else if (isCaution) {
        eyeHeight = 32;
        eyeWidth = 24;
      } else if (isSpeaking) {
        eyeHeight = baseEyeH * 1.15;
      }

      const drawEye = (x: number, angle: number) => {
        ctx.save();
        ctx.translate(x, eyeY);
        ctx.rotate(angle);

        if (isConfirming) {
          // Joyful arched crescent (^)
          ctx.beginPath();
          ctx.moveTo(-eyeWidth * 0.55, eyeHeight * 0.4);
          ctx.quadraticCurveTo(0, -eyeHeight * 0.5, eyeWidth * 0.55, eyeHeight * 0.4);
          ctx.strokeStyle = '#fbbf24';
          ctx.lineWidth = 4;
          ctx.lineCap = 'round';
          ctx.stroke();
        } else {
          // Rounded Pill Eye
          ctx.fillStyle = '#ffffff';
          ctx.shadowColor = primaryGlow;
          ctx.shadowBlur = 12;
          ctx.beginPath();
          const r = eyeWidth / 2;
          ctx.roundRect(-eyeWidth / 2, -eyeHeight / 2, eyeWidth, eyeHeight, [r, r, r, r]);
          ctx.fill();
          ctx.shadowBlur = 0;
        }
        ctx.restore();
      };

      drawEye(cx - eyeSpacing, tilt);
      drawEye(cx + eyeSpacing, -tilt);

      // 6. Expressive Mouth / Fluid Soundwave Line
      const mouthY = cy + (orbRadius * 0.38);

      if (isSpeaking) {
        // Fluid audio-reactive undulating sine soundwave
        const waveWidth = orbRadius * 0.92;
        const startX = cx - waveWidth / 2;
        ctx.save();
        ctx.beginPath();
        const steps = 30;
        for (let i = 0; i <= steps; i++) {
          const frac = i / steps;
          const x = startX + frac * waveWidth;
          const envelope = Math.sin(frac * Math.PI);
          const y = mouthY + Math.sin(frac * 18 + elapsed * 7.0) * (12 * envelope);
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.strokeStyle = '#fbbf24';
        ctx.shadowColor = '#f59e0b';
        ctx.shadowBlur = 10;
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.stroke();
        ctx.restore();
      } else if (isConfirming) {
        // Joyful warm smile curve
        const smileW = orbRadius * 0.8;
        ctx.beginPath();
        ctx.moveTo(cx - smileW / 2, mouthY - 4);
        ctx.quadraticCurveTo(cx, mouthY + 16, cx + smileW / 2, mouthY - 4);
        ctx.strokeStyle = '#fbbf24';
        ctx.shadowColor = '#fbbf24';
        ctx.shadowBlur = 12;
        ctx.lineWidth = 4.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else if (isThinking) {
        // Concentrated focus bar with center spark
        const barW = orbRadius * 0.44;
        ctx.beginPath();
        ctx.moveTo(cx - barW / 2, mouthY);
        ctx.lineTo(cx + barW / 2, mouthY);
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(cx, mouthY, 3, 0, Math.PI * 2);
        ctx.fillStyle = '#ffffff';
        ctx.fill();
      } else if (isCaution) {
        // Alert straight warning line
        const warnW = orbRadius * 0.65;
        ctx.beginPath();
        ctx.moveTo(cx - warnW / 2, mouthY + 2);
        ctx.lineTo(cx + warnW / 2, mouthY + 2);
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 3.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else if (isListening) {
        // Open attentive curve
        const listenW = orbRadius * 0.52;
        ctx.beginPath();
        ctx.moveTo(cx - listenW / 2, mouthY);
        ctx.quadraticCurveTo(cx, mouthY + 8, cx + listenW / 2, mouthY);
        ctx.strokeStyle = '#38bdf8';
        ctx.lineWidth = 3.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      } else {
        // Idle gentle resting smile
        const idleW = orbRadius * 0.48;
        ctx.beginPath();
        ctx.moveTo(cx - idleW / 2, mouthY);
        ctx.quadraticCurveTo(cx, mouthY + 5, cx + idleW / 2, mouthY);
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.75)';
        ctx.lineWidth = 2.8;
        ctx.lineCap = 'round';
        ctx.stroke();
      }

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, [state, size]);

  return (
    <div
      onClick={onClick}
      style={{
        width: size,
        height: size,
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        userSelect: 'none',
        touchAction: 'manipulation'
      }}
      role="button"
      tabIndex={0}
      aria-label={`Ally face presence. State: ${state}. Tap to talk.`}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick?.();
        }
      }}
    >
      <canvas
        ref={canvasRef}
        style={{
          width: size,
          height: size,
          display: 'block'
        }}
      />
    </div>
  );
};
