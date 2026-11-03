import React, { useEffect, useRef, useState } from 'react';
import { Camera, X, RefreshCw, Eye } from 'lucide-react';
import { VisionAiService } from '../services/visionAi';
import { LanguageCode } from '../types';

interface CameraCaptureModalProps {
  isOpen: boolean;
  onClose: () => void;
  langCode: LanguageCode;
  onDescribeScene: (dataUrl: string) => void;
}

export const CameraCaptureModal: React.FC<CameraCaptureModalProps> = ({
  isOpen,
  onClose,
  langCode,
  onDescribeScene
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [hasCamera, setHasCamera] = useState(false);
  const [capturedSnapshot, setCapturedSnapshot] = useState<string | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
        streamRef.current = null;
      }
      return;
    }

    let isMounted = true;

    async function startCamera() {
      try {
        setCameraError(null);
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          throw new Error('Camera API not supported on this browser.');
        }

        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } }
        });

        if (isMounted) {
          streamRef.current = stream;
          if (videoRef.current) {
            videoRef.current.srcObject = stream;
            videoRef.current.play().catch(() => {});
          }
          setHasCamera(true);
        }
      } catch (err: any) {
        if (isMounted) {
          setCameraError(err.message || 'Camera permission denied or camera not found.');
          setHasCamera(false);
        }
      }
    }

    startCamera();

    return () => {
      isMounted = false;
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
        streamRef.current = null;
      }
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const handleCapture = () => {
    if (videoRef.current) {
      const dataUrl = VisionAiService.captureFrameFromVideo(videoRef.current);
      if (dataUrl) {
        setCapturedSnapshot(dataUrl);
        VisionAiService.setLatestFrame(dataUrl);
        onDescribeScene(dataUrl);
      }
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="camera-modal-title">
      <div className="modal-content">
        <div className="modal-header">
          <h2 id="camera-modal-title">
            <Camera size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            Tier 2: Live Camera & Vision Helper
          </h2>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close camera viewer">
            <X size={24} />
          </button>
        </div>

        {cameraError ? (
          <div style={{ background: '#7f1d1d', color: '#fecaca', padding: 14, borderRadius: 10 }}>
            {cameraError}
            <p style={{ margin: '8px 0 0', fontSize: '0.85rem' }}>
              Ensure browser permissions for camera are granted. You can still ask questions via voice or text.
            </p>
          </div>
        ) : (
          <div style={{ position: 'relative', width: '100%', borderRadius: 12, overflow: 'hidden', background: '#000' }}>
            <video
              ref={videoRef}
              playsInline
              muted
              style={{ width: '100%', maxHeight: 260, objectFit: 'cover', display: 'block' }}
            />
            {capturedSnapshot && (
              <div
                style={{
                  position: 'absolute',
                  bottom: 8,
                  right: 8,
                  border: '2px solid var(--accent)',
                  borderRadius: 6,
                  overflow: 'hidden'
                }}
              >
                <img src={capturedSnapshot} alt="Last snapshot" style={{ width: 80, height: 60, display: 'block' }} />
              </div>
            )}
          </div>
        )}

        <div style={{ display: 'flex', gap: 10 }}>
          <button
            type="button"
            className="badge clickable"
            style={{ flex: 1, justifyContent: 'center', background: 'var(--accent)', color: '#000', fontWeight: 700 }}
            onClick={handleCapture}
            disabled={!hasCamera}
          >
            <Eye size={18} style={{ marginRight: 8 }} /> Describe Current Scene
          </button>
        </div>

        <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--muted)' }}>
          Tip: In wake word or hands-free mode, simply say <strong>“describe”</strong> or <strong>“what is around me”</strong> to automatically trigger camera description.
        </p>
      </div>
    </div>
  );
};
