package com.fja.ai.tinyrag.service;

import com.fja.ai.tinyrag.chat.ChatMessage;
import com.fja.ai.tinyrag.chat.ChatMessageRepository;
import com.fja.ai.tinyrag.chat.MessageRole;
import com.fja.ai.tinyrag.config.RAGProperties;
import com.fja.ai.tinyrag.model.RAGRequest;
import com.fja.ai.tinyrag.service.RerankService.RerankItem;
import com.fja.ai.tinyrag.service.query.QueryRouter;
import com.fja.ai.tinyrag.service.query.QueryRoutingDecision;
import com.fja.ai.tinyrag.service.web.WebSearchService;
import com.fja.ai.tinyrag.service.web.WebSearchService.WebResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class RAGService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RAGProperties ragProperties;
    private final RerankService rerankService;
    private final TaskExecutor taskExecutor;
    private final QueryRouter queryRouter;
    private final WebSearchService webSearchService;
    private final ChatMessageRepository chatMessageRepository;
    private final Resource rewriteSystemPrompt;
    private final Resource rewriteUserPrompt;
    private final Resource answerSystemPrompt;
    private final Resource answerUserPrompt;

    public RAGService(ChatClient chatClient,
                      VectorStore vectorStore,
                      RAGProperties ragProperties,
                      RerankService rerankService,
                      @Qualifier("RAGTaskExecutor") TaskExecutor taskExecutor,
                      QueryRouter queryRouter,
                      WebSearchService webSearchService,
                      ChatMessageRepository chatMessageRepository,
                      @Value("classpath:/prompts/rewrite-system.st") Resource rewriteSystemPrompt,
                      @Value("classpath:/prompts/rewrite-user.st") Resource rewriteUserPrompt,
                      @Value("classpath:/prompts/answer-system.st") Resource answerSystemPrompt,
                      @Value("classpath:/prompts/answer-user.st") Resource answerUserPrompt) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.rerankService = rerankService;
        this.taskExecutor = taskExecutor;
        this.queryRouter = queryRouter;
        this.webSearchService = webSearchService;
        this.chatMessageRepository = chatMessageRepository;
        this.rewriteSystemPrompt = rewriteSystemPrompt;
        this.rewriteUserPrompt = rewriteUserPrompt;
        this.answerSystemPrompt = answerSystemPrompt;
        this.answerUserPrompt = answerUserPrompt;
    }

    public SseEmitter streamChat(RAGRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        taskExecutor.execute(() -> {
            try {
                RAGExecutionContext context = prepareContext(request);
                sendEvent(emitter, "meta", Map.of(
                        "rewrittenQuestion", context.rewrittenQuestion(),
                        "routeReason", context.routeReason(),
                        "usedWebSearch", context.usedWebSearch(),
                        "ragDocsCount", context.ragDocsCount(),
                        "webResultsCount", context.webResultsCount(),
                        "webFallbackReason", context.webFallbackReason(),
                        "historyTurns", context.historyTurns()
                ));
                Map<String, Object> refsPayload = new LinkedHashMap<>();
                refsPayload.put("references", context.references());
                refsPayload.put("chunks", refChunksFromDocs(context.rerankedDocs()));
                sendEvent(emitter, "refs", refsPayload);

                streamAnswer(request.getQuestion(), context.rerankedDocs(), context.historyText(),
                        token -> sendEvent(emitter, "token", token));

                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", ex.getMessage() == null ? "stream error" : ex.getMessage());
                } catch (Exception ignored) {
                }
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    public String rewriteQuestion(String originalQuestion) {
        String rewrite = chatClient.prompt()
                .system(system -> system.text(rewriteSystemPrompt))
                .user(u -> u.text(rewriteUserPrompt)
                        .param("question", originalQuestion))
                .call()
                .content();

        if (rewrite == null || rewrite.isBlank()) {
            return originalQuestion;
        }
        return rewrite.trim();
    }

    public List<Document> retrieveCandidates(String rewrittenQuestion, String kb, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(rewrittenQuestion)
                .topK(topK)
                .similarityThresholdAll();

        if (kb != null && !kb.isBlank()) {
            builder.filterExpression("kb == '" + escapeForFilter(kb) + "'");
        }

        return vectorStore.similaritySearch(builder.build());
    }

    public List<Document> rerank(String originalQuestion, List<Document> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int safeTopN = Math.max(1, topN);
        List<Document> validCandidates = new ArrayList<>();
        List<String> candidateTexts = new ArrayList<>();
        for (Document candidate : candidates) {
            String text = safeText(candidate);
            if (text.isBlank()) {
                continue;
            }
            validCandidates.add(candidate);
            candidateTexts.add(truncate(text, ragProperties.getRerankMaxDocumentChars()));
        }

        if (validCandidates.isEmpty()) {
            return List.of();
        }

        try {
            List<RerankItem> rerankResults = rerankService.rerank(originalQuestion, candidateTexts, safeTopN);
            List<Document> reranked = pickByRerankResults(validCandidates, rerankResults, safeTopN);
            if (!reranked.isEmpty()) {
                return reranked;
            }
        } catch (Exception ex) {
            log.warn("Rerank failed, fallback to vector score. reason={}", ex.getMessage());
        }

        return fallbackByVectorScore(validCandidates, safeTopN);
    }

    public void streamAnswer(String question, List<Document> rerankedDocs, String historyText, Consumer<String> tokenConsumer) {
        String context = buildContext(rerankedDocs);
        chatClient.prompt()
                .system(system -> system.text(answerSystemPrompt))
                .user(u -> u.text(answerUserPrompt)
                        .param("question", question)
                        .param("history", historyText == null || historyText.isBlank() ? "(无历史对话)" : historyText)
                        .param("context", context))
                .stream()
                .content()
                .doOnNext(tokenConsumer)
                .blockLast();
    }

    public List<String> references(List<Document> docs) {
        return docs.stream()
                .map(this::referenceFromMetadata)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** SSE / 前端展示：每条命中的来源标签与正文片段 */
    private List<Map<String, String>> refChunksFromDocs(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, String> row = new LinkedHashMap<>();
            String src = referenceFromMetadata(doc);
            row.put("source", src != null ? src : "");
            row.put("text", truncate(safeText(doc), 4000));
            out.add(row);
        }
        return out;
    }

    private RAGExecutionContext prepareContext(RAGRequest request) {
        String originalQuestion = request.getQuestion();
        String rewritten = rewriteQuestion(originalQuestion);
        String kb = request.getKb();
        Long sessionId = request.getSessionId();
        String historyText = buildHistoryText(sessionId);
        int historyTurns = countHistoryTurns(sessionId);

        QueryRoutingDecision routing = queryRouter.route(originalQuestion);

        List<Document> reranked = List.of();
        int ragDocsCount = 0;
        if (routing.useRag()) {
            List<Document> candidates = retrieveCandidates(rewritten, kb, ragProperties.getRetrieveTopK());
            reranked = rerank(originalQuestion, candidates, ragProperties.getRerankTopN());
            ragDocsCount = reranked == null ? 0 : reranked.size();
        }

        boolean needWebFallback = shouldWebFallback(routing, reranked);
        boolean usedWeb = false;
        int webCount = 0;
        String webReason = needWebFallback ? "triggered-by-policy" : "not-triggered";
        if (needWebFallback) {
            log.info("RAG: web fallback triggered. routeReason={}, allowWeb={}, allowAutoOnEmpty={}, ragDocsCount={}",
                    routing == null ? null : routing.reason(),
                    routing != null && routing.allowWeb(),
                    Boolean.TRUE.equals(ragProperties.getAllowWebFallbackOnEmptyRag()),
                    ragDocsCount);
            List<WebResult> web = webSearchService.search(rewritten, ragProperties.getWebSearchMaxResults());
            if (web != null && !web.isEmpty()) {
                usedWeb = true;
                webCount = web.size();
                List<Document> webDocs = webResultsToDocs(web);
                // 若 RAG 有命中但质量一般，则把联网结果作为补充；若无命中，则直接用联网结果
                if (reranked == null || reranked.isEmpty()) {
                    reranked = webDocs;
                } else {
                    List<Document> merged = new ArrayList<>(reranked.size() + webDocs.size());
                    merged.addAll(reranked);
                    merged.addAll(webDocs);
                    reranked = merged;
                }
            } else {
                webReason = "web-search-returned-empty";
                log.warn("RAG: web fallback executed but returned empty results. rewrittenQuestion='{}'", rewritten);
            }
        }

        List<String> refs = references(reranked);
        return new RAGExecutionContext(rewritten, reranked, refs,
                routing == null ? null : routing.reason(),
                usedWeb,
                ragDocsCount,
                webCount,
                webReason,
                historyText,
                historyTurns
        );
    }

    private String buildHistoryText(Long sessionId) {
        if (sessionId == null) {
            return "(无历史对话)";
        }
        List<ChatMessage> latest = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        if (latest == null || latest.isEmpty()) {
            return "(无历史对话)";
        }
        List<ChatMessage> ordered = new ArrayList<>(latest);
        Collections.reverse(ordered);
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : ordered) {
            if (m == null || m.getRole() == null) {
                continue;
            }
            String role = m.getRole() == MessageRole.USER ? "用户" : "助手";
            String content = truncate(safeMsgText(m.getContent()), 1500);
            if (content.isBlank()) {
                continue;
            }
            sb.append(role).append("：").append(content).append("\n");
        }
        String text = sb.toString().trim();
        return text.isBlank() ? "(无历史对话)" : text;
    }

    private int countHistoryTurns(Long sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ChatMessage> latest = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        if (latest == null || latest.isEmpty()) {
            return 0;
        }
        long userCount = latest.stream().filter(m -> m != null && m.getRole() == MessageRole.USER).count();
        long assistantCount = latest.stream().filter(m -> m != null && m.getRole() == MessageRole.ASSISTANT).count();
        return (int) Math.min(userCount, assistantCount);
    }

    private String safeMsgText(String content) {
        return content == null ? "" : content.trim();
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 发送失败", ex);
        }
    }

    private List<Document> pickByRerankResults(List<Document> candidates, List<RerankItem> results, int topN) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<Document> picked = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (RerankItem result : results) {
            int index = result.index();
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            if (!seen.add(index)) {
                continue;
            }
            picked.add(candidates.get(index));
            if (picked.size() >= topN) {
                break;
            }
        }
        return picked;
    }

    private List<Document> fallbackByVectorScore(List<Document> candidates, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(this::vectorScore).reversed())
                .limit(topN)
                .toList();
    }

    private double vectorScore(Document document) {
        if (document == null || document.getScore() == null) {
            return 0.0;
        }
        return document.getScore();
    }

    private String buildContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "(无可用知识片段)";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            builder.append("[片段").append(i + 1).append("]\n");
            builder.append(safeText(doc)).append("\n");
            String ref = referenceFromMetadata(doc);
            if (ref != null) {
                builder.append("来源：").append(ref).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String referenceFromMetadata(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata.isEmpty()) {
            return null;
        }

        Object source = metadata.get("source");
        if (source == null) {
            source = metadata.get("filename");
        }
        if (source == null) {
            source = metadata.get("file_name");
        }

        Object chunkIndex = metadata.get("chunk_index");
        if (source == null) {
            return null;
        }

        if (chunkIndex == null) {
            return source.toString();
        }
        return source + "#chunk-" + chunkIndex;
    }

    private String safeText(Document document) {
        if (document == null || document.getText() == null) {
            return "";
        }
        return document.getText().trim();
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private String escapeForFilter(String kb) {
        return kb.replace("'", "\\'");
    }

    private boolean shouldWebFallback(QueryRoutingDecision routing, List<Document> rerankedDocs) {
        if (!Boolean.TRUE.equals(ragProperties.getWebFallbackEnabled())) {
            return false;
        }

        boolean allowWeb = routing != null && routing.allowWeb();
        boolean allowAutoOnEmpty = Boolean.TRUE.equals(ragProperties.getAllowWebFallbackOnEmptyRag());

        if (rerankedDocs == null || rerankedDocs.isEmpty()) {
            return allowWeb || allowAutoOnEmpty;
        }

        Double minScore = ragProperties.getRagMinTopScore();
        if (minScore == null) {
            return allowWeb;
        }
        double top = topScore(rerankedDocs);
        // 若拿不到分数（全是 0），只在用户显式 web-hint 时回退，避免无意义联网
        if (top <= 0.0) {
            return allowWeb;
        }
        return top < minScore && allowWeb;
    }

    private double topScore(List<Document> docs) {
        double top = 0.0;
        for (Document d : docs) {
            double s = vectorScore(d);
            if (s > top) {
                top = s;
            }
        }
        return top;
    }

    private List<Document> webResultsToDocs(List<WebResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<Document> docs = new ArrayList<>();
        int i = 0;
        for (WebResult r : results) {
            if (r == null || r.snippet() == null || r.snippet().isBlank()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("source", r.url() == null || r.url().isBlank() ? r.source() : r.url());
            meta.put("origin", "web");
            meta.put("chunk_index", i++);
            docs.add(new Document(r.snippet().trim(), meta));
        }
        return docs;
    }

    private static class RAGExecutionContext {
        private final String rewrittenQuestion;
        private final List<Document> rerankedDocs;
        private final List<String> references;
        private final String routeReason;
        private final boolean usedWebSearch;
        private final int ragDocsCount;
        private final int webResultsCount;
        private final String webFallbackReason;
        private final String historyText;
        private final int historyTurns;

        private RAGExecutionContext(String rewrittenQuestion,
                                    List<Document> rerankedDocs,
                                    List<String> references,
                                    String routeReason,
                                    boolean usedWebSearch,
                                    int ragDocsCount,
                                    int webResultsCount,
                                    String webFallbackReason,
                                    String historyText,
                                    int historyTurns) {
            this.rewrittenQuestion = rewrittenQuestion;
            this.rerankedDocs = rerankedDocs;
            this.references = references;
            this.routeReason = routeReason;
            this.usedWebSearch = usedWebSearch;
            this.ragDocsCount = ragDocsCount;
            this.webResultsCount = webResultsCount;
            this.webFallbackReason = webFallbackReason;
            this.historyText = historyText;
            this.historyTurns = historyTurns;
        }

        public String rewrittenQuestion() {
            return rewrittenQuestion;
        }

        public List<Document> rerankedDocs() {
            return rerankedDocs;
        }

        public List<String> references() {
            return references;
        }

        public String routeReason() {
            return routeReason;
        }

        public boolean usedWebSearch() {
            return usedWebSearch;
        }

        public int ragDocsCount() {
            return ragDocsCount;
        }

        public int webResultsCount() {
            return webResultsCount;
        }

        public String webFallbackReason() {
            return webFallbackReason;
        }

        public String historyText() {
            return historyText;
        }

        public int historyTurns() {
            return historyTurns;
        }
    }
}
