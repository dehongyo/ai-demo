import { DEMO_USER_ID } from "../types/api";

const BASE_URL = "/api";

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: HeadersInit = {
    "X-User-Id": DEMO_USER_ID,
    ...((options.headers as Record<string, string>) || {}),
  };

  if (options.body && typeof options.body === "string") {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({
      message: response.statusText,
    }));
    throw new Error(error.message || `HTTP ${response.status}`);
  }

  return response.json();
}

export function apiStream(_path: string, _body: unknown): EventSource {
  // SSE via fetch with streaming is complex, use EventSource pattern via POST
  // For now, use the JSON endpoint; SSE for future enhancement
  throw new Error("Use apiRequest for sync, SSE via fetch for streaming");
}
