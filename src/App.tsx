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
import { AudioService, WAKE_PHRASES, STOP_WORDS, SCENE_WORDS, cleanSpeech } from './services/audioService';
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
  const isListeningRef = useRef<boolean>(false);
  const activeSessionRef = useRef<'wake' | 'command' | null>(null);
  const isTransitioningRef = useRef<boolean>(false);
  const networkRetriesRef = useRef<number>(0);
  const hasErrorRef = useRef<boolean>(false);
  const appStateRef = useRef<AppState>(appState);
  appStateRef.current = appState;
  const listenModeRef = useRef<ListenMode>(listenMode);
  listenModeRef.current = listenMode;
  const startRecognitionRef = useRef<(mode: 'wake' | 'command') => void>(() => {});

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

  const updateStatus = useCallback((line: string, state?: AppState) => {
    setStatusLine(line);
    if (state) setAppState(state);

    const mode = state || appStateRef.current;
    const modeToHint: Record<AppState, string> = {
      idle:
        listenModeRef.current === 'button'
          ? 'Tap the big button and speak.'
          : listenModeRef.current === 'handsfree'
          ? 'Hands-free: just speak.'
          : 'Say "hey BlindSight", or tap the button.',
      wake: 'Waiting for "hey BlindSight"…',
      listen: 'Go ahead, I am listening.',
      think: 'One moment…',
      speak: 'Tap the button to interrupt me.'
    };
    setMicHint(modeToHint[mode]);
  }, []);

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
          setTimeout(() => {
            if (listenModeRef.current === 'wake') {
              startRecognitionRef.current('wake');
            } else if (listenModeRef.current === 'handsfree') {
              startRecognitionRef.current('command');
            }
          }, 350);
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

      const cleaned = cleanSpeech(prompt);

      // Check Stop words
      if (STOP_WORDS.some(w => cleaned === w || cleaned.startsWith(w + ' '))) {
        AudioService.stopSpeaking();
        AudioService.playEarcon('tick');
        updateStatus('Stopped.', 'idle');
        announce('Stopped.');
        return;
      }

      // Check Help
      if (['help', 'commands', 'what can i say', 'instructions'].includes(cleaned)) {
        announce(HELP_TEXT);
        speakReply(HELP_TEXT);
        return;
      }

      // Check Status
      if (['read status', 'system status', 'status report', 'status'].includes(cleaned)) {
        const srOk = AudioService.isSpeechRecognitionSupported();
        const ttsOk = typeof window !== 'undefined' && !!window.speechSynthesis;
        let gateway = 'unreachable';
        try {
          const ctrl = new AbortController();
          const timer = setTimeout(() => ctrl.abort(), 1200);
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

      // Check for local state commands (greetings, remember, forget, language, time, etc.)
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

      const isSceneQuery = SCENE_WORDS.some(p => cleaned === p || cleaned.startsWith(p + ' '));
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
      // Release any open mic analytics stream to give Web Speech API exclusive mic access on Windows
      AudioService.stopMicAnalytics();

      const SpeechRecognition =
        (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (!SpeechRecognition) return;

      try {
        if (recognitionRef.current) {
          recognitionRef.current.abort();
        }
      } catch {}

      activeSessionRef.current = mode;
      isTransitioningRef.current = false;

      const rec = new SpeechRecognition();
      const langConfig = LANGUAGES[language] || LANGUAGES.en;
      let targetLang = langConfig.speechLang;
      if (language === 'en' && typeof navigator !== 'undefined' && navigator.language && navigator.language.toLowerCase().startsWith('en')) {
        targetLang = navigator.language;
      }
      rec.lang = targetLang || 'en-US';
      rec.continuous = mode === 'wake' || listenModeRef.current === 'handsfree';
      rec.interimResults = mode === 'wake';

      rec.onstart = () => {
        isListeningRef.current = true;
        hasErrorRef.current = false;
        networkRetriesRef.current = 0;
        if (mode === 'wake') {
          setAppState('wake');
          updateStatus('Waiting for "hey BlindSight"…', 'wake');
        } else {
          setAppState('listen');
          updateStatus('Listening… go ahead.', 'listen');
        }
      };

      rec.onresult = (event: any) => {
        let transcript = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          transcript += event.results[i][0].transcript + ' ';
        }
        transcript = transcript.trim();
        if (!transcript) return;

        const cleaned = cleanSpeech(transcript);

        if (mode === 'wake') {
          const matchedPhrase = WAKE_PHRASES.find(phrase => cleaned.includes(phrase));
          if (matchedPhrase) {
            // Wake word detected! Transition safely to command session
            isTransitioningRef.current = true;
            try { rec.abort(); } catch {}
            setIsCue(true);
            setTimeout(() => setIsCue(false), 450);

            // Extract prompt after wake word if spoken in one breath
            const phraseIdx = cleaned.indexOf(matchedPhrase);
            const remaining = cleaned.substring(phraseIdx + matchedPhrase.length).trim();

            if (remaining.length > 2) {
              isTransitioningRef.current = false;
              handleCommand(remaining);
            } else {
              AudioService.playEarcon('wake');
              setTimeout(() => {
                isTransitioningRef.current = false;
                startRecognition('command');
              }, 180);
            }
            return;
          }
        } else {
          // Command mode: process when finalized
          let finalTranscript = '';
          for (let i = 0; i < event.results.length; i++) {
            if (event.results[i].isFinal) {
              finalTranscript += event.results[i][0].transcript + ' ';
            }
          }
          finalTranscript = finalTranscript.trim();
          if (finalTranscript) {
            handleCommand(finalTranscript);
          }
        }
      };

      rec.onerror = (event: any) => {
        const err = event.error;
        if (err === 'no-speech' || err === 'aborted') return; // ordinary endings

        hasErrorRef.current = true;

        if (err === 'not-allowed' || err === 'service-not-allowed') {
          const msg = 'Microphone permission denied. Tap the big button and allow microphone access in your browser.';
          updateStatus(msg, 'idle');
          announce(msg);
          return;
        }

        if (err === 'audio-capture') {
          const msg = 'No microphone found. Please connect a microphone.';
          updateStatus(msg, 'idle');
          announce(msg);
          return;
        }

        if (err === 'network') {
          hasErrorRef.current = true;
          const msg = 'Open.Jarvis local voice engine ready. Tap the big button and speak.';
          updateStatus(msg, 'idle');
          announce(msg);
          return;
        }

        updateStatus(`Speech note: ${err}.`, 'idle');
      };

      rec.onend = () => {
        isListeningRef.current = false;

        // If an error occurred, do NOT auto-restart here! Let the error handler or user action restart it
        if (hasErrorRef.current) {
          return;
        }

        // If intentionally transitioning to command, do not re-run wake
        if (isTransitioningRef.current) {
          return;
        }

        // If this was a command session and ended while listening (e.g. silence timeout)
        if (activeSessionRef.current === 'command') {
          activeSessionRef.current = null;
          if (appStateRef.current === 'listen') {
            setAppState('idle');
            if (listenModeRef.current === 'wake') {
              updateStatus('Waiting for "hey BlindSight"…', 'wake');
              setTimeout(() => {
                if (listenModeRef.current === 'wake' && appStateRef.current === 'idle') {
                  startRecognition('wake');
                }
              }, 400);
            } else if (listenModeRef.current === 'handsfree') {
              setTimeout(() => {
                if (listenModeRef.current === 'handsfree' && appStateRef.current === 'idle') {
                  startRecognition('command');
                }
              }, 400);
            } else {
              updateStatus('Tap the big button and speak.', 'idle');
            }
          }
          return;
        }

        // Auto-restart wake listener if we were in wake mode and not speaking/thinking
        if (listenModeRef.current === 'wake' && appStateRef.current !== 'speak' && appStateRef.current !== 'think') {
          setTimeout(() => {
            if (listenModeRef.current === 'wake' && appStateRef.current !== 'speak' && appStateRef.current !== 'think' && activeSessionRef.current !== 'command') {
              startRecognition('wake');
            }
          }, 350);
        } else if (listenModeRef.current === 'handsfree' && appStateRef.current !== 'speak' && appStateRef.current !== 'think') {
          setTimeout(() => {
            if (listenModeRef.current === 'handsfree' && appStateRef.current !== 'speak' && appStateRef.current !== 'think') {
              startRecognition('command');
            }
          }, 450);
        }
      };

      recognitionRef.current = rec;
      startRecognitionRef.current = startRecognition;
      try {
        rec.start();
      } catch {}
    },
    [language, updateStatus, handleCommand, announce]
  );

  // Stop speech/listening, then restore the chosen mode
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

    // Reset network retries counter on user intentional tap
    networkRetriesRef.current = 0;

    // If speech recognition had network error or not supported, use Open.Jarvis direct voice
    if (hasErrorRef.current || !AudioService.isSpeechRecognitionSupported()) {
      setIsCue(true);
      setTimeout(() => setIsCue(false), 450);
      (async () => {
        setAppState('listen');
        updateStatus('Listening with Open.Jarvis… speak now.', 'listen');
        AudioService.playEarcon('listen');
        const wavBlob = await AudioService.recordWav(3800, lvl => setMicLevel(lvl));
        if (!wavBlob) {
          setAppState('idle');
          updateStatus('Could not capture audio from microphone.', 'idle');
          return;
        }
        setAppState('think');
        updateStatus('Open.Jarvis thinking…', 'think');
        AudioService.playEarcon('think');
        const turn = await AudioService.sendVoiceToJarvis(
          wavBlob,
          language,
          () => {
            setAppState('speak');
            updateStatus('Speaking…', 'speak');
          },
          () => {
            setAppState('idle');
            updateStatus('Ready', 'idle');
          }
        );
        if (turn.transcript) {
          setHeardText(turn.transcript);
          const userMsg = CompanionStateService.addHistoryMessage('user', turn.transcript);
          const assistantMsg = CompanionStateService.addHistoryMessage('assistant', turn.reply);
          setMessages(prev => [...prev, userMsg, assistantMsg]);
        } else {
          setAppState('idle');
          updateStatus('Ready (Tap to speak)', 'idle');
        }
      })();
      return;
    }

    // Start single command session on button click. Visual cue only
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

    if (noiseGate && listenModeRef.current === 'button') {
      AudioService.openMicAnalytics((pct, hot) => {
        setMicMeterPct(pct);
        setIsHot(hot);
      });
    }

    // Auto-start wake listener on boot if in wake or handsfree mode
    const bootTimer = setTimeout(() => {
      if (listenModeRef.current === 'wake' || listenModeRef.current === 'handsfree') {
        startRecognition(listenModeRef.current === 'handsfree' ? 'command' : 'wake');
      } else {
        updateStatus('Tap the big button and speak.', 'idle');
      }
    }, 600);

    return () => {
      clearTimeout(bootTimer);
      AudioService.stopMicAnalytics();
      AudioService.stopSpeaking();
    };
  }, [startRecognition, noiseGate, textSize, updateStatus]);

  // Noise gate toggle
  const handleNoiseGateToggle = (enabled: boolean) => {
    setNoiseGate(enabled);
    localStorage.setItem('blindsight_noisegate', enabled ? 'on' : 'off');
    if (enabled) {
      if (appState === 'idle' && listenMode === 'button') {
        AudioService.openMicAnalytics((pct, hot) => {
          setMicMeterPct(pct);
          setIsHot(hot);
        });
      }
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
    listenModeRef.current = mode;
    networkRetriesRef.current = 0;
    handleStop();

    if (mode === 'wake') {
      announce('Wake word mode on. Say "hey BlindSight".');
      updateStatus('Waiting for "hey BlindSight"…', 'wake');
      setTimeout(() => startRecognition('wake'), 250);
    } else if (mode === 'handsfree') {
      announce('Hands-free mode on. Just speak.');
      updateStatus('Hands-free listening…', 'listen');
      setTimeout(() => startRecognition('command'), 250);
    } else {
      announce('Button mode. Tap the button to speak.');
      updateStatus('Tap the big button and speak.', 'idle');
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
