import React from 'react';
import { AppState } from '../types';

interface StatusCardProps {
  appState: AppState;
  statusLine: string;
}

const MODE_LABELS: Record<AppState, string> = {
  idle: 'Ready',
  wake: 'Listening for wake word',
  listen: 'Listening',
  think: 'Thinking',
  speak: 'Speaking'
};

export const StatusCard: React.FC<StatusCardProps> = ({ appState, statusLine }) => {
  const isPulse = appState === 'wake' || appState === 'listen' || appState === 'think';

  return (
    <section className="card status-card" aria-hidden="true">
      <span className={`mode-badge ${isPulse ? 'pulse' : ''}`} id="mode-badge">
        <span className="dot" aria-hidden="true"></span>
        <span>{MODE_LABELS[appState]}</span>
      </span>
      <p className="status-line" id="status-line">
        {statusLine}
      </p>
    </section>
  );
};
