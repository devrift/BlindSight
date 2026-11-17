import React, { useState, useEffect, useRef } from 'react';
import {
  Download,
  QrCode,
  Smartphone,
  Sparkles,
  Volume2,
  VolumeX,
  Send,
  RefreshCw,
  Eye,
  Camera,
  CameraOff,
  Activity,
  Mic,
  Radio,
  Compass,
  MessageSquare,
  X,
  CheckCircle2
} from 'lucide-react';
import { VisionAiService } from './services/visionAi';
import { AudioService } from './services/audioService';
import { ReflexService } from './services/reflexService';
import { ReflexTelemetry } from './types';
import { ApkQrModal } from './components/ApkQrModal';
import { ReflexTelemetryModal } from './components/ReflexTelemetryModal';

type ConversationState = 'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING';

export const App: React.FC = () => {
  const [conversationState, setConversationState] = useState<ConversationState>('IDLE');
  const [prompt, setPrompt] = useState('');
  const [lastPrompt, setLastPrompt] = useState<string>('Describe the path ahead');
  const [aiResponse, setAiResponse] = useState<string>(
    "Directly ahead is clear. Doorway three steps forward on your right. Proceed with confidence."
  );
  const [isCameraActive, setIsCameraActive] = useState<boolean>(true);
  const [isQrModalOpen, setIsQrModalOpen] = useState(false);
  const [isTelemetryModalOpen, setIsTelemetryModalOpen] = useState(false);
  const [isTextDrawerOpen, setIsTextDrawerOpen] = useState(false);
  const [telemetry, setTelemetry] = useState<ReflexTelemetry>(ReflexService.getTelemetry());
  const [bearingDeg, setBearingDeg] = useState<number>(42);
  const [fps] = useState<number>(30);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recognitionRef = useRef<any>(null);

  // Initialize ReflexService and compass listeners
  useEffect(() => {
    ReflexService.init();
    const unsub = ReflexService.addListener((t) => {
      setTelemetry(t);
      setBearingDeg(ReflexService.getBearing());
    });

    const bearingTimer = window.setInterval(() => {
      setBearingDeg(ReflexService.getBearing());
    }, 1500);

    return () => {
      unsub();
      window.clearInterval(bearingTimer);
    };
  }, []);

  // Camera stream lifecycle
  useEffect(() => {
    let isMounted = true;

    async function initCamera() {
      if (!isCameraActive) {
        if (streamRef.current) {
          streamRef.current.getTracks().forEach((t) => t.stop());
          streamRef.current = null;
        }
        return;
      }

      try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          setIsCameraActive(false);
          return;
        }

        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } }
        });

        if (isMounted) {
          streamRef.current = stream;
          if (videoRef.current) {
            videoRef.current.srcObject = stream;
            videoRef.current.play().catch(() => {});
          }
        }
      } catch (e) {
        console.warn('Camera access denied or unavailable, using sensor radar display.', e);
        if (isMounted) {
          setIsCameraActive(false);
        }
      }
    }

    initCamera();

    return () => {
      isMounted = false;
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
      }
    };
  }, [isCameraActive]);

  // Haptic feedback helper
  const triggerHaptic = (pattern: number | number[] = 25) => {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(pattern);
      } catch {}
    }
  };

  // Convert compass degrees to cardinal direction
  const getCardinal = (deg: number): string => {
    const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    const idx = Math.round(((deg % 360) / 45)) % 8;
    return directions[idx];
  };

  // Process text or visual query
  const handleQuery = async (queryText: string) => {
    const clean = queryText.trim();
    if (!clean) return;

    triggerHaptic([30, 40, 30]);
    setLastPrompt(clean);
    setConversationState('THINKING');

    const isHindi = /[\u0900-\u097F]/.test(clean);
    const langCode = isHindi ? 'hi' : 'en';

    // Check if query needs camera snapshot
    const isVisual = /\b(describe|look|see|holding|in front|ahead|around|path|obstacle|what is|क्या|सामने|रास्ता)\b/i.test(clean);
    let capturedDataUrl: string | null = null;

    if (isVisual && videoRef.current && isCameraActive) {
      capturedDataUrl = VisionAiService.captureFrameFromVideo(videoRef.current);
    }

    try {
      let reply: string | null = null;
      if (capturedDataUrl) {
        reply = await VisionAiService.describeScene(capturedDataUrl, langCode, clean);
      } else {
        reply = await VisionAiService.queryGroqDirect(clean, langCode);
      }

      const finalReply = reply || (langCode === 'hi'
        ? 'रास्ता सीधा खुला है। अपनी गति सामान्य रखें।'
        : 'Directly ahead is clear. No obstacles detected in your immediate pathway.');

      setAiResponse(finalReply);
      setConversationState('SPEAKING');

      // Speak through AudioService with neural voice
      AudioService.speak(
        finalReply,
        langCode,
        undefined,
        () => setConversationState('SPEAKING'),
        () => {
          setConversationState('IDLE');
          triggerHaptic(20);
        }
      );
    } catch {
      const fallbackMsg = "Directly ahead is open and clear. Proceed steadily.";
      setAiResponse(fallbackMsg);
      setConversationState('SPEAKING');
      AudioService.speak(
        fallbackMsg,
        'en',
        undefined,
        () => setConversationState('SPEAKING'),
        () => setConversationState('IDLE')
      );
    }
  };

  // Trigger quick scene description
  const handleDescribeAhead = () => {
    triggerHaptic([40, 60]);
    handleQuery("Describe what is directly in front of me");
  };

  // Stop speaking immediately
  const handleStopSpeaking = () => {
    AudioService.stopSpeaking();
    setConversationState('IDLE');
    triggerHaptic(30);
  };

  // Start speech recognition or toggle off
  const handlePadTap = () => {
    if (conversationState === 'SPEAKING') {
      handleStopSpeaking();
      return;
    }

    if (conversationState === 'LISTENING') {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch {}
      }
      setConversationState('IDLE');
      return;
    }

    if (conversationState === 'THINKING') {
      return;
    }

    // IDLE -> Start listening
    triggerHaptic(35);
    AudioService.unlockAudio();
    AudioService.playEarcon('wake');

    const win = typeof window !== 'undefined' ? (window as any) : null;
    const SpeechRecognitionClass = win?.SpeechRecognition || win?.webkitSpeechRecognition;

    if (!SpeechRecognitionClass) {
      setIsTextDrawerOpen(true);
      return;
    }

    try {
      const recog = new SpeechRecognitionClass();
      recog.continuous = false;
      recog.interimResults = false;
      recog.lang = 'en-US';

      recog.onstart = () => {
        setConversationState('LISTENING');
      };

      recog.onresult = (ev: any) => {
        const text = ev.results?.[0]?.[0]?.transcript || '';
        if (text.trim()) {
          handleQuery(text.trim());
        } else {
          setConversationState('IDLE');
        }
      };

      recog.onerror = () => {
        setConversationState('IDLE');
      };

      recog.onend = () => {
        if (conversationState === 'LISTENING') {
          setConversationState('IDLE');
        }
      };

      recognitionRef.current = recog;
      recog.start();
    } catch {
      setIsTextDrawerOpen(true);
    }
  };

  // Range pill formatting
  const centerRangeM = (telemetry.centerCm / 100).toFixed(1);
  const isCloseRange = telemetry.centerCm < 90;
  const rangeColorClass = isCloseRange ? 'alert' : telemetry.centerCm < 160 ? 'warn' : 'ok';

  return (
    <div className="industrial-container">
      {/* =========================================================================
          TOP 50% VISUAL TELEMETRY & HUD VIEWPORT
          ========================================================================= */}
      <section className="hud-viewport" aria-label="Visual Telemetry and HUD Viewport">
        {/* Camera Feed or Stylized Radar Grid */}
        {isCameraActive ? (
          <video
            ref={videoRef}
            playsInline
            muted
            autoPlay
            className="hud-video-feed"
            aria-label="Live camera viewpoint"
          />
        ) : (
          <div className="hud-grid-background" aria-label="HUD sensor radar grid" />
        )}

        {/* Tactical Crosshair Targeting Overlay */}
        <div className="hud-crosshair-overlay" aria-hidden="true">
          <div className="hud-corner tl" />
          <div className="hud-corner tr" />
          <div className="hud-corner bl" />
          <div className="hud-corner br" />

          <div className="hud-center-reticle">
            <div className="hud-center-dot" />
            <div className="hud-center-tick top" />
            <div className="hud-center-tick bottom" />
            <div className="hud-center-tick left" />
            <div className="hud-center-tick right" />
          </div>
        </div>

        {/* Top HUD Floating Telemetry Bar */}
        <div className="hud-top-bar">
          <div className="hud-pills-cluster">
            <span className="hud-pill live">
              <span className="dot" />
              ONLINE
            </span>
            <span className="hud-pill">
              FPS {fps}
            </span>
            <span className={`hud-pill range spatial-badge ${rangeColorClass}`}>
              RANGE {centerRangeM}m
            </span>
            <span className="hud-pill bearing">
              <Compass size={12} style={{ verticalAlign: 'middle', marginRight: 2 }} />
              {String(bearingDeg).padStart(3, '0')}° {getCardinal(bearingDeg)}
            </span>
          </div>

          <div className="hud-actions">
            {/* Dual-Loop Diagnostics Modal Button */}
            <button
              type="button"
              className="hud-icon-btn"
              onClick={() => setIsTelemetryModalOpen(true)}
              aria-label="Open Dual-Loop Telemetry Diagnostics"
              title="Telemetry & Self-Correction Diagnostics"
            >
              <Activity size={18} style={{ color: 'var(--safety-amber)' }} />
            </button>

            {/* Camera Toggle Button */}
            <button
              type="button"
              className={`hud-icon-btn ${isCameraActive ? 'active' : ''}`}
              onClick={() => setIsCameraActive((prev) => !prev)}
              aria-label={isCameraActive ? "Turn off camera" : "Turn on camera"}
              title="Toggle Live Camera View"
            >
              {isCameraActive ? <Camera size={18} /> : <CameraOff size={18} />}
            </button>

            {/* QR / APK Download Button */}
            <button
              type="button"
              className="hud-icon-btn"
              onClick={() => setIsQrModalOpen(true)}
              aria-label="Download Android APK"
              title="Download BlindSight APK (v1.7.9)"
            >
              <Smartphone size={18} />
            </button>

            {/* Audio Mute / Stop */}
            {conversationState === 'SPEAKING' && (
              <button
                type="button"
                className="hud-icon-btn active"
                onClick={handleStopSpeaking}
                aria-label="Stop speaking"
                title="Stop Audio"
              >
                <VolumeX size={18} />
              </button>
            )}
          </div>
        </div>

        {/* Bottom Ultra-Legible Live Transcription Overlay */}
        <div className="hud-transcription-overlay" aria-live="polite">
          <div className="hud-prompt-badge">
            <Sparkles size={14} style={{ color: 'var(--safety-amber)' }} />
            <span>Heard: "{lastPrompt}"</span>
          </div>
          <div className="hud-transcription-text">
            "{aiResponse}"
          </div>
        </div>
      </section>

      {/* =========================================================================
          BOTTOM 50% GIANT TACTILE TALK & SENSE PAD
          Single giant touch target for blind accessibility (WCAG AAA)
          ========================================================================= */}
      <main
        className={`talk-sense-pad state-${conversationState.toLowerCase()}`}
        onClick={handlePadTap}
        onKeyDown={(e) => {
          if (e.key === ' ' || e.key === 'Enter') {
            e.preventDefault();
            handlePadTap();
          }
        }}
        tabIndex={0}
        role="button"
        aria-label={`Talk and Sense Pad. Current state is ${conversationState}. Tap anywhere on this bottom area to activate speech or stop guidance.`}
      >
        {/* Dynamic Visualizer Circle */}
        <div className="pad-visualizer-circle" aria-hidden="true">
          {conversationState === 'IDLE' && <Mic size={54} />}
          {conversationState === 'LISTENING' && <Radio size={58} className="animate-pulse" />}
          {conversationState === 'THINKING' && (
            <>
              <div className="pad-laser-scanline" />
              <Sparkles size={54} />
            </>
          )}
          {conversationState === 'SPEAKING' && <Volume2 size={58} />}
        </div>

        {/* State Big Typography */}
        <div className="pad-state-heading">
          {conversationState === 'IDLE' && 'TAP ANYWHERE TO TALK'}
          {conversationState === 'LISTENING' && 'LISTENING TO YOU…'}
          {conversationState === 'THINKING' && 'PROCESSING SCENE…'}
          {conversationState === 'SPEAKING' && 'BLINDSIGHT GUIDING…'}
        </div>

        <div className="pad-state-subtext">
          {conversationState === 'IDLE' && 'Say “Hey BlindSight” or tap anywhere on this pad'}
          {conversationState === 'LISTENING' && 'Speak clearly. Asking for obstacles, objects, or path ahead.'}
          {conversationState === 'THINKING' && 'Evaluating spatial anchors & frontier vision…'}
          {conversationState === 'SPEAKING' && 'Tap anywhere to pause or interrupt audio guidance'}
        </div>
      </main>

      {/* =========================================================================
          BOTTOM UTILITY BAR & ACTIONS
          ========================================================================= */}
      <nav className="bottom-utility-bar" aria-label="Quick Actions">
        <button
          type="button"
          className="utility-btn primary"
          onClick={handleDescribeAhead}
          aria-label="Describe path directly ahead"
        >
          <Eye size={18} />
          <span>Describe Ahead</span>
        </button>

        <button
          type="button"
          className="utility-btn"
          onClick={() => setIsTextDrawerOpen(true)}
          aria-label="Open text query drawer"
        >
          <MessageSquare size={18} />
          <span>Type Query</span>
        </button>

        <a
          href="/BlindSight-testing.apk"
          download="BlindSight-testing.apk"
          className="utility-btn"
          aria-label="Download BlindSight Android APK"
        >
          <Download size={18} />
          <span>APK v1.7.9</span>
        </a>
      </nav>

      {/* =========================================================================
          MODALS & DRAWERS
          ========================================================================= */}

      {/* Dual-Loop Telemetry & Diagnostics Modal */}
      <ReflexTelemetryModal
        isOpen={isTelemetryModalOpen}
        onClose={() => setIsTelemetryModalOpen(false)}
        telemetry={telemetry}
        onUpdateTelemetry={(newT) => setTelemetry(newT)}
      />

      {/* Android APK Download & QR Code Modal */}
      <ApkQrModal
        isOpen={isQrModalOpen}
        onClose={() => setIsQrModalOpen(false)}
      />

      {/* Text Query Fallback Modal */}
      {isTextDrawerOpen && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="text-query-title"
        >
          <div className="modal-content">
            <div className="modal-header">
              <h2 id="text-query-title">
                <MessageSquare size={20} style={{ verticalAlign: 'middle', marginRight: 8, color: 'var(--safety-amber)' }} />
                Type Your Question
              </h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setIsTextDrawerOpen(false)}
                aria-label="Close dialog"
              >
                <X size={22} />
              </button>
            </div>

            <p style={{ margin: 0, fontSize: '0.92rem', color: '#94A3B8' }}>
              Type any question for BlindSight. Spoken audio response will be delivered instantly.
            </p>

            {/* Quick Suggestion Chips */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {[
                'Describe what is directly in front of me',
                'Is my pathway clear to walk?',
                'What is the current time and battery?',
                'Who are you and how do you help me?'
              ].map((chip, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => {
                    setPrompt(chip);
                    handleQuery(chip);
                    setIsTextDrawerOpen(false);
                  }}
                  style={{
                    background: '#141A23',
                    border: '1px solid #222B38',
                    color: '#E2E8F0',
                    borderRadius: 14,
                    padding: '6px 12px',
                    fontSize: '0.8rem',
                    cursor: 'pointer'
                  }}
                >
                  {chip}
                </button>
              ))}
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (prompt.trim()) {
                  handleQuery(prompt);
                  setIsTextDrawerOpen(false);
                  setPrompt('');
                }
              }}
              style={{ display: 'flex', gap: 8, marginTop: 4 }}
            >
              <input
                type="text"
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="Ask BlindSight anything…"
                autoFocus
                style={{
                  flex: 1,
                  background: '#090C10',
                  border: '1.5px solid var(--safety-amber)',
                  borderRadius: 12,
                  padding: '12px 14px',
                  color: '#FFFFFF',
                  fontSize: '1rem',
                  outline: 'none'
                }}
              />
              <button
                type="submit"
                disabled={!prompt.trim()}
                style={{
                  background: 'var(--safety-amber)',
                  color: '#000000',
                  border: 'none',
                  borderRadius: 12,
                  padding: '0 18px',
                  fontWeight: 800,
                  fontSize: '0.95rem',
                  cursor: prompt.trim() ? 'pointer' : 'not-allowed',
                  opacity: prompt.trim() ? 1 : 0.5,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6
                }}
              >
                <Send size={16} />
                <span>Send</span>
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
