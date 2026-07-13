package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.CreateDocumentBlockCommand;
import com.devcollab.knowledgecore.document.application.DocumentBlockApplicationService;
import com.devcollab.knowledgecore.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DocumentBlockController {

    private final DocumentBlockApplicationService blockService;

    public DocumentBlockController(
            DocumentBlockApplicationService blockService
    ) {
        this.blockService = blockService;
    }

    @PostMapping("/api/v1/documents/{documentId}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentBlockResponse create(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateDocumentBlockRequest request
    ) {
        return DocumentBlockResponse.from(blockService.create(
                documentId,
                currentUser.userId(),
                new CreateDocumentBlockCommand(
                        request.type(),
                        request.content().text()
                )
        ));
    }

    @GetMapping("/api/v1/documents/{documentId}/blocks")
    public List<DocumentBlockResponse> list(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return blockService.list(documentId, currentUser.userId())
                .stream()
                .map(DocumentBlockResponse::from)
                .toList();
    }
}
