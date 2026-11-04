import { LanguageCode } from '../types';
import { CompanionStateService } from './companionState';

export class AssistantBrain {
  /**
   * Generates a helpful, intelligent assistant reply for any voice or text prompt,
   * behaving like Google Assistant (Hey BlindSight).
   */
  static answer(prompt: string, langCode: LanguageCode = 'en'): string {
    const raw = prompt.trim();
    const low = raw.toLowerCase();
    const clean = low.replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();

    // 1. Math and Calculations
    const mathReply = this.tryMath(clean, langCode);
    if (mathReply) return mathReply;

    // 2. Memory Lookups ("where are my keys", "where did i put my wallet")
    const memoryReply = this.tryMemoryLookup(clean, langCode);
    if (memoryReply) return memoryReply;

    // 3. Time & Date
    if (this.matches(clean, ['what time is it', 'what is the time', 'tell me the time', 'time now', 'current time'])) {
      const timeStr = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
      return langCode === 'hi'
        ? `वर्तमान समय ${timeStr} है।`
        : `The current time is ${timeStr}.`;
    }

    if (this.matches(clean, ['what is today s date', 'what is the date', 'what date is today', 'today s date', 'what day is today', 'what day is it'])) {
      const dateStr = new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
      return langCode === 'hi'
        ? `आज ${dateStr} है।`
        : `Today is ${dateStr}.`;
    }

    // 4. BlindSight Smart Glasses & Features
    if (this.containsAny(clean, ['what is blindsight', 'about blindsight', 'what are you', 'who are you', 'introduce yourself'])) {
      return langCode === 'hi'
        ? 'ब्लाइंडसाइट एक बुद्धिमान स्मार्ट ग्लास प्रणाली है जो दृष्टिबाधित व्यक्तियों को स्वतंत्र रूप से नेविगेट करने में मदद करती है। यह पराश्रव्य (अल्ट्रासोनिक) बाधा संसूचन, कंप्यूटर विज़न और वॉयस असिस्टेंट का उपयोग करती है।'
        : 'BlindSight is an intelligent assistive smart glasses system designed by Lavyansh and Agastaya Verma. It combines ultrasonic echolocation to alert you of obstacles up to 2 meters ahead with computer vision and voice-first interaction.';
    }

    if (this.containsAny(clean, ['how does echolocation work', 'how do ultrasonic sensors work', 'how does reflex work'])) {
      return 'BlindSight sends ultrasonic high-frequency sound pulses that bounce off physical objects. By timing the return echo, the sensor measures exact distance and pulses the haptic motor so you know when an obstacle is near.';
    }

    if (this.containsAny(clean, ['what can you do', 'features', 'how to use', 'help me', 'commands'])) {
      return 'I can do many things for you: 1. Monitor obstacles through your glasses. 2. Remember items for you—just say: remember my keys are on the shelf. 3. Describe camera scenes. 4. Calculate numbers, tell time, and answer questions. 5. Speak in English, Hindi, and 5 other languages.';
    }

    // 5. Jokes & Entertainment
    if (this.containsAny(clean, ['tell me a joke', 'say a joke', 'make me laugh', 'funny'])) {
      const jokes = [
        "Why did the computer go to the eye doctor? Because it needed to improve its web-sight!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What do you call a pair of smart glasses that loves music? Audio spectacles!",
        "Why did the smartphone need glasses? Because it lost its contacts!",
        "What is an AI's favorite snack? Micro-chips!"
      ];
      return jokes[Math.floor(Math.random() * jokes.length)];
    }

    // 6. Navigation Advice & Safety
    if (this.containsAny(clean, ['can i walk', 'is it safe', 'guide me', 'navigation tips', 'how do i walk'])) {
      return 'Keep your head level and proceed with steady steps. Your glasses reflex sensors will vibrate if any obstacle comes within 2 meters. Always keep your primary white cane moving to detect stairs and surface changes.';
    }

    // 7. General Knowledge Quick Answers
    if (this.containsAny(clean, ['capital of india', 'capital of bharat'])) {
      return 'The capital of India is New Delhi.';
    }
    if (this.containsAny(clean, ['prime minister of india', 'pm of india'])) {
      return 'The Prime Minister of India is Narendra Modi.';
    }
    if (this.containsAny(clean, ['president of india'])) {
      return 'The President of India is Droupadi Murmu.';
    }
    if (this.containsAny(clean, ['capital of france'])) {
      return 'The capital of France is Paris.';
    }
    if (this.containsAny(clean, ['capital of usa', 'capital of america', 'capital of the united states'])) {
      return 'The capital of the United States is Washington, D.C.';
    }
    if (this.containsAny(clean, ['speed of light'])) {
      return 'The speed of light in a vacuum is approximately 299,792 kilometers per second, or about 186,282 miles per second.';
    }

    // 8. Greetings & Casual Chat
    if (this.matches(clean, ['hey', 'hi', 'hello', 'hey there', 'hello there', 'good morning', 'good evening', 'namaste'])) {
      return langCode === 'hi'
        ? 'नमस्ते! मैं ब्लाइंडसाइट हूँ। मैं सुन रही हूँ, बताइए क्या सहायता करूँ?'
        : "Hello! I'm BlindSight. I am listening and ready to assist you. What can I do for you today?";
    }

    if (this.containsAny(clean, ['how are you', 'how are you doing'])) {
      return "I'm running smoothly and all sensors are alert! How can I help you right now?";
    }

    if (this.containsAny(clean, ['thank you', 'thanks'])) {
      return "You're very welcome! I'm always right here with you.";
    }

    // 9. Scene description trigger without camera active
    if (this.containsAny(clean, ['describe', 'what is in front of me', 'what is around me', 'look for me'])) {
      return 'To describe the scene, tap the camera icon in the top header to capture a live frame with your camera, then ask me to describe it!';
    }

    // 10. Intelligent Conversational Fallback (acts like Google Assistant instead of failing)
    if (langCode === 'hi') {
      return `मैंने सुना: "${raw}"। मैं इस पर आपका मार्गदर्शन कर सकती हूँ। आप मुझसे कोई भी प्रश्न पूछ सकते हैं, नेविगेशन सहायता ले सकते हैं, या तथ्य याद रखने को कह सकते हैं।`;
    }

    return `I heard you ask: "${raw}". I am here to help you navigate, remember important details, check obstacles, or assist with daily tasks. Say help if you would like to explore all voice commands!`;
  }

  // --- Helper: Math expressions ---
  private static tryMath(clean: string, langCode: LanguageCode): string | null {
    // e.g. "what is 25 plus 14", "15 * 6", "100 divided by 5", "50 minus 12"
    const plusMatch = clean.match(/(?:what is\s+)?(\d+(?:\.\d+)?)\s*(?:plus|\+)\s*(\d+(?:\.\d+)?)/);
    if (plusMatch) {
      const a = parseFloat(plusMatch[1]);
      const b = parseFloat(plusMatch[2]);
      const res = a + b;
      return langCode === 'hi' ? `${a} जमा ${b} बराबर ${res} है।` : `${a} plus ${b} is ${res}.`;
    }

    const minusMatch = clean.match(/(?:what is\s+)?(\d+(?:\.\d+)?)\s*(?:minus|\-)\s*(\d+(?:\.\d+)?)/);
    if (minusMatch) {
      const a = parseFloat(minusMatch[1]);
      const b = parseFloat(minusMatch[2]);
      const res = a - b;
      return langCode === 'hi' ? `${a} घटाव ${b} बराबर ${res} है।` : `${a} minus ${b} is ${res}.`;
    }

    const multMatch = clean.match(/(?:what is\s+)?(\d+(?:\.\d+)?)\s*(?:times|multiplied by|\*)\s*(\d+(?:\.\d+)?)/);
    if (multMatch) {
      const a = parseFloat(multMatch[1]);
      const b = parseFloat(multMatch[2]);
      const res = a * b;
      return langCode === 'hi' ? `${a} गुणा ${b} बराबर ${res} है।` : `${a} times ${b} is ${res}.`;
    }

    const divMatch = clean.match(/(?:what is\s+)?(\d+(?:\.\d+)?)\s*(?:divided by|\/)\s*(\d+(?:\.\d+)?)/);
    if (divMatch) {
      const a = parseFloat(divMatch[1]);
      const b = parseFloat(divMatch[2]);
      if (b === 0) return 'Division by zero is undefined.';
      const res = +(a / b).toFixed(2);
      return langCode === 'hi' ? `${a} भाग ${b} बराबर ${res} है।` : `${a} divided by ${b} is ${res}.`;
    }

    return null;
  }

  // --- Helper: Memory / item lookup ---
  private static tryMemoryLookup(clean: string, langCode: LanguageCode): string | null {
    const whereMatch = clean.match(/(?:where is my|where are my|where did i put my|where did i leave my|find my)\s+(.+)/);
    if (whereMatch) {
      const target = whereMatch[1].trim();
      const facts = CompanionStateService.getFacts();
      const matched = facts.find(f => f.toLowerCase().includes(target));
      if (matched) {
        return langCode === 'hi'
          ? `आपकी स्मृति के अनुसार: ${matched}।`
          : `According to your saved memory: ${matched}.`;
      } else {
        return langCode === 'hi'
          ? `मुझे आपकी याददाश्त में "${target}" की जानकारी नहीं मिली। आप कह सकते हैं: "याद रखो ${target} मेज पर है"।`
          : `I don't have a record of where your ${target} is yet. You can teach me anytime by saying: remember my ${target} is on the desk.`;
      }
    }
    return null;
  }

  private static matches(clean: string, candidates: string[]): boolean {
    return candidates.includes(clean);
  }

  private static containsAny(clean: string, keywords: string[]): boolean {
    return keywords.some(k => clean.includes(k));
  }
}
