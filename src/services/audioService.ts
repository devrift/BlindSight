import { LanguageCode, VoiceOption } from '../types';
import { LANGUAGES } from './companionState';

// SpeechRecognition interfaces for browser compatibility
interface IWindow extends Window {
  SpeechRecognition?: any;
  webkitSpeechRecognition?: any;
  AudioContext?: any;
  webkitAudioContext?: any;
}

const win = typeof window !== 'undefined' ? (window as unknown as IWindow) : null;
const SpeechRecognitionClass = win ? (win.SpeechRecognition || win.webkitSpeechRecognition) : null;
const AudioContextClass = win ? (win.AudioContext || win.webkitAudioContext) : null;

export const WAKE_PHRASES = [
  'hey blindsight',
  'hey blind sight',
  'hey blind site',
  'hey blindside',
  'hey blind side',
  'ok blindsight',
  'okay blindsight',
  'ok blind sight',
  'okay blind sight',
  'ok blind side',
  'okay blind side',
  'blindsight',
  'blind sight',
  'blind site',
  'blindside',
  'blind side',
  'hi blindsight',
  'hi blind sight',
  'hello blindsight',
  'hello blind sight',
  'yo blindsight',
  'jarvis',
  'hey jarvis',
  'hi jarvis',
  'hello jarvis',
  'ok jarvis',
  'okay jarvis'
];

export const STOP_WORDS = ['stop', 'be quiet', 'quiet', 'cancel', 'shush'];
export const SCENE_WORDS = ['describe', 'describe the scene', 'what is around me', "what's around me", 'what is in front of me', 'look for me'];

export function cleanSpeech(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}

export class AudioService {
  private static audioCtx: AudioContext | null = null;
  private static analyser: AnalyserNode | null = null;
  private static micStream: MediaStream | null = null;
  private static micInterval: number | null = null;
  private static levelBuf: Uint8Array | null = null;
  private static noiseFloor = 0.02;
  private static micLevel = 0;
  private static speechWindow: number[] = [];
  private static calibrating = false;
  private static audioUnlocked = false;
  private static currentWavAudio: HTMLAudioElement | null = null;

  // Svara-TTS v1 OpenAI-compatible configuration
  private static svaraUrl = typeof window !== 'undefined'
    ? localStorage.getItem('blindsight_svara_url') || 'http://localhost:8000/v1/audio/speech'
    : 'http://localhost:8000/v1/audio/speech';
  private static svaraEnabled = typeof window !== 'undefined'
    ? localStorage.getItem('blindsight_use_svara') === 'on'
    : false;

  static isSvaraEnabled(): boolean {
    return this.svaraEnabled;
  }

  static setSvaraEnabled(enabled: boolean): void {
    this.svaraEnabled = enabled;
    if (typeof window !== 'undefined') {
      localStorage.setItem('blindsight_use_svara', enabled ? 'on' : 'off');
    }
  }

  static getSvaraUrl(): string {
    return this.svaraUrl;
  }

  static setSvaraUrl(url: string): void {
    this.svaraUrl = url;
    if (typeof window !== 'undefined') {
      localStorage.setItem('blindsight_svara_url', url);
    }
  }

  static isSpeechRecognitionSupported(): boolean {
    return !!SpeechRecognitionClass;
  }

  static getAudioContext(): AudioContext | null {
    if (!AudioContextClass) return null;
    if (!this.audioCtx) {
      try {
        this.audioCtx = new AudioContextClass();
      } catch {
        return null;
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume().catch(() => {});
    }
    return this.audioCtx;
  }

  static unlockAudio(): void {
    if (this.audioUnlocked) return;
    this.audioUnlocked = true;
    const ctx = this.getAudioContext();
    if (ctx && ctx.state === 'suspended') {
      ctx.resume().catch(() => {});
    }
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      try {
        const warm = new SpeechSynthesisUtterance(' ');
        warm.volume = 0;
        window.speechSynthesis.speak(warm);
      } catch {}
    }
  }

  // --- Earcons Synthesizer ---
  static playEarcon(kind: 'wake' | 'listen' | 'think' | 'ok' | 'error' | 'tick'): void {
    try {
      const ctx = this.getAudioContext();
      if (!ctx) return;

      const sequences: Record<string, [number, number][]> = {
        wake: [[880, 0.09], [1320, 0.12]],
        listen: [[660, 0.1]],
        think: [[520, 0.08], [520, 0.08]],
        ok: [[740, 0.08], [980, 0.12]],
        error: [[330, 0.12], [220, 0.16]],
        tick: [[990, 0.04]]
      };

      const seq = sequences[kind] || [[990, 0.04]];
      let at = ctx.currentTime;

      for (const [freq, dur] of seq) {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.value = freq;
        osc.type = 'sine';
        gain.gain.setValueAtTime(0.0001, at);
        gain.gain.exponentialRampToValueAtTime(0.18, at + 0.015);
        gain.gain.exponentialRampToValueAtTime(0.0001, at + dur);
        osc.connect(gain).connect(ctx.destination);
        osc.start(at);
        osc.stop(at + dur + 0.02);
        at += dur + 0.03;
      }
    } catch {
      // Audio is a nicety, never a fatal dependency
    }
  }

  // --- Hardware Sound Clips ---
  static playAudioClip(url: string, onEnded?: () => void): void {
    this.stopSpeaking();
    try {
      const audio = new Audio(url);
      this.currentWavAudio = audio;
      audio.onended = () => {
        this.currentWavAudio = null;
        if (onEnded) onEnded();
      };
      audio.onerror = () => {
        this.currentWavAudio = null;
        if (onEnded) onEnded();
      };
      audio.play().catch(() => {
        if (onEnded) onEnded();
      });
    } catch {
      if (onEnded) onEnded();
    }
  }

  // --- Mic Energy & Noise Gate Analytics ---
  static async openMicAnalytics(onLevelChange?: (level: number, isAboveGate: boolean) => void): Promise<boolean> {
    if (this.analyser || typeof navigator === 'undefined' || !navigator.mediaDevices) return true;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { noiseSuppression: true, echoCancellation: true, autoGainControl: true }
      });
      const ctx = this.getAudioContext();
      if (!ctx) return false;
      if (ctx.state === 'suspended') await ctx.resume();

      const source = ctx.createMediaStreamSource(stream);
      this.analyser = ctx.createAnalyser();
      this.analyser.fftSize = 1024;
      source.connect(this.analyser);
      this.levelBuf = new Uint8Array(this.analyser.fftSize);
      this.calibrating = true;
      this.micStream = stream;

      this.micInterval = window.setInterval(() => {
        if (!this.analyser || !this.levelBuf) return;
        this.analyser.getByteTimeDomainData(this.levelBuf as any);
        let sum = 0;
        for (let i = 0; i < this.levelBuf.length; i++) {
          const v = (this.levelBuf[i] - 128) / 128;
          sum += v * v;
        }
        const rms = Math.sqrt(sum / this.levelBuf.length);
        this.micLevel = rms;

        if (this.calibrating) {
          this.noiseFloor = Math.max(this.noiseFloor * 0.9 + rms * 0.1, 0.004);
        } else if (rms > this.noiseFloor * 2.5 + 0.012) {
          this.speechWindow.push(Date.now());
        }
        while (this.speechWindow.length && Date.now() - this.speechWindow[0] > 1200) {
          this.speechWindow.shift();
        }

        const gate = this.noiseFloor * 2.5 + 0.012;
        const isHot = rms > gate;
        const pct = Math.min(100, (rms / Math.max(gate * 3, 0.06)) * 100);
        if (onLevelChange) {
          onLevelChange(pct, isHot);
        }
      }, 100);

      window.setTimeout(() => {
        this.calibrating = false;
      }, 1500);
      return true;
    } catch {
      this.analyser = null;
      return false;
    }
  }

  static stopMicAnalytics(): void {
    if (this.micInterval) {
      window.clearInterval(this.micInterval);
      this.micInterval = null;
    }
    if (this.micStream) {
      try {
        this.micStream.getTracks().forEach(t => t.stop());
      } catch {}
      this.micStream = null;
    }
    this.analyser = null;
    this.speechWindow = [];
  }

  static speechRecently(): boolean {
    return this.speechWindow.length > 0;
  }

  // --- Svara-TTS v1 Caller ---
  static async speakWithSvara(
    text: string,
    langCode: LanguageCode,
    onStart?: () => void,
    onEnd?: () => void
  ): Promise<boolean> {
    const svaraVoices: Record<LanguageCode, string> = {
      hi: 'Hindi (Female)',
      en: 'English (Female)',
      bn: 'Bengali (Female)',
      ta: 'Tamil (Female)',
      te: 'Telugu (Female)',
      es: 'English (Female)',
      fr: 'English (Female)'
    };
    const voice = svaraVoices[langCode] || 'Hindi (Female)';

    try {
      const ctrl = new AbortController();
      const timer = window.setTimeout(() => ctrl.abort(), 4000);

      const resp = await fetch(this.svaraUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: 'svara-tts-v1',
          voice: voice,
          input: text,
          response_format: 'mp3'
        }),
        signal: ctrl.signal
      });
      window.clearTimeout(timer);

      if (resp.ok) {
        const blob = await resp.blob();
        const blobUrl = URL.createObjectURL(blob);
        if (onStart) onStart();
        this.playAudioClip(blobUrl, () => {
          URL.revokeObjectURL(blobUrl);
          if (onEnd) onEnd();
        });
        return true;
      }
    } catch {
      // Svara-TTS server not reachable on port 8000
    }
    return false;
  }

  // --- Speech Synthesis Router ---
  static speak(
    text: string,
    langCode: LanguageCode,
    voiceURI?: string,
    onStart?: () => void,
    onEnd?: () => void
  ): void {
    this.stopSpeaking();

    // 1. If Svara-TTS is enabled, attempt calling it
    if (this.svaraEnabled) {
      this.speakWithSvara(text, langCode, onStart, onEnd).then(success => {
        if (!success) {
          // If Svara-TTS endpoint is not running, seamlessly fallback to natural voice
          this.speakNative(text, langCode, voiceURI, onStart, onEnd);
        }
      });
      return;
    }

    // 2. Default browser neural voice
    this.speakNative(text, langCode, voiceURI, onStart, onEnd);
  }

  private static speakNative(
    text: string,
    langCode: LanguageCode,
    voiceURI?: string,
    onStart?: () => void,
    onEnd?: () => void
  ): void {
    if (typeof window === 'undefined' || !window.speechSynthesis) {
      if (onEnd) onEnd();
      return;
    }

    try {
      if (window.speechSynthesis.paused) {
        try { window.speechSynthesis.resume(); } catch {}
      }

      const utter = new SpeechSynthesisUtterance(text);
      const langConfig = LANGUAGES[langCode] || LANGUAGES.en;
      utter.lang = langConfig.speechLang;

      const voices = window.speechSynthesis.getVoices();
      if (voiceURI) {
        const found = voices.find(v => v.voiceURI === voiceURI);
        if (found) utter.voice = found;
      } else {
        const prefix = langConfig.speechLang.split('-')[0].toLowerCase();
        const matching = voices.filter(v => v.lang.toLowerCase().startsWith(prefix));
        const natural = matching.find(v => /natural|neural|online|google/i.test(v.name));
        utter.voice = natural || matching[0] || null;
      }

      utter.rate = 1.0;
      utter.pitch = 1.0;

      let finished = false;
      const finishOnce = () => {
        if (finished) return;
        finished = true;
        if (onEnd) onEnd();
      };

      utter.onstart = () => {
        if (onStart) onStart();
      };
      utter.onend = () => finishOnce();
      utter.onerror = () => finishOnce();

      const maxDuration = Math.max(3500, text.length * 80);
      window.setTimeout(() => {
        if (!finished) {
          finishOnce();
        }
      }, maxDuration);

      window.speechSynthesis.speak(utter);
    } catch {
      if (onEnd) onEnd();
    }
  }

  static stopSpeaking(): void {
    if (this.currentWavAudio) {
      try {
        this.currentWavAudio.pause();
        this.currentWavAudio.currentTime = 0;
      } catch {}
      this.currentWavAudio = null;
    }
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      try {
        window.speechSynthesis.cancel();
      } catch {}
    }
  }

  // --- Direct PCM WAV Recorder (Zero Browser Cloud Dependencies) ---
  private static wavStream: MediaStream | null = null;
  private static scriptProcessor: ScriptProcessorNode | null = null;
  private static pcmSamples: Float32Array[] = [];

  static async recordWav(durationMs = 3800, onLevel?: (lvl: number) => void): Promise<Blob | null> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices) return null;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      this.wavStream = stream;

      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      const ctx = new AudioContextClass({ sampleRate: 16000 });
      const source = ctx.createMediaStreamSource(stream);
      const processor = ctx.createScriptProcessor(4096, 1, 1);
      
      this.pcmSamples = [];
      processor.onaudioprocess = (e) => {
        const input = e.inputBuffer.getChannelData(0);
        this.pcmSamples.push(new Float32Array(input));
        if (onLevel) {
          let sum = 0;
          for (let i = 0; i < input.length; i++) sum += Math.abs(input[i]);
          onLevel(Math.min(1.0, (sum / input.length) * 5));
        }
      };

      source.connect(processor);
      processor.connect(ctx.destination);
      this.scriptProcessor = processor;

      await new Promise(r => setTimeout(r, durationMs));

      processor.disconnect();
      source.disconnect();
      stream.getTracks().forEach(t => t.stop());
      ctx.close().catch(() => {});
      this.wavStream = null;
      this.scriptProcessor = null;

      let totalLen = 0;
      for (const chunk of this.pcmSamples) totalLen += chunk.length;
      const merged = new Float32Array(totalLen);
      let offset = 0;
      for (const chunk of this.pcmSamples) {
        merged.set(chunk, offset);
        offset += chunk.length;
      }

      return this.encodeWAV(merged, 16000);
    } catch (e) {
      console.warn('WAV recording error:', e);
      return null;
    }
  }

  private static encodeWAV(samples: Float32Array, sampleRate: number): Blob {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);

    const writeString = (offset: number, str: string) => {
      for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
    };

    writeString(0, 'RIFF');
    view.setUint32(4, 36 + samples.length * 2, true);
    writeString(8, 'WAVE');
    writeString(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeString(36, 'data');
    view.setUint32(40, samples.length * 2, true);

    let offset = 44;
    for (let i = 0; i < samples.length; i++, offset += 2) {
      const s = Math.max(-1, Math.min(1, samples[i]));
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
    }

    return new Blob([buffer], { type: 'audio/wav' });
  }

  // --- Open.Jarvis Round-trip Voice Engine (/api/voice) ---
  static async sendVoiceToJarvis(
    audioBlob: Blob,
    lang = 'en',
    onStart?: () => void,
    onEnd?: () => void
  ): Promise<{ transcript: string; reply: string; wakeDetected: boolean; success: boolean }> {
    try {
      const ctrl = new AbortController();
      const timer = window.setTimeout(() => ctrl.abort(), 20000);
      const resp = await fetch(`/api/voice?lang=${encodeURIComponent(lang)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'audio/wav' },
        body: audioBlob,
        signal: ctrl.signal
      });
      window.clearTimeout(timer);

      if (resp.ok) {
        const transcript = decodeURIComponent(resp.headers.get('X-Transcript') || '');
        const reply = decodeURIComponent(resp.headers.get('X-Reply') || '');
        const wakeDetected = resp.headers.get('X-Wake-Detected') === '1';

        const audioData = await resp.blob();
        if (audioData.size > 100) {
          const audioUrl = URL.createObjectURL(audioData);
          if (onStart) onStart();
          this.playAudioClip(audioUrl, () => {
            URL.revokeObjectURL(audioUrl);
            if (onEnd) onEnd();
          });
        } else if (onEnd) {
          onEnd();
        }

        return { transcript, reply, wakeDetected, success: true };
      }
    } catch (e) {
      console.warn('sendVoiceToJarvis error:', e);
    }
    return { transcript: '', reply: '', wakeDetected: false, success: false };
  }

  // --- Hardware PC / Pi Microphone Trigger (/api/listen) ---
  static async triggerHardwareMic(
    lang = 'en',
    onStart?: () => void,
    onEnd?: () => void
  ): Promise<{ transcript: string; reply: string; success: boolean }> {
    try {
      const resp = await fetch(`/api/listen?lang=${encodeURIComponent(lang)}`, { method: 'POST' });
      if (resp.ok) {
        const data = await resp.json();
        if (data.ok) {
          if (data.tts_url) {
            if (onStart) onStart();
            this.playAudioClip(data.tts_url, onEnd);
          } else if (onEnd) {
            onEnd();
          }
          return { transcript: data.transcript, reply: data.reply, success: true };
        }
      }
    } catch (e) {
      console.warn('triggerHardwareMic error:', e);
    }
    return { transcript: '', reply: '', success: false };
  }

  // --- Round-trip Gateway Audio (/api/voice) ---
  static async sendVoiceToGateway(
    audioBlob: Blob,
    onStart?: () => void,
    onEnd?: () => void
  ): Promise<boolean> {
    try {
      const ctrl = new AbortController();
      const timer = window.setTimeout(() => ctrl.abort(), 20000);
      const resp = await fetch('/api/voice', {
        method: 'POST',
        headers: { 'Content-Type': 'audio/wav' },
        body: audioBlob,
        signal: ctrl.signal
      });
      window.clearTimeout(timer);

      if (resp.ok) {
        const wavBlob = await resp.blob();
        if (wavBlob.size > 100) {
          const audioUrl = URL.createObjectURL(wavBlob);
          if (onStart) onStart();
          this.playAudioClip(audioUrl, () => {
            URL.revokeObjectURL(audioUrl);
            if (onEnd) onEnd();
          });
          return true;
        }
      }
    } catch {
      // Gateway not available or timed out
    }
    return false;
  }

  static getAvailableVoices(): VoiceOption[] {
    if (typeof window === 'undefined' || !window.speechSynthesis) return [];
    const voices = window.speechSynthesis.getVoices();
    return voices.map(v => ({
      voiceURI: v.voiceURI,
      name: v.name,
      lang: v.lang,
      isNeural: /natural|neural|online|google/i.test(v.name)
    }));
  }
}
