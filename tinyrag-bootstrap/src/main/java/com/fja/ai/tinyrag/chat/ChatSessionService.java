package com.fja.ai.tinyrag.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fja.ai.tinyrag.chat.dto.AppendTurnRequest;
import com.fja.ai.tinyrag.chat.dto.ChatMessageDto;
import com.fja.ai.tinyrag.chat.dto.ChatSessionCreatedDto;
import com.fja.ai.tinyrag.chat.dto.CreateSessionRequest;
import com.fja.ai.tinyrag.chat.dto.SessionSummaryDto;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /** 不依赖容器中的 ObjectMapper Bean（部分环境下未自动注册） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatSessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public List<SessionSummaryDto> listSessions() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(s -> new SessionSummaryDto(
                        s.getId(),
                        s.getTitle(),
                        s.getKb(),
                        s.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ChatSessionCreatedDto createSession(CreateSessionRequest req) {
        ChatSession s = new ChatSession();
        s.setTitle(req.getTitle().trim());
        s.setKb(StringUtils.hasText(req.getKb()) ? req.getKb().trim() : "default");
        sessionRepository.save(s);
        return new ChatSessionCreatedDto(s.getId(), s.getTitle(), s.getKb());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> listMessages(Long sessionId) {
        ensureSession(sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(m -> new ChatMessageDto(
                        m.getId(),
                        m.getRole().name(),
                        m.getContent(),
                        m.getMetadataJson(),
                        m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void appendTurn(Long sessionId, AppendTurnRequest req) {
        ChatSession session = ensureSession(sessionId);

        ChatMessage user = new ChatMessage();
        user.setSession(session);
        user.setRole(MessageRole.USER);
        user.setContent(req.getQuestion().trim());
        messageRepository.save(user);

        ChatMessage assistant = new ChatMessage();
        assistant.setSession(session);
        assistant.setRole(MessageRole.ASSISTANT);
        assistant.setContent(req.getAnswer() != null ? req.getAnswer() : "");
        assistant.setMetadataJson(buildMetadataJson(req.getRewrittenQuestion(), req.getReferences(), req.getChunks()));
        messageRepository.save(assistant);

        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession s = sessionRepository.findById(sessionId).orElse(null);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        sessionRepository.delete(s);
    }

    private ChatSession ensureSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    private String buildMetadataJson(String rewritten, List<String> references, List<Map<String, String>> chunks) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.hasText(rewritten)) {
            map.put("rewritten", rewritten);
        }
        if (references != null && !references.isEmpty()) {
            map.put("refs", references);
        }
        if (chunks != null && !chunks.isEmpty()) {
            map.put("chunks", chunks);
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
