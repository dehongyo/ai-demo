export const DEMO_USER_ID = "11111111-1111-1111-1111-111111111111";

export interface ChatSession {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT" | "ASSISTANT_TOOL_CALL" | "TOOL";
  content: string;
  toolName?: string;
  toolCallId?: string;
  toolCallsJson?: string;
  sequenceNo: number;
  createdAt: string;
}

export interface AgentRunResponse {
  runId: string;
  messageId: string;
  answer: string;
  status: "COMPLETED" | "MAX_STEPS" | "ERROR";
}

export interface ToolInfo {
  name: string;
  description: string;
  parametersSchema: Record<string, unknown>;
}

export interface SSERunStarted {
  sessionId: string;
}

export interface SSERunFinished {
  runId: string;
  status: string;
}

export interface SSEDecision {
  stepNumber: number;
  decisionType: string;
  decisionSummary: string;
}

export interface SSEToolStarted {
  stepNumber: number;
  toolName: string;
  arguments: Record<string, unknown>;
}

export interface SSEToolFinished {
  stepNumber: number;
  toolName: string;
  success: boolean;
  durationMs: number;
  result: string;
}

export interface SSEAnswerDelta {
  content: string;
}

export interface SSEError {
  message: string;
}

export interface TodoItem {
  id: string;
  userId: string;
  sessionId: string;
  content: string;
  status: string;
  createdAt: string;
  completedAt: string | null;
}
