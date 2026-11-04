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
    confirmation: 'भाषा বাংলায় সেট করা হয়েছে।'
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
    const cleaned = low.replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();

    // 1. Greetings & Casual Interaction
    const greetings = [
      'hey', 'hi', 'hello', 'hey there', 'hello there', 'hi there',
      'good morning', 'good afternoon', 'good evening', 'namaste', 'namaskar'
    ];
    if (greetings.includes(cleaned)) {
      const reply = currentLang === 'hi'
        ? 'नमस्ते! मैं ब्लाइंडसाइट हूँ, आपकी दृष्टि सहायक। मैं सुन रही हूँ। आज मैं आपकी क्या मदद कर सकती हूँ?'
        : "Hello! I'm BlindSight, your visual assistant. I am listening. How can I help you today?";
      return { isLocal: true, reply };
    }

    // 2. Direct Wake Phrases addressed as commands (e.g. tapped button and said "hey blindsight")
    const directWakeCalls = [
      'hey blindsight', 'blindsight', 'blind sight', 'blind site', 'blindside', 'blind side',
      'ok blindsight', 'okay blindsight', 'ok blind sight', 'hi blindsight', 'hello blindsight'
    ];
    if (directWakeCalls.includes(cleaned)) {
      const reply = currentLang === 'hi'
        ? 'जी, मैं सुन रही हूँ! बताइए क्या सहायता चाहिए?'
        : "I'm right here and listening! What would you like me to do?";
      return { isLocal: true, reply };
    }

    // 3. Identity
    const identityTriggers = [
      'who are you', 'what are you', 'what is blindsight', 'who is blindsight',
      'introduce yourself', 'tell me about yourself'
    ];
    if (identityTriggers.includes(cleaned)) {
      const reply = currentLang === 'hi'
        ? 'मैं ब्लाइंडसाइट हूँ, दृष्टिबाधित लोगों के लिए एक सहायक साथी। मैं वस्तुओं की पहचान, बाधा निवारण और दैनिक नेविगेशन में आपकी सहायता करती हूँ।'
        : 'I am BlindSight, your assistive smart companion. I help you navigate obstacles, recognize objects, remember important details, and describe your surroundings.';
      return { isLocal: true, reply };
    }

    // 4. Health / Readiness check
    const healthTriggers = [
      'how are you', 'how are you doing', 'are you there', 'are you listening',
      'can you hear me'
    ];
    if (healthTriggers.includes(cleaned)) {
      const reply = currentLang === 'hi'
        ? 'मैं बिल्कुल ठीक हूँ और आपकी बात सुन रही हूँ! आप क्या जानना चाहते हैं?'
        : 'I am active, healthy, and listening clearly! What can I help you with?';
      return { isLocal: true, reply };
    }

    // 5. Creators / Attribution
    const creatorTriggers = [
      'who made you', 'who created you', 'who built you', 'who designed you', 'who is your creator'
    ];
    if (creatorTriggers.includes(cleaned)) {
      return {
        isLocal: true,
        reply: 'I was designed and developed for BlindSight by Lavyansh and Agastaya Verma to empower visually impaired navigation.'
      };
    }

    // 6. Time & Date (Useful offline utility for blind users)
    const timeTriggers = ['what time is it', 'what is the time', 'tell me the time', 'time now', 'time'];
    if (timeTriggers.includes(cleaned)) {
      const timeStr = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
      return { isLocal: true, reply: `The current time is ${timeStr}.` };
    }

    const dateTriggers = ["what is today's date", 'what date is today', 'what is the date', "today's date", 'what day is it', 'what day is today'];
    if (dateTriggers.includes(cleaned)) {
      const dateStr = new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
      return { isLocal: true, reply: `Today is ${dateStr}.` };
    }

    // 7. Capabilities
    const capabilityTriggers = [
      'what can you do', 'what are your features', 'how do i use you', 'how does this work'
    ];
    if (capabilityTriggers.includes(cleaned)) {
      return {
        isLocal: true,
        reply: 'I can remember facts, check system status, change languages, stop speaking, and describe scenes using computer vision when connected. Say help to hear the full list of voice commands.'
      };
    }

    // 8. Testing / Mic check
    const testTriggers = [
      'test', 'testing', 'mic check', 'check mic', 'test 1 2 3', 'testing 1 2 3'
    ];
    if (testTriggers.includes(cleaned)) {
      return {
        isLocal: true,
        reply: 'Loud and clear! Speech recognition and audio synthesis are both working perfectly.'
      };
    }

    // 9. Gratitude
    const thanksTriggers = [
      'thank you', 'thanks', 'thank you so much', 'thanks a lot'
    ];
    if (thanksTriggers.includes(cleaned)) {
      const reply = currentLang === 'hi'
        ? 'आपका स्वागत है! मैं हमेशा आपकी मदद के लिए उपस्थित हूँ।'
        : "You are very welcome! I'm always here whenever you need me.";
      return { isLocal: true, reply };
    }

    // 10. Remember
    if (low.startsWith('remember ')) {
      const factText = prompt.trim().slice('remember '.length);
      const res = this.addFact(factText);
      return { isLocal: true, reply: res.message };
    }

    // 11. Forget All
    if (low === 'forget everything' || low === 'forget all' || low === 'forget everything.') {
      const res = this.clearFacts();
      return { isLocal: true, reply: res.message };
    }

    // 12. Forget single
    if (low.startsWith('forget ')) {
      const query = prompt.trim().slice('forget '.length);
      const res = this.forgetFact(query);
      return { isLocal: true, reply: res.message };
    }

    // 13. Recall Memory
    const recallTriggers = ['what do you remember', 'list memories', 'your memory', 'what do you know', 'memories'];
    if (recallTriggers.some(t => cleaned === t)) {
      const facts = this.getFacts();
      if (facts.length === 0) {
        return {
          isLocal: true,
          reply: "I don't remember anything yet. Say remember followed by a fact to teach me."
        };
      }
      return { isLocal: true, reply: `I remember: ${facts.join('; ')}.` };
    }

    // 14. Clear Chat
    const clearTriggers = ['clear chat', 'start over', 'new chat', 'reset chat'];
    if (clearTriggers.some(t => cleaned === t)) {
      const reply = this.clearHistory();
      return { isLocal: true, reply };
    }

    // 15. Language switch
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

    // 16. Help
    const helpTriggers = ['help', 'what can i say', 'commands', 'instructions'];
    if (helpTriggers.includes(cleaned)) {
      return { isLocal: true, reply: HELP_TEXT };
    }

    return null;
  }
}
