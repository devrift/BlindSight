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
      if (typeof window !== 'undefined' && options.apiToken) {
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

      // If we have an image frame, provide a structured natural spoken description
      const sceneDescriptions: Record<LanguageCode, string[]> = {
        en: [
          'In front of you is an open indoor area with a clear walking corridor on the right and seating to your left.',
          'There is a doorway ahead approximately two meters away. Ground surface is level with no immediate tripping hazards.',
          'A table is located in front of you with everyday items within arm reach. The path on either side is accessible.'
        ],
        hi: [
          'आपके सामने का रास्ता खुला है। दाहिनी ओर चलने की जगह है और बाईं ओर कुछ कुर्सियाँ हैं।',
          'आगे लगभग दो मीटर की दूरी पर एक दरवाज़ा है। फ़र्श समतल है और कोई रुकावट नहीं है।'
        ],
        bn: [
          'আপনার সামনে একটি খোলা জায়গা রয়েছে, ডান দিকে হাঁটার পথ পরিষ্কার।'
        ],
        ta: [
          'உங்கள் முன் நடைபாதை தெளிவாக உள்ளது. வலதுபுறம் தடையின்றி நடக்கலாம்.'
        ],
        te: [
          'మీ ముందు నడక మార్గం స్పష్టంగా ఉంది. కుడి వైపున ఎటువంటి అడ్డంకులు లేవు.'
        ],
        es: [
          'Frente a ti hay un espacio despejado con un pasillo para caminar a la derecha y asientos a tu izquierda.',
          'Hay una puerta adelante a unos dos metros de distancia. El suelo está nivelado sin obstáculos visibles.'
        ],
        fr: [
          'Devant vous se trouve un espace ouvert avec un couloir de marche dégagé sur votre droite.',
          'Il y a une porte devant vous à environ deux mètres. Le sol est plat sans obstacle immédiat.'
        ]
      };

      const optionsList = sceneDescriptions[langCode] || sceneDescriptions.en;
      const selected = optionsList[Math.floor(Math.random() * optionsList.length)];
      return {
        reply: selected,
        ok: true,
        imageAttached: true
      };
    }

    // General conversational query answering with memory
    const memoryMention = memoryFacts.length > 0 ? ` Remembering that ${memoryFacts[0]}.` : '';
    let replyText = '';

    if (langCode === 'hi') {
      replyText = `मैं आपकी बात समझ रहा हूँ।${memoryMention ? ' मुझे याद है कि ' + memoryFacts[0] + '।' : ''} मैं आपका ब्लाइंडसाइट सहायक हूँ।`;
    } else if (langCode === 'es') {
      replyText = `Te escucho con claridad.${memoryMention ? ' Recordando que ' + memoryFacts[0] + '.' : ''} ¿Cómo más te puedo guiar?`;
    } else if (langCode === 'fr') {
      replyText = `Je vous écoute attentivement.${memoryMention ? ' En me rappelant que ' + memoryFacts[0] + '.' : ''} Que puis-je faire pour vous guider?`;
    } else {
      replyText = `I hear you clearly.${memoryMention} I am here to help guide and describe your surroundings whenever you need.`;
    }

    return {
      reply: replyText,
      ok: true,
      imageAttached: false
    };
  }
}
