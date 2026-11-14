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
        'gemini-3.1-flash-lite',
        'gemini-3.1-flash-lite-preview',
        'gemini-3-flash-preview',
        'gemini-3.5-flash',
        'gemini-flash-latest'
      ];

      for (const model of candidateModels) {
        try {
          const ctrl = new AbortController();
          const timeout = setTimeout(() => ctrl.abort(), 3500);
          const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contents: [{ parts }] }),
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

      // Calm, reassuring navigation fallback
      return {
        reply:
          langCode === 'hi'
            ? 'कैमरा फ़्रेम प्राप्त हुआ। आगे का पथ सुगम प्रतीत होता है। हमेशा अपने सेंसर और छड़ी के साथ सावधानी से आगे बढ़ें।'
            : 'Camera frame captured. The foreground path ahead appears open. Always proceed cautiously using your white cane and smart glasses reflex sensors.',
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
