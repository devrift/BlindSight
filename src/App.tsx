import React, { useState } from 'react';
import { Download, QrCode, Smartphone, Sparkles, CheckCircle2, Volume2, VolumeX, Send, RefreshCw, ShieldAlert, Cpu } from 'lucide-react';
import { VisionAiService } from './services/visionAi';
import { ApkQrModal } from './components/ApkQrModal';

export const App: React.FC = () => {
  const [prompt, setPrompt] = useState('');
  const [aiResponse, setAiResponse] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [latencySec, setLatencySec] = useState<number | null>(null);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [isQrModalOpen, setIsQrModalOpen] = useState(false);

  // Live in-browser Groq AI query
  const handleAskAi = async (queryText?: string) => {
    const textToQuery = (queryText || prompt).trim();
    if (!textToQuery || isLoading) return;

    setIsLoading(true);
    setAiResponse(null);
    setLatencySec(null);

    const isHindi = /[\u0900-\u097F]/.test(textToQuery);
    const langCode = isHindi ? 'hi' : 'en';

    const t0 = performance.now();
    try {
      const result = await VisionAiService.queryGroqDirect(textToQuery, langCode);
      const elapsed = (performance.now() - t0) / 1000;
      setLatencySec(Number(elapsed.toFixed(2)));

      if (result) {
        setAiResponse(result);
        speakText(result, langCode);
      } else {
        setAiResponse(
          langCode === 'hi'
            ? 'नमस्ते! मैं ब्लाइंडसाइट हूँ—आपका बुद्धिमान दृश्य गाइड और वॉयस साथी।'
            : "I'm BlindSight, your dedicated visual guide and voice companion walking right beside you."
        );
      }
    } catch {
      setAiResponse("I'm BlindSight, your dedicated visual guide and voice companion walking right beside you.");
    } finally {
      setIsLoading(false);
    }
  };

  const speakText = (text: string, lang: 'en' | 'hi') => {
    if (typeof window === 'undefined' || !window.speechSynthesis) return;
    try {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = lang === 'hi' ? 'hi-IN' : 'en-US';
      utterance.rate = 1.0;
      utterance.onstart = () => setIsSpeaking(true);
      utterance.onend = () => setIsSpeaking(false);
      utterance.onerror = () => setIsSpeaking(false);
      window.speechSynthesis.speak(utterance);
    } catch {
      setIsSpeaking(false);
    }
  };

  const stopSpeaking = () => {
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 760, margin: '0 auto', width: '100%', padding: '16px 14px 40px' }}>
      {/* Header */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
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
          <div>
            <h1 style={{ margin: 0, fontSize: '1.45rem', fontWeight: 800, letterSpacing: '0.02em', color: 'var(--text)' }}>
              BlindSight <span style={{ fontSize: '0.85rem', color: 'var(--accent)', fontWeight: 600, verticalAlign: 'middle', background: 'rgba(245,158,11,0.15)', padding: '2px 8px', borderRadius: 12 }}>v1.5</span>
            </h1>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#34d399', background: 'rgba(52,211,153,0.12)', border: '1px solid rgba(52,211,153,0.3)', padding: '4px 10px', borderRadius: 20, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#34d399', display: 'inline-block' }}></span>
            Groq 120B AI Active
          </span>
        </div>
      </header>

      {/* Main Download Hero Card */}
      <div style={{
        background: 'linear-gradient(145deg, #0f172a 0%, #0b1120 100%)',
        border: '2px solid rgba(245,158,11,0.3)',
        borderRadius: 20,
        padding: '28px 24px',
        boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden'
      }}>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <div style={{ width: 64, height: 64, borderRadius: '50%', background: 'rgba(245,158,11,0.18)', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid var(--accent)' }}>
            <Smartphone size={32} style={{ color: 'var(--accent-strong)' }} />
          </div>
        </div>

        <div>
          <h2 style={{ margin: '0 0 8px 0', fontSize: '1.8rem', fontWeight: 800, color: 'var(--text)' }}>
            BlindSight for Android
          </h2>
          <p style={{ margin: 0, color: 'var(--muted)', fontSize: '1.05rem', lineHeight: 1.5, maxWidth: 580, marginInline: 'auto' }}>
            Empowering independent mobility with <strong>120-Billion Parameter Frontier AI</strong>, sub-second <strong>Groq Whisper voice guidance</strong>, and ultrasonic obstacle radar.
          </p>
        </div>

        {/* Feature Pills */}
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: 8 }}>
          <span style={{ background: '#1e293b', color: 'var(--text)', fontSize: '0.82rem', fontWeight: 600, padding: '5px 12px', borderRadius: 16 }}>
            ⚡ Groq 120B AI Brain
          </span>
          <span style={{ background: '#1e293b', color: 'var(--text)', fontSize: '0.82rem', fontWeight: 600, padding: '5px 12px', borderRadius: 16 }}>
            🎙️ Whisper Large v3 Turbo
          </span>
          <span style={{ background: '#1e293b', color: 'var(--text)', fontSize: '0.82rem', fontWeight: 600, padding: '5px 12px', borderRadius: 16 }}>
            🔇 Zero Mic Flapping
          </span>
          <span style={{ background: '#1e293b', color: 'var(--text)', fontSize: '0.82rem', fontWeight: 600, padding: '5px 12px', borderRadius: 16 }}>
            🛡️ Ultrasonic Obstacle Radar
          </span>
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', flexDirection: 'column', smDirection: 'row', gap: 12, justifyContent: 'center', alignItems: 'center', marginTop: 6 } as any}>
          <a
            href="/BlindSight-testing.apk"
            download="BlindSight-testing.apk"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 12,
              background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
              color: '#000000',
              fontWeight: 800,
              fontSize: '1.15rem',
              padding: '16px 32px',
              borderRadius: 14,
              textDecoration: 'none',
              boxShadow: '0 6px 20px rgba(245,158,11,0.4)',
              cursor: 'pointer',
              width: '100%',
              maxWidth: 380,
              border: 'none',
              transition: 'transform 0.15s ease'
            }}
          >
            <Download size={24} />
            <span>Download BlindSight APK (v1.5)</span>
          </a>

          <button
            type="button"
            onClick={() => setIsQrModalOpen(true)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              background: 'transparent',
              color: 'var(--accent)',
              border: '1px solid rgba(245,158,11,0.4)',
              fontWeight: 700,
              fontSize: '0.95rem',
              padding: '12px 20px',
              borderRadius: 14,
              cursor: 'pointer'
            }}
          >
            <QrCode size={18} />
            <span>Scan QR Code on Phone</span>
          </button>
        </div>

        {/* Build Specs */}
        <div style={{ color: 'var(--muted)', fontSize: '0.82rem', marginTop: 4 }}>
          Version 1.5 (Build 5) • Size: 22.4 MB • Android 7.0+ (Nougat through 15) • Free
        </div>
      </div>

      {/* Live In-Browser AI Test Drive Card */}
      <div style={{
        background: 'var(--card)',
        border: '1px solid var(--card-edge)',
        borderRadius: 18,
        padding: '22px 20px',
        display: 'flex',
        flexDirection: 'column',
        gap: 16
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Cpu size={22} style={{ color: 'var(--accent)' }} />
            <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700 }}>
              Test Live Spoken AI Brain (Browser Demo)
            </h3>
          </div>
          {latencySec !== null && (
            <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#34d399', background: 'rgba(52,211,153,0.15)', padding: '2px 8px', borderRadius: 10 }}>
              ⚡ {latencySec}s via Groq 120B
            </span>
          )}
        </div>

        <p style={{ margin: 0, color: 'var(--muted)', fontSize: '0.92rem' }}>
          Ask any question below to test the active Groq 120B model live in your browser:
        </p>

        {/* Suggestion Chips */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {[
            'Who are you and what do you do?',
            'Tell me a funny joke about computers',
            'How can a blind person safely cross a street?',
            'नमस्ते आप मेरी क्या मदद कर सकते हैं?'
          ].map((chipText, i) => (
            <button
              key={i}
              type="button"
              onClick={() => {
                setPrompt(chipText);
                handleAskAi(chipText);
              }}
              style={{
                background: '#1e293b',
                color: 'var(--text)',
                border: '1px solid rgba(255,255,255,0.08)',
                padding: '6px 12px',
                borderRadius: 16,
                fontSize: '0.82rem',
                cursor: 'pointer',
                textAlign: 'left'
              }}
            >
              {chipText}
            </button>
          ))}
        </div>

        {/* Input Box */}
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            type="text"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleAskAi()}
            placeholder="Ask BlindSight anything (English or Hindi)…"
            style={{
              flex: 1,
              background: '#0b1220',
              border: '1px solid var(--card-edge)',
              borderRadius: 12,
              padding: '12px 16px',
              color: 'var(--text)',
              fontSize: '0.95rem'
            }}
          />
          <button
            type="button"
            onClick={() => handleAskAi()}
            disabled={isLoading || !prompt.trim()}
            style={{
              background: 'var(--accent)',
              color: '#000000',
              border: 'none',
              borderRadius: 12,
              padding: '0 20px',
              fontWeight: 700,
              cursor: isLoading ? 'wait' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              opacity: isLoading || !prompt.trim() ? 0.6 : 1
            }}
          >
            {isLoading ? <RefreshCw size={18} className="animate-spin" /> : <Send size={18} />}
            <span>Ask</span>
          </button>
        </div>

        {/* Response Box */}
        {aiResponse && (
          <div style={{
            background: '#0b1220',
            border: '1px solid rgba(245,158,11,0.25)',
            borderRadius: 14,
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: 10
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--accent)' }}>
                BlindSight Spoken Response:
              </span>
              <button
                type="button"
                onClick={isSpeaking ? stopSpeaking : () => speakText(aiResponse, /[\u0900-\u097F]/.test(aiResponse) ? 'hi' : 'en')}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: isSpeaking ? 'var(--bad)' : 'var(--accent)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: '0.8rem',
                  fontWeight: 600
                }}
              >
                {isSpeaking ? <VolumeX size={16} /> : <Volume2 size={16} />}
                <span>{isSpeaking ? 'Stop Audio' : 'Speak Again'}</span>
              </button>
            </div>
            <p style={{ margin: 0, fontSize: '1rem', color: 'var(--text)', lineHeight: 1.5 }}>
              "{aiResponse}"
            </p>
          </div>
        )}
      </div>

      {/* 3-Step Installation Guide */}
      <div style={{
        background: 'var(--card)',
        border: '1px solid var(--card-edge)',
        borderRadius: 18,
        padding: '22px 20px',
        display: 'flex',
        flexDirection: 'column',
        gap: 14
      }}>
        <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
          <CheckCircle2 size={20} style={{ color: 'var(--ok)' }} />
          <span>How to Install on Your Android Phone</span>
        </h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <div style={{ background: '#0b1220', padding: '14px', borderRadius: 12 }}>
            <div style={{ fontWeight: 700, color: 'var(--accent)', marginBottom: 4 }}>Step 1: Download</div>
            <div style={{ fontSize: '0.88rem', color: 'var(--muted)' }}>
              Tap the <strong>Download APK</strong> button above to download <code>BlindSight-testing.apk</code>.
            </div>
          </div>
          <div style={{ background: '#0b1220', padding: '14px', borderRadius: 12 }}>
            <div style={{ fontWeight: 700, color: 'var(--accent)', marginBottom: 4 }}>Step 2: Open File</div>
            <div style={{ fontSize: '0.88rem', color: 'var(--muted)' }}>
              In your phone's notification or Downloads folder, tap the downloaded APK.
            </div>
          </div>
          <div style={{ background: '#0b1220', padding: '14px', borderRadius: 12 }}>
            <div style={{ fontWeight: 700, color: 'var(--accent)', marginBottom: 4 }}>Step 3: Tap Install</div>
            <div style={{ fontSize: '0.88rem', color: 'var(--muted)' }}>
              Tap <strong>Install / Update</strong>. If asked, toggle "Allow from this source". You're ready to go!
            </div>
          </div>
        </div>
      </div>

      {/* QR Code Modal */}
      <ApkQrModal isOpen={isQrModalOpen} onClose={() => setIsQrModalOpen(false)} />

      {/* Footer */}
      <footer style={{ textAlign: 'center', color: 'var(--muted)', fontSize: '0.82rem', padding: '16px 0' }}>
        BlindSight Assistive Technologies • Built by Lavyansh & Agastaya Verma • Powered by Groq 120B AI
      </footer>
    </div>
  );
};
