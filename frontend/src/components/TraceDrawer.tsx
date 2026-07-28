import type { ChatMessage } from "../types/api";

interface Props {
  messages: ChatMessage[];
  isOpen: boolean;
  onClose: () => void;
}

export default function TraceDrawer({ messages, isOpen, onClose }: Props) {
  if (!isOpen) return null;

  const toolMessages = messages.filter(
    (m) => m.role === "ASSISTANT_TOOL_CALL" || m.role === "TOOL",
  );

  return (
    <aside className="trace-drawer">
      <div className="trace-header">
        <h3>执行 Trace</h3>
        <button onClick={onClose} className="btn-close">
          ✕
        </button>
      </div>
      <div className="trace-content">
        {toolMessages.length === 0 && (
          <p className="trace-empty">暂无工具调用记录</p>
        )}
        {toolMessages.map((msg, i) => (
          <div key={msg.id} className="trace-step">
            <div className="trace-step-header">
              Step {Math.floor(i / 2) + 1}: {msg.role === "ASSISTANT_TOOL_CALL" ? "决策" : "结果"}
            </div>
            <div className="trace-step-detail">
              {msg.role === "ASSISTANT_TOOL_CALL" && msg.toolCallsJson && (
                <pre className="trace-json">{msg.toolCallsJson}</pre>
              )}
              {msg.role === "TOOL" && (
                <div className="trace-tool-result">
                  <span className="trace-tool-name">🔧 {msg.toolName}</span>
                  <pre className="trace-content-text">{msg.content}</pre>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </aside>
  );
}
