package com.fja.ai.tinyrag.chat;

import com.fja.ai.tinyrag.chat.dto.AppendTurnRequest;
import com.fja.ai.tinyrag.chat.dto.ChatMessageDto;
import com.fja.ai.tinyrag.chat.dto.ChatSessionCreatedDto;
import com.fja.ai.tinyrag.chat.dto.CreateSessionRequest;
import com.fja.ai.tinyrag.chat.dto.SessionSummaryDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/sessions")
@Validated
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    public List<SessionSummaryDto> list() {
        return chatSessionService.listSessions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionCreatedDto create(@Valid @RequestBody CreateSessionRequest request) {
        return chatSessionService.createSession(request);
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageDto> messages(@PathVariable("id") Long id) {
        return chatSessionService.listMessages(id);
    }

    @PostMapping("/{id}/turns")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void appendTurn(@PathVariable("id") Long id, @Valid @RequestBody AppendTurnRequest request) {
        chatSessionService.appendTurn(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        chatSessionService.deleteSession(id);
    }
}
