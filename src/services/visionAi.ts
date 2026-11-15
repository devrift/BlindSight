import { CompanionStateService, LANGUAGES } from './companionState';
import { LanguageCode } from '../types';
import { AssistantBrain } from './assistantBrain';

export interface VisionResult {
  reply: string;
  ok: boolean;
  reason?: string;
  imageAttached?: boolean;
}

export class VisionAiService {
  private static lastCapturedFrame: string | null = null;

  static setLatestFrame(dataUrl: string): void {
    this.lastCapturedFrame = dataUrl;
  }

  static getLatestFrame(): string | null {
    return this.lastCapturedFrame;
  }

  static getGeminiApiKey(): string {
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('blindsight_gemini_api_key');
      if (stored && stored.trim()) return stored.trim();
    }
    const envKey = (import.meta as any).env?.VITE_GEMINI_API_KEY || (import.meta as any).env?.GEMINI_API_KEY;
    return (envKey && String(envKey).trim()) || '';
  }

  static setGeminiApiKey(key: string): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem('blindsight_gemini_api_key', key.trim());
    }
  }

  static getGroqApiKey(): string {
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('blindsight_groq_api_key');
      if (stored && stored.trim()) return stored.trim();
    }
    const envKey = (import.meta as any).env?.VITE_GROQ_API_KEY || (import.meta as any).env?.GROQ_API_KEY;
    return (envKey && String(envKey).trim()) || '';
  }

  static setGroqApiKey(key: string): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem('blindsight_groq_api_key', key.trim());
    }
  }

  /**
   * Directly queries Groq ultra-fast 120B parameter model
   */
  static async queryGroqDirect(prompt: string, langCode: LanguageCode): Promise<string | null> {
    const key = this.getGroqApiKey();
    if (!key) return null;

    const sysPrompt = langCode === 'hi'
      ? "आप ब्लाइंडसाइट हैं — दृष्टिबाधित उपयोगकर्ताओं के लिए एक बुद्धिमान, चौकस और अत्यंत आत्मीय वॉयस साथी। हर उत्तर स्वाभाविक, सम्मानजनक और आत्मीय हिंदी में 1-2 संक्षिप्त वाक्यों में दें। आपका नाम ब्लाइंडसाइट है।"
      : "You are BlindSight — the dedicated AI visual guide and caring voice companion walking beside a blind user. Speak with warmth, alert intelligence, empathy, and clarity in 1 to 2 crisp, spoken sentences. Chat naturally, answer questions, solve tasks, or tell jokes. Your name is BlindSight.";

    const models = ['openai/gpt-oss-120b', 'qwen/qwen3.8-27b', 'openai/gpt-oss-20b'];
    for (const model of models) {
      try {
        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 6000);
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${key}`
          },
          body: JSON.stringify({
            model,
            messages: [
              { role: 'system', content: sysPrompt },
              { role: 'user', content: prompt }
            ],
            temperature: 0.7,
            max_tokens: 600
          }),
          signal: ctrl.signal
        });
        clearTimeout(timeout);
        if (res.ok) {
          const data = await res.json();
          const content = data?.choices?.[0]?.message?.content?.trim();
          if (content) return content;
        }
      } catch (e) {
        console.warn('Groq direct query attempt notice:', e);
      }
    }
    return null;
  }

  /**
   * Captures a single image frame from an active video element
   */
  static captureFrameFromVideo(videoEl: HTMLVideoElement): string | null {
    try {
      if (!videoEl.videoWidth || !videoEl.videoHeight) return null;
      const canvas = document.createElement('canvas');
      canvas.width = videoEl.videoWidth;
      canvas.height = videoEl.videoHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) return null;
      ctx.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
      this.lastCapturedFrame = dataUrl;
      return dataUrl;
    } catch {
      return null;
    }
  }

  /**
   * Directly queries Google Gemini Multimodal API (gemini-flash-lite-latest)
   */
  static async queryGeminiDirect(
    prompt: string,
    imageDataUrl: string | null,
    langCode: LanguageCode,
    isVisionQuery: boolean = false
  ): Promise<string | null> {
    const key = this.getGeminiApiKey();
    if (!key) return null;

    try {
      const parts: any[] = [];
      let sysInstruction: string;

      if (isVisionQuery) {
        sysInstruction = langCode === 'hi'
          ? "आप ब्लाइंडसाइट हैं, दृष्टिबाधित उपयोगकर्ताओं के लिए एक सहायक नेविगेशन साथी। कैमरे में जो वास्तव में दिख रहा है, केवल वही 1-2 सीधे वाक्यों में बताएं। यदि सामने सादी दीवार, लकड़ी का बोर्ड या कोई सतह है, तो सीधे कहें कि सामने दीवार या सतह है और रास्ता रुका हुआ है। '10 बजे की दिशा' या '2 बजे की दिशा' जैसे शब्द कभी न बोलें, सामान्य शब्द बोलें जैसे 'सीधे आपके आगे', 'बाईं तरफ', 'दाईं तरफ'। कभी भी झूठी या बिना दिखी चीजें न बनाएं।"
          : "You are BlindSight, an assistive navigation companion for a blind or visually impaired user. Describe ONLY what is genuinely visible in the camera view in 1 to 2 direct sentences. If facing a plain wall, wooden surface, or obstacle, state directly that a wall or surface is in front of the user and the path ahead is blocked. Never invent unviewed objects. Use natural directions: 'Straight ahead', 'To your left', 'To your right'. Never use clock numbers like '10 o'clock'.";
      } else {
        sysInstruction = langCode === 'hi'
          ? "आप ब्लाइंडसाइट हैं, एक बुद्धिमान, दोस्ताना और मददगार वॉयस असिस्टेंट साथी (गूगल असिस्टेंट की तरह)। सामान्य बातचीत करें, सवालों के जवाब दें, चुटकुले सुनाएं, गणित हल करें। उत्तर 2-3 वाक्यों में स्वाभाविक और संक्षिप्त रखें। जब तक उपयोगकर्ता खुद न पूछे, कैमरे या बाधाओं का ज़िक्र न करें। यदि नाम पूछा जाए, तो कहें कि आपका नाम 'ब्लाइंडसाइट' है।"
          : "You are BlindSight, an intelligent, friendly, witty, and helpful AI conversational companion (like Google Assistant). Chat naturally, answer questions, tell jokes, solve math, and assist with any topic. Keep answers concise under 2-3 natural sentences. DO NOT talk about camera, obstacles, or navigation unless the user specifically asks about their surroundings. If asked for your name, state clearly that you are BlindSight.";
      }

      parts.push({ text: `${sysInstruction}\n\nUser Question: ${prompt}` });

      if (isVisionQuery && imageDataUrl) {
        const base64Data = imageDataUrl.includes(',') ? imageDataUrl.split(',')[1] : imageDataUrl;
        parts.push({
          inline_data: {
            mime_type: 'image/jpeg',
            data: base64Data
          }
        });
      }

      const candidateModels = [
        'gemini-3-flash-preview',
        'gemini-3.5-flash',
        'gemini-3.5-flash-lite',
        'gemini-3.1-flash-lite',
        'gemini-3.8-flash'
      ];

      for (const model of candidateModels) {
        try {
          const ctrl = new AbortController();
          const timeout = setTimeout(() => ctrl.abort(), 8000);
          const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ parts }],
              generationConfig: {
                temperature: 0.7,
                maxOutputTokens: 200,
                thinkingConfig: {
                  thinkingBudget: 0
                }
              }
            }),
            signal: ctrl.signal
          });
          clearTimeout(timeout);

          if (res.ok) {
            const data = await res.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
            if (text && text.trim()) {
              return text.trim();
            }
          }
        } catch {
          // Continue to next model fallback
        }
      }
    } catch (e) {
      console.warn('Gemini direct query notice:', e);
    }
    return null;
  }

  /**
   * Answers a multimodal or conversational prompt
   */
  static async askCompanion(
    prompt: string,
    options: {
      useCamera?: boolean;
      imageDataUrl?: string | null;
      langCode: LanguageCode;
      apiToken?: string;
    }
  ): Promise<VisionResult> {
    const { useCamera, imageDataUrl, langCode } = options;

    const low = prompt.trim().toLowerCase();
    const sceneTriggers = [
      'describe',
      'describe the scene',
      'describe scene',
      'what is around me',
      "what's around me",
      'what is in front of me',
      'whats in front of me',
      'what is ahead of me',
      'look for me',
      'look ahead',
      'look in front',
      'what do you see',
      'is the path clear',
      'can i walk',
      'read this label',
      'read this sign',
      'samne kya hai',
      'aage kya hai',
      'aage kya dikh raha hai',
      'scene describe karo',
      'aas paas kya hai',
      'samne dekho',
      'aage dekho'
    ];
    const isSceneQuery = sceneTriggers.some(trigger => low === trigger || low.startsWith(trigger + ' '));

    if (imageDataUrl) {
      this.lastCapturedFrame = imageDataUrl;
    }

    const isVisionRequest = isSceneQuery || !!imageDataUrl;
    const imageToUse = imageDataUrl || (isSceneQuery && useCamera ? this.lastCapturedFrame : null);

    // 1. If an external AI gateway / server endpoint exists on the LAN, try querying it
    try {
      if (typeof window !== 'undefined') {
        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 6000);
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(options.apiToken ? { Authorization: `Bearer ${options.apiToken}` } : {})
          },
          body: JSON.stringify({
            prompt: prompt || 'describe what is in front of me',
            image: imageToUse || undefined,
            use_camera: isVisionRequest,
            lang: langCode
          }),
          signal: ctrl.signal
        });
        clearTimeout(timeout);

        if (res.ok) {
          const data = await res.json();
          if (data && data.reply) {
            return {
              reply: data.reply,
              ok: !data.degraded,
              imageAttached: data.image_attached
            };
          }
        }
      }
    } catch {
      // Gateway unreachable or timed out
    }

    // 2. Direct Google Gemini Multimodal API query (using GEMINI_API_KEY from .env)
    const geminiDirect = await this.queryGeminiDirect(prompt, imageToUse, langCode, isVisionRequest);
    if (geminiDirect) {
      return {
        reply: geminiDirect,
        ok: true,
        imageAttached: !!imageToUse
      };
    }

    // 3. For conversational questions, query Groq 120B AI Companion directly (sub-300ms, zero quota limit)
    if (!isVisionRequest) {
      const groqDirect = await this.queryGroqDirect(prompt, langCode);
      if (groqDirect) {
        return {
          reply: groqDirect,
          ok: true,
          imageAttached: false
        };
      }
    }

    // 3. Multimodal scene requests without Gemini answer
    if (isVisionRequest) {
      if (!imageToUse) {
        return {
          reply:
            langCode === 'hi'
              ? 'कैमरा बंद है या कोई तस्वीर नहीं मिली। कृपया अपने स्मार्ट ग्लास या फोन का कैमरा चालू करें।'
              : 'The camera is turned off or not sending frames. Please turn on your camera so I can see what is in front of you.',
          ok: true,
          reason: 'no_camera_frame'
        };
      }

      // Truthful camera guidance
      return {
        reply:
          langCode === 'hi'
            ? 'कैमरा दृश्य का विश्लेषण नहीं हो सका। कृपया अपना नेटवर्क कनेक्शन जांचें।'
            : 'Could not analyze the camera view right now. Please check your network connection.',
        ok: true,
        imageAttached: true
      };
    }

    // 4. Google Assistant-style response engine (Hey BlindSight) - NEVER fails or says "offline"
    const reply = AssistantBrain.answer(prompt, langCode);
    return {
      reply,
      ok: true,
      imageAttached: false
    };
  }
}
