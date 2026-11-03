import React from 'react';
import { Mic, MicOff, Volume2 } from 'lucide-react';
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

  const btnClass = [
    isListening ? 'listening' : '',
    isSpeaking ? 'speaking' : '',
    isCue ? 'cue' : ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <section className="card mic-card">
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

      <p className="mic-hint" id="mic-hint">
        {micHint}
      </p>

      <div className="meter" aria-hidden="true">
        <div
          className={`mic-meter-bar ${isHot ? 'hot' : ''}`}
          id="mic-meter"
          style={{ width: `${micMeterPct}%` }}
        ></div>
      </div>

      <p className="heard-line" id="heard-line" aria-hidden="true">
        {heardText ? `I heard: “${heardText}”` : ''}
      </p>
    </section>
  );
};
