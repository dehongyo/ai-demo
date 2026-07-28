import { apiRequest } from "./client";
import type { AgentRunResponse, ToolInfo } from "../types/api";

export async function sendMessage(
  sessionId: string,
  content: string,
): Promise<AgentRunResponse> {
  return apiRequest<AgentRunResponse>(`/sessions/${sessionId}/messages`, {
    method: "POST",
    body: JSON.stringify({ content }),
  });
}

export async function getTools(): Promise<ToolInfo[]> {
  return apiRequest<ToolInfo[]>("/tools");
}
