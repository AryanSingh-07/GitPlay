"use client";

import { useState, type FormEvent } from "react";
import { Send, Square } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

export function ChatComposer({
  disabled = false,
  streaming = false,
  onSend,
  onStop,
}: {
  disabled?: boolean;
  streaming?: boolean;
  onSend: (content: string) => void | Promise<void>;
  onStop: () => void;
}) {
  const [content, setContent] = useState("");

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || streaming || !content.trim()) return;
    void onSend(content.trim());
    setContent("");
  }

  return (
    <form onSubmit={handleSubmit} className="border-t bg-background p-4">
      <div className="mx-auto flex max-w-3xl items-end gap-2">
        <Textarea
          aria-label="Ask about this repository"
          placeholder="Ask about this repository…"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          disabled={disabled || streaming}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
          className="max-h-48 min-h-12 resize-none"
        />
        {streaming ? (
          <Button type="button" variant="outline" size="icon" onClick={onStop} aria-label="Stop response">
            <Square className="size-4" />
          </Button>
        ) : (
          <Button type="submit" size="icon" disabled={disabled || !content.trim()} aria-label="Send message">
            <Send className="size-4" />
          </Button>
        )}
      </div>
      <p className="mx-auto mt-2 max-w-3xl text-xs text-muted-foreground">Enter to send · Shift+Enter for a new line</p>
    </form>
  );
}
