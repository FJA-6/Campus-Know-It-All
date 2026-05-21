package com.fja.ai.tinyrag.controller;

import com.fja.ai.tinyrag.admin.AdminAuditService;
import com.fja.ai.tinyrag.admin.AdminContext;
import com.fja.ai.tinyrag.admin.KnowledgeAsset;
import com.fja.ai.tinyrag.admin.KnowledgeAssetRepository;
import com.fja.ai.tinyrag.model.RAGRequest;
import com.fja.ai.tinyrag.model.UploadResponse;
import com.fja.ai.tinyrag.service.KnowledgeIngestionService;
import com.fja.ai.tinyrag.service.RAGService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/rag")
@Validated
public class RAGController {

    @Resource
    private final RAGService ragService;
    @Resource
    private final KnowledgeIngestionService ingestionService;

    private final KnowledgeAssetRepository knowledgeAssetRepository;
    private final AdminAuditService adminAuditService;
    private final AdminContext adminContext;

    public RAGController(RAGService ragService,
                         KnowledgeIngestionService ingestionService,
                         KnowledgeAssetRepository knowledgeAssetRepository,
                         AdminAuditService adminAuditService,
                         AdminContext adminContext) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.knowledgeAssetRepository = knowledgeAssetRepository;
        this.adminAuditService = adminAuditService;
        this.adminContext = adminContext;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody RAGRequest request) {
        return ragService.streamChat(request);
    }

    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse uploadFile(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "kb", required = false) String kb,
                                     HttpServletRequest request) {
        UploadResponse resp = ingestionService.ingest(file, kb);

        // 记录“知识资产”上传清单（演示后台用）
        try {
            KnowledgeAsset a = new KnowledgeAsset();
            a.setKb(resp.getKb());
            a.setFilename(resp.getFileName());
            a.setChunkCount(resp.getChunkCount() == null ? 0 : resp.getChunkCount());
            a.setUploader(adminContext.currentUsername(request));
            String ext = StringUtils.getFilenameExtension(resp.getFileName());
            a.setFileType(ext == null ? "" : ext.toLowerCase(Locale.ROOT));
            knowledgeAssetRepository.save(a);

            adminAuditService.log(request, "KB_UPLOAD", "kb=" + resp.getKb() + ", file=" + resp.getFileName() + ", chunks=" + resp.getChunkCount());
        } catch (Exception ignored) {
            // 演示优先：不让记录失败影响上传
        }
        return resp;
    }
}
