package com.devcollab.knowledgecore.grpc;

import com.devcollab.knowledgecore.document.application.ApplyDocumentCollaborationOperationCommand;
import com.devcollab.knowledgecore.document.application.DocumentApplicationService;
import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationResult;
import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationService;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.protocol.core.v1.ApplyDocumentOperationRequest;
import com.devcollab.protocol.core.v1.DocumentOperationResponse;
import com.devcollab.protocol.core.v1.DocumentOperationStatus;
import com.devcollab.protocol.core.v1.DocumentOperationType;
import com.devcollab.protocol.core.v1.KnowledgeCoreCollaborationServiceGrpc;
import com.devcollab.protocol.core.v1.ListDocumentOperationsRequest;
import com.devcollab.protocol.core.v1.ListDocumentOperationsResponse;
import com.devcollab.protocol.core.v1.VerifyDocumentAccessRequest;
import com.devcollab.protocol.core.v1.VerifyDocumentAccessResponse;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class KnowledgeCoreCollaborationGrpcService extends
        KnowledgeCoreCollaborationServiceGrpc
                .KnowledgeCoreCollaborationServiceImplBase {

    private final DocumentApplicationService documentService;
    private final DocumentCollaborationOperationService operationService;
    private final GrpcExceptionMapper exceptionMapper;

    public KnowledgeCoreCollaborationGrpcService(
            DocumentApplicationService documentService,
            DocumentCollaborationOperationService operationService,
            GrpcExceptionMapper exceptionMapper
    ) {
        this.documentService = documentService;
        this.operationService = operationService;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public void verifyDocumentAccess(
            VerifyDocumentAccessRequest request,
            StreamObserver<VerifyDocumentAccessResponse> responseObserver
    ) {
        respond(responseObserver, () -> {
            UUID userId = GrpcAuthenticationContext.requireClaims().userId();
            var document = documentService.get(
                    uuid(request.getDocumentId(), "document_id"),
                    userId
            );
            return VerifyDocumentAccessResponse.newBuilder()
                    .setDocumentId(document.id().toString())
                    .setWorkspaceId(document.workspaceId().toString())
                    .build();
        });
    }

    @Override
    public void applyDocumentOperation(
            ApplyDocumentOperationRequest request,
            StreamObserver<DocumentOperationResponse> responseObserver
    ) {
        respond(responseObserver, () -> {
            UUID userId = GrpcAuthenticationContext.requireClaims().userId();
            var command = new ApplyDocumentCollaborationOperationCommand(
                    uuid(request.getClientOperationId(), "client_operation_id"),
                    request.hasBlockId()
                            ? uuid(request.getBlockId(), "block_id")
                            : null,
                    operationType(request.getOperationType()),
                    request.hasExpectedVersion()
                            ? request.getExpectedVersion()
                            : null,
                    request.hasBlockType()
                            ? blockType(request.getBlockType())
                            : null,
                    request.hasTargetIndex()
                            ? request.getTargetIndex()
                            : null,
                    request.hasText() ? request.getText() : null
            );
            return operationResponse(operationService.apply(
                    uuid(request.getDocumentId(), "document_id"),
                    userId,
                    command
            ));
        });
    }

    @Override
    public void listDocumentOperations(
            ListDocumentOperationsRequest request,
            StreamObserver<ListDocumentOperationsResponse> responseObserver
    ) {
        respond(responseObserver, () -> {
            UUID userId = GrpcAuthenticationContext.requireClaims().userId();
            int limit = request.getLimit() == 0 ? 100 : request.getLimit();
            var page = operationService.listAfter(
                    uuid(request.getDocumentId(), "document_id"),
                    userId,
                    request.getAfterSequence(),
                    limit
            );
            var response = ListDocumentOperationsResponse.newBuilder()
                    .setRequestedAfterSequence(page.requestedAfterSequence())
                    .setLatestDocumentSequence(page.latestDocumentSequence())
                    .setHasMore(page.hasMore());
            page.operations().stream()
                    .map(this::operationResponse)
                    .forEach(response::addOperations);
            return response.build();
        });
    }

    private DocumentOperationResponse operationResponse(
            DocumentCollaborationOperationResult result
    ) {
        var response = DocumentOperationResponse.newBuilder()
                .setClientOperationId(result.clientOperationId().toString())
                .setOperationType(DocumentOperationType.valueOf(
                        result.operationType()
                ))
                .setStatus(DocumentOperationStatus.valueOf(result.status()))
                .setDocumentSequence(result.documentSequence())
                .setOperatorUserId(result.operatorUserId().toString());
        if (result.blockId() != null) {
            response.setBlockId(result.blockId().toString());
        }
        if (result.block() != null) {
            response.setBlock(block(result.block()));
        }
        result.blocks().stream()
                .map(this::block)
                .forEach(response::addBlocks);
        return response.build();
    }

    private com.devcollab.protocol.core.v1.DocumentBlock block(
            DocumentBlock block
    ) {
        return com.devcollab.protocol.core.v1.DocumentBlock.newBuilder()
                .setId(block.id().toString())
                .setDocumentId(block.documentId().toString())
                .setType(com.devcollab.protocol.core.v1.DocumentBlockType
                        .valueOf(block.type().name()))
                .setText(block.text())
                .setSortOrder(block.sortOrder())
                .setVersion(block.version())
                .setCreatedBy(block.createdBy().toString())
                .setCreatedAt(timestamp(block.createdAt()))
                .setUpdatedAt(timestamp(block.updatedAt()))
                .build();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private String operationType(DocumentOperationType type) {
        if (type == DocumentOperationType.DOCUMENT_OPERATION_TYPE_UNSPECIFIED
                || type == DocumentOperationType.UNRECOGNIZED) {
            throw new IllegalArgumentException("operation_type is required");
        }
        return type.name();
    }

    private com.devcollab.knowledgecore.document.domain.DocumentBlockType blockType(
            com.devcollab.protocol.core.v1.DocumentBlockType type
    ) {
        if (type == com.devcollab.protocol.core.v1.DocumentBlockType
                .DOCUMENT_BLOCK_TYPE_UNSPECIFIED
                || type == com.devcollab.protocol.core.v1.DocumentBlockType
                .UNRECOGNIZED) {
            throw new IllegalArgumentException("block_type is required");
        }
        return com.devcollab.knowledgecore.document.domain.DocumentBlockType
                .valueOf(type.name());
    }

    private UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private <T> void respond(
            StreamObserver<T> observer,
            Supplier<T> invocation
    ) {
        try {
            observer.onNext(invocation.get());
            observer.onCompleted();
        } catch (RuntimeException exception) {
            observer.onError(exceptionMapper.map(exception));
        }
    }
}
