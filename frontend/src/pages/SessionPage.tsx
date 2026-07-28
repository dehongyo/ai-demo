import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { listSessions, createSession, getSession } from "../api/sessions";
import { useAgentStream } from "../hooks/useAgentStream";
import SessionSidebar from "../components/SessionSidebar";
import ChatPanel from "../components/ChatPanel";
import TraceDrawer from "../components/TraceDrawer";
import type { ChatSession } from "../types/api";

export default function SessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [sidebarLoading, setSidebarLoading] = useState(false);
  const [traceOpen, setTraceOpen] = useState(false);
  const [currentTitle, setCurrentTitle] = useState("");

  const { messages, loading, error, loadMessages, send } = useAgentStream();

  // Load session list
  const loadSessions = useCallback(async () => {
    setSidebarLoading(true);
    try {
      const list = await listSessions();
      setSessions(list);
    } catch {
      // ignore
    } finally {
      setSidebarLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  // Load session details and messages when sessionId changes
  useEffect(() => {
    if (sessionId) {
      getSession(sessionId)
        .then((s) => setCurrentTitle(s.title))
        .catch(() => setCurrentTitle(""));
      loadMessages(sessionId);
    }
  }, [sessionId, loadMessages]);

  const handleSelectSession = (id: string) => {
    navigate(`/sessions/${id}`);
  };

  const handleCreateSession = async (title: string) => {
    try {
      const session = await createSession(title);
      await loadSessions();
      navigate(`/sessions/${session.id}`);
    } catch {
      // ignore
    }
  };

  const handleSend = async (content: string) => {
    if (!sessionId) return;
    await send(sessionId, content);
  };

  if (!sessionId) {
    return (
      <div className="app-layout">
        <SessionSidebar
          sessions={sessions}
          activeId={null}
          onSelect={handleSelectSession}
          onCreate={handleCreateSession}
          loading={sidebarLoading}
        />
        <main className="chat-panel">
          <div className="welcome-message">
            <h2>Minimal Agent</h2>
            <p>选择一个会话或创建新会话开始对话</p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app-layout">
      <SessionSidebar
        sessions={sessions}
        activeId={sessionId}
        onSelect={handleSelectSession}
        onCreate={handleCreateSession}
        loading={sidebarLoading}
      />
      <ChatPanel
        messages={messages}
        onSend={handleSend}
        loading={loading}
        title={currentTitle || "加载中..."}
      />
      <button
        onClick={() => setTraceOpen(!traceOpen)}
        className="btn-trace-toggle"
      >
        {traceOpen ? "关闭 Trace" : "Trace"}
      </button>
      <TraceDrawer
        messages={messages}
        isOpen={traceOpen}
        onClose={() => setTraceOpen(false)}
      />
      {error && <div className="error-toast">{error}</div>}
    </div>
  );
}
