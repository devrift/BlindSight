import { CompanionStateService, LANGUAGES } from './companionState';
import { LanguageCode } from '../types';
import { AssistantBrain } from './assistantBrain';
import { ReflexService } from './reflexService';

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
    const envKey = (import.meta as any).env?.VITE_GEMINI_API_KEY || (import.meta as any).env?.GEMINI_API_KEY || "";
    if (envKey && String(envKey).trim()) return String(envKey).trim();
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('blindsight_gemini_api_key');
      if (stored && stored.trim()) return stored.trim();
    }
    return '';
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
  static async queryGroqDirect(prompt: string, langCode: LanguageCode, imageDataUrl?: string | null): Promise<string | null> {
    const key = this.getGroqApiKey();
    if (!key) return null;

    const sysPrompt = `You are BlindSight, a warm, observant, and trusted companion for someone who is visually impaired—just like a friend on a call in Be My Eyes.

SPEECH MANNERISMS & TONE:
- Talk like a relaxed, clear-thinking human, not a digital bot. Use natural spoken conversational transitions: "Alright, let's take a look...", "Got it...", "Okay, so right in front of you...", "Hmm, looks like...".
- Absolutely NO robotic lists, bullets, markdown asterisks, or emojis. Never render any emojis.
- Deliver speech at a steady, trustworthy pace.
- Strictly respond in the language used by the user: English (conversational Indian English), natural Hindi-Urdu (Hindustani), Punjabi, or Bengali. Never drift into foreign languages.

RICH VISUAL PERCEPTION (WHAT & WHERE):
Do not give single-word labels. Describe spatial orientation, state, and relevant text:
1. Subject & Position: What is directly in front, to the left, and to the right (e.g., "Right in front of you on the desk is your open laptop. The screen is turned on with a code window open. Next to it on the right is a tall blue water bottle.").
2. Text & Labels: If there is prominent text, brand names, or writing visible, read it out naturally.
3. Hazards & Paths: Note immediate edges, cables, or obstacles so the user knows where their hands or feet can safely move.
Keep descriptions informative yet concise enough for audio delivery (around 35-50 words).

FULL APP CONTROL DIRECTIVES:
You have autonomous control over this app and the user's phone. When the user gives a command to navigate, pause, inspect history, or change device settings, acknowledge warmly and append the corresponding [ACTION:...] tag at the very end:
- Open Settings: [ACTION:GO_TO_SETTINGS]
- Return Home: [ACTION:GO_TO_HOME]
- Show History: [ACTION:SHOW_HISTORY]
- Show Telemetry: [ACTION:SHOW_TELEMETRY]
- Pause playback: [ACTION:PAUSE]
- Resume playback/listening: [ACTION:RESUME]
- Repeat last spoken response: [ACTION:REPEAT]
- Clear log/chat: [ACTION:CLEAR_HISTORY]
- Turn on flashlight: [ACTION:TORCH_ON]
- Turn off flashlight: [ACTION:TORCH_OFF]
- Flip camera: [ACTION:SWITCH_CAMERA]
- Read label/text directly: [ACTION:READ_TEXT]
- Walking navigation: [ACTION:NAVIGATE <destination>]
- Change language: [ACTION:SET_LANGUAGE <en|hi|pa|bn>]`;

    const models = ['openai/gpt-oss-120b', 'qwen/qwen3.8-27b', 'openai/gpt-oss-20b'];
    for (const model of models) {
      try {
        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 6000);

        let messages: any[];
        if (imageDataUrl && model === 'qwen/qwen3.8-27b') {
          const base64 = imageDataUrl.includes(',') ? imageDataUrl.split(',')[1] : imageDataUrl;
          messages = [
            { role: 'system', content: sysPrompt },
            {
              role: 'user',
              content: [
                { type: 'text', text: prompt },
                { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${base64}` } }
              ]
            }
          ];
        } else {
          messages = [
            { role: 'system', content: sysPrompt },
            { role: 'user', content: prompt }
          ];
        }

        const t0 = performance.now();
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${key}`
          },
          body: JSON.stringify({
            model,
            messages,
            temperature: 0.2,
            max_tokens: 40
          }),
          signal: ctrl.signal
        });
        clearTimeout(timeout);
        if (res.ok) {
          const data = await res.json();
          const content = data?.choices?.[0]?.message?.content?.trim();
          const latency = Math.round(performance.now() - t0);
          if (content) {
            ReflexService.recordCapture(latency, imageDataUrl ? Math.round(imageDataUrl.length * 0.75) : 800, 30, `Groq ${model}`);
            return content;
          }
        }
      } catch (e) {
        console.warn('Groq direct query attempt notice:', e);
      }
    }
    return null;
  }

  /**
   * Captures a single image frame from an active video element
   * with automatic 90-degree matrix rotation check and 640x480 (0.65 quality) downscale.
   */
  static captureFrameFromVideo(videoEl: HTMLVideoElement, forceRotate90?: boolean): string | null {
    try {
      if (!videoEl.videoWidth || !videoEl.videoHeight) return null;
      const canvas = document.createElement('canvas');
      
      const isScreenPortrait = typeof window !== 'undefined' ? window.innerHeight > window.innerWidth : true;
      const isVideoLandscape = videoEl.videoWidth > videoEl.videoHeight;
      const shouldRotate = forceRotate90 ?? (isScreenPortrait && isVideoLandscape);

      const targetWidth = 640;
      const targetHeight = 480;

      if (shouldRotate) {
        // Rotates landscape sensor feed to upright portrait frame
        canvas.width = targetHeight; // 480
        canvas.height = targetWidth; // 640
        const ctx = canvas.getContext('2d');
        if (!ctx) return null;
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate((90 * Math.PI) / 180);
        ctx.drawImage(videoEl, -targetWidth / 2, -targetHeight / 2, targetWidth, targetHeight);
      } else {
        canvas.width = targetWidth;
        canvas.height = targetHeight;
        const ctx = canvas.getContext('2d');
        if (!ctx) return null;
        ctx.drawImage(videoEl, 0, 0, targetWidth, targetHeight);
      }

      // Strict 640x480 JPEG (0.65 quality)
      const dataUrl = canvas.toDataURL('image/jpeg', 0.65);
      this.lastCapturedFrame = dataUrl;
      return dataUrl;
    } catch {
      return null;
    }
  }

  /**
   * Directly queries Google Gemini Multimodal API with strict parameters:
   * maxOutputTokens: 40, temperature: 0.2
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
      const sysInstruction = `You are BlindSight, a warm, observant, and trusted companion for someone who is visually impaired—just like a friend on a call in Be My Eyes.

SPEECH MANNERISMS & TONE:
- Talk like a relaxed, clear-thinking human, not a digital bot. Use natural spoken conversational transitions: "Alright, let's take a look...", "Got it...", "Okay, so right in front of you...", "Hmm, looks like...".
- Absolutely NO robotic lists, bullets, markdown asterisks, or emojis. Never render any emojis.
- Deliver speech at a steady, trustworthy pace.
- Strictly respond in the language used by the user: English (conversational Indian English), natural Hindi-Urdu (Hindustani), Punjabi, or Bengali. Never drift into foreign languages.

RICH VISUAL PERCEPTION (WHAT & WHERE):
Do not give single-word labels. Describe spatial orientation, state, and relevant text:
1. Subject & Position: What is directly in front, to the left, and to the right (e.g., "Right in front of you on the desk is your open laptop. The screen is turned on with a code window open. Next to it on the right is a tall blue water bottle.").
2. Text & Labels: If there is prominent text, brand names, or writing visible, read it out naturally.
3. Hazards & Paths: Note immediate edges, cables, or obstacles so the user knows where their hands or feet can safely move.
Keep descriptions informative yet concise enough for audio delivery (around 35-50 words).

FULL APP CONTROL DIRECTIVES:
You have autonomous control over this app and the user's phone. When the user gives a command to navigate, pause, inspect history, or change device settings, acknowledge warmly and append the corresponding [ACTION:...] tag at the very end:
- Open Settings: [ACTION:GO_TO_SETTINGS]
- Return Home: [ACTION:GO_TO_HOME]
- Show History: [ACTION:SHOW_HISTORY]
- Show Telemetry: [ACTION:SHOW_TELEMETRY]
- Pause playback: [ACTION:PAUSE]
- Resume playback/listening: [ACTION:RESUME]
- Repeat last spoken response: [ACTION:REPEAT]
- Clear log/chat: [ACTION:CLEAR_HISTORY]
- Turn on flashlight: [ACTION:TORCH_ON]
- Turn off flashlight: [ACTION:TORCH_OFF]
- Flip camera: [ACTION:SWITCH_CAMERA]
- Read label/text directly: [ACTION:READ_TEXT]
- Walking navigation: [ACTION:NAVIGATE <destination>]
- Change language: [ACTION:SET_LANGUAGE <en|hi|pa|bn>]`;

      parts.push({ text: `${sysInstruction}\n\nUser Question: ${prompt}` });

      let payloadBytes = 400;
      if (isVisionQuery && imageDataUrl) {
        const base64Data = imageDataUrl.includes(',') ? imageDataUrl.split(',')[1] : imageDataUrl;
        payloadBytes = Math.round(base64Data.length * 0.75);
        parts.push({
          inline_data: {
            mime_type: 'image/jpeg',
            data: base64Data
          }
        });
      }

      const candidateModels = [
        'gemini-1.5-flash',
        'gemini-2.5-flash',
        'gemini-3.8-flash',
        'gemini-flash-latest'
      ];

      for (const model of candidateModels) {
        try {
          const t0 = performance.now();
          const ctrl = new AbortController();
          const timeout = setTimeout(() => ctrl.abort(), 7000);
          const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ parts }],
              generationConfig: {
                temperature: 0.2,
                maxOutputTokens: 120
              }
            }),
            signal: ctrl.signal
          });
          clearTimeout(timeout);

          if (res.ok) {
            const data = await res.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
            const latency = Math.round(performance.now() - t0);
            if (text && text.trim()) {
              ReflexService.recordCapture(latency, payloadBytes, 32, model);
              return text.trim();
            }
          } else if (res.status === 429) {
            ReflexService.recordFallback(`Gemini ${model} 429 Rate Limit`, 'Groq LPU 120B');
            break; // Immediately break to Groq fallback without freezing UI!
          }
        } catch {
          ReflexService.recordFallback(`Gemini ${model} Network Timeout`, 'Groq LPU 120B');
        }
      }
    } catch (e) {
      ReflexService.recordFallback('Gemini Client Exception', 'Groq LPU 120B');
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

    // 1. Primary: Gemini Multimodal Vision / Deep QA
    const geminiReply = await this.queryGeminiDirect(prompt, imageToUse, langCode, isVisionRequest);
    if (geminiReply) {
      return { reply: geminiReply, ok: true, imageAttached: !!imageToUse };
    }

    // 2. Seamless Fast Fallback: Groq 120B LPU
    const groqReply = await this.queryGroqDirect(prompt, langCode, imageToUse);
    if (groqReply) {
      return { reply: groqReply, ok: true, imageAttached: !!imageToUse };
    }

    // 3. Guaranteed Local Assistant Brain (never freezes or leaves user silent)
    const localReply = AssistantBrain.answer(prompt, langCode);
    return { reply: localReply, ok: true, reason: 'local-fallback' };
  }
}
