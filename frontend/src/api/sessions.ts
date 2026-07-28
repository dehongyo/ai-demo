import { apiRequest } from "./client";
import type { ChatSession, ChatMessage } from "../types/api";

export async function createSession(title: string): Promise<ChatSession> {
  return apiRequest<ChatSession>("/sessions", {
    method: "POST",
    body: JSON.stringify({ title }),
  });
}

export async function listSessions(): Promise<ChatSession[]> {
  return apiRequest<ChatSession[]>("/sessions");
}

export async function getSession(sessionId: string): Promise<ChatSession> {
  return apiRequest<ChatSession>(`/sessions/${sessionId}`);
}

export async function getMessages(sessionId: string): Promise<ChatMessage[]> {
  return apiRequest<ChatMessage[]>(`/sessions/${sessionId}/messages`);
}

export async function getTodos(sessionId: string) {
  return apiRequest(`/sessions/${sessionId}/todos`);
}
