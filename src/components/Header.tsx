import React from 'react';
import { Eye, ShieldAlert, Radio } from 'lucide-react';
import { LanguageOption, ReflexTelemetry } from '../types';

interface HeaderProps {
  currentLang: LanguageOption;
  telemetry: ReflexTelemetry;
  onOpenReflexModal: () => void;
  onOpenCameraModal: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentLang,
  telemetry,
  onOpenReflexModal,
  onOpenCameraModal
}) => {
  const reflexBadgeClass =
    telemetry.alert === 'stop'
      ? 'bad'
      : telemetry.alert === 'clear'
      ? 'ok'
      : '';

  return (
    <header>
      <div className="brand">
        <svg
          width="38"
          height="38"
          viewBox="0 0 24 24"
          fill="none"
          stroke="var(--accent)"
          strokeWidth="2.4"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
        <h1>BlindSight</h1>
      </div>

      <div className="header-badges">
        <span className="badge" id="lang-badge" aria-label={`Current reply language: ${currentLang.label}`}>
          {currentLang.label}
        </span>

        <button
          type="button"
          className={`badge clickable ${reflexBadgeClass}`}
          onClick={onOpenReflexModal}
          title="Open Ultrasonic Reflex Telemetry"
          aria-label={`Ultrasonic reflex status: ${telemetry.message}. Tap to open sensor monitor.`}
        >
          <ShieldAlert size={16} style={{ marginRight: 6 }} aria-hidden="true" />
          <span>Reflex: {telemetry.alert.toUpperCase()}</span>
        </button>

        <button
          type="button"
          className="badge clickable"
          onClick={onOpenCameraModal}
          title="Open Camera for Sighted Helpers"
          aria-label="Open Camera View for sighted helpers"
        >
          <Eye size={16} style={{ marginRight: 6 }} aria-hidden="true" />
          <span>Vision</span>
        </button>
      </div>
    </header>
  );
};
