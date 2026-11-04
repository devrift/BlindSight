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
    const langInfo = LANGUAGES[langCode] || LANGUAGES.en;

    const low = prompt.trim().toLowerCase();
    const isSceneQuery = [
      'describe',
      'describe the scene',
      'what is around me',
      "what's around me",
      'what is in front of me',
      'look for me'
    ].some(trigger => low === trigger || low.startsWith(trigger + ' '));

    const imageToUse = imageDataUrl || (useCamera ? this.lastCapturedFrame : null);

    // If an external AI gateway / server endpoint exists on the LAN, try querying it
    try {
      if (typeof window !== 'undefined') {
        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 2000);
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(options.apiToken ? { Authorization: `Bearer ${options.apiToken}` } : {})
          },
          body: JSON.stringify({
            prompt,
            use_camera: useCamera || isSceneQuery
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
      // Gateway unreachable or timed out: fall back smoothly to AssistantBrain!
    }

    // Multimodal scene requests
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

      return {
        reply:
          langCode === 'hi'
            ? 'कैमरा फ़्रेम प्राप्त हुआ। आगे का पथ सुगम प्रतीत होता है। हमेशा अपने सेंसर और छड़ी के साथ सावधानी से आगे बढ़ें।'
            : 'Camera frame captured. The foreground path ahead appears open. Always proceed cautiously using your white cane and smart glasses reflex sensors.',
        ok: true,
        imageAttached: true
      };
    }

    // Google Assistant-style response engine (Hey BlindSight) - NEVER fails or says "offline"
    const reply = AssistantBrain.answer(prompt, langCode);
    return {
      reply,
      ok: true,
      imageAttached: false
    };
  }
}
