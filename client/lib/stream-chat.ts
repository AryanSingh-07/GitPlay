import { getApiBaseUrl, ApiError, type ChatMessage } from "@/lib/api";

export type StreamChatHandlers = {
  onUserMessage?: (message: ChatMessage) => void;
  onToken?: (token: string) => void;
  onAssistantMessage?: (message: ChatMessage) => void;
  onDone?: () => void;
  onError?: (error: Error) => void;
  signal?: AbortSignal;
};

export async function streamChatMessage(
  sessionId: string,
  content: string,
  handlers: StreamChatHandlers = {}
): Promise<void> {
  const res = await fetch(
    `${getApiBaseUrl()}/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", Accept: "text/event-stream, application/json" },
      body: JSON.stringify({ content }),
      signal: handlers.signal,
    }
  );

  if (!res.ok) {
    let message = res.statusText;
    try {
      const data = await res.json();
      message = data.message ?? data.error ?? message;
    } catch {
      // Some failures have no JSON response body.
    }
    throw new ApiError(res.status, message);
  }

  if (!res.body) {
    throw new Error("No response body for SSE stream");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;

  function dispatch(part: string) {
    let event = "message";
    const dataLines: string[] = [];

    for (const line of part.split(/\r?\n/)) {
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).replace(/^ /, ""));
      }
    }

    if (event === "done") {
      completed = true;
      return;
    }

    const data = dataLines.join("\n");
    if (!data) return;

    if (event === "token") {
      const token: unknown = JSON.parse(data);
      if (typeof token !== "string") throw new Error("Invalid chat token");
      handlers.onToken?.(token);
    } else if (event === "user_message") {
      handlers.onUserMessage?.(JSON.parse(data) as ChatMessage);
    } else if (event === "assistant_message") {
      handlers.onAssistantMessage?.(JSON.parse(data) as ChatMessage);
    } else if (event === "error") {
      const error: unknown = JSON.parse(data);
      const message = typeof error === "string"
        ? error
        : error && typeof error === "object" && "message" in error
          ? String(error.message)
          : "Chat response failed";
      throw new Error(message);
    }
  }

  function drain() {
    let boundary: RegExpMatchArray | null;
    while (!completed && (boundary = buffer.match(/\r?\n\r?\n/))) {
      const index = boundary.index!;
      const part = buffer.slice(0, index);
      buffer = buffer.slice(index + boundary[0].length);
      dispatch(part);
    }
  }

  try {
    while (!completed) {
      const { done, value } = await reader.read();
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
      drain();
      if (done) {
        if (!completed && buffer.trim()) dispatch(buffer);
        break;
      }
    }
    if (!completed) throw new Error("Chat response was interrupted. Please try again.");
    handlers.onDone?.();
  } catch (error) {
    const failure = error instanceof Error ? error : new Error("Chat response failed");
    if (failure.name !== "AbortError") handlers.onError?.(failure);
    throw failure;
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
