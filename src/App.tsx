import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  AppState,
  ListenMode,
  LanguageCode,
  TextSize,
  ChatMessage,
  ReflexTelemetry,
  VoiceOption
} from './types';
import { LANGUAGES, CompanionStateService, HELP_TEXT } from './services/companionState';
import { AudioService, WAKE_PHRASES, STOP_WORDS, SCENE_WORDS } from './services/audioService';
import { VisionAiService } from './services/visionAi';
import { ReflexService } from './services/reflexService';
import { Header } from './components/Header';
import { StatusCard } from './components/StatusCard';
import { MicCard } from './components/MicCard';
import { ControlsCard } from './components/ControlsCard';
import { ConversationLog } from './components/ConversationLog';
import { TextInputForm } from './components/TextInputForm';
import { ReflexTelemetryModal } from './components/ReflexTelemetryModal';
import { CameraCaptureModal } from './components/CameraCaptureModal';

export const App: React.FC = () => {
  // App state
  const [appState, setAppState] = useState<AppState>('idle');
  const [listenMode, setListenMode] = useState<ListenMode>(() => {
    return (localStorage.getItem('blindsight_mode') as ListenMode) || 'wake';
  });
  const [language, setLanguage] = useState<LanguageCode>('en');
  const [voices, setVoices] = useState<VoiceOption[]>([]);
  const [chosenVoiceURI, setChosenVoiceURI] = useState<string>(() => {
    return localStorage.getItem('blindsight_voice') || '';
  });
  const [token, setToken] = useState<string>(() => {
    return localStorage.getItem('blindsight_token') || '';
  });
  const [noiseGate, setNoiseGate] = useState<boolean>(() => {
    return localStorage.getItem('blindsight_noisegate') === 'on';
  });
  const [includeCamera, setIncludeCamera] = useState<boolean>(true);
  const [textSize, setTextSize] = useState<TextSize>(() => {
    return (localStorage.getItem('blindsight_text_size') as TextSize) || 'md';
  });

  // UI status and feedback
  const [statusLine, setStatusLine] = useState<string>('Ready');
  const [micHint, setMicHint] = useState<string>('Say "hey BlindSight", or tap the button.');
  const [heardText, setHeardText] = useState<string>('');
  const [micMeterPct, setMicMeterPct] = useState<number>(0);
  const [isHot, setIsHot] = useState<boolean>(false);
  const [isCue, setIsCue] = useState<boolean>(false);
  const [announcement, setAnnouncement] = useState<string>('');
  const [latestFrameUrl, setLatestFrameUrl] = useState<string | null>(null);

  // Messages and Telemetry
  const [messages, setMessages] = useState<ChatMessage[]>(() => CompanionStateService.getHistory());
  const [telemetry, setTelemetry] = useState<ReflexTelemetry>(() => ReflexService.getTelemetry());

  // Modals
  const [isReflexModalOpen, setIsReflexModalOpen] = useState(false);
  const [isCameraModalOpen, setIsCameraModalOpen] = useState(false);

  // Recognition refs
  const recognitionRef = useRef<any>(null);
  const isContinuousRef = useRef<boolean>(false);
  const isListeningRef = useRef<boolean>(false);
  const lastNetworkRetryRef = useRef<number>(0);
  const appStateRef = useRef<AppState>(appState);
  appStateRef.current = appState;

  const announce = useCallback((text: string) => {
    setAnnouncement('');
    setTimeout(() => setAnnouncement(text), 40);
  }, []);

  // Text size change handler
  const handleTextSizeChange = (size: TextSize) => {
    setTextSize(size);
    localStorage.setItem('blindsight_text_size', size);
    if (size === 'md') {
      document.documentElement.removeAttribute('data-text-size');
    } else {
      document.documentElement.setAttribute('data-text-size', size);
    }
    AudioService.playEarcon('tick');
    announce(`Text size ${size === 'md' ? 'normal' : size === 'lg' ? 'large' : 'extra large'}`);
  };

  // Status updates helper
  const updateStatus = useCallback((line: string, mode?: AppState) => {
    if (mode) setAppState(mode);
    setStatusLine(line);
    const modeToHint: Record<AppState, string> = {
      idle:
        listenMode === 'button'
          ? 'Tap the big button and speak.'
          : listenMode === 'handsfree'
          ? 'Hands-free: just speak.'
          : 'Say "hey BlindSight", or tap the button.',
      wake: 'Say "hey BlindSight"…',
      listen: 'Go ahead, I am listening.',
      think: 'One moment…',
      speak: 'Tap the button to interrupt me.'
    };
    setMicHint(modeToHint[mode || appState]);
  }, [appState, listenMode]);

  // Voice output helper
  const speakReply = useCallback(
    (text: string, onDone?: () => void) => {
      setAppState('speak');
      updateStatus('Speaking', 'speak');
      AudioService.speak(
        text,
        language,
        chosenVoiceURI,
        () => {
          setAppState('speak');
        },
        () => {
          setAppState('idle');
          updateStatus('Ready', 'idle');
          if (onDone) onDone();
        }
      );
    },
    [language, chosenVoiceURI, updateStatus]
  );

  // Command router
  const handleCommand = useCallback(
    async (rawPrompt: string, frameDataUrl?: string) => {
      const prompt = rawPrompt.trim();
      if (!prompt) return;

      AudioService.unlockAudio();
      setHeardText(prompt);

      // Check Stop words
      const low = prompt.toLowerCase();
      if (STOP_WORDS.some(w => low === w || low.startsWith(w + ' '))) {
        AudioService.stopSpeaking();
        AudioService.playEarcon('tick');
        updateStatus('Stopped.', 'idle');
        announce('Stopped.');
        return;
      }

      // Check Help
      if (['help', 'commands', 'what can i say'].includes(low.replace(/[?!.]/g, ''))) {
        announce(HELP_TEXT);
        speakReply(HELP_TEXT);
        return;
      }

      // Check Status
      if (['read status', 'system status', 'status report'].includes(low.replace(/[?!.]/g, ''))) {
        // Never claim health we did not observe: probe what is observable.
        const srOk = AudioService.isSpeechRecognitionSupported();
        const ttsOk = typeof window !== 'undefined' && !!window.speechSynthesis;
        let gateway = 'unreachable';
        try {
          const ctrl = new AbortController();
          const timer = setTimeout(() => ctrl.abort(), 1500);
          const r = await fetch('/api/config', { signal: ctrl.signal });
          gateway = r.ok ? 'reachable' : 'unreachable';
          clearTimeout(timer);
        } catch {
          gateway = 'unreachable';
        }
        const statusSummary =
          `System status: speech recognition ${srOk ? 'available' : 'not available in this browser'}, spoken replies ${ttsOk ? 'ready' : 'unavailable'}, companion gateway ${gateway}. The ultrasonic reflex runs on the head unit and cannot be observed from this phone.`;
        announce(statusSummary);
        speakReply(statusSummary);
        return;
      }

      // Record in conversation history
      const userMsg = CompanionStateService.addHistoryMessage('user', prompt);
      setMessages(prev => [...prev, userMsg]);

      // Check for local state commands (remember, forget, language, etc.)
      const localResult = CompanionStateService.processLocalCommand(prompt, language);
      if (localResult && localResult.isLocal) {
        if (localResult.newLanguage) {
          setLanguage(localResult.newLanguage);
        }
        const assistantMsg = CompanionStateService.addHistoryMessage('assistant', localResult.reply);
        setMessages(prev => [...prev, assistantMsg]);
        announce(localResult.reply);
        speakReply(localResult.reply);
        return;
      }

      // AI Vision or Conversational companion
      setAppState('think');
      updateStatus('Thinking…', 'think');
      AudioService.playEarcon('think');

      const isSceneQuery = SCENE_WORDS.some(p => low === p || low.startsWith(p + ' '));
      const shouldAttachCamera = includeCamera || isSceneQuery;

      const result = await VisionAiService.askCompanion(prompt, {
        useCamera: shouldAttachCamera,
        imageDataUrl: frameDataUrl,
        langCode: language,
        apiToken: token
      });

      const assistantMsg = CompanionStateService.addHistoryMessage(
        'assistant',
        result.reply,
        !result.ok
      );
      setMessages(prev => [...prev, assistantMsg]);
      announce(result.reply);
      speakReply(result.reply);
    },
    [language, includeCamera, token, updateStatus, speakReply, announce]
  );

  // Speech Recognition Session
  const startRecognition = useCallback(
    (mode: 'wake' | 'command') => {
      if (!AudioService.isSpeechRecognitionSupported()) {
        announce('Speech recognition is not supported in this browser. Please use text input below.');
        return;
      }

      AudioService.unlockAudio();
      AudioService.stopSpeaking();

      const SpeechRecognition =
        (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (!SpeechRecognition) return;

      try {
        if (recognitionRef.current) {
          recognitionRef.current.abort();
        }
      } catch {}

      const rec = new SpeechRecognition();
      const langConfig = LANGUAGES[language] || LANGUAGES.en;
      rec.lang = langConfig.speechLang;
      rec.continuous = mode === 'wake' || listenMode === 'handsfree';
      rec.interimResults = false;

      rec.onstart = () => {
        isListeningRef.current = true;
        if (mode === 'wake') {
          setAppState('wake');
          updateStatus('Waiting for wake word…', 'wake');
        } else {
          setAppState('listen');
          updateStatus('Listening…', 'listen');
        }
      };

      rec.onresult = (event: any) => {
        let transcript = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          if (event.results[i].isFinal) {
            transcript += event.results[i][0].transcript + ' ';
          }
        }
        transcript = transcript.trim();
        if (!transcript) return;

        const normalized = transcript.toLowerCase();

        if (mode === 'wake') {
          const matched = WAKE_PHRASES.some(phrase => normalized.includes(phrase));
          if (matched) {
            // Wake word detected! Flash visual cue and transition to command session
            // Visual cue only: a beep here steals Android audio focus from
            // the recognizer that opens next (the v3.3 mic-killer).
            setIsCue(true);
            setTimeout(() => setIsCue(false), 450);

            // Extract prompt after wake word if spoken in one breath
            let remaining = transcript;
            for (const phrase of WAKE_PHRASES) {
              const idx = remaining.toLowerCase().indexOf(phrase);
              if (idx !== -1) {
                remaining = remaining.substring(idx + phrase.length).trim();
              }
            }

            if (remaining.length > 2) {
              handleCommand(remaining);
            } else {
              startRecognition('command');
            }
          }
        } else {
          // Command mode
          handleCommand(transcript);
        }
      };

      rec.onerror = (event: any) => {
        const err = event.error;
        if (err === 'no-speech' || err === 'aborted') return; // ordinary endings
        const msg =
          err === 'not-allowed' || err === 'service-not-allowed'
            ? 'Microphone permission denied. Tap the big button and allow the microphone.'
            : err === 'audio-capture'
            ? 'No microphone found. Plug one in or pick another input.'
            : err === 'network'
            ? 'Speech cannot reach its internet service. Check the connection; trying again.'
            : `Speech recognition problem: ${err}. Tap the button to retry.`;
        announce(msg);
        speakReply(msg);
        if (err === 'network') {
          const now = Date.now();
          if (now - lastNetworkRetryRef.current > 5000) {
            lastNetworkRetryRef.current = now;
            setTimeout(() => startRecognition(mode), 2000);
          }
        }
      };

      rec.onend = () => {
        isListeningRef.current = false;
        // Auto-restart if in wake mode and not speaking/thinking
        if (listenMode === 'wake' && appStateRef.current === 'wake') {
          setTimeout(() => {
            if (listenMode === 'wake' && appStateRef.current !== 'speak' && appStateRef.current !== 'think') {
              startRecognition('wake');
            }
          }, 400);
        } else if (listenMode === 'handsfree' && appStateRef.current !== 'speak' && appStateRef.current !== 'think') {
          setTimeout(() => {
            if (listenMode === 'handsfree') {
              startRecognition('command');
            }
          }, 500);
        }
      };

      recognitionRef.current = rec;
      try {
        rec.start();
      } catch {}
    },
    [language, listenMode, appState, updateStatus, handleCommand, announce, speakReply]
  );

  // Stop speech/listening, then restore the chosen mode so the companion
  // never stays dead after a Stop command.
  const handleStop = useCallback(() => {
    AudioService.stopSpeaking();
    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort();
      } catch {}
    }
    setAppState('idle');
    updateStatus('Stopped.', 'idle');
    announce('Stopped.');
    if (listenMode === 'wake') {
      setTimeout(() => startRecognition('wake'), 400);
    } else if (listenMode === 'handsfree') {
      setTimeout(() => startRecognition('command'), 400);
    }
  }, [updateStatus, announce, listenMode, startRecognition]);

  // Big Mic Button click handler
  const handleMicClick = () => {
    AudioService.unlockAudio();

    if (appState === 'think') {
      announce('One moment, still thinking.');
      return;
    }

    if (appState === 'speak') {
      handleStop();
      return;
    }

    if (appState === 'listen') {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch {}
      }
      setAppState('idle');
      updateStatus('Listening stopped.', 'idle');
      announce('Listening stopped.');
      return;
    }

    // Start single command session on button click. Visual cue only (see
    // mic-killer note above).
    setIsCue(true);
    setTimeout(() => setIsCue(false), 450);
    startRecognition('command');
  };

  // Init: load voices, start mic analytics if noise gate on, set text size
  useEffect(() => {
    if (textSize !== 'md') {
      document.documentElement.setAttribute('data-text-size', textSize);
    }

    const loadVoiceList = () => {
      const vList = AudioService.getAvailableVoices();
      if (vList.length > 0) {
        setVoices(vList);
      }
    };
    loadVoiceList();
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = loadVoiceList;
    }

    if (noiseGate) {
      AudioService.openMicAnalytics((pct, hot) => {
        setMicMeterPct(pct);
        setIsHot(hot);
      });
    }

    return () => {
      AudioService.stopMicAnalytics();
      AudioService.stopSpeaking();
    };
  }, []);

  // Noise gate toggle
  const handleNoiseGateToggle = (enabled: boolean) => {
    setNoiseGate(enabled);
    localStorage.setItem('blindsight_noisegate', enabled ? 'on' : 'off');
    if (enabled) {
      AudioService.openMicAnalytics((pct, hot) => {
        setMicMeterPct(pct);
        setIsHot(hot);
      });
      announce('Noise gate on. Level bar reflects current audio.');
    } else {
      AudioService.stopMicAnalytics();
      setMicMeterPct(0);
      setIsHot(false);
      announce('Noise gate off.');
    }
  };

  // Mode change
  const handleModeChange = (mode: ListenMode) => {
    setListenMode(mode);
    localStorage.setItem('blindsight_mode', mode);
    handleStop();

    if (mode === 'wake') {
      announce('Wake word mode on. Say "hey BlindSight".');
      setTimeout(() => startRecognition('wake'), 200);
    } else if (mode === 'handsfree') {
      announce('Hands-free mode on. Just speak.');
      setTimeout(() => startRecognition('command'), 200);
    } else {
      announce('Button mode. Tap the button to speak.');
      updateStatus('Button mode: tap to talk.', 'idle');
    }
  };

  // Language change
  const handleLanguageChange = (lang: LanguageCode) => {
    setLanguage(lang);
    const opt = LANGUAGES[lang];
    AudioService.playEarcon('ok');
    announce(opt.confirmation);
    speakReply(opt.confirmation);
  };

  // Voice change
  const handleVoiceChange = (uri: string) => {
    setChosenVoiceURI(uri);
    localStorage.setItem('blindsight_voice', uri);
    AudioService.playEarcon('tick');
    speakReply('This is the voice I will use.');
  };

  // Save Token
  const handleSaveToken = (newToken: string) => {
    setToken(newToken);
    localStorage.setItem('blindsight_token', newToken);
    AudioService.playEarcon('ok');
    announce('Gateway token saved.');
  };

  // Fetch Latest Frame
  const handleFetchLatestFrame = () => {
    const frame = VisionAiService.getLatestFrame();
    if (frame) {
      setLatestFrameUrl(frame);
      announce('Latest camera frame displayed for sighted helpers.');
    } else {
      setIsCameraModalOpen(true);
    }
  };

  return (
    <>
      <a className="skip-link" href="#mic-btn">
        Skip to the microphone button
      </a>

      {/* Screen-reader assertive announcer */}
      <div
        id="sr-announcer"
        className="visually-hidden"
        role="status"
        aria-live="assertive"
        aria-atomic="true"
      >
        {announcement}
      </div>

      <Header
        currentLang={LANGUAGES[language] || LANGUAGES.en}
        telemetry={telemetry}
        onOpenReflexModal={() => setIsReflexModalOpen(true)}
        onOpenCameraModal={() => setIsCameraModalOpen(true)}
      />

      <main style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <StatusCard appState={appState} statusLine={statusLine} />

        <MicCard
          appState={appState}
          listenMode={listenMode}
          micHint={micHint}
          heardText={heardText}
          micMeterPct={micMeterPct}
          isHot={isHot}
          isCue={isCue}
          onMicClick={handleMicClick}
        />

        <ControlsCard
          listenMode={listenMode}
          onModeChange={handleModeChange}
          noiseGate={noiseGate}
          onNoiseGateToggle={handleNoiseGateToggle}
          currentLanguage={language}
          onLanguageChange={handleLanguageChange}
          voices={voices}
          chosenVoiceURI={chosenVoiceURI}
          onVoiceChange={handleVoiceChange}
          includeCamera={includeCamera}
          onCameraToggle={setIncludeCamera}
          textSize={textSize}
          onTextSizeChange={handleTextSizeChange}
          token={token}
          onSaveToken={handleSaveToken}
          onHelp={() => {
            announce(HELP_TEXT);
            speakReply(HELP_TEXT);
          }}
          onStop={handleStop}
          latestFrameUrl={latestFrameUrl}
          onFetchLatestFrame={handleFetchLatestFrame}
        />

        <ConversationLog messages={messages} />

        <TextInputForm onSubmitPrompt={handleCommand} disabled={appState === 'think'} />
      </main>

      {/* Modals */}
      <ReflexTelemetryModal
        telemetry={telemetry}
        isOpen={isReflexModalOpen}
        onClose={() => setIsReflexModalOpen(false)}
        onUpdateTelemetry={setTelemetry}
      />

      <CameraCaptureModal
        isOpen={isCameraModalOpen}
        onClose={() => setIsCameraModalOpen(false)}
        langCode={language}
        onDescribeScene={dataUrl => {
          setLatestFrameUrl(dataUrl);
          handleCommand('describe the scene', dataUrl);
          setIsCameraModalOpen(false);
        }}
      />
    </>
  );
};
