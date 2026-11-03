import React, { useEffect, useRef } from 'react';
import { ChatMessage } from '../types';

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
          </div>
        ))
      )}
    </section>
  );
};
