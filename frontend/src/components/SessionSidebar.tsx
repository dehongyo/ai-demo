import type { ChatSession } from "../types/api";

interface Props {
  sessions: ChatSession[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onCreate: (title: string) => void;
  loading: boolean;
}

export default function SessionSidebar({
  sessions,
  activeId,
  onSelect,
  onCreate,
  loading,
}: Props) {
  const handleCreate = () => {
    const title = prompt("会话名称:");
    if (title?.trim()) {
      onCreate(title.trim());
    }
  };

  return (
    <aside className="session-sidebar">
      <div className="sidebar-header">
        <h2>会话列表</h2>
        <button onClick={handleCreate} disabled={loading} className="btn-new">
          + 新建
        </button>
      </div>
      <ul className="session-list">
        {sessions.map((s) => (
          <li
            key={s.id}
            className={`session-item ${s.id === activeId ? "active" : ""}`}
            onClick={() => onSelect(s.id)}
          >
            <span className="session-title">{s.title}</span>
            <span className="session-date">
              {new Date(s.updatedAt).toLocaleDateString()}
            </span>
          </li>
        ))}
        {sessions.length === 0 && !loading && (
          <li className="session-empty">暂无会话，点击"+ 新建"创建</li>
        )}
      </ul>
    </aside>
  );
}
