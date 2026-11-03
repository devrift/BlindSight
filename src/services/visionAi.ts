import { CompanionStateService, LANGUAGES } from './companionState';
import { LanguageCode } from '../types';

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
    const memoryFacts = CompanionStateService.getFacts();

    // Check if prompt is a scene description trigger
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

    // If an OpenRouter / external gateway endpoint exists on the LAN, try querying it
    // If not or in local mode, generate intelligent fail-closed perception response
    try {
      if (typeof window !== 'undefined') {
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(options.apiToken ? { Authorization: `Bearer ${options.apiToken}` } : {})
          },
          body: JSON.stringify({
            prompt,
            use_camera: useCamera || isSceneQuery
          })
        });

        if (res.ok) {
          const data = await res.json();
          return {
            reply: data.reply,
            ok: !data.degraded,
            imageAttached: data.image_attached
          };
        }
      }
    } catch {
      // Gateway unreachable, fall back to companion reasoning
    }

    // Direct multimodal response generator
    if (isSceneQuery || imageToUse) {
      if (!imageToUse) {
        return {
          reply:
            langCode === 'en'
              ? 'I cannot see through the camera right now. Please allow camera access or ensure the camera is uncovered.'
              : `${langInfo.label}: No camera frame available. Please allow camera permissions.`,
          ok: false,
          reason: 'no_camera_frame'
        };
      }

      // HONESTY CONTRACT: without a reachable vision AI we must never invent
      // a scene. Guessing "path clear" for a blind user is a hazard, not a
      // feature.
      return {
        reply:
          langCode === 'en'
            ? 'I captured the image, but the vision AI is not connected right now, so I will not guess what is around you. Start the BlindSight gateway for real scene answers, or say help for offline commands.'
            : `${langInfo.label}: vision AI not connected. I will not guess your surroundings. Say help for offline commands.`,
        ok: false,
        reason: 'vision_ai_unreachable',
        imageAttached: true
      };
    }

    // No gateway/AI reachable: say so plainly. Local commands above still work.
    return {
      reply:
        langCode === 'en'
          ? 'The cloud companion is not connected right now, so I can only run on-device commands: remember, forget, language, read status, stop, help. Say help for the full list.'
          : `${langInfo.label}: companion offline. On-device commands only. Say help.`,
      ok: false,
      reason: 'companion_unreachable',
      imageAttached: false
    };
  }
}
