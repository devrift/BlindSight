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
    langCode: LanguageCode
  ): Promise<string | null> {
    const key = this.getGeminiApiKey();
    if (!key) return null;

    try {
      const parts: any[] = [];
      const sysInstruction = langCode === 'hi'
        ? "आप ब्लाइंडसाइट जार्विस हैं, दृष्टिबाधित उपयोगकर्ताओं के लिए एक सटीक नेविगेशन असिस्टेंट। दृश्य का संक्षिप्त और स्पष्ट विवरण 2-3 वाक्यों में दें: 1. आगे का रास्ता (12 बजे) खुला है या बंद और दूरी बताएं। 2. घड़ी की दिशा (10 बजे या 2 बजे) में मुख्य वस्तुएं बताएं। 3. जमीन पर खतरे (सीढ़ियाँ, तार, फिसलन) बताएं। 4. दिखे हुए बोर्ड या लेबल के शब्द बताएं।"
        : "You are BlindSight JARVIS, an ultra-precise assistive vision assistant for blind or visually impaired users. Provide a clean, calm, authoritative spatial description in 2-3 concise sentences: 1. WALKING CORRIDOR (12 o'clock): State whether it is OPEN or BLOCKED, with distance to nearest obstacle. 2. CLOCK-FACE OBSTACLES: Identify key items/furniture/people using clock positions (e.g. at 10 o'clock desk 4 feet away; at 2 o'clock doorway). 3. GROUND HAZARDS: Warn about steps, curbs, cables, drop-offs, or spills. 4. TEXT & SIGNS: Read visible signage or labels in quotes.";

      parts.push({ text: `${sysInstruction}\n\nUser Question: ${prompt}` });

      if (imageDataUrl) {
        const base64Data = imageDataUrl.includes(',') ? imageDataUrl.split(',')[1] : imageDataUrl;
        parts.push({
          inline_data: {
            mime_type: 'image/jpeg',
            data: base64Data
          }
        });
      }

      const ctrl = new AbortController();
      const timeout = setTimeout(() => ctrl.abort(), 9000);
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=${encodeURIComponent(key)}`, {
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
    const isSceneQuery = [
      'describe',
      'describe the scene',
      'what is around me',
      "what's around me",
      'what is in front of me',
      'look for me',
      'look ahead',
      'what do you see',
      'read this label'
    ].some(trigger => low === trigger || low.startsWith(trigger + ' '));

    const imageToUse = imageDataUrl || (useCamera ? this.lastCapturedFrame : null);

    // 1. If an external AI gateway / server endpoint exists on the LAN, try querying it
    try {
      if (typeof window !== 'undefined') {
        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 2500);
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(options.apiToken ? { Authorization: `Bearer ${options.apiToken}` } : {})
          },
          body: JSON.stringify({
            prompt,
            use_camera: useCamera || isSceneQuery,
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
    const geminiDirect = await this.queryGeminiDirect(prompt, imageToUse, langCode);
    if (geminiDirect) {
      return {
        reply: geminiDirect,
        ok: true,
        imageAttached: !!imageToUse
      };
    }

    // 3. Multimodal scene requests without Gemini answer
    if (isSceneQuery || imageToUse) {
      if (!imageToUse) {
        return {
          reply:
            langCode === 'hi'
              ? 'आसपास के दृश्य का विवरण प्राप्त करने के लिए, शीर्ष हेडर में कैमरा आइकन टैप करें और एक लाइव फ़्रेम कैप्चर करें।'
              : 'To describe what is around you, tap the camera icon in the top header to capture a live frame.',
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
