import React, { useState, useEffect } from 'react';

export const App: React.FC = () => {
  const [downloadUrl, setDownloadUrl] = useState<string>('/BlindSight-testing.apk');
  const [copied, setCopied] = useState<boolean>(false);
  const [downloadTriggered, setDownloadTriggered] = useState<boolean>(false);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const fullUrl = `${window.location.origin}/BlindSight-testing.apk`;
      setDownloadUrl(fullUrl);

      // Auto-trigger download if url has ?download=1
      if (window.location.search.includes('download=1')) {
        handleDownload();
      }
    }
  }, []);

  const qrImageUrl = `https://api.qrserver.com/v1/create-qr-code/?size=260x260&margin=12&data=${encodeURIComponent(downloadUrl)}`;

  const handleDownload = () => {
    setDownloadTriggered(true);
    const link = document.createElement('a');
    link.href = '/BlindSight-testing.apk';
    link.download = 'BlindSight-testing.apk';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => setDownloadTriggered(false), 4000);
  };

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(downloadUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      background: 'radial-gradient(ellipse at 50% 20%, #172554 0%, #05070c 70%)',
      color: '#f8fafc',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '32px 16px',
      fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }}>
      <main style={{
        maxWidth: '560px',
        width: '100%',
        background: 'rgba(15, 23, 42, 0.85)',
        backdropFilter: 'blur(16px)',
        border: '1px solid rgba(245, 158, 11, 0.35)',
        borderRadius: '24px',
        padding: '36px 28px',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px rgba(245, 158, 11, 0.15)',
        textAlign: 'center'
      }}>
        {/* App Badge & Icon */}
        <div style={{
          width: '76px',
          height: '76px',
          margin: '0 auto 16px',
          background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
          borderRadius: '20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 10px 25px rgba(245, 158, 11, 0.45)'
        }}>
          <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="#05070c" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </div>

        <h1 style={{
          margin: '0 0 6px',
          fontSize: '2rem',
          fontWeight: 800,
          letterSpacing: '-0.025em',
          background: 'linear-gradient(to right, #f8fafc, #f59e0b)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent'
        }}>
          BlindSight
        </h1>

        <p style={{
          margin: '0 0 20px',
          color: '#cbd5e1',
          fontSize: '0.98rem',
          lineHeight: '1.4'
        }}>
          AI Visual Assistant & Orientation Companion for Android
        </p>

        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '8px',
          padding: '4px 14px',
          background: 'rgba(245, 158, 11, 0.12)',
          border: '1px solid rgba(245, 158, 11, 0.3)',
          borderRadius: '999px',
          fontSize: '0.8rem',
          color: '#fbbf24',
          marginBottom: '26px'
        }}>
          <span style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: '#10b981',
            display: 'inline-block'
          }}></span>
          Testing Build Available • 22.5 MB • Android 8.0+
        </div>

        {/* Big Download Button */}
        <a
          href="/BlindSight-testing.apk"
          download="BlindSight-testing.apk"
          onClick={handleDownload}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '12px',
            width: '100%',
            padding: '16px 20px',
            background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
            color: '#05070c',
            fontWeight: 800,
            fontSize: '1.12rem',
            borderRadius: '16px',
            textDecoration: 'none',
            boxShadow: '0 10px 25px rgba(245, 158, 11, 0.35)',
            cursor: 'pointer',
            transition: 'transform 0.15s ease, box-shadow 0.15s ease',
            boxSizing: 'border-box'
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'translateY(-2px)';
            e.currentTarget.style.boxShadow = '0 14px 30px rgba(245, 158, 11, 0.45)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)';
            e.currentTarget.style.boxShadow = '0 10px 25px rgba(245, 158, 11, 0.35)';
          }}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="7 10 12 15 17 10" />
            <line x1="12" y1="15" x2="12" y2="3" />
          </svg>
          {downloadTriggered ? 'Downloading APK...' : 'Download BlindSight APK'}
        </a>

        {/* QR Code Section */}
        <div style={{
          marginTop: '28px',
          padding: '22px',
          background: 'rgba(2, 6, 23, 0.7)',
          border: '1px solid rgba(51, 65, 85, 0.7)',
          borderRadius: '18px'
        }}>
          <div style={{
            fontSize: '0.86rem',
            color: '#94a3b8',
            marginBottom: '14px',
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.05em'
          }}>
            Or Scan With Phone Camera
          </div>

          <div style={{
            display: 'inline-block',
            padding: '12px',
            background: '#ffffff',
            borderRadius: '16px',
            boxShadow: '0 8px 20px rgba(0, 0, 0, 0.5)'
          }}>
            <img
              src={qrImageUrl}
              alt="Scan QR code to download BlindSight APK"
              width={200}
              height={200}
              style={{ display: 'block', borderRadius: '6px' }}
            />
          </div>

          <p style={{
            margin: '14px 0 0',
            fontSize: '0.84rem',
            color: '#94a3b8',
            lineHeight: '1.4'
          }}>
            Point your Android phone camera at the QR code to download directly to your mobile device.
          </p>
        </div>

        {/* Direct Link Copy */}
        <div style={{
          marginTop: '20px',
          display: 'flex',
          gap: '8px'
        }}>
          <input
            type="text"
            readOnly
            value={downloadUrl}
            style={{
              flex: 1,
              padding: '10px 14px',
              background: 'rgba(15, 23, 42, 0.9)',
              border: '1px solid #334155',
              borderRadius: '12px',
              color: '#94a3b8',
              fontSize: '0.82rem',
              outline: 'none'
            }}
          />
          <button
            onClick={handleCopyLink}
            style={{
              padding: '10px 18px',
              background: copied ? '#10b981' : '#1e293b',
              color: '#f8fafc',
              border: '1px solid #334155',
              borderRadius: '12px',
              fontSize: '0.82rem',
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              whiteSpace: 'nowrap'
            }}
          >
            {copied ? '✓ Copied' : 'Copy Link'}
          </button>
        </div>

        {/* Installation Instructions */}
        <div style={{
          marginTop: '28px',
          textAlign: 'left',
          background: 'rgba(15, 23, 42, 0.5)',
          border: '1px solid #1e293b',
          borderRadius: '16px',
          padding: '18px'
        }}>
          <div style={{
            fontSize: '0.86rem',
            fontWeight: 700,
            color: '#f8fafc',
            marginBottom: '10px'
          }}>
            Quick Android Install Guide:
          </div>
          <ol style={{
            margin: 0,
            paddingLeft: '20px',
            fontSize: '0.82rem',
            color: '#cbd5e1',
            lineHeight: '1.6'
          }}>
            <li>Tap <strong>Download BlindSight APK</strong> (or scan the QR code).</li>
            <li>If Chrome prompts <em>"File might be harmful"</em>, tap <strong>Download anyway</strong>.</li>
            <li>Tap the downloaded file and select <strong>Install</strong> (enable <em>"Allow from this source"</em> if prompted).</li>
            <li>Open BlindSight, grant Microphone/Camera permissions, and speak!</li>
          </ol>
        </div>
      </main>

      <footer style={{
        marginTop: '24px',
        color: '#64748b',
        fontSize: '0.82rem',
        textAlign: 'center'
      }}>
        BlindSight Open Source Assistant •{' '}
        <a
          href="https://github.com/devrift/BlindSight"
          target="_blank"
          rel="noopener noreferrer"
          style={{ color: '#f59e0b', textDecoration: 'none' }}
        >
          GitHub Repository
        </a>
      </footer>
    </div>
  );
};
