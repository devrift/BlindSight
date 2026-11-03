import React, { useState } from 'react';
import { ShieldAlert, Volume2, X, RefreshCw } from 'lucide-react';
import { ReflexTelemetry } from '../types';
import { ReflexService } from '../services/reflexService';

interface ReflexTelemetryModalProps {
  telemetry: ReflexTelemetry;
  isOpen: boolean;
  onClose: () => void;
  onUpdateTelemetry: (newTelemetry: ReflexTelemetry) => void;
}

export const ReflexTelemetryModal: React.FC<ReflexTelemetryModalProps> = ({
  telemetry,
  isOpen,
  onClose,
  onUpdateTelemetry
}) => {
  if (!isOpen) return null;

  const [left, setLeft] = useState(telemetry.leftCm);
  const [center, setCenter] = useState(telemetry.centerCm);
  const [right, setRight] = useState(telemetry.rightCm);

  const handleSensorChange = (newLeft: number, newCenter: number, newRight: number) => {
    setLeft(newLeft);
    setCenter(newCenter);
    setRight(newRight);
    const updated = ReflexService.evaluateSensors(newLeft, newCenter, newRight);
    onUpdateTelemetry(updated);
    ReflexService.triggerReflexAudio(updated, false);
  };

  const handleSimulateScenario = (scenario: 'stop' | 'steer_left' | 'steer_right' | 'careful' | 'clear') => {
    let l = 150, c = 180, r = 160;
    if (scenario === 'stop') {
      l = 22; c = 18; r = 24;
    } else if (scenario === 'steer_left') {
      l = 120; c = 55; r = 32;
    } else if (scenario === 'steer_right') {
      l = 30; c = 50; r = 130;
    } else if (scenario === 'careful') {
      l = 75; c = 68; r = 80;
    } else {
      l = 160; c = 190; r = 175;
    }
    handleSensorChange(l, c, r);
  };

  const alertBadgeClass =
    telemetry.alert === 'stop'
      ? 'stop'
      : telemetry.alert.startsWith('steer')
      ? 'steer'
      : telemetry.alert === 'careful'
      ? 'careful'
      : 'clear';

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="reflex-modal-title">
      <div className="modal-content">
        <div className="modal-header">
          <h2 id="reflex-modal-title">
            <ShieldAlert size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            Tier 0: Ultrasonic Reflex Telemetry
          </h2>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close reflex telemetry modal">
            <X size={24} />
          </button>
        </div>

        <p style={{ margin: 0, color: 'var(--muted)', fontSize: '0.95rem' }}>
          Real-time HC-SR04 ultrasonic reflex subsystem from the Main ESP32 head unit. Operates independently
          without network latency.
        </p>

        <div className="reflex-box">
          <div className={`reflex-status-badge ${alertBadgeClass}`}>
            {telemetry.alert === 'stop' && '🔴 CRITICAL OBSTACLE — STOP'}
            {telemetry.alert === 'steer_left' && '⚠️ OBSTACLE RIGHT — STEER LEFT'}
            {telemetry.alert === 'steer_right' && '⚠️ OBSTACLE LEFT — STEER RIGHT'}
            {telemetry.alert === 'careful' && '🟡 OBSTACLE AHEAD — PROCEED CAREFULLY'}
            {telemetry.alert === 'clear' && '🟢 PATH CLEAR — SAFE TO PROCEED'}
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 600, color: 'var(--text)' }}>Live Message:</span>
            <span style={{ color: 'var(--accent-strong)', fontWeight: 700 }}>{telemetry.message}</span>
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
            <button
              type="button"
              className="badge clickable"
              style={{ flex: 1, justifyContent: 'center' }}
              onClick={() => ReflexService.triggerReflexAudio(telemetry, true)}
            >
              <Volume2 size={16} style={{ marginRight: 6 }} /> Play Voice Alert Clip
            </button>
          </div>
        </div>

        <div className="sensor-sliders">
          <div className="sensor-col">
            <label htmlFor="sensor-left">Left Beam</label>
            <div className="distance-val">{left} cm</div>
            <input
              id="sensor-left"
              type="range"
              min="5"
              max="250"
              value={left}
              onChange={e => handleSensorChange(Number(e.target.value), center, right)}
            />
          </div>

          <div className="sensor-col">
            <label htmlFor="sensor-center">Center Beam</label>
            <div className="distance-val">{center} cm</div>
            <input
              id="sensor-center"
              type="range"
              min="5"
              max="250"
              value={center}
              onChange={e => handleSensorChange(left, Number(e.target.value), right)}
            />
          </div>

          <div className="sensor-col">
            <label htmlFor="sensor-right">Right Beam</label>
            <div className="distance-val">{right} cm</div>
            <input
              id="sensor-right"
              type="range"
              min="5"
              max="250"
              value={right}
              onChange={e => handleSensorChange(left, center, Number(e.target.value))}
            />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <span style={{ fontSize: '0.9rem', color: 'var(--muted)', fontWeight: 600 }}>
            Test Reflex Scenarios:
          </span>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <button
              type="button"
              style={{ fontSize: '0.85rem', padding: '6px 12px' }}
              onClick={() => handleSimulateScenario('stop')}
            >
              Stop (&lt;25cm)
            </button>
            <button
              type="button"
              style={{ fontSize: '0.85rem', padding: '6px 12px' }}
              onClick={() => handleSimulateScenario('steer_left')}
            >
              Steer Left
            </button>
            <button
              type="button"
              style={{ fontSize: '0.85rem', padding: '6px 12px' }}
              onClick={() => handleSimulateScenario('steer_right')}
            >
              Steer Right
            </button>
            <button
              type="button"
              style={{ fontSize: '0.85rem', padding: '6px 12px' }}
              onClick={() => handleSimulateScenario('careful')}
            >
              Careful (&lt;100cm)
            </button>
            <button
              type="button"
              style={{ fontSize: '0.85rem', padding: '6px 12px' }}
              onClick={() => handleSimulateScenario('clear')}
            >
              Clear (&gt;100cm)
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
