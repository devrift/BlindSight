import { LanguageCode, LanguageOption, TextSize, ListenMode, ChatMessage } from '../types';

export const LANGUAGES: Record<LanguageCode, LanguageOption> = {
  en: {
    code: 'en',
    label: 'English',
    espeakVoice: 'en',
    neuralVoice: 'en-IN-NeerjaNeural',
    speechLang: 'en-IN',
    confirmation: 'Language set to English.'
  },
  hi: {
    code: 'hi',
    label: 'Hindi',
    espeakVoice: 'hi',
    neuralVoice: 'hi-IN-SwaraNeural',
    speechLang: 'hi-IN',
    confirmation: 'भाषा हिंदी पर सेट हो गई।'
  },
  bn: {
    code: 'bn',
    label: 'Bengali',
    espeakVoice: 'bn',
    neuralVoice: 'bn-IN-TanishaaNeural',
    speechLang: 'bn-IN',
    confirmation: 'ভাষা বাংলায় সেট করা হয়েছে।'
  },
  ta: {
    code: 'ta',
    label: 'Tamil',
    espeakVoice: 'ta',
    neuralVoice: 'ta-IN-PallaviNeural',
    speechLang: 'ta-IN',
    confirmation: 'மொழி தமிழக்கு அமைக்கப்பட்டது.'
  },
  te: {
    code: 'te',
    label: 'Telugu',
    espeakVoice: 'te',
    neuralVoice: 'te-IN-ShrutiNeural',
    speechLang: 'te-IN',
    confirmation: 'భాష తెలుగుకు సెట్ చేయబడింది.'
  },
  es: {
    code: 'es',
    label: 'Spanish',
    espeakVoice: 'es',
    neuralVoice: 'es-ES-ElviraNeural',
    speechLang: 'es-ES',
    confirmation: 'Idioma cambiado a español.'
  },
  fr: {
    code: 'fr',
    label: 'French',
    espeakVoice: 'fr',
    neuralVoice: 'fr-FR-DeniseNeural',
    speechLang: 'fr-FR',
    confirmation: 'Langue réglée sur le français.'
  }
};

const MEMORY_KEY = 'blindsight_memory';
const HISTORY_KEY = 'blindsight_history';
const PREFS_KEY = 'blindsight_prefs';

const MAX_FACTS = 20;
const MAX_FACT_CHARS = 200;
const MAX_HISTORY = 24;

export const HELP_TEXT =
  'Say hey BlindSight, then ask your question. Or switch to hands-free mode and ' +
  'just talk. Commands: describe, for a scene description. what do you remember. ' +
  'remember, followed by a fact. forget. language, followed by a language, like ' +
  'hindi. read status. clear chat. stop. help.';

export class CompanionStateService {
  // --- Memory (Facts) ---
  static getFacts(): string[] {
    try {
      const data = localStorage.getItem(MEMORY_KEY);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  static addFact(rawFact: string): { success: boolean; message: string; fact?: string } {
    const cleaned = rawFact.trim().slice(0, MAX_FACT_CHARS);
    if (!cleaned) {
      return {
        success: false,
        message: 'Say remember followed by what I should keep, for example: remember that my dog is Bruno.'
      };
    }
    const current = this.getFacts().filter(f => f.toLowerCase() !== cleaned.toLowerCase());
    current.push(cleaned);
    if (current.length > MAX_FACTS) {
      current.shift();
    }
    try {
      localStorage.setItem(MEMORY_KEY, JSON.stringify(current));
    } catch {
      // storage quota or failure
    }
    return { success: true, message: `Remembered: ${cleaned}`, fact: cleaned };
  }

  static forgetFact(query: string): { success: boolean; message: string; fact?: string } {
    const q = query.trim().toLowerCase();
    const current = this.getFacts();
    const index = current.findIndex(f => f.toLowerCase().includes(q));
    if (index === -1) {
      return { success: false, message: 'I could not find that in my memory.' };
    }
    const removed = current.splice(index, 1)[0];
    try {
      localStorage.setItem(MEMORY_KEY, JSON.stringify(current));
    } catch {}
    return { success: true, message: `Forgotten: ${removed}`, fact: removed };
  }

  static clearFacts(): { count: number; message: string } {
    const count = this.getFacts().length;
    try {
      localStorage.setItem(MEMORY_KEY, JSON.stringify([]));
    } catch {}
    return {
      count,
      message: `I forgot the ${count} thing${count !== 1 ? 's' : ''} you taught me.`
    };
  }

  // --- Chat History ---
  static getHistory(): ChatMessage[] {
    try {
      const data = localStorage.getItem(HISTORY_KEY);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  static addHistoryMessage(role: 'user' | 'assistant', content: string, degraded = false): ChatMessage {
    const current = this.getHistory();
    const msg: ChatMessage = {
      id: `${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      role,
      content,
      degraded,
      timestamp: Date.now()
    };
    current.push(msg);
    if (current.length > MAX_HISTORY) {
      current.splice(0, current.length - MAX_HISTORY);
    }
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(current));
    } catch {}
    return msg;
  }

  static clearHistory(): string {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify([]));
    } catch {}
    return 'Started a fresh conversation. I still keep the facts you taught me.';
  }

  // --- Local Commands Processor ---
  static processLocalCommand(prompt: string, currentLang: LanguageCode): {
    isLocal: boolean;
    reply: string;
    newLanguage?: LanguageCode;
  } | null {
    const low = prompt.trim().toLowerCase();

    // Remember
    if (low.startsWith('remember ')) {
      const factText = prompt.trim().slice('remember '.length);
      const res = this.addFact(factText);
      return { isLocal: true, reply: res.message };
    }

    // Forget All
    if (low === 'forget everything' || low === 'forget all' || low === 'forget everything.') {
      const res = this.clearFacts();
      return { isLocal: true, reply: res.message };
    }

    // Forget single
    if (low.startsWith('forget ')) {
      const query = prompt.trim().slice('forget '.length);
      const res = this.forgetFact(query);
      return { isLocal: true, reply: res.message };
    }

    // Recall Memory
    const recallTriggers = ['what do you remember', 'list memories', 'your memory', 'what do you know'];
    if (recallTriggers.some(t => low.replace(/[?!.]/g, '').trim() === t)) {
      const facts = this.getFacts();
      if (facts.length === 0) {
        return {
          isLocal: true,
          reply: "I don't remember anything yet. Say remember followed by a fact to teach me."
        };
      }
      return { isLocal: true, reply: `I remember: ${facts.join('; ')}.` };
    }

    // Clear Chat
    const clearTriggers = ['clear chat', 'start over', 'new chat', 'reset chat'];
    if (clearTriggers.some(t => low.replace(/[?!.]/g, '').trim() === t)) {
      const reply = this.clearHistory();
      return { isLocal: true, reply };
    }

    // Language switch
    let wantLang: string | null = null;
    if (low.startsWith('switch language to ')) {
      wantLang = low.slice('switch language to '.length).replace(/[.!?]/g, '').trim();
    } else if (low.startsWith('language ')) {
      wantLang = low.slice('language '.length).replace(/[.!?]/g, '').trim();
    }

    if (wantLang) {
      const entry = Object.entries(LANGUAGES).find(([code, opt]) =>
        code.toLowerCase() === wantLang?.toLowerCase() ||
        opt.label.toLowerCase() === wantLang?.toLowerCase()
      );
      if (entry) {
        const [code, opt] = entry as [LanguageCode, LanguageOption];
        return {
          isLocal: true,
          reply: opt.confirmation,
          newLanguage: code
        };
      } else {
        const labels = Object.values(LANGUAGES).map(l => l.label).join(', ');
        return {
          isLocal: true,
          reply: `I don't know that language yet. I can reply in: ${labels}.`
        };
      }
    }

    // Help
    const helpTriggers = ['help', 'what can i say', 'commands', 'instructions'];
    if (helpTriggers.includes(low.replace(/[?!.]/g, '').trim())) {
      return { isLocal: true, reply: HELP_TEXT };
    }

    return null;
  }
}
