import { useState, useRef, useEffect } from "react";
import type { ChatMessage } from "../types/api";
import MessageBubble from "./MessageBubble";

interface Props {
  messages: ChatMessage[];
  onSend: (content: string) => Promise<void>;
  loading: boolean;
  title: string;
}

export default function ChatPanel({ messages, onSend, loading, title }: Props) {
  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || loading) return;
    const content = input;
    setInput("");
    await onSend(content);
  };

  return (
    <div className="chat-panel">
      <header className="chat-header">
        <h2>{title}</h2>
      </header>
      <div className="chat-messages">
        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {loading && <div className="loading-indicator">Agent 思考中...</div>}
        <div ref={endRef} />
      </div>
      <form className="chat-input-area" onSubmit={handleSubmit}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入消息..."
          disabled={loading}
          maxLength={8000}
        />
        <button type="submit" disabled={loading || !input.trim()}>
          发送
        </button>
      </form>
    </div>
  );
}
