import { ReflexAlert, ReflexTelemetry, DiagnosticsLog } from '../types';
import { AudioService } from './audioService';

export class ReflexService {
  private static lastAlertTime = 0;
  private static logs: DiagnosticsLog[] = [];
  private static listeners: Array<(telemetry: ReflexTelemetry) => void> = [];
  private static currentBearing: number = 42;
  private static lastLatencyMs: number = 240;
  private static lastPayloadBytes: number = 48500;
  private static lastTokens: number = 28;
  private static compassInitialized = false;

  private static currentTelemetry: ReflexTelemetry = {
    leftCm: 150,
    centerCm: 180,
    rightCm: 160,
    alert: 'clear',
    message: 'Path clear.',
    audioClip: '/audio/clear.wav',
    timestamp: Date.now(),
    bearing: 42,
    fps: 30,
    lastLatencyMs: 240,
    lastPayloadBytes: 48500,
    lastTokens: 28,
    recentLogs: []
  };

  static init(): void {
    if (this.compassInitialized) return;
    this.compassInitialized = true;
    if (typeof window !== 'undefined' && 'DeviceOrientationEvent' in window) {
      window.addEventListener('deviceorientation', (e: any) => {
        if (e.webkitCompassHeading !== undefined) {
          this.currentBearing = Math.round(e.webkitCompassHeading);
        } else if (e.alpha !== null && !isNaN(e.alpha)) {
          this.currentBearing = Math.round(e.alpha);
        }
        this.currentTelemetry.bearing = this.currentBearing;
      });
    }
    this.addLog({
      type: 'info',
      message: 'Dual-loop diagnostics & HC-SR04 telemetry pipeline active.'
    });
  }

  static addListener(cb: (telemetry: ReflexTelemetry) => void): () => void {
    this.listeners.push(cb);
    return () => {
      this.listeners = this.listeners.filter(l => l !== cb);
    };
  }

  private static notifyListeners(): void {
    this.currentTelemetry.recentLogs = this.logs.slice(0, 15);
    this.currentTelemetry.bearing = this.currentBearing;
    this.currentTelemetry.lastLatencyMs = this.lastLatencyMs;
    this.currentTelemetry.lastPayloadBytes = this.lastPayloadBytes;
    this.currentTelemetry.lastTokens = this.lastTokens;
    for (const listener of this.listeners) {
      listener({ ...this.currentTelemetry });
    }
  }

  static addLog(log: Omit<DiagnosticsLog, 'id' | 'timestamp'>): void {
    const item: DiagnosticsLog = {
      id: `${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      timestamp: Date.now(),
      bearing: this.currentBearing,
      ...log
    };
    this.logs.unshift(item);
    if (this.logs.length > 50) this.logs.pop();
    this.notifyListeners();
  }

  static getLogs(): DiagnosticsLog[] {
    return [...this.logs];
  }

  static recordCapture(latencyMs: number, payloadBytes: number, tokenCount: number, model: string = 'Gemini 2.5 Flash'): void {
    this.lastLatencyMs = latencyMs;
    this.lastPayloadBytes = payloadBytes;
    this.lastTokens = tokenCount;
    this.addLog({
      type: 'capture',
      message: `Frame processed (${Math.round(payloadBytes / 1024)} KB, ${tokenCount} tokens, ${latencyMs}ms) via ${model}`,
      latencyMs,
      payloadBytes,
      tokenCount,
      modelUsed: model,
      bearing: this.currentBearing
    });
  }

  static recordFallback(reason: string, fallbackModel: string): void {
    this.addLog({
      type: 'fallback',
      message: `API fallback engaged [${reason}] -> seamlessly rerouted to ${fallbackModel} (UI responsive).`
    });
  }

  static getBearing(): number {
    return this.currentBearing;
  }

  static evaluateSensors(leftCm: number, centerCm: number, rightCm: number): ReflexTelemetry {
    const minDistance = Math.min(leftCm, centerCm, rightCm);
    let alert: ReflexAlert = 'clear';
    let message = 'Path clear.';
    let audioClip = '/audio/clear.wav';

    if (minDistance < 25) {
      alert = 'stop';
      message = 'Obstacle critical! Stop.';
      audioClip = '/audio/stop.wav';
    } else if (minDistance <= 45) {
      if (leftCm < rightCm) {
        alert = 'steer_right';
        message = 'Obstacle near left. Steer right.';
        audioClip = '/audio/right.wav';
      } else {
        alert = 'steer_left';
        message = 'Obstacle near right. Steer left.';
        audioClip = '/audio/left.wav';
      }
    } else if (minDistance < 100) {
      alert = 'careful';
      message = 'Obstacle ahead. Proceed carefully.';
      audioClip = '/audio/careful.wav';
    } else {
      alert = 'clear';
      message = 'Path clear.';
      audioClip = '/audio/clear.wav';
    }

    const telemetry: ReflexTelemetry = {
      leftCm,
      centerCm,
      rightCm,
      alert,
      message,
      audioClip,
      timestamp: Date.now(),
      bearing: this.currentBearing,
      fps: 30,
      lastLatencyMs: this.lastLatencyMs,
      lastPayloadBytes: this.lastPayloadBytes,
      lastTokens: this.lastTokens,
      recentLogs: this.logs.slice(0, 15)
    };

    this.currentTelemetry = telemetry;
    this.notifyListeners();
    return telemetry;
  }

  static getTelemetry(): ReflexTelemetry {
    return { ...this.currentTelemetry };
  }

  static triggerReflexAudio(telemetry: ReflexTelemetry, force = false): void {
    const now = Date.now();
    if (!force && telemetry.alert === 'clear' && now - this.lastAlertTime < 6000) {
      return;
    }
    if (!force && telemetry.alert !== 'stop' && now - this.lastAlertTime < 3000) {
      return;
    }
    this.lastAlertTime = now;
    if (telemetry.audioClip) {
      AudioService.playAudioClip(telemetry.audioClip);
    }
  }
}

// Auto-initialize on import
ReflexService.init();
