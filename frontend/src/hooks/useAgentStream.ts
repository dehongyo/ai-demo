import { useState, useCallback } from "react";
import { sendMessage } from "../api/runs";
import { getMessages } from "../api/sessions";
import type { ChatMessage } from "../types/api";

interface UseAgentStreamReturn {
  messages: ChatMessage[];
  loading: boolean;
  error: string | null;
  loadMessages: (sessionId: string) => Promise<void>;
  send: (sessionId: string, content: string) => Promise<void>;
}

export function useAgentStream(): UseAgentStreamReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadMessages = useCallback(async (sessionId: string) => {
    try {
      setError(null);
      const msgs = await getMessages(sessionId);
      setMessages(msgs);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load messages");
    }
  }, []);

  const send = useCallback(async (sessionId: string, content: string) => {
    setLoading(true);
    setError(null);

    // Add optimistic user message
    const optimisticId = `opt-${Date.now()}`;
    const optimisticMsg: ChatMessage = {
      id: optimisticId,
      role: "USER",
      content,
      sequenceNo: messages.length + 1,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimisticMsg]);

    try {
      await sendMessage(sessionId, content);

      // Remove optimistic message and reload all messages
      const msgs = await getMessages(sessionId);
      setMessages(msgs);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to send message");
      // Remove optimistic message on error
      setMessages((prev) => prev.filter((m) => m.id !== optimisticId));
    } finally {
      setLoading(false);
    }
  }, [messages.length]);

  return { messages, loading, error, loadMessages, send };
}
