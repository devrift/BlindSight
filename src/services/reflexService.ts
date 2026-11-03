import { ReflexAlert, ReflexTelemetry } from '../types';
import { AudioService } from './audioService';

export class ReflexService {
  private static lastAlertTime = 0;
  private static currentTelemetry: ReflexTelemetry = {
    leftCm: 150,
    centerCm: 180,
    rightCm: 160,
    alert: 'clear',
    message: 'Path clear.',
    audioClip: '/audio/clear.wav',
    timestamp: Date.now()
  };

  static evaluateSensors(leftCm: number, centerCm: number, rightCm: number): ReflexTelemetry {
    const minDistance = Math.min(leftCm, centerCm, rightCm);
    let alert: ReflexAlert = 'clear';
    let message = 'Path clear.';
    let audioClip = '/audio/clear.wav';

    // Tier 0 Reflex bands from firmware/esp32_main:
    // < 25 cm -> Critical stop
    // 25 - 45 cm -> Steer away from closest obstacle
    // < 100 cm -> Proceed carefully
    // >= 100 cm -> Clear
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
      timestamp: Date.now()
    };

    this.currentTelemetry = telemetry;
    return telemetry;
  }

  static getTelemetry(): ReflexTelemetry {
    return this.currentTelemetry;
  }

  static triggerReflexAudio(telemetry: ReflexTelemetry, force = false): void {
    const now = Date.now();
    // Throttle repeated alerts to avoid audio clutter unless critical or forced
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
