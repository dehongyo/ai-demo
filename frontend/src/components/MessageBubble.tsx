import type { ChatMessage } from "../types/api";
import ToolExecutionCard from "./ToolExecutionCard";

interface Props {
  message: ChatMessage;
}

export default function MessageBubble({ message }: Props) {
  if (message.role === "ASSISTANT_TOOL_CALL") {
    return (
      <div className="message-tool-call">
        <span className="tool-call-badge">🔧 调用工具</span>
        {message.toolCallsJson && (
          <ToolExecutionCard toolCallsJson={message.toolCallsJson} />
        )}
      </div>
    );
  }

  if (message.role === "TOOL") {
    return (
      <div className="message-tool-result">
        <span className="tool-result-badge">📋 {message.toolName}</span>
        <pre className="tool-result-content">{message.content}</pre>
      </div>
    );
  }

  const isUser = message.role === "USER";
  return (
    <div className={`message-bubble ${isUser ? "user" : "assistant"}`}>
      <div className="message-role">{isUser ? "你" : "Agent"}</div>
      <div className="message-content">{message.content}</div>
    </div>
  );
}
