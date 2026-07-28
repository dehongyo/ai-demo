import { useMemo } from "react";

interface Props {
  toolCallsJson: string;
  success?: boolean;
  durationMs?: number;
  result?: string;
}

export default function ToolExecutionCard({
  toolCallsJson,
  success,
  durationMs,
  result,
}: Props) {
  const calls = useMemo(() => {
    try {
      return JSON.parse(toolCallsJson) as Array<{
        id: string;
        function: { name: string; arguments: string };
      }>;
    } catch {
      return [];
    }
  }, [toolCallsJson]);

  if (calls.length === 0) return null;

  return (
    <div className={`tool-execution ${success === false ? "error" : ""}`}>
      {calls.map((call, i) => {
        let args: Record<string, unknown> = {};
        try {
          args = JSON.parse(call.function.arguments);
        } catch {
          // ignore parse errors
        }
        return (
          <div key={call.id || i} className="tool-call-detail">
            <span className="tool-name">{call.function.name}</span>
            <pre className="tool-args">
              {JSON.stringify(args, null, 2)}
            </pre>
            {durationMs !== undefined && (
              <span className="tool-duration">{durationMs}ms</span>
            )}
            {success !== undefined && (
              <span className={`tool-status ${success ? "ok" : "fail"}`}>
                {success ? "✓" : "✗"}
              </span>
            )}
            {result && (
              <pre className="tool-result-preview">
                {result.substring(0, 200)}
                {result.length > 200 ? "..." : ""}
              </pre>
            )}
          </div>
        );
      })}
    </div>
  );
}
