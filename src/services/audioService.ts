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

export const WAKE_PHRASES = ['hey blindsight', 'hey blind sight', 'ok blindsight', 'okay blindsight', 'blindsight'];
export const STOP_WORDS = ['stop', 'be quiet', 'quiet', 'cancel', 'shush'];
export const SCENE_WORDS = ['describe', 'describe the scene', 'what is around me', "what's around me", 'what is in front of me', 'look for me'];

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
        this.analyser.getByteTimeDomainData(this.levelBuf);
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
      this.micStream.getTracks().forEach(t => t.stop());
      this.micStream = null;
    }
    this.analyser = null;
    this.speechWindow = [];
  }

  static speechRecently(): boolean {
    return this.speechWindow.length > 0;
  }

  // --- Speech Synthesis (Speaking) ---
  static speak(
    text: string,
    langCode: LanguageCode,
    voiceURI?: string,
    onStart?: () => void,
    onEnd?: () => void
  ): void {
    this.stopSpeaking();
    if (typeof window === 'undefined' || !window.speechSynthesis) {
      if (onEnd) onEnd();
      return;
    }

    try {
      const utter = new SpeechSynthesisUtterance(text);
      const langConfig = LANGUAGES[langCode] || LANGUAGES.en;
      utter.lang = langConfig.speechLang;

      const voices = window.speechSynthesis.getVoices();
      if (voiceURI) {
        const found = voices.find(v => v.voiceURI === voiceURI);
        if (found) utter.voice = found;
      } else {
        // Choose best match for language
        const prefix = langConfig.speechLang.split('-')[0].toLowerCase();
        const matching = voices.filter(v => v.lang.toLowerCase().startsWith(prefix));
        const natural = matching.find(v => /natural|neural|online|google/i.test(v.name));
        utter.voice = natural || matching[0] || null;
      }

      utter.rate = 1.0;
      utter.pitch = 1.0;

      utter.onstart = () => {
        if (onStart) onStart();
      };
      utter.onend = () => {
        if (onEnd) onEnd();
      };
      utter.onerror = () => {
        if (onEnd) onEnd();
      };

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
