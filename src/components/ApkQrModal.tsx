import React from 'react';
import { X, QrCode, Download, Smartphone, CheckCircle } from 'lucide-react';

interface ApkQrModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ApkQrModal: React.FC<ApkQrModalProps> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  const downloadUrl = typeof window !== 'undefined'
    ? `${window.location.origin}/BlindSight-v1.0.0.apk`
    : '/BlindSight-v1.0.0.apk';

  // High-resolution instant QR code endpoint
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=260x260&margin=10&data=${encodeURIComponent(downloadUrl)}`;

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label="Download Android App via QR Code">
      <div className="modal-card" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 440, textAlign: 'center' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Smartphone size={24} style={{ color: 'var(--accent)' }} />
            <h3 style={{ margin: 0, fontSize: '1.25rem' }}>Get BlindSight Phone App</h3>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close dialog">
            <X size={20} />
          </button>
        </div>

        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: '0 0 16px 0' }}>
          Scan this QR code with your Android phone's camera to download and install the BlindSight Testing APK instantly.
        </p>

        <div style={{
          background: '#ffffff',
          padding: 16,
          borderRadius: 16,
          display: 'inline-block',
          boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
          marginBottom: 16
        }}>
          <img
            src={qrUrl}
            alt="Scan QR code to download BlindSight APK"
            width={240}
            height={240}
            style={{ display: 'block', borderRadius: 8 }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 8 }}>
          <a
            href="/BlindSight-v1.0.0.apk"
            download="BlindSight-v1.0.0.apk"
            className="btn btn-primary"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              padding: '12px 20px',
              textDecoration: 'none',
              fontWeight: 700,
              borderRadius: 12
            }}
          >
            <Download size={20} />
            <span>Direct Download (.apk)</span>
          </a>

          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textAlign: 'left', background: 'var(--card-edge)', padding: 12, borderRadius: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4, color: 'var(--accent)', fontWeight: 600 }}>
              <CheckCircle size={16} /> Ready for Vercel & Netlify
            </div>
            Direct URL: <code style={{ wordBreak: 'break-all', color: 'var(--text-primary)' }}>{downloadUrl}</code>
          </div>
        </div>
      </div>
    </div>
  );
};
