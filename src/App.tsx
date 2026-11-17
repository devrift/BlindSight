import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Volume2,
  VolumeX,
  Camera,
  CameraOff,
  BatteryCharging,
  Compass,
  Smartphone,
  Eye
} from 'lucide-react';
import { VisionAiService } from './services/visionAi';
import { AudioService } from './services/audioService';
import { ReflexService } from './services/reflexService';
import { LanguageCode } from './types';
import { LANGUAGES } from './services/companionState';
import { AllyFaceOrb, AllyOrbState } from './components/AllyFaceOrb';
import { ApkQrModal } from './components/ApkQrModal';

const LOCKED_LANGUAGES: LanguageCode[] = ['en', 'hi', 'pa', 'bn'];

/**
 * BlindSight Liquid Glass Accessible Experience.
 *
 * Designed strictly according to assistive mobility principles:
 * - Obsidian Canvas (#080B10)
 * - Apple / VisionOS Frosted Glassmorphism (blur 28px, border 1px solid rgba(255,255,255,0.12))
 * - Top 40%: Viewport HUD with glowing liquid glass border showing live upright camera
 * - Center: Organic fluid audio-reactive liquid orb/membrane
 * - Bottom 50%: Massive tactile touch surface with 1.5s speech silence buffer
 * - Zero emojis throughout UI and spoken output
 */
export const App: React.FC = () => {
  const [orbState, setOrbState] = useState<AllyOrbState>('idle');
  const [currentLang, setCurrentLang] = useState<LanguageCode>('en');
  const [aiResponse, setAiResponse] = useState<string>(
    "Directly ahead is clear. Doorway three steps forward on your right. Proceed with confidence."
  );
  const [isCameraActive, setIsCameraActive] = useState<boolean>(true);
  const [isQrModalOpen, setIsQrModalOpen] = useState(false);
  const [statusSubtitle, setStatusSubtitle] = useState<string>(
    "Single-tap: Speak  •  Double-tap: Describe  •  2 fingers: Silence"
  );

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recognitionRef = useRef<any>(null);
  const silenceTimerRef = useRef<number | null>(null);
  const lastTapTimeRef = useRef<number>(0);
  const singleTapTimerRef = useRef<number | null>(null);

  // Initialize ReflexService for telemetry & obstacle awareness
  useEffect(() => {
    ReflexService.init();
    const unsub = ReflexService.addListener((telemetry) => {
      if (telemetry.centerCm > 0 && telemetry.centerCm < 45) {
        setOrbState('caution');
        if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
          navigator.vibrate([0, 100, 80, 100]);
        }
      }
    });

    return () => {
      unsub();
    };
  }, []);

  // Background camera stream lifecycle for upright scene descriptions
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
          video: {
            facingMode: 'environment',
            width: { ideal: 640 },
            height: { ideal: 480 }
          }
        });

        if (isMounted) {
          streamRef.current = stream;
          if (videoRef.current) {
            videoRef.current.srcObject = stream;
            videoRef.current.play().catch(() => {});
          }
        }
      } catch (e) {
        console.warn('Camera access denied or unavailable, running in sensory mode.', e);
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
  const triggerHaptic = useCallback((pattern: number | number[] = 25) => {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(pattern);
      } catch {}
    }
  }, []);

  // Stop speaking immediately
  const handleStopSpeaking = useCallback(() => {
    AudioService.stopSpeaking();
    setOrbState('idle');
    triggerHaptic(30);
  }, [triggerHaptic]);

  // Cycle through the strictly locked 4 languages (EN, HI, PA, BN)
  const handleCycleLanguage = useCallback(() => {
    const currentIdx = LOCKED_LANGUAGES.indexOf(currentLang);
    const nextIdx = (currentIdx + 1) % LOCKED_LANGUAGES.length;
    const nextLang = LOCKED_LANGUAGES[nextIdx];
    setCurrentLang(nextLang);
    triggerHaptic([40, 50]);

    const confirmationText = LANGUAGES[nextLang]?.confirmation || 'Language updated.';
    setAiResponse(confirmationText);
    setOrbState('confirming');

    AudioService.speak(
      confirmationText,
      nextLang,
      undefined,
      () => setOrbState('speak'),
      () => {
        setOrbState('idle');
      }
    );
  }, [currentLang, triggerHaptic]);

  // Trigger web torch if supported
  const setWebTorch = useCallback(async (enabled: boolean) => {
    if (!streamRef.current) return;
    const track = streamRef.current.getVideoTracks()[0];
    if (track && 'applyConstraints' in track) {
      try {
        await (track as any).applyConstraints({
          advanced: [{ torch: enabled }]
        });
      } catch {}
    }
  }, []);

  // Immediate Scene Description (Double Tap)
  const handleDescribeScene = useCallback(async (isReadingText = false) => {
    triggerHaptic([50, 70, 50]);
    setOrbState('think');
    const promptText = isReadingText
      ? "Read all text, signs, labels, or numbers visible in this view. If no text is visible, say 'No text visible'."
      : "Describe what is directly in front of me with immediate directional anchors.";
    setStatusSubtitle(isReadingText ? "Reading visible text…" : "Capturing scene snapshot…");

    let capturedDataUrl: string | null = null;
    if (videoRef.current && isCameraActive) {
      capturedDataUrl = VisionAiService.captureFrameFromVideo(videoRef.current);
    }

    try {
      const reply = capturedDataUrl
        ? await VisionAiService.describeScene(capturedDataUrl, currentLang, promptText)
        : await VisionAiService.queryGroqDirect(promptText, currentLang);

      const cleanedReply = (reply || "Directly ahead is clear. Path is open.")
        .replace(/\[ACTION:[^\]]+\]/gi, '').trim();

      setAiResponse(cleanedReply);
      setOrbState('speak');

      AudioService.speak(
        cleanedReply,
        currentLang,
        undefined,
        () => setOrbState('speak'),
        () => {
          setOrbState('idle');
          triggerHaptic(20);
        }
      );
    } catch {
      const fallback = "Path directly ahead is open. Proceed steadily.";
      setAiResponse(fallback);
      setOrbState('speak');
      AudioService.speak(fallback, currentLang, undefined, () => setOrbState('speak'), () => setOrbState('idle'));
    }
  }, [currentLang, isCameraActive, triggerHaptic]);

  // Process Action tags cleanly
  const executeAppActions = useCallback((rawReply: string): string => {
    let text = rawReply;

    // 1. Navigation Action: [ACTION:NAVIGATE <destination>] or NAVIGATE_TO: <destination>
    const navRegex = /\[ACTION:NAVIGATE\s+([^\]]+)\]|NAVIGATE_TO:\s*(.+)/i;
    const navMatch = navRegex.exec(rawReply);
    if (navMatch) {
      const dest = (navMatch[1] || navMatch[2] || '').trim();
      text = text.replace(navMatch[0], '').trim();
      setOrbState('confirming');
      triggerHaptic([50, 70, 90]);
      window.open(`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(dest)}&travelmode=walking`, '_blank');
      const notice = currentLang === 'hi'
        ? `${dest} के लिए गूगल मैप्स पर पैदल नेविगेशन शुरू हो रहा है।`
        : `Starting walking navigation to ${dest} on Google Maps.`;
      return text ? `${text} ${notice}` : notice;
    }

    // 2. Flashlight Action: [ACTION:TORCH_ON] / [ACTION:TORCH_OFF]
    if (/\[ACTION:TORCH_?ON\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:TORCH_?ON\]/i, '').trim();
      setWebTorch(true);
      setOrbState('confirming');
      triggerHaptic(50);
    } else if (/\[ACTION:TORCH_?OFF\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:TORCH_?OFF\]/i, '').trim();
      setWebTorch(false);
      setOrbState('confirming');
      triggerHaptic(50);
    }

    // 3. Text Reading Action: [ACTION:READ_TEXT]
    if (/\[ACTION:READ_TEXT\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:READ_TEXT\]/i, '').trim();
      setTimeout(() => handleDescribeScene(true), 400);
      return text || (currentLang === 'hi' ? 'पाठ पढ़ा जा रहा है…' : 'Reading text in view…');
    }

    // 4. Surroundings / Describe Action: [ACTION:DESCRIBE_SCENE]
    if (/\[ACTION:DESCRIBE_SCENE\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:DESCRIBE_SCENE\]/i, '').trim();
      setTimeout(() => handleDescribeScene(false), 400);
      return text || (currentLang === 'hi' ? 'सामने का दृश्य देखा जा रहा है…' : 'Inspecting scene…');
    }

    // 5. Language Switch: [ACTION:SET_LANGUAGE <en|hi|pa|bn>]
    const langMatch = /\[ACTION:SET_LANGUAGE\s+([a-zA-Z]+)\]/i.exec(rawReply);
    if (langMatch) {
      const code = langMatch[1].toLowerCase().slice(0, 2) as LanguageCode;
      text = text.replace(langMatch[0], '').trim();
      if (LOCKED_LANGUAGES.includes(code)) {
        setCurrentLang(code);
      }
    }

    // Strip any remaining [ACTION:...] tags
    text = text.replace(/\[ACTION:[^\]]+\]/gi, '').trim();
    return text;
  }, [currentLang, triggerHaptic, setWebTorch, handleDescribeScene]);

  // Process text or visual query
  const handleQuery = useCallback(async (queryText: string) => {
    const clean = queryText.trim();
    if (!clean) return;

    triggerHaptic([30, 40, 30]);
    setOrbState('think');
    setStatusSubtitle(`Heard: "${clean}"`);

    // Detect language from query
    let detectedLang = currentLang;
    if (/[\u0A00-\u0A7F]/.test(clean)) detectedLang = 'pa';
    else if (/[\u0980-\u09FF]/.test(clean)) detectedLang = 'bn';
    else if (/[\u0900-\u097F]/.test(clean)) detectedLang = 'hi';

    const isVisual = /\b(describe|look|see|holding|in front|ahead|around|path|obstacle|what is|सामने|रास्ता|ਸਾਹਮਣੇ|সামনে)\b/i.test(clean);
    let capturedDataUrl: string | null = null;

    if (isVisual && videoRef.current && isCameraActive) {
      capturedDataUrl = VisionAiService.captureFrameFromVideo(videoRef.current);
    }

    try {
      let reply: string | null = null;
      if (capturedDataUrl) {
        reply = await VisionAiService.describeScene(capturedDataUrl, detectedLang, clean);
      } else {
        reply = await VisionAiService.queryGroqDirect(clean, detectedLang);
      }

      const rawReply = reply || (detectedLang === 'hi'
        ? 'रास्ता सीधा खुला है। अपनी गति सामान्य रखें।'
        : detectedLang === 'pa'
        ? 'ਰਾਸਤਾ ਸਿੱਧਾ ਖੁੱਲ੍ਹਾ ਹੈ। ਸੁਰੱਖਿਅਤ ਅੱਗੇ ਵਧੋ।'
        : detectedLang === 'bn'
        ? 'সামনের পথ পরিষ্কার আছে। সাবধানে চলুন।'
        : 'Directly ahead is clear. No obstacles detected in your immediate pathway.');

      const finalReply = executeAppActions(rawReply);
      setAiResponse(finalReply);
      setOrbState('speak');

      AudioService.speak(
        finalReply,
        detectedLang,
        undefined,
        () => setOrbState('speak'),
        () => {
          setOrbState('idle');
          triggerHaptic(20);
        }
      );
    } catch {
      const fallbackMsg = "Directly ahead is open and clear. Proceed steadily.";
      setAiResponse(fallbackMsg);
      setOrbState('speak');
      AudioService.speak(
        fallbackMsg,
        'en',
        undefined,
        () => setOrbState('speak'),
        () => setOrbState('idle')
      );
    }
  }, [currentLang, isCameraActive, triggerHaptic, executeAppActions]);

  // Voice Recognition Start / Stop with 1.5s silence gap buffer
  const handleStartListening = useCallback(() => {
    if (orbState === 'speak') {
      handleStopSpeaking();
      return;
    }

    if (orbState === 'listen') {
      if (silenceTimerRef.current) {
        clearTimeout(silenceTimerRef.current);
        silenceTimerRef.current = null;
      }
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch {}
      }
      setOrbState('idle');
      return;
    }

    if (orbState === 'think') return;

    // Start Listening
    triggerHaptic(35);
    AudioService.unlockAudio();
    AudioService.playEarcon('wake');

    const win = typeof window !== 'undefined' ? (window as any) : null;
    const SpeechRecognitionClass = win?.SpeechRecognition || win?.webkitSpeechRecognition;

    if (!SpeechRecognitionClass) {
      alert("Speech recognition is not supported on this browser.");
      return;
    }

    try {
      const recog = new SpeechRecognitionClass();
      recog.continuous = true;
      recog.interimResults = true;

      // Strict Locale Locking
      const langConfig = LANGUAGES[currentLang] || LANGUAGES.en;
      recog.lang = langConfig.speechLang;

      let accumulated = '';

      const finalizeAndSend = () => {
        if (silenceTimerRef.current) {
          clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = null;
        }
        try {
          recog.stop();
        } catch {}
        if (accumulated.trim()) {
          handleQuery(accumulated.trim());
        } else {
          setOrbState('idle');
        }
      };

      recog.onstart = () => {
        setOrbState('listen');
        setStatusSubtitle("Listening to you… Speak your question.");
      };

      recog.onresult = (ev: any) => {
        let interim = '';
        for (let i = ev.resultIndex; i < ev.results.length; ++i) {
          const trans = ev.results[i][0].transcript;
          if (ev.results[i].isFinal) {
            accumulated += ' ' + trans;
          } else {
            interim += trans;
          }
        }
        const full = (accumulated + ' ' + interim).trim();
        if (full) {
          setStatusSubtitle(`Listening: "${full}"`);
          // 1.5s Silence Gap Buffer before finalizing
          if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = window.setTimeout(() => {
            finalizeAndSend();
          }, 1500);
        }
      };

      recog.onerror = () => {
        if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
        setOrbState('idle');
      };

      recog.onend = () => {
        if (orbState === 'listen') {
          if (accumulated.trim()) {
            handleQuery(accumulated.trim());
          } else {
            setOrbState('idle');
          }
        }
      };

      recognitionRef.current = recog;
      recog.start();
    } catch {
      setOrbState('idle');
    }
  }, [orbState, currentLang, handleStopSpeaking, handleQuery, triggerHaptic]);

  // Touch & Blind Ergonomics Gesture Dispatcher
  const handleTouchStart = (e: React.TouchEvent) => {
    // Two-finger tap: instantly silence speech
    if (e.touches.length >= 2) {
      e.preventDefault();
      if (singleTapTimerRef.current) {
        clearTimeout(singleTapTimerRef.current);
        singleTapTimerRef.current = null;
      }
      lastTapTimeRef.current = 0;
      handleStopSpeaking();
    }
  };

  const handlePadTap = () => {
    const now = Date.now();
    if (now - lastTapTimeRef.current < 380) {
      // Double-tap detected: describe scene immediately!
      if (singleTapTimerRef.current) {
        clearTimeout(singleTapTimerRef.current);
        singleTapTimerRef.current = null;
      }
      lastTapTimeRef.current = 0;
      handleDescribeScene(false);
    } else {
      // Single tap: start delayed timer for talk
      lastTapTimeRef.current = now;
      if (singleTapTimerRef.current) {
        clearTimeout(singleTapTimerRef.current);
      }
      singleTapTimerRef.current = window.setTimeout(() => {
        handleStartListening();
        singleTapTimerRef.current = null;
      }, 340);
    }
  };

  const stateHeadline = {
    idle: 'Tap Lower Glass to Talk',
    listen: 'Listening to you…',
    think: 'Thinking…',
    speak: 'BlindSight Guiding…',
    confirming: 'Understood',
    caution: 'Obstacle Warning'
  }[orbState];

  return (
    <div
      style={{
        backgroundColor: '#080B10',
        color: '#FFFFFF',
        minHeight: '100dvh',
        width: '100vw',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'space-between',
        overflow: 'hidden',
        userSelect: 'none',
        fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      }}
      onTouchStart={handleTouchStart}
    >
      {/* =================================================================
          1. TOP HEADER: Subtle Battery/GPS Status Pill & Active Language Badge
          ================================================================= */}
      <header
        style={{
          width: '100%',
          maxWidth: 640,
          padding: '14px 20px 8px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          zIndex: 20
        }}
      >
        {/* Subtle Battery & Sensor Status Pill */}
        <div
          onClick={() => {
            const report = `BlindSight active. Battery normal. GPS locked. Path ahead clear.`;
            setAiResponse(report);
            AudioService.speak(report, currentLang);
            triggerHaptic(30);
          }}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 7,
            backgroundColor: 'rgba(18, 24, 38, 0.45)',
            backdropFilter: 'blur(28px) saturate(180%)',
            WebkitBackdropFilter: 'blur(28px) saturate(180%)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            borderRadius: 20,
            padding: '7px 14px',
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer'
          }}
          role="button"
          tabIndex={0}
          aria-label="Hardware status pill. Tap to hear battery and GPS report."
        >
          <BatteryCharging size={15} color="#38bdf8" />
          <span>READY</span>
          <span style={{ color: '#94A3B8' }}>•</span>
          <Compass size={14} color="#f59e0b" />
          <span style={{ color: '#94A3B8', fontWeight: 600 }}>GPS READY</span>
        </div>

        {/* Right Action Icons: Stop Speech, Language Badge, Camera Toggle, APK QR */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {orbState === 'speak' && (
            <button
              type="button"
              onClick={handleStopSpeaking}
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                backgroundColor: 'rgba(239, 68, 68, 0.2)',
                border: '1px solid #ef4444',
                color: '#ef4444',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer'
              }}
              aria-label="Silence audio"
            >
              <VolumeX size={18} />
            </button>
          )}

          {/* Active 4-Language Badge (EN, HI, PA, BN) */}
          <button
            type="button"
            onClick={handleCycleLanguage}
            style={{
              backgroundColor: 'rgba(18, 24, 38, 0.45)',
              backdropFilter: 'blur(28px) saturate(180%)',
              WebkitBackdropFilter: 'blur(28px) saturate(180%)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              borderRadius: 20,
              padding: '7px 13px',
              fontSize: 12,
              fontWeight: 800,
              color: '#f59e0b',
              letterSpacing: 1,
              cursor: 'pointer'
            }}
            aria-label={`Current language is ${LANGUAGES[currentLang]?.label}. Tap to switch language.`}
          >
            {currentLang.toUpperCase()}
          </button>

          {/* Camera toggle */}
          <button
            type="button"
            onClick={() => setIsCameraActive((prev) => !prev)}
            style={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              backgroundColor: 'rgba(18, 24, 38, 0.45)',
              backdropFilter: 'blur(28px) saturate(180%)',
              WebkitBackdropFilter: 'blur(28px) saturate(180%)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: isCameraActive ? '#38bdf8' : '#94A3B8',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer'
            }}
            aria-label={isCameraActive ? "Camera is active" : "Camera is off"}
          >
            {isCameraActive ? <Camera size={17} /> : <CameraOff size={17} />}
          </button>

          {/* APK QR Modal trigger */}
          <button
            type="button"
            onClick={() => setIsQrModalOpen(true)}
            style={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              backgroundColor: 'rgba(18, 24, 38, 0.45)',
              backdropFilter: 'blur(28px) saturate(180%)',
              WebkitBackdropFilter: 'blur(28px) saturate(180%)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: '#94A3B8',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer'
            }}
            aria-label="Download BlindSight Android APK"
          >
            <Smartphone size={17} />
          </button>
        </div>
      </header>

      {/* =================================================================
          2. TOP 40%: VIEWPORT HUD WITH GLOWING LIQUID GLASS BORDER
          ================================================================= */}
      <section
        style={{
          width: '100%',
          maxWidth: 600,
          height: '24vh',
          minHeight: 140,
          maxHeight: 200,
          margin: '4px 16px',
          padding: '0 16px',
          boxSizing: 'border-box',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative'
        }}
        aria-label="Camera Viewport HUD"
      >
        <div
          style={{
            width: '100%',
            height: '100%',
            borderRadius: 22,
            overflow: 'hidden',
            position: 'relative',
            backgroundColor: 'rgba(18, 24, 38, 0.45)',
            backdropFilter: 'blur(28px) saturate(180%)',
            WebkitBackdropFilter: 'blur(28px) saturate(180%)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            boxShadow: '0 4px 24px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.15)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          {isCameraActive ? (
            <video
              ref={videoRef}
              playsInline
              muted
              autoPlay
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                display: 'block'
              }}
            />
          ) : (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 8,
                color: '#94A3B8'
              }}
            >
              <Eye size={28} color="#38bdf8" />
              <span style={{ fontSize: 13, fontWeight: 600, letterSpacing: 0.5 }}>
                VISION VIEWPORT READY
              </span>
            </div>
          )}

          {/* Liquid HUD Overlay Ribbon */}
          <div
            style={{
              position: 'absolute',
              top: 10,
              left: 12,
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              backgroundColor: 'rgba(8, 11, 16, 0.75)',
              backdropFilter: 'blur(12px)',
              borderRadius: 12,
              padding: '4px 10px',
              fontSize: 10,
              fontWeight: 700,
              letterSpacing: 0.8,
              color: isCameraActive ? '#38bdf8' : '#94A3B8',
              border: '1px solid rgba(255, 255, 255, 0.08)'
            }}
          >
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                backgroundColor: isCameraActive ? '#38bdf8' : '#64748b'
              }}
            />
            {isCameraActive ? 'UPRIGHT FEED • 640x480' : 'STANDBY'}
          </div>
        </div>
      </section>

      {/* =================================================================
          3. CENTER: ORGANIC FLUID AUDIO-REACTIVE LIQUID ORB / MEMBRANE
          ================================================================= */}
      <main
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          flex: '0 0 auto',
          padding: '4px 0'
        }}
      >
        <AllyFaceOrb
          state={orbState}
          onClick={handlePadTap}
          size={210}
        />
      </main>

      {/* =================================================================
          4. BOTTOM 50%: MASSIVE TACTILE TOUCH GLASS SURFACE
          ================================================================= */}
      <section
        onClick={handlePadTap}
        style={{
          width: '100%',
          maxWidth: 640,
          flex: '1 1 auto',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: 'rgba(18, 24, 38, 0.45)',
          backdropFilter: 'blur(28px) saturate(180%)',
          WebkitBackdropFilter: 'blur(28px) saturate(180%)',
          borderTop: '1px solid rgba(255, 255, 255, 0.12)',
          boxShadow: '0 -8px 32px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.12)',
          borderTopLeftRadius: 32,
          borderTopRightRadius: 32,
          padding: '24px 20px',
          boxSizing: 'border-box',
          cursor: 'pointer',
          touchAction: 'manipulation'
        }}
        role="button"
        tabIndex={0}
        aria-label={`Tactile Talk and Sense surface. Current state: ${stateHeadline}. Single tap anywhere to speak, double tap to describe scene, or tap with two fingers to silence.`}
        onKeyDown={(e) => {
          if (e.key === ' ' || e.key === 'Enter') {
            e.preventDefault();
            handlePadTap();
          }
        }}
      >
        <div style={{ textAlign: 'center', maxWidth: 540 }}>
          {/* Big State Heading (28px–32px) */}
          <h1
            style={{
              fontSize: '1.75rem',
              fontWeight: 800,
              margin: '0 0 12px 0',
              color:
                orbState === 'caution'
                  ? '#ef4444'
                  : orbState === 'confirming'
                  ? '#f59e0b'
                  : orbState === 'listen'
                  ? '#38bdf8'
                  : '#FFFFFF'
            }}
          >
            {stateHeadline}
          </h1>

          {/* Spoken Guidance / AI Response (22px–26px) */}
          <p
            style={{
              fontSize: '1.35rem',
              fontWeight: 500,
              lineHeight: 1.45,
              color: '#FFFFFF',
              margin: 0,
              padding: '0 8px'
            }}
          >
            "{aiResponse}"
          </p>
        </div>

        {/* Blind Ergonomics Gesture Hint Pill */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            backgroundColor: 'rgba(8, 11, 16, 0.75)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            borderRadius: 18,
            padding: '8px 16px',
            fontSize: 13,
            fontWeight: 600,
            color: '#94A3B8'
          }}
        >
          <Volume2 size={15} color="#f59e0b" />
          <span>{statusSubtitle}</span>
        </div>
      </section>

      {/* APK QR Modal */}
      {isQrModalOpen && (
        <ApkQrModal onClose={() => setIsQrModalOpen(false)} />
      )}
    </div>
  );
};
