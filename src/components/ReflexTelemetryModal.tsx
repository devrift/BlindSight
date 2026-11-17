import React, { useState } from 'react';
import { ShieldAlert, Volume2, X, Activity, Compass, Cpu, HardDrive, CheckCircle2, AlertTriangle } from 'lucide-react';
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
  const [activeTab, setActiveTab] = useState<'telemetry' | 'diagnostics'>('telemetry');

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

  const logs = ReflexService.getLogs();

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="reflex-modal-title">
      <div className="modal-content" style={{ maxWidth: 580, background: '#0e1217', border: '1px solid rgba(0, 229, 255, 0.25)', borderRadius: 20 }}>
        <div className="modal-header" style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: 12 }}>
          <div>
            <h2 id="reflex-modal-title" style={{ margin: 0, fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: 8, color: '#00E5FF' }}>
              <Activity size={22} />
              Dual-Loop Telemetry &amp; Live Diagnostics
            </h2>
            <span style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Real-time perception HUD and background self-correcting ring buffer</span>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close telemetry modal">
            <X size={22} />
          </button>
        </div>

        {/* Tab Switcher */}
        <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
          <button
            type="button"
            onClick={() => setActiveTab('telemetry')}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: 10,
              background: activeTab === 'telemetry' ? '#00E5FF' : 'rgba(255,255,255,0.06)',
              color: activeTab === 'telemetry' ? '#000' : '#fff',
              fontWeight: 700,
              border: 'none',
              cursor: 'pointer',
              fontSize: '0.85rem'
            }}
          >
            Reflex &amp; Sensor Telemetry
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('diagnostics')}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: 10,
              background: activeTab === 'diagnostics' ? '#00E5FF' : 'rgba(255,255,255,0.06)',
              color: activeTab === 'diagnostics' ? '#000' : '#fff',
              fontWeight: 700,
              border: 'none',
              cursor: 'pointer',
              fontSize: '0.85rem'
            }}
          >
            Diagnostics Log ({logs.length})
          </button>
        </div>

        {activeTab === 'telemetry' ? (
          <>
            {/* Live Metrics Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginTop: 6 }}>
              <div style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: '10px 8px', textAlign: 'center' }}>
                <span style={{ fontSize: '0.7rem', color: '#94a3b8', display: 'block' }}>LATENCY</span>
                <span style={{ fontSize: '1.1rem', fontWeight: 800, color: '#38BDF8' }}>{telemetry.lastLatencyMs || 240}ms</span>
              </div>
              <div style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: '10px 8px', textAlign: 'center' }}>
                <span style={{ fontSize: '0.7rem', color: '#94a3b8', display: 'block' }}>PAYLOAD</span>
                <span style={{ fontSize: '1.1rem', fontWeight: 800, color: '#FFB800' }}>{Math.round((telemetry.lastPayloadBytes || 48000) / 1024)} KB</span>
              </div>
              <div style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: '10px 8px', textAlign: 'center' }}>
                <span style={{ fontSize: '0.7rem', color: '#94a3b8', display: 'block' }}>TOKENS</span>
                <span style={{ fontSize: '1.1rem', fontWeight: 800, color: '#34D399' }}>{telemetry.lastTokens || 28} / 40</span>
              </div>
              <div style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: '10px 8px', textAlign: 'center' }}>
                <span style={{ fontSize: '0.7rem', color: '#94a3b8', display: 'block' }}>BEARING</span>
                <span style={{ fontSize: '1.1rem', fontWeight: 800, color: '#A78BFA' }}>{telemetry.bearing || 42}° NE</span>
              </div>
            </div>

            {/* Status Alert Badge */}
            <div className="reflex-box" style={{ background: 'rgba(22, 27, 34, 0.75)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14 }}>
              <div className={`reflex-status-badge ${alertBadgeClass}`}>
                {telemetry.alert === 'stop' && '🔴 CRITICAL OBSTACLE — STOP'}
                {telemetry.alert === 'steer_left' && '⚠️ OBSTACLE RIGHT — STEER LEFT'}
                {telemetry.alert === 'steer_right' && '⚠️ OBSTACLE LEFT — STEER RIGHT'}
                {telemetry.alert === 'careful' && '🟡 OBSTACLE AHEAD — PROCEED CAREFULLY'}
                {telemetry.alert === 'clear' && '🟢 PATH CLEAR — SAFE TO PROCEED'}
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
                <span style={{ fontWeight: 600, color: 'var(--text)' }}>Spoken Anchor:</span>
                <span style={{ color: '#00E5FF', fontWeight: 700 }}>{telemetry.message}</span>
              </div>

              <button
                type="button"
                className="badge clickable"
                style={{ width: '100%', justifyContent: 'center', marginTop: 10, background: 'rgba(255,184,0,0.15)', border: '1px solid #FFB800', color: '#FFB800', fontWeight: 700 }}
                onClick={() => ReflexService.triggerReflexAudio(telemetry, true)}
              >
                <Volume2 size={16} style={{ marginRight: 6 }} /> Play Voice Alert Clip
              </button>
            </div>

            {/* Beam Sliders */}
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

            {/* Quick Test Scenarios */}
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <button type="button" style={{ fontSize: '0.82rem', padding: '6px 10px', background: '#262c36', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} onClick={() => handleSimulateScenario('stop')}>
                Stop (&lt;25cm)
              </button>
              <button type="button" style={{ fontSize: '0.82rem', padding: '6px 10px', background: '#262c36', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} onClick={() => handleSimulateScenario('steer_left')}>
                Steer Left
              </button>
              <button type="button" style={{ fontSize: '0.82rem', padding: '6px 10px', background: '#262c36', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} onClick={() => handleSimulateScenario('steer_right')}>
                Steer Right
              </button>
              <button type="button" style={{ fontSize: '0.82rem', padding: '6px 10px', background: '#262c36', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} onClick={() => handleSimulateScenario('careful')}>
                Careful (&lt;100cm)
              </button>
              <button type="button" style={{ fontSize: '0.82rem', padding: '6px 10px', background: '#262c36', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }} onClick={() => handleSimulateScenario('clear')}>
                Clear (&gt;100cm)
              </button>
            </div>
          </>
        ) : (
          /* Diagnostics Log Ring Buffer */
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 360, overflowY: 'auto', paddingRight: 4 }}>
            {logs.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: '#64748b' }}>No events logged yet.</div>
            ) : (
              logs.map(log => (
                <div
                  key={log.id}
                  style={{
                    background: log.type === 'fallback' ? 'rgba(239, 68, 68, 0.12)' : log.type === 'capture' ? 'rgba(0, 229, 255, 0.08)' : 'rgba(255,255,255,0.03)',
                    border: `1px solid ${log.type === 'fallback' ? 'rgba(239, 68, 68, 0.35)' : log.type === 'capture' ? 'rgba(0, 229, 255, 0.25)' : 'rgba(255,255,255,0.06)'}`,
                    borderRadius: 10,
                    padding: '8px 12px',
                    fontSize: '0.82rem'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                    <span style={{ fontWeight: 700, textTransform: 'uppercase', color: log.type === 'fallback' ? '#F87171' : log.type === 'capture' ? '#00E5FF' : '#94A3B8' }}>
                      {log.type}
                    </span>
                    <span style={{ color: '#64748b', fontSize: '0.75rem' }}>
                      {new Date(log.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                  <div style={{ color: '#e2e8f0', lineHeight: 1.4 }}>{log.message}</div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};
