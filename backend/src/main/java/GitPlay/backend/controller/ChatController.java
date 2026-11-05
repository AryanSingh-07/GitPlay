package GitPlay.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import GitPlay.backend.dto.ChatMessageRequest;
import GitPlay.backend.dto.ChatMessageResponse;
import GitPlay.backend.dto.ChatSessionResponse;
import GitPlay.backend.dto.CreateChatSessionRequest;
import GitPlay.backend.security.CurrentUser;
import GitPlay.backend.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatController {
    private final CurrentUser currentUser;
    private final ChatService chatService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse create(@Valid @RequestBody CreateChatSessionRequest request) {
        return chatService.createSession(currentUser.require().getId(), request);
    }

    @GetMapping
    public List<ChatSessionResponse> list(@RequestParam UUID repositoryId) {
        return chatService.listSessions(currentUser.require().getId(), repositoryId);
    }

    @GetMapping("/{id}")
    public List<ChatMessageResponse> messages(@PathVariable UUID id) {
        return chatService.getMessages(currentUser.require().getId(), id);
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reply(@PathVariable UUID id, @Valid @RequestBody ChatMessageRequest request) {
        return chatService.streamReply(currentUser.require().getId(), id, request.content());
    }
}
