import React, { useEffect, useRef } from 'react';
import { ChatMessage } from '../types';
import { AlertTriangle, Tag, Navigation } from 'lucide-react';

interface ConversationLogProps {
  messages: ChatMessage[];
}

export const ConversationLog: React.FC<ConversationLogProps> = ({ messages }) => {
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight;
    }
  }, [messages]);

  const renderSpatialBadges = (content: string) => {
    const badges: Array<{ label: string; type: 'ok' | 'warn' | 'alert' | 'info'; icon: any }> = [];
    const low = content.toLowerCase();

    if (low.includes("path") || low.includes("corridor") || low.includes("ahead") || low.includes("आगे") || low.includes("रास्ता") || low.includes("दीवार") || low.includes("wall") || low.includes("surface")) {
      const isBlocked = low.includes("blocked") || low.includes("बंद") || low.includes("रुका") || low.includes("obstacle") || low.includes("दीवार") || low.includes("wall");
      badges.push({
        label: isBlocked ? "Path: Blocked Ahead" : "Path: Open",
        type: isBlocked ? "warn" : "ok",
        icon: Navigation
      });
    }

    if (low.includes("hazard") || low.includes("steps") || low.includes("curb") || low.includes("सीढ़ियाँ") || low.includes("spill")) {
      const isClean = low.includes("no hazard") || low.includes("clear of") || low.includes("कोई खतरा नहीं");
      badges.push({
        label: isClean ? "Ground: Clear" : "Ground Hazard Alert",
        type: isClean ? "ok" : "alert",
        icon: AlertTriangle
      });
    }

    // Quoted text / OCR
    const quoteMatch = content.match(/"([^"]+)"/);
    if (quoteMatch && quoteMatch[1]) {
      badges.push({
        label: `Sign: "${quoteMatch[1]}"`,
        type: "info",
        icon: Tag
      });
    }

    if (!badges.length) return null;

    return (
      <div className="spatial-badges-row">
        {badges.map((b, idx) => {
          const Icon = b.icon;
          return (
            <span key={idx} className={`spatial-badge ${b.type}`}>
              <Icon size={13} aria-hidden="true" />
              <span>{b.label}</span>
            </span>
          );
        })}
      </div>
    );
  };

  return (
    <section
      className="card chat-log"
      id="chat-log"
      ref={logRef}
      role="log"
      aria-label="Conversation history"
      aria-live="off"
    >
      {messages.length === 0 ? (
        <div style={{ textAlign: 'center', color: 'var(--muted)', padding: '16px 0', fontSize: '0.95rem' }}>
          No conversations yet. Speak or type a command to begin.
        </div>
      ) : (
        messages.map(msg => (
          <div
            key={msg.id}
            className={`bubble ${msg.role === 'user' ? 'user' : 'bot'} ${msg.degraded ? 'degraded' : ''}`}
          >
            <span className="who">{msg.role === 'user' ? 'You' : 'BlindSight'}</span>
            <span>{msg.content}</span>
            {msg.role === 'assistant' ? renderSpatialBadges(msg.content) : null}
          </div>
        ))
      )}
    </section>
  );
};
