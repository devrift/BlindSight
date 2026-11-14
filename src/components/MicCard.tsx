import React, { useMemo } from 'react';
import { Mic, Volume2, Sparkles } from 'lucide-react';
import { AppState, ListenMode } from '../types';

interface MicCardProps {
  appState: AppState;
  listenMode: ListenMode;
  micHint: string;
  heardText: string;
  micMeterPct: number;
  isHot: boolean;
  isCue: boolean;
  onMicClick: () => void;
}

export const MicCard: React.FC<MicCardProps> = ({
  appState,
  listenMode,
  micHint,
  heardText,
  micMeterPct,
  isHot,
  isCue,
  onMicClick
}) => {
  const isListening = appState === 'listen';
  const isSpeaking = appState === 'speak';
  const isThinking = appState === 'think';

  const btnClass = [
    isListening ? 'listening' : '',
    isHot ? 'hot' : '',
    isSpeaking ? 'speaking' : '',
    isThinking ? 'thinking' : '',
    isCue ? 'cue' : ''
  ]
    .filter(Boolean)
    .join(' ');

  // Generate 18 dynamic equalizer bars with harmonic heights based on micMeterPct
  const spectrumBars = useMemo(() => {
    const barsCount = 18;
    return Array.from({ length: barsCount }, (_, i) => {
      // Bell-curve harmonic factor: middle bars are taller
      const harmonic = Math.sin((i / (barsCount - 1)) * Math.PI);
      const minHeight = 4;
      const height = isListening
        ? Math.max(minHeight, Math.round(harmonic * micMeterPct * 0.35 + (isHot ? 8 : 2)))
        : minHeight;
      return { id: i, height, active: height > minHeight, hot: isHot && height > 12 };
    });
  }, [micMeterPct, isListening, isHot]);

  return (
    <section className="card mic-card">
      {/* Floating Heard Pill */}
      {heardText ? (
        <div className="heard-pill" aria-live="polite">
          <Sparkles size={16} color="var(--accent)" aria-hidden="true" />
          <span>Heard: “{heardText}”</span>
        </div>
      ) : null}

      <div className="mic-btn-wrapper">
        {/* Animated Sonic Radar Rings */}
        <div
          className={`mic-radar-ring ${isListening ? 'listening' : ''} ${isHot ? 'hot' : ''} ${isSpeaking ? 'speaking' : ''}`}
          aria-hidden="true"
        />

        <button
          id="mic-btn"
          type="button"
          className={btnClass}
          onClick={onMicClick}
          aria-label="Tap to speak. Tap again to stop listening or to interrupt the answer."
        >
          {isSpeaking ? (
            <Volume2 size={46} aria-hidden="true" />
          ) : isListening ? (
            <Mic size={46} aria-hidden="true" />
          ) : (
            <svg
              width="46"
              height="46"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
              <line x1="12" y1="19" x2="12" y2="23"></line>
              <line x1="8" y1="23" x2="16" y2="23"></line>
            </svg>
          )}
        </button>
      </div>

      <p className="mic-hint" id="mic-hint">
        {micHint}
      </p>

      {/* 18-bar Real-time Equalizer Spectrum */}
      <div className="spectrum-visualizer" aria-hidden="true">
        {spectrumBars.map(bar => (
          <div
            key={bar.id}
            className={`spectrum-bar ${bar.active ? 'active' : ''} ${bar.hot ? 'hot' : ''}`}
            style={{ height: `${bar.height}px` }}
          />
        ))}
      </div>
    </section>
  );
};
