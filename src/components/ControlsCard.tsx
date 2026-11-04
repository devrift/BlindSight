import React, { useState } from 'react';
import { ListenMode, LanguageCode, TextSize, VoiceOption } from '../types';
import { LANGUAGES } from '../services/companionState';
import { AudioService } from '../services/audioService';

interface ControlsCardProps {
  listenMode: ListenMode;
  onModeChange: (mode: ListenMode) => void;
  noiseGate: boolean;
  onNoiseGateToggle: (enabled: boolean) => void;
  currentLanguage: LanguageCode;
  onLanguageChange: (lang: LanguageCode) => void;
  voices: VoiceOption[];
  chosenVoiceURI: string;
  onVoiceChange: (uri: string) => void;
  includeCamera: boolean;
  onCameraToggle: (include: boolean) => void;
  textSize: TextSize;
  onTextSizeChange: (size: TextSize) => void;
  token: string;
  onSaveToken: (token: string) => void;
  onHelp: () => void;
  onStop: () => void;
  latestFrameUrl: string | null;
  onFetchLatestFrame: () => void;
}

export const ControlsCard: React.FC<ControlsCardProps> = ({
  listenMode,
  onModeChange,
  noiseGate,
  onNoiseGateToggle,
  currentLanguage,
  onLanguageChange,
  voices,
  chosenVoiceURI,
  onVoiceChange,
  includeCamera,
  onCameraToggle,
  textSize,
  onTextSizeChange,
  token,
  onSaveToken,
  onHelp,
  onStop,
  latestFrameUrl,
  onFetchLatestFrame
}) => {
  const [inputToken, setInputToken] = useState(token);
  const [showTokenSettings, setShowTokenSettings] = useState(false);
  const [useSvara, setUseSvara] = useState<boolean>(() => AudioService.isSvaraEnabled());
  const [showSvaraConfig, setShowSvaraConfig] = useState(false);
  const [svaraUrl, setSvaraUrl] = useState<string>(() => AudioService.getSvaraUrl());

  const handleSvaraToggle = (enabled: boolean) => {
    setUseSvara(enabled);
    AudioService.setSvaraEnabled(enabled);
  };

  const handleSaveSvaraUrl = () => {
    AudioService.setSvaraUrl(svaraUrl.trim());
    setShowSvaraConfig(false);
  };

  return (
    <section className="card controls-card" aria-label="Companion settings">
      {/* Listening Mode */}
      <div className="control">
        <label htmlFor="mode-select">Listening mode</label>
        <select
          id="mode-select"
          aria-label="Listening mode"
          value={listenMode}
          onChange={e => onModeChange(e.target.value as ListenMode)}
        >
          <option value="wake">Wake word</option>
          <option value="handsfree">Hands-free</option>
          <option value="button">Button only</option>
        </select>
      </div>

      {/* Noise Gate */}
      <div className="control">
        <label htmlFor="noise-gate-toggle">
          Noise gate
          <small>filters room noise</small>
        </label>
        <span className="switch">
          <input
            type="checkbox"
            id="noise-gate-toggle"
            checked={noiseGate}
            onChange={e => onNoiseGateToggle(e.target.checked)}
          />
          <span className="track" aria-hidden="true"></span>
        </span>
      </div>

      {/* Voice Selector */}
      <div className="control">
        <label htmlFor="voice-select">Voice</label>
        <select
          id="voice-select"
          aria-label="Assistant voice"
          value={chosenVoiceURI}
          onChange={e => onVoiceChange(e.target.value)}
        >
          <option value="">Default neural voice</option>
          {voices.map(v => (
            <option key={v.voiceURI} value={v.voiceURI}>
              {v.name} ({v.lang})
            </option>
          ))}
        </select>
      </div>

      {/* Svara-TTS v1 Indian Neural Voice Integration */}
      <div className="control">
        <label htmlFor="svara-toggle">
          Svara-TTS v1
          <small>{useSvara ? 'Port 8000 (Hindi/Indian neural)' : 'Docker neural Indian TTS'}</small>
        </label>
        <span className="switch">
          <input
            type="checkbox"
            id="svara-toggle"
            checked={useSvara}
            onChange={e => handleSvaraToggle(e.target.checked)}
          />
          <span className="track" aria-hidden="true"></span>
        </span>
      </div>

      {/* Include Camera */}
      <div className="control">
        <label htmlFor="camera-toggle">
          Include camera
          <small>with questions</small>
        </label>
        <span className="switch">
          <input
            type="checkbox"
            id="camera-toggle"
            checked={includeCamera}
            onChange={e => onCameraToggle(e.target.checked)}
          />
          <span className="track" aria-hidden="true"></span>
        </span>
      </div>

      {/* Reply Language */}
      <div className="control">
        <label htmlFor="language-select">Reply language</label>
        <select
          id="language-select"
          aria-label="Reply language"
          value={currentLanguage}
          onChange={e => onLanguageChange(e.target.value as LanguageCode)}
        >
          {Object.values(LANGUAGES).map(opt => (
            <option key={opt.code} value={opt.code}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* Text Size Switcher */}
      <div className="control">
        <span className="group-label" id="text-size-label">
          Text size
        </span>
        <span className="text-size-group" role="group" aria-labelledby="text-size-label">
          <button
            type="button"
            data-size="md"
            aria-pressed={textSize === 'md'}
            aria-label="Normal text size"
            onClick={() => onTextSizeChange('md')}
          >
            A
          </button>
          <button
            type="button"
            data-size="lg"
            aria-pressed={textSize === 'lg'}
            aria-label="Large text size"
            onClick={() => onTextSizeChange('lg')}
          >
            A+
          </button>
          <button
            type="button"
            data-size="xl"
            aria-pressed={textSize === 'xl'}
            aria-label="Extra large text size"
            onClick={() => onTextSizeChange('xl')}
          >
            A++
          </button>
        </span>
      </div>

      {/* Actions: Help & Stop */}
      <div className="control">
        <button type="button" id="help-btn" onClick={onHelp} aria-label="Read help and available commands">
          Help<br />
          <small>read commands</small>
        </button>
        <button type="button" id="stop-btn" onClick={onStop} aria-label="Stop audio and silence assistant">
          Stop<br />
          <small>silence audio</small>
        </button>
      </div>

      {/* Camera Frame for Sighted Helpers */}
      <div className="control">
        <button
          type="button"
          id="frame-btn"
          onClick={onFetchLatestFrame}
          aria-label="Capture or show latest camera frame for sighted helpers"
        >
          Camera frame<br />
          <small>for sighted helpers</small>
        </button>
        {latestFrameUrl ? (
          <img
            src={latestFrameUrl}
            alt="Latest camera frame"
            style={{ width: 72, height: 54, objectFit: 'cover', borderRadius: 8 }}
          />
        ) : (
          <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>No frame</span>
        )}
      </div>

      {/* Gateway Token Access */}
      <div className="control" style={{ gridColumn: '1 / -1' }}>
        <label htmlFor="token-input">
          Gateway token
          <small>{showTokenSettings ? 'set secret token' : token ? 'token active' : 'optional auth'}</small>
        </label>
        {showTokenSettings ? (
          <div style={{ display: 'flex', gap: 8, flex: 1, justifyContent: 'flex-end' }}>
            <input
              type="password"
              id="token-input"
              placeholder="Access token"
              value={inputToken}
              onChange={e => setInputToken(e.target.value)}
              style={{ maxWidth: 180 }}
            />
            <button
              type="button"
              onClick={() => {
                onSaveToken(inputToken);
                setShowTokenSettings(false);
              }}
            >
              Save
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => setShowTokenSettings(true)}>
            {token ? 'Edit Token' : 'Set Token'}
          </button>
        )}
      </div>
    </section>
  );
};
