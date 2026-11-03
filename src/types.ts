export type AppState = 'idle' | 'wake' | 'listen' | 'think' | 'speak';

export type ListenMode = 'wake' | 'handsfree' | 'button';

export type TextSize = 'md' | 'lg' | 'xl';

export type LanguageCode = 'en' | 'hi' | 'bn' | 'ta' | 'te' | 'es' | 'fr';

export interface LanguageOption {
  code: LanguageCode;
  label: string;
  espeakVoice: string;
  neuralVoice: string;
  speechLang: string;
  confirmation: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  degraded?: boolean;
  timestamp: number;
}

export interface SystemHealth {
  status: 'ok' | 'degraded' | 'error';
  version: string;
  visionReady: boolean;
  ttsReady: boolean;
  companionReady: boolean;
  issues: string[];
}

export type ReflexAlert = 'stop' | 'steer_left' | 'steer_right' | 'careful' | 'clear';

export interface ReflexTelemetry {
  leftCm: number;
  centerCm: number;
  rightCm: number;
  alert: ReflexAlert;
  message: string;
  audioClip?: string;
  timestamp: number;
}

export interface VoiceOption {
  voiceURI: string;
  name: string;
  lang: string;
  isNeural?: boolean;
}
