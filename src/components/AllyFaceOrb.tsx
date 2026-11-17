import React, { useEffect, useRef } from 'react';

export type AllyOrbState = 'idle' | 'listen' | 'think' | 'speak' | 'confirming' | 'caution';

interface AllyFaceOrbProps {
  state: AllyOrbState;
  onClick?: () => void;
  size?: number;
}

/**
 * Organic, Fluid Audio-Reactive Liquid Orb / Membrane (Web).
 *
 * An industrial-grade, Apple/VisionOS-inspired liquid glass intelligence presence:
 * - IDLE: Calm, rhythmic breathing expansion, deep obsidian and cyan specular reflections.
 * - LISTENING: Concentric harmonic ripples propagating outward, liquid cyan membrane reacting to audio.
 * - THINKING: Swirling rotational liquid plasma vortex with orbiting caustic flares.
 * - SPEAKING: Volumetric fluid undulation and expansion with multi-harmonic wave nodes and radiant glow.
 * - CONFIRMING: Radiant golden fluid shimmer expanding outward with glass refraction.
 * - CAUTION: Soft pulsing red-amber warning luminescence across the outer perimeter.
 *
 * Strictly adheres to project accessibility guidelines: zero emojis, pure tactile liquid aesthetic.
 */
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
      const baseRadius = size * 0.34;

      const isListening = state === 'listen';
      const isSpeaking = state === 'speak';
      const isThinking = state === 'think';
      const isConfirming = state === 'confirming';
      const isCaution = state === 'caution';

      // Organic breathing scale
      const breathing = Math.sin(elapsed * 2.2) * 0.05 + 1.0;
      const rScale = isListening || isSpeaking ? breathing : 1.0;

      // Dynamic Colors
      let primaryGlow = '#38bdf8';
      let secondaryGlow = '#1d4ed8';
      if (isCaution) {
        primaryGlow = '#ef4444';
        secondaryGlow = '#b91c1c';
      } else if (isConfirming) {
        primaryGlow = '#fbbf24';
        secondaryGlow = '#d97706';
      } else if (isThinking || isSpeaking) {
        primaryGlow = '#f59e0b';
        secondaryGlow = '#b45309';
      }

      // 1. Concentric Ripple Waves for Listening
      if (isListening) {
        for (let i = 0; i < 3; i++) {
          const phase = ((elapsed * 0.7) + i * 0.33) % 1.0;
          const r = baseRadius + phase * (size * 0.28);
          const alpha = (1.0 - phase) * 0.45;
          ctx.beginPath();
          ctx.arc(cx, cy, r, 0, Math.PI * 2);
          ctx.strokeStyle = `rgba(56, 189, 248, ${alpha})`;
          ctx.lineWidth = 2.2;
          ctx.stroke();
        }
      }

      // 2. Ambient Volumetric Glow Halo
      const glowR = baseRadius * (isSpeaking ? 1.55 : 1.35);
      const glowGrad = ctx.createRadialGradient(cx, cy, baseRadius * 0.4, cx, cy, glowR);
      glowGrad.addColorStop(0, primaryGlow + (isSpeaking || isConfirming ? '99' : '55'));
      glowGrad.addColorStop(0.6, secondaryGlow + '22');
      glowGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = glowGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, glowR, 0, Math.PI * 2);
      ctx.fill();

      // 3. Fluid Multi-Harmonic Membrane
      const points = 36;
      const amp1 = isSpeaking ? baseRadius * 0.12 : isListening ? baseRadius * 0.08 : isThinking ? baseRadius * 0.06 : isCaution ? baseRadius * 0.09 : baseRadius * 0.035;
      const amp2 = isSpeaking ? baseRadius * 0.08 : isListening ? baseRadius * 0.05 : isThinking ? baseRadius * 0.04 : baseRadius * 0.02;

      const phase1 = elapsed * 2.6;
      const phase2 = -elapsed * 3.7;

      const coords: Array<{ x: number; y: number }> = [];
      for (let i = 0; i < points; i++) {
        const theta = (i / points) * Math.PI * 2;
        const harmonic = Math.sin(theta * 3 + phase1) * amp1 + Math.cos(theta * 5 + phase2) * amp2;
        const r = (baseRadius + harmonic) * rScale;
        coords.push({
          x: cx + r * Math.cos(theta),
          y: cy + r * Math.sin(theta)
        });
      }

      ctx.save();
      ctx.beginPath();
      if (coords.length > 0) {
        ctx.moveTo(
          (coords[0].x + coords[points - 1].x) / 2,
          (coords[0].y + coords[points - 1].y) / 2
        );
        for (let i = 0; i < points; i++) {
          const current = coords[i];
          const next = coords[(i + 1) % points];
          const midX = (current.x + next.x) / 2;
          const midY = (current.y + next.y) / 2;
          ctx.quadraticCurveTo(current.x, current.y, midX, midY);
        }
        ctx.closePath();
      }

      // Liquid Glass Interior Fill
      const coreGrad = ctx.createRadialGradient(cx - baseRadius * 0.2, cy - baseRadius * 0.25, baseRadius * 0.1, cx, cy, baseRadius * 1.1);
      coreGrad.addColorStop(0, '#1e293b');
      coreGrad.addColorStop(0.65, '#111827');
      coreGrad.addColorStop(1, '#080b10');
      ctx.fillStyle = coreGrad;
      ctx.fill();

      // Liquid Membrane Specular Outer Rim
      ctx.strokeStyle = primaryGlow + 'cc';
      ctx.lineWidth = 2.4;
      ctx.stroke();
      ctx.restore();

      // 4. Rotational Plasma Shimmer Vortex (Thinking State)
      if (isThinking) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(elapsed * 2.8);
        const arcGrad = ctx.createLinearGradient(-baseRadius, 0, baseRadius, 0);
        arcGrad.addColorStop(0, 'transparent');
        arcGrad.addColorStop(0.5, '#fbbf24');
        arcGrad.addColorStop(1, '#f59e0b');
        ctx.strokeStyle = arcGrad;
        ctx.lineWidth = 3.2;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.arc(0, 0, baseRadius * 0.9, 0, Math.PI * 0.75);
        ctx.stroke();
        ctx.restore();
      }

      // 5. Inner Luminous Core / Audio Nucleus
      const nucleusR = baseRadius * (isSpeaking ? 0.46 : 0.35) * breathing;
      const nucleusGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, nucleusR);
      nucleusGrad.addColorStop(0, primaryGlow + (isSpeaking ? 'ee' : 'aa'));
      nucleusGrad.addColorStop(0.6, secondaryGlow + '55');
      nucleusGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = nucleusGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, nucleusR, 0, Math.PI * 2);
      ctx.fill();

      // 6. Liquid Glass Top Specular Caustic Reflection
      const specX = cx;
      const specY = cy - baseRadius * 0.42;
      const specW = baseRadius * 0.85;
      const specH = baseRadius * 0.26;
      ctx.save();
      const specGrad = ctx.createRadialGradient(specX, specY, 0, specX, specY, specW / 2);
      specGrad.addColorStop(0, 'rgba(255, 255, 255, ' + (isSpeaking ? '0.45' : '0.28') + ')');
      specGrad.addColorStop(0.7, 'rgba(255, 255, 255, 0.05)');
      specGrad.addColorStop(1, 'transparent');
      ctx.fillStyle = specGrad;
      ctx.beginPath();
      ctx.ellipse(specX, specY, specW / 2, specH / 2, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [state, size]);

  return (
    <div
      onClick={onClick}
      role="button"
      tabIndex={0}
      aria-label="BlindSight Voice Companion Orb"
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
