import React, { useState } from 'react';

interface TextInputFormProps {
  onSubmitPrompt: (text: string) => void;
  disabled?: boolean;
}

export const TextInputForm: React.FC<TextInputFormProps> = ({ onSubmitPrompt, disabled }) => {
  const [text, setText] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    setText('');
    onSubmitPrompt(trimmed);
  };

  return (
    <form className="text-input-form" id="chat-form" onSubmit={handleSubmit}>
      <label className="visually-hidden" htmlFor="prompt-input">
        Type your question
      </label>
      <input
        type="text"
        id="prompt-input"
        placeholder="Or type a question here…"
        autoComplete="off"
        value={text}
        onChange={e => setText(e.target.value)}
        disabled={disabled}
      />
      <button type="submit" disabled={disabled || !text.trim()}>
        Ask
      </button>
    </form>
  );
};
