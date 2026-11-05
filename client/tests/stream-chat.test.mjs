import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

const source = readFileSync(new URL("../lib/stream-chat.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});

function chatStream(response) {
  const exports = {};
  runInNewContext(outputText, {
    exports,
    Error,
    JSON,
    TextDecoder,
    fetch: async () => response,
    require: () => ({
      getApiBaseUrl: () => "http://localhost:8080",
      ApiError: class extends Error {
        constructor(status, message) {
          super(message);
          this.status = status;
        }
      },
    }),
  });
  return exports.streamChatMessage;
}

function responseFor(text, splitEveryByte = false) {
  const encoded = new TextEncoder().encode(text);
  return new Response(new ReadableStream({
    start(controller) {
      if (splitEveryByte) {
        for (const byte of encoded) controller.enqueue(Uint8Array.of(byte));
      } else {
        controller.enqueue(encoded);
      }
      controller.close();
    },
  }));
}

test("decodes UTF-8 and CRLF events split across chunks, and flushes final done", async () => {
  const tokens = [];
  let doneCount = 0;
  await chatStream(responseFor('event: token\r\ndata: "Hello 👋"\r\n\r\nevent: done\r\ndata: true', true))("session", "Hi", {
    onToken: (token) => tokens.push(token),
    onDone: () => doneCount++,
  });
  assert.deepEqual(tokens, ["Hello 👋"]);
  assert.equal(doneCount, 1);
});

test("dispatches saved user and assistant messages", async () => {
  const messages = [];
  await chatStream(responseFor('event: user_message\ndata: {"id":"user"}\n\nevent: assistant_message\ndata: {"id":"assistant"}\n\nevent: done\n\n'))("session", "Hi", {
    onUserMessage: (message) => messages.push(message.id),
    onAssistantMessage: (message) => messages.push(message.id),
  });
  assert.deepEqual(messages, ["user", "assistant"]);
});

test("reports a server error and never signals success", async () => {
  let doneCount = 0;
  let errorCount = 0;
  await assert.rejects(chatStream(responseFor('event: error\ndata: {"message":"AI unavailable"}\n\n'))("session", "Hi", {
    onDone: () => doneCount++,
    onError: () => errorCount++,
  }), /AI unavailable/);
  assert.equal(doneCount, 0);
  assert.equal(errorCount, 1);
});

test("rejects an interrupted response after flushing an undelimited token", async () => {
  const tokens = [];
  let doneCount = 0;
  await assert.rejects(chatStream(responseFor('event: token\ndata: "partial"'))("session", "Hi", {
    onToken: (token) => tokens.push(token),
    onDone: () => doneCount++,
  }), /interrupted/);
  assert.deepEqual(tokens, ["partial"]);
  assert.equal(doneCount, 0);
});

test("rejects malformed JSON instead of hiding parsing failures", async () => {
  await assert.rejects(chatStream(responseFor('event: token\ndata: invalid\n\nevent: done\n\n'))("session", "Hi"), SyntaxError);
});

test("ignores events after done and signals completion once", async () => {
  let doneCount = 0;
  const tokens = [];
  await chatStream(responseFor('event: done\n\nevent: done\n\nevent: token\ndata: "late"\n\n'))("session", "Hi", {
    onDone: () => doneCount++,
    onToken: (token) => tokens.push(token),
  });
  assert.equal(doneCount, 1);
  assert.deepEqual(tokens, []);
});

test("preserves API error status and message", async () => {
  await assert.rejects(chatStream(new Response(JSON.stringify({ message: "Session not found" }), {
    status: 404,
  }))("missing", "Hi"), (error) => error.status === 404 && error.message === "Session not found");
});
