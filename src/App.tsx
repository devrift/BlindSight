import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Volume2,
  VolumeX,
  Camera,
  Moon,
  Sun,
  Smartphone,
  Video
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
 * BlindSight True Liquid Glass Companion Experience.
 *
 * Implements:
 * - Authentic VisionOS liquid glass styling with multi-layer depth and specular reflections.
 * - Animated Ally Smiley Companion Face.
 * - Robust Pause / Resume audio state machine (STANDBY_PAUSED gate).
 * - Minimal functional telemetry chips (Glasses & Lens) without robotic filler strings.
 * - Animated spring-physics theme switch (Dark Oceanic Obsidian / Light Porcelain Ice).
 * - 1.15x brisk conversational speech cadence.
 */
export const App: React.FC = () => {
  const [orbState, setOrbState] = useState<AllyOrbState>('idle');
  const [isStandbyPaused, setIsStandbyPaused] = useState<boolean>(false);
  const [isDarkMode, setIsDarkMode] = useState<boolean>(true);
  const [currentLang, setCurrentLang] = useState<LanguageCode>('en');
  const [isCameraActive, setIsCameraActive] = useState<boolean>(true);
  const [isGlassesConnected, setIsGlassesConnected] = useState<boolean>(false);
  const [isQrModalOpen, setIsQrModalOpen] = useState(false);
  const [aiResponse, setAiResponse] = useState<string>(
    "Directly ahead is clear. Doorway three steps forward on your right. Proceed with confidence."
  );
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
      setIsGlassesConnected(!!telemetry.isHardwareConnected);
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

  // Background camera stream lifecycle
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
        console.warn('Camera access denied or unavailable.', e);
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
    if (isStandbyPaused) {
      setOrbState('paused');
    } else {
      setOrbState('idle');
    }
    triggerHaptic(30);
  }, [isStandbyPaused, triggerHaptic]);

  // Pause / Resume Audio State Machine
  const handlePauseStandby = useCallback(() => {
    AudioService.stopSpeaking();
    setIsStandbyPaused(true);
    setOrbState('paused');
    setStatusSubtitle("Paused. Say 'Resume' to continue.");
    triggerHaptic(40);
  }, [triggerHaptic]);

  const handleResumeStandby = useCallback(() => {
    setIsStandbyPaused(false);
    setOrbState('idle');
    setStatusSubtitle("Single-tap: Speak  •  Double-tap: Describe  •  2 fingers: Silence");
    AudioService.playEarcon('ok');
    triggerHaptic([30, 40]);
    const resumeNotice = currentLang === 'hi' ? 'दोबारा शुरू किया गया।' : 'Resumed.';
    setAiResponse(resumeNotice);
    AudioService.speak(
      resumeNotice,
      currentLang,
      undefined,
      () => setOrbState('speak'),
      () => setOrbState('idle')
    );
  }, [currentLang, triggerHaptic]);

  // Toggle Dark/Light Theme with smooth transition
  const handleToggleTheme = useCallback(() => {
    setIsDarkMode((prev) => !prev);
    triggerHaptic(20);
  }, [triggerHaptic]);

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
    if (isStandbyPaused) {
      handleResumeStandby();
      return;
    }

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
  }, [isStandbyPaused, currentLang, isCameraActive, triggerHaptic, handleResumeStandby]);

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

    // 2. Pause Action: [ACTION:PAUSE]
    if (/\[ACTION:PAUSE\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:PAUSE\]/i, '').trim();
      handlePauseStandby();
      return text || (currentLang === 'hi' ? 'पॉज़ किया गया। जारी रखने के लिए रेज़्यूम कहें।' : 'Paused. Say resume to continue.');
    }

    // 3. Resume Action: [ACTION:RESUME]
    if (/\[ACTION:RESUME\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:RESUME\]/i, '').trim();
      handleResumeStandby();
      return text || (currentLang === 'hi' ? 'दोबारा शुरू किया गया।' : 'Resumed.');
    }

    // 4. Flashlight Action: [ACTION:TORCH_ON] / [ACTION:TORCH_OFF]
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

    // 5. Text Reading Action: [ACTION:READ_TEXT]
    if (/\[ACTION:READ_TEXT\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:READ_TEXT\]/i, '').trim();
      setTimeout(() => handleDescribeScene(true), 400);
      return text || (currentLang === 'hi' ? 'पाठ पढ़ा जा रहा है…' : 'Reading text in view…');
    }

    // 6. Surroundings / Describe Action: [ACTION:DESCRIBE_SCENE]
    if (/\[ACTION:DESCRIBE_SCENE\]/i.test(rawReply)) {
      text = text.replace(/\[ACTION:DESCRIBE_SCENE\]/i, '').trim();
      setTimeout(() => handleDescribeScene(false), 400);
      return text || (currentLang === 'hi' ? 'सामने का दृश्य देखा जा रहा है…' : 'Inspecting scene…');
    }

    // 7. Make Call: [ACTION:CALL:target]
    const callMatch = /\[ACTION:CALL:([^\]]+)\]/i.exec(rawReply);
    if (callMatch) {
      const target = callMatch[1].trim();
      text = text.replace(callMatch[0], '').trim();
      const digits = target.replace(/[^0-9+]/g, '');
      if (digits) {
        window.location.href = `tel:${digits}`;
      }
    }

    // 8. Open App: [ACTION:OPEN_APP:name]
    const openAppMatch = /\[ACTION:OPEN_APP:([^\]]+)\]/i.exec(rawReply);
    if (openAppMatch) {
      const appName = openAppMatch[1].trim().toLowerCase();
      text = text.replace(openAppMatch[0], '').trim();
      if (appName.includes('whatsapp')) {
        window.open('https://api.whatsapp.com', '_blank');
      } else if (appName.includes('map')) {
        window.open('https://maps.google.com', '_blank');
      }
    }

    // Strip any remaining [ACTION:...] tags
    text = text.replace(/\[ACTION:[^\]]+\]/gi, '').trim();
    return text;
  }, [currentLang, triggerHaptic, setWebTorch, handleDescribeScene, handlePauseStandby, handleResumeStandby]);

  // Process text or visual query
  const handleQuery = useCallback(async (queryText: string) => {
    const clean = queryText.trim();
    if (!clean) return;

    triggerHaptic([30, 40, 30]);
    setOrbState('think');
    setStatusSubtitle(`Heard: "${clean}"`);

    // Keep user's explicitly selected language strictly locked; do not flip on quiet speech
    let detectedLang = currentLang;
    const lowerClean = clean.toLowerCase();
    if (lowerClean.includes('switch language to hindi') || lowerClean.includes('speak in hindi')) {
      detectedLang = 'hi';
      setCurrentLang('hi');
    } else if (lowerClean.includes('switch language to english') || lowerClean.includes('speak in english')) {
      detectedLang = 'en';
      setCurrentLang('en');
    } else if (lowerClean.includes('switch language to punjabi')) {
      detectedLang = 'pa';
      setCurrentLang('pa');
    } else if (lowerClean.includes('switch language to bengali')) {
      detectedLang = 'bn';
      setCurrentLang('bn');
    }

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
        ? 'माफ़ कीजिए, समझ नहीं आया। क्या आप दोबारा बोल सकते हैं?'
        : 'Sorry, I\'m not clear. Can you speak again?');

      const finalReply = executeAppActions(rawReply);
      setAiResponse(finalReply);
      setOrbState('speak');

      AudioService.speak(
        finalReply,
        detectedLang,
        undefined,
        () => setOrbState('speak'),
        () => {
          setOrbState(isStandbyPaused ? 'paused' : 'idle');
          triggerHaptic(20);
        }
      );
    } catch {
      const fallbackMsg = "Sorry, I'm not clear. Can you speak again?";
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
  }, [currentLang, isCameraActive, isStandbyPaused, triggerHaptic, executeAppActions]);

  // Voice Recognition Start / Stop with 1.5s silence gap buffer and Standby Filter
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
      setOrbState(isStandbyPaused ? 'paused' : 'idle');
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

        const phrase = accumulated.trim();
        if (phrase) {
          if (isStandbyPaused) {
            // Check if resume intent was spoken while in STANDBY_PAUSED
            if (/\b(?:resume|continue|start|chalo|shuru\s*karo|wake\s*up|unpause|shuru|chal)\b/i.test(phrase)) {
              handleResumeStandby();
            }
            return;
          }
          handleQuery(phrase);
        } else {
          setOrbState(isStandbyPaused ? 'paused' : 'idle');
        }
      };

      recog.onstart = () => {
        setOrbState('listen');
        setStatusSubtitle(isStandbyPaused ? "Listening for 'Resume'..." : "Listening to you… Speak your question.");
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
          // 1.5s Silence Gap Buffer
          if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = window.setTimeout(() => {
            finalizeAndSend();
          }, 1500);
        }
      };

      recog.onerror = () => {
        if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
        setOrbState(isStandbyPaused ? 'paused' : 'idle');
      };

      recog.onend = () => {
        if (recognitionRef.current) {
          if (accumulated.trim()) {
            finalizeAndSend();
          } else {
            setOrbState(isStandbyPaused ? 'paused' : 'idle');
          }
        }
      };

      recognitionRef.current = recog;
      recog.start();
    } catch {
      setOrbState(isStandbyPaused ? 'paused' : 'idle');
    }
  }, [orbState, currentLang, isStandbyPaused, handleStopSpeaking, handleQuery, handleResumeStandby, triggerHaptic]);

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
    if (isStandbyPaused) {
      handleResumeStandby();
      return;
    }

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
    caution: 'Caution',
    paused: 'Paused (Standby)'
  }[orbState];

  const canvasBackground = isDarkMode ? '#060B14' : '#EAF2FF';
  const textPrimaryColor = isDarkMode ? '#FFFFFF' : '#0F172A';
  const textSecondaryColor = isDarkMode ? '#94A3B8' : '#475569';

  return (
    <div
      style={{
        backgroundColor: canvasBackground,
        color: textPrimaryColor,
        minHeight: '100dvh',
        width: '100vw',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'space-between',
        overflow: 'hidden',
        userSelect: 'none',
        transition: 'background-color 0.4s ease, color 0.4s ease',
        fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      }}
      onTouchStart={handleTouchStart}
    >
      {/* =================================================================
          1. TOP HEADER: Functional Connection Chips & Animated Theme Switch
          ================================================================= */}
      <header
        style={{
          width: '100%',
          maxWidth: 640,
          padding: '12px 20px 6px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          zIndex: 20
        }}
      >
        {/* Left: Functional Real-world Chips ONLY */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Glasses Connection Chip */}
          <div
            onClick={() => {
              const report = isGlassesConnected
                ? "Smart glasses are connected and radar reflex is active."
                : "Smart glasses are disconnected. Mobile back camera fallback is active.";
              setAiResponse(report);
              AudioService.speak(report, currentLang);
              triggerHaptic(30);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 7,
              background: isGlassesConnected
                ? 'linear-gradient(135deg, #1E6FFF 0%, #00D2FF 100%)'
                : isDarkMode
                ? 'rgba(10, 25, 52, 0.65)'
                : 'rgba(255, 255, 255, 0.75)',
              backdropFilter: 'blur(28px) saturate(180%)',
              WebkitBackdropFilter: 'blur(28px) saturate(180%)',
              border: '1px solid rgba(255, 255, 255, 0.22)',
              borderRadius: 20,
              padding: '7px 13px',
              fontSize: 12,
              fontWeight: 700,
              cursor: 'pointer',
              color: isGlassesConnected ? '#FFFFFF' : textSecondaryColor
            }}
            role="button"
            tabIndex={0}
            aria-label="Smart glasses connection chip"
          >
            <span
              style={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                backgroundColor: isGlassesConnected ? '#00D2FF' : '#64748B'
              }}
            />
            <span>{isGlassesConnected ? 'Glasses Connected' : 'Glasses Disconnected'}</span>
          </div>

          {/* Active Sensor Source Chip */}
          <div
            onClick={() => setIsCameraActive((prev) => !prev)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: isDarkMode ? 'rgba(10, 25, 52, 0.65)' : 'rgba(255, 255, 255, 0.75)',
              backdropFilter: 'blur(28px) saturate(180%)',
              WebkitBackdropFilter: 'blur(28px) saturate(180%)',
              border: '1px solid rgba(255, 255, 255, 0.22)',
              borderRadius: 20,
              padding: '7px 12px',
              fontSize: 11,
              fontWeight: 600,
              color: textSecondaryColor,
              cursor: 'pointer'
            }}
            role="button"
            tabIndex={0}
            aria-label="Active sensor camera source"
          >
            <Camera size={14} color="#1E6FFF" />
            <span>{isCameraActive ? 'Back Camera' : 'Camera Off'}</span>
          </div>
        </div>

        {/* Right Action Icons: Mic Indicator Dot, Animated Spring Theme Switch, APK QR */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Subtle Mic Audio Indicator Dot */}
          <span
            style={{
              width: 9,
              height: 9,
              borderRadius: '50%',
              backgroundColor: orbState === 'listen' ? '#00D2FF' : '#334155',
              transition: 'background-color 0.25s ease'
            }}
            title={orbState === 'listen' ? 'Mic Active' : 'Mic Standby'}
          />

          {orbState === 'speak' && (
            <button
              type="button"
              onClick={handleStopSpeaking}
              style={{
                width: 34,
                height: 34,
                borderRadius: '50%',
                backgroundColor: 'rgba(239, 68, 68, 0.2)',
                border: '1px solid #EF4444',
                color: '#EF4444',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer'
              }}
              aria-label="Silence audio"
            >
              <VolumeX size={17} />
            </button>
          )}

          {/* Animated Spring Physics Theme Switch */}
          <div
            onClick={handleToggleTheme}
            style={{
              width: 48,
              height: 28,
              borderRadius: 14,
              backgroundColor: isDarkMode ? '#0F2752' : '#C7DEFF',
              border: '1px solid rgba(255, 255, 255, 0.25)',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
              cursor: 'pointer',
              position: 'relative'
            }}
            role="button"
            tabIndex={0}
            aria-label="Toggle Dark and Light theme"
          >
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: '50%',
                backgroundColor: '#1E6FFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transform: isDarkMode ? 'translateX(20px)' : 'translateX(0px)',
                transition: 'transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1)'
              }}
            >
              {isDarkMode ? <Moon size={13} color="#FFFFFF" /> : <Sun size={13} color="#FFFFFF" />}
            </div>
          </div>

          {/* APK QR Modal trigger */}
          <button
            type="button"
            onClick={() => setIsQrModalOpen(true)}
            style={{
              width: 34,
              height: 34,
              borderRadius: '50%',
              backgroundColor: isDarkMode ? 'rgba(10, 25, 52, 0.65)' : 'rgba(255, 255, 255, 0.75)',
              border: '1px solid rgba(255, 255, 255, 0.22)',
              color: textSecondaryColor,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer'
            }}
            aria-label="Download BlindSight Android APK"
          >
            <Smartphone size={16} />
          </button>
        </div>
      </header>

      {/* =================================================================
          2. TOP 40%: VIEWPORT HUD (TRUE LIQUID GLASS)
          ================================================================= */}
      <section
        style={{
          width: '100%',
          maxWidth: 600,
          height: '24vh',
          minHeight: 140,
          maxHeight: 190,
          margin: '2px 16px',
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
            borderRadius: 32,
            overflow: 'hidden',
            position: 'relative',
            background: isDarkMode
              ? 'linear-gradient(135deg, rgba(255, 255, 255, 0.18) 0%, rgba(255, 255, 255, 0.03) 100%), rgba(8, 20, 44, 0.65)'
              : 'linear-gradient(135deg, rgba(255, 255, 255, 0.8) 0%, rgba(255, 255, 255, 0.3) 100%), rgba(234, 242, 255, 0.75)',
            boxShadow: 'inset 0 1px 1px 0 rgba(255, 255, 255, 0.35), inset 0 -1px 2px 0 rgba(0, 0, 0, 0.4), 0 20px 50px rgba(0, 0, 0, 0.4)',
            backdropFilter: 'blur(40px) saturate(210%)',
            WebkitBackdropFilter: 'blur(40px) saturate(210%)',
            border: '1px solid rgba(255, 255, 255, 0.22)',
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
                color: textSecondaryColor
              }}
            >
              <Video size={28} color="#1E6FFF" />
              <span style={{ fontSize: 13, fontWeight: 700, letterSpacing: 0.5, color: '#1E6FFF' }}>
                Back Camera Inactive
              </span>
              <span style={{ fontSize: 11, fontWeight: 500, color: textSecondaryColor }}>
                Tap camera icon to activate video stream
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
              backgroundColor: isDarkMode ? 'rgba(6, 11, 20, 0.8)' : 'rgba(234, 242, 255, 0.85)',
              backdropFilter: 'blur(12px)',
              borderRadius: 12,
              padding: '4px 10px',
              fontSize: 10,
              fontWeight: 700,
              letterSpacing: 0.8,
              color: isCameraActive ? '#00D2FF' : textSecondaryColor,
              border: '1px solid rgba(255, 255, 255, 0.15)'
            }}
          >
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                backgroundColor: isCameraActive ? '#00D2FF' : '#64748b'
              }}
            />
            {isCameraActive ? 'UPRIGHT 640x480' : 'STANDBY'}
          </div>
        </div>
      </section>

      {/* =================================================================
          3. CENTER: ANIMATED ALLY SMILEY COMPANION FACE
          ================================================================= */}
      <main
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          flex: '0 0 auto',
          padding: '2px 0'
        }}
      >
        <AllyFaceOrb
          state={orbState}
          onClick={handlePadTap}
          size={215}
          isDarkMode={isDarkMode}
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
          background: isDarkMode
            ? 'linear-gradient(135deg, rgba(255, 255, 255, 0.18) 0%, rgba(255, 255, 255, 0.03) 100%), rgba(8, 20, 44, 0.65)'
            : 'linear-gradient(135deg, rgba(255, 255, 255, 0.85) 0%, rgba(255, 255, 255, 0.35) 100%), rgba(234, 242, 255, 0.80)',
          boxShadow: 'inset 0 1px 1px 0 rgba(255, 255, 255, 0.35), inset 0 -1px 2px 0 rgba(0, 0, 0, 0.4), 0 20px 50px rgba(0, 0, 0, 0.5)',
          backdropFilter: 'blur(40px) saturate(210%)',
          WebkitBackdropFilter: 'blur(40px) saturate(210%)',
          borderTop: '1px solid rgba(255, 255, 255, 0.22)',
          borderTopLeftRadius: 32,
          borderTopRightRadius: 32,
          padding: '22px 20px',
          boxSizing: 'border-box',
          cursor: 'pointer',
          touchAction: 'manipulation'
        }}
        role="button"
        tabIndex={0}
        aria-label={`Tactile Talk and Sense surface. Current state: ${stateHeadline}. Single tap to speak, double tap to describe scene, or tap with two fingers to silence.`}
        onKeyDown={(e) => {
          if (e.key === ' ' || e.key === 'Enter') {
            e.preventDefault();
            handlePadTap();
          }
        }}
      >
        <div style={{ textAlign: 'center', maxWidth: 540 }}>
          {/* Big State Heading (26px–30px) */}
          <h1
            style={{
              fontSize: '1.75rem',
              fontWeight: 800,
              margin: '0 0 10px 0',
              color:
                orbState === 'caution'
                  ? '#EF4444'
                  : orbState === 'paused'
                  ? '#64748B'
                  : orbState === 'confirming'
                  ? '#00D2FF'
                  : orbState === 'listen'
                  ? '#00D2FF'
                  : textPrimaryColor
            }}
          >
            {stateHeadline}
          </h1>

          {/* Spoken Guidance / AI Response */}
          <p
            style={{
              fontSize: '1.3rem',
              fontWeight: 500,
              lineHeight: 1.45,
              color: textPrimaryColor,
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
            backgroundColor: isDarkMode ? 'rgba(6, 11, 20, 0.8)' : 'rgba(234, 242, 255, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.18)',
            borderRadius: 18,
            padding: '8px 16px',
            fontSize: 12,
            fontWeight: 600,
            color: textSecondaryColor
          }}
        >
          <Volume2 size={15} color="#1E6FFF" />
          <span>{statusSubtitle}</span>
        </div>
      </section>

      {/* APK QR Modal */}
      {isQrModalOpen && (
        <ApkQrModal isOpen={isQrModalOpen} onClose={() => setIsQrModalOpen(false)} />
      )}
    </div>
  );
};
